package com.nagopy.android.foldlytics.data

import com.nagopy.android.foldlytics.model.AnalysisPeriod
import com.nagopy.android.foldlytics.model.Calibration
import com.nagopy.android.foldlytics.model.DisplayConfiguration
import com.nagopy.android.foldlytics.model.UsageRecord
import java.time.ZoneOffset
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StoredAnalysisSnapshotConcurrencyTest {
    private val zoneId = ZoneOffset.UTC
    private val usageEventDao = FakeUsageEventDao()
    private val checkpointDao = FakePostureCheckpointDao()
    private val summaryDao = GatedDailySummaryDao()
    private val repository = DailySummaryRepository(
        usageEventDao = usageEventDao,
        checkpointDao = checkpointDao,
        summaryDao = summaryDao,
    )

    @Test
    fun screenSnapshotBlocksCompetingRebuildUntilSessionDetailsAreRead() {
        runBlocking {
            val loader = loader(repository)
            val screen = async(start = CoroutineStart.UNDISPATCHED) {
                loader.load(request(CALIBRATION), zoneId)
            }
            summaryDao.appUsageReadEntered.await()

            val competingRebuild = async(start = CoroutineStart.UNDISPATCHED) {
                repository.ensureUpToDate(
                    calibration = SWAPPED_CALIBRATION,
                    syncedThroughMillis = END_MILLIS,
                    syncQueryBeginMillis = 0L,
                    checkpointRevision = 0L,
                    zoneId = zoneId,
                    collectionGapStarts = emptyList(),
                )
            }
            assertEquals(
                listOf("loadState", "loadAll", "loadAggregatedAppUsage"),
                summaryDao.operations,
            )
            assertFalse(summaryDao.competingLoadStateEntered.isCompleted)

            summaryDao.releaseAppUsageRead.complete(Unit)
            summaryDao.sessionReadEntered.await()
            assertEquals(
                listOf("loadState", "loadAll", "loadAggregatedAppUsage", "loadSessions"),
                summaryDao.operations,
            )
            assertFalse(summaryDao.competingLoadStateEntered.isCompleted)

            summaryDao.releaseSessionRead.complete(Unit)
            val snapshot = screen.await()
            withTimeout(1_000L) {
                summaryDao.competingLoadStateEntered.await()
                competingRebuild.await()
            }

            assertEquals(AnalysisPeriod.DAYS_7, snapshot.selectedPeriod)
            assertEquals(COVER_MILLIS, snapshot.periodSummary?.coverMillis)
            assertEquals(INNER_MILLIS, snapshot.periodSummary?.innerMillis)
            assertEquals(
                listOf(APP_PACKAGE to (COVER_MILLIS to INNER_MILLIS)),
                snapshot.periodSummary?.apps?.map {
                    it.packageName to (it.coverMillis to it.innerMillis)
                },
            )
            assertEquals(1, snapshot.innerSessionSummary?.completeSessionCount)
            assertTrue(
                summaryDao.operations.indexOf("loadState:competing") >
                    summaryDao.operations.indexOf("loadSessionAppUsages"),
            )
        }
    }

    @Test
    fun cancelledSnapshotReleasesCompetingRebuild() {
        runBlocking {
            val loader = loader(repository)
            val screen = async(start = CoroutineStart.UNDISPATCHED) {
                loader.load(request(CALIBRATION), zoneId)
            }
            summaryDao.appUsageReadEntered.await()
            summaryDao.releaseAppUsageRead.complete(Unit)
            summaryDao.sessionReadEntered.await()

            val competingRebuild = async(start = CoroutineStart.UNDISPATCHED) {
                repository.ensureUpToDate(
                    calibration = SWAPPED_CALIBRATION,
                    syncedThroughMillis = END_MILLIS,
                    syncQueryBeginMillis = 0L,
                    checkpointRevision = 0L,
                    zoneId = zoneId,
                    collectionGapStarts = emptyList(),
                )
            }
            assertFalse(summaryDao.competingLoadStateEntered.isCompleted)

            screen.cancelAndJoin()
            withTimeout(1_000L) {
                summaryDao.competingLoadStateEntered.await()
                competingRebuild.await()
            }
        }
    }

    private fun loader(repository: DailySummaryRepository) = StoredAnalysisLoader(
        syncRepository = UsageSyncRepository(
            eventSource = NoopUsageEventSource,
            eventStore = FakeUsageEventStore,
        ),
        checkpointRepository = PostureCheckpointRepository(checkpointDao),
        dailySummaryRepository = repository,
        packageLabel = { it },
        isLauncherApp = { false },
        currentTimeMillis = { END_MILLIS },
    )

    private fun request(calibration: Calibration) = StoredAnalysisRequest(
        period = AnalysisPeriod.DAYS_7,
        customRange = null,
        calibration = calibration,
        syncState = UsageSyncState(
            lastSuccessfulEndMillis = END_MILLIS,
            lastSuccessfulAtMillis = END_MILLIS,
            lastQueryBeginMillis = 0L,
            lastInsertedEventCount = 0,
        ),
        checkpointRevision = 0L,
    )

    private companion object {
        const val DAY_MILLIS = 86_400_000L
        const val END_MILLIS = DAY_MILLIS * 10L
        const val COVER_MILLIS = 70L
        const val INNER_MILLIS = 140L
        const val APP_PACKAGE = "app.example"

        val COVER_CONFIGURATION = DisplayConfiguration(
            screenWidthDp = 400,
            screenHeightDp = 900,
            smallestScreenWidthDp = 400,
            orientation = 1,
            densityDpi = 420,
        )
        val INNER_CONFIGURATION = DisplayConfiguration(
            screenWidthDp = 800,
            screenHeightDp = 900,
            smallestScreenWidthDp = 800,
            orientation = 1,
            densityDpi = 420,
        )
        val CALIBRATION = Calibration(
            cover = COVER_CONFIGURATION,
            inner = INNER_CONFIGURATION,
        )
        val SWAPPED_CALIBRATION = Calibration(
            cover = INNER_CONFIGURATION,
            inner = COVER_CONFIGURATION,
        )

        val INITIAL_STATE = DailySummaryStateEntity(
            lastAggregatedThroughMillis = END_MILLIS,
            calibrationKey = CALIBRATION.dailySummaryCacheKey(),
            zoneId = ZoneOffset.UTC.id,
            checkpointRevision = 0L,
            aggregationVersion = 6,
            lastAggregatedSyncHistoryId = 1L,
        )
        val DAILY_SUMMARIES = (7 downTo 1).map { daysBeforeEnd ->
            val start = END_MILLIS - DAY_MILLIS * daysBeforeEnd
            DailyPostureSummaryEntity(
                dayStartMillis = start,
                dayEndMillis = start + DAY_MILLIS,
                zoneId = ZoneOffset.UTC.id,
                coverMillis = COVER_MILLIS / 7L,
                innerMillis = INNER_MILLIS / 7L,
                excludedMillis = 0L,
                openedCount = if (daysBeforeEnd == 1) 1 else 0,
                closedCount = if (daysBeforeEnd == 1) 1 else 0,
                evidenceGapCount = 0,
            )
        }
        val APP_USAGE = listOf(
            AggregatedAppUsage(
                packageName = APP_PACKAGE,
                coverMillis = COVER_MILLIS,
                innerMillis = INNER_MILLIS,
                excludedMillis = 0L,
            ),
        )
        val INNER_SESSION = listOf(
            InnerDisplaySessionEntity(
                openedAtMillis = END_MILLIS - DAY_MILLIS,
                openedSequenceAtTimestamp = 0,
                closedAtMillis = END_MILLIS - DAY_MILLIS + INNER_MILLIS,
                innerActiveMillis = INNER_MILLIS,
            ),
        )
        val INNER_SESSION_APP_USAGE = listOf(
            InnerDisplaySessionAppUsageEntity(
                openedAtMillis = END_MILLIS - DAY_MILLIS,
                openedSequenceAtTimestamp = 0,
                packageName = APP_PACKAGE,
                innerActiveMillis = INNER_MILLIS,
            ),
        )
    }

    private class GatedDailySummaryDao : DailyPostureSummaryDao {
        val operations = mutableListOf<String>()
        val appUsageReadEntered = CompletableDeferred<Unit>()
        val releaseAppUsageRead = CompletableDeferred<Unit>()
        val sessionReadEntered = CompletableDeferred<Unit>()
        val releaseSessionRead = CompletableDeferred<Unit>()
        val competingLoadStateEntered = CompletableDeferred<Unit>()
        private var state = INITIAL_STATE
        private var loadStateCount = 0
        private var summaries = DAILY_SUMMARIES
        private var appUsage = APP_USAGE
        private var sessions = INNER_SESSION
        private var sessionAppUsage = INNER_SESSION_APP_USAGE

        override suspend fun load(
            beginMillis: Long,
            endMillis: Long,
        ): List<DailyPostureSummaryEntity> =
            summaries.filter { it.dayStartMillis < endMillis && it.dayEndMillis > beginMillis }

        override suspend fun loadAll(): List<DailyPostureSummaryEntity> {
            operations += "loadAll"
            return summaries
        }

        override suspend fun loadAggregatedAppUsage(
            beginMillis: Long,
            endMillis: Long,
        ): List<AggregatedAppUsage> {
            operations += "loadAggregatedAppUsage"
            appUsageReadEntered.complete(Unit)
            releaseAppUsageRead.await()
            return appUsage
        }

        override suspend fun loadCompleteInnerSessions(
            beginMillis: Long,
            endMillis: Long,
        ): List<InnerDisplaySessionEntity> {
            operations += "loadSessions"
            sessionReadEntered.complete(Unit)
            releaseSessionRead.await()
            return sessions.filter {
                it.openedAtMillis >= beginMillis &&
                    it.closedAtMillis != null &&
                    it.closedAtMillis < endMillis
            }
        }

        override suspend fun loadCompleteInnerSessionAppUsages(
            beginMillis: Long,
            endMillis: Long,
        ): List<InnerDisplaySessionAppUsageEntity> {
            operations += "loadSessionAppUsages"
            return sessionAppUsage
        }

        override suspend fun earliestInnerSessionStartOverlapping(beginMillis: Long): Long? = null

        override suspend fun loadState(): DailySummaryStateEntity? {
            loadStateCount += 1
            if (loadStateCount == 1) {
                operations += "loadState"
            } else {
                operations += "loadState:competing"
                competingLoadStateEntered.complete(Unit)
            }
            return state
        }

        override suspend fun insertAll(summaries: List<DailyPostureSummaryEntity>) {
            this.summaries = summaries
        }

        override suspend fun insertAllAppUsage(summaries: List<DailyAppUsageSummaryEntity>) {
            appUsage = summaries.map {
                AggregatedAppUsage(
                    packageName = it.packageName,
                    coverMillis = it.coverMillis,
                    innerMillis = it.innerMillis,
                    excludedMillis = it.excludedMillis,
                )
            }
        }

        override suspend fun insertAllInnerSessions(sessions: List<InnerDisplaySessionEntity>) {
            this.sessions = sessions
        }

        override suspend fun insertAllInnerSessionAppUsages(
            appUsages: List<InnerDisplaySessionAppUsageEntity>,
        ) {
            sessionAppUsage = appUsages
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
            appUsage = emptyList()
        }

        override suspend fun deleteInnerSessionsFrom(beginMillis: Long) {
            sessions = sessions.filter { it.openedAtMillis < beginMillis }
        }

        override suspend fun deleteInnerSessionAppUsagesFrom(beginMillis: Long) {
            sessionAppUsage = sessionAppUsage.filter { it.openedAtMillis < beginMillis }
        }
    }

    private class FakeUsageEventDao : UsageEventDao {
        override suspend fun loadEvents(
            beginMillis: Long,
            endMillis: Long,
        ): List<UsageEventEntity> = emptyList()

        override suspend fun loadDeviceEvents(
            beginMillis: Long,
            endMillis: Long,
            rawEventTypes: List<Int>,
        ): List<UsageEventEntity> = emptyList()

        override suspend fun loadLatestDeviceEventsBefore(
            endMillis: Long,
            rawEventTypes: List<Int>,
        ): List<UsageEventEntity> = emptyList()

        override suspend fun loadLatestActivityEventsBefore(
            endMillis: Long,
            rawEventTypes: List<Int>,
        ): List<UsageEventEntity> = emptyList()

        override suspend fun earliestEventTimestamp(): Long? = null

        override suspend fun earliestDeviceEventTimestamp(rawEventTypes: List<Int>): Long? = null

        override suspend fun loadSyncState(): UsageSyncStateEntity? = null

        override fun observeSyncState(): Flow<UsageSyncStateEntity?> = flowOf(null)

        override fun observeSyncHistoryRevision(): Flow<Long> = flowOf(0L)

        override suspend fun insertEvents(events: List<UsageEventEntity>): List<Long> =
            List(events.size) { -1L }

        override suspend fun loadEventsAtTimestamps(
            timestamps: List<Long>,
        ): List<UsageEventEntity> =
            emptyList()

        override suspend fun updateEvents(events: List<UsageEventEntity>): Int = 0

        override suspend fun upsertSyncState(state: UsageSyncStateEntity) = Unit

        override suspend fun insertSyncHistory(attempt: SyncHistoryEntity): Long = 1L

        override suspend fun loadSyncHistory(
            beginMillis: Long,
            endMillis: Long,
        ): List<SyncHistoryEntity> =
            emptyList()

        override suspend fun loadDeviceStateCheckpoints(
            beginMillis: Long,
            endMillis: Long,
        ): List<SyncHistoryEntity> = emptyList()

        override suspend fun loadLatestDeviceStateCheckpointBefore(
            endMillis: Long,
        ): SyncHistoryEntity? = null

        override suspend fun earliestDeviceStateCheckpointTimestamp(): Long? = null

        override suspend fun latestSuccessfulSyncHistoryIdThrough(
            syncedThroughMillis: Long,
        ): Long? = 1L

        override suspend fun earliestSuccessfulSyncQueryBeginAfter(
            afterHistoryId: Long,
            throughHistoryId: Long,
            syncedThroughMillis: Long,
        ): Long? = null
    }

    private class FakePostureCheckpointDao : PostureCheckpointDao {
        override suspend fun insert(checkpoint: PostureCheckpointEntity): Long = -1L

        override suspend fun insertAll(checkpoints: List<PostureCheckpointEntity>): List<Long> =
            List(checkpoints.size) { -1L }

        override suspend fun load(
            beginMillis: Long,
            endMillis: Long,
        ): List<PostureCheckpointEntity> = emptyList()

        override suspend fun latestBefore(endMillis: Long): PostureCheckpointEntity? = null

        override suspend fun latest(source: String): PostureCheckpointEntity? = null

        override fun observeRevision(): Flow<Long> = flowOf(0L)

        override suspend fun earliestTimestamp(): Long? = null

        override suspend fun latestTimestamp(): Long? = null
    }

    private object FakeUsageEventStore : UsageEventStore {
        override suspend fun loadSyncState(): UsageSyncState? = null

        override fun observeSyncState(): Flow<UsageSyncState?> = flowOf(null)

        override fun observeSyncHistoryRevision(): Flow<Long> = flowOf(0L)

        override suspend fun persistSuccessfulSync(
            records: List<UsageRecord>,
            state: UsageSyncState,
            attempt: SyncAttempt,
        ): Int = 0

        override suspend fun recordSyncAttempt(attempt: SyncAttempt) = Unit

        override suspend fun loadRecordsForAnalysis(
            beginMillis: Long,
            endMillis: Long,
        ): List<UsageRecord> = emptyList()

        override suspend fun loadSyncAttempts(
            beginMillis: Long,
            endMillis: Long,
        ): List<SyncAttempt> = emptyList()

        override suspend fun loadDeviceStateCheckpointsForAnalysis(
            beginMillis: Long,
            endMillis: Long,
        ): List<DeviceStateCheckpoint> = emptyList()
    }

    private object NoopUsageEventSource : UsageEventSource {
        override fun hasUsageAccess(): Boolean = true

        override fun read(beginMillis: Long, endMillis: Long): UsageReadResult =
            UsageReadResult.Success(emptyList())
    }
}
