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
    fun createsFreshVersionTwoDatabase() {
        assertEquals(2, database.openHelper.readableDatabase.version)
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
        packageName: String? = null,
    ) = UsageEventEntity(
        eventKey = key,
        timestampMillis = timestampMillis,
        sequenceAtTimestamp = sequence,
        rawEventType = rawEventType,
        packageName = packageName,
        className = packageName?.let { "$it.MainActivity" },
        hasConfiguration = withConfiguration,
        screenWidthDp = configuration.screenWidthDp.takeIf { withConfiguration },
        screenHeightDp = configuration.screenHeightDp.takeIf { withConfiguration },
        smallestScreenWidthDp = configuration.smallestScreenWidthDp.takeIf {
            withConfiguration
        },
        orientation = configuration.orientation.takeIf { withConfiguration },
        densityDpi = configuration.densityDpi.takeIf { withConfiguration },
    )

}
