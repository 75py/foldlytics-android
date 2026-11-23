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
import com.nagopy.android.foldlytics.model.PostureCheckpoint
import com.nagopy.android.foldlytics.model.PostureEvent
import com.nagopy.android.foldlytics.model.UsageAnalysis
import com.nagopy.android.foldlytics.model.availableAnalysisPeriods
import com.nagopy.android.foldlytics.model.isValidCustomAnalysisRange
import com.nagopy.android.foldlytics.model.resolveCalendarAnalysisRange
import java.time.ZoneId
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.first

/** Inputs that identify one stored-analysis result. */
data class StoredAnalysisRequest(
    val period: AnalysisPeriod,
    val customRange: CustomAnalysisRange?,
    val calibration: Calibration,
    val syncState: UsageSyncState?,
    val checkpointRevision: Long,
)

/**
 * The inputs of one displayed diagnostic analysis except its usage records. Startup analysis
 * replaces old history with a compact seed, so diagnostics replay the complete posture history
 * with these inputs only when they are shown or shared.
 */
data class DiagnosticHistoryRequest(
    val calibration: Calibration,
    val rangeStartMillis: Long,
    val rangeEndMillis: Long,
    val checkpoints: List<PostureCheckpoint>,
    val collectionGapStarts: List<Long>,
    /** The usage-event revision the analysis read, or null when the store cannot tell. */
    val sourceRevision: Long? = null,
)

/**
 * The stored usage events changed after the analysis behind a [DiagnosticHistoryRequest] was
 * read, so its complete history would not match it. Reload the analysis and request again.
 */
class StaleDiagnosticHistoryException : IllegalStateException(
    "Usage events changed after the displayed analysis was read",
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
    val diagnosticHistory: DiagnosticHistoryRequest? = null,
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
    ): List<DailyPostureSummary> = dailySummaryRepository.withDatabaseSnapshot {
        loadSavedDailyHistoryInSnapshot(calibration, zoneId)
    }

    private suspend fun loadSavedDailyHistoryInSnapshot(
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

    /**
     * Returns the posture events the full-history analysis behind [request] would list, including
     * every lookback event before its range. Posture events depend only on configuration and
     * restart records, checkpoints and collection gaps, so this replays those without activity.
     */
    suspend fun loadDiagnosticPostureEvents(
        request: DiagnosticHistoryRequest,
        zoneId: ZoneId,
    ): List<PostureEvent> {
        // Only the reads hold the transaction; the replay below runs after it ends.
        val (records, revision) = dailySummaryRepository.withDatabaseSnapshot {
            syncRepository.loadPostureEvidenceBefore(request.rangeEndMillis) to
                syncRepository.loadUsageEventRevision()
        }
        if (request.sourceRevision != null && revision != request.sourceRevision) {
            throw StaleDiagnosticHistoryException()
        }
        val job = currentCoroutineContext()[Job]
        return analyzer.analyze(
            records = records,
            rangeStartMillis = request.rangeStartMillis,
            rangeEndMillis = request.rangeEndMillis,
            calibration = request.calibration,
            checkpoints = request.checkpoints,
            zoneId = zoneId,
            collectionGapStarts = request.collectionGapStarts,
            checkCancelled = { job?.ensureActive() },
        ).postureEvents
    }

    suspend fun load(
        request: StoredAnalysisRequest,
        zoneId: ZoneId,
    ): StoredAnalysisSnapshot = dailySummaryRepository.withDatabaseSnapshot {
        loadInSnapshot(request, zoneId)
    }

    private suspend fun loadInSnapshot(
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
        val collectionGaps = detectCollectionGaps(allSyncAttempts)
        return dailySummaryRepository.withUpToDateSnapshot(
            calibration = request.calibration,
            syncedThroughMillis = syncState.lastSuccessfulEndMillis,
            syncQueryBeginMillis = syncState.lastQueryBeginMillis,
            checkpointRevision = request.checkpointRevision,
            zoneId = zoneId,
            collectionGapStarts = collectionGaps.map(CollectionGap::startMillis),
        ) {
            // Read in this transaction, so a later history read can prove it saw the same events.
            val sourceRevision = syncRepository.loadUsageEventRevision()
            val summaries = dailySummaries
            val recordRangeStartMillis = summaries
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
            val calendarRange = effectivePeriod.calendarDays?.let { days ->
                resolveCalendarAnalysisRange(
                    days = days,
                    nowMillis = currentMillis,
                    recordRangeStartMillis = recordRangeStartMillis,
                    syncedThroughMillis = syncState.lastSuccessfulEndMillis,
                    zoneId = zoneId,
                )
            }
            val longTermInsights = if (calendarRange != null) {
                longTermAnalyzer.analyzeCalendarRange(
                    summaries = summaries,
                    range = calendarRange,
                    syncedThroughMillis = syncState.lastSuccessfulEndMillis,
                    zoneId = zoneId,
                )
            } else if (effectivePeriod == AnalysisPeriod.CUSTOM) {
                val range = requireNotNull(validCustomRange)
                longTermAnalyzer.analyzeRange(
                    summaries = summaries,
                    rangeStartMillis = range.startMillis,
                    rangeEndMillis = minOf(range.endMillis, syncState.lastSuccessfulEndMillis),
                    recordingEndMillis = syncState.lastSuccessfulEndMillis,
                    zoneId = zoneId,
                )
            } else {
                effectivePeriod.longTermPeriod?.let { period ->
                    longTermAnalyzer.analyze(
                        summaries = summaries,
                        period = period,
                        rangeEndMillis = syncState.lastSuccessfulEndMillis,
                        zoneId = zoneId,
                    )
                }
            }
            val selectedRangeStart = longTermInsights?.rangeStartMillis ?: window.rangeStartMillis
            val selectedRangeEnd =
                longTermInsights?.rangeEndMillis ?: syncState.lastSuccessfulEndMillis
            val cachedSessions = loadCompleteInnerSessions(
                beginMillis = selectedRangeStart,
                endMillis = selectedRangeEnd,
            )
            val selectedSessions = innerSessionSummarizer.selectLongSessions(cachedSessions)
            val seedStart = minOf(
                window.seedStartMillis,
                selectedSessions.minOfOrNull { it.openedAtMillis } ?: window.seedStartMillis,
            )
            // One historical seed read supplies both diagnostic analysis and selected details.
            // Every gap is applied below, so the store may compact history before the seed start.
            val records = syncRepository.loadRecordsForAnalysis(
                beginMillis = seedStart,
                endMillis = window.rangeEndMillis,
                collectionGapStarts = collectionGaps.map(CollectionGap::startMillis),
            )
            val checkpoints = checkpointRepository.loadForAnalysis(seedStart, window.rangeEndMillis)
            val deviceStateCheckpoints = syncRepository.loadDeviceStateCheckpointsForAnalysis(
                seedStart,
                window.rangeEndMillis,
            )
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
            val periodSummary = if (longTermInsights == null) {
                diagnosticAnalysis.toPeriodSummary(effectivePeriod)
            } else {
                val apps = loadAggregatedAppUsage(
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
                    .copy(calendarRange = calendarRange)
            }
            val replayedSessions = replaySelectedInnerSessions(
                selectedSessions = selectedSessions,
                records = records,
                checkpoints = checkpoints,
                deviceStateCheckpoints = deviceStateCheckpoints,
                collectionGapStarts = collectionGaps.map(CollectionGap::startMillis),
                calibration = request.calibration,
            ).associateBy { it.openedAtMillis to it.openedSequenceAtTimestamp }
            val innerSessionSummary = innerSessionSummarizer.summarize(
                sessions = cachedSessions.map { cached ->
                    replayedSessions[cached.openedAtMillis to cached.openedSequenceAtTimestamp]
                        ?: cached
                },
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
            StoredAnalysisSnapshot(
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
                diagnosticHistory = DiagnosticHistoryRequest(
                    calibration = request.calibration,
                    rangeStartMillis = window.rangeStartMillis,
                    rangeEndMillis = window.rangeEndMillis,
                    checkpoints = checkpoints,
                    collectionGapStarts = collectionGaps.map(CollectionGap::startMillis),
                    sourceRevision = sourceRevision,
                ),
            )
        }
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
