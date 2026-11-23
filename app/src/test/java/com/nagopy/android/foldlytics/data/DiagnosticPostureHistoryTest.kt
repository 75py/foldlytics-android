package com.nagopy.android.foldlytics.data

import android.app.usage.UsageEvents
import com.nagopy.android.foldlytics.MainUiState
import com.nagopy.android.foldlytics.model.AnalysisPeriod
import com.nagopy.android.foldlytics.model.Calibration
import com.nagopy.android.foldlytics.model.DisplayConfiguration
import com.nagopy.android.foldlytics.model.PostureCheckpoint
import com.nagopy.android.foldlytics.model.PostureCheckpointSource
import com.nagopy.android.foldlytics.model.PostureEventSource
import java.time.ZoneOffset
import kotlinx.coroutines.flow.Flow
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
        val request = StoredAnalysisRequest(
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
            diagnosticPostureEvents = diagnosticEvents,
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
    fun visibleEventsFallBackToTheAnalysisUntilTheHistoryLoads() {
        val state = MainUiState(isDiagnosticHistoryLoading = true)

        assertTrue(state.isDiagnosticHistoryPending)
        assertEquals(emptyList<Any>(), state.visiblePostureEvents)
        assertTrue(
            !state.copy(diagnosticPostureEvents = emptyList()).isDiagnosticHistoryPending,
        )
    }

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
