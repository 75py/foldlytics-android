package com.nagopy.android.foldlytics

import com.nagopy.android.foldlytics.data.DiagnosticHistoryRequest
import com.nagopy.android.foldlytics.data.StaleDiagnosticHistoryException
import com.nagopy.android.foldlytics.model.PostureEvent
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

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
    /**
     * The complete history of the previous comparable analysis, shown marked as refreshing while
     * this one is pending or loading, so a refresh does not blank the list. Never shared.
     */
    val refreshingEvents: List<PostureEvent>? = null,
)

/**
 * Loads the complete posture history of the displayed analysis on demand. Every displayed
 * analysis starts a new generation, even when its inputs are unchanged, because the stored
 * source may have changed. Loads of an older generation are cancelled and their results
 * discarded.
 *
 * Not thread-safe: call it, and update the displayed analysis, from one thread.
 *
 * @param onStale called when the stored events changed after the displayed analysis was read;
 *   the owner must reload and display the analysis again, or report that reloading failed.
 */
internal class DiagnosticHistoryController(
    private val scope: CoroutineScope,
    private val load: suspend (DiagnosticHistoryRequest) -> List<PostureEvent>,
    private val onStateChanged: (DiagnosticHistoryState) -> Unit,
    private val onStale: () -> Unit = {},
    private val staleReloadTimeoutMillis: Long = STALE_RELOAD_TIMEOUT_MILLIS,
) {
    private var request: DiagnosticHistoryRequest? = null
    private var pending: Pair<Long, Deferred<List<PostureEvent>>>? = null
    private val generations = MutableStateFlow(0L)

    var state = DiagnosticHistoryState()
        private set

    /** Call before the update that displays a new analysis; null when it has no history. */
    fun onAnalysisDisplayed(request: DiagnosticHistoryRequest?) {
        val previousRequest = this.request
        val previous = state
        this.request = request
        cancelPending()
        val comparable = request != null &&
            previousRequest != null &&
            request.calibration == previousRequest.calibration &&
            request.rangeEndMillis - request.rangeStartMillis ==
            previousRequest.rangeEndMillis - previousRequest.rangeStartMillis
        publish(
            DiagnosticHistoryState(
                generation = previous.generation + 1L,
                status = if (request == null) {
                    DiagnosticHistoryStatus.NOT_REQUIRED
                } else {
                    DiagnosticHistoryStatus.PENDING
                },
                refreshingEvents = if (comparable) {
                    previous.events ?: previous.refreshingEvents
                } else {
                    null
                },
            ),
        )
    }

    /** Call when reloading the displayed analysis failed, so waiting shares stop waiting. */
    fun onAnalysisReloadFailed() {
        cancelPending()
        publish(
            DiagnosticHistoryState(
                generation = state.generation + 1L,
                status = if (request == null) {
                    DiagnosticHistoryStatus.NOT_REQUIRED
                } else {
                    DiagnosticHistoryStatus.FAILED
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
     * between the check and [block], so it reads one consistent state. A replaced analysis is
     * loaded again, and a stale one waits for its reload. Returns null if loading failed or the
     * analysis kept changing for [maxAttempts] loads. Cancelling the caller cancels the wait but
     * not a load that the screen may still use.
     */
    suspend fun <T> withCompleteHistory(maxAttempts: Int = 3, block: () -> T): T? {
        repeat(maxAttempts) {
            val generation = state.generation
            val load = startLoadIfNeeded() ?: return block()
            val outcome = try {
                load.await()
                LoadOutcome.READY
            } catch (error: CancellationException) {
                // Rethrows when the caller itself was cancelled.
                currentCoroutineContext().ensureActive()
                LoadOutcome.SUPERSEDED
            } catch (_: StaleDiagnosticHistoryException) {
                LoadOutcome.STALE
            } catch (_: Exception) {
                LoadOutcome.FAILED
            }
            if (outcome == LoadOutcome.STALE && state.generation == generation) {
                // The owner reloads the analysis; wait for it to be displayed.
                withTimeoutOrNull(staleReloadTimeoutMillis) {
                    generations.first { it != generation }
                } ?: return null
            }
            if (state.generation != generation) return@repeat
            if (outcome != LoadOutcome.READY || state.status != DiagnosticHistoryStatus.READY) {
                return null
            }
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
                        pending = null
                        publish(
                            state.copy(
                                status = DiagnosticHistoryStatus.READY,
                                events = events,
                                refreshingEvents = null,
                            ),
                        )
                    }
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: StaleDiagnosticHistoryException) {
                if (state.generation == generation) {
                    // Keep showing the refreshing list while the owner reloads the analysis.
                    pending = null
                    publish(state.copy(status = DiagnosticHistoryStatus.LOADING))
                    onStale()
                }
                throw error
            } catch (error: Exception) {
                if (state.generation == generation) {
                    // Drop the failed load so the next request retries.
                    pending = null
                    publish(
                        state.copy(
                            status = DiagnosticHistoryStatus.FAILED,
                            events = null,
                            refreshingEvents = null,
                        ),
                    )
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

    /** Stops the replay of a superseded analysis, which no screen or share can use. */
    private fun cancelPending() {
        pending?.second?.cancel()
        pending = null
    }

    private fun publish(next: DiagnosticHistoryState) {
        state = next
        generations.value = next.generation
        onStateChanged(next)
    }

    private enum class LoadOutcome { READY, SUPERSEDED, STALE, FAILED }

    private companion object {
        const val STALE_RELOAD_TIMEOUT_MILLIS = 30_000L
        val CompletedLoad: Deferred<List<PostureEvent>> = CompletableDeferred(emptyList())
    }
}
