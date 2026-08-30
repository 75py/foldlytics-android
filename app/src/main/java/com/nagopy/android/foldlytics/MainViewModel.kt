package com.nagopy.android.foldlytics

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.nagopy.android.foldlytics.data.CalibrationStore
import com.nagopy.android.foldlytics.data.CollectionGap
import com.nagopy.android.foldlytics.data.InnerSessionSummarizer
import com.nagopy.android.foldlytics.data.LongTermAnalyzer
import com.nagopy.android.foldlytics.data.LongTermCsvWriter
import com.nagopy.android.foldlytics.data.UsageAnalyzer
import com.nagopy.android.foldlytics.data.UsageReadUnavailableReason
import com.nagopy.android.foldlytics.data.UsageSyncResult
import com.nagopy.android.foldlytics.data.UsageSyncState
import com.nagopy.android.foldlytics.data.createUsageAnalysisWindow
import com.nagopy.android.foldlytics.data.detectCollectionGaps
import com.nagopy.android.foldlytics.data.toDisplayConfiguration
import com.nagopy.android.foldlytics.model.AnalysisPeriod
import com.nagopy.android.foldlytics.model.AppUsage
import com.nagopy.android.foldlytics.model.Calibration
import com.nagopy.android.foldlytics.model.CollectionHealth
import com.nagopy.android.foldlytics.model.CustomAnalysisRange
import com.nagopy.android.foldlytics.model.DailyPostureSummary
import com.nagopy.android.foldlytics.model.DisplayConfiguration
import com.nagopy.android.foldlytics.model.DisplayPosture
import com.nagopy.android.foldlytics.model.InnerSessionSummary
import com.nagopy.android.foldlytics.model.LongTermInsights
import com.nagopy.android.foldlytics.model.PeriodUsageSummary
import com.nagopy.android.foldlytics.model.PostureCheckpoint
import com.nagopy.android.foldlytics.model.PostureCheckpointSource
import com.nagopy.android.foldlytics.model.UnknownPostureReason
import com.nagopy.android.foldlytics.model.UsageAnalysis
import com.nagopy.android.foldlytics.model.availableAnalysisPeriods
import com.nagopy.android.foldlytics.model.isValidCustomAnalysisRange
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class FoldFeatureSnapshot(
    val present: Boolean = false,
    val state: String = "—",
    val orientation: String = "—",
    val occlusion: String = "—",
    val bounds: String = "—",
)

private val DEFAULT_AVAILABLE_PERIODS = AnalysisPeriod.entries
    .filterTo(mutableSetOf()) { it.hours != null }

data class MainUiState(
    val hasUsageAccess: Boolean = false,
    val currentConfiguration: DisplayConfiguration? = null,
    val calibration: Calibration = Calibration(),
    val currentPosture: DisplayPosture = DisplayPosture.UNKNOWN,
    val foldFeature: FoldFeatureSnapshot = FoldFeatureSnapshot(),
    val hingeSensorAvailable: Boolean = false,
    val hingeAngle: Float? = null,
    val selectedPeriod: AnalysisPeriod = AnalysisPeriod.HOURS_24,
    val availablePeriods: Set<AnalysisPeriod> = AnalysisPeriod.entries
        .filterTo(mutableSetOf()) { it.hours != null },
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
    val errorMessage: String? = null,
)

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val foldlyticsApplication = application as FoldlyticsApplication
    private val calibrationStore = CalibrationStore(application)
    private val checkpointRepository = foldlyticsApplication.postureCheckpointRepository
    private val dailySummaryRepository = foldlyticsApplication.dailySummaryRepository
    private val syncRepository = foldlyticsApplication.usageSyncRepository
    private val analyzer = UsageAnalyzer(
        packageLabel = foldlyticsApplication.usageEventReader::packageLabel,
        isLauncherApp = foldlyticsApplication.usageEventReader::isLauncherApp,
    )
    private val longTermAnalyzer = LongTermAnalyzer()
    private val innerSessionSummarizer = InnerSessionSummarizer(
        packageLabel = foldlyticsApplication.usageEventReader::packageLabel,
        isLauncherApp = foldlyticsApplication.usageEventReader::isLauncherApp,
    )
    private val initialCalibration = calibrationStore.load()
    private val selectedPeriod = MutableStateFlow(AnalysisPeriod.HOURS_24)
    private val customRange = MutableStateFlow<CustomAnalysisRange?>(null)
    private val activeCalibration = MutableStateFlow(initialCalibration)
    private val analysisRevision = MutableStateFlow(0L)
    private var refreshJob: Job? = null
    @Volatile
    private var latestDailySummaries: List<DailyPostureSummary> = emptyList()

    private val _uiState = MutableStateFlow(
        MainUiState(
            currentConfiguration = application.resources.configuration.toDisplayConfiguration(),
            calibration = initialCalibration,
        ),
    )
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    init {
        recalculateCurrentPosture()
        viewModelScope.launch {
            observeStoredAnalysis()
        }
        saveCurrentCheckpoint(PostureCheckpointSource.APP_LAUNCH)
        checkPermissionAndRefresh()
    }

    fun checkPermissionAndRefresh() {
        val hasAccess = syncRepository.hasUsageAccess()
        _uiState.update { it.copy(hasUsageAccess = hasAccess) }
        if (hasAccess) {
            refresh()
        } else {
            refreshJob?.cancel()
            _uiState.update { it.copy(isLoading = false) }
        }
    }

    fun updateConfiguration(configuration: DisplayConfiguration) {
        _uiState.update { it.copy(currentConfiguration = configuration) }
        recalculateCurrentPosture()
    }

    fun updateFoldFeature(snapshot: FoldFeatureSnapshot) {
        _uiState.update { it.copy(foldFeature = snapshot) }
    }

    fun updateHingeSensor(available: Boolean, angle: Float? = null) {
        _uiState.update {
            it.copy(
                hingeSensorAvailable = available,
                hingeAngle = angle ?: if (available) it.hingeAngle else null,
            )
        }
    }

    fun recordAppForegroundCheckpoint() {
        saveCurrentCheckpoint(PostureCheckpointSource.APP_FOREGROUND)
    }

    fun recordAppBackgroundCheckpoint() {
        saveCurrentCheckpoint(PostureCheckpointSource.APP_BACKGROUND)
    }

    fun refreshFromCurrentState() {
        saveCurrentCheckpoint(PostureCheckpointSource.MANUAL_REFRESH)
        refresh()
    }

    fun setPeriod(period: AnalysisPeriod) {
        if (period == AnalysisPeriod.CUSTOM || period !in _uiState.value.availablePeriods) return
        _uiState.update { it.copy(selectedPeriod = period) }
        selectedPeriod.value = period
    }

    fun setCustomPeriod(startMillis: Long, endMillis: Long) {
        val state = _uiState.value
        val recordStart = state.recordRangeStartMillis ?: return
        val recordEnd = state.recordRangeEndMillis ?: return
        val range = CustomAnalysisRange(startMillis = startMillis, endMillis = endMillis)
        if (!isValidCustomAnalysisRange(range, recordStart, recordEnd, ZoneId.systemDefault())) {
            return
        }
        _uiState.update {
            it.copy(
                selectedPeriod = AnalysisPeriod.CUSTOM,
                customRangeStartMillis = range.startMillis,
                customRangeEndMillis = range.endMillis,
            )
        }
        customRange.value = range
        selectedPeriod.value = AnalysisPeriod.CUSTOM
    }

    fun saveCurrentAsCover() {
        val configuration = _uiState.value.currentConfiguration ?: return
        calibrationStore.saveCover(configuration)
        saveCurrentCheckpoint(PostureCheckpointSource.CALIBRATION_COVER)
        reloadCalibrationAndRefresh()
    }

    fun saveCurrentAsInner() {
        val configuration = _uiState.value.currentConfiguration ?: return
        calibrationStore.saveInner(configuration)
        saveCurrentCheckpoint(PostureCheckpointSource.CALIBRATION_INNER)
        reloadCalibrationAndRefresh()
    }

    fun clearCalibration() {
        calibrationStore.clear()
        reloadCalibrationAndRefresh()
    }

    fun refresh() {
        if (!_uiState.value.hasUsageAccess) return
        if (refreshJob?.isActive == true) return
        refreshJob = viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            when (val result = syncRepository.sync()) {
                is UsageSyncResult.Success -> {
                    _uiState.update { it.copy(isLoading = false) }
                }

                is UsageSyncResult.Skipped -> {
                    val message = when (result.reason) {
                        UsageReadUnavailableReason.PERMISSION_DENIED -> null
                        UsageReadUnavailableReason.USER_LOCKED ->
                            getApplication<Application>().getString(
                                R.string.sync_after_first_unlock,
                            )
                        UsageReadUnavailableReason.SYSTEM_UNAVAILABLE ->
                            getApplication<Application>().getString(
                                R.string.usage_events_temporarily_unavailable,
                            )
                    }
                    _uiState.update {
                        it.copy(
                            hasUsageAccess = syncRepository.hasUsageAccess(),
                            isLoading = false,
                            errorMessage = message,
                        )
                    }
                }

                is UsageSyncResult.Failed -> {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = getApplication<Application>().getString(
                                R.string.sync_failed,
                                result.error.javaClass.simpleName,
                            ),
                        )
                    }
                }
            }
            analysisRevision.value += 1L
        }
    }

    fun diagnosticReport(): String {
        val state = _uiState.value
        val analysis = state.analysis
        val resources = getApplication<Application>().resources
        val formatter = DateTimeFormatter.ofPattern(
            resources.getString(R.string.report_date_time_pattern),
            resources.primaryLocale,
        )
            .withZone(ZoneId.systemDefault())
        fun formatInstant(timestampMillis: Long): String =
            formatter.format(Instant.ofEpochMilli(timestampMillis))
        return buildString {
            fun appendField(labelRes: Int, value: String) {
                appendLine(
                    resources.getString(
                        R.string.report_field,
                        resources.getString(labelRes),
                        value,
                    ),
                )
            }

            appendLine(resources.getString(R.string.report_title))
            appendField(R.string.label_created, formatter.format(Instant.now()))
            appendField(
                R.string.label_usage_access,
                resources.getString(
                    if (state.hasUsageAccess) R.string.status_allowed else R.string.status_not_allowed,
                ),
            )
            appendField(
                R.string.label_current_posture,
                resources.getString(state.currentPosture.labelRes),
            )
            appendField(
                R.string.label_current_configuration,
                state.currentConfiguration?.toDisplayText(resources)
                    ?: resources.getString(R.string.posture_unknown),
            )
            appendField(
                R.string.label_cover_calibration,
                state.calibration.cover?.toDisplayText(resources)
                    ?: resources.getString(R.string.status_not_registered),
            )
            appendField(
                R.string.label_inner_calibration,
                state.calibration.inner?.toDisplayText(resources)
                    ?: resources.getString(R.string.status_not_registered),
            )
            appendField(
                R.string.label_data_source,
                resources.getString(R.string.data_source_on_device_database),
            )
            val selectedPeriodText = if (
                state.selectedPeriod == AnalysisPeriod.CUSTOM && state.periodSummary != null
            ) {
                val summary = state.periodSummary
                resources.getString(
                    R.string.custom_period_with_range,
                    resources.getString(state.selectedPeriod.labelRes),
                    formatInstant(summary.rangeStartMillis),
                    formatInstant(summary.rangeEndMillis),
                )
            } else {
                resources.getString(state.selectedPeriod.labelRes)
            }
            appendField(R.string.label_screen_period, selectedPeriodText)
            appendField(
                R.string.label_last_sync,
                state.lastSuccessfulSyncMillis?.let(::formatInstant)
                    ?: resources.getString(R.string.status_not_synced),
            )
            if (state.lastSyncQueryBeginMillis != null && state.lastSuccessfulSyncMillis != null) {
                appendField(
                    R.string.label_recent_sync_range,
                    resources.getString(
                        R.string.date_range,
                        formatInstant(state.lastSyncQueryBeginMillis),
                        formatInstant(state.lastSuccessfulSyncMillis),
                    ),
                )
                appendField(
                    R.string.label_recent_sync_saved,
                    resources.getString(
                        R.string.value_item_count,
                        state.lastSyncInsertedEventCount,
                    ),
                )
            }
            appendField(R.string.label_folding_feature, state.foldFeature.toString())
            appendField(
                R.string.label_hinge_angle,
                state.hingeAngle?.let { resources.getString(R.string.value_degrees, it) }
                    ?: resources.getString(R.string.status_not_acquired),
            )
            appendLine()
            if (analysis == null) {
                appendLine(resources.getString(R.string.report_no_analysis))
            } else {
                appendField(
                    R.string.label_diagnostic_period,
                    resources.getString(
                        R.string.duration_hours_only,
                        state.selectedPeriod.diagnosticHours,
                    ),
                )
                appendField(
                    R.string.label_analysis_range,
                    resources.getString(
                        R.string.date_range,
                        formatInstant(analysis.rangeStartMillis),
                        formatInstant(analysis.rangeEndMillis),
                    ),
                )
                appendField(
                    R.string.posture_cover,
                    analysis.coverMillis.toDurationText(resources),
                )
                appendField(
                    R.string.posture_inner,
                    analysis.innerMillis.toDurationText(resources),
                )
                appendField(
                    R.string.label_classified_time,
                    analysis.classifiedPostureMillis.toDurationText(resources),
                )
                appendField(
                    R.string.label_data_coverage,
                    resources.getString(
                        R.string.value_percent_1,
                        analysis.dataCoverageRatio * 100,
                    ),
                )
                appendField(
                    R.string.label_excluded_time,
                    analysis.excludedPostureMillis.toDurationText(resources),
                )
                UnknownPostureReason.entries.forEach { reason ->
                    val duration = analysis.excludedPostureMillisByReason[reason] ?: 0L
                    if (duration > 0L) {
                        appendLine(
                            resources.getString(
                                R.string.report_bullet_field,
                                resources.getString(reason.labelRes),
                                duration.toDurationText(resources),
                            ),
                        )
                    }
                }
                appendField(
                    R.string.label_opened,
                    resources.getString(R.string.value_open_count, analysis.openedCount),
                )
                appendField(
                    R.string.label_closed,
                    resources.getString(R.string.value_open_count, analysis.closedCount),
                )
                if (analysis.evidenceGapCount > 0) {
                    appendField(
                        R.string.label_evidence_gaps,
                        resources.getString(R.string.value_item_count, analysis.evidenceGapCount),
                    )
                }
                appendField(
                    R.string.label_event_count,
                    resources.getString(R.string.value_item_count, analysis.eventCount),
                )
                appendLine()
                appendLine(resources.getString(R.string.report_apps_heading))
                analysis.apps.take(MAX_REPORT_APPS).forEach { app ->
                    appendLine(
                        "${app.label} (${app.packageName}): " +
                            "${app.coverMillis.toDurationText(resources)} / " +
                            "${app.innerMillis.toDurationText(resources)} / " +
                            app.excludedMillis.toDurationText(resources),
                    )
                }
                if (analysis.apps.size > MAX_REPORT_APPS) {
                    appendLine(
                        resources.getQuantityString(
                            R.plurals.report_apps_omitted,
                            analysis.apps.size - MAX_REPORT_APPS,
                            analysis.apps.size - MAX_REPORT_APPS,
                        ),
                    )
                }
                appendLine()
                val inRangeEvents = analysis.postureEvents.count { !it.isBeforeRange }
                val seedEvents = analysis.postureEvents.size - inRangeEvents
                appendLine(
                    resources.getString(
                        R.string.report_posture_log_heading,
                        inRangeEvents,
                        seedEvents,
                        MAX_REPORT_POSTURE_EVENTS,
                    ),
                )
                analysis.postureEvents.take(MAX_REPORT_POSTURE_EVENTS).forEach { event ->
                    val rangeLabel = resources.getString(
                        if (event.isBeforeRange) R.string.status_range_seed else R.string.status_range_in,
                    )
                    val sourceLabel = event.checkpointSource?.let {
                        resources.getString(
                            R.string.event_source_with_checkpoint,
                            resources.getString(event.source.labelRes),
                            resources.getString(it.labelRes),
                        )
                    } ?: resources.getString(event.source.labelRes)
                    val reasonLabel = event.unknownReason
                        ?.let {
                            resources.getString(
                                R.string.report_reason_suffix,
                                resources.getString(it.labelRes),
                            )
                        }
                        .orEmpty()
                    val rawTypeLabel = event.rawEventType
                        ?.let { resources.getString(R.string.report_raw_type_suffix, it) }
                        .orEmpty()
                    appendLine(
                        resources.getString(
                            R.string.report_event_line,
                            formatInstant(event.timestampMillis),
                            rangeLabel,
                            sourceLabel,
                            resources.getString(event.posture.labelRes),
                            reasonLabel,
                            rawTypeLabel,
                        ),
                    )
                    appendLine(
                        resources.getString(
                            R.string.report_configuration_line,
                            event.configuration?.toDisplayText(resources)
                                ?: resources.getString(R.string.status_none),
                        ),
                    )
                    if (event.coverDistance != null || event.innerDistance != null) {
                        appendLine(
                            resources.getString(
                                R.string.report_distance_line,
                                event.coverDistance?.toString() ?: "—",
                                event.innerDistance?.toString() ?: "—",
                            ),
                        )
                    }
                }
            }
            state.longTermInsights?.let { insights ->
                appendLine()
                appendLine(
                    resources.getString(
                        R.string.report_usage_trends_heading,
                        resources.getString(state.selectedPeriod.labelRes),
                    ),
                )
                appendField(
                    R.string.posture_cover,
                    insights.coverMillis.toDurationText(resources),
                )
                appendField(
                    R.string.posture_inner,
                    insights.innerMillis.toDurationText(resources),
                )
                appendField(
                    R.string.label_inner_ratio,
                    resources.getString(R.string.value_percent_1, insights.innerRatio * 100),
                )
                appendField(
                    R.string.label_opened,
                    resources.getString(R.string.value_open_count, insights.openedCount),
                )
                appendField(
                    R.string.label_closed,
                    resources.getString(R.string.value_open_count, insights.closedCount),
                )
                appendLine(
                    resources.getString(
                        R.string.report_observed_days,
                        insights.observedDayCount,
                        insights.calendarDayCount,
                        insights.innerUsedDayCount,
                    ),
                )
                insights.thirtyDayInnerRatioDelta?.let { delta ->
                    appendField(
                        R.string.label_inner_ratio_change,
                        resources.getString(R.string.value_points, delta * 100),
                    )
                }
            }
            state.collectionHealth?.let { health ->
                appendLine()
                appendLine(resources.getString(R.string.report_collection_heading))
                appendField(
                    R.string.label_recorded_sync_attempts,
                    resources.getString(R.string.value_item_count, health.recordedAttemptCount),
                )
                appendField(
                    R.string.label_unsuccessful_sync_attempts,
                    resources.getString(R.string.value_item_count, health.unsuccessfulAttemptCount),
                )
                appendField(
                    R.string.label_collection_interruptions,
                    resources.getString(
                        R.string.value_item_count,
                        health.collectionInterruptionCount,
                    ),
                )
                health.longestSuccessfulSyncGapMillis?.let { gap ->
                    appendField(R.string.label_longest_sync_gap, gap.toDurationText(resources))
                }
            }
        }
    }

    fun writeLongTermCsv(output: Appendable) {
        LongTermCsvWriter.write(latestDailySummaries, output)
    }

    private fun saveCurrentCheckpoint(source: PostureCheckpointSource) {
        val configuration = _uiState.value.currentConfiguration ?: return
        if (!configuration.isUsable()) return
        val checkpoint = PostureCheckpoint(
            timestampMillis = System.currentTimeMillis(),
            configuration = configuration,
            source = source,
        )
        viewModelScope.launch {
            try {
                checkpointRepository.save(checkpoint)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                _uiState.update {
                    it.copy(
                        errorMessage = getApplication<Application>().getString(
                            R.string.checkpoint_save_failed,
                            error.javaClass.simpleName,
                        ),
                    )
                }
            }
        }
    }

    private fun reloadCalibrationAndRefresh() {
        val calibration = calibrationStore.load()
        activeCalibration.value = calibration
        _uiState.update { it.copy(calibration = calibration) }
        recalculateCurrentPosture()
        refresh()
    }

    private fun observeStoredAnalysis() {
        viewModelScope.launch {
            val requests = combine(
                selectedPeriod,
                customRange,
                activeCalibration,
                syncRepository.observeSyncState(),
                checkpointRepository.observeRevision(),
            ) { period, customRange, calibration, syncState, checkpointRevision ->
                StoredAnalysisRequest(
                    period = period,
                    customRange = customRange,
                    calibration = calibration,
                    syncState = syncState,
                    checkpointRevision = checkpointRevision,
                )
            }
            combine(requests, analysisRevision) { request, _ -> request }
                .collectLatest { request ->
                    _uiState.update {
                        it.copy(
                            isAnalysisLoading = true,
                            lastSuccessfulSyncMillis = request.syncState?.lastSuccessfulAtMillis,
                            lastSyncQueryBeginMillis = request.syncState?.lastQueryBeginMillis,
                            lastSyncInsertedEventCount =
                                request.syncState?.lastInsertedEventCount ?: 0,
                        )
                    }

                    try {
                        val snapshot = withContext(Dispatchers.IO) {
                            val syncState = request.syncState
                                ?: return@withContext StoredAnalysisSnapshot(
                                    selectedPeriod = request.period.takeIf {
                                        it in DEFAULT_AVAILABLE_PERIODS
                                    } ?: AnalysisPeriod.HOURS_24,
                                    availablePeriods = DEFAULT_AVAILABLE_PERIODS,
                                    recordRangeStartMillis = null,
                                    recordRangeEndMillis = null,
                                    customRange = null,
                                    analysis = null,
                                    periodSummary = null,
                                    innerSessionSummary = null,
                                    longTermInsights = null,
                                    collectionHealth = null,
                                    dailySummaries = emptyList(),
                                )
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
                            val deviceStateCheckpoints =
                                syncRepository.loadDeviceStateCheckpointsForAnalysis(
                                    window.seedStartMillis,
                                    window.rangeEndMillis,
                                )
                            val zoneId = ZoneId.systemDefault()
                            val allSyncAttempts = syncRepository.loadSyncAttempts(
                                beginMillis = 0L,
                                endMillis = syncState.lastSuccessfulEndMillis + 1L,
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
                                collectionGapStarts = collectionGaps.map(
                                    CollectionGap::startMillis,
                                ),
                                deviceStateCheckpoints = deviceStateCheckpoints,
                            )
                            val longTermInsights = if (effectivePeriod == AnalysisPeriod.CUSTOM) {
                                val range = requireNotNull(validCustomRange)
                                longTermAnalyzer.analyzeRange(
                                    summaries = dailySummaries,
                                    rangeStartMillis = range.startMillis,
                                    rangeEndMillis = minOf(
                                        range.endMillis,
                                        syncState.lastSuccessfulEndMillis,
                                    ),
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
                                val reader = foldlyticsApplication.usageEventReader
                                val apps = dailySummaryRepository.loadAggregatedAppUsage(
                                    beginMillis = longTermInsights.rangeStartMillis,
                                    endMillis = longTermInsights.rangeEndMillis,
                                ).map { stored ->
                                    AppUsage(
                                        packageName = stored.packageName,
                                        label = reader.packageLabel(stored.packageName),
                                        coverMillis = stored.coverMillis,
                                        innerMillis = stored.innerMillis,
                                        excludedMillis = stored.excludedMillis,
                                        isLauncherApp = reader.isLauncherApp(stored.packageName),
                                    )
                                }.sortedWith(
                                    compareByDescending<AppUsage> { it.classifiedMillis }
                                        .thenByDescending { it.observedMillis },
                                )
                                longTermInsights.toPeriodSummary(effectivePeriod, apps)
                            }
                            val selectedRangeStart =
                                longTermInsights?.rangeStartMillis ?: window.rangeStartMillis
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
                            val periodSyncAttempts = allSyncAttempts.filter {
                                it.attemptedAtMillis in selectedRangeStart..selectedRangeEnd
                            }
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
                                collectionHealth = longTermAnalyzer.collectionHealth(
                                    periodSyncAttempts,
                                ),
                                dailySummaries = dailySummaries,
                            )
                        }
                        latestDailySummaries = snapshot.dailySummaries
                        _uiState.update {
                            it.copy(
                                selectedPeriod = snapshot.selectedPeriod,
                                availablePeriods = snapshot.availablePeriods,
                                recordRangeStartMillis = snapshot.recordRangeStartMillis,
                                recordRangeEndMillis = snapshot.recordRangeEndMillis,
                                customRangeStartMillis = snapshot.customRange?.startMillis,
                                customRangeEndMillis = snapshot.customRange?.endMillis,
                                analysis = snapshot.analysis,
                                periodSummary = snapshot.periodSummary,
                                innerSessionSummary = snapshot.innerSessionSummary,
                                longTermInsights = snapshot.longTermInsights,
                                collectionHealth = snapshot.collectionHealth,
                                isAnalysisLoading = false,
                            )
                        }
                        if (selectedPeriod.value != snapshot.selectedPeriod) {
                            selectedPeriod.value = snapshot.selectedPeriod
                        }
                        if (customRange.value != snapshot.customRange) {
                            customRange.value = snapshot.customRange
                        }
                    } catch (error: CancellationException) {
                        throw error
                    } catch (error: Exception) {
                        _uiState.update {
                            it.copy(
                                isAnalysisLoading = false,
                                errorMessage = getApplication<Application>().getString(
                                    R.string.stored_analysis_failed,
                                    error.javaClass.simpleName,
                                ),
                            )
                        }
                    }
                }
        }
    }

    private fun recalculateCurrentPosture() {
        _uiState.update {
            it.copy(currentPosture = it.calibration.classify(it.currentConfiguration))
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

    companion object {
        private const val MAX_REPORT_POSTURE_EVENTS = 50
        private const val MAX_REPORT_APPS = 100
    }

    private data class StoredAnalysisRequest(
        val period: AnalysisPeriod,
        val customRange: CustomAnalysisRange?,
        val calibration: Calibration,
        val syncState: UsageSyncState?,
        val checkpointRevision: Long,
    )

    private data class StoredAnalysisSnapshot(
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
        val dailySummaries: List<DailyPostureSummary>,
    )
}
