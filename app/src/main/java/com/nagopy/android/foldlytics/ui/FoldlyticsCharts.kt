@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.nagopy.android.foldlytics.ui

import android.content.res.Resources
import android.icu.text.ListFormatter
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.nagopy.android.foldlytics.R
import com.nagopy.android.foldlytics.model.LongTermBucket
import com.nagopy.android.foldlytics.toShortDateText

internal data class PostureColors(
    val inner: Color,
    val cover: Color,
    val unknown: Color,
)

internal data class OpenCountChartScale(
    val maximum: Long,
    val middle: Long,
)

internal fun openCountChartScale(observedMaximum: Int): OpenCountChartScale {
    val nonNegativeMaximum = observedMaximum.coerceAtLeast(0).toLong()
    val maximum = when {
        nonNegativeMaximum < 2L -> 2L
        nonNegativeMaximum % 2L == 0L -> nonNegativeMaximum
        else -> nonNegativeMaximum + 1L
    }
    return OpenCountChartScale(maximum = maximum, middle = maximum / 2L)
}

@Composable
internal fun postureColors(): PostureColors = if (isSystemInDarkTheme()) {
    PostureColors(
        inner = Color(DisplayChartPalette.DARK_INNER),
        cover = Color(DisplayChartPalette.DARK_COVER),
        unknown = Color(0xFF9CA3AF),
    )
} else {
    PostureColors(
        inner = Color(DisplayChartPalette.LIGHT_INNER),
        cover = Color(DisplayChartPalette.LIGHT_COVER),
        unknown = Color(0xFF6B7280),
    )
}

@Composable
internal fun PostureLegend(colors: PostureColors) {
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        LegendDot(colors.inner, stringResource(R.string.posture_inner))
        LegendDot(colors.cover, stringResource(R.string.posture_cover))
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
        drawTrend(
            colors = colors,
            hasData = buckets.map { it.classifiedMillis > 0L },
            fractions = buckets.map { it.innerRatio.coerceIn(0f, 1f) },
        )
    }
}

@Composable
internal fun OpenCountTrendChart(
    buckets: List<LongTermBucket>,
    colors: PostureColors,
    modifier: Modifier = Modifier,
) {
    val resources = LocalResources.current
    val observedMaximum = buckets
        .filter { it.observedDayCount > 0 }
        .maxOfOrNull(LongTermBucket::openedCount)
        ?: 0
    val scale = openCountChartScale(observedMaximum)
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
            resources.getString(R.string.value_open_count, scale.maximum),
            resources.getString(R.string.value_open_count, scale.middle),
            resources.getString(R.string.value_open_count, 0),
        ),
        buckets = buckets,
        description = description,
        modifier = modifier,
    ) {
        drawTrend(
            colors = colors,
            hasData = buckets.map { it.observedDayCount > 0 },
            fractions = buckets.map { bucket ->
                bucket.openedCount.toLong().coerceIn(0L, scale.maximum).toFloat() /
                    scale.maximum.toFloat()
            },
        )
    }
}

/**
 * Draws one trend series: dashed guides, a soft fill under each unbroken run, and the line itself.
 * Buckets without data break the line and leave a quiet marker on the baseline.
 */
private fun DrawScope.drawTrend(
    colors: PostureColors,
    hasData: List<Boolean>,
    fractions: List<Float>,
) {
    val gridColor = colors.unknown.copy(alpha = 0.3f)
    val dash = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 5.dp.toPx()))
    listOf(0f, size.height / 2f).forEach { y ->
        drawLine(gridColor, Offset(0f, y), Offset(size.width, y), 1.dp.toPx(), pathEffect = dash)
    }
    drawLine(gridColor, Offset(0f, size.height), Offset(size.width, size.height), 1.dp.toPx())
    val positions = fractions.mapIndexed { index, fraction ->
        Offset(chartX(index, fractions.size, size.width), size.height * (1f - fraction))
    }
    val fill = Brush.verticalGradient(
        listOf(colors.inner.copy(alpha = 0.26f), colors.inner.copy(alpha = 0f)),
        startY = 0f,
        endY = size.height,
    )
    var runStart = -1
    for (index in 0..positions.size) {
        val present = index < positions.size && hasData[index]
        if (present && runStart < 0) runStart = index
        if (!present && runStart >= 0) {
            val run = positions.subList(runStart, index)
            if (run.size > 1) {
                val area = Path().apply {
                    moveTo(run.first().x, size.height)
                    run.forEach { lineTo(it.x, it.y) }
                    lineTo(run.last().x, size.height)
                    close()
                }
                drawPath(area, fill)
                val line = Path().apply {
                    moveTo(run.first().x, run.first().y)
                    run.drop(1).forEach { lineTo(it.x, it.y) }
                }
                drawPath(
                    line,
                    colors.inner,
                    style = Stroke(
                        width = 3.dp.toPx(),
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round,
                    ),
                )
            } else {
                drawCircle(colors.inner, radius = 3.5.dp.toPx(), center = run.first())
            }
            runStart = -1
        }
    }
    positions.forEachIndexed { index, position ->
        if (!hasData[index]) {
            drawCircle(
                colors.unknown.copy(alpha = 0.7f),
                radius = 2.5.dp.toPx(),
                center = Offset(position.x, size.height),
            )
        }
    }
    val lastIndex = hasData.lastIndexOf(true)
    if (lastIndex >= 0) {
        drawCircle(colors.inner.copy(alpha = 0.22f), 9.dp.toPx(), positions[lastIndex])
        drawCircle(colors.inner, 4.5.dp.toPx(), positions[lastIndex])
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
    drawChart: DrawScope.() -> Unit,
) {
    val resources = LocalResources.current
    val axisWidth = AxisLabelWidth * LocalDensity.current.fontScale.coerceAtLeast(1f)
    Column(modifier) {
        Row(Modifier.fillMaxWidth()) {
            // The labels are as tall as the plot plus half a line at each end, which centers the
            // first and last label on their guides.
            Column(
                modifier = Modifier
                    .width(axisWidth)
                    .height(ChartHeight + AxisLabelOverhang * 2),
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
            val reveal = remember { Animatable(0f) }
            val revealSpec = MaterialTheme.motionScheme.slowSpatialSpec<Float>()
            LaunchedEffect(Unit) { reveal.animateTo(1f, revealSpec) }
            Canvas(
                Modifier
                    .weight(1f)
                    .padding(vertical = AxisLabelOverhang)
                    .height(ChartHeight)
                    .semantics { contentDescription = description },
            ) {
                // The line draws itself in from the oldest bucket; overshoot must not un-clip it.
                clipRect(
                    left = -12.dp.toPx(),
                    top = -12.dp.toPx(),
                    right = (size.width + 12.dp.toPx()) * reveal.value.coerceIn(0f, 1f),
                    bottom = size.height + 12.dp.toPx(),
                ) {
                    drawChart()
                }
            }
        }
        if (buckets.isNotEmpty()) {
            val firstDate = buckets.first().startMillis.toShortDateText(resources)
            val lastDate = buckets.last().startMillis.toShortDateText(resources)
            BoxWithConstraints(Modifier.fillMaxWidth().padding(start = axisWidth + 8.dp)) {
                if (maxWidth / LocalDensity.current.fontScale < 220.dp) {
                    Column(Modifier.fillMaxWidth()) {
                        Text(
                            firstDate,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            lastDate,
                            modifier = Modifier.align(Alignment.End),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(
                            firstDate,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            lastDate,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
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
    val target = if (maximum <= 0L) {
        0f
    } else {
        (value.toFloat() / maximum.toFloat()).coerceIn(0f, 1f)
    }
    val fraction by animateFloatAsState(
        targetValue = target,
        animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
        label = "appRankingBar",
    )
    val trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
    Canvas(
        modifier
            .fillMaxWidth()
            .height(BarHeight),
    ) {
        val cornerRadius = CornerRadius(size.height / 2f, size.height / 2f)
        drawRoundRect(
            color = trackColor,
            cornerRadius = cornerRadius,
        )
        val width = size.width * fraction.coerceIn(0f, 1f)
        if (width > 0f) {
            drawRoundRect(
                color = color,
                size = Size(width.coerceAtLeast(size.height), size.height),
                cornerRadius = cornerRadius,
            )
        }
    }
}

/** The outer and inner shares as two panels that meet at a hinge gap. */
@Composable
internal fun AppDisplayShareBar(
    coverFraction: Float,
    colors: PostureColors,
    modifier: Modifier = Modifier,
) {
    val target = coverFraction
        .takeIf { it.isFinite() }
        ?.coerceIn(0f, 1f)
        ?: 0f
    val visibleCoverFraction by animateFloatAsState(
        targetValue = target,
        animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
        label = "appDisplayShareBar",
    )
    Canvas(
        modifier
            .fillMaxWidth()
            .height(BarHeight)
            .clip(RoundedCornerShape(50)),
    ) {
        val split = size.width * visibleCoverFraction.coerceIn(0f, 1f)
        // Never erase a small but nonzero share with the decorative hinge gap.
        val gap = minOf(3.dp.toPx(), split, size.width - split)
        val hinge = CornerRadius(1.5.dp.toPx())
        if (split > 0f) {
            drawRoundRect(
                color = colors.cover,
                size = Size((split - gap / 2f).coerceAtLeast(0f), size.height),
                cornerRadius = hinge,
            )
        }
        if (split < size.width) {
            drawRoundRect(
                color = colors.inner,
                topLeft = Offset(split + gap / 2f, 0f),
                size = Size((size.width - split - gap / 2f).coerceAtLeast(0f), size.height),
                cornerRadius = hinge,
            )
        }
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
        Spacer(Modifier.width(6.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private val ChartHeight = 148.dp
private val AxisLabelWidth = 38.dp
private val AxisLabelOverhang = 8.dp
private val BarHeight = 10.dp

private fun chartX(index: Int, count: Int, width: Float): Float = when {
    count <= 0 -> 0f
    count == 1 -> width / 2f
    else -> index.toFloat() / (count - 1).toFloat() * width
}
