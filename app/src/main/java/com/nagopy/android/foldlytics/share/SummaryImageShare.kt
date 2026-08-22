package com.nagopy.android.foldlytics.share

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import androidx.core.content.FileProvider
import com.nagopy.android.foldlytics.R
import com.nagopy.android.foldlytics.ui.SUMMARY_SHARE_IMAGE_HEIGHT
import com.nagopy.android.foldlytics.ui.SUMMARY_SHARE_IMAGE_WIDTH
import java.io.File
import java.io.FileOutputStream

internal const val SUMMARY_SHARE_MIME_TYPE = "image/png"
internal const val SUMMARY_SHARE_FILE_PREFIX = "foldlytics-summary-"
internal const val SUMMARY_SHARE_FILE_SUFFIX = ".png"
internal const val SUMMARY_SHARE_DIRECTORY = "share"
internal const val SUMMARY_SHARE_RETENTION_MILLIS = 24L * 60L * 60L * 1_000L

internal object SummaryImageShare {
    fun writeImage(
        context: Context,
        bitmap: Bitmap,
        nowMillis: Long = System.currentTimeMillis(),
    ): Uri {
        require(bitmap.width == SUMMARY_SHARE_IMAGE_WIDTH) {
            "Unexpected summary image width: ${bitmap.width}"
        }
        require(bitmap.height == SUMMARY_SHARE_IMAGE_HEIGHT) {
            "Unexpected summary image height: ${bitmap.height}"
        }

        val directory = File(context.cacheDir, SUMMARY_SHARE_DIRECTORY)
        check(directory.exists() || directory.mkdirs()) {
            "Could not create summary share directory"
        }
        pruneExpiredImages(directory, nowMillis)

        val output = File.createTempFile(
            "$SUMMARY_SHARE_FILE_PREFIX$nowMillis-",
            SUMMARY_SHARE_FILE_SUFFIX,
            directory,
        )
        val written = runCatching {
            FileOutputStream(output).use { stream ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
            }
        }.getOrElse {
            output.delete()
            throw it
        }
        check(written) {
            output.delete()
            "Could not encode summary share image"
        }

        return FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            output,
        )
    }

    fun createSendIntent(context: Context, imageUri: Uri): Intent =
        Intent(Intent.ACTION_SEND).apply {
            type = SUMMARY_SHARE_MIME_TYPE
            putExtra(Intent.EXTRA_STREAM, imageUri)
            clipData = ClipData.newUri(
                context.contentResolver,
                context.getString(R.string.share_summary_preview_title),
                imageUri,
            )
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

    internal fun pruneExpiredImages(
        directory: File,
        nowMillis: Long,
    ) {
        val oldestAllowedMillis = nowMillis - SUMMARY_SHARE_RETENTION_MILLIS
        directory.listFiles().orEmpty()
            .asSequence()
            .filter { file ->
                file.isFile &&
                    file.name.startsWith(SUMMARY_SHARE_FILE_PREFIX) &&
                    file.name.endsWith(SUMMARY_SHARE_FILE_SUFFIX)
            }
            .filter { it.lastModified() < oldestAllowedMillis }
            .forEach(File::delete)
    }
}
