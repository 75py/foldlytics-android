package com.nagopy.android.foldlytics.ui

import android.content.res.Resources
import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.nagopy.android.foldlytics.R
import com.nagopy.android.foldlytics.model.PeriodUsageSummary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal const val SUMMARY_SHARE_PREVIEW_TAG = "summary_share_preview"
internal const val SUMMARY_SHARE_PREVIEW_IMAGE_TAG = "summary_share_preview_image"
internal const val SUMMARY_SHARE_CANCEL_TAG = "summary_share_cancel"
internal const val SUMMARY_SHARE_CONFIRM_TAG = "summary_share_confirm"
internal const val SUMMARY_SHARE_ERROR_TAG = "summary_share_error"

internal val LocalSummaryShareImageGenerator = staticCompositionLocalOf {
    SummaryShareImageGenerator { resources: Resources, summary: PeriodUsageSummary ->
        withContext(Dispatchers.Default) {
            SummaryShareImageRenderer.renderWithDiagnostics(resources, summary)
        }
    }
}

private sealed interface SummarySharePreviewState {
    data object Generating : SummarySharePreviewState

    data class Ready(val image: SummaryShareRenderResult) : SummarySharePreviewState

    data object Failed : SummarySharePreviewState
}

@Composable
internal fun SummarySharePreviewDialog(
    summary: PeriodUsageSummary,
    canShare: Boolean,
    onDismiss: () -> Unit,
    onShare: suspend (Bitmap) -> Boolean,
) {
    val resources = LocalResources.current
    val generator = LocalSummaryShareImageGenerator.current
    val scope = rememberCoroutineScope()
    var previewState by remember(summary) {
        mutableStateOf<SummarySharePreviewState>(SummarySharePreviewState.Generating)
    }
    var isSharing by remember(summary) { mutableStateOf(false) }
    var shareFailed by remember(summary) { mutableStateOf(false) }

    LaunchedEffect(summary, generator) {
        previewState = runCatching {
            generator.generate(resources, summary).also { image ->
                check(image.bitmap.width == SUMMARY_SHARE_IMAGE_WIDTH)
                check(image.bitmap.height == SUMMARY_SHARE_IMAGE_HEIGHT)
            }
        }.fold(
            onSuccess = SummarySharePreviewState::Ready,
            onFailure = { SummarySharePreviewState.Failed },
        )
    }

    AlertDialog(
        modifier = Modifier.testTag(SUMMARY_SHARE_PREVIEW_TAG),
        onDismissRequest = { if (!isSharing) onDismiss() },
        title = { Text(resources.getString(R.string.share_summary_preview_title)) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                when (val current = previewState) {
                    SummarySharePreviewState.Generating -> {
                        CircularProgressIndicator()
                        Text(resources.getString(R.string.share_summary_generating))
                    }

                    SummarySharePreviewState.Failed -> {
                        ShareErrorText(
                            resources.getString(R.string.share_summary_generation_failed),
                        )
                    }

                    is SummarySharePreviewState.Ready -> {
                        val preview = remember(current.image.bitmap) {
                            current.image.bitmap.asImageBitmap()
                        }
                        val previewDescription = current.image.content.accessibilityDescription
                        Image(
                            bitmap = preview,
                            contentDescription = null,
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(
                                    SUMMARY_SHARE_IMAGE_WIDTH.toFloat() /
                                        SUMMARY_SHARE_IMAGE_HEIGHT.toFloat(),
                                )
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .semantics { contentDescription = previewDescription }
                                .testTag(SUMMARY_SHARE_PREVIEW_IMAGE_TAG),
                            contentScale = ContentScale.Fit,
                        )
                    }
                }
                if (shareFailed) {
                    ShareErrorText(resources.getString(R.string.share_summary_failed))
                }
            }
        },
        confirmButton = {
            val bitmap = (previewState as? SummarySharePreviewState.Ready)?.image?.bitmap
            TextButton(
                modifier = Modifier.testTag(SUMMARY_SHARE_CONFIRM_TAG),
                enabled = bitmap != null && canShare && !isSharing,
                onClick = {
                    if (bitmap == null || isSharing) return@TextButton
                    isSharing = true
                    shareFailed = false
                    scope.launch {
                        val succeeded = runCatching { onShare(bitmap) }.getOrDefault(false)
                        isSharing = false
                        if (succeeded) {
                            onDismiss()
                        } else {
                            shareFailed = true
                        }
                    }
                },
            ) {
                Text(resources.getString(R.string.action_share_summary_image))
            }
        },
        dismissButton = {
            TextButton(
                modifier = Modifier.testTag(SUMMARY_SHARE_CANCEL_TAG),
                enabled = !isSharing,
                onClick = onDismiss,
            ) {
                Text(resources.getString(R.string.action_cancel))
            }
        },
    )
}

@Composable
private fun ShareErrorText(message: String) {
    Text(
        text = message,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .testTag(SUMMARY_SHARE_ERROR_TAG),
        color = MaterialTheme.colorScheme.error,
        style = MaterialTheme.typography.bodyMedium,
    )
}
