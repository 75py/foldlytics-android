package com.nagopy.android.foldlytics.share

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.core.content.IntentCompat
import androidx.test.platform.app.InstrumentationRegistry
import com.nagopy.android.foldlytics.ui.SUMMARY_SHARE_IMAGE_HEIGHT
import com.nagopy.android.foldlytics.ui.SUMMARY_SHARE_IMAGE_WIDTH
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SummaryImageShareTest {
    private val context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun writesAnExactPngAndBuildsAnImageOnlyShareIntent() {
        val bitmap = Bitmap.createBitmap(
            SUMMARY_SHARE_IMAGE_WIDTH,
            SUMMARY_SHARE_IMAGE_HEIGHT,
            Bitmap.Config.ARGB_8888,
        ).apply {
            eraseColor(0xFFF4F7FB.toInt())
        }
        val nowMillis = System.currentTimeMillis()
        val uri = SummaryImageShare.writeImage(context, bitmap, nowMillis)

        assertEquals("content", uri.scheme)
        assertEquals("${context.packageName}.fileprovider", uri.authority)
        assertEquals(SUMMARY_SHARE_MIME_TYPE, context.contentResolver.getType(uri))
        context.contentResolver.openInputStream(uri).use { input ->
            val decoded = BitmapFactory.decodeStream(input)
            assertNotNull(decoded)
            assertEquals(SUMMARY_SHARE_IMAGE_WIDTH, decoded.width)
            assertEquals(SUMMARY_SHARE_IMAGE_HEIGHT, decoded.height)
        }

        val intent = SummaryImageShare.createSendIntent(context, uri)
        val stream = IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)
        assertEquals(Intent.ACTION_SEND, intent.action)
        assertEquals(SUMMARY_SHARE_MIME_TYPE, intent.type)
        assertEquals(uri, stream)
        assertEquals(uri, intent.clipData?.getItemAt(0)?.uri)
        assertTrue(intent.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
        assertFalse(intent.hasExtra(Intent.EXTRA_TEXT))
    }

    @Test
    fun providerIsPrivateAndExposesOnlyTheShareCacheDirectory() {
        val authority = "${context.packageName}.fileprovider"
        val provider = requireNotNull(
            context.packageManager.resolveContentProvider(authority, 0),
        )

        assertEquals(SummaryImageFileProvider::class.java.name, provider.name)
        assertFalse(provider.exported)
        assertTrue(provider.grantUriPermissions)

        val outsideShareDirectory = File(context.cacheDir, "not-shared-by-foldlytics.txt")
        outsideShareDirectory.writeText("not shareable")
        val result = runCatching {
            FileProvider.getUriForFile(context, authority, outsideShareDirectory)
        }
        outsideShareDirectory.delete()

        assertTrue(result.isFailure)
    }

    @Test
    fun nextGenerationDeletesOnlyExpiredSummaryImages() {
        val directory = File(context.cacheDir, SUMMARY_SHARE_DIRECTORY)
        assertTrue(directory.exists() || directory.mkdirs())
        val nowMillis = System.currentTimeMillis()
        val expired = File(
            directory,
            "$SUMMARY_SHARE_FILE_PREFIX-expired$SUMMARY_SHARE_FILE_SUFFIX",
        ).apply {
            writeText("expired")
            setLastModified(nowMillis - SUMMARY_SHARE_RETENTION_MILLIS - 1L)
        }
        val recent = File(
            directory,
            "$SUMMARY_SHARE_FILE_PREFIX-recent$SUMMARY_SHARE_FILE_SUFFIX",
        ).apply {
            writeText("recent")
            setLastModified(nowMillis - SUMMARY_SHARE_RETENTION_MILLIS + 1L)
        }
        val unrelated = File(directory, "unrelated.tmp").apply {
            writeText("unrelated")
            setLastModified(nowMillis - SUMMARY_SHARE_RETENTION_MILLIS - 1L)
        }

        SummaryImageShare.pruneExpiredImages(directory, nowMillis)

        assertFalse(expired.exists())
        assertTrue(recent.exists())
        assertTrue(unrelated.exists())
        recent.delete()
        unrelated.delete()
    }
}
