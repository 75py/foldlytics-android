package com.nagopy.android.foldlytics.insight

import android.content.Context
import android.util.AtomicFile
import java.io.File
import java.security.MessageDigest
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Reads the install-time asset locally. No SDK, downloader, or remote service is involved. */
internal class InsightModelStore(private val context: Context) {
    suspend fun prepare(): File = mutex.withLock {
        val directory = File(context.noBackupFilesDir, "insight").apply { mkdirs() }
        val file = File(directory, "usage-insight.gguf")
        val marker = File(directory, "model.sha256")
        if (file.length() == MODEL_BYTES && marker.readTextOrNull() == MODEL_SHA256) return@withLock file
        val atomic = AtomicFile(file)
        val output = atomic.startWrite()
        try {
            val digest = MessageDigest.getInstance("SHA-256")
            var total = 0L
            context.assets.open("usage-insight.gguf").use { input ->
                val buffer = ByteArray(1024 * 1024)
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val count = input.read(buffer)
                    if (count < 0) break
                    total += count
                    check(total <= MODEL_BYTES) { "Unexpected model size" }
                    digest.update(buffer, 0, count)
                    output.write(buffer, 0, count)
                }
            }
            check(total == MODEL_BYTES && digest.digest().joinToString("") { "%02x".format(it) } == MODEL_SHA256) {
                "Unexpected model checksum"
            }
            atomic.finishWrite(output)
            marker.writeText(MODEL_SHA256)
            file
        } catch (error: Throwable) {
            atomic.failWrite(output)
            throw error
        }
    }

    companion object {
        const val MODEL_SHA256 = "9465e63a22add5354d9bb4b99e90117043c7124007664907259bd16d043bb031"
        const val MODEL_BYTES = 639_446_688L
        private val mutex = Mutex()
    }
}

private fun File.readTextOrNull(): String? = if (isFile) readText() else null
