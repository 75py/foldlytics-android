package com.nagopy.android.foldlytics.data

import android.app.usage.UsageEvents
import android.content.Context
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.nagopy.android.foldlytics.model.AnalysisPeriod
import com.nagopy.android.foldlytics.model.Calibration
import com.nagopy.android.foldlytics.model.DisplayConfiguration
import java.io.StringWriter
import java.io.Writer
import java.time.LocalDate
import java.time.ZoneId
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

/**
 * Verifies that [StoredAnalysisLoader] and [LongTermCsvExporter] read real persisted history.
 *
 * Every test builds its own database rows and then constructs the loader or the exporter fresh, so
 * nothing is carried over from a screen that already ran an analysis pass in the same process.
 *
 * Each recorded day holds the same synthetic shape, observed from the day start until five hours
 * later: one hour on the cover display, three hours on the inner display, then one more hour on
 * the cover display. Two apps take turns in the foreground with the paired resume and pause
 * events a device reports, so app attribution is checkable too.
 */
@RunWith(AndroidJUnit4::class)
class StoredAnalysisRoomTest {
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

    /** The same anchors read the other way round, so a rebuild is visible in every value. */
    private val swappedCalibration = Calibration(
        cover = innerConfiguration,
        inner = coverConfiguration,
    )

    private val firstDay = LocalDate.of(2024, 5, 1)
    private val recordedDayCount = 8L
    private val syncedThroughMillis = firstDay.plusDays(recordedDayCount)
        .atStartOfDay(zoneId)
        .toInstant()
        .toEpochMilli()

    private val coverMillisPerDay = TimeUnit.HOURS.toMillis(2)
    private val innerMillisPerDay = TimeUnit.HOURS.toMillis(3)

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
    fun freshLoaderReadsShortTermResultsFromPersistedHistory() = runBlocking {
        persistRecordedHistory()

        val snapshot = loader().load(request(AnalysisPeriod.HOURS_24), zoneId)

        val analysis = requireNotNull(snapshot.analysis)
        assertEquals(dayStart(7), analysis.rangeStartMillis)
        assertEquals(syncedThroughMillis, analysis.rangeEndMillis)
        assertEquals(coverMillisPerDay, analysis.coverMillis)
        assertEquals(innerMillisPerDay, analysis.innerMillis)
        assertEquals(0L, analysis.excludedPostureMillis)
        assertEquals(1, analysis.openedCount)
        assertEquals(1, analysis.closedCount)

        val summary = requireNotNull(snapshot.periodSummary)
        assertEquals(AnalysisPeriod.HOURS_24, summary.period)
        assertEquals(coverMillisPerDay, summary.coverMillis)
        assertEquals(innerMillisPerDay, summary.innerMillis)
        assertEquals(
            mapOf(
                COVER_APP to (coverMillisPerDay to 0L),
                INNER_APP to (0L to innerMillisPerDay),
            ),
            summary.apps.associate { it.packageName to (it.coverMillis to it.innerMillis) },
        )

        val sessions = requireNotNull(snapshot.innerSessionSummary)
        assertEquals(1, sessions.completeSessionCount)
        assertEquals(innerMillisPerDay, sessions.longestInnerActiveMillis)
        assertEquals(1, sessions.detectedOpenCount)

        assertEquals(dayStart(0), snapshot.recordRangeStartMillis)
        assertEquals(syncedThroughMillis, snapshot.recordRangeEndMillis)
        assertEquals(
            setOf(
                AnalysisPeriod.HOURS_1,
                AnalysisPeriod.HOURS_6,
                AnalysisPeriod.HOURS_24,
                AnalysisPeriod.DAYS_7,
                AnalysisPeriod.CUSTOM,
            ),
            snapshot.availablePeriods,
        )
        val health = requireNotNull(snapshot.collectionHealth)
        assertEquals(0, health.unsuccessfulAttemptCount)
        assertEquals(0, health.collectionInterruptionCount)
    }

    @Test
    fun freshLoaderReadsLongTermResultsFromPersistedHistory() = runBlocking {
        persistRecordedHistory()

        val snapshot = loader().load(request(AnalysisPeriod.DAYS_7), zoneId)

        val insights = requireNotNull(snapshot.longTermInsights)
        // The seven-day window ends on the last recorded day, so the first day stays outside it.
        assertEquals(dayStart(1), insights.rangeStartMillis)
        assertEquals(syncedThroughMillis, insights.rangeEndMillis)
        assertEquals(coverMillisPerDay * 7, insights.coverMillis)
        assertEquals(innerMillisPerDay * 7, insights.innerMillis)
        assertEquals(0L, insights.excludedMillis)
        assertEquals(7, insights.openedCount)
        assertEquals(7, insights.closedCount)
        assertEquals(7, insights.calendarDayCount)
        assertEquals(7, insights.observedDayCount)
        assertEquals(7, insights.innerUsedDayCount)

        val summary = requireNotNull(snapshot.periodSummary)
        assertEquals(AnalysisPeriod.DAYS_7, summary.period)
        assertEquals(coverMillisPerDay * 7, summary.coverMillis)
        assertEquals(innerMillisPerDay * 7, summary.innerMillis)
        // Long-term app totals come from the aggregated table, ranked by classified time.
        assertEquals(
            listOf(INNER_APP, COVER_APP),
            summary.apps.map { it.packageName },
        )
        assertEquals(
            mapOf(
                COVER_APP to (coverMillisPerDay * 7 to 0L),
                INNER_APP to (0L to innerMillisPerDay * 7),
            ),
            summary.apps.associate { it.packageName to (it.coverMillis to it.innerMillis) },
        )

        val sessions = requireNotNull(snapshot.innerSessionSummary)
        assertEquals(7, sessions.completeSessionCount)
        assertEquals(innerMillisPerDay, sessions.medianInnerActiveMillis)
        assertEquals(7, sessions.detectedOpenCount)
    }

    @Test
    fun freshExporterWritesPersistedHistoryWithoutAnyAnalysisPass() = runBlocking {
        persistRecordedHistory()

        // Nothing loads the screen state first: this is the recreated-process export.
        val rows = exportRows(calibration, zoneId)

        assertEquals(recordedDayCount.toInt(), rows.size)
        assertEquals(
            (0 until recordedDayCount.toInt()).map { firstDay.plusDays(it.toLong()).toString() },
            rows.map { it.date },
        )
        rows.forEach { row ->
            assertEquals("Z", row.zoneId)
            assertEquals(coverMillisPerDay, row.coverMillis)
            assertEquals(innerMillisPerDay, row.innerMillis)
            assertEquals(coverMillisPerDay + innerMillisPerDay, row.classifiedMillis)
            assertEquals(0L, row.excludedMillis)
            assertEquals(1, row.openedCount)
            assertEquals(1, row.closedCount)
            assertEquals(0, row.evidenceGapCount)
        }
        assertEquals(dayStart(0), rows.first().dayStartMillis)
        assertEquals(dayStart(7), rows.last().dayStartMillis)
    }

    @Test
    fun exportRebuildsTheHistoryWhenCalibrationChanged() = runBlocking {
        persistRecordedHistory()
        // Populate the cache the way the screen would, using the original anchors.
        loader().load(request(AnalysisPeriod.HOURS_24), zoneId)

        val rows = exportRows(swappedCalibration, zoneId)

        assertEquals(recordedDayCount.toInt(), rows.size)
        rows.forEach { row ->
            assertEquals(innerMillisPerDay, row.coverMillis)
            assertEquals(coverMillisPerDay, row.innerMillis)
        }
    }

    @Test
    fun exportRebuildsTheHistoryForADifferentTimeZone() = runBlocking {
        persistRecordedHistory()
        val utcRows = exportRows(calibration, zoneId)
        assertEquals("Z", utcRows.first().zoneId)

        val shiftedZone = ZoneOffset.ofHours(9)
        val shiftedRows = exportRows(calibration, shiftedZone)

        assertTrue(shiftedRows.all { it.zoneId == "+09:00" })
        val firstRecordedDay = shiftedRows.single { it.date == firstDay.toString() }
        // Each recorded day still falls inside one local day, only the day boundary moves.
        assertEquals(
            firstDay.atStartOfDay(shiftedZone).toInstant().toEpochMilli(),
            firstRecordedDay.dayStartMillis,
        )
        assertEquals(coverMillisPerDay, firstRecordedDay.coverMillis)
        assertEquals(innerMillisPerDay, firstRecordedDay.innerMillis)
    }

    @Test
    fun exportAndScreenAnalysisAgreeOnTheSameHistory() = runBlocking {
        persistRecordedHistory()

        val snapshot = loader().load(request(AnalysisPeriod.DAYS_7), zoneId)
        val insights = requireNotNull(snapshot.longTermInsights)
        val rows = exportRows(calibration, zoneId)
            .filter { it.dayStartMillis >= insights.rangeStartMillis }

        assertEquals(7, rows.size)
        assertEquals(insights.coverMillis, rows.sumOf { it.coverMillis })
        assertEquals(insights.innerMillis, rows.sumOf { it.innerMillis })
        assertEquals(insights.openedCount, rows.sumOf { it.openedCount })
        assertEquals(insights.closedCount, rows.sumOf { it.closedCount })
    }

    @Test
    fun exportWritesOnlyTheHeaderBeforeTheFirstSync() = runBlocking {
        val output = StringWriter()

        LongTermCsvExporter {
            loader().loadSavedDailyHistory(calibration, zoneId)
        }.export { output.asNonClosing() }

        val lines = output.toString().lineSequence().filter(String::isNotEmpty).toList()
        assertEquals(1, lines.size)
        assertTrue(lines.single().startsWith("date,zone_id,"))
        assertFalse(database.dailyPostureSummaryDao().loadAll().any())
    }

    private suspend fun persistRecordedHistory() {
        (0 until recordedDayCount).forEach { day ->
            database.usageEventDao().insertEvents(eventsForDay(dayStart(day)))
        }
        database.usageEventDao().upsertSyncState(
            UsageSyncStateEntity(
                lastSuccessfulEndMillis = syncedThroughMillis,
                lastSuccessfulAtMillis = syncedThroughMillis,
                lastQueryBeginMillis = 0L,
                lastInsertedEventCount = 0,
            ),
        )
        // A single attempt covering the whole history leaves no collection gap to reset state on.
        database.usageEventDao().insertSyncHistory(
            SyncHistoryEntity(
                attemptedAtMillis = syncedThroughMillis,
                queryBeginMillis = 0L,
                queryEndMillis = syncedThroughMillis,
                status = SyncAttemptStatus.SUCCESS.name,
                readEventCount = 0,
                insertedEventCount = 0,
            ),
        )
    }

    private fun eventsForDay(dayStartMillis: Long): List<UsageEventEntity> {
        val openedAt = dayStartMillis + TimeUnit.HOURS.toMillis(1)
        val closedAt = dayStartMillis + TimeUnit.HOURS.toMillis(4)
        val screenOffAt = dayStartMillis + TimeUnit.HOURS.toMillis(5)
        return listOf(
            event(
                key = "$dayStartMillis-cover",
                timestampMillis = dayStartMillis,
                sequence = 0,
                rawEventType = UsageEvents.Event.CONFIGURATION_CHANGE,
                eventConfiguration = coverConfiguration,
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
                key = "$dayStartMillis-cover-app",
                timestampMillis = dayStartMillis,
                sequence = 3,
                rawEventType = UsageEvents.Event.ACTIVITY_RESUMED,
                packageName = COVER_APP,
            ),
            event(
                key = "$dayStartMillis-opened",
                timestampMillis = openedAt,
                sequence = 0,
                rawEventType = UsageEvents.Event.CONFIGURATION_CHANGE,
                eventConfiguration = innerConfiguration,
            ),
            event(
                key = "$dayStartMillis-cover-app-paused",
                timestampMillis = openedAt,
                sequence = 1,
                rawEventType = UsageEvents.Event.ACTIVITY_PAUSED,
                packageName = COVER_APP,
            ),
            event(
                key = "$dayStartMillis-inner-app",
                timestampMillis = openedAt,
                sequence = 2,
                rawEventType = UsageEvents.Event.ACTIVITY_RESUMED,
                packageName = INNER_APP,
            ),
            event(
                key = "$dayStartMillis-closed",
                timestampMillis = closedAt,
                sequence = 0,
                rawEventType = UsageEvents.Event.CONFIGURATION_CHANGE,
                eventConfiguration = coverConfiguration,
            ),
            event(
                key = "$dayStartMillis-inner-app-paused",
                timestampMillis = closedAt,
                sequence = 1,
                rawEventType = UsageEvents.Event.ACTIVITY_PAUSED,
                packageName = INNER_APP,
            ),
            event(
                key = "$dayStartMillis-cover-app-again",
                timestampMillis = closedAt,
                sequence = 2,
                rawEventType = UsageEvents.Event.ACTIVITY_RESUMED,
                packageName = COVER_APP,
            ),
            event(
                key = "$dayStartMillis-cover-app-paused-again",
                timestampMillis = screenOffAt,
                sequence = 0,
                rawEventType = UsageEvents.Event.ACTIVITY_PAUSED,
                packageName = COVER_APP,
            ),
            event(
                key = "$dayStartMillis-screen-off",
                timestampMillis = screenOffAt,
                sequence = 1,
                rawEventType = UsageEvents.Event.SCREEN_NON_INTERACTIVE,
            ),
        )
    }

    private fun event(
        key: String,
        timestampMillis: Long,
        sequence: Int,
        rawEventType: Int,
        eventConfiguration: DisplayConfiguration? = null,
        packageName: String? = null,
    ) = UsageEventEntity(
        eventKey = key,
        timestampMillis = timestampMillis,
        sequenceAtTimestamp = sequence,
        rawEventType = rawEventType,
        packageName = packageName,
        className = packageName?.let { "$it.MainActivity" },
        hasConfiguration = eventConfiguration != null,
        screenWidthDp = eventConfiguration?.screenWidthDp,
        screenHeightDp = eventConfiguration?.screenHeightDp,
        smallestScreenWidthDp = eventConfiguration?.smallestScreenWidthDp,
        orientation = eventConfiguration?.orientation,
        densityDpi = eventConfiguration?.densityDpi,
    )

    private fun dayStart(dayOffset: Long): Long = firstDay.plusDays(dayOffset)
        .atStartOfDay(zoneId)
        .toInstant()
        .toEpochMilli()

    private fun request(period: AnalysisPeriod) = StoredAnalysisRequest(
        period = period,
        customRange = null,
        calibration = calibration,
        syncState = UsageSyncState(
            lastSuccessfulEndMillis = syncedThroughMillis,
            lastSuccessfulAtMillis = syncedThroughMillis,
            lastQueryBeginMillis = 0L,
            lastInsertedEventCount = 0,
        ),
        checkpointRevision = 0L,
    )

    private fun loader() = StoredAnalysisLoader(
        syncRepository = UsageSyncRepository(
            eventSource = OfflineEventSource,
            eventStore = RoomUsageEventStore(database.usageEventDao()),
        ),
        checkpointRepository = PostureCheckpointRepository(database.postureCheckpointDao()),
        dailySummaryRepository = DailySummaryRepository(
            usageEventDao = database.usageEventDao(),
            checkpointDao = database.postureCheckpointDao(),
            summaryDao = database.dailyPostureSummaryDao(),
        ),
        packageLabel = { it },
        isLauncherApp = { false },
        currentTimeMillis = { syncedThroughMillis },
    )

    private suspend fun exportRows(
        calibration: Calibration,
        zoneId: ZoneId,
    ): List<CsvRow> {
        val output = StringWriter()
        val loader = loader()
        LongTermCsvExporter {
            loader.loadSavedDailyHistory(calibration, zoneId)
        }.export { output.asNonClosing() }
        return output.toString()
            .lineSequence()
            .filter(String::isNotEmpty)
            .drop(1)
            .map(::parseCsvRow)
            .toList()
    }

    private data class CsvRow(
        val date: String,
        val zoneId: String,
        val dayStartMillis: Long,
        val coverMillis: Long,
        val innerMillis: Long,
        val classifiedMillis: Long,
        val excludedMillis: Long,
        val openedCount: Int,
        val closedCount: Int,
        val evidenceGapCount: Int,
    )

    private fun parseCsvRow(line: String): CsvRow {
        val fields = line.split(',').map { it.trim('"') }
        return CsvRow(
            date = fields[0],
            zoneId = fields[1],
            dayStartMillis = fields[2].toLong(),
            coverMillis = fields[4].toLong(),
            innerMillis = fields[5].toLong(),
            classifiedMillis = fields[6].toLong(),
            excludedMillis = fields[7].toLong(),
            openedCount = fields[10].toInt(),
            closedCount = fields[11].toInt(),
            evidenceGapCount = fields[12].toInt(),
        )
    }

    /** Keeps the captured text readable after the exporter closes the writer it was handed. */
    private fun StringWriter.asNonClosing(): Writer {
        val target = this
        return object : Writer() {
            override fun write(cbuf: CharArray, off: Int, len: Int) = target.write(cbuf, off, len)

            override fun flush() = target.flush()

            override fun close() = Unit
        }
    }

    private object OfflineEventSource : UsageEventSource {
        override fun hasUsageAccess(): Boolean = true

        override fun read(beginMillis: Long, endMillis: Long): UsageReadResult =
            UsageReadResult.Success(emptyList())
    }

    private companion object {
        const val COVER_APP = "app.cover"
        const val INNER_APP = "app.inner"
    }
}
