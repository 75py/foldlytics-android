package com.nagopy.android.foldlytics.data

import android.app.usage.UsageEvents
import com.nagopy.android.foldlytics.model.Calibration
import com.nagopy.android.foldlytics.model.DisplayConfiguration
import com.nagopy.android.foldlytics.model.InnerDisplaySession
import com.nagopy.android.foldlytics.model.PostureCheckpoint
import com.nagopy.android.foldlytics.model.PostureCheckpointSource
import com.nagopy.android.foldlytics.model.UsageAnalysis
import java.time.ZoneId
import kotlin.random.Random
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BoundedAnalysisSeedTest {
    private val cover = DisplayConfiguration(420, 890, 420, 1, 420)
    private val inner = DisplayConfiguration(730, 820, 730, 2, 420)
    private val calibration = Calibration(cover = cover, inner = inner)
    private val zoneId = ZoneId.of("Asia/Tokyo")
    private val analyzer = UsageAnalyzer { it }

    @Test
    fun boundedSeedMatchesFullHistoryAcrossRepeatedStartups() = runBlocking {
        (1..6).forEach { seed ->
            val history = randomHistory(seed = seed, days = 18, allowRestarts = seed % 2 == 0)
            val dao = InMemoryUsageEventDao(history.events)
            val legacy = InMemoryUsageEventDao(history.events, triggersInstalled = false)
            val random = Random(seed * 31)
            // Daily startups move forward; occasional reopens jump back to older days.
            val begins = (2 until 17).flatMap { day ->
                listOf(
                    day * DAY + random.nextLong(DAY),
                    day * DAY + random.nextLong(DAY),
                    (day - random.nextInt(2)) * DAY,
                )
            } + listOf(9 * DAY - 1, 3 * DAY + 1, 12 * DAY)
            begins.forEach { begin ->
                assertEquivalent(
                    dao = dao,
                    legacy = legacy,
                    history = history,
                    beginMillis = begin,
                    endMillis = begin + DAY / 2 + random.nextLong(DAY),
                    message = "seed=$seed begin=$begin",
                )
            }
            assertTrue(dao.snapshotBoundaries.isNotEmpty())
        }
    }

    @Test
    fun beginAtGapRestartAndBoundaryTimesMatchesFullHistory() = runBlocking {
        val history = randomHistory(seed = 99, days = 10, allowRestarts = true)
        val dao = InMemoryUsageEventDao(history.events)
        val legacy = InMemoryUsageEventDao(history.events, triggersInstalled = false)
        val restarts = history.events
            .filter { it.rawEventType == UsageEvents.Event.DEVICE_STARTUP }
            .map(UsageEventEntity::timestampMillis)
        val begins = (history.gapStarts + restarts).flatMap { listOf(it, it + 1) } +
            history.gapStarts.map { analysisSeedBoundaryAtOrBefore(it) + DAY } +
            (1L..9L).map { it * DAY }
        begins.filter { it in 1 until 10 * DAY }.sorted().forEach { begin ->
            assertEquivalent(dao, legacy, history, begin, begin + DAY, "begin=$begin")
        }
    }

    @Test
    fun snapshotsAreInvalidatedByBackfilledReorderedAndDeletedEvents() = runBlocking {
        val history = randomHistory(seed = 7, days = 12, allowRestarts = false)
        val dao = InMemoryUsageEventDao(history.events)
        val legacy = InMemoryUsageEventDao(history.events, triggersInstalled = false)
        val begin = 10 * DAY + HOUR
        assertEquivalent(dao, legacy, history, begin, begin + DAY, "warm")
        assertTrue(10 * DAY in dao.snapshotBoundaries)

        // A late sync backfills an earlier configuration and resumed activity.
        val backfill = listOf(
            entity("backfill-config", 4 * DAY + 5, UsageEvents.Event.CONFIGURATION_CHANGE, config = inner),
            entity(
                "backfill-resume",
                4 * DAY + 6,
                UsageEvents.Event.ACTIVITY_RESUMED,
                packageName = "app.backfill",
                className = "Main",
            ),
        )
        dao.insertEvents(backfill)
        legacy.insertEvents(backfill)
        assertTrue(dao.snapshotBoundaries.all { it <= 4 * DAY + 5 })
        val updatedHistory = history.copy(events = history.events + backfill)
        assertEquivalent(dao, legacy, updatedHistory, begin, begin + DAY, "backfill")

        // Reconciliation renumbers sequences at one timestamp.
        val reordered = updatedHistory.events
            .groupBy(UsageEventEntity::timestampMillis)
            .values
            .first { rows -> rows.size > 1 && rows.first().timestampMillis < 9 * DAY }
            .let { rows ->
                rows.mapIndexed { index, row ->
                    row.copy(sequenceAtTimestamp = rows.size - 1 - index)
                }
            }
        dao.updateEvents(reordered)
        legacy.updateEvents(reordered)
        val reorderedByKey = reordered.associateBy(UsageEventEntity::eventKey)
        val reorderedHistory = updatedHistory.copy(
            events = updatedHistory.events.map { reorderedByKey[it.eventKey] ?: it },
        )
        assertEquivalent(dao, legacy, reorderedHistory, begin, begin + DAY, "reordered")

        val deleted = reorderedHistory.events.first {
            it.rawEventType == UsageEvents.Event.CONFIGURATION_CHANGE &&
                it.timestampMillis in 8 * DAY until 9 * DAY
        }
        dao.deleteEvent(deleted.eventKey)
        legacy.deleteEvent(deleted.eventKey)
        assertEquivalent(
            dao,
            legacy,
            reorderedHistory.copy(events = reorderedHistory.events - deleted),
            begin,
            begin + DAY,
            "deleted",
        )
    }

    @Test
    fun snapshotIsNotReusedWhenTheLatestEarlierGapChanges() = runBlocking {
        val history = randomHistory(seed = 13, days = 12, allowRestarts = false)
        val dao = InMemoryUsageEventDao(history.events)
        val legacy = InMemoryUsageEventDao(history.events, triggersInstalled = false)
        val begin = 10 * DAY + 3 * HOUR
        assertEquivalent(dao, legacy, history, begin, begin + DAY, "warm")

        // Newly detected interruption before the stored boundary.
        val withGap = history.copy(gapStarts = (history.gapStarts + (9 * DAY + 7 * HOUR)).sorted())
        assertEquivalent(dao, legacy, withGap, begin, begin + DAY, "new gap")
        // Removing it again must not reuse the snapshot built with the gap.
        assertEquivalent(dao, legacy, history, begin, begin + DAY, "gap removed")
    }

    @Test
    fun databaseWithoutInvalidationTriggersUsesFullHistory() = runBlocking {
        val history = randomHistory(seed = 5, days = 8, allowRestarts = false)
        val dao = InMemoryUsageEventDao(history.events, triggersInstalled = false)
        val begin = 7 * DAY

        val bounded = dao.loadBoundedUsageEventsForAnalysis(begin, begin + DAY, history.gapStarts)

        assertEquals(dao.loadUsageEventsForAnalysis(begin, begin + DAY), bounded)
        assertTrue(dao.snapshotBoundaries.isEmpty())
    }

    /**
     * Startup work must not grow with history length. Both histories repeat the same day,
     * including an activity whose missing pause leaves evidence that never clears, and neither
     * has a recent restart or collection gap.
     */
    @Test
    fun repeatedStartupReadsTheSameRowsForSevenAndNinetySixDayHistories() = runBlocking {
        val sevenDay = startupWorkload(days = 7)
        val ninetySixDay = startupWorkload(days = 96)
        println("startup rows read: 7-day=$sevenDay 96-day=$ninetySixDay")

        assertEquals(sevenDay.boundedRows, ninetySixDay.boundedRows)
        assertEquals(sevenDay.seedRows, ninetySixDay.seedRows)
        assertTrue(ninetySixDay.legacyRows > 10 * sevenDay.legacyRows / 2)
        assertTrue(ninetySixDay.boundedRows * 5 < ninetySixDay.legacyRows)
    }

    private data class Workload(
        val legacyRows: Long,
        val boundedRows: Long,
        val seedRows: Int,
    )

    private suspend fun startupWorkload(days: Int): Workload {
        val events = (0 until days).flatMap { day -> periodicDay(day.toLong() * DAY) }
        val dao = InMemoryUsageEventDao(events)
        val now = days * DAY - HOUR
        // The previous startup, a day earlier, left its snapshots behind.
        dao.loadBoundedUsageEventsForAnalysis(now - DAY - 3 * DAY - DAY, now - DAY, emptyList())
        dao.loadBoundedUsageEventsForAnalysis(
            analysisSeedBoundaryAtOrBefore(now - DAY) - DAY,
            now - DAY,
            emptyList(),
        )

        dao.resetRowsRead()
        // Displayed 24 h window with its lookback, then the daily-cache update from midnight.
        val displayed = dao.loadBoundedUsageEventsForAnalysis(now - DAY - 3 * DAY, now, emptyList())
        val cache = dao.loadBoundedUsageEventsForAnalysis(
            analysisSeedBoundaryAtOrBefore(now) - DAY,
            now,
            emptyList(),
        )
        val boundedRows = dao.rowsRead

        val legacy = InMemoryUsageEventDao(events, triggersInstalled = false)
        val legacyDisplayed = legacy.loadUsageEventsForAnalysis(now - DAY - 3 * DAY, now)
        val legacyCache = legacy.loadUsageEventsForAnalysis(
            analysisSeedBoundaryAtOrBefore(now) - DAY,
            now,
        )
        assertSameAnalysis(legacyDisplayed, displayed, now - DAY - 3 * DAY, now, emptyList())
        assertSameAnalysis(
            legacyCache,
            cache,
            analysisSeedBoundaryAtOrBefore(now) - DAY,
            now,
            emptyList(),
        )
        return Workload(
            legacyRows = legacy.rowsRead,
            boundedRows = boundedRows,
            seedRows = dao.snapshotEventCount(analysisSeedBoundaryAtOrBefore(now - 4 * DAY)),
        )
    }

    private suspend fun assertEquivalent(
        dao: InMemoryUsageEventDao,
        legacy: InMemoryUsageEventDao,
        history: History,
        beginMillis: Long,
        endMillis: Long,
        message: String,
    ) {
        val full = legacy.loadUsageEventsForAnalysis(beginMillis, endMillis)
        // Twice, so the second load reuses the snapshot the first one stored.
        repeat(2) { pass ->
            val bounded = dao.loadBoundedUsageEventsForAnalysis(
                beginMillis,
                endMillis,
                history.gapStarts,
            )
            assertTrue(
                "$message pass=$pass bounded rows must be stored rows",
                full.map(UsageEventEntity::eventKey)
                    .containsAll(bounded.map(UsageEventEntity::eventKey)),
            )
            assertEquals(
                "$message pass=$pass rows from the begin are unchanged",
                full.filter { it.timestampMillis >= beginMillis },
                bounded.filter { it.timestampMillis >= beginMillis },
            )
            assertSameAnalysis(
                full,
                bounded,
                beginMillis,
                endMillis,
                history.gapStarts,
                history,
                "$message pass=$pass",
            )
        }
    }

    private fun assertSameAnalysis(
        full: List<UsageEventEntity>,
        bounded: List<UsageEventEntity>,
        beginMillis: Long,
        endMillis: Long,
        gapStarts: List<Long>,
        history: History? = null,
        message: String = "",
    ) {
        val fullRecords = full.map(UsageEventEntity::toModel)
        val boundedRecords = bounded.map(UsageEventEntity::toModel)
        val checkpoints = history?.checkpointsForAnalysis(beginMillis, endMillis).orEmpty()
        val deviceStates = history?.deviceStatesForAnalysis(beginMillis, endMillis).orEmpty()
        val chunkGaps = buildList {
            gapStarts.lastOrNull { it < beginMillis }?.let(::add)
            addAll(gapStarts.filter { it in beginMillis until endMillis })
        }
        // The displayed window starts after a lookback; daily chunks start at the begin.
        listOf(
            Triple("display", minOf(beginMillis + 3 * HOUR, endMillis), gapStarts),
            Triple("chunk", beginMillis, chunkGaps),
        ).forEach { (mode, rangeStart, gaps) ->
            fun analyze(records: List<com.nagopy.android.foldlytics.model.UsageRecord>) =
                analyzer.analyze(
                    records = records,
                    rangeStartMillis = rangeStart,
                    rangeEndMillis = endMillis,
                    calibration = calibration,
                    checkpoints = checkpoints,
                    zoneId = zoneId,
                    collectionGapStarts = gaps,
                    deviceStateCheckpoints = deviceStates,
                )
            val expected = analyze(fullRecords)
            val actual = analyze(boundedRecords)
            assertEquals(
                "$message $mode analysis",
                expected.withoutLookback(),
                actual.withoutLookback(),
            )
            assertEquals(
                "$message $mode latest lookback event",
                expected.postureEvents.firstOrNull { it.isBeforeRange },
                actual.postureEvents.firstOrNull { it.isBeforeRange },
            )
            assertEquals(
                "$message $mode sessions",
                sessions(fullRecords, checkpoints, deviceStates, gaps, beginMillis, endMillis),
                sessions(boundedRecords, checkpoints, deviceStates, gaps, beginMillis, endMillis),
            )
        }
    }

    private fun sessions(
        records: List<com.nagopy.android.foldlytics.model.UsageRecord>,
        checkpoints: List<PostureCheckpoint>,
        deviceStates: List<DeviceStateCheckpoint>,
        gaps: List<Long>,
        beginMillis: Long,
        endMillis: Long,
    ): List<InnerDisplaySession> = InnerDisplaySessionAnalyzer(
        calibration = calibration,
        analysisStartMillis = beginMillis,
        captureAppSets = true,
    ).apply {
        processChunk(records, checkpoints, deviceStates, gaps, endMillis)
    }.sessionsAtEnd()

    private fun UsageAnalysis.withoutLookback(): UsageAnalysis =
        copy(postureEvents = postureEvents.filterNot { it.isBeforeRange })

    private data class History(
        val events: List<UsageEventEntity>,
        val gapStarts: List<Long>,
        val checkpoints: List<PostureCheckpoint>,
        val deviceStates: List<DeviceStateCheckpoint>,
    ) {
        fun checkpointsForAnalysis(begin: Long, end: Long): List<PostureCheckpoint> = buildList {
            checkpoints.lastOrNull { it.timestampMillis < begin }?.let(::add)
            addAll(checkpoints.filter { it.timestampMillis in begin until end })
        }

        fun deviceStatesForAnalysis(begin: Long, end: Long): List<DeviceStateCheckpoint> =
            buildList {
                deviceStates.lastOrNull { it.observedAtMillis < begin }?.let(::add)
                addAll(deviceStates.filter { it.observedAtMillis in begin until end })
            }
    }

    private fun randomHistory(seed: Int, days: Int, allowRestarts: Boolean): History {
        val random = Random(seed)
        val builder = EventBuilder()
        val partialInner = DisplayConfiguration(730, 0, 730, 0, 0)
        val partialCover = DisplayConfiguration(420, 0, 420, 0, 0)
        val emptyDelta = DisplayConfiguration(0, 0, 0, 0, 0)
        val packages = listOf("app.a", "app.b", "app.c", "launcher")
        val classes = listOf("Main", "Other", null)
        var timestamp = HOUR
        while (timestamp < days * DAY) {
            // Some events share a millisecond, so same-time order is exercised.
            timestamp += if (random.nextInt(8) == 0) 0L else random.nextLong(1L, 20 * MINUTE)
            when (random.nextInt(100)) {
                in 0..7 -> builder.add(timestamp, UsageEvents.Event.SCREEN_INTERACTIVE)
                in 8..12 -> builder.add(timestamp, UsageEvents.Event.SCREEN_NON_INTERACTIVE)
                in 13..18 -> builder.add(timestamp, UsageEvents.Event.KEYGUARD_HIDDEN)
                in 19..22 -> builder.add(timestamp, UsageEvents.Event.KEYGUARD_SHOWN)
                in 23..30 -> builder.add(timestamp, CONFIGURATION, config = inner)
                in 31..38 -> builder.add(timestamp, CONFIGURATION, config = cover)
                in 39..42 -> builder.add(timestamp, CONFIGURATION, config = partialInner)
                in 43..46 -> builder.add(timestamp, CONFIGURATION, config = partialCover)
                47 -> builder.add(timestamp, CONFIGURATION, config = emptyDelta)
                48 -> builder.add(timestamp, CONFIGURATION, config = null)
                49 -> if (allowRestarts) {
                    builder.add(timestamp, UsageEvents.Event.DEVICE_SHUTDOWN)
                    builder.add(timestamp + MINUTE, UsageEvents.Event.DEVICE_STARTUP)
                }
                in 50..69 -> builder.add(
                    timestamp,
                    UsageEvents.Event.ACTIVITY_RESUMED,
                    packageName = packages.random(random),
                    className = classes.random(random),
                )
                in 70..84 -> builder.add(
                    timestamp,
                    UsageEvents.Event.ACTIVITY_PAUSED,
                    packageName = packages.random(random),
                    className = classes.random(random),
                )
                in 85..97 -> builder.add(
                    timestamp,
                    UsageEvents.Event.ACTIVITY_STOPPED,
                    packageName = packages.random(random),
                    className = classes.random(random),
                )
                else -> builder.add(
                    timestamp,
                    UsageEvents.Event.ACTIVITY_RESUMED,
                    packageName = null,
                    className = null,
                )
            }
        }
        val gaps = (0 until days / 3).map { random.nextLong(HOUR, days * DAY) }.sorted()
        val checkpoints = (0 until days * 2).map {
            PostureCheckpoint(
                timestampMillis = random.nextLong(HOUR, days * DAY),
                configuration = if (random.nextBoolean()) inner else cover,
                source = PostureCheckpointSource.APP_FOREGROUND,
            )
        }.sortedBy(PostureCheckpoint::timestampMillis)
        val deviceStates = (0 until days * 3).map {
            DeviceStateCheckpoint(
                observedAtMillis = random.nextLong(HOUR, days * DAY),
                screenInteractive = random.nextBoolean(),
                keyguardHidden = random.nextBoolean(),
            )
        }.sortedBy(DeviceStateCheckpoint::observedAtMillis)
        return History(builder.events, gaps, checkpoints, deviceStates)
    }

    /** One identical day of use without a restart. */
    private fun periodicDay(dayStart: Long): List<UsageEventEntity> {
        val builder = EventBuilder(keyPrefix = "d$dayStart-")
        var timestamp = dayStart + 7 * HOUR
        repeat(24) { session ->
            builder.add(timestamp, UsageEvents.Event.SCREEN_INTERACTIVE)
            builder.add(timestamp + 1_000, UsageEvents.Event.KEYGUARD_HIDDEN)
            builder.add(
                timestamp + 2_000,
                CONFIGURATION,
                config = if (session % 2 == 0) inner else cover,
            )
            val packageName = "app.${session % 5}"
            builder.add(timestamp + 3_000, UsageEvents.Event.ACTIVITY_RESUMED, packageName, "Main")
            builder.add(timestamp + 20 * MINUTE, UsageEvents.Event.ACTIVITY_PAUSED, packageName, "Main")
            builder.add(timestamp + 20 * MINUTE + 500, UsageEvents.Event.ACTIVITY_STOPPED, packageName, "Main")
            builder.add(timestamp + 21 * MINUTE, UsageEvents.Event.KEYGUARD_SHOWN)
            builder.add(timestamp + 21 * MINUTE + 1, UsageEvents.Event.SCREEN_NON_INTERACTIVE)
            timestamp += 40 * MINUTE
        }
        // Pauses that never arrive leave this activity's evidence open forever.
        builder.add(dayStart + 6 * HOUR, UsageEvents.Event.ACTIVITY_RESUMED, "app.stuck", "Main")
        builder.add(dayStart + 6 * HOUR + 1, UsageEvents.Event.ACTIVITY_RESUMED, "app.stuck", "Main")
        return builder.events
    }

    private class EventBuilder(private val keyPrefix: String = "") {
        val events = mutableListOf<UsageEventEntity>()
        private val sequenceAt = mutableMapOf<Long, Int>()

        fun add(
            timestampMillis: Long,
            rawEventType: Int,
            packageName: String? = null,
            className: String? = null,
            config: DisplayConfiguration? = null,
        ) {
            val sequence = sequenceAt.getOrDefault(timestampMillis, 0)
            sequenceAt[timestampMillis] = sequence + 1
            events += entity(
                key = "$keyPrefix${events.size.toString().padStart(6, '0')}",
                timestampMillis = timestampMillis,
                rawEventType = rawEventType,
                packageName = packageName,
                className = className,
                config = config,
                sequence = sequence,
            )
        }
    }

    private companion object {
        const val MINUTE = 60_000L
        const val HOUR = 60 * MINUTE
        const val DAY = 24 * HOUR
        const val CONFIGURATION = UsageEvents.Event.CONFIGURATION_CHANGE

        fun entity(
            key: String,
            timestampMillis: Long,
            rawEventType: Int,
            packageName: String? = null,
            className: String? = null,
            config: DisplayConfiguration? = null,
            sequence: Int = 0,
        ) = UsageEventEntity(
            eventKey = key,
            timestampMillis = timestampMillis,
            sequenceAtTimestamp = sequence,
            rawEventType = rawEventType,
            packageName = packageName,
            className = className,
            hasConfiguration = config != null,
            screenWidthDp = config?.screenWidthDp,
            screenHeightDp = config?.screenHeightDp,
            smallestScreenWidthDp = config?.smallestScreenWidthDp,
            orientation = config?.orientation,
            densityDpi = config?.densityDpi,
        )
    }
}
