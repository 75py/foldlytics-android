package com.nagopy.android.foldlytics

import com.nagopy.android.foldlytics.data.DiagnosticHistoryRequest
import com.nagopy.android.foldlytics.model.PostureEvent
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async

/** Whether diagnostics can list the complete posture history of the displayed analysis. */
enum class DiagnosticHistoryStatus {
    /** The displayed state has no compacted history to complete, so its own events are complete. */
    NOT_REQUIRED,

    /** The displayed analysis changed and its complete history has not been requested yet. */
    PENDING,
    LOADING,
    READY,

    /** Loading failed; requesting it again retries. */
    FAILED,
}

/** The complete posture history for one displayed analysis, identified by [generation]. */
data class DiagnosticHistoryState(
    val generation: Long = 0L,
    val status: DiagnosticHistoryStatus = DiagnosticHistoryStatus.NOT_REQUIRED,
    val events: List<PostureEvent>? = null,
)

/**
 * Loads the complete posture history of the displayed analysis on demand. Every displayed
 * analysis starts a new generation, even when its inputs are unchanged, because the stored
 * source may have changed. Results of an older generation are discarded.
 *
 * Not thread-safe: call it, and update the displayed analysis, from one thread.
 */
internal class DiagnosticHistoryController(
    private val scope: CoroutineScope,
    private val load: suspend (DiagnosticHistoryRequest) -> List<PostureEvent>,
    private val onStateChanged: (DiagnosticHistoryState) -> Unit,
) {
    private var request: DiagnosticHistoryRequest? = null
    private var pending: Pair<Long, Deferred<List<PostureEvent>>>? = null

    var state = DiagnosticHistoryState()
        private set

    /** Call in the same update that displays a new analysis; null when it has no history. */
    fun onAnalysisDisplayed(request: DiagnosticHistoryRequest?) {
        this.request = request
        pending = null
        publish(
            DiagnosticHistoryState(
                generation = state.generation + 1L,
                status = if (request == null) {
                    DiagnosticHistoryStatus.NOT_REQUIRED
                } else {
                    DiagnosticHistoryStatus.PENDING
                },
            ),
        )
    }

    /** Starts loading the current history unless it is loaded or loading. */
    fun request() {
        startLoadIfNeeded()
    }

    /**
     * Runs [block] once the displayed analysis has its complete history, without suspending
     * between the check and [block], so it reads one consistent state. Returns null if loading
     * failed, or if the analysis kept changing for [maxAttempts] loads.
     */
    suspend fun <T> withCompleteHistory(maxAttempts: Int = 3, block: () -> T): T? {
        repeat(maxAttempts) {
            val generation = state.generation
            val load = startLoadIfNeeded() ?: return block()
            val ready = try {
                load.await()
                true
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                false
            }
            // A newer analysis replaced the one this load was for: load that one instead.
            if (state.generation != generation) return@repeat
            if (!ready || state.status != DiagnosticHistoryStatus.READY) return null
            return block()
        }
        return null
    }

    /** Returns the load to await, or null when the current state needs none. */
    private fun startLoadIfNeeded(): Deferred<List<PostureEvent>>? {
        val request = request ?: return null
        if (state.status == DiagnosticHistoryStatus.READY) return CompletedLoad
        val generation = state.generation
        pending?.takeIf { it.first == generation }?.let { return it.second }
        val load = scope.async {
            try {
                load(request).also { events ->
                    if (state.generation == generation) {
                        publish(state.copy(status = DiagnosticHistoryStatus.READY, events = events))
                    }
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                if (state.generation == generation) {
                    // Drop the failed load so the next request retries.
                    pending = null
                    publish(state.copy(status = DiagnosticHistoryStatus.FAILED, events = null))
                }
                throw error
            }
        }
        // A load that already finished has published its result; keep only a running one.
        if (!load.isCompleted) {
            pending = generation to load
            publish(state.copy(status = DiagnosticHistoryStatus.LOADING))
        }
        return load
    }

    private fun publish(next: DiagnosticHistoryState) {
        state = next
        onStateChanged(next)
    }

    private companion object {
        val CompletedLoad: Deferred<List<PostureEvent>> = CompletableDeferred(emptyList())
    }
}
