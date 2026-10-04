@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.nagopy.android.foldlytics.ui

import android.content.res.Resources
import android.icu.text.ListFormatter
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Surface
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nagopy.android.foldlytics.MainUiState
import com.nagopy.android.foldlytics.R
import com.nagopy.android.foldlytics.labelRes
import com.nagopy.android.foldlytics.model.AnalysisPeriod
import com.nagopy.android.foldlytics.model.AppUsage
import com.nagopy.android.foldlytics.model.DisplayPosture
import com.nagopy.android.foldlytics.model.InnerSessionSummary
import com.nagopy.android.foldlytics.model.LongTermInsights
import com.nagopy.android.foldlytics.model.MAX_CUSTOM_RANGE_DAYS
import com.nagopy.android.foldlytics.model.PeriodUsageSummary
import com.nagopy.android.foldlytics.model.customAnalysisRangeDayCount
import com.nagopy.android.foldlytics.model.recordedCalendarDayCount
import com.nagopy.android.foldlytics.toDurationText
import com.nagopy.android.foldlytics.toShortDateText
import com.nagopy.android.foldlytics.toTimeText
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

internal const val CUSTOM_PERIOD_DIALOG_TITLE_TAG = "custom_period_dialog_title"
internal const val CUSTOM_PERIOD_DIALOG_GUIDANCE_TAG = "custom_period_dialog_guidance"
internal const val CUSTOM_PERIOD_DIALOG_CANCEL_TAG = "custom_period_dialog_cancel"
internal const val CUSTOM_PERIOD_DIALOG_APPLY_TAG = "custom_period_dialog_apply"
internal const val ANALYSIS_PERIOD_OPTION_TAG_PREFIX = "analysis_period_option_"
internal const val HOME_PERMISSION_CARD_TAG = "home_permission_card"
internal const val LIVE_STATE_CARD_TAG = "live_state_card"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun HomeScreen(
    state: MainUiState,
    scaffoldPadding: PaddingValues,
    listState: LazyListState,
    onRequestUsageAccess: () -> Unit,
    onPeriodChanged: (AnalysisPeriod) -> Unit,
    onCustomPeriodChanged: (Long, Long) -> Unit,
    onRefresh: () -> Unit,
    onShareSummaryRequested: (PeriodUsageSummary) -> Unit,
    onOpenAppUsage: () -> Unit,
    onOpenInnerSessions: () -> Unit,
    screenshotSectionEndSpacing: Dp,
) {
    // An unfolded device has room for the summary and its trend side by side.
    BoxWithConstraints {
        val twoPane = maxWidth >= TwoPaneMinWidth
        FoldlyticsLazyColumn(
            scaffoldPadding = scaffoldPadding,
            listState = listState,
            maxContentWidth = if (twoPane) TwoPaneContentWidth else MaxContentWidth,
        ) {
            if (!state.hasUsageAccess) {
                item {
                    PermissionCard(
                        hasSavedData = state.periodSummary != null,
                        onOpenSettings = onRequestUsageAccess,
                    )
                }
            }
            item { LiveStateCard(state) }
            item {
                ResultHeader(
                    state = state,
                    onPeriodChanged = onPeriodChanged,
                    onCustomPeriodChanged = onCustomPeriodChanged,
                    onRefresh = onRefresh,
                )
            }
            state.periodSummary?.let { summary ->
                val trendInsights = state.longTermInsights.takeIf { summary.period.showsTrends }
                if (twoPane && trendInsights != null) {
                    item {
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Box(Modifier.weight(1f)) {
                                SummaryCard(
                                    summary = summary,
                                    canShare =
                                        summary.classifiedMillis > 0L &&
                                            !state.isLoading &&
                                            !state.isAnalysisLoading,
                                    onShare = { onShareSummaryRequested(summary) },
                                )
                            }
                            Box(Modifier.weight(1f)) {
                                UsageTrendCard(trendInsights)
                            }
                        }
                    }
                } else {
                    item {
                        Column {
                            SummaryCard(
                                summary = summary,
                                canShare =
                                    summary.classifiedMillis > 0L &&
                                        !state.isLoading &&
                                        !state.isAnalysisLoading,
                                onShare = { onShareSummaryRequested(summary) },
                            )
                            if (screenshotSectionEndSpacing > 0.dp) {
                                Spacer(Modifier.height(screenshotSectionEndSpacing))
                            }
                        }
                    }
                    if (summary.period.showsTrends) {
                        state.longTermInsights?.let { insights ->
                            item {
                                Column {
                                    UsageTrendCard(insights)
                                    if (screenshotSectionEndSpacing > 0.dp) {
                                        Spacer(Modifier.height(screenshotSectionEndSpacing))
                                    }
                                }
                            }
                        }
                    }
                }
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        HomeDetailLink(
                            title = stringResource(R.string.home_app_usage_link_title),
                            preview = appUsagePreview(summary.apps),
                            enabled = !state.isAnalysisLoading,
                            tag = HOME_APP_USAGE_LINK_TAG,
                            description = stringResource(R.string.content_desc_home_app_usage_link),
                            shape = groupedShape(0, 2),
                            onClick = onOpenAppUsage,
                        )
                        HomeDetailLink(
                            title = stringResource(R.string.home_inner_sessions_link_title),
                            preview = innerSessionsPreview(state.innerSessionSummary),
                            enabled = !state.isAnalysisLoading,
                            tag = HOME_INNER_SESSIONS_LINK_TAG,
                            description = stringResource(R.string.content_desc_home_inner_sessions_link),
                            shape = groupedShape(1, 2),
                            onClick = onOpenInnerSessions,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PermissionCard(
    hasSavedData: Boolean,
    onOpenSettings: () -> Unit,
) {
    Surface(
        modifier = Modifier.testTag(HOME_PERMISSION_CARD_TAG),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(CardPadding),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                stringResource(R.string.usage_access_required_title),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                stringResource(R.string.usage_access_required_body),
                style = MaterialTheme.typography.bodyMedium,
            )
            if (hasSavedData) {
                Text(
                    stringResource(R.string.saved_data_usage_access_note),
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Button(
                onClick = onOpenSettings,
                modifier = Modifier.align(Alignment.End),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.secondary,
                    contentColor = MaterialTheme.colorScheme.onSecondary,
                ),
            ) {
                Text(stringResource(R.string.action_review_details))
            }
        }
    }
}

@Composable
private fun LiveStateCard(state: MainUiState) {
    val colors = postureColors()
    val postureColor = when (state.currentPosture) {
        DisplayPosture.COVER -> colors.cover
        DisplayPosture.INNER -> colors.inner
        DisplayPosture.UNKNOWN -> colors.unknown
    }
    val postureLabel = stringResource(state.currentPosture.labelRes)
    val recordingStatus = stringResource(
        if (state.hasUsageAccess) {
            R.string.recording_status_available
        } else {
            R.string.recording_status_access_required
        },
    )
    val stateDescription = stringResource(
        state.currentPosture.liveStateDescriptionRes(state.hasUsageAccess),
    )
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(LIVE_STATE_CARD_TAG)
            .clearAndSetSemantics { contentDescription = stateDescription },
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        FlowRow(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.padding(end = 12.dp),
            ) {
                FoldGlyph(state.currentPosture, postureColor)
                Text(postureLabel, style = MaterialTheme.typography.titleSmall)
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Box(
                    Modifier
                        .size(7.dp)
                        .background(
                            if (state.hasUsageAccess) postureColor else MaterialTheme.colorScheme.outline,
                            CircleShape,
                        ),
                )
                Text(
                    recordingStatus,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ResultHeader(
    state: MainUiState,
    onPeriodChanged: (AnalysisPeriod) -> Unit,
    onCustomPeriodChanged: (Long, Long) -> Unit,
    onRefresh: () -> Unit,
) {
    val resources = LocalResources.current
    var expanded by rememberSaveable { mutableStateOf(false) }
    var showCustomPeriodDialog by rememberSaveable { mutableStateOf(false) }
    Column(
        modifier = Modifier.padding(top = 4.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.weight(1f)) {
                SectionTitle(stringResource(R.string.section_analysis_period))
            }
            val refreshEnabled = state.hasUsageAccess && !state.isLoading
            FilledTonalButton(
                onClick = onRefresh,
                enabled = refreshEnabled,
                contentPadding = PaddingValues(start = 14.dp, end = 18.dp),
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ),
            ) {
                val iconColor = LocalContentColor.current
                Canvas(Modifier.size(18.dp).clearAndSetSemantics {}) {
                    drawRefreshIcon(iconColor)
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    stringResource(
                        if (state.isLoading) R.string.action_syncing else R.string.action_refresh,
                    ),
                )
            }
        }
        Spacer(Modifier.height(2.dp))
        Box(Modifier.fillMaxWidth()) {
            val currentPeriodLabel = stringResource(state.selectedPeriod.labelRes)
            val periodSelectorDescription = stringResource(
                R.string.content_desc_analysis_period_selector,
                currentPeriodLabel,
            )
            val selectorEnabled = state.availablePeriods.isNotEmpty()
            Surface(
                onClick = { expanded = true },
                enabled = selectorEnabled,
                modifier = Modifier
                    .fillMaxWidth()
                    .alpha(if (selectorEnabled) 1f else 0.5f)
                    .testTag(ANALYSIS_PERIOD_SELECTOR_TAG)
                    .semantics {
                        contentDescription = periodSelectorDescription
                        role = Role.Button
                    },
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 60.dp)
                        .padding(start = 24.dp, end = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    FigureText(
                        currentPeriodLabel,
                        modifier = Modifier.weight(1f),
                        figureStyle = MaterialTheme.typography.headlineMedium,
                        unitStyle = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                    )
                    Box(
                        Modifier
                            .size(36.dp)
                            .background(MaterialTheme.colorScheme.surfaceContainerLowest, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Chevron(MaterialTheme.colorScheme.onSurface, down = true)
                    }
                }
            }
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                shape = MaterialTheme.shapes.medium,
                containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
            ) {
                AnalysisPeriod.entries.forEach { period ->
                    DropdownMenuItem(
                        text = { Text(stringResource(period.labelRes)) },
                        enabled = period in state.availablePeriods,
                        onClick = {
                            expanded = false
                            if (period == AnalysisPeriod.CUSTOM) {
                                showCustomPeriodDialog = true
                            } else {
                                onPeriodChanged(period)
                            }
                        },
                        modifier = Modifier.testTag(
                            "$ANALYSIS_PERIOD_OPTION_TAG_PREFIX${period.name}",
                        ),
                    )
                }
            }
        }
        Spacer(Modifier.height(2.dp))
        state.periodSummary?.let { summary ->
            Text(
                summary.analysisRangeText(resources),
                modifier = Modifier.padding(horizontal = 4.dp),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
            )
            summary.calendarRange?.let { range ->
                val days = range.recordedDayCount(ZoneId.systemDefault())
                if (days > 0 && days.toLong() < requireNotNull(summary.period.calendarDays)) {
                    Text(
                        stringResource(
                            R.string.analysis_partial_recording,
                            resources.getQuantityString(R.plurals.days_count, days, days),
                        ),
                        modifier = Modifier.padding(horizontal = 4.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        Text(
            state.recordRangeText(resources),
            modifier = Modifier.padding(horizontal = 4.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            state.lastSuccessfulSyncMillis?.let {
                stringResource(R.string.last_updated, it.toTimeText(resources))
            } ?: stringResource(R.string.never_synced),
            modifier = Modifier.padding(horizontal = 4.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    val recordStartMillis = state.recordRangeStartMillis
    val recordEndMillis = state.recordRangeEndMillis
    if (
        showCustomPeriodDialog &&
        recordStartMillis != null &&
        recordEndMillis != null
    ) {
        CustomPeriodDialog(
            recordStartMillis = recordStartMillis,
            recordEndMillis = recordEndMillis,
            initialStartMillis = state.customRangeStartMillis
                ?: state.periodSummary?.rangeStartMillis
                ?: recordStartMillis,
            initialEndMillis = state.customRangeEndMillis
                ?: state.periodSummary?.rangeEndMillis
                ?: recordEndMillis,
            onDismiss = { showCustomPeriodDialog = false },
            onConfirm = { startMillis, endMillis ->
                showCustomPeriodDialog = false
                onCustomPeriodChanged(startMillis, endMillis)
            },
        )
    }
}

@Composable
private fun HomeDetailLink(
    title: String,
    preview: String,
    enabled: Boolean,
    tag: String,
    description: String,
    shape: Shape,
    onClick: () -> Unit,
) {
    val fullDescription = if (enabled) {
        "$description $preview"
    } else {
        "$description $preview ${stringResource(R.string.detail_unavailable_while_analyzing)}"
    }
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(if (enabled) 1f else 0.6f)
            .testTag(tag)
            .clip(shape)
            .clickable(
                enabled = enabled,
                role = Role.Button,
                onClick = onClick,
            )
            .semantics(mergeDescendants = true) {
                contentDescription = fullDescription
                if (!enabled) disabled()
            },
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = CardPadding, top = 18.dp, end = 16.dp, bottom = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(
                    preview,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Box(
                Modifier
                    .size(40.dp)
                    .background(MaterialTheme.colorScheme.primary, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Chevron(MaterialTheme.colorScheme.onPrimary)
            }
        }
    }
}

@Composable
private fun appUsagePreview(apps: List<AppUsage>): String {
    val resources = LocalResources.current
    val names = rankAppsForHomePreview(apps).map(AppUsage::label)
    return if (names.isEmpty()) {
        resources.getString(R.string.home_app_usage_empty)
    } else {
        resources.getString(R.string.home_app_usage_preview, formatList(resources, names))
    }
}

internal fun rankAppsForHomePreview(apps: List<AppUsage>): List<AppUsage> = apps
    .let { rankAppsForDisplay(it, AppRankingBasis.TOTAL) }
    .asSequence()
    .take(3)
    .map(RankedAppUsage::app)
    .toList()

@Composable
private fun innerSessionsPreview(summary: InnerSessionSummary?): String {
    if (summary == null) return stringResource(R.string.home_inner_sessions_empty)
    if (summary.detectedOpenCount == 0) {
        return stringResource(R.string.inner_sessions_empty_no_opens)
    }
    if (summary.completeSessionCount == 0) {
        return pluralStringResource(
            R.plurals.inner_sessions_empty_no_complete,
            summary.detectedOpenCount,
            summary.detectedOpenCount,
        )
    }
    val resources = LocalResources.current
    val median = summary.medianInnerActiveMillis
        ?.toDurationText(resources)
        ?: stringResource(R.string.label_no_data)
    val longest = summary.longestInnerActiveMillis
        ?.toDurationText(resources)
        ?: stringResource(R.string.label_no_data)
    return stringResource(R.string.home_inner_sessions_preview, median, longest)
}

private fun formatList(resources: Resources, items: List<String>): String =
    ListFormatter.getInstance(resources.configuration.locales[0]).format(items)

@Composable
private fun SummaryCard(
    summary: PeriodUsageSummary,
    canShare: Boolean,
    onShare: () -> Unit,
) {
    val resources = LocalResources.current
    val palette = foldlyticsPalette
    val percent = resources.getString(R.string.value_percent_0, summary.innerRatio * 100)
    val noData = stringResource(R.string.label_no_data)
    val innerRatioText = if (summary.classifiedMillis > 0L) percent else noData
    val dataCoverageText = if (summary.observedMillis > 0L) {
        resources.getString(R.string.value_percent_0, summary.dataCoverageRatio * 100)
    } else {
        noData
    }
    val description = stringResource(
        R.string.content_desc_summary,
        stringResource(summary.period.labelRes),
        summary.coverMillis.toDurationText(resources),
        summary.innerMillis.toDurationText(resources),
        innerRatioText,
    )
    // Two joined surfaces: the ink panel carries the result, the paper panel how far to trust it.
    Column(
        modifier = Modifier.testTag(SUMMARY_CARD_TAG),
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Surface(
            shape = groupedShape(0, 2),
            color = palette.hero,
            contentColor = palette.onHero,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = CardPadding, top = 10.dp, end = CardPadding, bottom = CardPadding),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stringResource(R.string.summary_title),
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.titleMedium,
                        color = palette.onHeroMuted,
                    )
                    Box(Modifier.offset(x = 12.dp)) {
                        SummaryShareButton(
                            enabled = canShare,
                            onClick = onShare,
                            tint = palette.onHero,
                        )
                    }
                }
                val figure = if (summary.classifiedMillis > 0L) percent else "—"
                val openedText = resources.getString(R.string.value_open_count, summary.openedCount)
                BoxWithConstraints(Modifier.fillMaxWidth()) {
                    if (maxWidth >= 520.dp) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(28.dp),
                            verticalAlignment = Alignment.Bottom,
                        ) {
                            SummaryHeadline(figure, description, openedText, Modifier.weight(0.46f))
                            FoldSplit(summary, Modifier.weight(0.54f))
                        }
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
                            SummaryHeadline(figure, description, openedText)
                            FoldSplit(summary)
                        }
                    }
                }
            }
        }
        Surface(
            shape = groupedShape(1, 2),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = CardPadding, top = 16.dp, end = CardPadding, bottom = 18.dp),
            ) {
                InfoLine(
                    stringResource(R.string.label_classified_time),
                    summary.classifiedMillis.toDurationText(resources),
                )
                InfoLine(
                    stringResource(R.string.label_data_coverage),
                    dataCoverageText,
                )
                Text(
                    stringResource(R.string.display_classification_rate_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** The inner share as the headline figure, with the open count set against it. */
@Composable
private fun SummaryHeadline(
    figure: String,
    description: String,
    openedText: String,
    modifier: Modifier = Modifier,
) {
    val palette = foldlyticsPalette
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(
            Modifier
                .weight(1f)
                .semantics { contentDescription = description },
        ) {
            Overline(stringResource(R.string.posture_inner), color = palette.heroInner)
            FigureText(
                figure,
                figureStyle = MaterialTheme.typography.displayLarge.copy(
                    fontSize = 88.sp,
                    lineHeight = 92.sp,
                    letterSpacing = (-2.5).sp,
                ),
                unitStyle = MaterialTheme.typography.headlineSmall.copy(fontSize = 34.sp),
                color = palette.onHero,
                shrinkToFit = true,
            )
        }
        Column(
            modifier = Modifier.padding(bottom = 14.dp),
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FoldGlyph(
                    DisplayPosture.INNER,
                    palette.onHeroMuted,
                    Modifier.size(width = 18.dp, height = 14.dp),
                )
                Overline(stringResource(R.string.label_opened), color = palette.onHeroMuted)
            }
            FigureText(
                openedText,
                figureStyle = MaterialTheme.typography.displaySmall,
                unitStyle = MaterialTheme.typography.labelLarge,
                color = palette.onHero,
                maxLines = 1,
            )
        }
    }
}

/** Equal-sized labels stay readable; the separate hinge bar encodes the exact display shares. */
@Composable
private fun FoldSplit(summary: PeriodUsageSummary, modifier: Modifier = Modifier) {
    val resources = LocalResources.current
    val palette = foldlyticsPalette
    Column(
        modifier = modifier.fillMaxWidth().clearAndSetSemantics {},
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (summary.classifiedMillis > 0L) {
            AppDisplayShareBar(
                coverFraction = summary.coverMillis.toFloat() / summary.classifiedMillis,
                colors = PostureColors(
                    inner = palette.heroInner,
                    cover = palette.heroCover,
                    unknown = palette.onHeroMuted,
                ),
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            FoldPanel(
                label = stringResource(R.string.posture_cover),
                value = summary.coverMillis.toDurationText(resources),
                color = palette.heroCover,
                contentColor = palette.onHeroCover,
                shape = RoundedCornerShape(
                    topStart = 22.dp,
                    bottomStart = 22.dp,
                    topEnd = HingeCorner,
                    bottomEnd = HingeCorner,
                ),
                modifier = Modifier.weight(1f).fillMaxHeight(),
            )
            FoldPanel(
                label = stringResource(R.string.posture_inner),
                value = summary.innerMillis.toDurationText(resources),
                color = palette.heroInner,
                contentColor = palette.onHeroInner,
                shape = RoundedCornerShape(
                    topStart = HingeCorner,
                    bottomStart = HingeCorner,
                    topEnd = 22.dp,
                    bottomEnd = 22.dp,
                ),
                modifier = Modifier.weight(1f).fillMaxHeight(),
            )
        }
    }
}

@Composable
private fun FoldPanel(
    label: String,
    value: String,
    color: Color,
    contentColor: Color,
    shape: Shape,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .background(color, shape)
            .padding(horizontal = 14.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = contentColor)
        FigureText(
            value,
            figureStyle = MaterialTheme.typography.headlineMedium.copy(
                fontSize = 24.sp,
                lineHeight = 28.sp,
            ),
            unitStyle = MaterialTheme.typography.labelMedium,
            color = contentColor,
            shrinkToFit = true,
        )
    }
}

private enum class TrendMetric {
    INNER_RATIO,
    OPEN_COUNT,
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun UsageTrendCard(insights: LongTermInsights) {
    val resources = LocalResources.current
    val colors = postureColors()
    var selectedMetric by rememberSaveable { mutableStateOf(TrendMetric.INNER_RATIO) }
    LabCard(
        title = stringResource(R.string.section_usage_trends),
        modifier = Modifier.testTag(USAGE_TREND_CARD_TAG),
    ) {
        SegmentedToggle(
            options = TrendMetric.entries,
            selected = selectedMetric,
            onSelected = { selectedMetric = it },
            label = { metric ->
                stringResource(
                    if (metric == TrendMetric.INNER_RATIO) {
                        R.string.usage_trend_inner_ratio
                    } else {
                        R.string.usage_trend_open_count
                    },
                )
            },
            optionModifier = { metric ->
                val isInnerRatio = metric == TrendMetric.INNER_RATIO
                val description = stringResource(
                    if (isInnerRatio) {
                        R.string.content_desc_select_inner_ratio_trend
                    } else {
                        R.string.content_desc_select_open_count_trend
                    },
                )
                Modifier
                    .testTag(
                        if (isInnerRatio) USAGE_TREND_INNER_RATIO_TAG else USAGE_TREND_OPEN_COUNT_TAG,
                    )
                    .semantics { contentDescription = description }
            },
        )
        Spacer(Modifier.height(14.dp))
        if (selectedMetric == TrendMetric.INNER_RATIO) {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                LegendDot(colors.inner, stringResource(R.string.legend_inner_ratio))
                LegendDot(colors.unknown, stringResource(R.string.label_no_data))
            }
            Spacer(Modifier.height(8.dp))
            InnerRatioTrendChart(insights.buckets, colors)
            Spacer(Modifier.height(14.dp))
            InfoLine(
                stringResource(R.string.label_observed_days),
                resources.getString(
                    R.string.value_day_fraction,
                    insights.observedDayCount,
                    insights.calendarDayCount,
                ),
            )
            InfoLine(
                stringResource(R.string.label_inner_used_days),
                resources.getString(
                    R.string.value_day_fraction,
                    insights.innerUsedDayCount,
                    insights.observedDayCount,
                ),
            )
            InfoLine(
                stringResource(R.string.label_change_from_first_30_days),
                insights.thirtyDayInnerRatioDelta?.let {
                    resources.getString(R.string.value_points, it * 100)
                } ?: stringResource(R.string.label_no_data),
            )
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                LegendDot(colors.inner, stringResource(R.string.legend_open_count))
                LegendDot(colors.unknown, stringResource(R.string.label_no_data))
            }
            Spacer(Modifier.height(8.dp))
            OpenCountTrendChart(insights.buckets, colors)
            Spacer(Modifier.height(14.dp))
            InfoLine(
                stringResource(R.string.label_period_total),
                resources.getString(R.string.value_open_count, insights.openedCount),
            )
            InfoLine(
                stringResource(R.string.label_opened_per_observed_day),
                resources.getString(
                    R.string.value_average_open_count,
                    insights.averageOpenedPerObservedDay,
                ),
            )
        }
    }
}

private fun MainUiState.recordRangeText(resources: Resources): String {
    val startMillis = recordRangeStartMillis
        ?: return resources.getString(R.string.record_range_empty)
    val endMillis = recordRangeEndMillis
        ?: return resources.getString(R.string.record_range_empty)
    val dayCount = recordedCalendarDayCount(startMillis, endMillis, ZoneId.systemDefault())
    return resources.getString(
        R.string.record_range,
        startMillis.toShortDateText(resources),
        (endMillis - 1L).toShortDateText(resources),
        resources.getQuantityString(R.plurals.days_count, dayCount.toInt(), dayCount),
    )
}

private fun PeriodUsageSummary.analysisRangeText(resources: Resources): String {
    if (calendarRange != null && calendarRange.dataRange == null) {
        return resources.getString(
            if (period == AnalysisPeriod.TODAY) R.string.analysis_today_not_updated
            else R.string.analysis_period_no_recording,
        )
    }
    return resources.getString(
        R.string.analysis_range,
        resources.getString(period.labelRes),
        rangeStartMillis.toShortDateText(resources),
        (rangeEndMillis - 1L).coerceAtLeast(0L).toShortDateText(resources),
    )
}

private fun DisplayPosture.liveStateDescriptionRes(hasUsageAccess: Boolean): Int = when (this) {
    DisplayPosture.COVER -> if (hasUsageAccess) {
        R.string.content_desc_live_state_cover_recording
    } else {
        R.string.content_desc_live_state_cover_access_required
    }
    DisplayPosture.INNER -> if (hasUsageAccess) {
        R.string.content_desc_live_state_inner_recording
    } else {
        R.string.content_desc_live_state_inner_access_required
    }
    DisplayPosture.UNKNOWN -> if (hasUsageAccess) {
        R.string.content_desc_live_state_unknown_recording
    } else {
        R.string.content_desc_live_state_unknown_access_required
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CustomPeriodDialog(
    recordStartMillis: Long,
    recordEndMillis: Long,
    initialStartMillis: Long,
    initialEndMillis: Long,
    onDismiss: () -> Unit,
    onConfirm: (Long, Long) -> Unit,
) {
    val resources = LocalResources.current
    val zoneId = remember { ZoneId.systemDefault() }
    val recordStartDate = recordStartMillis.toLocalDate(zoneId)
    val recordEndDate = (recordEndMillis - 1L).toLocalDate(zoneId)
    val selectableStartMillis = recordStartDate.toDatePickerMillis()
    val selectableEndMillis = recordEndDate.toDatePickerMillis()
    val selectableDates = remember(selectableStartMillis, selectableEndMillis) {
        object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long): Boolean =
                utcTimeMillis in selectableStartMillis..selectableEndMillis

            override fun isSelectableYear(year: Int): Boolean =
                year in recordStartDate.year..recordEndDate.year
        }
    }
    val initialStartDate = initialStartMillis.toLocalDate(zoneId)
        .coerceIn(recordStartDate, recordEndDate)
    val initialEndDate = (initialEndMillis - 1L).toLocalDate(zoneId)
        .coerceIn(initialStartDate, recordEndDate)
    val pickerState = androidx.compose.material3.rememberDateRangePickerState(
        initialSelectedStartDateMillis = initialStartDate.toDatePickerMillis(),
        initialSelectedEndDateMillis = initialEndDate.toDatePickerMillis(),
        initialDisplayedMonthMillis = initialEndDate.toDatePickerMillis(),
        yearRange = recordStartDate.year..recordEndDate.year,
        selectableDates = selectableDates,
    )
    val selectedStartDate = pickerState.selectedStartDateMillis?.toDatePickerLocalDate()
    val selectedEndDate = pickerState.selectedEndDateMillis?.toDatePickerLocalDate()
    val selectedStartMillis = selectedStartDate?.atStartOfDay(zoneId)?.toInstant()?.toEpochMilli()
    val selectedEndMillis = selectedEndDate?.plusDays(1L)
        ?.atStartOfDay(zoneId)
        ?.toInstant()
        ?.toEpochMilli()
    val selectedDayCount = if (selectedStartMillis != null && selectedEndMillis != null) {
        customAnalysisRangeDayCount(selectedStartMillis, selectedEndMillis, zoneId)
    } else {
        0L
    }
    val canConfirm = selectedDayCount in 1L..MAX_CUSTOM_RANGE_DAYS
    val guidance = when {
        selectedStartDate == null || selectedEndDate == null ->
            stringResource(R.string.custom_period_select_both)
        selectedDayCount > MAX_CUSTOM_RANGE_DAYS ->
            stringResource(
                R.string.custom_period_too_long,
                resources.getQuantityString(
                    R.plurals.days_count,
                    selectedDayCount.toInt(),
                    selectedDayCount,
                ),
                resources.getQuantityString(
                    R.plurals.days_count,
                    MAX_CUSTOM_RANGE_DAYS.toInt(),
                    MAX_CUSTOM_RANGE_DAYS,
                ),
            )
        else -> stringResource(R.string.custom_period_selected, selectedDayCount)
    }

    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                modifier = Modifier.testTag(CUSTOM_PERIOD_DIALOG_APPLY_TAG),
                enabled = canConfirm,
                onClick = {
                    onConfirm(
                        requireNotNull(selectedStartMillis),
                        requireNotNull(selectedEndMillis),
                    )
                },
            ) {
                Text(stringResource(R.string.action_apply))
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag(CUSTOM_PERIOD_DIALOG_CANCEL_TAG),
            ) {
                Text(stringResource(R.string.action_cancel))
            }
        },
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            DateRangePicker(
                state = pickerState,
                modifier = Modifier.weight(1f),
                title = {
                    Text(
                        stringResource(R.string.custom_period_dialog_title),
                        modifier = Modifier
                            .testTag(CUSTOM_PERIOD_DIALOG_TITLE_TAG)
                            .padding(start = 24.dp, top = 16.dp, end = 12.dp),
                    )
                },
                showModeToggle = false,
            )
            Text(
                guidance,
                modifier = Modifier
                    .testTag(CUSTOM_PERIOD_DIALOG_GUIDANCE_TAG)
                    .padding(horizontal = 24.dp, vertical = 8.dp),
                style = MaterialTheme.typography.bodySmall,
                color = if (selectedDayCount > MAX_CUSTOM_RANGE_DAYS) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }
    }
}

private fun Long.toLocalDate(zoneId: ZoneId): LocalDate =
    Instant.ofEpochMilli(this).atZone(zoneId).toLocalDate()

private fun LocalDate.toDatePickerMillis(): Long =
    atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

private fun Long.toDatePickerLocalDate(): LocalDate =
    Instant.ofEpochMilli(this).atZone(ZoneOffset.UTC).toLocalDate()
