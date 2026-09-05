package com.nagopy.android.foldlytics.data

import com.nagopy.android.foldlytics.model.AnalysisPeriod
import com.nagopy.android.foldlytics.model.AppUsage
import com.nagopy.android.foldlytics.model.Calibration
import com.nagopy.android.foldlytics.model.CollectionHealth
import com.nagopy.android.foldlytics.model.CustomAnalysisRange
import com.nagopy.android.foldlytics.model.DEFAULT_ANALYSIS_PERIODS
import com.nagopy.android.foldlytics.model.DailyPostureSummary
import com.nagopy.android.foldlytics.model.InnerSessionSummary
import com.nagopy.android.foldlytics.model.LongTermInsights
import com.nagopy.android.foldlytics.model.PeriodUsageSummary
import com.nagopy.android.foldlytics.model.UsageAnalysis
import com.nagopy.android.foldlytics.model.availableAnalysisPeriods
import com.nagopy.android.foldlytics.model.isValidCustomAnalysisRange
import java.time.ZoneId
import kotlinx.coroutines.flow.first

/** Inputs that identify one stored-analysis result. */
data class StoredAnalysisRequest(
    val period: AnalysisPeriod,
    val customRange: CustomAnalysisRange?,
    val calibration: Calibration,
    val syncState: UsageSyncState?,
    val checkpointRevision: Long,
)

/** Everything one stored-analysis pass produces for the screen. */
data class StoredAnalysisSnapshot(
    val selectedPeriod: AnalysisPeriod,
    val availablePeriods: Set<AnalysisPeriod>,
    val recordRangeStartMillis: Long?,
    val recordRangeEndMillis: Long?,
    val customRange: CustomAnalysisRange?,
    val analysis: UsageAnalysis?,
    val periodSummary: PeriodUsageSummary?,
    val innerSessionSummary: InnerSessionSummary?,
    val longTermInsights: LongTermInsights?,
    val collectionHealth: CollectionHealth?,
)

/**
 * Reads the on-device database and turns it into the analysis results the screen displays. This
 * performs blocking database and aggregation work, so callers dispatch it off the main thread.
 */
class StoredAnalysisLoader(
    private val syncRepository: UsageSyncRepository,
    private val checkpointRepository: PostureCheckpointRepository,
    private val dailySummaryRepository: DailySummaryRepository,
    private val packageLabel: (String) -> String,
    private val isLauncherApp: (String) -> Boolean,
    private val currentTimeMillis: () -> Long = System::currentTimeMillis,
) {
    private val analyzer = UsageAnalyzer(
        packageLabel = packageLabel,
        isLauncherApp = isLauncherApp,
    )
    private val longTermAnalyzer = LongTermAnalyzer()
    private val innerSessionSummarizer = InnerSessionSummarizer(
        packageLabel = packageLabel,
        isLauncherApp = isLauncherApp,
    )

    /**
     * Loads the saved daily history, rebuilding stale aggregates first. Unlike [load] this reads
     * the current sync state itself, so it does not depend on an analysis pass having already run
     * in this process.
     */
    suspend fun loadSavedDailyHistory(
        calibration: Calibration,
        zoneId: ZoneId,
    ): List<DailyPostureSummary> {
        val syncState = syncRepository.observeSyncState().first() ?: return emptyList()
        val checkpointRevision = checkpointRepository.observeRevision().first()
        val syncAttempts = syncRepository.loadSyncAttempts(
            beginMillis = 0L,
            endMillis = currentTimeMillis().endExclusive(),
        )
        return dailySummaryRepository.ensureUpToDate(
            calibration = calibration,
            syncedThroughMillis = syncState.lastSuccessfulEndMillis,
            syncQueryBeginMillis = syncState.lastQueryBeginMillis,
            checkpointRevision = checkpointRevision,
            zoneId = zoneId,
            collectionGapStarts = detectCollectionGaps(syncAttempts)
                .map(CollectionGap::startMillis),
        )
    }

    suspend fun load(
        request: StoredAnalysisRequest,
        zoneId: ZoneId,
    ): StoredAnalysisSnapshot {
        val syncState = request.syncState
        val currentMillis = currentTimeMillis()
        val allSyncAttempts = syncRepository.loadSyncAttempts(
            beginMillis = 0L,
            endMillis = currentMillis.endExclusive(),
        )
        if (syncState == null) {
            return StoredAnalysisSnapshot(
                selectedPeriod = request.period.takeIf { it in DEFAULT_ANALYSIS_PERIODS }
                    ?: AnalysisPeriod.HOURS_24,
                availablePeriods = DEFAULT_ANALYSIS_PERIODS,
                recordRangeStartMillis = null,
                recordRangeEndMillis = null,
                customRange = null,
                analysis = null,
                periodSummary = null,
                innerSessionSummary = null,
                longTermInsights = null,
                collectionHealth = longTermAnalyzer.collectionHealth(allSyncAttempts),
            )
        }
        val window = createUsageAnalysisWindow(
            periodHours = request.period.diagnosticHours,
            syncedThroughMillis = syncState.lastSuccessfulEndMillis,
        )
        val records = syncRepository.loadRecordsForAnalysis(
            window.seedStartMillis,
            window.rangeEndMillis,
        )
        val checkpoints = checkpointRepository.loadForAnalysis(
            window.seedStartMillis,
            window.rangeEndMillis,
        )
        val deviceStateCheckpoints = syncRepository.loadDeviceStateCheckpointsForAnalysis(
            window.seedStartMillis,
            window.rangeEndMillis,
        )
        val collectionGaps = detectCollectionGaps(allSyncAttempts)
        val dailySummaries = dailySummaryRepository.ensureUpToDate(
            calibration = request.calibration,
            syncedThroughMillis = syncState.lastSuccessfulEndMillis,
            syncQueryBeginMillis = syncState.lastQueryBeginMillis,
            checkpointRevision = request.checkpointRevision,
            zoneId = zoneId,
            collectionGapStarts = collectionGaps.map(CollectionGap::startMillis),
        )
        val recordRangeStartMillis = dailySummaries
            .minOfOrNull(DailyPostureSummary::dayStartMillis)
        val recordRangeEndMillis = syncState.lastSuccessfulEndMillis.takeIf {
            recordRangeStartMillis != null && it > recordRangeStartMillis
        }
        val availablePeriods = availableAnalysisPeriods(
            recordRangeStartMillis = recordRangeStartMillis,
            recordRangeEndMillis = recordRangeEndMillis,
            zoneId = zoneId,
        )
        val validCustomRange = request.customRange?.takeIf { range ->
            recordRangeStartMillis != null &&
                recordRangeEndMillis != null &&
                isValidCustomAnalysisRange(
                    range = range,
                    recordRangeStartMillis = recordRangeStartMillis,
                    recordRangeEndMillis = recordRangeEndMillis,
                    zoneId = zoneId,
                )
        }
        val effectivePeriod = request.period.takeIf { period ->
            period in availablePeriods &&
                (period != AnalysisPeriod.CUSTOM || validCustomRange != null)
        } ?: AnalysisPeriod.HOURS_24
        val diagnosticAnalysis = analyzer.analyze(
            records = records,
            rangeStartMillis = window.rangeStartMillis,
            rangeEndMillis = window.rangeEndMillis,
            calibration = request.calibration,
            checkpoints = checkpoints,
            zoneId = zoneId,
            collectionGapStarts = collectionGaps.map(CollectionGap::startMillis),
            deviceStateCheckpoints = deviceStateCheckpoints,
        )
        val longTermInsights = if (effectivePeriod == AnalysisPeriod.CUSTOM) {
            val range = requireNotNull(validCustomRange)
            longTermAnalyzer.analyzeRange(
                summaries = dailySummaries,
                rangeStartMillis = range.startMillis,
                rangeEndMillis = minOf(range.endMillis, syncState.lastSuccessfulEndMillis),
                recordingEndMillis = syncState.lastSuccessfulEndMillis,
                zoneId = zoneId,
            )
        } else {
            effectivePeriod.longTermPeriod?.let { period ->
                longTermAnalyzer.analyze(
                    summaries = dailySummaries,
                    period = period,
                    rangeEndMillis = syncState.lastSuccessfulEndMillis,
                    zoneId = zoneId,
                )
            }
        }
        val periodSummary = if (longTermInsights == null) {
            diagnosticAnalysis.toPeriodSummary(effectivePeriod)
        } else {
            val apps = dailySummaryRepository.loadAggregatedAppUsage(
                beginMillis = longTermInsights.rangeStartMillis,
                endMillis = longTermInsights.rangeEndMillis,
            ).map { stored ->
                AppUsage(
                    packageName = stored.packageName,
                    label = packageLabel(stored.packageName),
                    coverMillis = stored.coverMillis,
                    innerMillis = stored.innerMillis,
                    excludedMillis = stored.excludedMillis,
                    isLauncherApp = isLauncherApp(stored.packageName),
                )
            }.sortedWith(
                compareByDescending<AppUsage> { it.classifiedMillis }
                    .thenByDescending { it.observedMillis },
            )
            longTermInsights.toPeriodSummary(effectivePeriod, apps)
        }
        val selectedRangeStart = longTermInsights?.rangeStartMillis ?: window.rangeStartMillis
        val selectedRangeEnd =
            longTermInsights?.rangeEndMillis ?: syncState.lastSuccessfulEndMillis
        val innerSessionSummary = innerSessionSummarizer.summarize(
            sessions = dailySummaryRepository.loadCompleteInnerSessions(
                beginMillis = selectedRangeStart,
                endMillis = selectedRangeEnd,
            ),
            rangeStartMillis = selectedRangeStart,
            rangeEndMillis = selectedRangeEnd,
            detectedOpenCount = periodSummary.openedCount,
        )
        val periodSyncAttempts = collectionHealthAttemptsForRange(
            attempts = allSyncAttempts,
            rangeStartMillis = selectedRangeStart,
            rangeEndMillis = selectedRangeEnd,
            currentMillis = currentMillis,
            isCustomRange = effectivePeriod == AnalysisPeriod.CUSTOM,
        )
        return StoredAnalysisSnapshot(
            selectedPeriod = effectivePeriod,
            availablePeriods = availablePeriods,
            recordRangeStartMillis = recordRangeStartMillis,
            recordRangeEndMillis = recordRangeEndMillis,
            customRange = validCustomRange,
            analysis = diagnosticAnalysis,
            periodSummary = periodSummary,
            innerSessionSummary = innerSessionSummary,
            longTermInsights = longTermInsights,
            collectionHealth = longTermAnalyzer.collectionHealth(periodSyncAttempts),
        )
    }

    private fun UsageAnalysis.toPeriodSummary(period: AnalysisPeriod): PeriodUsageSummary =
        PeriodUsageSummary(
            period = period,
            rangeStartMillis = rangeStartMillis,
            rangeEndMillis = rangeEndMillis,
            coverMillis = coverMillis,
            innerMillis = innerMillis,
            excludedMillis = excludedPostureMillis,
            openedCount = openedCount,
            closedCount = closedCount,
            apps = apps,
        )

    private fun LongTermInsights.toPeriodSummary(
        period: AnalysisPeriod,
        apps: List<AppUsage>,
    ): PeriodUsageSummary = PeriodUsageSummary(
        period = period,
        rangeStartMillis = rangeStartMillis,
        rangeEndMillis = rangeEndMillis,
        coverMillis = coverMillis,
        innerMillis = innerMillis,
        excludedMillis = excludedMillis,
        openedCount = openedCount,
        closedCount = closedCount,
        apps = apps,
    )

    private fun Long.endExclusive(): Long = if (this == Long.MAX_VALUE) this else this + 1L
}
