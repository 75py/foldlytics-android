package com.nagopy.android.foldlytics

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.nagopy.android.foldlytics.data.CalibrationStore
import com.nagopy.android.foldlytics.data.CsvExportOutput
import com.nagopy.android.foldlytics.data.DiagnosticArchiveExporter
import com.nagopy.android.foldlytics.data.DiagnosticArchiveOutput
import com.nagopy.android.foldlytics.data.LongTermCsvExporter
import com.nagopy.android.foldlytics.data.StoredAnalysisLoader
import com.nagopy.android.foldlytics.data.StoredAnalysisRequest
import com.nagopy.android.foldlytics.data.StoredAnalysisSnapshot
import com.nagopy.android.foldlytics.data.UsageReadUnavailableReason
import com.nagopy.android.foldlytics.data.UsageSyncResult
import com.nagopy.android.foldlytics.data.UsageSyncState
import com.nagopy.android.foldlytics.data.toDisplayConfiguration
import com.nagopy.android.foldlytics.model.AnalysisPeriod
import com.nagopy.android.foldlytics.model.Calibration
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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext

/**
 * The stored data needed by [MainViewModel]. Keeping this boundary here lets instrumentation
 * tests exercise period changes with controlled analysis loads without opening the app database.
 */
internal interface MainViewModelDataSource {
    fun hasUsageAccess(): Boolean

    suspend fun sync(): UsageSyncResult

    fun observeSyncState(): Flow<UsageSyncState?>

    fun observeCheckpointRevision(): Flow<Long>

    fun observeSyncHistoryRevision(): Flow<Long>

    suspend fun saveCheckpoint(checkpoint: PostureCheckpoint)

    suspend fun load(
        request: StoredAnalysisRequest,
        zoneId: ZoneId,
    ): StoredAnalysisSnapshot

    suspend fun loadSavedDailyHistory(
        calibration: Calibration,
        zoneId: ZoneId,
    ): List<DailyPostureSummary>
}

private class ProductionMainViewModelDataSource(
    application: FoldlyticsApplication,
) : MainViewModelDataSource {
    private val checkpointRepository = application.postureCheckpointRepository
    private val syncRepository = application.usageSyncRepository
    private val storedAnalysisLoader = StoredAnalysisLoader(
        syncRepository = syncRepository,
        checkpointRepository = checkpointRepository,
        dailySummaryRepository = application.dailySummaryRepository,
        packageLabel = application.usageEventReader::packageLabel,
        isLauncherApp = application.usageEventReader::isLauncherApp,
    )

    override fun hasUsageAccess(): Boolean = syncRepository.hasUsageAccess()

    override suspend fun sync(): UsageSyncResult = syncRepository.sync()

    override fun observeSyncState(): Flow<UsageSyncState?> = syncRepository.observeSyncState()

    override fun observeCheckpointRevision(): Flow<Long> = checkpointRepository.observeRevision()

    override fun observeSyncHistoryRevision(): Flow<Long> =
        syncRepository.observeSyncHistoryRevision()

    override suspend fun saveCheckpoint(checkpoint: PostureCheckpoint) {
        checkpointRepository.save(checkpoint)
    }

    override suspend fun load(
        request: StoredAnalysisRequest,
        zoneId: ZoneId,
    ): StoredAnalysisSnapshot = storedAnalysisLoader.load(request, zoneId)

    override suspend fun loadSavedDailyHistory(
        calibration: Calibration,
        zoneId: ZoneId,
    ) = storedAnalysisLoader.loadSavedDailyHistory(calibration, zoneId)
}

class MainViewModel internal constructor(
    application: Application,
    private val dataSource: MainViewModelDataSource,
    private val calibrationStore: CalibrationStore,
) : AndroidViewModel(application) {
    constructor(application: Application) : this(
        application = application,
        dataSource = ProductionMainViewModelDataSource(application as FoldlyticsApplication),
        calibrationStore = CalibrationStore(application),
    )

    private val initialCalibration = calibrationStore.load()
    private val selectedPeriod = MutableStateFlow(AnalysisPeriod.HOURS_24)
    private val customRange = MutableStateFlow<CustomAnalysisRange?>(null)
    private val activeCalibration = MutableStateFlow(initialCalibration)
    private val analysisRevision = MutableStateFlow(0L)
    private val csvExporter = LongTermCsvExporter {
        dataSource.loadSavedDailyHistory(
            calibration = activeCalibration.value,
            zoneId = ZoneId.systemDefault(),
        )
    }
    private var refreshJob: Job? = null
    private val diagnosticExportMutex = Mutex()

    private val _uiState = MutableStateFlow(
        MainUiState(
            currentConfiguration = application.resources.configuration.toDisplayConfiguration(),
            calibration = initialCalibration,
            calibrationValidationFailure = initialCalibration.validationFailure,
        ).withRecalculatedPosture(),
    )
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    init {
        observeStoredAnalysis()
        checkPermissionAndRefresh()
    }

    fun checkPermissionAndRefresh() {
        val hasAccess = dataSource.hasUsageAccess()
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
        _uiState.update { it.withConfiguration(configuration, isInMultiWindowMode) }
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
            when (val result = dataSource.sync()) {
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
                            hasUsageAccess = dataSource.hasUsageAccess(),
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

    /**
     * Writes every saved daily summary to [output]. The history is loaded first, so an export
     * started right after the process was recreated exports the saved history instead of an empty
     * file, and a failed or cancelled load leaves the destination untouched.
     */
    suspend fun exportLongTermCsv(output: CsvExportOutput) {
        withContext(Dispatchers.IO) {
            csvExporter.export(output)
        }
    }

    suspend fun exportDiagnosticArchive(output: DiagnosticArchiveOutput) {
        check(BuildConfig.ENABLE_DIAGNOSTIC_EXPORT) { "Diagnostic export is disabled" }
        check(diagnosticExportMutex.tryLock()) { "Diagnostic export is already running" }
        try {
            val snapshot = _uiState.value
            _uiState.update { it.copy(isExportingDiagnostic = true) }
            val application = getApplication<FoldlyticsApplication>()
            withContext(Dispatchers.IO) {
                DiagnosticArchiveExporter(application, application.database).export(
                    calibration = snapshot.calibration,
                    diagnosticReport = DiagnosticReportFormatter.format(snapshot, application.resources),
                    output = output,
                )
            }
        } finally {
            _uiState.update { it.copy(isExportingDiagnostic = false) }
            diagnosticExportMutex.unlock()
        }
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
                dataSource.saveCheckpoint(checkpoint)
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
        _uiState.update { it.withCalibration(calibration) }
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
                dataSource.observeSyncState(),
                dataSource.observeCheckpointRevision(),
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
                dataSource.observeSyncHistoryRevision(),
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
                            dataSource.load(request, ZoneId.systemDefault())
                        }
                        _uiState.update {
                            it.copy(
                                selectedPeriod = snapshot.selectedPeriod,
                                analyzedPeriod = snapshot.selectedPeriod,
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
}
