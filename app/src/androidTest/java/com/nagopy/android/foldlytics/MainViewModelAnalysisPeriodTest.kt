package com.nagopy.android.foldlytics

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.nagopy.android.foldlytics.data.CalibrationPersistence
import com.nagopy.android.foldlytics.data.CalibrationStore
import com.nagopy.android.foldlytics.data.StoredAnalysisRequest
import com.nagopy.android.foldlytics.data.StoredAnalysisSnapshot
import com.nagopy.android.foldlytics.data.UsageSyncResult
import com.nagopy.android.foldlytics.data.UsageSyncState
import com.nagopy.android.foldlytics.model.AnalysisPeriod
import com.nagopy.android.foldlytics.model.Calibration
import com.nagopy.android.foldlytics.model.DailyPostureSummary
import com.nagopy.android.foldlytics.model.PeriodUsageSummary
import com.nagopy.android.foldlytics.model.PostureCheckpoint
import com.nagopy.android.foldlytics.model.UsageAnalysis
import java.time.ZoneId
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MainViewModelAnalysisPeriodTest {
    @Test
    fun periodChangeRetainsSnapshotUntilSuccessAndAfterFailure() = runBlocking {
        val source = ControlledDataSource()
        val store = ViewModelStore()
        try {
            val viewModel = withContext(Dispatchers.Main) { createViewModel(store, source) }
            val initial = source.awaitLoad()
            val firstSnapshot = snapshot(AnalysisPeriod.HOURS_24, marker = 24L)
            assertEquals(AnalysisPeriod.HOURS_24, initial.request.period)
            initial.succeed(firstSnapshot)
            assertSnapshot(viewModel.awaitState { !it.isAnalysisLoading }, firstSnapshot)

            withContext(Dispatchers.Main) { viewModel.setPeriod(AnalysisPeriod.HOURS_1) }
            val pendingSuccess = source.awaitLoad()
            assertEquals(AnalysisPeriod.HOURS_1, pendingSuccess.request.period)
            val loading = viewModel.awaitState { it.isAnalysisLoading }
            assertEquals(AnalysisPeriod.HOURS_1, loading.selectedPeriod)
            assertSnapshot(loading, firstSnapshot)

            val secondSnapshot = snapshot(AnalysisPeriod.HOURS_1, marker = 1L)
            pendingSuccess.succeed(secondSnapshot)
            val succeeded = viewModel.awaitState { !it.isAnalysisLoading }
            assertEquals(AnalysisPeriod.HOURS_1, succeeded.selectedPeriod)
            assertSnapshot(succeeded, secondSnapshot)

            withContext(Dispatchers.Main) { viewModel.setPeriod(AnalysisPeriod.HOURS_24) }
            val pendingFailure = source.awaitLoad()
            assertEquals(AnalysisPeriod.HOURS_24, pendingFailure.request.period)
            val loadingFailure = viewModel.awaitState { it.isAnalysisLoading }
            assertEquals(AnalysisPeriod.HOURS_24, loadingFailure.selectedPeriod)
            assertSnapshot(loadingFailure, secondSnapshot)

            pendingFailure.fail(IllegalStateException("forced analysis failure"))
            val failed = viewModel.awaitState { !it.isAnalysisLoading }
            assertEquals(MainUiErrorKind.ANALYSIS, failed.error?.kind)
            assertEquals(AnalysisPeriod.HOURS_24, failed.selectedPeriod)
            assertSnapshot(failed, secondSnapshot)
        } finally {
            withContext(NonCancellable + Dispatchers.Main) { store.clear() }
        }
    }

    @Test
    fun newerPeriodCancelsTheSupersededAnalysisLoad() = runBlocking {
        val source = ControlledDataSource()
        val store = ViewModelStore()
        try {
            val viewModel = withContext(Dispatchers.Main) { createViewModel(store, source) }
            source.awaitLoad().succeed(snapshot(AnalysisPeriod.HOURS_24, marker = 24L))
            viewModel.awaitState { !it.isAnalysisLoading }

            withContext(Dispatchers.Main) { viewModel.setPeriod(AnalysisPeriod.HOURS_1) }
            val superseded = source.awaitLoad()
            withContext(Dispatchers.Main) { viewModel.setPeriod(AnalysisPeriod.HOURS_6) }
            withTimeout(TIMEOUT_MILLIS) { superseded.cancelled.await() }
            val replacement = source.awaitLoad()
            assertEquals(AnalysisPeriod.HOURS_6, replacement.request.period)
            replacement.succeed(snapshot(AnalysisPeriod.HOURS_6, marker = 6L))

            val completed = viewModel.awaitState { !it.isAnalysisLoading }
            assertEquals(AnalysisPeriod.HOURS_6, completed.selectedPeriod)
            assertEquals(AnalysisPeriod.HOURS_6, completed.analyzedPeriod)
        } finally {
            withContext(NonCancellable + Dispatchers.Main) { store.clear() }
        }
    }

    private fun createViewModel(
        store: ViewModelStore,
        source: MainViewModelDataSource,
    ): MainViewModel {
        val application = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext
            as Application
        val owner = object : ViewModelStoreOwner {
            override val viewModelStore: ViewModelStore = store
        }
        val factory = object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                @Suppress("UNCHECKED_CAST")
                return MainViewModel(
                    application = application,
                    dataSource = source,
                    calibrationStore = CalibrationStore(InMemoryCalibrationPersistence()),
                ) as T
            }
        }
        return ViewModelProvider(owner, factory)[MainViewModel::class.java]
    }

    private suspend fun MainViewModel.awaitState(
        predicate: (MainUiState) -> Boolean,
    ): MainUiState = withTimeout(TIMEOUT_MILLIS) {
        uiState.first(predicate)
    }

    private fun assertSnapshot(state: MainUiState, snapshot: StoredAnalysisSnapshot) {
        assertEquals(snapshot.selectedPeriod, state.analyzedPeriod)
        assertEquals(snapshot.analysis, state.analysis)
        assertEquals(snapshot.periodSummary, state.periodSummary)
    }

    private fun snapshot(
        period: AnalysisPeriod,
        marker: Long,
    ): StoredAnalysisSnapshot {
        val analysis = UsageAnalysis(
            rangeStartMillis = marker,
            rangeEndMillis = marker + 1L,
            coverMillis = marker,
            innerMillis = marker + 2L,
            excludedPostureMillis = marker + 3L,
            excludedPostureMillisByReason = emptyMap(),
            openedCount = marker.toInt(),
            closedCount = marker.toInt() + 1,
            evidenceGapCount = 0,
            foldTransitions = emptyList(),
            dailySummaries = emptyList(),
            apps = emptyList(),
            postureEvents = emptyList(),
            eventCount = marker.toInt(),
            multiResumeMillis = 0L,
        )
        return StoredAnalysisSnapshot(
            selectedPeriod = period,
            availablePeriods = setOf(
                AnalysisPeriod.HOURS_1,
                AnalysisPeriod.HOURS_6,
                AnalysisPeriod.HOURS_24,
            ),
            recordRangeStartMillis = marker,
            recordRangeEndMillis = marker + 1L,
            customRange = null,
            analysis = analysis,
            periodSummary = PeriodUsageSummary(
                period = period,
                rangeStartMillis = marker,
                rangeEndMillis = marker + 1L,
                coverMillis = marker,
                innerMillis = marker + 2L,
                excludedMillis = marker + 3L,
                openedCount = marker.toInt(),
                closedCount = marker.toInt() + 1,
                apps = emptyList(),
            ),
            innerSessionSummary = null,
            longTermInsights = null,
            collectionHealth = null,
        )
    }

    private class ControlledDataSource : MainViewModelDataSource {
        private val pendingLoads = Channel<PendingLoad>(Channel.UNLIMITED)

        override fun hasUsageAccess(): Boolean = false

        override suspend fun sync(): UsageSyncResult = error("sync should not run")

        override fun observeSyncState(): Flow<UsageSyncState?> = MutableStateFlow(null)

        override fun observeCheckpointRevision(): Flow<Long> = MutableStateFlow(0L)

        override fun observeSyncHistoryRevision(): Flow<Long> = MutableStateFlow(0L)

        override suspend fun saveCheckpoint(checkpoint: PostureCheckpoint) =
            error("checkpoint saving should not run")

        override suspend fun load(
            request: StoredAnalysisRequest,
            zoneId: ZoneId,
        ): StoredAnalysisSnapshot {
            val pending = PendingLoad(request)
            pendingLoads.send(pending)
            try {
                return pending.result.await()
            } finally {
                if (!pending.result.isCompleted) {
                    pending.cancelled.complete(Unit)
                }
            }
        }

        override suspend fun loadSavedDailyHistory(
            calibration: Calibration,
            zoneId: ZoneId,
        ): List<DailyPostureSummary> = emptyList()

        suspend fun awaitLoad(): PendingLoad = withTimeout(TIMEOUT_MILLIS) {
            pendingLoads.receive()
        }
    }

    private class PendingLoad(
        val request: StoredAnalysisRequest,
    ) {
        val result = CompletableDeferred<StoredAnalysisSnapshot>()
        val cancelled = CompletableDeferred<Unit>()

        fun succeed(snapshot: StoredAnalysisSnapshot) {
            assertTrue(result.complete(snapshot))
        }

        fun fail(error: Exception) {
            assertTrue(result.completeExceptionally(error))
        }
    }

    private class InMemoryCalibrationPersistence : CalibrationPersistence {
        override fun load(): Calibration = Calibration()

        override fun save(calibration: Calibration) = Unit

        override fun clear() = Unit
    }

    private companion object {
        const val TIMEOUT_MILLIS = 5_000L
    }
}
