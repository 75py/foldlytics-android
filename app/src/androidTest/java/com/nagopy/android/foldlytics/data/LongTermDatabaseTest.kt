package com.nagopy.android.foldlytics.data

import android.app.usage.UsageEvents
import android.content.Context
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.nagopy.android.foldlytics.model.Calibration
import com.nagopy.android.foldlytics.model.DisplayConfiguration
import com.nagopy.android.foldlytics.model.PostureCheckpoint
import com.nagopy.android.foldlytics.model.PostureCheckpointSource
import com.nagopy.android.foldlytics.model.UsageEventKind
import com.nagopy.android.foldlytics.model.UsageRecord
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LongTermDatabaseTest {
    private lateinit var context: Context
    private lateinit var database: FoldlyticsDatabase
    private val zoneId = ZoneOffset.UTC
    private val configuration = DisplayConfiguration(
        screenWidthDp = 443,
        screenHeightDp = 994,
        smallestScreenWidthDp = 443,
        orientation = 1,
        densityDpi = 420,
    )
    private val innerConfiguration = DisplayConfiguration(
        screenWidthDp = 852,
        screenHeightDp = 883,
        smallestScreenWidthDp = 852,
        orientation = 1,
        densityDpi = 420,
    )

    @Before
    fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        database = Room.inMemoryDatabaseBuilder(
            context,
            FoldlyticsDatabase::class.java,
        ).build()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun createsFreshVersionFiveDatabase() {
        assertEquals(5, database.openHelper.readableDatabase.version)
    }

    @Test
    fun periodAnalysisAndPersistedSessionsShareThePostureBaselineBeforeSeed() = runBlocking {
        val rangeEnd = LocalDate.of(2026, 1, 4)
            .atStartOfDay(zoneId)
            .toInstant()
            .toEpochMilli()
        val window = createUsageAnalysisWindow(
            periodHours = 24,
            syncedThroughMillis = rangeEnd,
        )
        val openedAt = window.rangeStartMillis + TimeUnit.HOURS.toMillis(1L)
        val closedAt = openedAt + TimeUnit.HOURS.toMillis(1L)
        database.postureCheckpointDao().insert(
            PostureCheckpoint(
                timestampMillis = window.seedStartMillis - 1L,
                configuration = configuration,
                source = PostureCheckpointSource.MANUAL_REFRESH,
            ).toEntity(),
        )
        database.usageEventDao().insertEvents(
            listOf(
                event(
                    key = "opened",
                    timestampMillis = openedAt,
                    sequence = 0,
                    rawEventType = UsageEvents.Event.CONFIGURATION_CHANGE,
                    eventConfiguration = innerConfiguration,
                ),
                event(
                    key = "screen-on",
                    timestampMillis = openedAt - 2L,
                    sequence = 0,
                    rawEventType = UsageEvents.Event.SCREEN_INTERACTIVE,
                ),
                event(
                    key = "unlocked",
                    timestampMillis = openedAt - 1L,
                    sequence = 0,
                    rawEventType = UsageEvents.Event.KEYGUARD_HIDDEN,
                ),
                event(
                    key = "closed",
                    timestampMillis = closedAt,
                    sequence = 0,
                    rawEventType = UsageEvents.Event.CONFIGURATION_CHANGE,
                    eventConfiguration = configuration,
                ),
            ),
        )

        val checkpoints = PostureCheckpointRepository(database.postureCheckpointDao())
            .loadForAnalysis(window.seedStartMillis, window.rangeEndMillis)
        assertEquals(
            listOf(window.seedStartMillis - 1L),
            checkpoints.map(PostureCheckpoint::timestampMillis),
        )
        val analysis = UsageAnalyzer(packageLabel = { it }).analyze(
            records = database.usageEventDao()
                .loadUsageEventsForAnalysis(window.seedStartMillis, window.rangeEndMillis)
                .map(UsageEventEntity::toModel),
            rangeStartMillis = window.rangeStartMillis,
            rangeEndMillis = window.rangeEndMillis,
            calibration = Calibration(cover = configuration, inner = innerConfiguration),
            checkpoints = checkpoints,
            zoneId = zoneId,
        )
        val summaryRepository = repository()
        val sessions = summaryRepository.withUpToDateSnapshot(
            calibration = Calibration(cover = configuration, inner = innerConfiguration),
            syncedThroughMillis = window.rangeEndMillis,
            syncQueryBeginMillis = window.seedStartMillis,
            checkpointRevision = 0L,
            zoneId = zoneId,
            collectionGapStarts = emptyList(),
        ) {
            loadCompleteInnerSessions(
                window.rangeStartMillis,
                window.rangeEndMillis,
            )
        }

        assertEquals(1, analysis.openedCount)
        assertEquals(1, sessions.size)
        assertEquals(analysis.openedCount, sessions.size)
    }

    @Test
    fun replaceAllAndReplaceFromKeepSessionAppUsageRowsInSync() = runBlocking {
        fun session(
            openedAtMillis: Long,
            innerActiveMillis: Long,
        ) = InnerDisplaySessionEntity(
            openedAtMillis = openedAtMillis,
            openedSequenceAtTimestamp = 0,
            closedAtMillis = openedAtMillis + innerActiveMillis,
            innerActiveMillis = innerActiveMillis,
        )

        fun appUsage(
            openedAtMillis: Long,
            packageName: String,
            innerActiveMillis: Long,
        ) = InnerDisplaySessionAppUsageEntity(
            openedAtMillis = openedAtMillis,
            openedSequenceAtTimestamp = 0,
            packageName = packageName,
            innerActiveMillis = innerActiveMillis,
        )

        val state = DailySummaryStateEntity(
            lastAggregatedThroughMillis = 10_000L,
            calibrationKey = "calibration",
            zoneId = zoneId.id,
            checkpointRevision = 0L,
            aggregationVersion = 8,
        )
        val first = session(1_000L, 100L)
        val second = session(5_000L, 200L)
        database.dailyPostureSummaryDao().replaceAll(
            summaries = emptyList(),
            appUsage = emptyList(),
            innerSessions = listOf(first, second),
            innerSessionAppUsages = listOf(
                appUsage(1_000L, "app.one", 100L),
                appUsage(5_000L, "app.two", 200L),
            ),
            state = state,
        )

        val replacement = session(5_000L, 300L)
        database.dailyPostureSummaryDao().replaceFrom(
            beginMillis = 3_000L,
            summaries = emptyList(),
            appUsage = emptyList(),
            innerSessions = listOf(replacement),
            innerSessionAppUsages = listOf(appUsage(5_000L, "app.three", 300L)),
            state = state,
        )

        val afterIncremental = loadCompleteInnerSessions(0L, 10_000L)
        assertEquals(listOf(1_000L, 5_000L), afterIncremental.map { it.openedAtMillis })
        assertEquals(mapOf("app.one" to 100L), afterIncremental[0].appUsageMillis)
        assertEquals(mapOf("app.three" to 300L), afterIncremental[1].appUsageMillis)

        database.dailyPostureSummaryDao().replaceAll(
            summaries = emptyList(),
            appUsage = emptyList(),
            innerSessions = listOf(first),
            innerSessionAppUsages = listOf(appUsage(1_000L, "app.final", 100L)),
            state = state,
        )
        val afterFull = loadCompleteInnerSessions(0L, 10_000L)
        assertEquals(listOf(1_000L), afterFull.map { it.openedAtMillis })
        assertEquals(mapOf("app.final" to 100L), afterFull.single().appUsageMillis)
    }

    @Test
    fun storesAndIncrementallyAggregatesMoreThanThreeYears() = runBlocking {
        val firstDate = LocalDate.of(2023, 1, 1)
        val firstStart = firstDate.atStartOfDay(zoneId).toInstant().toEpochMilli()
        val initialDays = 1_095L
        val initialEnd = firstDate.plusDays(initialDays)
            .atStartOfDay(zoneId)
            .toInstant()
            .toEpochMilli()
        database.usageEventDao().insertEvents(
            (0L until initialDays).flatMap { offset ->
                eventsForDay(firstStart + TimeUnit.DAYS.toMillis(offset))
            },
        )
        val repository = DailySummaryRepository(
            usageEventDao = database.usageEventDao(),
            checkpointDao = database.postureCheckpointDao(),
            summaryDao = database.dailyPostureSummaryDao(),
        )

        val initial = repository.ensureUpToDate(
            calibration = Calibration(cover = configuration),
            syncedThroughMillis = initialEnd,
            syncQueryBeginMillis = 0L,
            checkpointRevision = 0L,
            zoneId = zoneId,
            collectionGapStarts = emptyList(),
        )

        assertEquals(1_095, initial.size)
        assertEquals(TimeUnit.HOURS.toMillis(1), initial.first().coverMillis)
        assertEquals(TimeUnit.HOURS.toMillis(1) * 1_095L, initial.sumOf { it.coverMillis })

        database.usageEventDao().insertEvents(eventsForDay(initialEnd))
        val nextEnd = initialEnd + TimeUnit.DAYS.toMillis(1)
        val extended = repository.ensureUpToDate(
            calibration = Calibration(cover = configuration),
            syncedThroughMillis = nextEnd,
            syncQueryBeginMillis = initialEnd - TimeUnit.HOURS.toMillis(1),
            checkpointRevision = 0L,
            zoneId = zoneId,
            collectionGapStarts = emptyList(),
        )

        assertEquals(1_096, extended.size)
        assertEquals(initial.first(), extended.first())
        assertEquals(TimeUnit.HOURS.toMillis(1), extended.last().coverMillis)
    }

    @Test
    fun repeatedSameMillisecondEventsSurviveRoomRoundTripAndOverlap() = runBlocking {
        val records = listOf(
            usageRecord(
                timestampMillis = 0L,
                sequence = 0,
                kind = UsageEventKind.CONFIGURATION_CHANGED,
                rawEventType = UsageEvents.Event.CONFIGURATION_CHANGE,
                eventConfiguration = configuration,
            ),
            usageRecord(
                timestampMillis = 0L,
                sequence = 1,
                kind = UsageEventKind.SCREEN_INTERACTIVE,
                rawEventType = UsageEvents.Event.SCREEN_INTERACTIVE,
            ),
            usageRecord(
                timestampMillis = 0L,
                sequence = 2,
                kind = UsageEventKind.KEYGUARD_HIDDEN,
                rawEventType = UsageEvents.Event.KEYGUARD_HIDDEN,
            ),
            usageRecord(
                timestampMillis = 1_000L,
                sequence = 0,
                kind = UsageEventKind.ACTIVITY_RESUMED,
                rawEventType = UsageEvents.Event.ACTIVITY_RESUMED,
                packageName = "app.example",
            ),
            usageRecord(
                timestampMillis = 1_000L,
                sequence = 1,
                kind = UsageEventKind.ACTIVITY_PAUSED,
                rawEventType = UsageEvents.Event.ACTIVITY_PAUSED,
                packageName = "app.example",
            ),
            usageRecord(
                timestampMillis = 1_000L,
                sequence = 2,
                kind = UsageEventKind.ACTIVITY_RESUMED,
                rawEventType = UsageEvents.Event.ACTIVITY_RESUMED,
                packageName = "app.example",
            ),
        )
        val store = RoomUsageEventStore(database.usageEventDao())

        val firstInserted = store.persistSuccessfulSync(
            records = records,
            state = UsageSyncState(2_000L, 2_000L, 0L, 0),
            attempt = SyncAttempt(
                attemptedAtMillis = 2_000L,
                queryBeginMillis = 0L,
                queryEndMillis = 2_000L,
                status = SyncAttemptStatus.SUCCESS,
                readEventCount = records.size,
            ),
        )
        val secondInserted = store.persistSuccessfulSync(
            records = records.map {
                it.copy(sequenceAtTimestamp = it.sequenceAtTimestamp + 4)
            },
            state = UsageSyncState(3_000L, 3_000L, 0L, 0),
            attempt = SyncAttempt(
                attemptedAtMillis = 3_000L,
                queryBeginMillis = 0L,
                queryEndMillis = 3_000L,
                status = SyncAttemptStatus.SUCCESS,
                readEventCount = records.size,
            ),
        )
        val roundTripped = store.loadRecordsForAnalysis(0L, 2_000L)
        val analysis = UsageAnalyzer(packageLabel = { it }).analyze(
            records = roundTripped,
            rangeStartMillis = 0L,
            rangeEndMillis = 2_000L,
            calibration = Calibration(cover = configuration),
            zoneId = zoneId,
        )

        assertEquals(6, firstInserted)
        assertEquals(0, secondInserted)
        assertEquals(6, roundTripped.size)
        assertEquals(
            listOf(0, 1, 2),
            roundTripped.filter { it.timestampMillis == 1_000L }
                .map(UsageRecord::sequenceAtTimestamp),
        )
        assertEquals(1_000L, analysis.apps.single().coverMillis)
    }

    @Test
    fun activitySeedPreservesSameClassDuplicateAmbiguityForSessionAttribution() = runBlocking {
        val begin = 10_000L
        val end = 16_000L
        database.usageEventDao().insertEvents(
            listOf(
                event(
                    key = "seed-cover",
                    timestampMillis = 0L,
                    sequence = 0,
                    rawEventType = UsageEvents.Event.CONFIGURATION_CHANGE,
                    eventConfiguration = configuration,
                ),
                event(
                    key = "seed-screen-on",
                    timestampMillis = 0L,
                    sequence = 1,
                    rawEventType = UsageEvents.Event.SCREEN_INTERACTIVE,
                ),
                event(
                    key = "seed-unlocked",
                    timestampMillis = 0L,
                    sequence = 2,
                    rawEventType = UsageEvents.Event.KEYGUARD_HIDDEN,
                ),
                event(
                    key = "seed-app-a-resumed-1",
                    timestampMillis = 1_000L,
                    sequence = 0,
                    rawEventType = UsageEvents.Event.ACTIVITY_RESUMED,
                    packageName = "app.a",
                ),
                event(
                    key = "seed-app-a-resumed-2",
                    timestampMillis = 2_000L,
                    sequence = 0,
                    rawEventType = UsageEvents.Event.ACTIVITY_RESUMED,
                    packageName = "app.a",
                ),
                event(
                    key = "app-a-paused",
                    timestampMillis = begin + 1_000L,
                    sequence = 0,
                    rawEventType = UsageEvents.Event.ACTIVITY_PAUSED,
                    packageName = "app.a",
                ),
                event(
                    key = "app-b-resumed",
                    timestampMillis = begin + 2_000L,
                    sequence = 0,
                    rawEventType = UsageEvents.Event.ACTIVITY_RESUMED,
                    packageName = "app.b",
                ),
                event(
                    key = "opened",
                    timestampMillis = begin + 3_000L,
                    sequence = 0,
                    rawEventType = UsageEvents.Event.CONFIGURATION_CHANGE,
                    eventConfiguration = innerConfiguration,
                ),
                event(
                    key = "closed",
                    timestampMillis = begin + 5_000L,
                    sequence = 0,
                    rawEventType = UsageEvents.Event.CONFIGURATION_CHANGE,
                    eventConfiguration = configuration,
                ),
            ),
        )
        val loadedEvents = database.usageEventDao()
            .loadUsageEventsForAnalysis(begin, end)
        val records = loadedEvents.map(UsageEventEntity::toModel)
        val analyzer = InnerDisplaySessionAnalyzer(
            calibration = Calibration(cover = configuration, inner = innerConfiguration),
            analysisStartMillis = begin,
        )

        analyzer.processChunk(
            records = records,
            checkpoints = emptyList(),
            collectionGapStarts = emptyList(),
            chunkEndMillis = end,
        )

        assertEquals(
            listOf(
                "seed-app-a-resumed-1",
                "seed-app-a-resumed-2",
                "app-a-paused",
                "app-b-resumed",
            ),
            loadedEvents.filter { it.packageName != null }.map(UsageEventEntity::eventKey),
        )
        val session = analyzer.sessionsAtEnd().single()
        assertEquals(2_000L, session.innerActiveMillis)
        assertEquals(emptyMap<String, Long>(), session.appUsageMillis)
    }

    @Test
    fun activitySeedPreservesRecoveredDuplicateAmbiguityForLaterPause() = runBlocking {
        val begin = 10_000L
        val end = 16_000L
        database.usageEventDao().insertEvents(
            listOf(
                event(
                    key = "seed-cover",
                    timestampMillis = 0L,
                    sequence = 0,
                    rawEventType = UsageEvents.Event.CONFIGURATION_CHANGE,
                    eventConfiguration = configuration,
                ),
                event(
                    key = "seed-screen-on",
                    timestampMillis = 0L,
                    sequence = 1,
                    rawEventType = UsageEvents.Event.SCREEN_INTERACTIVE,
                ),
                event(
                    key = "seed-unlocked",
                    timestampMillis = 0L,
                    sequence = 2,
                    rawEventType = UsageEvents.Event.KEYGUARD_HIDDEN,
                ),
                event(
                    key = "seed-app-a-resumed-1",
                    timestampMillis = 1_000L,
                    sequence = 0,
                    rawEventType = UsageEvents.Event.ACTIVITY_RESUMED,
                    packageName = "app.a",
                ),
                event(
                    key = "seed-app-a-resumed-2",
                    timestampMillis = 2_000L,
                    sequence = 0,
                    rawEventType = UsageEvents.Event.ACTIVITY_RESUMED,
                    packageName = "app.a",
                ),
                event(
                    key = "seed-app-a-paused",
                    timestampMillis = 3_000L,
                    sequence = 0,
                    rawEventType = UsageEvents.Event.ACTIVITY_PAUSED,
                    packageName = "app.a",
                ),
                event(
                    key = "seed-app-a-resumed-3",
                    timestampMillis = 4_000L,
                    sequence = 0,
                    rawEventType = UsageEvents.Event.ACTIVITY_RESUMED,
                    packageName = "app.a",
                ),
                event(
                    key = "app-a-paused-final",
                    timestampMillis = begin + 1_000L,
                    sequence = 0,
                    rawEventType = UsageEvents.Event.ACTIVITY_PAUSED,
                    packageName = "app.a",
                ),
                event(
                    key = "app-b-resumed",
                    timestampMillis = begin + 2_000L,
                    sequence = 0,
                    rawEventType = UsageEvents.Event.ACTIVITY_RESUMED,
                    packageName = "app.b",
                ),
                event(
                    key = "opened",
                    timestampMillis = begin + 3_000L,
                    sequence = 0,
                    rawEventType = UsageEvents.Event.CONFIGURATION_CHANGE,
                    eventConfiguration = innerConfiguration,
                ),
                event(
                    key = "closed",
                    timestampMillis = begin + 5_000L,
                    sequence = 0,
                    rawEventType = UsageEvents.Event.CONFIGURATION_CHANGE,
                    eventConfiguration = configuration,
                ),
            ),
        )
        val loadedEvents = database.usageEventDao()
            .loadUsageEventsForAnalysis(begin, end)
        val analyzer = InnerDisplaySessionAnalyzer(
            calibration = Calibration(cover = configuration, inner = innerConfiguration),
            analysisStartMillis = begin,
        )

        analyzer.processChunk(
            records = loadedEvents.map(UsageEventEntity::toModel),
            checkpoints = emptyList(),
            collectionGapStarts = emptyList(),
            chunkEndMillis = end,
        )

        assertEquals(
            listOf(
                "seed-app-a-resumed-1",
                "seed-app-a-resumed-2",
                "seed-app-a-paused",
                "seed-app-a-resumed-3",
                "app-a-paused-final",
                "app-b-resumed",
            ),
            loadedEvents.filter { it.packageName != null }.map(UsageEventEntity::eventKey),
        )
        val session = analyzer.sessionsAtEnd().single()
        assertEquals(2_000L, session.innerActiveMillis)
        assertEquals(emptyMap<String, Long>(), session.appUsageMillis)
    }

    @Test
    fun activitySeedPreservesPausedPredecessorAmbiguityForLaterStop() = runBlocking {
        val begin = 10_000L
        val end = 16_000L
        database.usageEventDao().insertEvents(
            listOf(
                event(
                    key = "seed-cover",
                    timestampMillis = 0L,
                    sequence = 0,
                    rawEventType = UsageEvents.Event.CONFIGURATION_CHANGE,
                    eventConfiguration = configuration,
                ),
                event(
                    key = "seed-screen-on",
                    timestampMillis = 0L,
                    sequence = 1,
                    rawEventType = UsageEvents.Event.SCREEN_INTERACTIVE,
                ),
                event(
                    key = "seed-unlocked",
                    timestampMillis = 0L,
                    sequence = 2,
                    rawEventType = UsageEvents.Event.KEYGUARD_HIDDEN,
                ),
                event(
                    key = "seed-app-a-resumed-1",
                    timestampMillis = 1_000L,
                    sequence = 0,
                    rawEventType = UsageEvents.Event.ACTIVITY_RESUMED,
                    packageName = "app.a",
                ),
                event(
                    key = "seed-app-a-paused",
                    timestampMillis = 2_000L,
                    sequence = 0,
                    rawEventType = UsageEvents.Event.ACTIVITY_PAUSED,
                    packageName = "app.a",
                ),
                event(
                    key = "seed-app-a-resumed-2",
                    timestampMillis = 3_000L,
                    sequence = 0,
                    rawEventType = UsageEvents.Event.ACTIVITY_RESUMED,
                    packageName = "app.a",
                ),
                event(
                    key = "app-a-stopped",
                    timestampMillis = begin + 1_000L,
                    sequence = 0,
                    rawEventType = UsageEvents.Event.ACTIVITY_STOPPED,
                    packageName = "app.a",
                ),
                event(
                    key = "app-b-resumed",
                    timestampMillis = begin + 2_000L,
                    sequence = 0,
                    rawEventType = UsageEvents.Event.ACTIVITY_RESUMED,
                    packageName = "app.b",
                ),
                event(
                    key = "opened",
                    timestampMillis = begin + 3_000L,
                    sequence = 0,
                    rawEventType = UsageEvents.Event.CONFIGURATION_CHANGE,
                    eventConfiguration = innerConfiguration,
                ),
                event(
                    key = "closed",
                    timestampMillis = begin + 5_000L,
                    sequence = 0,
                    rawEventType = UsageEvents.Event.CONFIGURATION_CHANGE,
                    eventConfiguration = configuration,
                ),
            ),
        )
        val loadedEvents = database.usageEventDao()
            .loadUsageEventsForAnalysis(begin, end)
        val analyzer = InnerDisplaySessionAnalyzer(
            calibration = Calibration(cover = configuration, inner = innerConfiguration),
            analysisStartMillis = begin,
        )

        analyzer.processChunk(
            records = loadedEvents.map(UsageEventEntity::toModel),
            checkpoints = emptyList(),
            collectionGapStarts = emptyList(),
            chunkEndMillis = end,
        )

        assertEquals(
            listOf(
                "seed-app-a-resumed-1",
                "seed-app-a-paused",
                "seed-app-a-resumed-2",
                "app-a-stopped",
                "app-b-resumed",
            ),
            loadedEvents.filter { it.packageName != null }.map(UsageEventEntity::eventKey),
        )
        val session = analyzer.sessionsAtEnd().single()
        assertEquals(2_000L, session.innerActiveMillis)
        assertEquals(emptyMap<String, Long>(), session.appUsageMillis)
    }

    @Test
    fun multipleBackgroundSyncsRebuildEarliestOverlapAndMatchFullRebuild() = runBlocking {
        val firstDayStart = LocalDate.of(2026, 1, 1)
            .atStartOfDay(zoneId)
            .toInstant()
            .toEpochMilli()
        val baseline = firstDayStart + TimeUnit.HOURS.toMillis(23L)
        val initialEnd = firstDayStart + TimeUnit.DAYS.toMillis(1L) +
            TimeUnit.MINUTES.toMillis(30L)
        val firstBackgroundEnd = initialEnd + TimeUnit.HOURS.toMillis(6L)
        val secondBackgroundEnd = firstBackgroundEnd + TimeUnit.HOURS.toMillis(6L)
        val dao = database.usageEventDao()
        dao.persistSuccessfulSync(
            events = listOf(
                event(
                    key = "baseline-configuration",
                    timestampMillis = baseline,
                    sequence = 0,
                    rawEventType = UsageEvents.Event.CONFIGURATION_CHANGE,
                    withConfiguration = true,
                ),
                event(
                    key = "baseline-screen-on",
                    timestampMillis = baseline,
                    sequence = 1,
                    rawEventType = UsageEvents.Event.SCREEN_INTERACTIVE,
                ),
                event(
                    key = "baseline-unlocked",
                    timestampMillis = baseline,
                    sequence = 2,
                    rawEventType = UsageEvents.Event.KEYGUARD_HIDDEN,
                ),
            ),
            state = syncState(initialEnd, baseline),
            attempt = successfulAttempt(initialEnd, baseline, readEventCount = 3),
        )
        val repository = repository()
        val initial = repository.ensureUpToDate(
            calibration = Calibration(cover = configuration),
            syncedThroughMillis = initialEnd,
            syncQueryBeginMillis = baseline,
            checkpointRevision = 0L,
            zoneId = zoneId,
            collectionGapStarts = emptyList(),
        )
        assertEquals(
            listOf(TimeUnit.HOURS.toMillis(1L), TimeUnit.MINUTES.toMillis(30L)),
            initial.map { it.coverMillis },
        )

        val firstQueryBegin = firstDayStart + TimeUnit.HOURS.toMillis(23L) +
            TimeUnit.MINUTES.toMillis(30L)
        dao.persistSuccessfulSync(
            events = listOf(
                event(
                    key = "late-screen-off",
                    timestampMillis = firstDayStart + TimeUnit.HOURS.toMillis(23L) +
                        TimeUnit.MINUTES.toMillis(45L),
                    sequence = 0,
                    rawEventType = UsageEvents.Event.SCREEN_NON_INTERACTIVE,
                ),
            ),
            state = syncState(firstBackgroundEnd, firstQueryBegin),
            attempt = successfulAttempt(
                firstBackgroundEnd,
                firstQueryBegin,
                readEventCount = 1,
            ),
        )
        val lastQueryBegin = firstDayStart + TimeUnit.DAYS.toMillis(1L) +
            TimeUnit.HOURS.toMillis(5L) + TimeUnit.MINUTES.toMillis(30L)
        dao.persistSuccessfulSync(
            events = emptyList(),
            state = syncState(secondBackgroundEnd, lastQueryBegin),
            attempt = successfulAttempt(
                secondBackgroundEnd,
                lastQueryBegin,
                readEventCount = 0,
            ),
        )

        val incremental = repository.ensureUpToDate(
            calibration = Calibration(cover = configuration),
            syncedThroughMillis = secondBackgroundEnd,
            syncQueryBeginMillis = lastQueryBegin,
            checkpointRevision = 0L,
            zoneId = zoneId,
            collectionGapStarts = emptyList(),
        )
        assertEquals(listOf(TimeUnit.MINUTES.toMillis(45L)), incremental.map { it.coverMillis })
        val incrementalState = requireNotNull(database.dailyPostureSummaryDao().loadState())
        assertEquals(3L, incrementalState.lastAggregatedSyncHistoryId)

        database.dailyPostureSummaryDao().upsertState(
            incrementalState.copy(aggregationVersion = 0),
        )
        val full = repository.ensureUpToDate(
            calibration = Calibration(cover = configuration),
            syncedThroughMillis = secondBackgroundEnd,
            syncQueryBeginMillis = lastQueryBegin,
            checkpointRevision = 0L,
            zoneId = zoneId,
            collectionGapStarts = emptyList(),
        )

        assertEquals(full, incremental)
        assertEquals(8, database.dailyPostureSummaryDao().loadState()?.aggregationVersion)
    }

    @Test
    fun carriesActiveAppStateAcrossAggregationChunks() = runBlocking {
        val start = LocalDate.of(2024, 1, 1)
            .atStartOfDay(zoneId)
            .toInstant()
            .toEpochMilli()
        val end = LocalDate.of(2024, 2, 2)
            .atStartOfDay(zoneId)
            .toInstant()
            .toEpochMilli()
        database.usageEventDao().insertEvents(
            listOf(
                event(
                    key = "configuration",
                    timestampMillis = start,
                    sequence = 0,
                    rawEventType = UsageEvents.Event.CONFIGURATION_CHANGE,
                    withConfiguration = true,
                ),
                event(
                    key = "screen-on",
                    timestampMillis = start,
                    sequence = 1,
                    rawEventType = UsageEvents.Event.SCREEN_INTERACTIVE,
                ),
                event(
                    key = "unlocked",
                    timestampMillis = start,
                    sequence = 2,
                    rawEventType = UsageEvents.Event.KEYGUARD_HIDDEN,
                ),
                event(
                    key = "app-resumed",
                    timestampMillis = start,
                    sequence = 3,
                    rawEventType = UsageEvents.Event.ACTIVITY_RESUMED,
                    packageName = "app.example",
                ),
            ),
        )
        val repository = DailySummaryRepository(
            usageEventDao = database.usageEventDao(),
            checkpointDao = database.postureCheckpointDao(),
            summaryDao = database.dailyPostureSummaryDao(),
        )

        val appUsage = repository.withUpToDateSnapshot(
            calibration = Calibration(cover = configuration),
            syncedThroughMillis = end,
            syncQueryBeginMillis = start,
            checkpointRevision = 0L,
            zoneId = zoneId,
            collectionGapStarts = emptyList(),
        ) {
            loadAggregatedAppUsage(start, end).single()
        }

        assertEquals("app.example", appUsage.packageName)
        assertEquals(end - start, appUsage.coverMillis)
        assertEquals(0L, appUsage.innerMillis)
    }

    @Test
    fun incrementalAggregationPreservesDuplicateActivityAmbiguityAcrossRebuildStart() =
        runBlocking {
            val start = LocalDate.of(2024, 1, 1)
                .atStartOfDay(zoneId)
                .toInstant()
                .toEpochMilli()
            val rebuildStart = LocalDate.of(2024, 4, 3)
                .atStartOfDay(zoneId)
                .toInstant()
                .toEpochMilli()
            val end = rebuildStart + 6_000L
            val calibration = Calibration(cover = configuration, inner = innerConfiguration)
            database.usageEventDao().insertEvents(
                listOf(
                    event(
                        key = "cover",
                        timestampMillis = start,
                        sequence = 0,
                        rawEventType = UsageEvents.Event.CONFIGURATION_CHANGE,
                        eventConfiguration = configuration,
                    ),
                    event(
                        key = "screen-on",
                        timestampMillis = start,
                        sequence = 1,
                        rawEventType = UsageEvents.Event.SCREEN_INTERACTIVE,
                    ),
                    event(
                        key = "unlocked",
                        timestampMillis = start,
                        sequence = 2,
                        rawEventType = UsageEvents.Event.KEYGUARD_HIDDEN,
                    ),
                    event(
                        key = "app-a-resumed-1",
                        timestampMillis = start + 1_000L,
                        sequence = 0,
                        rawEventType = UsageEvents.Event.ACTIVITY_RESUMED,
                        packageName = "app.a",
                    ),
                    event(
                        key = "app-a-resumed-2",
                        timestampMillis = start + 2_000L,
                        sequence = 0,
                        rawEventType = UsageEvents.Event.ACTIVITY_RESUMED,
                        packageName = "app.a",
                    ),
                    event(
                        key = "app-a-paused",
                        timestampMillis = rebuildStart + 1_000L,
                        sequence = 0,
                        rawEventType = UsageEvents.Event.ACTIVITY_PAUSED,
                        packageName = "app.a",
                    ),
                    event(
                        key = "app-b-resumed",
                        timestampMillis = rebuildStart + 2_000L,
                        sequence = 0,
                        rawEventType = UsageEvents.Event.ACTIVITY_RESUMED,
                        packageName = "app.b",
                    ),
                    event(
                        key = "opened",
                        timestampMillis = rebuildStart + 3_000L,
                        sequence = 0,
                        rawEventType = UsageEvents.Event.CONFIGURATION_CHANGE,
                        eventConfiguration = innerConfiguration,
                    ),
                    event(
                        key = "closed",
                        timestampMillis = rebuildStart + 5_000L,
                        sequence = 0,
                        rawEventType = UsageEvents.Event.CONFIGURATION_CHANGE,
                        eventConfiguration = configuration,
                    ),
                ),
            )
            database.dailyPostureSummaryDao().upsertState(
                DailySummaryStateEntity(
                    lastAggregatedThroughMillis = rebuildStart,
                    calibrationKey = calibration.dailySummaryCacheKey(),
                    zoneId = zoneId.id,
                    checkpointRevision = 0L,
                    aggregationVersion = 8,
                ),
            )

            val incrementalSessions = repository().withUpToDateSnapshot(
                calibration = calibration,
                syncedThroughMillis = end,
                syncQueryBeginMillis = rebuildStart,
                checkpointRevision = 0L,
                zoneId = zoneId,
                collectionGapStarts = emptyList(),
            ) {
                loadCompleteInnerSessions(start, end)
            }
            database.dailyPostureSummaryDao().upsertState(
                requireNotNull(database.dailyPostureSummaryDao().loadState())
                    .copy(aggregationVersion = 0),
            )
            val fullSessions = repository().withUpToDateSnapshot(
                calibration = calibration,
                syncedThroughMillis = end,
                syncQueryBeginMillis = rebuildStart,
                checkpointRevision = 0L,
                zoneId = zoneId,
                collectionGapStarts = emptyList(),
            ) {
                loadCompleteInnerSessions(start, end)
            }

            assertEquals(1, incrementalSessions.size)
            assertEquals(2_000L, incrementalSessions.single().innerActiveMillis)
            assertEquals(emptyMap<String, Long>(), incrementalSessions.single().appUsageMillis)
            assertEquals(fullSessions, incrementalSessions)
        }

    @Test
    fun incrementalAggregationPreservesRecoveredDuplicateAmbiguityAcrossRebuildStart() =
        runBlocking {
            assertIncrementalAmbiguousSessionMatchesFullRebuild(
                seedActivityEvents = { start ->
                    listOf(
                        event(
                            key = "app-a-resumed-1",
                            timestampMillis = start + 1_000L,
                            sequence = 0,
                            rawEventType = UsageEvents.Event.ACTIVITY_RESUMED,
                            packageName = "app.a",
                        ),
                        event(
                            key = "app-a-resumed-2",
                            timestampMillis = start + 2_000L,
                            sequence = 0,
                            rawEventType = UsageEvents.Event.ACTIVITY_RESUMED,
                            packageName = "app.a",
                        ),
                        event(
                            key = "app-a-paused",
                            timestampMillis = start + 3_000L,
                            sequence = 0,
                            rawEventType = UsageEvents.Event.ACTIVITY_PAUSED,
                            packageName = "app.a",
                        ),
                        event(
                            key = "app-a-resumed-3",
                            timestampMillis = start + 4_000L,
                            sequence = 0,
                            rawEventType = UsageEvents.Event.ACTIVITY_RESUMED,
                            packageName = "app.a",
                        ),
                    )
                },
                terminalEvent = { rebuildStart ->
                    event(
                        key = "app-a-paused-final",
                        timestampMillis = rebuildStart + 1_000L,
                        sequence = 0,
                        rawEventType = UsageEvents.Event.ACTIVITY_PAUSED,
                        packageName = "app.a",
                    )
                },
            )
        }

    @Test
    fun incrementalAggregationPreservesPausedPredecessorStopAcrossRebuildStart() =
        runBlocking {
            assertIncrementalAmbiguousSessionMatchesFullRebuild(
                seedActivityEvents = { start ->
                    listOf(
                        event(
                            key = "app-a-resumed-1",
                            timestampMillis = start + 1_000L,
                            sequence = 0,
                            rawEventType = UsageEvents.Event.ACTIVITY_RESUMED,
                            packageName = "app.a",
                        ),
                        event(
                            key = "app-a-paused",
                            timestampMillis = start + 2_000L,
                            sequence = 0,
                            rawEventType = UsageEvents.Event.ACTIVITY_PAUSED,
                            packageName = "app.a",
                        ),
                        event(
                            key = "app-a-resumed-2",
                            timestampMillis = start + 3_000L,
                            sequence = 0,
                            rawEventType = UsageEvents.Event.ACTIVITY_RESUMED,
                            packageName = "app.a",
                        ),
                    )
                },
                terminalEvent = { rebuildStart ->
                    event(
                        key = "app-a-stopped",
                        timestampMillis = rebuildStart + 1_000L,
                        sequence = 0,
                        rawEventType = UsageEvents.Event.ACTIVITY_STOPPED,
                        packageName = "app.a",
                    )
                },
            )
        }

    @Test
    fun deviceStateBaselineStartsAtObservationAndCarriesAcrossAggregationChunks() = runBlocking {
        val start = LocalDate.of(2024, 1, 1)
            .atStartOfDay(zoneId)
            .toInstant()
            .toEpochMilli()
        val observation = LocalDate.of(2024, 4, 2)
            .atTime(12, 0)
            .atZone(zoneId)
            .toInstant()
            .toEpochMilli()
        val end = LocalDate.of(2024, 4, 4)
            .atStartOfDay(zoneId)
            .toInstant()
            .toEpochMilli()
        database.usageEventDao().insertEvents(
            listOf(
                event(
                    key = "baseline-configuration",
                    timestampMillis = start,
                    sequence = 0,
                    rawEventType = UsageEvents.Event.CONFIGURATION_CHANGE,
                    withConfiguration = true,
                ),
                event(
                    key = "baseline-app-resumed",
                    timestampMillis = start,
                    sequence = 1,
                    rawEventType = UsageEvents.Event.ACTIVITY_RESUMED,
                    packageName = "app.example",
                ),
            ),
        )
        database.usageEventDao().insertSyncHistory(
            SyncHistoryEntity(
                attemptedAtMillis = observation + 1L,
                queryBeginMillis = start,
                queryEndMillis = observation + 1L,
                status = SyncAttemptStatus.SUCCESS.name,
                readEventCount = 2,
                insertedEventCount = 2,
                deviceStateObservedAtMillis = observation,
                screenInteractive = true,
                keyguardHidden = true,
            ),
        )

        val repository = repository()
        val aggregation = repository.withUpToDateSnapshot(
            calibration = Calibration(cover = configuration),
            syncedThroughMillis = end,
            syncQueryBeginMillis = start,
            checkpointRevision = 0L,
            zoneId = zoneId,
            collectionGapStarts = emptyList(),
        ) {
            dailySummaries to loadAggregatedAppUsage(start, end).single()
        }
        val summaries = aggregation.first
        val appUsage = aggregation.second

        assertEquals(2, summaries.size)
        assertEquals(
            LocalDate.of(2024, 4, 2).atStartOfDay(zoneId).toInstant().toEpochMilli(),
            summaries[0].dayStartMillis,
        )
        assertEquals(TimeUnit.HOURS.toMillis(12), summaries[0].coverMillis)
        assertEquals(TimeUnit.HOURS.toMillis(24), summaries[1].coverMillis)
        assertEquals(TimeUnit.HOURS.toMillis(36), summaries.sumOf { it.coverMillis })
        assertEquals(TimeUnit.HOURS.toMillis(36), appUsage.coverMillis)
        assertEquals(0L, summaries.sumOf { it.innerMillis })
        assertEquals(0, summaries.sumOf { it.openedCount + it.closedCount })
    }

    @Test
    fun startupBeforeChunkBoundaryInvalidatesAllEarlierSeedState() = runBlocking {
        val start = LocalDate.of(2024, 1, 1)
            .atStartOfDay(zoneId)
            .toInstant()
            .toEpochMilli()
        val startup = LocalDate.of(2024, 4, 2)
            .atStartOfDay(zoneId)
            .toInstant()
            .toEpochMilli()
        val laterConfiguration = LocalDate.of(2024, 4, 2)
            .atTime(12, 0)
            .atZone(zoneId)
            .toInstant()
            .toEpochMilli()
        val end = LocalDate.of(2024, 4, 4)
            .atStartOfDay(zoneId)
            .toInstant()
            .toEpochMilli()
        database.usageEventDao().insertEvents(
            listOf(
                event(
                    key = "reset-configuration-before",
                    timestampMillis = start,
                    sequence = 0,
                    rawEventType = UsageEvents.Event.CONFIGURATION_CHANGE,
                    withConfiguration = true,
                ),
                event(
                    key = "reset-screen-on",
                    timestampMillis = start,
                    sequence = 1,
                    rawEventType = UsageEvents.Event.SCREEN_INTERACTIVE,
                ),
                event(
                    key = "reset-unlocked",
                    timestampMillis = start,
                    sequence = 2,
                    rawEventType = UsageEvents.Event.KEYGUARD_HIDDEN,
                ),
                event(
                    key = "reset-app-resumed",
                    timestampMillis = start,
                    sequence = 3,
                    rawEventType = UsageEvents.Event.ACTIVITY_RESUMED,
                    packageName = "app.example",
                ),
                event(
                    key = "reset-startup",
                    timestampMillis = startup,
                    sequence = 0,
                    rawEventType = UsageEvents.Event.DEVICE_STARTUP,
                ),
                event(
                    key = "reset-configuration-after",
                    timestampMillis = laterConfiguration,
                    sequence = 0,
                    rawEventType = UsageEvents.Event.CONFIGURATION_CHANGE,
                    withConfiguration = true,
                ),
            ),
        )

        val summaries = repository().ensureUpToDate(
            calibration = Calibration(cover = configuration),
            syncedThroughMillis = end,
            syncQueryBeginMillis = start,
            checkpointRevision = 0L,
            zoneId = zoneId,
            collectionGapStarts = emptyList(),
        )

        assertEquals(TimeUnit.DAYS.toMillis(92), summaries.sumOf { it.coverMillis })
        assertEquals(0L, summaries.sumOf { it.innerMillis })
        assertEquals(0L, summaries.filter { it.dayStartMillis >= startup }.sumOf { it.coverMillis })
    }

    @Test
    fun carriesInnerSessionAndDeviceStateAcrossNinetyThreeDayAggregationChunk() = runBlocking {
        val start = LocalDate.of(2024, 1, 1)
            .atStartOfDay(zoneId)
            .toInstant()
            .toEpochMilli()
        val chunkBoundary = LocalDate.of(2024, 4, 3)
            .atStartOfDay(zoneId)
            .toInstant()
            .toEpochMilli()
        val end = chunkBoundary + 2_000L
        database.usageEventDao().insertEvents(
            listOf(
                event(
                    key = "cover",
                    timestampMillis = start,
                    sequence = 0,
                    rawEventType = UsageEvents.Event.CONFIGURATION_CHANGE,
                    eventConfiguration = configuration,
                ),
                event(
                    key = "app-resumed",
                    timestampMillis = start,
                    sequence = 1,
                    rawEventType = UsageEvents.Event.ACTIVITY_RESUMED,
                    packageName = "app.example",
                ),
                event(
                    key = "opened",
                    timestampMillis = chunkBoundary - 1_000L,
                    sequence = 0,
                    rawEventType = UsageEvents.Event.CONFIGURATION_CHANGE,
                    eventConfiguration = innerConfiguration,
                ),
                event(
                    key = "closed",
                    timestampMillis = chunkBoundary + 1_000L,
                    sequence = 0,
                    rawEventType = UsageEvents.Event.CONFIGURATION_CHANGE,
                    eventConfiguration = configuration,
                ),
            ),
        )
        database.usageEventDao().insertSyncHistory(
            SyncHistoryEntity(
                attemptedAtMillis = start + 1L,
                queryBeginMillis = start,
                queryEndMillis = start + 1L,
                status = SyncAttemptStatus.SUCCESS.name,
                readEventCount = 2,
                insertedEventCount = 2,
                deviceStateObservedAtMillis = start,
                screenInteractive = true,
                keyguardHidden = true,
            ),
        )
        val repository = repository()

        val session = repository.withUpToDateSnapshot(
            calibration = Calibration(cover = configuration, inner = innerConfiguration),
            syncedThroughMillis = end,
            syncQueryBeginMillis = start,
            checkpointRevision = 0L,
            zoneId = zoneId,
            collectionGapStarts = emptyList(),
        ) {
            loadCompleteInnerSessions(start, end).single()
        }
        assertEquals(chunkBoundary - 1_000L, session.openedAtMillis)
        assertEquals(chunkBoundary + 1_000L, session.closedAtMillis)
        assertEquals(2_000L, session.innerActiveMillis)
        assertEquals(mapOf("app.example" to 2_000L), session.appUsageMillis)
    }

    @Test
    fun incrementalRefreshRebuildsFromEarlierIncompleteSessionStart() = runBlocking {
        val start = LocalDate.of(2024, 1, 1)
            .atStartOfDay(zoneId)
            .toInstant()
            .toEpochMilli()
        val initialEnd = LocalDate.of(2024, 4, 3)
            .atStartOfDay(zoneId)
            .toInstant()
            .toEpochMilli()
        val openedAt = initialEnd - TimeUnit.HOURS.toMillis(25L)
        val closedAt = initialEnd + 1_000L
        val extendedEnd = closedAt + 1_000L
        database.usageEventDao().insertEvents(
            listOf(
                event(
                    key = "cover",
                    timestampMillis = start,
                    sequence = 0,
                    rawEventType = UsageEvents.Event.CONFIGURATION_CHANGE,
                    eventConfiguration = configuration,
                ),
                event(
                    key = "screen-on",
                    timestampMillis = start,
                    sequence = 1,
                    rawEventType = UsageEvents.Event.SCREEN_INTERACTIVE,
                ),
                event(
                    key = "unlocked",
                    timestampMillis = start,
                    sequence = 2,
                    rawEventType = UsageEvents.Event.KEYGUARD_HIDDEN,
                ),
                event(
                    key = "resumed",
                    timestampMillis = start,
                    sequence = 3,
                    rawEventType = UsageEvents.Event.ACTIVITY_RESUMED,
                    packageName = "app.example",
                ),
                event(
                    key = "opened",
                    timestampMillis = openedAt,
                    sequence = 0,
                    rawEventType = UsageEvents.Event.CONFIGURATION_CHANGE,
                    eventConfiguration = innerConfiguration,
                ),
                event(
                    key = "closed",
                    timestampMillis = closedAt,
                    sequence = 0,
                    rawEventType = UsageEvents.Event.CONFIGURATION_CHANGE,
                    eventConfiguration = configuration,
                ),
            ),
        )
        val repository = repository()
        val calibration = Calibration(cover = configuration, inner = innerConfiguration)

        val initialSessions = repository.withUpToDateSnapshot(
            calibration = calibration,
            syncedThroughMillis = initialEnd,
            syncQueryBeginMillis = start,
            checkpointRevision = 0L,
            zoneId = zoneId,
            collectionGapStarts = emptyList(),
        ) {
            loadCompleteInnerSessions(start, initialEnd)
        }
        assertEquals(emptyList<Any>(), initialSessions)
        database.openHelper.readableDatabase.query(
            "SELECT package_name, inner_active_millis FROM inner_display_session_app_usage",
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("app.example", cursor.getString(0))
            assertEquals(initialEnd - openedAt, cursor.getLong(1))
            assertFalse(cursor.moveToNext())
        }

        val session = repository.withUpToDateSnapshot(
            calibration = calibration,
            syncedThroughMillis = extendedEnd,
            syncQueryBeginMillis = initialEnd - TimeUnit.HOURS.toMillis(1L),
            checkpointRevision = 0L,
            zoneId = zoneId,
            collectionGapStarts = emptyList(),
        ) {
            loadCompleteInnerSessions(start, extendedEnd).single()
        }
        assertEquals(openedAt, session.openedAtMillis)
        assertEquals(closedAt, session.closedAtMillis)
        assertEquals(closedAt - openedAt, session.innerActiveMillis)
        assertEquals(mapOf("app.example" to closedAt - openedAt), session.appUsageMillis)
        database.openHelper.readableDatabase.query(
            "SELECT package_name, inner_active_millis FROM inner_display_session_app_usage",
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("app.example", cursor.getString(0))
            assertEquals(closedAt - openedAt, cursor.getLong(1))
            assertFalse(cursor.moveToNext())
        }
    }

    @Test
    fun aggregationVersionChangeRebuildsAllDerivedCaches() = runBlocking {
        val start = LocalDate.of(2024, 1, 1)
            .atStartOfDay(zoneId)
            .toInstant()
            .toEpochMilli()
        val openedAt = start + 1_000L
        val closedAt = start + 2_000L
        val end = start + 3_000L
        val calibration = Calibration(cover = configuration, inner = innerConfiguration)
        database.usageEventDao().insertEvents(
            listOf(
                event(
                    key = "cover",
                    timestampMillis = start,
                    sequence = 0,
                    rawEventType = UsageEvents.Event.CONFIGURATION_CHANGE,
                    eventConfiguration = configuration,
                ),
                event(
                    key = "screen-on",
                    timestampMillis = start,
                    sequence = 1,
                    rawEventType = UsageEvents.Event.SCREEN_INTERACTIVE,
                ),
                event(
                    key = "unlocked",
                    timestampMillis = start,
                    sequence = 2,
                    rawEventType = UsageEvents.Event.KEYGUARD_HIDDEN,
                ),
                event(
                    key = "opened",
                    timestampMillis = openedAt,
                    sequence = 0,
                    rawEventType = UsageEvents.Event.CONFIGURATION_CHANGE,
                    eventConfiguration = innerConfiguration,
                ),
                event(
                    key = "closed",
                    timestampMillis = closedAt,
                    sequence = 0,
                    rawEventType = UsageEvents.Event.CONFIGURATION_CHANGE,
                    eventConfiguration = configuration,
                ),
            ),
        )
        database.dailyPostureSummaryDao().replaceAll(
            summaries = listOf(
                DailyPostureSummaryEntity(
                    dayStartMillis = start,
                    dayEndMillis = start + TimeUnit.DAYS.toMillis(1L),
                    zoneId = zoneId.id,
                    coverMillis = 999L,
                    innerMillis = 999L,
                    excludedMillis = 999L,
                    openedCount = 99,
                    closedCount = 99,
                    evidenceGapCount = 99,
                ),
            ),
            appUsage = emptyList(),
            innerSessions = listOf(
                InnerDisplaySessionEntity(
                    openedAtMillis = start + 500L,
                    openedSequenceAtTimestamp = 0,
                    closedAtMillis = null,
                    innerActiveMillis = 999L,
                ),
            ),
            state = DailySummaryStateEntity(
                lastAggregatedThroughMillis = end,
                calibrationKey =
                    "cover=443,994,443,1,420|inner=852,883,852,1,420",
                zoneId = zoneId.id,
                checkpointRevision = 0L,
                // Version 6 is the previous cache format; version 7 must rebuild it.
                aggregationVersion = 6,
            ),
        )

        val repository = repository()
        val sessions = repository.withUpToDateSnapshot(
            calibration = calibration,
            syncedThroughMillis = end,
            syncQueryBeginMillis = start,
            checkpointRevision = 0L,
            zoneId = zoneId,
            collectionGapStarts = emptyList(),
        ) {
            loadCompleteInnerSessions(start, end)
        }
        assertEquals(listOf(openedAt), sessions.map { it.openedAtMillis })
        assertEquals(1_000L, sessions.single().innerActiveMillis)
        assertEquals(8, database.dailyPostureSummaryDao().loadState()?.aggregationVersion)
        assertEquals(1, database.dailyPostureSummaryDao().loadAll().single().openedCount)
    }

    private suspend fun assertIncrementalAmbiguousSessionMatchesFullRebuild(
        seedActivityEvents: (Long) -> List<UsageEventEntity>,
        terminalEvent: (Long) -> UsageEventEntity,
    ) {
        val start = LocalDate.of(2024, 1, 1)
            .atStartOfDay(zoneId)
            .toInstant()
            .toEpochMilli()
        val rebuildStart = LocalDate.of(2024, 4, 3)
            .atStartOfDay(zoneId)
            .toInstant()
            .toEpochMilli()
        val end = rebuildStart + 6_000L
        val calibration = Calibration(cover = configuration, inner = innerConfiguration)
        database.usageEventDao().insertEvents(
            listOf(
                event(
                    key = "cover",
                    timestampMillis = start,
                    sequence = 0,
                    rawEventType = UsageEvents.Event.CONFIGURATION_CHANGE,
                    eventConfiguration = configuration,
                ),
                event(
                    key = "screen-on",
                    timestampMillis = start,
                    sequence = 1,
                    rawEventType = UsageEvents.Event.SCREEN_INTERACTIVE,
                ),
                event(
                    key = "unlocked",
                    timestampMillis = start,
                    sequence = 2,
                    rawEventType = UsageEvents.Event.KEYGUARD_HIDDEN,
                ),
            ) + seedActivityEvents(start) + listOf(
                terminalEvent(rebuildStart),
                event(
                    key = "app-b-resumed",
                    timestampMillis = rebuildStart + 2_000L,
                    sequence = 0,
                    rawEventType = UsageEvents.Event.ACTIVITY_RESUMED,
                    packageName = "app.b",
                ),
                event(
                    key = "opened",
                    timestampMillis = rebuildStart + 3_000L,
                    sequence = 0,
                    rawEventType = UsageEvents.Event.CONFIGURATION_CHANGE,
                    eventConfiguration = innerConfiguration,
                ),
                event(
                    key = "closed",
                    timestampMillis = rebuildStart + 5_000L,
                    sequence = 0,
                    rawEventType = UsageEvents.Event.CONFIGURATION_CHANGE,
                    eventConfiguration = configuration,
                ),
            ),
        )
        database.dailyPostureSummaryDao().upsertState(
            DailySummaryStateEntity(
                lastAggregatedThroughMillis = rebuildStart,
                calibrationKey = calibration.dailySummaryCacheKey(),
                zoneId = zoneId.id,
                checkpointRevision = 0L,
                aggregationVersion = 8,
            ),
        )

        val incrementalSessions = repository().withUpToDateSnapshot(
            calibration = calibration,
            syncedThroughMillis = end,
            syncQueryBeginMillis = rebuildStart,
            checkpointRevision = 0L,
            zoneId = zoneId,
            collectionGapStarts = emptyList(),
        ) {
            loadCompleteInnerSessions(start, end)
        }
        database.dailyPostureSummaryDao().upsertState(
            requireNotNull(database.dailyPostureSummaryDao().loadState())
                .copy(aggregationVersion = 0),
        )
        val fullSessions = repository().withUpToDateSnapshot(
            calibration = calibration,
            syncedThroughMillis = end,
            syncQueryBeginMillis = rebuildStart,
            checkpointRevision = 0L,
            zoneId = zoneId,
            collectionGapStarts = emptyList(),
        ) {
            loadCompleteInnerSessions(start, end)
        }

        assertEquals(1, incrementalSessions.size)
        assertEquals(2_000L, incrementalSessions.single().innerActiveMillis)
        assertEquals(emptyMap<String, Long>(), incrementalSessions.single().appUsageMillis)
        assertEquals(fullSessions, incrementalSessions)
    }

    private fun eventsForDay(dayStartMillis: Long): List<UsageEventEntity> = listOf(
        event(
            key = "$dayStartMillis-configuration",
            timestampMillis = dayStartMillis,
            sequence = 0,
            rawEventType = UsageEvents.Event.CONFIGURATION_CHANGE,
            withConfiguration = true,
        ),
        event(
            key = "$dayStartMillis-screen-on",
            timestampMillis = dayStartMillis,
            sequence = 1,
            rawEventType = UsageEvents.Event.SCREEN_INTERACTIVE,
        ),
        event(
            key = "$dayStartMillis-unlocked",
            timestampMillis = dayStartMillis,
            sequence = 2,
            rawEventType = UsageEvents.Event.KEYGUARD_HIDDEN,
        ),
        event(
            key = "$dayStartMillis-screen-off",
            timestampMillis = dayStartMillis + TimeUnit.HOURS.toMillis(1),
            sequence = 0,
            rawEventType = UsageEvents.Event.SCREEN_NON_INTERACTIVE,
        ),
    )

    private fun event(
        key: String,
        timestampMillis: Long,
        sequence: Int,
        rawEventType: Int,
        withConfiguration: Boolean = false,
        eventConfiguration: DisplayConfiguration? = null,
        packageName: String? = null,
    ) = UsageEventEntity(
        eventKey = key,
        timestampMillis = timestampMillis,
        sequenceAtTimestamp = sequence,
        rawEventType = rawEventType,
        packageName = packageName,
        className = packageName?.let { "$it.MainActivity" },
        hasConfiguration = withConfiguration || eventConfiguration != null,
        screenWidthDp = eventConfiguration?.screenWidthDp
            ?: configuration.screenWidthDp.takeIf { withConfiguration },
        screenHeightDp = eventConfiguration?.screenHeightDp
            ?: configuration.screenHeightDp.takeIf { withConfiguration },
        smallestScreenWidthDp = eventConfiguration?.smallestScreenWidthDp
            ?: configuration.smallestScreenWidthDp.takeIf { withConfiguration },
        orientation = eventConfiguration?.orientation
            ?: configuration.orientation.takeIf { withConfiguration },
        densityDpi = eventConfiguration?.densityDpi
            ?: configuration.densityDpi.takeIf { withConfiguration },
    )

    private fun usageRecord(
        timestampMillis: Long,
        sequence: Int,
        kind: UsageEventKind,
        rawEventType: Int,
        packageName: String? = null,
        eventConfiguration: DisplayConfiguration? = null,
    ) = UsageRecord(
        timestampMillis = timestampMillis,
        kind = kind,
        packageName = packageName,
        className = packageName?.let { "$it.MainActivity" },
        configuration = eventConfiguration,
        rawEventType = rawEventType,
        sequenceAtTimestamp = sequence,
    )

    private fun syncState(endMillis: Long, queryBeginMillis: Long) = UsageSyncStateEntity(
        lastSuccessfulEndMillis = endMillis,
        lastSuccessfulAtMillis = endMillis,
        lastQueryBeginMillis = queryBeginMillis,
        lastInsertedEventCount = 0,
    )

    private fun successfulAttempt(
        endMillis: Long,
        queryBeginMillis: Long,
        readEventCount: Int,
    ) = SyncHistoryEntity(
        attemptedAtMillis = endMillis,
        queryBeginMillis = queryBeginMillis,
        queryEndMillis = endMillis,
        status = SyncAttemptStatus.SUCCESS.name,
        readEventCount = readEventCount,
        insertedEventCount = 0,
    )

    private fun repository() = DailySummaryRepository(
        usageEventDao = database.usageEventDao(),
        checkpointDao = database.postureCheckpointDao(),
        summaryDao = database.dailyPostureSummaryDao(),
    )

    private suspend fun loadCompleteInnerSessions(
        beginMillis: Long,
        endMillis: Long,
    ) = database.dailyPostureSummaryDao().let { summaryDao ->
        val sessions = summaryDao.loadCompleteInnerSessions(beginMillis, endMillis)
        val appUsages = summaryDao.loadCompleteInnerSessionAppUsages(beginMillis, endMillis)
            .groupBy { it.openedAtMillis to it.openedSequenceAtTimestamp }
        sessions.map { session ->
            session.toModel(
                appUsages = appUsages[
                    session.openedAtMillis to session.openedSequenceAtTimestamp
                ].orEmpty(),
            )
        }
    }

}
