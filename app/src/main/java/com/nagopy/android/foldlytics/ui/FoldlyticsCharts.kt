package com.nagopy.android.foldlytics.ui

import android.content.res.Resources
import android.icu.text.ListFormatter
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.nagopy.android.foldlytics.R
import com.nagopy.android.foldlytics.model.LongTermBucket
import com.nagopy.android.foldlytics.toShortDateText
import kotlin.math.roundToInt

internal data class PostureColors(
    val cover: Color,
    val inner: Color,
    val unknown: Color,
)

internal data class DonutSegment(
    val label: String,
    val value: Long,
    val color: Color,
)

@Composable
internal fun postureColors(): PostureColors = if (isSystemInDarkTheme()) {
    PostureColors(
        cover = Color(0xFFFFB077),
        inner = Color(0xFF73C7FF),
        unknown = Color(0xFF9CA3AF),
    )
} else {
    PostureColors(
        cover = Color(0xFFC44E00),
        inner = Color(0xFF0067A5),
        unknown = Color(0xFF6B7280),
    )
}

@Composable
internal fun DonutChart(
    segments: List<DonutSegment>,
    centerLabel: String,
    centerValue: String,
    description: String,
    modifier: Modifier = Modifier,
    size: Dp = 164.dp,
    centerValueTextStyle: TextStyle? = null,
) {
    val visibleSegments = segments.filter { it.value > 0L }
    val total = visibleSegments.sumOf(DonutSegment::value).coerceAtLeast(1L).toFloat()
    val emptyColor = MaterialTheme.colorScheme.surfaceVariant
    Box(
        modifier = modifier
            .size(size)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val strokeWidth = this.size.minDimension * 0.14f
            val inset = strokeWidth / 2f
            val arcTopLeft = Offset(inset, inset)
            val arcSize = Size(
                width = this.size.width - strokeWidth,
                height = this.size.height - strokeWidth,
            )
            drawArc(
                color = emptyColor,
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = arcTopLeft,
                size = arcSize,
                style = Stroke(width = strokeWidth),
            )
            var startAngle = -90f
            visibleSegments.forEach { segment ->
                val sweepAngle = segment.value.toFloat() / total * 360f
                drawArc(
                    color = segment.color,
                    startAngle = startAngle,
                    sweepAngle = sweepAngle,
                    useCenter = false,
                    topLeft = arcTopLeft,
                    size = arcSize,
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Butt),
                )
                startAngle += sweepAngle
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                centerLabel,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                centerValue,
                modifier = Modifier.width(size * 0.64f),
                style = centerValueTextStyle ?: MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                maxLines = 2,
            )
        }
    }
}

@Composable
internal fun PostureLegend(colors: PostureColors) {
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        LegendDot(colors.cover, stringResource(R.string.posture_cover))
        LegendDot(colors.inner, stringResource(R.string.posture_inner))
        LegendDot(colors.unknown, stringResource(R.string.label_no_data))
    }
}

@Composable
internal fun InnerRatioTrendChart(
    buckets: List<LongTermBucket>,
    colors: PostureColors,
    modifier: Modifier = Modifier,
) {
    val resources = LocalResources.current
    val description = resources.formatChartAccessibilityList(buckets.map { bucket ->
        if (bucket.classifiedMillis == 0L) {
            resources.getString(
                R.string.content_desc_chart_no_data,
                bucket.startMillis.toShortDateText(resources),
            )
        } else {
            resources.getString(
                R.string.content_desc_chart_inner_ratio,
                bucket.startMillis.toShortDateText(resources),
                resources.getString(R.string.value_percent_0, bucket.innerRatio * 100),
            )
        }
    })
    ChartWithYAxis(
        labels = listOf("100%", "50%", "0%"),
        buckets = buckets,
        description = description,
        modifier = modifier,
    ) {
        val gridColor = colors.unknown.copy(alpha = 0.28f)
        listOf(0f, size.height / 2f, size.height).forEach { y ->
            drawLine(gridColor, Offset(0f, y), Offset(size.width, y), strokeWidth = 1.dp.toPx())
        }
        val positions = buckets.mapIndexed { index, bucket ->
            val x = chartX(index, buckets.size, size.width)
            val y = size.height * (1f - bucket.innerRatio.coerceIn(0f, 1f))
            Offset(x, y)
        }
        buckets.zipWithNext().forEachIndexed { index, (first, second) ->
            if (first.classifiedMillis > 0L && second.classifiedMillis > 0L) {
                drawLine(
                    color = colors.inner,
                    start = positions[index],
                    end = positions[index + 1],
                    strokeWidth = 3.dp.toPx(),
                    cap = StrokeCap.Round,
                )
            }
        }
        buckets.forEachIndexed { index, bucket ->
            if (bucket.classifiedMillis > 0L) {
                drawCircle(colors.inner, radius = 3.5.dp.toPx(), center = positions[index])
            } else {
                drawCircle(
                    colors.unknown.copy(alpha = 0.7f),
                    radius = 2.5.dp.toPx(),
                    center = Offset(positions[index].x, size.height),
                )
            }
        }
    }
}

@Composable
internal fun OpenCountTrendChart(
    buckets: List<LongTermBucket>,
    colors: PostureColors,
    modifier: Modifier = Modifier,
) {
    val resources = LocalResources.current
    val maximum = buckets
        .filter { it.observedDayCount > 0 }
        .maxOfOrNull(LongTermBucket::openedCount)
        ?.coerceAtLeast(1)
        ?: 1
    val middle = maximum / 2f
    val description = resources.formatChartAccessibilityList(buckets.map { bucket ->
        if (bucket.observedDayCount == 0) {
            resources.getString(
                R.string.content_desc_chart_no_data,
                bucket.startMillis.toShortDateText(resources),
            )
        } else {
            resources.getQuantityString(
                R.plurals.content_desc_chart_opened,
                bucket.openedCount,
                bucket.startMillis.toShortDateText(resources),
                bucket.openedCount,
            )
        }
    })
    ChartWithYAxis(
        labels = listOf(
            resources.getString(R.string.value_open_count, maximum),
            resources.getString(R.string.value_open_count, middle.roundToInt()),
            resources.getString(R.string.value_open_count, 0),
        ),
        buckets = buckets,
        description = description,
        modifier = modifier,
    ) {
        val gridColor = colors.unknown.copy(alpha = 0.28f)
        listOf(0f, size.height / 2f, size.height).forEach { y ->
            drawLine(gridColor, Offset(0f, y), Offset(size.width, y), strokeWidth = 1.dp.toPx())
        }
        val positions = buckets.mapIndexed { index, bucket ->
            Offset(
                x = chartX(index, buckets.size, size.width),
                y = size.height * (1f - bucket.openedCount.toFloat() / maximum),
            )
        }
        buckets.zipWithNext().forEachIndexed { index, (firstBucket, secondBucket) ->
            if (firstBucket.observedDayCount > 0 && secondBucket.observedDayCount > 0) {
                drawLine(
                    color = colors.inner,
                    start = positions[index],
                    end = positions[index + 1],
                    strokeWidth = 3.dp.toPx(),
                    cap = StrokeCap.Round,
                )
            }
        }
        buckets.forEachIndexed { index, bucket ->
            if (bucket.observedDayCount > 0) {
                drawCircle(colors.inner, radius = 3.5.dp.toPx(), center = positions[index])
            } else {
                drawCircle(
                    colors.unknown.copy(alpha = 0.7f),
                    radius = 2.5.dp.toPx(),
                    center = Offset(positions[index].x, size.height),
                )
            }
        }
    }
}

private fun Resources.formatChartAccessibilityList(items: List<String>): String =
    if (items.isEmpty()) {
        getString(R.string.label_no_data)
    } else {
        ListFormatter.getInstance(configuration.locales[0]).format(items)
    }

@Composable
private fun ChartWithYAxis(
    labels: List<String>,
    buckets: List<LongTermBucket>,
    description: String,
    modifier: Modifier,
    drawChart: androidx.compose.ui.graphics.drawscope.DrawScope.() -> Unit,
) {
    val resources = LocalResources.current
    Column(modifier) {
        Row(Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.height(136.dp),
                verticalArrangement = Arrangement.SpaceBetween,
                horizontalAlignment = Alignment.End,
            ) {
                labels.forEach { label ->
                    Text(
                        label,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
            Canvas(
                Modifier
                    .weight(1f)
                    .height(136.dp)
                    .semantics { contentDescription = description },
                onDraw = drawChart,
            )
        }
        if (buckets.isNotEmpty()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Spacer(Modifier.width(42.dp))
                Text(
                    buckets.first().startMillis.toShortDateText(resources),
                    style = MaterialTheme.typography.labelSmall,
                )
                Spacer(Modifier.weight(1f))
                Text(
                    buckets.last().startMillis.toShortDateText(resources),
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }
    }
}

@Composable
internal fun AppRankingBar(
    value: Long,
    maximum: Long,
    color: Color,
    modifier: Modifier = Modifier,
) {
    val fraction = if (maximum <= 0L) {
        0f
    } else {
        (value.toFloat() / maximum.toFloat()).coerceIn(0f, 1f)
    }
    val trackColor = MaterialTheme.colorScheme.surfaceVariant
    Canvas(
        modifier
            .fillMaxWidth()
            .height(8.dp),
    ) {
        val cornerRadius = CornerRadius(size.height / 2f, size.height / 2f)
        drawRoundRect(
            color = trackColor,
            cornerRadius = cornerRadius,
        )
        drawRoundRect(
            color = color,
            size = Size(size.width * fraction, size.height),
            cornerRadius = cornerRadius,
        )
    }
}

@Composable
internal fun AppDisplayShareBar(
    coverFraction: Float,
    colors: PostureColors,
    modifier: Modifier = Modifier,
) {
    val visibleCoverFraction = coverFraction
        .takeIf { it.isFinite() }
        ?.coerceIn(0f, 1f)
        ?: 0f
    Canvas(
        modifier
            .fillMaxWidth()
            .height(8.dp)
            .clip(RoundedCornerShape(50)),
    ) {
        drawRect(color = colors.inner)
        drawRect(
            color = colors.cover,
            size = Size(size.width * visibleCoverFraction, size.height),
        )
    }
}

@Composable
internal fun LegendDot(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(9.dp)
                .background(color, RoundedCornerShape(50)),
        )
        Spacer(Modifier.width(5.dp))
        Text(label, style = MaterialTheme.typography.labelSmall)
    }
}

private fun chartX(index: Int, count: Int, width: Float): Float = when {
    count <= 0 -> 0f
    count == 1 -> width / 2f
    else -> index.toFloat() / (count - 1).toFloat() * width
}
