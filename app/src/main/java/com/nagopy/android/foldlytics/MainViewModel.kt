package com.nagopy.android.foldlytics

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.nagopy.android.foldlytics.data.CalibrationStore
import com.nagopy.android.foldlytics.data.LongTermCsvWriter
import com.nagopy.android.foldlytics.data.StoredAnalysisLoader
import com.nagopy.android.foldlytics.data.StoredAnalysisRequest
import com.nagopy.android.foldlytics.data.UsageReadUnavailableReason
import com.nagopy.android.foldlytics.data.UsageSyncResult
import com.nagopy.android.foldlytics.data.toDisplayConfiguration
import com.nagopy.android.foldlytics.model.AnalysisPeriod
import com.nagopy.android.foldlytics.model.CalibrationAnchor
import com.nagopy.android.foldlytics.model.CalibrationUpdateResult
import com.nagopy.android.foldlytics.model.CalibrationValidationFailure
import com.nagopy.android.foldlytics.model.CustomAnalysisRange
import com.nagopy.android.foldlytics.model.DailyPostureSummary
import com.nagopy.android.foldlytics.model.DisplayConfiguration
import com.nagopy.android.foldlytics.model.PostureCheckpoint
import com.nagopy.android.foldlytics.model.PostureCheckpointSource
import com.nagopy.android.foldlytics.model.isValidCustomAnalysisRange
import java.time.ZoneId
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

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val foldlyticsApplication = application as FoldlyticsApplication
    private val calibrationStore = CalibrationStore(application)
    private val checkpointRepository = foldlyticsApplication.postureCheckpointRepository
    private val syncRepository = foldlyticsApplication.usageSyncRepository
    private val storedAnalysisLoader = StoredAnalysisLoader(
        syncRepository = syncRepository,
        checkpointRepository = checkpointRepository,
        dailySummaryRepository = foldlyticsApplication.dailySummaryRepository,
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
            calibrationValidationFailure = initialCalibration.validationFailure,
        ),
    )
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    init {
        recalculateCurrentPosture()
        observeStoredAnalysis()
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

    fun updateConfiguration(
        configuration: DisplayConfiguration,
        isInMultiWindowMode: Boolean,
    ) {
        _uiState.update {
            it.copy(
                currentConfiguration = configuration,
                currentConfigurationCanBePostureEvidence =
                    configuration.canBePostureEvidence(isInMultiWindowMode),
                calibrationValidationFailure = it.calibration.validationFailure,
            )
        }
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

    fun recordAppLaunchCheckpoint() {
        saveCurrentCheckpoint(PostureCheckpointSource.APP_LAUNCH)
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
        saveCurrentCalibration(
            anchor = CalibrationAnchor.COVER,
            checkpointSource = PostureCheckpointSource.CALIBRATION_COVER,
        )
    }

    fun saveCurrentAsInner() {
        saveCurrentCalibration(
            anchor = CalibrationAnchor.INNER,
            checkpointSource = PostureCheckpointSource.CALIBRATION_INNER,
        )
    }

    fun clearCalibration() {
        calibrationStore.clear()
        reloadCalibrationAndRefresh()
    }

    fun refresh() {
        if (!_uiState.value.hasUsageAccess) return
        if (refreshJob?.isActive == true) return
        refreshJob = viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isLoading = true,
                    error = it.error?.takeIf { currentError ->
                        currentError.kind == MainUiErrorKind.CHECKPOINT
                    },
                )
            }
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
                    val syncError = message?.let {
                        MainUiError(
                            kind = MainUiErrorKind.SYNC,
                            message = it,
                        )
                    }
                    _uiState.update { state ->
                        state.copy(
                            hasUsageAccess = syncRepository.hasUsageAccess(),
                            isLoading = false,
                            error = syncError ?: state.error,
                        )
                    }
                }

                is UsageSyncResult.Failed -> {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            error = MainUiError(
                                kind = MainUiErrorKind.SYNC,
                                message = getApplication<Application>().getString(
                                    R.string.sync_failed,
                                    result.error.javaClass.simpleName,
                                ),
                            ),
                        )
                    }
                }
            }
            analysisRevision.value += 1L
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    fun diagnosticReport(): String = DiagnosticReportFormatter.format(
        state = _uiState.value,
        resources = getApplication<Application>().resources,
    )

    fun writeLongTermCsv(output: Appendable) {
        LongTermCsvWriter.write(latestDailySummaries, output)
    }

    private fun saveCurrentCheckpoint(source: PostureCheckpointSource) {
        val configuration = _uiState.value.currentConfiguration ?: return
        if (!_uiState.value.currentConfigurationCanBePostureEvidence) return
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
                        error = MainUiError(
                            kind = MainUiErrorKind.CHECKPOINT,
                            message = getApplication<Application>().getString(
                                R.string.checkpoint_save_failed,
                                error.javaClass.simpleName,
                            ),
                        ),
                    )
                }
            }
        }
    }

    private fun reloadCalibrationAndRefresh() {
        val calibration = calibrationStore.load()
        activeCalibration.value = calibration
        _uiState.update {
            it.copy(
                calibration = calibration,
                calibrationValidationFailure = calibration.validationFailure,
            )
        }
        recalculateCurrentPosture()
        refresh()
    }

    private fun saveCurrentCalibration(
        anchor: CalibrationAnchor,
        checkpointSource: PostureCheckpointSource,
    ) {
        val configuration = _uiState.value.currentConfiguration
        if (configuration == null || !_uiState.value.currentConfigurationCanBePostureEvidence) {
            _uiState.update {
                it.copy(
                    calibrationValidationFailure =
                        CalibrationValidationFailure.CONFIGURATION_UNAVAILABLE,
                )
            }
            return
        }

        val result = when (anchor) {
            CalibrationAnchor.COVER -> calibrationStore.saveCover(configuration)
            CalibrationAnchor.INNER -> calibrationStore.saveInner(configuration)
        }
        when (result) {
            is CalibrationUpdateResult.Accepted -> {
                saveCurrentCheckpoint(checkpointSource)
                reloadCalibrationAndRefresh()
            }

            is CalibrationUpdateResult.Rejected -> {
                _uiState.update {
                    it.copy(calibrationValidationFailure = result.reason)
                }
            }
        }
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
            val requestsWithSyncHistory = combine(
                requests,
                syncRepository.observeSyncHistoryRevision(),
            ) { request, _ -> request }
            combine(requestsWithSyncHistory, analysisRevision) { request, _ -> request }
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
                            storedAnalysisLoader.load(request, ZoneId.systemDefault())
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
                                error = it.error?.takeUnless { currentError ->
                                    currentError.kind == MainUiErrorKind.ANALYSIS
                                },
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
                                error = MainUiError(
                                    kind = MainUiErrorKind.ANALYSIS,
                                    message = getApplication<Application>().getString(
                                        R.string.stored_analysis_failed,
                                        error.javaClass.simpleName,
                                    ),
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
}
