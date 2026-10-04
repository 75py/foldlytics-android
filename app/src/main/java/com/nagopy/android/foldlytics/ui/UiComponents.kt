@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.nagopy.android.foldlytics.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import com.nagopy.android.foldlytics.R
import com.nagopy.android.foldlytics.labelRes
import com.nagopy.android.foldlytics.model.AnalysisPeriod
import com.nagopy.android.foldlytics.model.DisplayPosture
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
internal const val INNER_SESSION_APP_ICONS_TAG_PREFIX = "inner_session_app_icons_"
internal const val INNER_SESSION_APP_ICON_TAG_PREFIX = "inner_session_app_icon_"
internal const val INNER_SESSION_APP_DURATION_TAG_PREFIX = "inner_session_app_duration_"
internal const val INNER_SESSION_LONG_SESSIONS_CARD_TAG = "inner_session_long_sessions_card"
internal const val INNER_SESSION_METHOD_TAG = "inner_session_method"

internal val MaxContentWidth = 640.dp
internal val TwoPaneContentWidth = 1080.dp
internal val TwoPaneMinWidth = 760.dp
internal val CardPadding = 20.dp

@Composable
internal fun FoldlyticsLazyColumn(
    modifier: Modifier = Modifier,
    scaffoldPadding: PaddingValues,
    listState: LazyListState = rememberLazyListState(),
    maxContentWidth: Dp = MaxContentWidth,
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
                .widthIn(max = maxContentWidth)
                .fillMaxSize(),
            state = listState,
            contentPadding = PaddingValues(start = 16.dp, top = 4.dp, end = 16.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
            content = content,
        )
    }
}

private fun DrawScope.iconLine(color: Color, start: Offset, end: Offset) {
    drawLine(
        color = color,
        start = start,
        end = end,
        strokeWidth = 2.25.dp.toPx(),
        cap = StrokeCap.Round,
    )
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
            // Two uneven strokes: a folded page rather than the stock three-line menu.
            iconLine(lineColor, Offset(3.dp.toPx(), 8.dp.toPx()), Offset(21.dp.toPx(), 8.dp.toPx()))
            iconLine(lineColor, Offset(3.dp.toPx(), 16.dp.toPx()), Offset(14.dp.toPx(), 16.dp.toPx()))
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
            val tip = Offset(5.dp.toPx(), 12.dp.toPx())
            iconLine(lineColor, tip, Offset(19.dp.toPx(), 12.dp.toPx()))
            iconLine(lineColor, tip, Offset(11.5.dp.toPx(), 5.5.dp.toPx()))
            iconLine(lineColor, tip, Offset(11.5.dp.toPx(), 18.5.dp.toPx()))
        }
    }
}

@Composable
internal fun SummaryShareButton(
    enabled: Boolean,
    onClick: () -> Unit,
    tint: Color = MaterialTheme.colorScheme.primary,
) {
    val iconColor = if (enabled) tint else tint.copy(alpha = 0.38f)
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
            drawLine(iconColor, left, upperRight, strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round)
            drawLine(iconColor, left, lowerRight, strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round)
            listOf(left, upperRight, lowerRight).forEach { center ->
                drawCircle(color = iconColor, radius = 3.dp.toPx(), center = center)
            }
        }
    }
}

/** A chevron pointing right, or down when [down] is set. */
@Composable
internal fun Chevron(color: Color, modifier: Modifier = Modifier, down: Boolean = false) {
    Canvas(modifier.size(20.dp).clearAndSetSemantics {}) {
        val near = 7.5.dp.toPx()
        val far = 12.5.dp.toPx()
        val low = 5.dp.toPx()
        val mid = 10.dp.toPx()
        val high = 15.dp.toPx()
        if (down) {
            iconLine(color, Offset(low, near), Offset(mid, far))
            iconLine(color, Offset(high, near), Offset(mid, far))
        } else {
            iconLine(color, Offset(near, low), Offset(far, mid))
            iconLine(color, Offset(near, high), Offset(far, mid))
        }
    }
}

/**
 * The device as a glyph: one narrow panel when closed, two panels when open. The second panel
 * unfolds with the theme's spatial spring whenever the posture changes.
 */
@Composable
internal fun FoldGlyph(
    posture: DisplayPosture,
    color: Color,
    modifier: Modifier = Modifier,
) {
    val openness by animateFloatAsState(
        targetValue = when (posture) {
            DisplayPosture.COVER -> 0f
            DisplayPosture.INNER -> 1f
            DisplayPosture.UNKNOWN -> 0.45f
        },
        animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
        label = "foldGlyphOpenness",
    )
    Canvas(modifier.size(width = 28.dp, height = 22.dp).clearAndSetSemantics {}) {
        val panelWidth = size.width * 9f / 28f
        val gap = size.width / 14f * openness.coerceIn(0f, 1f)
        val secondWidth = panelWidth * openness.coerceAtLeast(0f)
        val total = panelWidth + gap + secondWidth
        val left = (size.width - total) / 2f
        val outer = CornerRadius(size.height * 3.5f / 22f)
        drawRoundRect(
            color = color,
            topLeft = Offset(left, 0f),
            size = Size(panelWidth, size.height),
            cornerRadius = outer,
        )
        if (secondWidth > 0.5f) {
            drawRoundRect(
                color = color.copy(alpha = if (posture == DisplayPosture.UNKNOWN) 0.4f else 1f),
                topLeft = Offset(left + panelWidth + gap, 0f),
                size = Size(secondWidth, size.height),
                cornerRadius = outer,
            )
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
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            if (rangeStartMillis != null && rangeEndMillis != null && rangeEndMillis <= rangeStartMillis) {
                stringResource(
                    if (period == AnalysisPeriod.TODAY) R.string.analysis_today_not_updated
                    else R.string.analysis_period_no_recording,
                )
            } else if (rangeStartMillis != null && rangeEndMillis != null) {
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
internal fun ApplicationIcon(
    packageName: String,
    label: String,
    size: Dp = 44.dp,
) {
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
            modifier = Modifier.size(size),
            contentScale = ContentScale.Fit,
        )
    } else {
        Box(
            modifier = Modifier
                .size(size)
                .background(MaterialTheme.colorScheme.surfaceContainerHighest, RoundedCornerShape(30))
                .clearAndSetSemantics {},
            contentAlignment = Alignment.Center,
        ) {
            Text(
                label.firstOrNull()?.uppercase() ?: "?",
                fontSize = if (size < 44.dp) 12.sp else TextUnit.Unspecified,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * Sets the digits of a formatted value in the display figure face and the units around them in a
 * smaller sans, so "259h 1m" and "259時間1分" read as numbers first in every language.
 */
internal fun figureText(
    text: String,
    figureStyle: TextStyle,
    unitStyle: TextStyle,
): AnnotatedString = buildAnnotatedString {
    // The unit size is relative to the figure so the pair scales together when auto-sized.
    val figureSpan = SpanStyle(
        fontFamily = FigureFontFamily,
        fontWeight = figureStyle.fontWeight ?: FontWeight.Medium,
        letterSpacing = figureStyle.letterSpacing,
    )
    val unitSpan = SpanStyle(
        fontFamily = FontFamily.Default,
        fontWeight = unitStyle.fontWeight ?: FontWeight.SemiBold,
        fontSize = (unitStyle.fontSize.value / figureStyle.fontSize.value).em,
        letterSpacing = 0.sp,
    )
    text.forEachIndexed { index, character ->
        val isFigure = character.isDigit() || character == '—' ||
            ((character == '.' || character == ',') &&
                text.getOrNull(index - 1)?.isDigit() == true &&
                text.getOrNull(index + 1)?.isDigit() == true)
        withStyle(if (isFigure) figureSpan else unitSpan) { append(character) }
    }
}

@Composable
internal fun FigureText(
    text: String,
    modifier: Modifier = Modifier,
    figureStyle: TextStyle = MaterialTheme.typography.headlineMedium,
    unitStyle: TextStyle = MaterialTheme.typography.labelLarge,
    color: Color = Color.Unspecified,
    maxLines: Int = 2,
    textAlign: TextAlign? = null,
    shrinkToFit: Boolean = false,
) {
    Text(
        figureText(text, figureStyle, unitStyle),
        modifier = modifier,
        style = figureStyle,
        color = color,
        maxLines = if (shrinkToFit) 1 else maxLines,
        softWrap = !shrinkToFit,
        overflow = TextOverflow.Ellipsis,
        textAlign = textAlign,
        autoSize = if (shrinkToFit) {
            TextAutoSize.StepBased(
                minFontSize = figureStyle.fontSize * 0.5f,
                maxFontSize = figureStyle.fontSize,
                stepSize = 1.sp,
            )
        } else {
            null
        },
    )
}

@Composable
internal fun Metric(
    label: String,
    value: String,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = color)
        FigureText(
            value,
            figureStyle = MaterialTheme.typography.headlineMedium,
            unitStyle = MaterialTheme.typography.labelLarge,
        )
    }
}

@Composable
internal fun InfoLine(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            label,
            modifier = Modifier.weight(1f, fill = false),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            value,
            modifier = Modifier
                .padding(start = 12.dp)
                .weight(1f, fill = false),
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.End,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )
    }
    Spacer(Modifier.height(6.dp))
}

@Composable
internal fun LabCard(
    title: String,
    modifier: Modifier = Modifier,
    titleAction: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(CardPadding),
            verticalArrangement = Arrangement.spacedBy(4.dp),
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
                )
                titleAction?.invoke()
            }
            Spacer(Modifier.height(8.dp))
            content()
        }
    }
}

@Composable
internal fun HintCard(text: String, modifier: Modifier = Modifier, isError: Boolean = false) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = if (isError) {
            MaterialTheme.colorScheme.errorContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainer
        },
        contentColor = if (isError) {
            MaterialTheme.colorScheme.onErrorContainer
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
    ) {
        Text(
            text,
            modifier = Modifier.padding(CardPadding),
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
internal fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleLarge)
}

/** A small caption that names the block beneath it. */
@Composable
internal fun Overline(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    Text(
        text,
        modifier = modifier,
        style = MaterialTheme.typography.labelMedium,
        color = color,
    )
}

/**
 * The shape of one row in a stack of joined surfaces: full corners at the ends of the stack and
 * hinge corners where two rows meet.
 */
internal fun groupedShape(index: Int, count: Int, corner: Dp = PanelCorner): Shape {
    val top = if (index == 0) corner else HingeCorner
    val bottom = if (index == count - 1) corner else HingeCorner
    return RoundedCornerShape(topStart = top, topEnd = top, bottomStart = bottom, bottomEnd = bottom)
}

/**
 * A single-choice control whose ink pill slides between options on the theme's spatial spring.
 */
@Composable
internal fun <T> SegmentedToggle(
    options: List<T>,
    selected: T,
    onSelected: (T) -> Unit,
    label: @Composable (T) -> String,
    optionModifier: @Composable (T) -> Modifier,
    modifier: Modifier = Modifier,
) {
    val selectedIndex = options.indexOf(selected).coerceAtLeast(0)
    val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val indicatorIndex by animateFloatAsState(
        targetValue = selectedIndex.toFloat(),
        animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
        label = "segmentedToggleIndicator",
    )
    val indicatorColor = MaterialTheme.colorScheme.primary
    BoxWithConstraints(modifier.fillMaxWidth()) {
        // Keep complete labels and generous targets when three choices cannot fit at the user's
        // text size. The same radio group becomes a vertical stack instead of splitting words.
        if (maxWidth / options.size / LocalDensity.current.fontScale < 64.dp) {
            Column(
                Modifier.fillMaxWidth().selectableGroup(),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                options.forEachIndexed { index, option ->
                    val isSelected = option == selected
                    val background by animateColorAsState(
                        if (isSelected) indicatorColor else MaterialTheme.colorScheme.surfaceContainerHighest,
                        animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
                        label = "stackedToggleBackground",
                    )
                    val foreground by animateColorAsState(
                        if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                        animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
                        label = "stackedToggleText",
                    )
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .clip(groupedShape(index, options.size, 20.dp))
                            .background(background)
                            .then(optionModifier(option))
                            .selectable(
                                selected = isSelected,
                                role = Role.RadioButton,
                                onClick = { onSelected(option) },
                            )
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(label(option), style = MaterialTheme.typography.labelLarge, color = foreground)
                    }
                }
            }
        } else {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                    .padding(4.dp)
                    .drawBehind {
                        val optionWidth = size.width / options.size
                        drawRoundRect(
                            color = indicatorColor,
                            topLeft = Offset(
                                optionWidth * if (isRtl) options.lastIndex - indicatorIndex else indicatorIndex,
                                0f,
                            ),
                            size = Size(optionWidth, size.height),
                            cornerRadius = CornerRadius(size.height / 2f),
                        )
                    },
            ) {
                Row(Modifier.fillMaxWidth().selectableGroup()) {
                    options.forEach { option ->
                        val isSelected = option == selected
                        val textColor by animateColorAsState(
                            targetValue = if (isSelected) {
                                MaterialTheme.colorScheme.onPrimary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
                            label = "segmentedToggleText",
                        )
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 48.dp)
                                .clip(CircleShape)
                                .then(optionModifier(option))
                                .selectable(
                                    selected = isSelected,
                                    role = Role.RadioButton,
                                    onClick = { onSelected(option) },
                                )
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                label(option),
                                textAlign = TextAlign.Center,
                                style = MaterialTheme.typography.labelLarge,
                                color = textColor,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }
    }
}

/** A ring-shaped refresh arrow drawn to match the app's stroked icons. */
internal fun DrawScope.drawRefreshIcon(color: Color) {
    val stroke = 2.dp.toPx()
    val radius = size.minDimension / 2f - stroke
    val endAngle = Math.toRadians(-40.0)
    drawArc(
        color = color,
        startAngle = 20f,
        sweepAngle = 300f,
        useCenter = false,
        topLeft = Offset(center.x - radius, center.y - radius),
        size = Size(radius * 2f, radius * 2f),
        style = Stroke(width = stroke, cap = StrokeCap.Round),
    )
    val tip = Offset(
        center.x + radius * kotlin.math.cos(endAngle).toFloat(),
        center.y + radius * kotlin.math.sin(endAngle).toFloat(),
    )
    val head = 4.5.dp.toPx()
    drawLine(color, tip, Offset(tip.x, tip.y - head), stroke, StrokeCap.Round)
    drawLine(color, tip, Offset(tip.x - head, tip.y + 0.5.dp.toPx()), stroke, StrokeCap.Round)
}
