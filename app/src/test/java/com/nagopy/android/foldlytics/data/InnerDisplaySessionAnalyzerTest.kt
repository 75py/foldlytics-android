package com.nagopy.android.foldlytics.data

import com.nagopy.android.foldlytics.model.Calibration
import com.nagopy.android.foldlytics.model.DisplayConfiguration
import com.nagopy.android.foldlytics.model.PostureCheckpoint
import com.nagopy.android.foldlytics.model.PostureCheckpointSource
import com.nagopy.android.foldlytics.model.UsageEventKind
import com.nagopy.android.foldlytics.model.UsageRecord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class InnerDisplaySessionAnalyzerTest {
    private val cover = configuration(width = 420, height = 890, smallest = 420)
    private val inner = configuration(width = 730, height = 820, smallest = 730)
    private val calibration = Calibration(cover = cover, inner = inner)

    @Test
    fun completesCoverInnerCoverSessionAndStoresInnerPackageTime() {
        val analyzer = analyzer()

        analyzer.processChunk(
            records = activeCoverRecords() + listOf(
                record(1_000, UsageEventKind.CONFIGURATION_CHANGED, configuration = inner),
                record(6_000, UsageEventKind.CONFIGURATION_CHANGED, configuration = cover),
            ),
            checkpoints = emptyList(),
            collectionGapStarts = emptyList(),
            chunkEndMillis = 7_000,
        )

        val session = analyzer.sessionsAtEnd().single()
        assertTrue(session.isComplete)
        assertEquals(1_000L, session.openedAtMillis)
        assertEquals(6_000L, session.closedAtMillis)
        assertEquals(5_000L, session.innerActiveMillis)
        assertEquals(mapOf("app.a" to 5_000L), session.appUsageMillis)
    }

    @Test
    fun deviceStateCheckpointProvidesScreenAndLockBaseline() {
        val analyzer = analyzer()

        analyzer.processChunk(
            records = listOf(
                record(
                    0,
                    UsageEventKind.CONFIGURATION_CHANGED,
                    configuration = cover,
                    sequenceAtTimestamp = 0,
                ),
                record(
                    0,
                    UsageEventKind.ACTIVITY_RESUMED,
                    packageName = "app.a",
                    className = "A",
                    sequenceAtTimestamp = 1,
                ),
                record(1_000, UsageEventKind.CONFIGURATION_CHANGED, configuration = inner),
                record(6_000, UsageEventKind.CONFIGURATION_CHANGED, configuration = cover),
            ),
            checkpoints = emptyList(),
            deviceStateCheckpoints = listOf(
                DeviceStateCheckpoint(
                    observedAtMillis = 0,
                    screenInteractive = true,
                    keyguardHidden = true,
                ),
            ),
            collectionGapStarts = emptyList(),
            chunkEndMillis = 7_000,
        )

        val session = analyzer.sessionsAtEnd().single()
        assertEquals(5_000L, session.innerActiveMillis)
        assertEquals(mapOf("app.a" to 5_000L), session.appUsageMillis)
    }

    @Test
    fun screenOffAndLockIntervalsPauseTimeWithoutEndingTheSession() {
        val analyzer = analyzer()

        analyzer.processChunk(
            records = activeCoverRecords() + listOf(
                record(1_000, UsageEventKind.CONFIGURATION_CHANGED, configuration = inner),
                record(2_000, UsageEventKind.SCREEN_NON_INTERACTIVE),
                record(3_000, UsageEventKind.SCREEN_INTERACTIVE),
                record(3_500, UsageEventKind.KEYGUARD_SHOWN),
                record(4_500, UsageEventKind.KEYGUARD_HIDDEN),
                record(6_000, UsageEventKind.CONFIGURATION_CHANGED, configuration = cover),
            ),
            checkpoints = emptyList(),
            collectionGapStarts = emptyList(),
            chunkEndMillis = 7_000,
        )

        val session = analyzer.sessionsAtEnd().single()
        assertTrue(session.isComplete)
        assertEquals(3_000L, session.innerActiveMillis)
        assertEquals(mapOf("app.a" to 3_000L), session.appUsageMillis)
    }

    @Test
    fun doesNotCountUnobservedScreenAndLockStateAsZeroTime() {
        val analyzer = analyzer()

        analyzer.processChunk(
            records = listOf(
                record(0, UsageEventKind.CONFIGURATION_CHANGED, configuration = cover),
                record(1_000, UsageEventKind.CONFIGURATION_CHANGED, configuration = inner),
                record(61_000, UsageEventKind.CONFIGURATION_CHANGED, configuration = cover),
            ),
            checkpoints = emptyList(),
            collectionGapStarts = emptyList(),
            chunkEndMillis = 62_000,
        )

        assertTrue(analyzer.sessionsAtEnd().isEmpty())
    }

    @Test
    fun excludesScreenOnIntervalWhenLockStateIsUnobserved() {
        val analyzer = analyzer()

        analyzer.processChunk(
            records = listOf(
                record(0, UsageEventKind.CONFIGURATION_CHANGED, configuration = cover),
                record(0, UsageEventKind.SCREEN_INTERACTIVE),
                record(1_000, UsageEventKind.CONFIGURATION_CHANGED, configuration = inner),
                record(61_000, UsageEventKind.CONFIGURATION_CHANGED, configuration = cover),
            ),
            checkpoints = emptyList(),
            collectionGapStarts = emptyList(),
            chunkEndMillis = 62_000,
        )

        assertTrue(analyzer.sessionsAtEnd().isEmpty())
    }

    @Test
    fun confirmedScreenOffWithUnknownLockStateCountsAsZeroTime() {
        val analyzer = analyzer()

        analyzer.processChunk(
            records = listOf(
                record(0, UsageEventKind.CONFIGURATION_CHANGED, configuration = cover),
                record(500, UsageEventKind.SCREEN_NON_INTERACTIVE),
                record(1_000, UsageEventKind.CONFIGURATION_CHANGED, configuration = inner),
                record(61_000, UsageEventKind.CONFIGURATION_CHANGED, configuration = cover),
            ),
            checkpoints = emptyList(),
            collectionGapStarts = emptyList(),
            chunkEndMillis = 62_000,
        )

        val session = analyzer.sessionsAtEnd().single()
        assertTrue(session.isComplete)
        assertEquals(0L, session.innerActiveMillis)
    }

    @Test
    fun discardsWholeSessionWhenUnknownTimePrecedesKnownState() {
        val analyzer = analyzer()

        analyzer.processChunk(
            records = listOf(
                record(0, UsageEventKind.CONFIGURATION_CHANGED, configuration = cover),
                record(0, UsageEventKind.SCREEN_INTERACTIVE),
                record(1_000, UsageEventKind.CONFIGURATION_CHANGED, configuration = inner),
                // Screen-on is known, but lock state is still unknown for this interval.
                record(2_000, UsageEventKind.KEYGUARD_HIDDEN),
                record(4_000, UsageEventKind.CONFIGURATION_CHANGED, configuration = cover),
            ),
            checkpoints = emptyList(),
            collectionGapStarts = emptyList(),
            chunkEndMillis = 5_000,
        )

        assertTrue(analyzer.sessionsAtEnd().isEmpty())
    }

    @Test
    fun restartUnknownPostureAndCollectionGapInvalidatePendingSessions() {
        val restartAnalyzer = analyzer()
        restartAnalyzer.processChunk(
            records = activeCoverRecords() + listOf(
                record(1_000, UsageEventKind.CONFIGURATION_CHANGED, configuration = inner),
                record(2_000, UsageEventKind.DEVICE_STARTUP),
                record(3_000, UsageEventKind.CONFIGURATION_CHANGED, configuration = cover),
            ),
            checkpoints = emptyList(),
            collectionGapStarts = emptyList(),
            chunkEndMillis = 4_000,
        )

        val unknownAnalyzer = analyzer()
        unknownAnalyzer.processChunk(
            records = activeCoverRecords() + listOf(
                record(1_000, UsageEventKind.CONFIGURATION_CHANGED, configuration = inner),
                record(2_000, UsageEventKind.CONFIGURATION_CHANGED, configuration = null),
                record(3_000, UsageEventKind.CONFIGURATION_CHANGED, configuration = cover),
            ),
            checkpoints = emptyList(),
            collectionGapStarts = emptyList(),
            chunkEndMillis = 4_000,
        )

        val gapAnalyzer = analyzer()
        gapAnalyzer.processChunk(
            records = activeCoverRecords() + listOf(
                record(1_000, UsageEventKind.CONFIGURATION_CHANGED, configuration = inner),
                record(3_000, UsageEventKind.CONFIGURATION_CHANGED, configuration = cover),
            ),
            checkpoints = emptyList(),
            collectionGapStarts = listOf(2_000L),
            chunkEndMillis = 4_000,
        )

        assertTrue(restartAnalyzer.sessionsAtEnd().isEmpty())
        assertTrue(unknownAnalyzer.sessionsAtEnd().isEmpty())
        assertTrue(gapAnalyzer.sessionsAtEnd().isEmpty())
    }

    @Test
    fun shutdownAndNonInnerCheckpointInvalidatePendingSessionsWithoutCompletingThem() {
        val shutdownAnalyzer = analyzer()
        shutdownAnalyzer.processChunk(
            records = activeCoverRecords() + listOf(
                record(1_000, UsageEventKind.CONFIGURATION_CHANGED, configuration = inner),
                record(2_000, UsageEventKind.DEVICE_SHUTDOWN),
                record(3_000, UsageEventKind.CONFIGURATION_CHANGED, configuration = cover),
            ),
            checkpoints = emptyList(),
            collectionGapStarts = emptyList(),
            chunkEndMillis = 4_000,
        )

        val checkpointAnalyzer = analyzer()
        checkpointAnalyzer.processChunk(
            records = activeCoverRecords() + listOf(
                record(1_000, UsageEventKind.CONFIGURATION_CHANGED, configuration = inner),
                record(3_000, UsageEventKind.CONFIGURATION_CHANGED, configuration = cover),
            ),
            checkpoints = listOf(
                PostureCheckpoint(
                    timestampMillis = 2_000,
                    configuration = cover,
                    source = PostureCheckpointSource.APP_LAUNCH,
                ),
            ),
            collectionGapStarts = emptyList(),
            chunkEndMillis = 4_000,
        )

        assertTrue(shutdownAnalyzer.sessionsAtEnd().isEmpty())
        assertTrue(checkpointAnalyzer.sessionsAtEnd().isEmpty())
    }

    @Test
    fun doesNotCreateSessionWhenRangeStartsAlreadyInner() {
        val analyzer = analyzer(analysisStartMillis = 1_000)

        analyzer.processChunk(
            records = listOf(
                record(0, UsageEventKind.CONFIGURATION_CHANGED, configuration = inner),
                record(0, UsageEventKind.SCREEN_INTERACTIVE),
                record(0, UsageEventKind.KEYGUARD_HIDDEN),
                record(2_000, UsageEventKind.CONFIGURATION_CHANGED, configuration = cover),
            ),
            checkpoints = emptyList(),
            collectionGapStarts = emptyList(),
            chunkEndMillis = 3_000,
        )

        assertTrue(analyzer.sessionsAtEnd().isEmpty())
    }

    @Test
    fun keepsSessionIncompleteWhenNoCloseIsObserved() {
        val analyzer = analyzer()

        analyzer.processChunk(
            records = activeCoverRecords() +
                record(1_000, UsageEventKind.CONFIGURATION_CHANGED, configuration = inner),
            checkpoints = emptyList(),
            collectionGapStarts = emptyList(),
            chunkEndMillis = 2_000,
        )

        val session = analyzer.sessionsAtEnd().single()
        assertFalse(session.isComplete)
        assertNull(session.closedAtMillis)
        assertEquals(1_000L, session.innerActiveMillis)
    }

    @Test
    fun preservesSameTimestampSequenceAndIncludesZeroTimeSessions() {
        val analyzer = analyzer()

        analyzer.processChunk(
            records = listOf(
                record(0, UsageEventKind.CONFIGURATION_CHANGED, configuration = cover),
                // Deliberately shuffled: sequenceAtTimestamp, not input order, defines evidence.
                record(
                    1_000,
                    UsageEventKind.CONFIGURATION_CHANGED,
                    configuration = cover,
                    sequenceAtTimestamp = 2,
                ),
                record(
                    1_000,
                    UsageEventKind.CONFIGURATION_CHANGED,
                    configuration = inner,
                    sequenceAtTimestamp = 1,
                ),
            ),
            checkpoints = emptyList(),
            collectionGapStarts = emptyList(),
            chunkEndMillis = 2_000,
        )

        val sessions = analyzer.sessionsAtEnd()
        assertEquals(listOf(1), sessions.map { it.openedSequenceAtTimestamp })
        assertEquals(listOf(0L), sessions.map { it.innerActiveMillis })
        assertEquals(1_000L, sessions.single().closedAtMillis)
    }

    @Test
    fun leavesMultiResumeIntervalsUnallocated() {
        val analyzer = analyzer()

        analyzer.processChunk(
            records = listOf(
                record(0, UsageEventKind.CONFIGURATION_CHANGED, configuration = cover),
                record(0, UsageEventKind.SCREEN_INTERACTIVE),
                record(0, UsageEventKind.KEYGUARD_HIDDEN),
                record(0, UsageEventKind.ACTIVITY_RESUMED, "app.a", "A"),
                record(0, UsageEventKind.ACTIVITY_RESUMED, "app.b", "B"),
                record(1_000, UsageEventKind.CONFIGURATION_CHANGED, configuration = inner),
                record(2_000, UsageEventKind.ACTIVITY_PAUSED, "app.b", "B"),
                record(3_000, UsageEventKind.CONFIGURATION_CHANGED, configuration = cover),
            ),
            checkpoints = emptyList(),
            collectionGapStarts = emptyList(),
            chunkEndMillis = 4_000,
        )

        val session = analyzer.sessionsAtEnd().single()
        assertEquals(2_000L, session.innerActiveMillis)
        assertEquals(mapOf("app.a" to 1_000L), session.appUsageMillis)
    }

    @Test
    fun leavesIntervalsWithoutAResumedPackageUnallocated() {
        val analyzer = analyzer()

        analyzer.processChunk(
            records = listOf(
                record(0, UsageEventKind.CONFIGURATION_CHANGED, configuration = cover),
                record(0, UsageEventKind.SCREEN_INTERACTIVE),
                record(0, UsageEventKind.KEYGUARD_HIDDEN),
                record(0, UsageEventKind.ACTIVITY_RESUMED, "app.a", "A"),
                record(1_000, UsageEventKind.CONFIGURATION_CHANGED, configuration = inner),
                record(2_000, UsageEventKind.ACTIVITY_PAUSED, "app.a", "A"),
                record(4_000, UsageEventKind.CONFIGURATION_CHANGED, configuration = cover),
            ),
            checkpoints = emptyList(),
            collectionGapStarts = emptyList(),
            chunkEndMillis = 5_000,
        )

        val session = analyzer.sessionsAtEnd().single()
        assertEquals(3_000L, session.innerActiveMillis)
        assertEquals(mapOf("app.a" to 1_000L), session.appUsageMillis)
        assertEquals(2_000L, session.innerActiveMillis - session.appUsageMillis.values.sum())
    }

    @Test
    fun allocatesEachSingleResumedPackageIntervalAndLeavesMultiResumeUnallocated() {
        val analyzer = analyzer()

        analyzer.processChunk(
            records = listOf(
                record(0, UsageEventKind.CONFIGURATION_CHANGED, configuration = cover),
                record(0, UsageEventKind.SCREEN_INTERACTIVE),
                record(0, UsageEventKind.KEYGUARD_HIDDEN),
                record(0, UsageEventKind.ACTIVITY_RESUMED, "app.a", "A"),
                record(1_000, UsageEventKind.CONFIGURATION_CHANGED, configuration = inner),
                record(2_000, UsageEventKind.ACTIVITY_RESUMED, "app.b", "B"),
                record(3_000, UsageEventKind.ACTIVITY_PAUSED, "app.a", "A"),
                record(4_000, UsageEventKind.CONFIGURATION_CHANGED, configuration = cover),
            ),
            checkpoints = emptyList(),
            collectionGapStarts = emptyList(),
            chunkEndMillis = 5_000,
        )

        val session = analyzer.sessionsAtEnd().single()
        assertEquals(3_000L, session.innerActiveMillis)
        assertEquals(mapOf("app.a" to 1_000L, "app.b" to 1_000L), session.appUsageMillis)
        assertEquals(1_000L, session.innerActiveMillis - session.appUsageMillis.values.sum())
    }

    @Test
    fun sameClassDuplicateResumeAllocatesUntilTerminalEventThenLeavesUnallocated() {
        val analyzer = analyzer()

        analyzer.processChunk(
            records = activeCoverRecords() + listOf(
                record(1_000, UsageEventKind.CONFIGURATION_CHANGED, configuration = inner),
                record(2_000, UsageEventKind.ACTIVITY_RESUMED, "app.a", "A"),
                record(3_000, UsageEventKind.ACTIVITY_PAUSED, "app.a", "A"),
                record(6_000, UsageEventKind.CONFIGURATION_CHANGED, configuration = cover),
            ),
            checkpoints = emptyList(),
            collectionGapStarts = emptyList(),
            chunkEndMillis = 7_000,
        )

        val session = analyzer.sessionsAtEnd().single()
        assertEquals(5_000L, session.innerActiveMillis)
        assertEquals(mapOf("app.a" to 2_000L), session.appUsageMillis)
        assertEquals(3_000L, session.innerActiveMillis - session.appUsageMillis.values.sum())
    }

    @Test
    fun pauseFollowedByStopKeepsDuplicateSameClassSessionTimeUnallocated() {
        val analyzer = analyzer()

        analyzer.processChunk(
            records = activeCoverRecords() + listOf(
                record(1_000, UsageEventKind.CONFIGURATION_CHANGED, configuration = inner),
                record(2_000, UsageEventKind.ACTIVITY_RESUMED, "app.a", "A"),
                record(3_000, UsageEventKind.ACTIVITY_PAUSED, "app.a", "A"),
                record(4_000, UsageEventKind.ACTIVITY_STOPPED, "app.a", "A"),
                record(6_000, UsageEventKind.CONFIGURATION_CHANGED, configuration = cover),
            ),
            checkpoints = emptyList(),
            collectionGapStarts = emptyList(),
            chunkEndMillis = 7_000,
        )

        val session = analyzer.sessionsAtEnd().single()
        assertEquals(5_000L, session.innerActiveMillis)
        assertEquals(mapOf("app.a" to 2_000L), session.appUsageMillis)
    }

    @Test
    fun unresolvedPackagePreventsFalseExclusiveSessionAllocationToAnotherPackage() {
        val analyzer = analyzer()

        analyzer.processChunk(
            records = activeCoverRecords() + listOf(
                record(1_000, UsageEventKind.CONFIGURATION_CHANGED, configuration = inner),
                record(2_000, UsageEventKind.ACTIVITY_RESUMED, "app.a", "A"),
                record(3_000, UsageEventKind.ACTIVITY_PAUSED, "app.a", "A"),
                record(4_000, UsageEventKind.ACTIVITY_RESUMED, "app.b", "B"),
                record(6_000, UsageEventKind.CONFIGURATION_CHANGED, configuration = cover),
            ),
            checkpoints = emptyList(),
            collectionGapStarts = emptyList(),
            chunkEndMillis = 7_000,
        )

        val session = analyzer.sessionsAtEnd().single()
        assertEquals(5_000L, session.innerActiveMillis)
        assertEquals(mapOf("app.a" to 2_000L), session.appUsageMillis)
        assertEquals(3_000L, session.innerActiveMillis - session.appUsageMillis.values.sum())
    }

    @Test
    fun resumedEventRecoversSameClassSessionAllocationAfterAmbiguity() {
        val analyzer = analyzer()

        analyzer.processChunk(
            records = activeCoverRecords() + listOf(
                record(1_000, UsageEventKind.CONFIGURATION_CHANGED, configuration = inner),
                record(2_000, UsageEventKind.ACTIVITY_RESUMED, "app.a", "A"),
                record(3_000, UsageEventKind.ACTIVITY_PAUSED, "app.a", "A"),
                record(4_000, UsageEventKind.ACTIVITY_RESUMED, "app.a", "A"),
                record(6_000, UsageEventKind.CONFIGURATION_CHANGED, configuration = cover),
            ),
            checkpoints = emptyList(),
            collectionGapStarts = emptyList(),
            chunkEndMillis = 7_000,
        )

        val session = analyzer.sessionsAtEnd().single()
        assertEquals(5_000L, session.innerActiveMillis)
        assertEquals(mapOf("app.a" to 4_000L), session.appUsageMillis)
    }

    @Test
    fun repeatedTimestampResumePauseResumeAllocatesFromThatTimestamp() {
        val analyzer = analyzer()

        analyzer.processChunk(
            records = listOf(
                record(0, UsageEventKind.CONFIGURATION_CHANGED, configuration = cover),
                record(0, UsageEventKind.SCREEN_INTERACTIVE),
                record(0, UsageEventKind.KEYGUARD_HIDDEN),
                record(1_000, UsageEventKind.CONFIGURATION_CHANGED, configuration = inner),
                record(
                    2_000,
                    UsageEventKind.ACTIVITY_RESUMED,
                    "app.a",
                    "A",
                    sequenceAtTimestamp = 0,
                ),
                record(
                    2_000,
                    UsageEventKind.ACTIVITY_PAUSED,
                    "app.a",
                    "A",
                    sequenceAtTimestamp = 1,
                ),
                record(
                    2_000,
                    UsageEventKind.ACTIVITY_RESUMED,
                    "app.a",
                    "A",
                    sequenceAtTimestamp = 2,
                ),
                record(4_000, UsageEventKind.CONFIGURATION_CHANGED, configuration = cover),
            ),
            checkpoints = emptyList(),
            collectionGapStarts = emptyList(),
            chunkEndMillis = 5_000,
        )

        val session = analyzer.sessionsAtEnd().single()
        assertEquals(3_000L, session.innerActiveMillis)
        assertEquals(mapOf("app.a" to 2_000L), session.appUsageMillis)
    }

    @Test
    fun appAmbiguityAcrossScreenOffKeepsSessionActiveTimeKnownButUnallocated() {
        val analyzer = analyzer()

        analyzer.processChunk(
            records = activeCoverRecords() + listOf(
                record(1_000, UsageEventKind.CONFIGURATION_CHANGED, configuration = inner),
                record(2_000, UsageEventKind.ACTIVITY_RESUMED, "app.a", "A"),
                record(3_000, UsageEventKind.ACTIVITY_PAUSED, "app.a", "A"),
                record(4_000, UsageEventKind.SCREEN_NON_INTERACTIVE),
                record(5_000, UsageEventKind.SCREEN_INTERACTIVE),
                record(7_000, UsageEventKind.CONFIGURATION_CHANGED, configuration = cover),
            ),
            checkpoints = emptyList(),
            collectionGapStarts = emptyList(),
            chunkEndMillis = 8_000,
        )

        val session = analyzer.sessionsAtEnd().single()
        assertEquals(5_000L, session.innerActiveMillis)
        assertEquals(mapOf("app.a" to 2_000L), session.appUsageMillis)
        assertEquals(3_000L, session.innerActiveMillis - session.appUsageMillis.values.sum())
    }

    @Test
    fun carriesPendingSessionAndActiveTimeAcrossChunks() {
        val analyzer = analyzer()

        analyzer.processChunk(
            records = activeCoverRecords() +
                record(1_500, UsageEventKind.CONFIGURATION_CHANGED, configuration = inner),
            checkpoints = emptyList(),
            collectionGapStarts = emptyList(),
            chunkEndMillis = 2_000,
        )
        analyzer.processChunk(
            records = listOf(
                record(3_500, UsageEventKind.CONFIGURATION_CHANGED, configuration = cover),
            ),
            checkpoints = emptyList(),
            collectionGapStarts = emptyList(),
            chunkEndMillis = 4_000,
        )

        val session = analyzer.sessionsAtEnd().single()
        assertTrue(session.isComplete)
        assertEquals(2_000L, session.innerActiveMillis)
        assertEquals(mapOf("app.a" to 2_000L), session.appUsageMillis)
    }

    private fun analyzer(analysisStartMillis: Long = 0L) = InnerDisplaySessionAnalyzer(
        calibration = calibration,
        analysisStartMillis = analysisStartMillis,
    )

    private fun activeCoverRecords(): List<UsageRecord> = listOf(
        record(0, UsageEventKind.CONFIGURATION_CHANGED, configuration = cover, sequenceAtTimestamp = 0),
        record(0, UsageEventKind.SCREEN_INTERACTIVE, sequenceAtTimestamp = 1),
        record(0, UsageEventKind.KEYGUARD_HIDDEN, sequenceAtTimestamp = 2),
        record(
            0,
            UsageEventKind.ACTIVITY_RESUMED,
            packageName = "app.a",
            className = "A",
            sequenceAtTimestamp = 3,
        ),
    )

    private fun configuration(
        width: Int,
        height: Int,
        smallest: Int,
    ) = DisplayConfiguration(
        screenWidthDp = width,
        screenHeightDp = height,
        smallestScreenWidthDp = smallest,
        orientation = 1,
        densityDpi = 420,
    )

    private fun record(
        time: Long,
        kind: UsageEventKind,
        packageName: String? = null,
        className: String? = null,
        configuration: DisplayConfiguration? = null,
        sequenceAtTimestamp: Int = 0,
    ) = UsageRecord(
        timestampMillis = time,
        kind = kind,
        packageName = packageName,
        className = className,
        configuration = configuration,
        rawEventType = 0,
        sequenceAtTimestamp = sequenceAtTimestamp,
    )
}
