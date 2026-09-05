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
import com.nagopy.android.foldlytics.model.UsageRecord
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ConfigurationDeltaStorageTest {
    private lateinit var database: FoldlyticsDatabase
    private val zoneId = ZoneOffset.UTC
    private val cover = DisplayConfiguration(443, 994, 443, 1, 420)
    private val inner = DisplayConfiguration(852, 883, 852, 1, 420)
    private val emptyDelta = DisplayConfiguration(0, 0, 0, 0, 0)
    private val calibration = Calibration(cover = cover, inner = inner)
    private val start = LocalDate.of(2026, 1, 1)
        .atStartOfDay(zoneId).toInstant().toEpochMilli()

    @Before
    fun setUp() {
        val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
        database = Room.inMemoryDatabaseBuilder(context, FoldlyticsDatabase::class.java).build()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun reconstructsPreWindowDimensionsEvenWhenTheLatestConfigurationIsEmpty() = runBlocking {
        persist(
            configurationEvent(start, cover),
            configurationEvent(start + 1_000, emptyDelta.copy(screenWidthDp = 852)),
            configurationEvent(
                start + 2_000,
                emptyDelta.copy(screenHeightDp = 883, smallestScreenWidthDp = 852),
            ),
            configurationEvent(start + 3_000, emptyDelta),
            event(start + 3_500, UsageEvents.Event.SCREEN_INTERACTIVE),
            event(start + 3_501, UsageEvents.Event.KEYGUARD_HIDDEN),
        )

        val analysis = analyze(start + 4_000, start + 5_000)

        assertEquals(1_000L, analysis.innerMillis)
        assertEquals(0L, analysis.excludedPostureMillis)
        assertEquals(
            listOf(
                cover,
                emptyDelta.copy(screenWidthDp = 852),
                emptyDelta.copy(screenHeightDp = 883, smallestScreenWidthDp = 852),
                emptyDelta,
            ),
            analysis.postureEvents.asReversed().map { it.configuration },
        )
    }

    @Test
    fun preWindowRestartPreventsBorrowingEarlierDimensions() = runBlocking {
        persist(
            configurationEvent(start, inner),
            event(start + 1_000, UsageEvents.Event.DEVICE_STARTUP),
            configurationEvent(start + 2_000, emptyDelta.copy(screenWidthDp = 852)),
            configurationEvent(start + 3_000, emptyDelta),
            event(start + 3_500, UsageEvents.Event.SCREEN_INTERACTIVE),
            event(start + 3_501, UsageEvents.Event.KEYGUARD_HIDDEN),
        )

        val analysis = analyze(start + 4_000, start + 5_000)

        assertEquals(0L, analysis.innerMillis)
        assertEquals(1_000L, analysis.excludedPostureMillis)
    }

    @Test
    fun preWindowMissingConfigurationInvalidatesEarlierDimensions() = runBlocking {
        persist(
            configurationEvent(start, inner),
            configurationEvent(start + 1_000, null),
            configurationEvent(start + 2_000, emptyDelta.copy(screenWidthDp = 852)),
            configurationEvent(start + 3_000, emptyDelta),
            event(start + 3_500, UsageEvents.Event.SCREEN_INTERACTIVE),
            event(start + 3_501, UsageEvents.Event.KEYGUARD_HIDDEN),
        )

        assertEquals(1_000L, analyze(start + 4_000, start + 5_000).excludedPostureMillis)
    }

    @Test
    fun preWindowCheckpointSuppliesBaselineForLaterDeltas() = runBlocking {
        persist(
            configurationEvent(start, cover),
            configurationEvent(start + 2_000, emptyDelta),
            event(start + 3_500, UsageEvents.Event.SCREEN_INTERACTIVE),
            event(start + 3_501, UsageEvents.Event.KEYGUARD_HIDDEN),
        )
        database.postureCheckpointDao().insert(
            PostureCheckpoint(
                timestampMillis = start + 1_000,
                configuration = inner,
                source = PostureCheckpointSource.APP_FOREGROUND,
            ).toEntity(),
        )

        assertEquals(1_000L, analyze(start + 4_000, start + 5_000).innerMillis)
    }

    @Test
    fun rebuildsVersionSevenCacheAndRestoresHistoricalSessionAppBreakdown() = runBlocking {
        val end = start + 10_000
        persist(
            configurationEvent(start, cover),
            event(start + 1, UsageEvents.Event.SCREEN_INTERACTIVE),
            event(start + 2, UsageEvents.Event.KEYGUARD_HIDDEN),
            event(start + 3, UsageEvents.Event.ACTIVITY_RESUMED, packageName = "app.reader"),
            configurationEvent(start + 1_000, inner),
            configurationEvent(start + 2_000, emptyDelta),
            configurationEvent(start + 5_000, cover),
        )
        // An old cache with the same sync cursor must still be rebuilt after an app update.
        database.dailyPostureSummaryDao().upsertState(
            DailySummaryStateEntity(
                lastAggregatedThroughMillis = end,
                calibrationKey = calibration.dailySummaryCacheKey(),
                zoneId = zoneId.id,
                checkpointRevision = 0L,
                aggregationVersion = 7,
            ),
        )

        repository().withUpToDateSnapshot(
            calibration = calibration,
            syncedThroughMillis = end,
            syncQueryBeginMillis = end - 1,
            checkpointRevision = 0L,
            zoneId = zoneId,
            collectionGapStarts = emptyList(),
        ) {
            assertEquals(4_000L, dailySummaries.sumOf { it.innerMillis })
            assertEquals(0L, dailySummaries.sumOf { it.excludedMillis })
            val apps = loadAggregatedAppUsage(start, end)
            assertEquals(4_000L, apps.single().innerMillis)
            val sessions = loadCompleteInnerSessions(start, end)
            assertEquals(mapOf("app.reader" to 4_000L), sessions.single().appUsageMillis)
            val detail = InnerSessionSummarizer(packageLabel = { it }, isLauncherApp = { true })
                .summarize(sessions, start, end, detectedOpenCount = 1).longSessions.single()
            assertEquals(0L, detail.otherInnerActiveMillis)
            assertEquals("app.reader", detail.appUsages.single().packageName)
        }
        assertEquals(8, database.dailyPostureSummaryDao().loadState()?.aggregationVersion)
        // Reconstruction must leave the source delta intact for future reanalysis.
        val savedDelta = database.usageEventDao().loadEvents(start + 2_000, start + 2_001)
        assertEquals(emptyDelta, savedDelta.single().toModel().configuration)
    }

    @Test
    fun dailyAndSessionAggregationAgreeAcrossChunkBoundaryWithEmptyDelta() = runBlocking {
        val boundary = start + TimeUnit.DAYS.toMillis(93)
        val opened = boundary - 1_000
        val closed = boundary + 3_000
        val end = closed + 2_000
        persist(
            configurationEvent(start, cover),
            event(opened - 3, UsageEvents.Event.SCREEN_INTERACTIVE),
            event(opened - 2, UsageEvents.Event.KEYGUARD_HIDDEN),
            event(opened - 1, UsageEvents.Event.ACTIVITY_RESUMED, packageName = "app.reader"),
            configurationEvent(opened, inner),
            configurationEvent(boundary + 1_000, emptyDelta),
            configurationEvent(closed, cover),
        )

        repository().withUpToDateSnapshot(
            calibration = calibration,
            syncedThroughMillis = end,
            syncQueryBeginMillis = 0L,
            checkpointRevision = 0L,
            zoneId = zoneId,
            collectionGapStarts = emptyList(),
        ) {
            assertEquals(4_000L, dailySummaries.sumOf { it.innerMillis })
            assertTrue(dailySummaries.all { it.excludedMillis == 0L })
            val session = loadCompleteInnerSessions(start, end).single()
            assertEquals(4_000L, session.innerActiveMillis)
            assertEquals(mapOf("app.reader" to 4_000L), session.appUsageMillis)
        }
    }

    private suspend fun analyze(begin: Long, end: Long) = UsageAnalyzer(packageLabel = { it })
        .analyze(
            records = database.usageEventDao().loadUsageEventsForAnalysis(begin, end)
                .map(UsageEventEntity::toModel),
            rangeStartMillis = begin,
            rangeEndMillis = end,
            calibration = calibration,
            checkpoints = database.postureCheckpointDao().loadForAnalysis(begin, end)
                .map(PostureCheckpointEntity::toModel),
            zoneId = zoneId,
        )

    private suspend fun persist(vararg records: UsageRecord) {
        database.usageEventDao().insertEvents(records.toList().toEntities())
    }

    private fun configurationEvent(timestamp: Long, configuration: DisplayConfiguration?) =
        event(timestamp, UsageEvents.Event.CONFIGURATION_CHANGE, configuration)

    private fun event(
        timestamp: Long,
        type: Int,
        configuration: DisplayConfiguration? = null,
        packageName: String? = null,
    ) = UsageRecord(
        timestampMillis = timestamp,
        kind = type.toUsageEventKind(),
        rawEventType = type,
        configuration = configuration,
        packageName = packageName,
        className = packageName?.let { "$it.MainActivity" },
    )

    private fun repository() = DailySummaryRepository(
        database.usageEventDao(),
        database.postureCheckpointDao(),
        database.dailyPostureSummaryDao(),
    )
}
