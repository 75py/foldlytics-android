package com.nagopy.android.foldlytics

import com.nagopy.android.foldlytics.data.DiagnosticHistoryRequest
import com.nagopy.android.foldlytics.model.Calibration
import com.nagopy.android.foldlytics.model.DisplayPosture
import com.nagopy.android.foldlytics.model.PostureEvent
import com.nagopy.android.foldlytics.model.PostureEventSource
import java.io.IOException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Sharing and showing diagnostics must use one analysis with its own complete history. */
class DiagnosticHistoryControllerTest {
    private val requestA = request(rangeEnd = 1_000L)
    private val requestB = request(rangeEnd = 2_000L)

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

    private fun controller(
        parent: CoroutineScope,
        load: suspend (DiagnosticHistoryRequest) -> List<PostureEvent>,
    ): DiagnosticHistoryController {
        // A failed load must not cancel the test, as in viewModelScope.
        val scope = CoroutineScope(parent.coroutineContext + SupervisorJob())
        return DiagnosticHistoryController(scope = scope, load = load, onStateChanged = {})
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
