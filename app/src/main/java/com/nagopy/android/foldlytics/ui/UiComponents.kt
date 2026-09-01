package com.nagopy.android.foldlytics.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import com.nagopy.android.foldlytics.R
import com.nagopy.android.foldlytics.labelRes
import com.nagopy.android.foldlytics.model.AnalysisPeriod
import com.nagopy.android.foldlytics.toShortDateText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal const val SUMMARY_SHARE_BUTTON_TAG = "summary_share_button"
internal const val SUMMARY_CARD_TAG = "summary_card"
internal const val HOME_APP_USAGE_LINK_TAG = "home_app_usage_link"
internal const val HOME_INNER_SESSIONS_LINK_TAG = "home_inner_sessions_link"
internal const val APP_USAGE_SCREEN_TAG = "app_usage_screen"
internal const val APP_USAGE_CARD_TAG_PREFIX = "app_usage_card_"
internal const val INNER_DISPLAY_SESSION_SCREEN_TAG = "inner_display_session_screen"
internal const val DETAIL_BACK_BUTTON_TAG = "detail_back_button"
internal const val ANALYSIS_PERIOD_SELECTOR_TAG = "analysis_period_selector"
internal const val USAGE_TREND_CARD_TAG = "usage_trend_card"
internal const val USAGE_TREND_INNER_RATIO_TAG = "usage_trend_inner_ratio"
internal const val USAGE_TREND_OPEN_COUNT_TAG = "usage_trend_open_count"
internal const val INNER_SESSION_CARD_TAG = "inner_session_card"
internal const val INNER_SESSION_METRICS_TAG = "inner_session_metrics"
internal const val INNER_SESSION_EMPTY_TAG = "inner_session_empty"
internal const val INNER_SESSION_COUNT_TAG = "inner_session_count"
internal const val INNER_SESSION_OTHER_DESCRIPTION_TAG = "inner_session_other_description"
internal const val INNER_SESSION_DETAIL_TAG_PREFIX = "inner_session_detail_"
internal const val INNER_SESSION_OTHER_TAG_PREFIX = "inner_session_other_"
internal const val INNER_SESSION_APP_TAG_PREFIX = "inner_session_app_"
internal const val INNER_SESSION_LONG_SESSIONS_CARD_TAG = "inner_session_long_sessions_card"
internal const val INNER_SESSION_METHOD_TAG = "inner_session_method"

private val MaxContentWidth = 720.dp

@Composable
internal fun FoldlyticsLazyColumn(
    modifier: Modifier = Modifier,
    scaffoldPadding: PaddingValues,
    listState: LazyListState = rememberLazyListState(),
    content: LazyListScope.() -> Unit,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(scaffoldPadding),
        contentAlignment = Alignment.TopCenter,
    ) {
        LazyColumn(
            modifier = Modifier
                .widthIn(max = MaxContentWidth)
                .fillMaxSize(),
            state = listState,
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            content = content,
        )
    }
}

@Composable
internal fun MenuButton(onClick: () -> Unit) {
    val lineColor = MaterialTheme.colorScheme.onSurface
    val menuDescription = stringResource(R.string.content_desc_open_menu)
    IconButton(
        onClick = onClick,
        modifier = Modifier.semantics { contentDescription = menuDescription },
    ) {
        Canvas(Modifier.size(24.dp)) {
            listOf(6.dp.toPx(), 12.dp.toPx(), 18.dp.toPx()).forEach { y ->
                drawLine(
                    color = lineColor,
                    start = Offset(3.dp.toPx(), y),
                    end = Offset(21.dp.toPx(), y),
                    strokeWidth = 2.dp.toPx(),
                    cap = StrokeCap.Round,
                )
            }
        }
    }
}

@Composable
internal fun DetailBackButton(onClick: () -> Unit) {
    val lineColor = MaterialTheme.colorScheme.onSurface
    val description = stringResource(R.string.content_desc_go_back)
    IconButton(
        onClick = onClick,
        modifier = Modifier
            .testTag(DETAIL_BACK_BUTTON_TAG)
            .semantics { contentDescription = description },
    ) {
        Canvas(Modifier.size(24.dp)) {
            drawLine(
                color = lineColor,
                start = Offset(5.dp.toPx(), 12.dp.toPx()),
                end = Offset(19.dp.toPx(), 12.dp.toPx()),
                strokeWidth = 2.dp.toPx(),
                cap = StrokeCap.Round,
            )
            drawLine(
                color = lineColor,
                start = Offset(5.dp.toPx(), 12.dp.toPx()),
                end = Offset(12.dp.toPx(), 5.dp.toPx()),
                strokeWidth = 2.dp.toPx(),
                cap = StrokeCap.Round,
            )
            drawLine(
                color = lineColor,
                start = Offset(5.dp.toPx(), 12.dp.toPx()),
                end = Offset(12.dp.toPx(), 19.dp.toPx()),
                strokeWidth = 2.dp.toPx(),
                cap = StrokeCap.Round,
            )
        }
    }
}

@Composable
internal fun SummaryShareButton(
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val iconColor = if (enabled) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
    }
    val description = stringResource(R.string.content_desc_share_summary)
    IconButton(
        enabled = enabled,
        onClick = onClick,
        modifier = Modifier
            .testTag(SUMMARY_SHARE_BUTTON_TAG)
            .semantics { contentDescription = description },
    ) {
        Canvas(Modifier.size(24.dp)) {
            val left = Offset(6.dp.toPx(), 12.dp.toPx())
            val upperRight = Offset(17.dp.toPx(), 6.dp.toPx())
            val lowerRight = Offset(17.dp.toPx(), 18.dp.toPx())
            drawLine(
                color = iconColor,
                start = left,
                end = upperRight,
                strokeWidth = 2.dp.toPx(),
                cap = StrokeCap.Round,
            )
            drawLine(
                color = iconColor,
                start = left,
                end = lowerRight,
                strokeWidth = 2.dp.toPx(),
                cap = StrokeCap.Round,
            )
            listOf(left, upperRight, lowerRight).forEach { center ->
                drawCircle(color = iconColor, radius = 3.dp.toPx(), center = center)
            }
        }
    }
}

@Composable
internal fun AnalysisPeriodContext(
    period: AnalysisPeriod,
    rangeStartMillis: Long?,
    rangeEndMillis: Long?,
) {
    val resources = LocalResources.current
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            stringResource(R.string.selected_period, stringResource(period.labelRes)),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
        )
        Text(
            if (rangeStartMillis != null && rangeEndMillis != null) {
                stringResource(
                    R.string.date_range,
                    rangeStartMillis.toShortDateText(resources),
                    (rangeEndMillis - 1L).coerceAtLeast(0L).toShortDateText(resources),
                )
            } else {
                stringResource(R.string.record_range_empty)
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
internal fun ApplicationIcon(packageName: String, label: String) {
    val context = LocalContext.current
    val icon by produceState<ImageBitmap?>(initialValue = null, key1 = packageName) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                context.packageManager
                    .getApplicationIcon(packageName)
                    .toBitmap(width = 96, height = 96)
                    .asImageBitmap()
            }.getOrNull()
        }
    }
    if (icon != null) {
        Image(
            bitmap = requireNotNull(icon),
            contentDescription = null,
            modifier = Modifier.size(44.dp),
            contentScale = ContentScale.Fit,
        )
    } else {
        Box(
            modifier = Modifier
                .size(44.dp)
                .background(MaterialTheme.colorScheme.secondaryContainer, RoundedCornerShape(11.dp))
                .clearAndSetSemantics {},
            contentAlignment = Alignment.Center,
        ) {
            Text(
                label.firstOrNull()?.uppercase() ?: "?",
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
        }
    }
}

@Composable
internal fun Metric(
    label: String,
    value: String,
    color: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier,
) {
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = color)
        Text(value, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
internal fun InfoLine(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top,
    ) {
        Text(label, style = MaterialTheme.typography.bodySmall)
        Text(
            value,
            modifier = Modifier
                .padding(start = 12.dp)
                .weight(1f, fill = false),
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )
    }
    Spacer(Modifier.height(4.dp))
}

@Composable
internal fun LabCard(
    title: String,
    modifier: Modifier = Modifier,
    titleAction: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(modifier = modifier) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    title,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                titleAction?.invoke()
            }
            Spacer(Modifier.height(5.dp))
            content()
        }
    }
}

@Composable
internal fun HintCard(text: String, isError: Boolean = false) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (isError) {
                MaterialTheme.colorScheme.errorContainer
            } else {
                MaterialTheme.colorScheme.surfaceContainer
            },
        ),
    ) {
        Text(text, Modifier.padding(16.dp))
    }
}

@Composable
internal fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
}
