package com.nagopy.android.foldlytics.insight

import androidx.annotation.Keep
import java.io.File
import java.util.concurrent.Executors
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine

/** One offline generation at a time; cancellation also interrupts CPU decoding. */
@Keep
class LlamaInsightEngine {
    suspend fun generate(
        modelFile: File,
        systemPrompt: String,
        userPrompt: String,
        maxOutputTokens: Int = 256,
    ): String {
        require(modelFile.isFile) { "The local language model is missing." }
        require(maxOutputTokens in 1..512) { "Output token limit must be between 1 and 512." }
        require(systemPrompt.length + userPrompt.length <= 16_384) { "The prompt is too long." }
        return suspendCancellableCoroutine { continuation ->
            val requestLock = Any()
            var request = 0L
            var cancelled = false
            continuation.invokeOnCancellation {
                synchronized(requestLock) {
                    cancelled = true
                    if (request != 0L) nativeCancel(request)
                }
            }
            executor.execute {
                try {
                    ensureLibraryLoaded()
                    val currentRequest = synchronized(requestLock) {
                        if (cancelled) return@execute
                        nativeCreateRequest().also { request = it }
                    }
                    val output = nativeGenerate(
                        currentRequest,
                        modelFile.absolutePath.toByteArray(Charsets.UTF_8),
                        systemPrompt.toByteArray(Charsets.UTF_8),
                        userPrompt.toByteArray(Charsets.UTF_8),
                        maxOutputTokens,
                    ).toString(Charsets.UTF_8).trim()
                    if (continuation.isActive) continuation.resume(output)
                } catch (failure: Throwable) {
                    if (continuation.isActive) continuation.resumeWithException(failure)
                } finally {
                    // Cancellation cannot access a handle after its native storage is freed.
                    synchronized(requestLock) {
                        if (request != 0L) {
                            nativeDestroyRequest(request)
                            request = 0L
                        }
                    }
                }
            }
        }
    }

    private external fun nativeCreateRequest(): Long
    private external fun nativeCancel(request: Long)
    private external fun nativeDestroyRequest(request: Long)
    private external fun nativeGenerate(
        request: Long,
        modelPath: ByteArray,
        systemPrompt: ByteArray,
        userPrompt: ByteArray,
        maxOutputTokens: Int,
    ): ByteArray

    companion object {
        private val executor = Executors.newSingleThreadExecutor { task ->
            Thread(task, "foldlytics-inference").apply { isDaemon = true }
        }
        private val libraryLoaded by lazy { System.loadLibrary("foldlytics_llama") }

        private fun ensureLibraryLoaded() {
            libraryLoaded
        }
    }
}
