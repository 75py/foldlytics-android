package com.nagopy.android.foldlytics.model

import kotlin.math.abs

enum class DisplayPosture {
    COVER,
    INNER,
    UNKNOWN,
}

data class DisplayConfiguration(
    val screenWidthDp: Int,
    val screenHeightDp: Int,
    val smallestScreenWidthDp: Int,
    val orientation: Int,
    val densityDpi: Int,
) {
    val shortSideDp: Int = minOf(screenWidthDp, screenHeightDp)
    val longSideDp: Int = maxOf(screenWidthDp, screenHeightDp)
    val effectiveSmallestScreenWidthDp: Int =
        smallestScreenWidthDp.takeIf { it > 0 } ?: shortSideDp

    fun isUsable(): Boolean =
        screenWidthDp > 0 && screenHeightDp > 0

    /**
     * [screenWidthDp] and [screenHeightDp] describe an app window in multi-window mode. They
     * cannot safely establish the physical display posture there, even when they are usable for
     * current UI diagnostics.
     */
    fun canBePostureEvidence(isInMultiWindowMode: Boolean): Boolean =
        isUsable() && !isInMultiWindowMode
}

data class Calibration(
    val cover: DisplayConfiguration? = null,
    val inner: DisplayConfiguration? = null,
) {
    val validationFailure: CalibrationValidationFailure?
        get() = if (
            cover?.isUsable() == true &&
                inner?.isUsable() == true &&
                !cover.isDistinguishableFrom(inner)
        ) {
            CalibrationValidationFailure.ANCHORS_TOO_CLOSE
        } else {
            null
        }

    val isComplete: Boolean =
        cover?.isUsable() == true &&
            inner?.isUsable() == true &&
            validationFailure == null

    fun withAnchor(
        anchor: CalibrationAnchor,
        configuration: DisplayConfiguration,
    ): CalibrationUpdateResult {
        if (!configuration.isUsable()) {
            return CalibrationUpdateResult.Rejected(
                CalibrationValidationFailure.CONFIGURATION_UNAVAILABLE,
            )
        }
        val usableCalibration = usableAnchorsOnly()
        val updated = when (anchor) {
            CalibrationAnchor.COVER -> usableCalibration.copy(cover = configuration)
            CalibrationAnchor.INNER -> usableCalibration.copy(inner = configuration)
        }
        val failure = updated.validationFailure
        return if (failure == null) {
            CalibrationUpdateResult.Accepted(updated)
        } else {
            CalibrationUpdateResult.Rejected(failure)
        }
    }

    fun usableAnchorsOnly(): Calibration = Calibration(
        cover = cover?.takeIf(DisplayConfiguration::isUsable),
        inner = inner?.takeIf(DisplayConfiguration::isUsable),
    )

    fun classify(configuration: DisplayConfiguration?): DisplayPosture =
        classifyWithDetails(configuration).posture

    fun classifyWithDetails(configuration: DisplayConfiguration?): PostureClassification {
        if (configuration == null || !configuration.isUsable()) {
            return PostureClassification(DisplayPosture.UNKNOWN)
        }

        val coverConfig = cover
        val innerConfig = inner

        if (!isComplete || coverConfig == null || innerConfig == null) {
            return configuration.classifyWithDefaultThreshold()
        }

        val coverDistance = configuration.distanceTo(coverConfig)
        val innerDistance = configuration.distanceTo(innerConfig)
        return PostureClassification(
            posture = if (coverDistance <= innerDistance) {
                DisplayPosture.COVER
            } else {
                DisplayPosture.INNER
            },
            coverDistance = coverDistance,
            innerDistance = innerDistance,
        )
    }

    private fun DisplayConfiguration.distanceTo(other: DisplayConfiguration): Int =
        abs(effectiveSmallestScreenWidthDp - other.effectiveSmallestScreenWidthDp) * 4 +
            abs(shortSideDp - other.shortSideDp) * 2 +
            abs(longSideDp - other.longSideDp)

    /**
     * Configuration dimensions have one-dp resolution. A difference of at most one dp in every
     * input used by [distanceTo] can be caused by integer rounding and cannot safely define two
     * opposite postures. A two-dp difference in any input remains valid so genuinely close display
     * configurations are not rejected unnecessarily.
     */
    private fun DisplayConfiguration.isDistinguishableFrom(
        other: DisplayConfiguration,
    ): Boolean =
        abs(effectiveSmallestScreenWidthDp - other.effectiveSmallestScreenWidthDp) >
            MAX_INDISTINGUISHABLE_DIFFERENCE_DP ||
            abs(shortSideDp - other.shortSideDp) > MAX_INDISTINGUISHABLE_DIFFERENCE_DP ||
            abs(longSideDp - other.longSideDp) > MAX_INDISTINGUISHABLE_DIFFERENCE_DP

    private fun DisplayConfiguration.classifyWithDefaultThreshold(): PostureClassification =
        PostureClassification(
            posture = if (effectiveSmallestScreenWidthDp >= DEFAULT_INNER_MIN_WIDTH_DP) {
                DisplayPosture.INNER
            } else {
                DisplayPosture.COVER
            },
        )

    private companion object {
        const val DEFAULT_INNER_MIN_WIDTH_DP = 600
        const val MAX_INDISTINGUISHABLE_DIFFERENCE_DP = 1
    }
}

enum class CalibrationAnchor {
    COVER,
    INNER,
}

enum class CalibrationValidationFailure {
    CONFIGURATION_UNAVAILABLE,
    ANCHORS_TOO_CLOSE,
}

sealed interface CalibrationUpdateResult {
    data class Accepted(val calibration: Calibration) : CalibrationUpdateResult

    data class Rejected(
        val reason: CalibrationValidationFailure,
    ) : CalibrationUpdateResult
}

data class PostureClassification(
    val posture: DisplayPosture,
    val coverDistance: Int? = null,
    val innerDistance: Int? = null,
)

enum class UnknownPostureReason {
    NO_BASELINE,
    AFTER_DEVICE_RESTART,
    COLLECTION_INTERRUPTION,
    CONFIGURATION_UNAVAILABLE,
}

enum class PostureCheckpointSource {
    APP_LAUNCH,
    APP_FOREGROUND,
    APP_BACKGROUND,
    CALIBRATION_COVER,
    CALIBRATION_INNER,
    MANUAL_REFRESH,
    MEASUREMENT_START,
}

data class PostureCheckpoint(
    val timestampMillis: Long,
    val configuration: DisplayConfiguration,
    val source: PostureCheckpointSource,
)

enum class UsageEventKind {
    ACTIVITY_RESUMED,
    ACTIVITY_PAUSED,
    ACTIVITY_STOPPED,
    CONFIGURATION_CHANGED,
    SCREEN_INTERACTIVE,
    SCREEN_NON_INTERACTIVE,
    KEYGUARD_SHOWN,
    KEYGUARD_HIDDEN,
    DEVICE_STARTUP,
    DEVICE_SHUTDOWN,
    OTHER,
}

data class UsageRecord(
    val timestampMillis: Long,
    val kind: UsageEventKind,
    val packageName: String? = null,
    val className: String? = null,
    val configuration: DisplayConfiguration? = null,
    val rawEventType: Int,
    val sequenceAtTimestamp: Int = 0,
)

data class AppUsage(
    val packageName: String,
    val label: String,
    val coverMillis: Long,
    val innerMillis: Long,
    val excludedMillis: Long,
    val isLauncherApp: Boolean = true,
) {
    val classifiedMillis: Long = coverMillis + innerMillis
    val observedMillis: Long = classifiedMillis + excludedMillis
}

/**
 * A session between a detected cover-to-inner transition and the following inner-to-cover
 * transition.
 *
 * The package map contains only time for intervals that had exactly one distinct resumed package.
 * The difference between [innerActiveMillis] and the map total is intentionally retained as
 * unallocated time and is presented as "Other".
 */
data class InnerDisplaySession(
    val openedAtMillis: Long,
    val openedSequenceAtTimestamp: Int,
    val closedAtMillis: Long?,
    val innerActiveMillis: Long,
    val appUsageMillis: Map<String, Long> = emptyMap(),
) {
    val isComplete: Boolean = closedAtMillis != null
}

data class InnerSessionAppUsage(
    val packageName: String,
    val label: String,
    val innerActiveMillis: Long,
    val isLauncherApp: Boolean = true,
)

data class InnerSessionDetail(
    val openedAtMillis: Long,
    val openedSequenceAtTimestamp: Int,
    val innerActiveMillis: Long,
    val appUsages: List<InnerSessionAppUsage>,
    val otherInnerActiveMillis: Long,
)

data class InnerSessionSummary(
    val rangeStartMillis: Long,
    val rangeEndMillis: Long,
    val detectedOpenCount: Int,
    val completeSessionCount: Int,
    val medianInnerActiveMillis: Long?,
    val averageInnerActiveMillis: Long?,
    val longestInnerActiveMillis: Long?,
    val longSessions: List<InnerSessionDetail> = emptyList(),
)

enum class PostureEventSource {
    CONFIGURATION_CHANGE,
    CHECKPOINT,
    COLLECTION_INTERRUPTION,
    DEVICE_STARTUP,
    DEVICE_SHUTDOWN,
}

data class PostureEvent(
    val timestampMillis: Long,
    val source: PostureEventSource,
    val posture: DisplayPosture,
    val configuration: DisplayConfiguration?,
    val checkpointSource: PostureCheckpointSource? = null,
    val unknownReason: UnknownPostureReason? = null,
    val rawEventType: Int? = null,
    val isBeforeRange: Boolean = false,
    val coverDistance: Int? = null,
    val innerDistance: Int? = null,
)

enum class FoldTransitionDirection {
    OPENED,
    CLOSED,
}

data class FoldTransition(
    val timestampMillis: Long,
    val direction: FoldTransitionDirection,
    val from: DisplayPosture,
    val to: DisplayPosture,
)

data class DailyPostureSummary(
    val dayStartMillis: Long,
    val dayEndMillis: Long,
    val zoneId: String,
    val coverMillis: Long,
    val innerMillis: Long,
    val excludedMillis: Long,
    val openedCount: Int,
    val closedCount: Int,
    val evidenceGapCount: Int,
) {
    val classifiedMillis: Long = coverMillis + innerMillis
    val observedMillis: Long = classifiedMillis + excludedMillis
    val innerRatio: Float =
        if (classifiedMillis == 0L) 0f else innerMillis.toFloat() / classifiedMillis
}

data class DailyAppUsageSummary(
    val dayStartMillis: Long,
    val dayEndMillis: Long,
    val zoneId: String,
    val packageName: String,
    val coverMillis: Long,
    val innerMillis: Long,
    val excludedMillis: Long,
) {
    val classifiedMillis: Long = coverMillis + innerMillis
    val observedMillis: Long = classifiedMillis + excludedMillis
}

enum class LongTermPeriod(val days: Long) {
    DAYS_7(7),
    DAYS_30(30),
    DAYS_90(90),
    DAYS_365(365),
    DAYS_1095(1_095),
}

enum class AnalysisPeriod(
    val hours: Int? = null,
    val longTermPeriod: LongTermPeriod? = null,
) {
    HOURS_1(hours = 1),
    HOURS_6(hours = 6),
    HOURS_24(hours = 24),
    DAYS_7(longTermPeriod = LongTermPeriod.DAYS_7),
    DAYS_30(longTermPeriod = LongTermPeriod.DAYS_30),
    DAYS_90(longTermPeriod = LongTermPeriod.DAYS_90),
    DAYS_365(longTermPeriod = LongTermPeriod.DAYS_365),
    CUSTOM,
    ;

    val showsTrends: Boolean
        get() = longTermPeriod != null || this == CUSTOM
    val diagnosticHours: Int = hours ?: 24
}

data class CustomAnalysisRange(
    val startMillis: Long,
    val endMillis: Long,
)

data class LongTermBucket(
    val startMillis: Long,
    val endMillis: Long,
    val coverMillis: Long,
    val innerMillis: Long,
    val excludedMillis: Long,
    val openedCount: Int,
    val closedCount: Int,
    val observedDayCount: Int,
    val evidenceGapDayCount: Int,
) {
    val classifiedMillis: Long = coverMillis + innerMillis
    val innerRatio: Float =
        if (classifiedMillis == 0L) 0f else innerMillis.toFloat() / classifiedMillis
}

data class LongTermInsights(
    val rangeStartMillis: Long,
    val rangeEndMillis: Long,
    val coverMillis: Long,
    val innerMillis: Long,
    val excludedMillis: Long,
    val openedCount: Int,
    val closedCount: Int,
    val calendarDayCount: Int,
    val observedDayCount: Int,
    val innerUsedDayCount: Int,
    val evidenceGapDayCount: Int,
    val buckets: List<LongTermBucket>,
    val firstThirtyDayInnerRatio: Float?,
    val recentThirtyDayInnerRatio: Float?,
) {
    val classifiedMillis: Long = coverMillis + innerMillis
    val observedMillis: Long = classifiedMillis + excludedMillis
    val innerRatio: Float =
        if (classifiedMillis == 0L) 0f else innerMillis.toFloat() / classifiedMillis
    val dataCoverageRatio: Float =
        if (observedMillis == 0L) 0f else classifiedMillis.toFloat() / observedMillis
    val averageOpenedPerObservedDay: Float =
        if (observedDayCount == 0) 0f else openedCount.toFloat() / observedDayCount
    val thirtyDayInnerRatioDelta: Float? =
        if (firstThirtyDayInnerRatio == null || recentThirtyDayInnerRatio == null) {
            null
        } else {
            recentThirtyDayInnerRatio - firstThirtyDayInnerRatio
    }
}

data class PeriodUsageSummary(
    val period: AnalysisPeriod,
    val rangeStartMillis: Long,
    val rangeEndMillis: Long,
    val coverMillis: Long,
    val innerMillis: Long,
    val excludedMillis: Long,
    val openedCount: Int,
    val closedCount: Int,
    val apps: List<AppUsage>,
) {
    val classifiedMillis: Long = coverMillis + innerMillis
    val observedMillis: Long = classifiedMillis + excludedMillis
    val innerRatio: Float =
        if (classifiedMillis == 0L) 0f else innerMillis.toFloat() / classifiedMillis
    val dataCoverageRatio: Float =
        if (observedMillis == 0L) 0f else classifiedMillis.toFloat() / observedMillis
}

data class CollectionHealth(
    val recordedAttemptCount: Int,
    val unsuccessfulAttemptCount: Int,
    val longestSuccessfulSyncGapMillis: Long?,
    val collectionInterruptionCount: Int,
)

data class UsageAnalysis(
    val rangeStartMillis: Long,
    val rangeEndMillis: Long,
    val coverMillis: Long,
    val innerMillis: Long,
    val excludedPostureMillis: Long,
    val excludedPostureMillisByReason: Map<UnknownPostureReason, Long>,
    val openedCount: Int,
    val closedCount: Int,
    val evidenceGapCount: Int,
    val foldTransitions: List<FoldTransition>,
    val dailySummaries: List<DailyPostureSummary>,
    val dailyAppSummaries: List<DailyAppUsageSummary> = emptyList(),
    val apps: List<AppUsage>,
    val postureEvents: List<PostureEvent>,
    val eventCount: Int,
    val multiResumeMillis: Long,
) {
    val classifiedPostureMillis: Long = coverMillis + innerMillis
    val observedPostureMillis: Long = classifiedPostureMillis + excludedPostureMillis
    val dataCoverageRatio: Float =
        if (observedPostureMillis == 0L) {
            0f
        } else {
            classifiedPostureMillis.toFloat() / observedPostureMillis
        }
    val innerRatio: Float =
        if (classifiedPostureMillis == 0L) {
            0f
        } else {
            innerMillis.toFloat() / classifiedPostureMillis
        }
}
