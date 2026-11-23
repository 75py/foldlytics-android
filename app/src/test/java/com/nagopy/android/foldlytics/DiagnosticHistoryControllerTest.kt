package com.nagopy.android.foldlytics

import com.nagopy.android.foldlytics.data.DiagnosticHistoryRequest
import com.nagopy.android.foldlytics.data.StaleDiagnosticHistoryException
import com.nagopy.android.foldlytics.model.Calibration
import com.nagopy.android.foldlytics.model.DisplayConfiguration
import com.nagopy.android.foldlytics.model.DisplayPosture
import com.nagopy.android.foldlytics.model.PostureEvent
import com.nagopy.android.foldlytics.model.PostureEventSource
import java.io.IOException
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Sharing and showing diagnostics must use one analysis with its own complete history. */
class DiagnosticHistoryControllerTest {
    private val requestA = request(rangeEnd = 1_000L)
    private val requestB = request(rangeEnd = 2_000L)

    /** [requestA]'s window after a sync moved it, with the same length. */
    private val movedA = requestA.copy(rangeStartMillis = 1_000L, rangeEndMillis = 2_000L)

    @Test
    fun failedHistoryReturnsNoReportAndTheNextRequestRetries() = runBlocking {
        val loads = mutableListOf<DiagnosticHistoryRequest>()
        var fail = true
        val controller = controller(this) { request ->
            loads += request
            if (fail) throw IOException("disk")
            events("complete")
        }
        controller.onAnalysisDisplayed(requestA)

        assertNull(controller.withCompleteHistory { "report" })
        assertEquals(DiagnosticHistoryStatus.FAILED, controller.state.status)
        assertNull(controller.state.events)

        fail = false
        controller.request()
        yield()
        assertEquals(DiagnosticHistoryStatus.READY, controller.state.status)
        assertEquals("report", controller.withCompleteHistory { "report" })
        assertEquals(events("complete"), controller.state.events)
        assertEquals(listOf(requestA, requestA), loads)
    }

    @Test
    fun analysisReplacedWhileSharingFormatsOnlyTheNewAnalysisWithItsHistory() = runBlocking {
        val gates = mapOf(
            requestA to CompletableDeferred<Unit>(),
            requestB to CompletableDeferred<Unit>(),
        )
        val controller = controller(this) { request ->
            gates.getValue(request).await()
            events(if (request == requestA) "old" else "new")
        }
        var displayed = "A"
        controller.onAnalysisDisplayed(requestA)

        val share = async(start = CoroutineStart.UNDISPATCHED) {
            controller.withCompleteHistory { displayed to controller.state.events }
        }
        // A newer analysis is displayed while the old history is still loading.
        controller.onAnalysisDisplayed(requestB)
        displayed = "B"
        gates.getValue(requestA).complete(Unit)
        yield()
        // The old history is never published for the new analysis.
        assertNull(controller.state.events)
        gates.getValue(requestB).complete(Unit)

        assertEquals("B" to events("new"), share.await())
    }

    @Test
    fun analysisThatKeepsChangingWhileSharingReturnsNoReport() = runBlocking {
        lateinit var controller: DiagnosticHistoryController
        var generation = 0L
        controller = controller(this) { request ->
            // Every load is overtaken by a newer displayed analysis.
            controller.onAnalysisDisplayed(request(rangeEnd = 3_000L + ++generation))
            events("stale")
        }
        controller.onAnalysisDisplayed(requestA)

        assertNull(controller.withCompleteHistory(maxAttempts = 3) { "report" })
        assertEquals(3L, generation)
    }

    @Test
    fun reloadedAnalysisWithUnchangedInputsDropsTheOlderHistory() = runBlocking {
        var source = "before sync"
        val controller = controller(this) { events(source) }
        controller.onAnalysisDisplayed(requestA)
        assertEquals("before", controller.withCompleteHistory { "before" })
        assertEquals(events("before sync"), controller.state.events)

        // The stored rows changed, and the reload produced an equal request.
        source = "after sync"
        controller.onAnalysisDisplayed(requestA)
        assertEquals(DiagnosticHistoryStatus.PENDING, controller.state.status)
        assertNull(controller.state.events)

        assertEquals("after", controller.withCompleteHistory { "after" })
        assertEquals(events("after sync"), controller.state.events)
    }

    @Test
    fun failureOfAReplacedLoadDoesNotFailTheNewAnalysis() = runBlocking {
        val oldGate = CompletableDeferred<Unit>()
        val controller = controller(this) { request ->
            if (request == requestA) {
                oldGate.await()
                throw IOException("old")
            }
            events("new")
        }
        controller.onAnalysisDisplayed(requestA)
        controller.request()
        controller.onAnalysisDisplayed(requestB)
        oldGate.complete(Unit)
        yield()

        assertEquals(DiagnosticHistoryStatus.PENDING, controller.state.status)
        assertEquals("ok", controller.withCompleteHistory { "ok" })
        assertEquals(events("new"), controller.state.events)
    }

    @Test
    fun stateWithoutHistoryFormatsImmediately() = runBlocking {
        val controller = controller(this) { error("must not load") }
        controller.onAnalysisDisplayed(null)

        assertEquals(DiagnosticHistoryStatus.NOT_REQUIRED, controller.state.status)
        assertEquals("report", controller.withCompleteHistory { "report" })
    }

    @Test
    fun staleHistoryWaitsForTheReloadedAnalysisAndSharesOnlyIt() = runBlocking {
        var reloads = 0
        lateinit var controller: DiagnosticHistoryController
        controller = controller(
            this,
            onStale = { reloads += 1 },
        ) { request ->
            if (request == requestA) throw StaleDiagnosticHistoryException()
            events("reloaded")
        }
        var displayed = "A"
        controller.onAnalysisDisplayed(requestA)

        val share = async(start = CoroutineStart.UNDISPATCHED) {
            controller.withCompleteHistory { displayed to controller.state.events }
        }
        yield()
        assertEquals(1, reloads)
        // The owner reloads the analysis and displays it.
        controller.onAnalysisDisplayed(requestB)
        displayed = "B"

        assertEquals("B" to events("reloaded"), share.await())
    }

    @Test
    fun staleHistoryWhoseReloadFailsSharesNothing() = runBlocking {
        lateinit var controller: DiagnosticHistoryController
        controller = controller(this, onStale = { controller.onAnalysisReloadFailed() }) {
            throw StaleDiagnosticHistoryException()
        }
        controller.onAnalysisDisplayed(requestA)

        assertNull(controller.withCompleteHistory { "report" })
        assertEquals(DiagnosticHistoryStatus.FAILED, controller.state.status)
    }

    @Test
    fun replacedAnalysisCancelsItsRunningLoad() = runBlocking {
        val oldLoadCancelled = CompletableDeferred<Unit>()
        val controller = controller(this) { request ->
            if (request == requestA) {
                try {
                    awaitCancellation()
                } finally {
                    oldLoadCancelled.complete(Unit)
                }
            }
            events("new")
        }
        controller.onAnalysisDisplayed(requestA)
        controller.request()
        yield()

        controller.onAnalysisDisplayed(requestB)

        withTimeout(1_000L) { oldLoadCancelled.await() }
        assertEquals("ok", controller.withCompleteHistory { "ok" })
    }

    @Test
    fun cancelledShareRethrowsAndLeavesTheLoadForTheScreen() = runBlocking {
        val gate = CompletableDeferred<Unit>()
        val controller = controller(this) {
            gate.await()
            events("complete")
        }
        controller.onAnalysisDisplayed(requestA)
        val share = async(start = CoroutineStart.UNDISPATCHED) {
            controller.withCompleteHistory { "report" }
        }

        share.cancel()
        val error = runCatching { share.await() }.exceptionOrNull()
        assertTrue("$error", error is CancellationException)
        gate.complete(Unit)
        yield()
        yield()
        assertEquals(DiagnosticHistoryStatus.READY, controller.state.status)
        assertEquals(events("complete"), controller.state.events)
    }

    @Test
    fun refreshKeepsThePreviousComparableHistoryUntilTheNewOneIsReady() = runBlocking {
        val gate = CompletableDeferred<Unit>()
        val controller = controller(this) { request ->
            if (request == movedA) gate.await()
            events(if (request == requestA) "first" else "second")
        }
        controller.onAnalysisDisplayed(requestA)
        controller.withCompleteHistory { Unit }

        // The next sync moves the same-length window.
        controller.onAnalysisDisplayed(movedA)
        assertEquals(DiagnosticHistoryStatus.PENDING, controller.state.status)
        assertNull(controller.state.events)
        assertEquals(events("first"), controller.state.refreshingEvents)
        controller.request()
        assertEquals(DiagnosticHistoryStatus.LOADING, controller.state.status)
        assertEquals(events("first"), controller.state.refreshingEvents)

        gate.complete(Unit)
        // Sharing never uses the refreshing list: it waits for the new history.
        assertEquals(events("second"), controller.withCompleteHistory { controller.state.events })
        assertNull(controller.state.refreshingEvents)
    }

    @Test
    fun refreshDropsThePreviousHistoryForAnotherWindowCalibrationOrFailure() = runBlocking {
        var fail = false
        val controller = controller(this) {
            if (fail) throw IOException("disk")
            events("first")
        }
        suspend fun displayLoaded() {
            controller.onAnalysisDisplayed(requestA)
            controller.withCompleteHistory { Unit }
        }

        displayLoaded()
        // A different period's window is not comparable.
        controller.onAnalysisDisplayed(requestB)
        assertNull(controller.state.refreshingEvents)

        displayLoaded()
        controller.onAnalysisDisplayed(
            movedA.copy(calibration = Calibration(cover = DisplayConfiguration(1, 1, 1, 1, 1))),
        )
        assertNull(controller.state.refreshingEvents)

        displayLoaded()
        controller.onAnalysisDisplayed(null)
        assertNull(controller.state.refreshingEvents)

        displayLoaded()
        controller.onAnalysisDisplayed(movedA)
        assertEquals(events("first"), controller.state.refreshingEvents)
        fail = true
        assertNull(controller.withCompleteHistory { "report" })
        assertNull(controller.state.refreshingEvents)
    }

    private fun controller(
        parent: CoroutineScope,
        onStale: () -> Unit = {},
        load: suspend (DiagnosticHistoryRequest) -> List<PostureEvent>,
    ): DiagnosticHistoryController {
        // A failed load must not cancel the test, as in viewModelScope.
        val scope = CoroutineScope(parent.coroutineContext + SupervisorJob())
        return DiagnosticHistoryController(
            scope = scope,
            load = load,
            onStateChanged = {},
            onStale = onStale,
        )
    }

    private fun request(rangeEnd: Long) = DiagnosticHistoryRequest(
        calibration = Calibration(),
        rangeStartMillis = 0L,
        rangeEndMillis = rangeEnd,
        checkpoints = emptyList(),
        collectionGapStarts = emptyList(),
    )

    private fun events(label: String) = listOf(
        PostureEvent(
            timestampMillis = label.length.toLong(),
            source = PostureEventSource.CONFIGURATION_CHANGE,
            posture = DisplayPosture.UNKNOWN,
            configuration = null,
            isBeforeRange = true,
        ),
        PostureEvent(
            timestampMillis = label.hashCode().toLong(),
            source = PostureEventSource.CONFIGURATION_CHANGE,
            posture = DisplayPosture.UNKNOWN,
            configuration = null,
        ),
    )
}
