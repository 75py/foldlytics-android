package com.nagopy.android.foldlytics

import com.nagopy.android.foldlytics.model.AnalysisPeriod
import com.nagopy.android.foldlytics.model.Calibration
import com.nagopy.android.foldlytics.model.CalibrationValidationFailure
import com.nagopy.android.foldlytics.model.CollectionHealth
import com.nagopy.android.foldlytics.model.DEFAULT_ANALYSIS_PERIODS
import com.nagopy.android.foldlytics.model.DisplayConfiguration
import com.nagopy.android.foldlytics.model.DisplayPosture
import com.nagopy.android.foldlytics.model.InnerSessionSummary
import com.nagopy.android.foldlytics.model.LongTermInsights
import com.nagopy.android.foldlytics.model.PeriodUsageSummary
import com.nagopy.android.foldlytics.model.UsageAnalysis

data class FoldFeatureSnapshot(
    val present: Boolean = false,
    val state: String = "—",
    val orientation: String = "—",
    val occlusion: String = "—",
    val bounds: String = "—",
)

enum class MainUiErrorKind {
    SYNC,
    ANALYSIS,
    CHECKPOINT,
}

data class MainUiError(
    val kind: MainUiErrorKind,
    val message: String,
)

data class MainUiState(
    val hasUsageAccess: Boolean = false,
    val currentConfiguration: DisplayConfiguration? = null,
    val currentConfigurationCanBePostureEvidence: Boolean = false,
    val calibration: Calibration = Calibration(),
    val calibrationValidationFailure: CalibrationValidationFailure? = null,
    val currentPosture: DisplayPosture = DisplayPosture.UNKNOWN,
    val foldFeature: FoldFeatureSnapshot = FoldFeatureSnapshot(),
    val hingeSensorAvailable: Boolean = false,
    val hingeAngle: Float? = null,
    val selectedPeriod: AnalysisPeriod = AnalysisPeriod.HOURS_24,
    val availablePeriods: Set<AnalysisPeriod> = DEFAULT_ANALYSIS_PERIODS,
    val recordRangeStartMillis: Long? = null,
    val recordRangeEndMillis: Long? = null,
    val customRangeStartMillis: Long? = null,
    val customRangeEndMillis: Long? = null,
    val isLoading: Boolean = false,
    val isAnalysisLoading: Boolean = false,
    val analysis: UsageAnalysis? = null,
    val periodSummary: PeriodUsageSummary? = null,
    val innerSessionSummary: InnerSessionSummary? = null,
    val longTermInsights: LongTermInsights? = null,
    val collectionHealth: CollectionHealth? = null,
    val lastSuccessfulSyncMillis: Long? = null,
    val lastSyncQueryBeginMillis: Long? = null,
    val lastSyncInsertedEventCount: Int = 0,
    val error: MainUiError? = null,
)
