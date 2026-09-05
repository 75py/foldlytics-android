package com.nagopy.android.foldlytics.data

import com.nagopy.android.foldlytics.model.Calibration
import com.nagopy.android.foldlytics.model.DisplayConfiguration
import com.nagopy.android.foldlytics.model.DisplayPosture
import com.nagopy.android.foldlytics.model.PostureCheckpoint
import com.nagopy.android.foldlytics.model.PostureCheckpointSource
import com.nagopy.android.foldlytics.model.UnknownPostureReason
import com.nagopy.android.foldlytics.model.UsageEventKind
import com.nagopy.android.foldlytics.model.UsageRecord
import java.time.Instant
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Test

class UsageAnalyzerTest {
    private val cover = configuration(width = 420, height = 890, smallest = 420)
    private val inner = configuration(width = 730, height = 820, smallest = 730)
    private val calibration = Calibration(cover = cover, inner = inner)
    private val analyzer = UsageAnalyzer { "label:$it" }

    @Test
    fun collectionInterruptionStopsCarryingForwardStaleDeviceState() {
        val hour = 3_600_000L
        val records = listOf(
            record(0, UsageEventKind.CONFIGURATION_CHANGED, configuration = cover),
            record(0, UsageEventKind.SCREEN_INTERACTIVE),
            record(0, UsageEventKind.KEYGUARD_HIDDEN),
        )

        val result = analyzer.analyze(
            records = records,
            rangeStartMillis = 0,
            rangeEndMillis = hour * 72,
            calibration = calibration,
            collectionGapStarts = listOf(hour),
        )

        assertEquals(hour, result.coverMillis)
        assertEquals(1, result.evidenceGapCount)
        assertEquals(
            UnknownPostureReason.COLLECTION_INTERRUPTION,
            result.postureEvents.first().unknownReason,
        )
    }

    @Test
    fun splitsVisibleTimeByPostureAndApp() {
        val records = listOf(
            record(0, UsageEventKind.CONFIGURATION_CHANGED, configuration = cover),
            record(0, UsageEventKind.SCREEN_INTERACTIVE),
            record(0, UsageEventKind.KEYGUARD_HIDDEN),
            record(1_000, UsageEventKind.ACTIVITY_RESUMED, "app.a", "A"),
            record(6_000, UsageEventKind.CONFIGURATION_CHANGED, configuration = inner),
            record(9_000, UsageEventKind.ACTIVITY_PAUSED, "app.a", "A"),
            record(9_000, UsageEventKind.ACTIVITY_RESUMED, "app.b", "B"),
        )

        val result = analyzer.analyze(records, 0, 14_000, calibration)

        assertEquals(6_000, result.coverMillis)
        assertEquals(8_000, result.innerMillis)
        assertEquals(5_000, result.apps.first { it.packageName == "app.a" }.coverMillis)
        assertEquals(3_000, result.apps.first { it.packageName == "app.a" }.innerMillis)
        assertEquals(5_000, result.apps.first { it.packageName == "app.b" }.innerMillis)
        assertEquals(1, result.openedCount)
        assertEquals(0, result.closedCount)
    }

    @Test
    fun splitsAppUsageAcrossCalendarDays() {
        val midnight = 86_400_000L
        val rangeStart = midnight - 2_000L
        val records = listOf(
            record(rangeStart, UsageEventKind.CONFIGURATION_CHANGED, configuration = cover),
            record(rangeStart, UsageEventKind.SCREEN_INTERACTIVE),
            record(rangeStart, UsageEventKind.KEYGUARD_HIDDEN),
            record(midnight - 1_000L, UsageEventKind.ACTIVITY_RESUMED, "app.a", "A"),
            record(midnight + 2_000L, UsageEventKind.ACTIVITY_PAUSED, "app.a", "A"),
        )

        val result = analyzer.analyze(
            records = records,
            rangeStartMillis = rangeStart,
            rangeEndMillis = midnight + 3_000L,
            calibration = calibration,
            zoneId = ZoneOffset.UTC,
        )

        assertEquals(2, result.dailyAppSummaries.size)
        assertEquals(1_000L, result.dailyAppSummaries[0].coverMillis)
        assertEquals(2_000L, result.dailyAppSummaries[1].coverMillis)
        assertEquals(3_000L, result.dailyAppSummaries.sumOf { it.classifiedMillis })
    }

    @Test
    fun marksWhetherEachObservedPackageHasALauncherEntry() {
        val analyzerWithLauncherMetadata = UsageAnalyzer(
            isLauncherApp = { packageName -> packageName == "app.launcher" },
            packageLabel = { packageName -> "label:$packageName" },
        )
        val records = listOf(
            record(0, UsageEventKind.CONFIGURATION_CHANGED, configuration = cover),
            record(0, UsageEventKind.SCREEN_INTERACTIVE),
            record(0, UsageEventKind.KEYGUARD_HIDDEN),
            record(0, UsageEventKind.ACTIVITY_RESUMED, "app.launcher", "A"),
            record(1_000, UsageEventKind.ACTIVITY_PAUSED, "app.launcher", "A"),
            record(1_000, UsageEventKind.ACTIVITY_RESUMED, "app.internal", "B"),
        )

        val result = analyzerWithLauncherMetadata.analyze(records, 0, 2_000, calibration)

        assertEquals(true, result.apps.first { it.packageName == "app.launcher" }.isLauncherApp)
        assertEquals(false, result.apps.first { it.packageName == "app.internal" }.isLauncherApp)
    }

    @Test
    fun excludesScreenOffAndLockedIntervals() {
        val records = listOf(
            record(0, UsageEventKind.CONFIGURATION_CHANGED, configuration = cover),
            record(0, UsageEventKind.SCREEN_INTERACTIVE),
            record(2_000, UsageEventKind.KEYGUARD_HIDDEN),
            record(4_000, UsageEventKind.ACTIVITY_RESUMED, "app.a", "A"),
            record(7_000, UsageEventKind.KEYGUARD_SHOWN),
            record(9_000, UsageEventKind.KEYGUARD_HIDDEN),
            record(11_000, UsageEventKind.SCREEN_NON_INTERACTIVE),
        )

        val result = analyzer.analyze(records, 0, 15_000, calibration)

        assertEquals(7_000, result.coverMillis)
        assertEquals(5_000, result.apps.single().coverMillis)
    }

    @Test
    fun unlockedObservationSuppliesMissingKeyguardBaseline() {
        val records = listOf(
            record(0, UsageEventKind.CONFIGURATION_CHANGED, configuration = cover),
            record(0, UsageEventKind.SCREEN_INTERACTIVE),
            record(0, UsageEventKind.ACTIVITY_RESUMED, "app.a", "A"),
        )

        val result = analyzer.analyze(
            records = records,
            rangeStartMillis = 0,
            rangeEndMillis = 10_000,
            calibration = calibration,
            deviceStateCheckpoints = listOf(
                DeviceStateCheckpoint(
                    observedAtMillis = 2_000,
                    screenInteractive = true,
                    keyguardHidden = true,
                ),
            ),
        )

        assertEquals(8_000, result.coverMillis)
        assertEquals(8_000, result.apps.single().coverMillis)
    }

    @Test
    fun interactiveObservationSuppliesMissingScreenBaseline() {
        val records = listOf(
            record(0, UsageEventKind.CONFIGURATION_CHANGED, configuration = cover),
            record(0, UsageEventKind.KEYGUARD_HIDDEN),
            record(0, UsageEventKind.ACTIVITY_RESUMED, "app.a", "A"),
        )

        val result = analyzer.analyze(
            records = records,
            rangeStartMillis = 0,
            rangeEndMillis = 10_000,
            calibration = calibration,
            deviceStateCheckpoints = listOf(
                DeviceStateCheckpoint(2_000, screenInteractive = true, keyguardHidden = true),
            ),
        )

        assertEquals(8_000, result.coverMillis)
        assertEquals(8_000, result.apps.single().coverMillis)
    }

    @Test
    fun keyguardShownAfterUnlockedObservationStopsUsage() {
        val records = listOf(
            record(0, UsageEventKind.CONFIGURATION_CHANGED, configuration = cover),
            record(0, UsageEventKind.ACTIVITY_RESUMED, "app.a", "A"),
            record(3_000, UsageEventKind.KEYGUARD_SHOWN),
        )

        val result = analyzer.analyze(
            records = records,
            rangeStartMillis = 0,
            rangeEndMillis = 10_000,
            calibration = calibration,
            deviceStateCheckpoints = listOf(
                DeviceStateCheckpoint(0, screenInteractive = true, keyguardHidden = true),
            ),
        )

        assertEquals(3_000, result.coverMillis)
        assertEquals(3_000, result.apps.single().coverMillis)
    }

    @Test
    fun keyguardHiddenAfterShownResumesUsageFromTheEventTime() {
        val records = listOf(
            record(0, UsageEventKind.CONFIGURATION_CHANGED, configuration = cover),
            record(0, UsageEventKind.ACTIVITY_RESUMED, "app.a", "A"),
            record(3_000, UsageEventKind.KEYGUARD_SHOWN),
            record(7_000, UsageEventKind.KEYGUARD_HIDDEN),
        )

        val result = analyzer.analyze(
            records = records,
            rangeStartMillis = 0,
            rangeEndMillis = 10_000,
            calibration = calibration,
            deviceStateCheckpoints = listOf(
                DeviceStateCheckpoint(0, screenInteractive = true, keyguardHidden = true),
            ),
        )

        assertEquals(6_000, result.coverMillis)
        assertEquals(6_000, result.apps.single().coverMillis)
    }

    @Test
    fun matchingDeviceStateCheckpointDoesNotChangeNormalEventResult() {
        val records = listOf(
            record(0, UsageEventKind.CONFIGURATION_CHANGED, configuration = cover),
            record(0, UsageEventKind.SCREEN_INTERACTIVE),
            record(2_000, UsageEventKind.KEYGUARD_HIDDEN),
            record(4_000, UsageEventKind.ACTIVITY_RESUMED, "app.a", "A"),
            record(7_000, UsageEventKind.KEYGUARD_SHOWN),
            record(9_000, UsageEventKind.KEYGUARD_HIDDEN),
            record(11_000, UsageEventKind.SCREEN_NON_INTERACTIVE),
        )
        val original = analyzer.analyze(records, 0, 15_000, calibration)

        val withCheckpoint = analyzer.analyze(
            records = records,
            rangeStartMillis = 0,
            rangeEndMillis = 15_000,
            calibration = calibration,
            deviceStateCheckpoints = listOf(
                DeviceStateCheckpoint(5_000, screenInteractive = true, keyguardHidden = true),
            ),
        )

        assertEquals(original, withCheckpoint)
    }

    @Test
    fun checkpointAtRangeEndDoesNotBackfillEarlierHistory() {
        val records = listOf(
            record(0, UsageEventKind.CONFIGURATION_CHANGED, configuration = cover),
            record(0, UsageEventKind.ACTIVITY_RESUMED, "app.a", "A"),
        )
        val checkpoint = DeviceStateCheckpoint(
            observedAtMillis = 5_000,
            screenInteractive = true,
            keyguardHidden = true,
        )

        val beforeObservation = analyzer.analyze(
            records = records,
            rangeStartMillis = 0,
            rangeEndMillis = 5_000,
            calibration = calibration,
            deviceStateCheckpoints = listOf(checkpoint),
        )
        val afterObservation = analyzer.analyze(
            records = records,
            rangeStartMillis = 0,
            rangeEndMillis = 10_000,
            calibration = calibration,
            deviceStateCheckpoints = listOf(checkpoint),
        )

        assertEquals(0, beforeObservation.coverMillis)
        assertEquals(5_000, afterObservation.coverMillis)
    }

    @Test
    fun nonInteractiveCheckpointWaitsForScreenInteractiveEvent() {
        val records = listOf(
            record(0, UsageEventKind.CONFIGURATION_CHANGED, configuration = cover),
            record(0, UsageEventKind.ACTIVITY_RESUMED, "app.a", "A"),
            record(4_000, UsageEventKind.SCREEN_INTERACTIVE),
        )

        val result = analyzer.analyze(
            records = records,
            rangeStartMillis = 0,
            rangeEndMillis = 10_000,
            calibration = calibration,
            deviceStateCheckpoints = listOf(
                DeviceStateCheckpoint(0, screenInteractive = false, keyguardHidden = true),
            ),
        )

        assertEquals(6_000, result.coverMillis)
        assertEquals(6_000, result.apps.single().coverMillis)
    }

    @Test
    fun startupAndShutdownInvalidateEarlierDeviceStateObservations() {
        listOf(UsageEventKind.DEVICE_STARTUP, UsageEventKind.DEVICE_SHUTDOWN).forEach { reset ->
            val records = listOf(
                record(0, UsageEventKind.CONFIGURATION_CHANGED, configuration = cover),
                record(0, UsageEventKind.ACTIVITY_RESUMED, "app.a", "A"),
                record(3_000, reset),
                record(4_000, UsageEventKind.CONFIGURATION_CHANGED, configuration = cover),
            )

            val result = analyzer.analyze(
                records = records,
                rangeStartMillis = 0,
                rangeEndMillis = 10_000,
                calibration = calibration,
                deviceStateCheckpoints = listOf(
                    DeviceStateCheckpoint(0, screenInteractive = true, keyguardHidden = true),
                ),
            )

            assertEquals(reset.name, 3_000, result.coverMillis)
            assertEquals(reset.name, 3_000, result.apps.single().coverMillis)
        }
    }

    @Test
    fun collectionGapInvalidatesEarlierDeviceStateObservation() {
        val records = listOf(
            record(0, UsageEventKind.CONFIGURATION_CHANGED, configuration = cover),
            record(0, UsageEventKind.ACTIVITY_RESUMED, "app.a", "A"),
        )

        val result = analyzer.analyze(
            records = records,
            rangeStartMillis = 0,
            rangeEndMillis = 10_000,
            calibration = calibration,
            collectionGapStarts = listOf(3_000),
            deviceStateCheckpoints = listOf(
                DeviceStateCheckpoint(0, screenInteractive = true, keyguardHidden = true),
            ),
        )

        assertEquals(3_000, result.coverMillis)
        assertEquals(3_000, result.apps.single().coverMillis)
    }

    @Test
    fun observationAfterStartupResumesOnlyFromItsObservationTime() {
        val records = listOf(
            record(0, UsageEventKind.CONFIGURATION_CHANGED, configuration = cover),
            record(0, UsageEventKind.ACTIVITY_RESUMED, "app.a", "A"),
            record(3_000, UsageEventKind.DEVICE_STARTUP),
            record(4_000, UsageEventKind.CONFIGURATION_CHANGED, configuration = cover),
            record(5_000, UsageEventKind.ACTIVITY_RESUMED, "app.a", "A"),
        )

        val result = analyzer.analyze(
            records = records,
            rangeStartMillis = 0,
            rangeEndMillis = 10_000,
            calibration = calibration,
            deviceStateCheckpoints = listOf(
                DeviceStateCheckpoint(0, screenInteractive = true, keyguardHidden = true),
                DeviceStateCheckpoint(5_000, screenInteractive = true, keyguardHidden = true),
            ),
        )

        assertEquals(8_000, result.coverMillis)
        assertEquals(8_000, result.apps.single().coverMillis)
    }

    @Test
    fun rawScreenOffAndLockEventsWinOverObservationAtTheSameTimestamp() {
        listOf(UsageEventKind.SCREEN_NON_INTERACTIVE, UsageEventKind.KEYGUARD_SHOWN)
            .forEach { event ->
                val records = listOf(
                    record(0, UsageEventKind.CONFIGURATION_CHANGED, configuration = cover),
                    record(0, UsageEventKind.ACTIVITY_RESUMED, "app.a", "A"),
                    record(2_000, event),
                )

                val result = analyzer.analyze(
                    records = records,
                    rangeStartMillis = 0,
                    rangeEndMillis = 10_000,
                    calibration = calibration,
                    deviceStateCheckpoints = listOf(
                        DeviceStateCheckpoint(
                            2_000,
                            screenInteractive = true,
                            keyguardHidden = true,
                        ),
                    ),
                )

                assertEquals(event.name, 0, result.coverMillis)
                assertEquals(event.name, 0, result.apps.size)
            }
    }

    @Test
    fun collectionGapWinsOverObservationAtTheSameTimestamp() {
        val records = listOf(
            record(0, UsageEventKind.CONFIGURATION_CHANGED, configuration = cover),
            record(0, UsageEventKind.ACTIVITY_RESUMED, "app.a", "A"),
        )

        val result = analyzer.analyze(
            records = records,
            rangeStartMillis = 0,
            rangeEndMillis = 10_000,
            calibration = calibration,
            collectionGapStarts = listOf(3_000),
            deviceStateCheckpoints = listOf(
                DeviceStateCheckpoint(0, screenInteractive = true, keyguardHidden = true),
                DeviceStateCheckpoint(3_000, screenInteractive = true, keyguardHidden = true),
            ),
        )

        assertEquals(3_000, result.coverMillis)
        assertEquals(3_000, result.apps.single().coverMillis)
    }

    @Test
    fun countsMultiResumeButDoesNotDoubleCountDeviceTime() {
        val records = listOf(
            record(0, UsageEventKind.CONFIGURATION_CHANGED, configuration = inner),
            record(0, UsageEventKind.SCREEN_INTERACTIVE),
            record(0, UsageEventKind.KEYGUARD_HIDDEN),
            record(0, UsageEventKind.ACTIVITY_RESUMED, "app.a", "A"),
            record(2_000, UsageEventKind.ACTIVITY_RESUMED, "app.b", "B"),
        )

        val result = analyzer.analyze(records, 0, 7_000, calibration)

        assertEquals(7_000, result.innerMillis)
        assertEquals(5_000, result.multiResumeMillis)
        assertEquals(7_000, result.apps.first { it.packageName == "app.a" }.innerMillis)
        assertEquals(5_000, result.apps.first { it.packageName == "app.b" }.innerMillis)
    }

    @Test
    fun sameClassDuplicateResumeKeepsPackageTimeUntilTerminalEvent() {
        val records = listOf(
            record(0, UsageEventKind.CONFIGURATION_CHANGED, configuration = cover),
            record(0, UsageEventKind.SCREEN_INTERACTIVE),
            record(0, UsageEventKind.KEYGUARD_HIDDEN),
            record(0, UsageEventKind.ACTIVITY_RESUMED, "app.a", "A"),
            record(1_000, UsageEventKind.ACTIVITY_RESUMED, "app.a", "A"),
            record(3_000, UsageEventKind.ACTIVITY_PAUSED, "app.a", "A"),
        )

        val result = analyzer.analyze(records, 0, 6_000, calibration)

        assertEquals(6_000L, result.coverMillis)
        assertEquals(0L, result.excludedPostureMillis)
        assertEquals(3_000L, result.apps.single().coverMillis)
        assertEquals(0L, result.multiResumeMillis)
    }

    @Test
    fun pauseFollowedByStopAfterDuplicateResumeKeepsLaterSameClassTimeUnassigned() {
        val records = listOf(
            record(0, UsageEventKind.CONFIGURATION_CHANGED, configuration = cover),
            record(0, UsageEventKind.SCREEN_INTERACTIVE),
            record(0, UsageEventKind.KEYGUARD_HIDDEN),
            record(0, UsageEventKind.ACTIVITY_RESUMED, "app.a", "A"),
            record(1_000, UsageEventKind.ACTIVITY_RESUMED, "app.a", "A"),
            record(3_000, UsageEventKind.ACTIVITY_PAUSED, "app.a", "A"),
            record(4_000, UsageEventKind.ACTIVITY_STOPPED, "app.a", "A"),
        )

        val result = analyzer.analyze(records, 0, 6_000, calibration)

        assertEquals(6_000L, result.coverMillis)
        assertEquals(3_000L, result.apps.single().coverMillis)
    }

    @Test
    fun uncertainPackageDoesNotPreventKnownUnrelatedPackageFromReceivingRankingTime() {
        val records = listOf(
            record(0, UsageEventKind.CONFIGURATION_CHANGED, configuration = cover),
            record(0, UsageEventKind.SCREEN_INTERACTIVE),
            record(0, UsageEventKind.KEYGUARD_HIDDEN),
            record(0, UsageEventKind.ACTIVITY_RESUMED, "app.a", "A"),
            record(1_000, UsageEventKind.ACTIVITY_RESUMED, "app.a", "A"),
            record(2_000, UsageEventKind.ACTIVITY_PAUSED, "app.a", "A"),
            record(3_000, UsageEventKind.ACTIVITY_RESUMED, "app.b", "B"),
        )

        val result = analyzer.analyze(records, 0, 6_000, calibration)

        assertEquals(6_000L, result.coverMillis)
        assertEquals(2_000L, result.apps.first { it.packageName == "app.a" }.coverMillis)
        assertEquals(3_000L, result.apps.first { it.packageName == "app.b" }.coverMillis)
        assertEquals(0L, result.multiResumeMillis)
    }

    @Test
    fun resumedEventRecoversSameClassAttributionAfterAmbiguity() {
        val records = listOf(
            record(0, UsageEventKind.CONFIGURATION_CHANGED, configuration = cover),
            record(0, UsageEventKind.SCREEN_INTERACTIVE),
            record(0, UsageEventKind.KEYGUARD_HIDDEN),
            record(0, UsageEventKind.ACTIVITY_RESUMED, "app.a", "A"),
            record(1_000, UsageEventKind.ACTIVITY_RESUMED, "app.a", "A"),
            record(2_000, UsageEventKind.ACTIVITY_PAUSED, "app.a", "A"),
            record(4_000, UsageEventKind.ACTIVITY_RESUMED, "app.a", "A"),
        )

        val result = analyzer.analyze(records, 0, 6_000, calibration)

        assertEquals(6_000L, result.coverMillis)
        assertEquals(4_000L, result.apps.single().coverMillis)
    }

    @Test
    fun terminalAfterRecoveredDuplicateResumeLeavesLaterOtherPackageOnlyRankingTime() {
        val records = listOf(
            record(0, UsageEventKind.CONFIGURATION_CHANGED, configuration = cover),
            record(0, UsageEventKind.SCREEN_INTERACTIVE),
            record(0, UsageEventKind.KEYGUARD_HIDDEN),
            record(0, UsageEventKind.ACTIVITY_RESUMED, "app.a", "A"),
            record(1_000, UsageEventKind.ACTIVITY_RESUMED, "app.a", "A"),
            record(2_000, UsageEventKind.ACTIVITY_PAUSED, "app.a", "A"),
            record(3_000, UsageEventKind.ACTIVITY_RESUMED, "app.a", "A"),
            record(4_000, UsageEventKind.ACTIVITY_PAUSED, "app.a", "A"),
            record(5_000, UsageEventKind.ACTIVITY_RESUMED, "app.b", "B"),
        )

        val result = analyzer.analyze(records, 0, 7_000, calibration)

        assertEquals(7_000L, result.coverMillis)
        assertEquals(3_000L, result.apps.first { it.packageName == "app.a" }.coverMillis)
        assertEquals(2_000L, result.apps.first { it.packageName == "app.b" }.coverMillis)
        assertEquals(0L, result.multiResumeMillis)
    }

    @Test
    fun stopAfterPausedPredecessorLeavesLaterOtherPackageOnlyRankingTime() {
        val records = listOf(
            record(0, UsageEventKind.CONFIGURATION_CHANGED, configuration = cover),
            record(0, UsageEventKind.SCREEN_INTERACTIVE),
            record(0, UsageEventKind.KEYGUARD_HIDDEN),
            record(0, UsageEventKind.ACTIVITY_RESUMED, "app.a", "A"),
            record(1_000, UsageEventKind.ACTIVITY_PAUSED, "app.a", "A"),
            record(2_000, UsageEventKind.ACTIVITY_RESUMED, "app.a", "A"),
            record(3_000, UsageEventKind.ACTIVITY_STOPPED, "app.a", "A"),
            record(4_000, UsageEventKind.ACTIVITY_RESUMED, "app.b", "B"),
        )

        val result = analyzer.analyze(records, 0, 6_000, calibration)

        assertEquals(6_000L, result.coverMillis)
        assertEquals(2_000L, result.apps.first { it.packageName == "app.a" }.coverMillis)
        assertEquals(2_000L, result.apps.first { it.packageName == "app.b" }.coverMillis)
        assertEquals(0L, result.multiResumeMillis)
    }

    @Test
    fun repeatedTimestampResumePauseResumeLeavesPackageAssignable() {
        val records = listOf(
            record(0, UsageEventKind.CONFIGURATION_CHANGED, configuration = cover),
            record(0, UsageEventKind.SCREEN_INTERACTIVE),
            record(0, UsageEventKind.KEYGUARD_HIDDEN),
            record(
                1_000,
                UsageEventKind.ACTIVITY_RESUMED,
                "app.a",
                "A",
                sequenceAtTimestamp = 0,
            ),
            record(
                1_000,
                UsageEventKind.ACTIVITY_PAUSED,
                "app.a",
                "A",
                sequenceAtTimestamp = 1,
            ),
            record(
                1_000,
                UsageEventKind.ACTIVITY_RESUMED,
                "app.a",
                "A",
                sequenceAtTimestamp = 2,
            ),
        )

        val result = analyzer.analyze(records, 0, 3_000, calibration)

        assertEquals(3_000L, result.coverMillis)
        assertEquals(2_000L, result.apps.single().coverMillis)
    }

    @Test
    fun appAttributionAmbiguityDoesNotReducePostureCoverageAcrossScreenOff() {
        val records = listOf(
            record(0, UsageEventKind.CONFIGURATION_CHANGED, configuration = cover),
            record(0, UsageEventKind.SCREEN_INTERACTIVE),
            record(0, UsageEventKind.KEYGUARD_HIDDEN),
            record(0, UsageEventKind.ACTIVITY_RESUMED, "app.a", "A"),
            record(1_000, UsageEventKind.ACTIVITY_RESUMED, "app.a", "A"),
            record(2_000, UsageEventKind.ACTIVITY_PAUSED, "app.a", "A"),
            record(4_000, UsageEventKind.SCREEN_NON_INTERACTIVE),
            record(5_000, UsageEventKind.SCREEN_INTERACTIVE),
        )

        val result = analyzer.analyze(records, 0, 7_000, calibration)

        assertEquals(6_000L, result.coverMillis)
        assertEquals(0L, result.excludedPostureMillis)
        assertEquals(1f, result.dataCoverageRatio, 0f)
        assertEquals(2_000L, result.apps.single().coverMillis)
    }

    @Test
    fun restartResetsUncertainActivityBeforeNewAttributionEvidence() {
        val records = listOf(
            record(0, UsageEventKind.CONFIGURATION_CHANGED, configuration = cover),
            record(0, UsageEventKind.SCREEN_INTERACTIVE),
            record(0, UsageEventKind.KEYGUARD_HIDDEN),
            record(0, UsageEventKind.ACTIVITY_RESUMED, "app.a", "A"),
            record(1_000, UsageEventKind.ACTIVITY_RESUMED, "app.a", "A"),
            record(2_000, UsageEventKind.ACTIVITY_PAUSED, "app.a", "A"),
            record(3_000, UsageEventKind.DEVICE_STARTUP),
            record(4_000, UsageEventKind.CONFIGURATION_CHANGED, configuration = cover),
            record(4_000, UsageEventKind.SCREEN_INTERACTIVE),
            record(4_000, UsageEventKind.KEYGUARD_HIDDEN),
            record(5_000, UsageEventKind.ACTIVITY_RESUMED, "app.b", "B"),
        )

        val result = analyzer.analyze(records, 0, 7_000, calibration)

        assertEquals(6_000L, result.coverMillis)
        assertEquals(2_000L, result.apps.first { it.packageName == "app.a" }.coverMillis)
        assertEquals(2_000L, result.apps.first { it.packageName == "app.b" }.coverMillis)
        assertEquals(0L, result.multiResumeMillis)
    }

    @Test
    fun collectionGapResetsUncertainActivityBeforeNewAttributionEvidence() {
        val records = listOf(
            record(0, UsageEventKind.CONFIGURATION_CHANGED, configuration = cover),
            record(0, UsageEventKind.SCREEN_INTERACTIVE),
            record(0, UsageEventKind.KEYGUARD_HIDDEN),
            record(0, UsageEventKind.ACTIVITY_RESUMED, "app.a", "A"),
            record(1_000, UsageEventKind.ACTIVITY_RESUMED, "app.a", "A"),
            record(2_000, UsageEventKind.ACTIVITY_PAUSED, "app.a", "A"),
            record(4_000, UsageEventKind.CONFIGURATION_CHANGED, configuration = cover),
            record(4_000, UsageEventKind.SCREEN_INTERACTIVE),
            record(4_000, UsageEventKind.KEYGUARD_HIDDEN),
            record(5_000, UsageEventKind.ACTIVITY_RESUMED, "app.b", "B"),
        )

        val result = analyzer.analyze(
            records = records,
            rangeStartMillis = 0,
            rangeEndMillis = 7_000,
            calibration = calibration,
            collectionGapStarts = listOf(3_000),
        )

        assertEquals(6_000L, result.coverMillis)
        assertEquals(2_000L, result.apps.first { it.packageName == "app.a" }.coverMillis)
        assertEquals(2_000L, result.apps.first { it.packageName == "app.b" }.coverMillis)
        assertEquals(1, result.evidenceGapCount)
        assertEquals(0L, result.multiResumeMillis)
    }

    @Test
    fun calibrationChoosesNearestKnownConfiguration() {
        assertEquals(DisplayPosture.COVER, calibration.classify(configuration(430, 900, 430)))
        assertEquals(DisplayPosture.INNER, calibration.classify(configuration(720, 830, 720)))
    }

    @Test
    fun automaticClassificationUsesTheSixHundredDpBreakpoint() {
        val automatic = Calibration()

        assertEquals(DisplayPosture.COVER, automatic.classify(configuration(443, 994, 443)))
        assertEquals(DisplayPosture.INNER, automatic.classify(configuration(852, 883, 852)))
    }

    @Test
    fun incompleteCalibrationKeepsUsingAutomaticClassification() {
        val coverOnly = Calibration(cover = cover)

        assertEquals(DisplayPosture.COVER, coverOnly.classify(cover))
        assertEquals(DisplayPosture.INNER, coverOnly.classify(inner))
    }

    @Test
    fun classifiesBothDisplaysTheSameAfterRotation() {
        val coverLandscape = configuration(890, 420, 420, orientation = 2)
        val innerLandscape = configuration(820, 730, 730, orientation = 2)

        val coverResult = calibration.classifyWithDetails(coverLandscape)
        val innerResult = calibration.classifyWithDetails(innerLandscape)

        assertEquals(DisplayPosture.COVER, coverResult.posture)
        assertEquals(0, coverResult.coverDistance)
        assertEquals(DisplayPosture.INNER, innerResult.posture)
        assertEquals(0, innerResult.innerDistance)
    }

    @Test
    fun classifiesPixelPartialConfigurationsUsingScreenDimensions() {
        val pixelCalibration = Calibration(
            cover = configuration(443, 994, 443, density = 390),
            inner = configuration(852, 883, 852, density = 390),
        )
        val coverLandscape = configuration(
            width = 994,
            height = 443,
            smallest = 0,
            orientation = 2,
            density = 0,
        )
        val innerPortrait = configuration(
            width = 852,
            height = 883,
            smallest = 0,
            orientation = 1,
            density = 0,
        )

        val coverResult = pixelCalibration.classifyWithDetails(coverLandscape)
        val innerResult = pixelCalibration.classifyWithDetails(innerPortrait)

        assertEquals(DisplayPosture.COVER, coverResult.posture)
        assertEquals(0, coverResult.coverDistance)
        assertEquals(DisplayPosture.INNER, innerResult.posture)
        assertEquals(0, innerResult.innerDistance)
    }

    @Test
    fun partialConfigurationChangesPostureWithoutUnknownTime() {
        val partialInnerLandscape = configuration(
            width = 883,
            height = 852,
            smallest = 0,
            orientation = 2,
            density = 0,
        )
        val records = listOf(
            record(0, UsageEventKind.CONFIGURATION_CHANGED, configuration = cover),
            record(0, UsageEventKind.SCREEN_INTERACTIVE),
            record(0, UsageEventKind.KEYGUARD_HIDDEN),
            record(2_000, UsageEventKind.CONFIGURATION_CHANGED, configuration = partialInnerLandscape),
        )

        val result = analyzer.analyze(records, 0, 10_000, calibration)

        assertEquals(2_000, result.coverMillis)
        assertEquals(8_000, result.innerMillis)
        assertEquals(0, result.excludedPostureMillis)
    }

    @Test
    fun nullAndZeroSizedConfigurationsRemainUnknown() {
        val emptyConfiguration = configuration(
            width = 0,
            height = 0,
            smallest = 0,
            orientation = 0,
            density = 0,
        )

        assertEquals(DisplayPosture.UNKNOWN, calibration.classify(null))
        assertEquals(DisplayPosture.UNKNOWN, calibration.classify(emptyConfiguration))
    }

    @Test
    fun usableConfigurationEventsAreAlwaysCoverOrInner() {
        val records = listOf(
            record(0, UsageEventKind.CONFIGURATION_CHANGED, configuration = cover),
            record(1_000, UsageEventKind.CONFIGURATION_CHANGED, configuration = inner),
        )

        val result = analyzer.analyze(records, 0, 2_000, calibration)

        assertEquals(
            listOf(DisplayPosture.COVER, DisplayPosture.INNER),
            result.postureEvents.asReversed().map { it.posture },
        )
    }

    @Test
    fun excludesTimeBeforeFirstConfigurationAndReportsCoverage() {
        val records = listOf(
            record(0, UsageEventKind.SCREEN_INTERACTIVE),
            record(0, UsageEventKind.KEYGUARD_HIDDEN),
            record(0, UsageEventKind.ACTIVITY_RESUMED, "app.a", "A"),
            record(5_000, UsageEventKind.CONFIGURATION_CHANGED, configuration = cover),
        )

        val result = analyzer.analyze(records, 0, 10_000, calibration)

        assertEquals(5_000, result.excludedPostureMillis)
        assertEquals(
            5_000L,
            result.excludedPostureMillisByReason[UnknownPostureReason.NO_BASELINE],
        )
        assertEquals(5_000, result.coverMillis)
        assertEquals(0.5f, result.dataCoverageRatio, 0f)
    }

    @Test
    fun checkpointSeedsPostureWithoutConfigurationEvent() {
        val records = listOf(
            record(0, UsageEventKind.SCREEN_INTERACTIVE),
            record(0, UsageEventKind.KEYGUARD_HIDDEN),
            record(0, UsageEventKind.ACTIVITY_RESUMED, "app.a", "A"),
        )
        val checkpoints = listOf(
            PostureCheckpoint(
                timestampMillis = 0,
                configuration = cover,
                source = PostureCheckpointSource.MEASUREMENT_START,
            ),
        )

        val result = analyzer.analyze(
            records = records,
            rangeStartMillis = 0,
            rangeEndMillis = 10_000,
            calibration = calibration,
            checkpoints = checkpoints,
        )

        assertEquals(10_000, result.coverMillis)
        assertEquals(0, result.excludedPostureMillis)
        assertEquals(1f, result.dataCoverageRatio, 0f)
    }

    @Test
    fun excludesTimeAfterRestartUntilNextConfiguration() {
        val records = listOf(
            record(0, UsageEventKind.CONFIGURATION_CHANGED, configuration = cover),
            record(0, UsageEventKind.SCREEN_INTERACTIVE),
            record(0, UsageEventKind.KEYGUARD_HIDDEN),
            record(2_000, UsageEventKind.DEVICE_STARTUP),
            record(3_000, UsageEventKind.SCREEN_INTERACTIVE),
            record(3_000, UsageEventKind.KEYGUARD_HIDDEN),
            record(6_000, UsageEventKind.CONFIGURATION_CHANGED, configuration = inner),
        )

        val result = analyzer.analyze(records, 0, 10_000, calibration)

        assertEquals(2_000, result.coverMillis)
        assertEquals(3_000, result.excludedPostureMillis)
        assertEquals(
            3_000L,
            result.excludedPostureMillisByReason[UnknownPostureReason.AFTER_DEVICE_RESTART],
        )
        assertEquals(4_000, result.innerMillis)
    }

    @Test
    fun excludesTimeAfterConfigurationWithoutValues() {
        val records = listOf(
            record(0, UsageEventKind.CONFIGURATION_CHANGED, configuration = cover),
            record(0, UsageEventKind.SCREEN_INTERACTIVE),
            record(0, UsageEventKind.KEYGUARD_HIDDEN),
            record(2_000, UsageEventKind.CONFIGURATION_CHANGED, configuration = null),
            record(5_000, UsageEventKind.CONFIGURATION_CHANGED, configuration = inner),
        )

        val result = analyzer.analyze(records, 0, 10_000, calibration)

        assertEquals(3_000, result.excludedPostureMillis)
        assertEquals(
            3_000L,
            result.excludedPostureMillisByReason[UnknownPostureReason.CONFIGURATION_UNAVAILABLE],
        )
        assertEquals(
            UnknownPostureReason.CONFIGURATION_UNAVAILABLE,
            result.postureEvents.first { it.posture == DisplayPosture.UNKNOWN }.unknownReason,
        )
    }

    @Test
    fun excludesTimeAfterUnusableConfigurationAsConfigurationUnavailable() {
        val unusableConfiguration = configuration(
            width = 0,
            height = 0,
            smallest = 0,
            orientation = 0,
            density = 0,
        )
        val records = listOf(
            record(0, UsageEventKind.CONFIGURATION_CHANGED, configuration = cover),
            record(0, UsageEventKind.SCREEN_INTERACTIVE),
            record(0, UsageEventKind.KEYGUARD_HIDDEN),
            record(2_000, UsageEventKind.CONFIGURATION_CHANGED, configuration = unusableConfiguration),
            record(5_000, UsageEventKind.CONFIGURATION_CHANGED, configuration = inner),
        )

        val result = analyzer.analyze(records, 0, 10_000, calibration)

        assertEquals(3_000L, result.excludedPostureMillis)
        assertEquals(
            3_000L,
            result.excludedPostureMillisByReason[UnknownPostureReason.CONFIGURATION_UNAVAILABLE],
        )
        assertEquals(
            UnknownPostureReason.CONFIGURATION_UNAVAILABLE,
            result.postureEvents.first { it.posture == DisplayPosture.UNKNOWN }.unknownReason,
        )
    }

    @Test
    fun countsOnlyConfirmedCoverAndInnerTransitions() {
        val innerLandscape = configuration(820, 730, 730, orientation = 2)
        val records = listOf(
            record(0, UsageEventKind.CONFIGURATION_CHANGED, configuration = inner),
            record(1_000, UsageEventKind.CONFIGURATION_CHANGED, configuration = cover),
            record(2_000, UsageEventKind.CONFIGURATION_CHANGED, configuration = inner),
            record(3_000, UsageEventKind.CONFIGURATION_CHANGED, configuration = innerLandscape),
            record(4_000, UsageEventKind.CONFIGURATION_CHANGED, configuration = cover),
        )

        val result = analyzer.analyze(records, 0, 5_000, calibration)

        assertEquals(1, result.openedCount)
        assertEquals(2, result.closedCount)
        assertEquals(3, result.foldTransitions.size)
    }

    @Test
    fun unknownConfigurationBreaksTransitionEvidence() {
        val records = listOf(
            record(0, UsageEventKind.CONFIGURATION_CHANGED, configuration = cover),
            record(1_000, UsageEventKind.CONFIGURATION_CHANGED, configuration = null),
            record(2_000, UsageEventKind.CONFIGURATION_CHANGED, configuration = inner),
        )

        val result = analyzer.analyze(records, 0, 3_000, calibration)

        assertEquals(0, result.openedCount)
        assertEquals(0, result.closedCount)
        assertEquals(1, result.evidenceGapCount)
    }

    @Test
    fun restartBreaksTransitionEvidence() {
        val records = listOf(
            record(0, UsageEventKind.CONFIGURATION_CHANGED, configuration = cover),
            record(1_000, UsageEventKind.DEVICE_STARTUP),
            record(2_000, UsageEventKind.CONFIGURATION_CHANGED, configuration = inner),
        )

        val result = analyzer.analyze(records, 0, 3_000, calibration)

        assertEquals(0, result.openedCount)
        assertEquals(1, result.evidenceGapCount)
    }

    @Test
    fun checkpointSeedsTransitionButDoesNotCountAsAnAction() {
        val checkpoints = listOf(
            PostureCheckpoint(
                timestampMillis = 0,
                configuration = cover,
                source = PostureCheckpointSource.MEASUREMENT_START,
            ),
            PostureCheckpoint(
                timestampMillis = 2_000,
                configuration = cover,
                source = PostureCheckpointSource.APP_FOREGROUND,
            ),
        )
        val records = listOf(
            record(1_000, UsageEventKind.CONFIGURATION_CHANGED, configuration = inner),
        )

        val result = analyzer.analyze(records, 0, 3_000, calibration, checkpoints)

        assertEquals(1, result.openedCount)
        assertEquals(0, result.closedCount)
    }

    @Test
    fun splitScreenWindowCheckpointDoesNotReplaceInnerDisplayEvidence() {
        val innerSplitScreenWindow = configuration(width = 380, height = 900, smallest = 380)
        val checkpoints = listOfNotNull(
            PostureCheckpoint(
                timestampMillis = 4_000,
                configuration = innerSplitScreenWindow,
                source = PostureCheckpointSource.APP_FOREGROUND,
            ).takeIf {
                it.configuration.canBePostureEvidence(isInMultiWindowMode = true)
            },
        )
        val records = listOf(
            record(0, UsageEventKind.CONFIGURATION_CHANGED, configuration = cover),
            record(0, UsageEventKind.SCREEN_INTERACTIVE),
            record(0, UsageEventKind.KEYGUARD_HIDDEN),
            record(1_000, UsageEventKind.CONFIGURATION_CHANGED, configuration = inner),
        )

        val result = analyzer.analyze(records, 0, 6_000, calibration, checkpoints)

        assertEquals(DisplayPosture.COVER, calibration.classify(innerSplitScreenWindow))
        assertEquals(1_000L, result.coverMillis)
        assertEquals(5_000L, result.innerMillis)
        assertEquals(1, result.openedCount)
        assertEquals(0, result.closedCount)
    }

    @Test
    fun countsDetectedTransitionsWhileScreenIsOff() {
        val records = listOf(
            record(0, UsageEventKind.CONFIGURATION_CHANGED, configuration = cover),
            record(1_000, UsageEventKind.CONFIGURATION_CHANGED, configuration = inner),
        )

        val result = analyzer.analyze(records, 0, 2_000, calibration)

        assertEquals(1, result.openedCount)
        assertEquals(0L, result.observedPostureMillis)
    }

    @Test
    fun usesSeedPostureAtRangeStartAndExcludesTransitionAtRangeEnd() {
        val records = listOf(
            record(0, UsageEventKind.CONFIGURATION_CHANGED, configuration = cover),
            record(1_000, UsageEventKind.CONFIGURATION_CHANGED, configuration = inner),
            record(2_000, UsageEventKind.CONFIGURATION_CHANGED, configuration = cover),
        )

        val result = analyzer.analyze(records, 1_000, 2_000, calibration)

        assertEquals(1, result.openedCount)
        assertEquals(0, result.closedCount)
    }

    @Test
    fun splitsDailySummariesAtLocalMidnight() {
        val start = Instant.parse("2026-01-01T23:59:00Z").toEpochMilli()
        val end = Instant.parse("2026-01-02T00:01:00Z").toEpochMilli()
        val records = listOf(
            record(start, UsageEventKind.CONFIGURATION_CHANGED, configuration = cover),
            record(start, UsageEventKind.SCREEN_INTERACTIVE),
            record(start, UsageEventKind.KEYGUARD_HIDDEN),
        )

        val result = analyzer.analyze(
            records = records,
            rangeStartMillis = start,
            rangeEndMillis = end,
            calibration = calibration,
            zoneId = ZoneOffset.UTC,
        )

        assertEquals(2, result.dailySummaries.size)
        assertEquals(60_000L, result.dailySummaries[0].coverMillis)
        assertEquals(60_000L, result.dailySummaries[1].coverMillis)
    }

    private fun configuration(
        width: Int,
        height: Int,
        smallest: Int,
        orientation: Int = 1,
        density: Int = 420,
    ) = DisplayConfiguration(
        screenWidthDp = width,
        screenHeightDp = height,
        smallestScreenWidthDp = smallest,
        orientation = orientation,
        densityDpi = density,
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
