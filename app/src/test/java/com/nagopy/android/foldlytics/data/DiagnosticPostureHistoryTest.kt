package com.nagopy.android.foldlytics.data

import android.app.usage.UsageEvents
import com.nagopy.android.foldlytics.DiagnosticHistoryController
import com.nagopy.android.foldlytics.DiagnosticHistoryState
import com.nagopy.android.foldlytics.DiagnosticHistoryStatus
import com.nagopy.android.foldlytics.MainUiState
import com.nagopy.android.foldlytics.model.AnalysisPeriod
import com.nagopy.android.foldlytics.model.Calibration
import com.nagopy.android.foldlytics.model.DisplayConfiguration
import com.nagopy.android.foldlytics.model.PostureCheckpoint
import com.nagopy.android.foldlytics.model.PostureCheckpointSource
import com.nagopy.android.foldlytics.model.PostureEventSource
import java.time.ZoneOffset
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Startup analysis replaces old history with a compact seed. Diagnostics and the shared report
 * must still list and count every posture event the full-history analysis produced.
 */
class DiagnosticPostureHistoryTest {
    private val zoneId = ZoneOffset.UTC
    private val cover = DisplayConfiguration(420, 890, 420, 1, 420)
    private val inner = DisplayConfiguration(730, 820, 730, 2, 420)
    private val calibration = Calibration(cover = cover, inner = inner)

    @Test
    fun diagnosticHistoryRestoresEveryVisibleLookbackEventAndCount() = runBlocking {
        val events = history()
        val boundedDao = InMemoryUsageEventDao(events)
        val legacyDao = InMemoryUsageEventDao(events, triggersInstalled = false)
        val request = request()

        val legacy = loader(legacyDao).load(request, zoneId)
        val boundedLoader = loader(boundedDao)
        val bounded = boundedLoader.load(request, zoneId)
        val legacyAnalysis = requireNotNull(legacy.analysis)
        val boundedAnalysis = requireNotNull(bounded.analysis)
        // The startup analysis really did drop lookback events, including the repeated one.
        assertNotEquals(legacyAnalysis.postureEvents, boundedAnalysis.postureEvents)
        assertEquals(
            legacyAnalysis.copy(postureEvents = emptyList()),
            boundedAnalysis.copy(postureEvents = emptyList()),
        )

        boundedDao.resetRowsRead()
        val diagnosticEvents = boundedLoader.loadDiagnosticPostureEvents(
            requireNotNull(bounded.diagnosticHistory),
            zoneId,
        )
        // Only configuration and restart rows are read for the complete history.
        assertEquals(
            events.count { it.rawEventType in StoredUsageEventTypes.postureEvidence }.toLong(),
            boundedDao.rowsRead,
        )

        val expected = MainUiState(analysis = legacyAnalysis)
        val actual = MainUiState(
            analysis = boundedAnalysis,
            diagnosticHistory = DiagnosticHistoryState(
                generation = 1L,
                status = DiagnosticHistoryStatus.READY,
                events = diagnosticEvents,
            ),
        )
        assertEquals(expected.visiblePostureEvents, actual.visiblePostureEvents)
        assertEquals(
            expected.visiblePostureEvents.count { it.isBeforeRange },
            actual.visiblePostureEvents.count { it.isBeforeRange },
        )
        assertEquals(
            expected.visiblePostureEvents.count { !it.isBeforeRange },
            actual.visiblePostureEvents.count { !it.isBeforeRange },
        )
        val repeatedOldEvents = actual.visiblePostureEvents.filter {
            it.isBeforeRange &&
                it.source == PostureEventSource.CONFIGURATION_CHANGE &&
                it.configuration == cover &&
                it.timestampMillis < 3 * HOUR
        }
        assertEquals(listOf(2 * HOUR, HOUR), repeatedOldEvents.map { it.timestampMillis })
        assertTrue(actual.visiblePostureEvents.size > boundedAnalysis.postureEvents.size)
    }

    @Test
    fun historyReadAfterABackfilledConfigurationDoesNotMixWithTheDisplayedAnalysis() = runBlocking {
        val dao = InMemoryUsageEventDao(history())
        val loader = loader(dao)
        val displayed = loader.load(request(), zoneId)
        val displayedInRange = requireNotNull(displayed.analysis).postureEvents
            .filterNot { it.isBeforeRange }

        // A late sync backfills a configuration inside the displayed window.
        dao.insertEvents(listOf(backfilledInnerEvent()))

        val outcome = runCatching {
            loader.loadDiagnosticPostureEvents(requireNotNull(displayed.diagnosticHistory), zoneId)
        }
        // Before the source revision check, this returned the new history, whose in-range events
        // no longer matched the displayed analysis.
        assertTrue(
            "returned history matches the display: " +
                "${outcome.getOrNull()?.filterNot { it.isBeforeRange } == displayedInRange}",
            outcome.exceptionOrNull() is StaleDiagnosticHistoryException,
        )

        // Reloading the analysis gives a history from the same revision.
        val reloaded = loader.load(request(), zoneId)
        val events = loader.loadDiagnosticPostureEvents(
            requireNotNull(reloaded.diagnosticHistory),
            zoneId,
        )
        assertEquals(
            requireNotNull(reloaded.analysis).postureEvents.filterNot { it.isBeforeRange },
            events.filterNot { it.isBeforeRange },
        )
        assertNotEquals(displayedInRange, events.filterNot { it.isBeforeRange })
    }

    @Test
    fun sharingAfterABackfillReloadsAndUsesOneRevisionForAnalysisAndHistory() = runBlocking {
        val events = history()
        val dao = InMemoryUsageEventDao(events)
        val loader = loader(dao)
        var displayed = loader.load(request(), zoneId)
        val scope = CoroutineScope(coroutineContext + SupervisorJob())
        lateinit var controller: DiagnosticHistoryController
        var reloads = 0
        controller = DiagnosticHistoryController(
            scope = scope,
            load = { loader.loadDiagnosticPostureEvents(it, zoneId) },
            onStateChanged = {},
            onStale = {
                // As the view model does: reload, then display the new analysis.
                scope.launch {
                    reloads += 1
                    val reloaded = loader.load(request(), zoneId)
                    controller.onAnalysisDisplayed(reloaded.diagnosticHistory)
                    displayed = reloaded
                }
            },
        )
        controller.onAnalysisDisplayed(displayed.diagnosticHistory)

        // A late sync backfills a configuration after the analysis was displayed.
        dao.insertEvents(listOf(backfilledInnerEvent()))
        val (sharedAnalysis, sharedEvents) = requireNotNull(
            controller.withCompleteHistory {
                requireNotNull(displayed.analysis) to requireNotNull(controller.state.events)
            },
        )

        assertEquals(1, reloads)
        val expected = loader(
            InMemoryUsageEventDao(events + backfilledInnerEvent(), triggersInstalled = false),
        ).load(request(), zoneId)
        assertEquals(requireNotNull(expected.analysis).postureEvents, sharedEvents)
        assertEquals(
            sharedAnalysis.postureEvents.filterNot { it.isBeforeRange },
            sharedEvents.filterNot { it.isBeforeRange },
        )
        scope.cancel()
    }

    @Test
    fun completeHistoryListsMoreThanTheReportLimitNewestFirst() = runBlocking {
        val events = history()
        val bounded = loader(InMemoryUsageEventDao(events))
        val legacy = loader(InMemoryUsageEventDao(events, triggersInstalled = false))
            .load(request(), zoneId)
        val snapshot = bounded.load(request(), zoneId)
        val complete = bounded.loadDiagnosticPostureEvents(
            requireNotNull(snapshot.diagnosticHistory),
            zoneId,
        )

        assertTrue(complete.size > 50)
        assertEquals(requireNotNull(legacy.analysis).postureEvents, complete)
        assertEquals(
            complete.sortedByDescending { it.timestampMillis }.map { it.timestampMillis },
            complete.map { it.timestampMillis },
        )
    }

    @Test
    fun visibleEventsFallBackToTheAnalysisUntilTheHistoryLoads() {
        val state = MainUiState(
            diagnosticHistory = DiagnosticHistoryState(status = DiagnosticHistoryStatus.LOADING),
        )

        assertTrue(state.isDiagnosticHistoryPending)
        assertEquals(emptyList<Any>(), state.visiblePostureEvents)
        val ready = state.copy(
            diagnosticHistory = DiagnosticHistoryState(
                status = DiagnosticHistoryStatus.READY,
                events = emptyList(),
            ),
        )
        assertTrue(!ready.isDiagnosticHistoryPending)
    }

    private fun request() = StoredAnalysisRequest(
        period = AnalysisPeriod.HOURS_24,
        customRange = null,
        calibration = calibration,
        syncState = UsageSyncState(
            lastSuccessfulEndMillis = END_MILLIS,
            lastSuccessfulAtMillis = END_MILLIS,
            lastQueryBeginMillis = END_MILLIS - HOUR,
            lastInsertedEventCount = 0,
        ),
        checkpointRevision = 0L,
    )

    private fun backfilledInnerEvent() = UsageEventEntity(
        eventKey = "backfill",
        timestampMillis = END_MILLIS - 3 * HOUR - 7L,
        sequenceAtTimestamp = 0,
        rawEventType = UsageEvents.Event.CONFIGURATION_CHANGE,
        packageName = null,
        className = null,
        hasConfiguration = true,
        screenWidthDp = inner.screenWidthDp,
        screenHeightDp = inner.screenHeightDp,
        smallestScreenWidthDp = inner.smallestScreenWidthDp,
        orientation = inner.orientation,
        densityDpi = inner.densityDpi,
    )

    private fun loader(dao: InMemoryUsageEventDao): StoredAnalysisLoader {
        val checkpointDao = InMemoryCheckpointDao(
            listOf(
                PostureCheckpoint(
                    timestampMillis = DAY + 3 * HOUR,
                    configuration = inner,
                    source = PostureCheckpointSource.APP_FOREGROUND,
                ).toEntity(),
            ),
        )
        return StoredAnalysisLoader(
            syncRepository = UsageSyncRepository(
                eventSource = NoUsageEventSource,
                eventStore = RoomUsageEventStore(dao),
            ),
            checkpointRepository = PostureCheckpointRepository(checkpointDao),
            dailySummaryRepository = DailySummaryRepository(
                usageEventDao = dao,
                checkpointDao = checkpointDao,
                summaryDao = InMemorySummaryDao(),
            ),
            packageLabel = { it },
            isLauncherApp = { false },
            currentTimeMillis = { END_MILLIS },
        )
    }

    /** Six days without a restart. The same cover event repeats on the first day. */
    private fun history(): List<UsageEventEntity> {
        val rows = mutableListOf<UsageEventEntity>()
        fun add(
            at: Long,
            type: Int,
            packageName: String? = null,
            config: DisplayConfiguration? = null,
        ) {
            rows += UsageEventEntity(
                eventKey = "e${rows.size}",
                timestampMillis = at,
                sequenceAtTimestamp = 0,
                rawEventType = type,
                packageName = packageName,
                className = packageName?.let { "Main" },
                hasConfiguration = config != null,
                screenWidthDp = config?.screenWidthDp,
                screenHeightDp = config?.screenHeightDp,
                smallestScreenWidthDp = config?.smallestScreenWidthDp,
                orientation = config?.orientation,
                densityDpi = config?.densityDpi,
            )
        }
        add(HOUR, UsageEvents.Event.CONFIGURATION_CHANGE, config = cover)
        add(2 * HOUR, UsageEvents.Event.CONFIGURATION_CHANGE, config = cover)
        (0 until 6).forEach { day ->
            (0 until 8).forEach { session ->
                val at = day * DAY + 4 * HOUR + session * 2 * HOUR
                add(at, UsageEvents.Event.SCREEN_INTERACTIVE)
                add(at + 1, UsageEvents.Event.KEYGUARD_HIDDEN)
                add(at + 2, UsageEvents.Event.CONFIGURATION_CHANGE, config = inner)
                add(at + 3, UsageEvents.Event.ACTIVITY_RESUMED, packageName = "app.$session")
                add(at + 30 * MINUTE, UsageEvents.Event.CONFIGURATION_CHANGE, config = cover)
                add(at + 40 * MINUTE, UsageEvents.Event.ACTIVITY_PAUSED, packageName = "app.$session")
                add(at + 41 * MINUTE, UsageEvents.Event.ACTIVITY_STOPPED, packageName = "app.$session")
                add(at + 42 * MINUTE, UsageEvents.Event.SCREEN_NON_INTERACTIVE)
            }
        }
        return rows
    }

    private object NoUsageEventSource : UsageEventSource {
        override fun hasUsageAccess(): Boolean = true

        override fun read(beginMillis: Long, endMillis: Long): UsageReadResult =
            UsageReadResult.Success(emptyList())
    }

    private class InMemoryCheckpointDao(
        private var checkpoints: List<PostureCheckpointEntity>,
    ) : PostureCheckpointDao {
        private val ordered get() = checkpoints.sortedBy(PostureCheckpointEntity::timestampMillis)

        override suspend fun insert(checkpoint: PostureCheckpointEntity): Long {
            checkpoints = checkpoints + checkpoint
            return checkpoints.size.toLong()
        }

        override suspend fun insertAll(checkpoints: List<PostureCheckpointEntity>): List<Long> =
            checkpoints.map { insert(it) }

        override suspend fun load(beginMillis: Long, endMillis: Long) =
            ordered.filter { it.timestampMillis in beginMillis until endMillis }

        override suspend fun latestBefore(endMillis: Long) =
            ordered.lastOrNull { it.timestampMillis < endMillis }

        override suspend fun latest(source: String) = ordered.lastOrNull { it.source == source }

        override fun observeRevision(): Flow<Long> = flowOf(0L)

        override suspend fun earliestTimestamp() = ordered.firstOrNull()?.timestampMillis

        override suspend fun latestTimestamp() = ordered.lastOrNull()?.timestampMillis
    }

    private class InMemorySummaryDao : DailyPostureSummaryDao {
        private var state: DailySummaryStateEntity? = null
        private var summaries = emptyList<DailyPostureSummaryEntity>()
        private var appUsage = emptyList<DailyAppUsageSummaryEntity>()
        private var sessions = emptyList<InnerDisplaySessionEntity>()
        private var sessionAppUsage = emptyList<InnerDisplaySessionAppUsageEntity>()

        override suspend fun load(beginMillis: Long, endMillis: Long) =
            summaries.filter { it.dayStartMillis < endMillis && it.dayEndMillis > beginMillis }

        override suspend fun loadAll() = summaries.sortedBy(DailyPostureSummaryEntity::dayStartMillis)

        override suspend fun loadAggregatedAppUsage(beginMillis: Long, endMillis: Long) = appUsage
            .filter { it.dayStartMillis < endMillis && it.dayEndMillis > beginMillis }
            .groupBy(DailyAppUsageSummaryEntity::packageName)
            .map { (packageName, rows) ->
                AggregatedAppUsage(
                    packageName = packageName,
                    coverMillis = rows.sumOf { it.coverMillis },
                    innerMillis = rows.sumOf { it.innerMillis },
                    excludedMillis = rows.sumOf { it.excludedMillis },
                )
            }
            .sortedBy(AggregatedAppUsage::packageName)

        override suspend fun loadCompleteInnerSessions(beginMillis: Long, endMillis: Long) =
            sessions.filter {
                it.openedAtMillis >= beginMillis &&
                    it.closedAtMillis != null &&
                    it.closedAtMillis < endMillis
            }

        override suspend fun loadCompleteInnerSessionAppUsages(beginMillis: Long, endMillis: Long) =
            sessionAppUsage.filter { usage ->
                loadCompleteInnerSessions(beginMillis, endMillis).any {
                    it.openedAtMillis == usage.openedAtMillis &&
                        it.openedSequenceAtTimestamp == usage.openedSequenceAtTimestamp
                }
            }

        override suspend fun earliestInnerSessionStartOverlapping(beginMillis: Long) = sessions
            .filter { it.closedAtMillis == null || it.closedAtMillis >= beginMillis }
            .minOfOrNull(InnerDisplaySessionEntity::openedAtMillis)

        override suspend fun loadState() = state

        override suspend fun insertAll(summaries: List<DailyPostureSummaryEntity>) {
            this.summaries = this.summaries + summaries
        }

        override suspend fun insertAllAppUsage(summaries: List<DailyAppUsageSummaryEntity>) {
            appUsage = appUsage + summaries
        }

        override suspend fun insertAllInnerSessions(sessions: List<InnerDisplaySessionEntity>) {
            this.sessions = this.sessions + sessions
        }

        override suspend fun insertAllInnerSessionAppUsages(
            appUsages: List<InnerDisplaySessionAppUsageEntity>,
        ) {
            sessionAppUsage = sessionAppUsage + appUsages
        }

        override suspend fun upsertState(state: DailySummaryStateEntity) {
            this.state = state
        }

        override suspend fun deleteAll() {
            summaries = emptyList()
        }

        override suspend fun deleteAllAppUsage() {
            appUsage = emptyList()
        }

        override suspend fun deleteAllInnerSessions() {
            sessions = emptyList()
        }

        override suspend fun deleteAllInnerSessionAppUsages() {
            sessionAppUsage = emptyList()
        }

        override suspend fun deleteFrom(beginMillis: Long) {
            summaries = summaries.filter { it.dayStartMillis < beginMillis }
        }

        override suspend fun deleteAppUsageFrom(beginMillis: Long) {
            appUsage = appUsage.filter { it.dayStartMillis < beginMillis }
        }

        override suspend fun deleteInnerSessionsFrom(beginMillis: Long) {
            sessions = sessions.filter { it.openedAtMillis < beginMillis }
        }

        override suspend fun deleteInnerSessionAppUsagesFrom(beginMillis: Long) {
            sessionAppUsage = sessionAppUsage.filter { it.openedAtMillis < beginMillis }
        }
    }

    private companion object {
        const val MINUTE = 60_000L
        const val HOUR = 60 * MINUTE
        const val DAY = 24 * HOUR
        const val END_MILLIS = 6 * DAY - HOUR
    }
}
