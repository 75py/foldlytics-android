package com.nagopy.android.foldlytics.data

import android.app.usage.UsageEvents
import android.content.Context
import android.os.SystemClock
import androidx.room.Room
import androidx.room.withTransaction
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import com.nagopy.android.foldlytics.model.Calibration
import com.nagopy.android.foldlytics.model.DisplayConfiguration
import java.io.File
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

@LargeTest
@RunWith(AndroidJUnit4::class)
class HighDensityLongTermDatabaseTest {
    private lateinit var context: Context
    private lateinit var database: FoldlyticsDatabase
    private val zoneId = ZoneOffset.UTC
    private val coverConfiguration = DisplayConfiguration(
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
    private val calibration = Calibration(
        cover = coverConfiguration,
        inner = innerConfiguration,
    )

    @Before
    fun setUp() {
        // Instrumentation runs as the target app UID, so use its writable sandbox but a strictly
        // separate filename. Never open, delete, or migrate the production foldlytics.db file.
        context = InstrumentationRegistry.getInstrumentation().targetContext
        require(DATABASE_NAME != "foldlytics.db")
        context.deleteDatabase(DATABASE_NAME)
        context.getDatabasePath(DATABASE_NAME).parentFile?.let { databaseDirectory ->
            check(databaseDirectory.exists() || databaseDirectory.mkdirs()) {
                "Could not create isolated test database directory"
            }
        }
        metricsFile().delete()
        database = openDatabase()
    }

    @After
    fun tearDown() {
        if (::database.isInitialized) database.close()
        context.deleteDatabase(DATABASE_NAME)
        File(context.cacheDir, CSV_FILE_NAME).delete()
    }

    @Test
    fun threeYearsOfDenseUsageRemainFastAndIncrementalAfterReopen() = runBlocking {
        val firstDate = LocalDate.of(2023, 1, 1)
        val firstStart = firstDate.atStartOfDay(zoneId).toInstant().toEpochMilli()
        val initialEnd = firstDate.plusDays(INITIAL_DAYS)
            .atStartOfDay(zoneId)
            .toInstant()
            .toEpochMilli()

        val seedStarted = SystemClock.elapsedRealtime()
        val initialInsertedCount = seedHistoryInSingleTransaction(
            firstStartMillis = firstStart,
            endMillis = initialEnd,
        )
        val seedMillis = SystemClock.elapsedRealtime() - seedStarted
        val expectedInitialEventCount = INITIAL_DAYS * HOURS_PER_DAY * EVENTS_PER_HOUR
        assertEquals(expectedInitialEventCount, initialInsertedCount)
        assertEquals(expectedInitialEventCount, tableCount("usage_events"))
        assertEquals(INITIAL_SYNC_COUNT, tableCount("sync_history"))
        assertEquals(initialEnd, database.usageEventDao().loadSyncState()?.lastSuccessfulEndMillis)
        assertDuration(
            "single-transaction synthetic seed",
            seedMillis,
            MAX_SYNTHETIC_SETUP_MILLIS,
        )

        val initialAggregationStarted = SystemClock.elapsedRealtime()
        val repository = repository()
        val initialAggregation = repository.withUpToDateSnapshot(
            calibration = calibration,
            syncedThroughMillis = initialEnd,
            syncQueryBeginMillis = initialEnd - SYNC_OVERLAP_MILLIS,
            checkpointRevision = 0L,
            zoneId = zoneId,
            collectionGapStarts = emptyList(),
        ) {
            dailySummaries to loadAggregatedAppUsage(firstStart, initialEnd)
        }
        val initial = initialAggregation.first
        val initialAppUsage = initialAggregation.second
        val initialAggregationMillis =
            SystemClock.elapsedRealtime() - initialAggregationStarted
        assertEquals(INITIAL_DAYS.toInt(), initial.size)
        assertEquals(EXPECTED_COVER_MILLIS_PER_DAY, initial.first().coverMillis)
        assertEquals(EXPECTED_INNER_MILLIS_PER_DAY, initial.first().innerMillis)
        assertEquals(
            EXPECTED_COVER_MILLIS_PER_DAY * INITIAL_DAYS,
            initial.sumOf { it.coverMillis },
        )
        assertEquals(
            EXPECTED_INNER_MILLIS_PER_DAY * INITIAL_DAYS,
            initial.sumOf { it.innerMillis },
        )
        assertEquals(0L, initial.sumOf { it.excludedMillis })
        assertEquals(32, initialAppUsage.size)
        assertEquals(
            EXPECTED_APP_MILLIS_PER_POSTURE_PER_DAY * INITIAL_DAYS,
            initialAppUsage.sumOf { it.coverMillis },
        )
        assertEquals(
            EXPECTED_APP_MILLIS_PER_POSTURE_PER_DAY * INITIAL_DAYS,
            initialAppUsage.sumOf { it.innerMillis },
        )
        assertDuration(
            "three-year initial aggregation",
            initialAggregationMillis,
            MAX_FULL_AGGREGATION_MILLIS,
        )

        database.close()
        val databaseBytes = databaseFilesSize()
        assertTrue(
            "Synthetic database is unexpectedly large: $databaseBytes bytes",
            databaseBytes in 1..MAX_DATABASE_BYTES,
        )

        val reopenStarted = SystemClock.elapsedRealtime()
        database = openDatabase()
        val reopened = repository().ensureUpToDate(
            calibration = calibration,
            syncedThroughMillis = initialEnd,
            syncQueryBeginMillis = initialEnd - SYNC_OVERLAP_MILLIS,
            checkpointRevision = 0L,
            zoneId = zoneId,
            collectionGapStarts = emptyList(),
        )
        val reopenMillis = SystemClock.elapsedRealtime() - reopenStarted
        assertEquals(initial, reopened)
        assertEquals(expectedInitialEventCount, tableCount("usage_events"))
        assertDuration("database reopen and cached load", reopenMillis, MAX_CACHED_LOAD_MILLIS)

        val nextEnd = initialEnd + SYNC_INTERVAL_MILLIS
        val overlappingEvents = eventsForRange(
            beginMillis = initialEnd - SYNC_OVERLAP_MILLIS,
            endMillis = nextEnd,
            firstStartMillis = firstStart,
        )
        assertEquals(EVENTS_PER_HOUR * 7L, overlappingEvents.size.toLong())
        val syncWriteStarted = SystemClock.elapsedRealtime()
        val insertedNext = database.usageEventDao().persistSuccessfulSync(
            events = overlappingEvents,
            state = UsageSyncStateEntity(
                lastSuccessfulEndMillis = nextEnd,
                lastSuccessfulAtMillis = nextEnd,
                lastQueryBeginMillis = initialEnd - SYNC_OVERLAP_MILLIS,
                lastInsertedEventCount = 0,
            ),
            attempt = SyncHistoryEntity(
                attemptedAtMillis = nextEnd,
                queryBeginMillis = initialEnd - SYNC_OVERLAP_MILLIS,
                queryEndMillis = nextEnd,
                status = SyncAttemptStatus.SUCCESS.name,
                readEventCount = overlappingEvents.size,
                insertedEventCount = 0,
            ),
        )
        val syncWriteMillis = SystemClock.elapsedRealtime() - syncWriteStarted
        assertEquals(EVENTS_PER_SYNC.toInt(), insertedNext)
        assertEquals(expectedInitialEventCount + EVENTS_PER_SYNC, tableCount("usage_events"))
        assertEquals(INITIAL_SYNC_COUNT + 1L, tableCount("sync_history"))
        assertDuration("overlapping six-hour sync write", syncWriteMillis, MAX_SYNC_WRITE_MILLIS)

        val incrementalStarted = SystemClock.elapsedRealtime()
        val extended = repository().ensureUpToDate(
            calibration = calibration,
            syncedThroughMillis = nextEnd,
            syncQueryBeginMillis = initialEnd - SYNC_OVERLAP_MILLIS,
            checkpointRevision = 0L,
            zoneId = zoneId,
            collectionGapStarts = emptyList(),
        )
        val incrementalMillis = SystemClock.elapsedRealtime() - incrementalStarted
        assertEquals(INITIAL_DAYS.toInt() + 1, extended.size)
        assertEquals(initial, extended.take(initial.size))
        assertEquals(EXPECTED_PARTIAL_COVER_MILLIS, extended.last().coverMillis)
        assertEquals(EXPECTED_PARTIAL_INNER_MILLIS, extended.last().innerMillis)
        assertDuration("six-hour incremental aggregation", incrementalMillis, MAX_INCREMENTAL_MILLIS)

        val recalibrated = Calibration(
            cover = coverConfiguration.copy(densityDpi = coverConfiguration.densityDpi + 1),
            inner = innerConfiguration.copy(densityDpi = innerConfiguration.densityDpi + 1),
        )
        val rebuildStarted = SystemClock.elapsedRealtime()
        val rebuilt = repository().ensureUpToDate(
            calibration = recalibrated,
            syncedThroughMillis = nextEnd,
            syncQueryBeginMillis = initialEnd - SYNC_OVERLAP_MILLIS,
            checkpointRevision = 0L,
            zoneId = zoneId,
            collectionGapStarts = emptyList(),
        )
        val rebuildMillis = SystemClock.elapsedRealtime() - rebuildStarted
        assertEquals(extended, rebuilt)
        assertDuration("calibration-triggered full rebuild", rebuildMillis, MAX_FULL_AGGREGATION_MILLIS)

        val csvFile = File(context.cacheDir, CSV_FILE_NAME)
        val csvStarted = SystemClock.elapsedRealtime()
        csvFile.bufferedWriter(Charsets.UTF_8).use { writer ->
            LongTermCsvWriter.write(rebuilt, writer)
        }
        val csvMillis = SystemClock.elapsedRealtime() - csvStarted
        assertEquals(rebuilt.size + 1, csvFile.useLines { lines -> lines.count() })
        assertTrue("CSV should not be empty", csvFile.length() > 0L)
        assertDuration("full-history CSV", csvMillis, MAX_CSV_MILLIS)

        metricsFile().writeText(
            buildString {
                appendLine("days=$INITIAL_DAYS")
                appendLine("device_sessions_per_day=$HOURS_PER_DAY")
                appendLine("app_sessions_per_day=${HOURS_PER_DAY * APP_SESSIONS_PER_HOUR}")
                appendLine("events_per_day=${HOURS_PER_DAY * EVENTS_PER_HOUR}")
                appendLine("initial_unique_events=$expectedInitialEventCount")
                appendLine("initial_sync_attempts=$INITIAL_SYNC_COUNT")
                appendLine("single_transaction_seed_ms=$seedMillis")
                appendLine("initial_aggregation_ms=$initialAggregationMillis")
                appendLine("database_bytes=$databaseBytes")
                appendLine("reopen_cached_load_ms=$reopenMillis")
                appendLine("incremental_sync_write_ms=$syncWriteMillis")
                appendLine("incremental_6h_ms=$incrementalMillis")
                appendLine("calibration_full_rebuild_ms=$rebuildMillis")
                appendLine("csv_rows=${rebuilt.size}")
                appendLine("csv_bytes=${csvFile.length()}")
                appendLine("csv_write_ms=$csvMillis")
            },
            Charsets.UTF_8,
        )
    }

    private suspend fun seedHistoryInSingleTransaction(
        firstStartMillis: Long,
        endMillis: Long,
    ): Long = database.withTransaction {
        val dao = database.usageEventDao()
        var syncStart = firstStartMillis
        var syncIndex = 0L
        var insertedTotal = 0L
        while (syncStart < endMillis) {
            val syncEnd = minOf(syncStart + SYNC_INTERVAL_MILLIS, endMillis)
            val queryBegin = if (syncIndex == 0L) {
                syncStart
            } else {
                syncStart - SYNC_OVERLAP_MILLIS
            }
            val events = eventsForRange(syncStart, syncEnd, firstStartMillis)
            insertedTotal += dao.insertEvents(events).count { it != -1L }
            dao.insertSyncHistory(
                SyncHistoryEntity(
                    attemptedAtMillis = syncEnd,
                    queryBeginMillis = queryBegin,
                    queryEndMillis = syncEnd,
                    status = SyncAttemptStatus.SUCCESS.name,
                    readEventCount = events.size + if (syncIndex == 0L) {
                        0
                    } else {
                        EVENTS_PER_HOUR.toInt()
                    },
                    insertedEventCount = events.size,
                ),
            )
            syncStart = syncEnd
            syncIndex += 1L
        }
        dao.upsertSyncState(
            UsageSyncStateEntity(
                lastSuccessfulEndMillis = endMillis,
                lastSuccessfulAtMillis = endMillis,
                lastQueryBeginMillis = endMillis - SYNC_INTERVAL_MILLIS - SYNC_OVERLAP_MILLIS,
                lastInsertedEventCount = EVENTS_PER_SYNC.toInt(),
            ),
        )
        insertedTotal
    }

    private fun eventsForRange(
        beginMillis: Long,
        endMillis: Long,
        firstStartMillis: Long,
    ): List<UsageEventEntity> = buildList {
        var hourStart = beginMillis
        while (hourStart < endMillis) {
            val absoluteHour = (hourStart - firstStartMillis) / HOUR_MILLIS
            addAll(eventsForHour(hourStart, absoluteHour))
            hourStart += HOUR_MILLIS
        }
    }

    private fun eventsForHour(
        hourStartMillis: Long,
        absoluteHour: Long,
    ): List<UsageEventEntity> = buildList {
        val configuration = if (absoluteHour % 2L == 0L) {
            coverConfiguration
        } else {
            innerConfiguration
        }
        add(
            event(
                key = "$hourStartMillis-configuration",
                timestampMillis = hourStartMillis,
                sequence = 0,
                rawEventType = UsageEvents.Event.CONFIGURATION_CHANGE,
                configuration = configuration,
            ),
        )
        add(
            event(
                key = "$hourStartMillis-screen-on",
                timestampMillis = hourStartMillis,
                sequence = 1,
                rawEventType = UsageEvents.Event.SCREEN_INTERACTIVE,
            ),
        )
        add(
            event(
                key = "$hourStartMillis-unlocked",
                timestampMillis = hourStartMillis,
                sequence = 2,
                rawEventType = UsageEvents.Event.KEYGUARD_HIDDEN,
            ),
        )
        repeat(APP_SESSIONS_PER_HOUR) { appSession ->
            val resumeMillis = hourStartMillis + TimeUnit.MINUTES.toMillis(
                1L + appSession * 4L,
            )
            val packageName = "com.example.synthetic.app${(absoluteHour + appSession) % 32L}"
            add(
                event(
                    key = "$hourStartMillis-app-$appSession-resumed",
                    timestampMillis = resumeMillis,
                    sequence = 0,
                    rawEventType = UsageEvents.Event.ACTIVITY_RESUMED,
                    packageName = packageName,
                ),
            )
            add(
                event(
                    key = "$hourStartMillis-app-$appSession-paused",
                    timestampMillis = resumeMillis + TimeUnit.MINUTES.toMillis(2L),
                    sequence = 0,
                    rawEventType = UsageEvents.Event.ACTIVITY_PAUSED,
                    packageName = packageName,
                ),
            )
        }
        val screenOffMillis = hourStartMillis + TimeUnit.MINUTES.toMillis(20L)
        add(
            event(
                key = "$hourStartMillis-screen-off",
                timestampMillis = screenOffMillis,
                sequence = 0,
                rawEventType = UsageEvents.Event.SCREEN_NON_INTERACTIVE,
            ),
        )
        add(
            event(
                key = "$hourStartMillis-locked",
                timestampMillis = screenOffMillis,
                sequence = 1,
                rawEventType = UsageEvents.Event.KEYGUARD_SHOWN,
            ),
        )
    }

    private fun event(
        key: String,
        timestampMillis: Long,
        sequence: Int,
        rawEventType: Int,
        configuration: DisplayConfiguration? = null,
        packageName: String? = null,
    ) = UsageEventEntity(
        eventKey = key,
        timestampMillis = timestampMillis,
        sequenceAtTimestamp = sequence,
        rawEventType = rawEventType,
        packageName = packageName,
        className = packageName?.let { "$it.MainActivity" },
        hasConfiguration = configuration != null,
        screenWidthDp = configuration?.screenWidthDp,
        screenHeightDp = configuration?.screenHeightDp,
        smallestScreenWidthDp = configuration?.smallestScreenWidthDp,
        orientation = configuration?.orientation,
        densityDpi = configuration?.densityDpi,
    )

    private fun repository() = DailySummaryRepository(
        usageEventDao = database.usageEventDao(),
        checkpointDao = database.postureCheckpointDao(),
        summaryDao = database.dailyPostureSummaryDao(),
    )

    private fun openDatabase(): FoldlyticsDatabase =
        Room.databaseBuilder(context, FoldlyticsDatabase::class.java, DATABASE_NAME)
            .build()

    private fun tableCount(tableName: String): Long =
        database.openHelper.readableDatabase
            .query("SELECT COUNT(*) FROM $tableName")
            .use { cursor ->
                check(cursor.moveToFirst())
                cursor.getLong(0)
            }

    private fun databaseFilesSize(): Long {
        val databasePath = context.getDatabasePath(DATABASE_NAME)
        return listOf("", "-wal", "-shm")
            .sumOf { suffix -> File(databasePath.path + suffix).takeIf(File::exists)?.length() ?: 0L }
    }

    private fun metricsFile() = File(context.filesDir, METRICS_FILE_NAME)

    private fun assertDuration(label: String, actualMillis: Long, maximumMillis: Long) {
        assertTrue(
            "$label took ${actualMillis}ms; maximum is ${maximumMillis}ms",
            actualMillis <= maximumMillis,
        )
    }

    private companion object {
        const val DATABASE_NAME = "foldlytics-high-density-test.db"
        const val METRICS_FILE_NAME = "foldlytics-high-density-metrics.txt"
        const val CSV_FILE_NAME = "foldlytics-high-density.csv"
        const val INITIAL_DAYS = 1_095L
        const val HOURS_PER_DAY = 24L
        const val APP_SESSIONS_PER_HOUR = 4
        const val EVENTS_PER_HOUR = 5L + APP_SESSIONS_PER_HOUR * 2L
        const val EVENTS_PER_SYNC = EVENTS_PER_HOUR * 6L
        const val INITIAL_SYNC_COUNT = INITIAL_DAYS * 4L
        val HOUR_MILLIS = TimeUnit.HOURS.toMillis(1L)
        val SYNC_INTERVAL_MILLIS = TimeUnit.HOURS.toMillis(6L)
        val SYNC_OVERLAP_MILLIS = TimeUnit.HOURS.toMillis(1L)
        val EXPECTED_COVER_MILLIS_PER_DAY = TimeUnit.HOURS.toMillis(4L)
        val EXPECTED_INNER_MILLIS_PER_DAY = TimeUnit.HOURS.toMillis(4L)
        val EXPECTED_APP_MILLIS_PER_POSTURE_PER_DAY = TimeUnit.MINUTES.toMillis(96L)
        val EXPECTED_PARTIAL_COVER_MILLIS = TimeUnit.HOURS.toMillis(1L)
        val EXPECTED_PARTIAL_INNER_MILLIS = TimeUnit.HOURS.toMillis(1L)
        val MAX_SYNTHETIC_SETUP_MILLIS = TimeUnit.MINUTES.toMillis(2L)
        val MAX_SYNC_WRITE_MILLIS = TimeUnit.SECONDS.toMillis(2L)
        val MAX_FULL_AGGREGATION_MILLIS = TimeUnit.MINUTES.toMillis(1L)
        val MAX_CACHED_LOAD_MILLIS = TimeUnit.SECONDS.toMillis(5L)
        val MAX_INCREMENTAL_MILLIS = TimeUnit.SECONDS.toMillis(5L)
        val MAX_CSV_MILLIS = TimeUnit.SECONDS.toMillis(5L)
        const val MAX_DATABASE_BYTES = 512L * 1024L * 1024L
    }
}
