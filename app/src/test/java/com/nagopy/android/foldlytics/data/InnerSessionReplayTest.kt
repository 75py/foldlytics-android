package com.nagopy.android.foldlytics.data

import com.nagopy.android.foldlytics.model.Calibration
import com.nagopy.android.foldlytics.model.DisplayConfiguration
import com.nagopy.android.foldlytics.model.InnerDisplaySession
import com.nagopy.android.foldlytics.model.PostureCheckpoint
import com.nagopy.android.foldlytics.model.PostureCheckpointSource
import com.nagopy.android.foldlytics.model.UsageEventKind
import com.nagopy.android.foldlytics.model.UsageRecord
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class InnerSessionReplayTest {
    private val cover = DisplayConfiguration(400, 900, 400, 1, 420)
    private val inner = DisplayConfiguration(800, 900, 800, 1, 420)
    private val calibration = Calibration(cover, inner)
    private val baseline = listOf(
        event(0, UsageEventKind.CONFIGURATION_CHANGED, config = cover),
        event(1, UsageEventKind.SCREEN_INTERACTIVE),
        event(2, UsageEventKind.KEYGUARD_HIDDEN),
        event(3, UsageEventKind.ACTIVITY_RESUMED, "a"),
    )
    private val transitions = listOf(
        event(10, UsageEventKind.CONFIGURATION_CHANGED, config = inner),
        event(15, UsageEventKind.ACTIVITY_RESUMED, "b"),
        event(25, UsageEventKind.ACTIVITY_PAUSED, "a"),
        event(30, UsageEventKind.CONFIGURATION_CHANGED, config = cover),
    )
    private val cached = InnerDisplaySession(10, 0, 30, 20)

    @Test
    fun replaysHistoricalActivityAndPartitionsTransitionsWithoutChangingRegularAppTotals() {
        val result = replay(baseline + transitions).single()
        assertEquals(mapOf(setOf("a") to 5L, setOf("a", "b") to 10L, setOf("b") to 5L),
            result.appSetUsageMillis)
        assertEquals(cached.copy(appSetUsageMillis = result.appSetUsageMillis), result)
        val regular = UsageAnalyzer { it }.analyze(
            records = baseline + transitions,
            rangeStartMillis = 10,
            rangeEndMillis = 30,
            calibration = calibration,
            checkpoints = emptyList(),
            zoneId = ZoneOffset.UTC,
        )
        assertEquals(20L, regular.innerMillis)
        assertEquals(mapOf("a" to 15L, "b" to 15L), regular.apps.associate { it.packageName to it.innerMillis })
    }

    @Test
    fun ignoresPossibleOnlyEvidenceAndCoalescesUnorderedSets() {
        val records = baseline + listOf(
            event(4, UsageEventKind.ACTIVITY_RESUMED, "stale"),
            event(5, UsageEventKind.ACTIVITY_RESUMED, "stale"),
            event(6, UsageEventKind.ACTIVITY_PAUSED, "stale"),
            event(7, UsageEventKind.ACTIVITY_STOPPED, "stale"),
        ) + transitions + listOf(
            event(20, UsageEventKind.ACTIVITY_PAUSED, "a"),
            event(20, UsageEventKind.ACTIVITY_STOPPED, "a", sequence = 1),
            event(20, UsageEventKind.ACTIVITY_RESUMED, "a", sequence = 2),
        )
        val sets = replay(records).single().appSetUsageMillis.orEmpty()
        assertEquals(mapOf(setOf("a") to 5L, setOf("a", "b") to 10L, setOf("b") to 5L), sets)
        assertTrue(sets.keys.none { "stale" in it })
    }

    @Test
    fun keepsOffAndLockedIntervalsOutOfCombinationDurations() {
        val result = replay(baseline + transitions + listOf(
            event(16, UsageEventKind.SCREEN_NON_INTERACTIVE),
            event(18, UsageEventKind.SCREEN_INTERACTIVE),
            event(21, UsageEventKind.KEYGUARD_SHOWN),
            event(24, UsageEventKind.KEYGUARD_HIDDEN),
        ), session = cached.copy(innerActiveMillis = 15)).single()
        assertEquals(mapOf(setOf("a") to 5L, setOf("a", "b") to 5L, setOf("b") to 5L),
            result.appSetUsageMillis)
    }

    @Test
    fun rejectsMissingHistoryChangedBoundariesRestartGapsAndUnknownConfiguration() {
        val sources = listOf(
            transitions,
            baseline + transitions + event(20, UsageEventKind.DEVICE_STARTUP),
            baseline + transitions + event(20, UsageEventKind.DEVICE_SHUTDOWN),
            baseline + transitions + event(20, UsageEventKind.CONFIGURATION_CHANGED),
        )
        sources.forEach { records ->
            assertEquals(emptyMap<Set<String>, Long>(), replay(records).single().appSetUsageMillis)
        }
        assertEquals(emptyMap<Set<String>, Long>(), replay(baseline + transitions, gaps = listOf(20)).single().appSetUsageMillis)
        assertEquals(emptyMap<Set<String>, Long>(), replay(baseline + transitions,
            session = cached.copy(closedAtMillis = 31)).single().appSetUsageMillis)
        assertEquals(emptyMap<Set<String>, Long>(), replay(baseline + transitions,
            session = cached.copy(innerActiveMillis = 21)).single().appSetUsageMillis)
    }

    @Test
    fun reconstructsConfigurationDeltasAndOrdersSameTimeEvidence() {
        val records = baseline.filter { it.kind != UsageEventKind.CONFIGURATION_CHANGED } +
            transitions.map { record ->
                if (record.timestampMillis == 10L) record.copy(
                    configuration = DisplayConfiguration(800, 0, 800, 0, 0),
                ) else record
            } + listOf(event(15, UsageEventKind.ACTIVITY_PAUSED, "b", sequence = 1))
        val result = replay(records, checkpoints = listOf(
            PostureCheckpoint(0, cover, PostureCheckpointSource.APP_LAUNCH),
        )).single()
        assertEquals(mapOf(setOf("a") to 15L), result.appSetUsageMillis)
        // Device observations precede raw screen-off evidence at the same millisecond.
        val off = replaySelectedInnerSessions(
            listOf(cached.copy(innerActiveMillis = 10)),
            baseline + transitions + event(20, UsageEventKind.SCREEN_NON_INTERACTIVE),
            emptyList(), listOf(DeviceStateCheckpoint(20, true, true)), emptyList(), calibration,
        ).single()
        assertEquals(10L, off.appSetUsageMillis.orEmpty().values.sum())
    }

    @Test
    fun retainsOnlySelectedSessionKeysAndPreservesSelectionBoundaries() {
        val analyzer = InnerDisplaySessionAnalyzer(calibration, 0, true, setOf(10L to 0))
        analyzer.processChunk(baseline + transitions + listOf(
            event(40, UsageEventKind.CONFIGURATION_CHANGED, config = inner),
            event(50, UsageEventKind.CONFIGURATION_CHANGED, config = cover),
        ), emptyList(), collectionGapStarts = emptyList(), chunkEndMillis = 60)
        assertEquals(listOf(10L), analyzer.sessionsAtEnd().map { it.openedAtMillis })
    }

    private fun replay(
        records: List<UsageRecord>,
        session: InnerDisplaySession = cached,
        gaps: List<Long> = emptyList(),
        checkpoints: List<PostureCheckpoint> = emptyList(),
    ) = replaySelectedInnerSessions(listOf(session), records, checkpoints, emptyList(), gaps, calibration)

    private fun event(
        time: Long,
        kind: UsageEventKind,
        pkg: String? = null,
        config: DisplayConfiguration? = null,
        sequence: Int = 0,
    ) = UsageRecord(time, kind, pkg, pkg, config, 0, sequence)
}
