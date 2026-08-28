package com.nagopy.android.foldlytics.data

import android.app.usage.UsageEvents
import android.content.Context
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.nagopy.android.foldlytics.model.Calibration
import com.nagopy.android.foldlytics.model.DisplayConfiguration
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
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
    fun createsFreshVersionFourDatabase() {
        assertEquals(4, database.openHelper.readableDatabase.version)
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

        repository.ensureUpToDate(
            calibration = Calibration(cover = configuration),
            syncedThroughMillis = end,
            syncQueryBeginMillis = start,
            checkpointRevision = 0L,
            zoneId = zoneId,
            collectionGapStarts = emptyList(),
        )
        val appUsage = repository.loadAggregatedAppUsage(start, end).single()

        assertEquals("app.example", appUsage.packageName)
        assertEquals(end - start, appUsage.coverMillis)
        assertEquals(0L, appUsage.innerMillis)
    }

    @Test
    fun deviceStateBaselineStartsAtObservationAndCarriesAcrossAggregationChunks() = runBlocking {
        val start = LocalDate.of(2024, 1, 1)
            .atStartOfDay(zoneId)
            .toInstant()
            .toEpochMilli()
        val observation = LocalDate.of(2024, 1, 31)
            .atTime(12, 0)
            .atZone(zoneId)
            .toInstant()
            .toEpochMilli()
        val end = LocalDate.of(2024, 2, 2)
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

        val summaries = repository().ensureUpToDate(
            calibration = Calibration(cover = configuration),
            syncedThroughMillis = end,
            syncQueryBeginMillis = start,
            checkpointRevision = 0L,
            zoneId = zoneId,
            collectionGapStarts = emptyList(),
        )
        val appUsage = repository().loadAggregatedAppUsage(start, end).single()

        assertEquals(2, summaries.size)
        assertEquals(
            LocalDate.of(2024, 1, 31).atStartOfDay(zoneId).toInstant().toEpochMilli(),
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
        val startup = LocalDate.of(2024, 1, 15)
            .atStartOfDay(zoneId)
            .toInstant()
            .toEpochMilli()
        val laterConfiguration = LocalDate.of(2024, 1, 20)
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

        assertEquals(TimeUnit.DAYS.toMillis(14), summaries.sumOf { it.coverMillis })
        assertEquals(0L, summaries.sumOf { it.innerMillis })
        assertEquals(0L, summaries.filter { it.dayStartMillis >= startup }.sumOf { it.coverMillis })
    }

    @Test
    fun carriesInnerSessionAndDeviceStateAcrossThirtyOneDayAggregationChunk() = runBlocking {
        val start = LocalDate.of(2024, 1, 1)
            .atStartOfDay(zoneId)
            .toInstant()
            .toEpochMilli()
        val chunkBoundary = LocalDate.of(2024, 2, 1)
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

        repository.ensureUpToDate(
            calibration = Calibration(cover = configuration, inner = innerConfiguration),
            syncedThroughMillis = end,
            syncQueryBeginMillis = start,
            checkpointRevision = 0L,
            zoneId = zoneId,
            collectionGapStarts = emptyList(),
        )

        val session = repository.loadCompleteInnerSessions(start, end).single()
        assertEquals(chunkBoundary - 1_000L, session.openedAtMillis)
        assertEquals(chunkBoundary + 1_000L, session.closedAtMillis)
        assertEquals(2_000L, session.innerActiveMillis)
        assertEquals("app.example", session.startPackageName)
    }

    @Test
    fun incrementalRefreshRebuildsFromEarlierIncompleteSessionStart() = runBlocking {
        val start = LocalDate.of(2024, 1, 1)
            .atStartOfDay(zoneId)
            .toInstant()
            .toEpochMilli()
        val initialEnd = LocalDate.of(2024, 2, 1)
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

        repository.ensureUpToDate(
            calibration = calibration,
            syncedThroughMillis = initialEnd,
            syncQueryBeginMillis = start,
            checkpointRevision = 0L,
            zoneId = zoneId,
            collectionGapStarts = emptyList(),
        )
        assertEquals(emptyList<Any>(), repository.loadCompleteInnerSessions(start, initialEnd))

        repository.ensureUpToDate(
            calibration = calibration,
            syncedThroughMillis = extendedEnd,
            syncQueryBeginMillis = initialEnd - TimeUnit.HOURS.toMillis(1L),
            checkpointRevision = 0L,
            zoneId = zoneId,
            collectionGapStarts = emptyList(),
        )

        val session = repository.loadCompleteInnerSessions(start, extendedEnd).single()
        assertEquals(openedAt, session.openedAtMillis)
        assertEquals(closedAt, session.closedAtMillis)
        assertEquals(closedAt - openedAt, session.innerActiveMillis)
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
                    startPackageName = "stale.app",
                ),
            ),
            state = DailySummaryStateEntity(
                lastAggregatedThroughMillis = end,
                calibrationKey =
                    "cover=443,994,443,1,420|inner=852,883,852,1,420",
                zoneId = zoneId.id,
                checkpointRevision = 0L,
                aggregationVersion = 2,
            ),
        )

        repository().ensureUpToDate(
            calibration = calibration,
            syncedThroughMillis = end,
            syncQueryBeginMillis = start,
            checkpointRevision = 0L,
            zoneId = zoneId,
            collectionGapStarts = emptyList(),
        )

        val sessions = repository().loadCompleteInnerSessions(start, end)
        assertEquals(listOf(openedAt), sessions.map { it.openedAtMillis })
        assertEquals(1_000L, sessions.single().innerActiveMillis)
        assertEquals(3, database.dailyPostureSummaryDao().loadState()?.aggregationVersion)
        assertEquals(1, database.dailyPostureSummaryDao().loadAll().single().openedCount)
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

    private fun repository() = DailySummaryRepository(
        usageEventDao = database.usageEventDao(),
        checkpointDao = database.postureCheckpointDao(),
        summaryDao = database.dailyPostureSummaryDao(),
    )

}
