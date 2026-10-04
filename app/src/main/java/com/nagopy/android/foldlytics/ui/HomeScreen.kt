@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.nagopy.android.foldlytics.ui

import android.content.res.Resources
import android.icu.text.ListFormatter
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
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
    onShareSummaryRequested: (PeriodUsageSummary) -> Unit,
    onOpenAppUsage: () -> Unit,
    onOpenInnerSessions: () -> Unit,
    screenshotSectionEndSpacing: Dp,
) {
    val hasSavedData = state.periodSummary != null || state.recordRangeStartMillis != null
    BoxWithConstraints {
        val twoPane = maxWidth >= TwoPaneMinWidth && LocalDensity.current.fontScale < 1.5f
        FoldlyticsLazyColumn(
            scaffoldPadding = scaffoldPadding,
            listState = listState,
            maxContentWidth = if (twoPane) TwoPaneContentWidth else MaxContentWidth,
        ) {
            if (!state.hasUsageAccess) {
                item {
                    PermissionCard(
                        hasSavedData = hasSavedData,
                        onOpenSettings = onRequestUsageAccess,
                    )
                }
            }
            if (state.hasUsageAccess || hasSavedData) {
                item(key = "result-header") { ResultHeader(state, onPeriodChanged, onCustomPeriodChanged) }
            }
            state.periodSummary?.let { summary ->
                val canShare = summary.classifiedMillis > 0L && !state.isLoading && !state.isAnalysisLoading
                if (twoPane) {
                    item {
                        Row(horizontalArrangement = Arrangement.spacedBy(32.dp)) {
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                                SummaryCard(summary, canShare) { onShareSummaryRequested(summary) }
                                SessionEntry(state, onOpenInnerSessions)
                            }
                            Box(Modifier.weight(1f)) {
                                InnerAppsPreview(state, onOpenAppUsage)
                            }
                        }
                    }
                } else {
                    item {
                        SummaryCard(summary, canShare) { onShareSummaryRequested(summary) }
                        if (screenshotSectionEndSpacing > 0.dp) Spacer(Modifier.height(screenshotSectionEndSpacing))
                    }
                    item {
                        BoxWithConstraints {
                            if (maxWidth / LocalDensity.current.fontScale < 280.dp) {
                                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    AppEntry(state, onOpenAppUsage)
                                    SessionEntry(state, onOpenInnerSessions)
                                }
                            } else {
                                Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    Box(Modifier.weight(1f).fillMaxHeight()) { AppEntry(state, onOpenAppUsage) }
                                    Box(Modifier.weight(1f).fillMaxHeight()) { SessionEntry(state, onOpenInnerSessions) }
                                }
                            }
                        }
                    }
                }
                if (summary.period.showsTrends) {
                    state.longTermInsights?.let { insights ->
                        item {
                            UsageTrendCard(insights)
                            if (screenshotSectionEndSpacing > 0.dp) Spacer(Modifier.height(screenshotSectionEndSpacing))
                        }
                    }
                }
            }
            item(key = "record-details") { RecordingDetails(state) }
        }
    }
}

@Composable
private fun PermissionCard(
    hasSavedData: Boolean,
    onOpenSettings: () -> Unit,
) {
    if (!hasSavedData) {
        Column(
            modifier = Modifier.fillMaxWidth().testTag(HOME_PERMISSION_CARD_TAG)
                .padding(horizontal = 8.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            DisplayDuet(Modifier.size(208.dp, 174.dp).align(Alignment.CenterHorizontally))
            Text(stringResource(R.string.home_recording_title), style = MaterialTheme.typography.headlineLarge)
            Text(stringResource(R.string.home_recording_body), style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Button(onClick = onOpenSettings, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
                Text(stringResource(R.string.action_review_details))
            }
            Text(stringResource(R.string.drawer_privacy_note), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }
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
) {
    val resources = LocalResources.current
    var expanded by rememberSaveable { mutableStateOf(false) }
    var showCustomPeriodDialog by rememberSaveable { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                stringResource(R.string.home_display_balance),
                modifier = Modifier.padding(top = 10.dp, end = 12.dp),
                style = MaterialTheme.typography.headlineSmall,
            )
            Box {
                val selectorDescription = stringResource(
                    R.string.content_desc_analysis_period_selector,
                    stringResource(state.selectedPeriod.labelRes),
                )
                Surface(
                    onClick = { expanded = true },
                    enabled = state.availablePeriods.isNotEmpty(),
                    modifier = Modifier.testTag(ANALYSIS_PERIOD_SELECTOR_TAG)
                        .semantics { contentDescription = selectorDescription; role = Role.Button },
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceContainer,
                ) {
                    Row(
                        Modifier.heightIn(min = 48.dp).padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Text(stringResource(state.selectedPeriod.labelRes), style = MaterialTheme.typography.labelLarge)
                        Chevron(MaterialTheme.colorScheme.onSurfaceVariant, down = true)
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
        }
        state.periodSummary?.let { summary ->
            Text(
                summary.analysisRangeText(resources),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            summary.calendarRange?.let { range ->
                val days = range.recordedDayCount(ZoneId.systemDefault())
                if (days > 0 && days.toLong() < requireNotNull(summary.period.calendarDays)) {
                    Text(
                        stringResource(R.string.analysis_partial_recording,
                            resources.getQuantityString(R.plurals.days_count, days, days)),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
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
    val colors = postureColors()
    val percent = if (summary.classifiedMillis > 0L) {
        resources.getString(R.string.value_percent_0, summary.innerRatio * 100)
    } else "—"
    val description = stringResource(
        R.string.content_desc_summary,
        stringResource(summary.period.labelRes),
        summary.coverMillis.toDurationText(resources),
        summary.innerMillis.toDurationText(resources),
        if (summary.classifiedMillis > 0L) percent else stringResource(R.string.label_no_data),
    )
    Column(
        Modifier.fillMaxWidth().testTag(SUMMARY_CARD_TAG),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f).clearAndSetSemantics { contentDescription = description }) {
                Text(
                    stringResource(R.string.home_inner_share),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                FigureText(
                    percent,
                    figureStyle = MaterialTheme.typography.displayLarge,
                    unitStyle = MaterialTheme.typography.headlineLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    shrinkToFit = true,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                SummaryShareButton(canShare, onShare)
                DisplayDuet(Modifier.size(104.dp, 86.dp))
            }
        }
        if (summary.classifiedMillis > 0L) {
            DisplayRatioRibbon(coverFraction = 1f - summary.innerRatio)
        }
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            DisplayTime(
                stringResource(R.string.posture_cover), summary.coverMillis.toDurationText(resources),
                colors.cover, Modifier.weight(1f),
            )
            DisplayTime(
                stringResource(R.string.posture_inner), summary.innerMillis.toDurationText(resources),
                colors.inner, Modifier.weight(1f),
            )
        }
        Text(
            stringResource(R.string.home_classified_caption),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun DisplayTime(label: String, value: String, accent: Color, modifier: Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Box(Modifier.size(7.dp).background(accent, CircleShape))
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        FigureText(value, figureStyle = MaterialTheme.typography.headlineMedium,
            unitStyle = MaterialTheme.typography.labelLarge, shrinkToFit = true)
    }
}

@Composable
private fun AppEntry(state: MainUiState, onClick: () -> Unit) {
    val summary = state.periodSummary
    val apps = rankAppsForHomePreview(summary?.apps.orEmpty())
    val preview = appUsagePreview(summary?.apps.orEmpty())
    EntrySurface(
        title = stringResource(R.string.home_apps_short),
        description = stringResource(R.string.content_desc_home_app_usage_link) + " " + preview,
        tag = HOME_APP_USAGE_LINK_TAG,
        enabled = !state.isAnalysisLoading,
        onClick = onClick,
        color = MaterialTheme.colorScheme.surfaceContainer,
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            apps.forEach { ApplicationIcon(it.packageName, it.label, 30.dp) }
        }
        Text(
            if (apps.isEmpty()) stringResource(R.string.home_apps_empty_short) else apps.joinToString(" · ") { it.label },
            style = MaterialTheme.typography.bodySmall,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun SessionEntry(state: MainUiState, onClick: () -> Unit) {
    val resources = LocalResources.current
    val summary = state.innerSessionSummary
    val count = state.periodSummary?.openedCount ?: 0
    EntrySurface(
        title = stringResource(R.string.home_sessions_short),
        description = stringResource(R.string.content_desc_home_inner_sessions_link) + " " + innerSessionsPreview(summary),
        tag = HOME_INNER_SESSIONS_LINK_TAG,
        enabled = !state.isAnalysisLoading,
        onClick = onClick,
        color = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary,
    ) {
        FigureText(
            resources.getString(R.string.value_open_count, count),
            figureStyle = MaterialTheme.typography.headlineLarge,
            unitStyle = MaterialTheme.typography.labelMedium,
        )
        Text(
            summary?.medianInnerActiveMillis?.let {
                stringResource(R.string.home_median_short, it.toDurationText(resources))
            } ?: stringResource(R.string.home_sessions_empty_short),
            style = MaterialTheme.typography.bodySmall,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun EntrySurface(
    title: String,
    description: String,
    tag: String,
    enabled: Boolean,
    onClick: () -> Unit,
    color: Color,
    contentColor: Color,
    content: @Composable ColumnScope.() -> Unit,
) {
    val unavailable = stringResource(R.string.detail_unavailable_while_analyzing)
    Surface(
        onClick = onClick,
        enabled = enabled,
        color = color,
        contentColor = contentColor,
        shape = RoundedCornerShape(22.dp),
        modifier = Modifier.fillMaxWidth().heightIn(min = 142.dp).testTag(tag).alpha(if (enabled) 1f else 0.55f)
            .clearAndSetSemantics {
                contentDescription = description + if (enabled) "" else " " + unavailable
                role = Role.Button
                if (!enabled) disabled()
            },
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                Chevron(contentColor)
            }
            content()
        }
    }
}

@Composable
private fun InnerAppsPreview(state: MainUiState, onClick: () -> Unit) {
    val resources = LocalResources.current
    val apps = rankAppsForDisplay(state.periodSummary?.apps.orEmpty(), AppRankingBasis.INNER).take(4)
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Column {
            Text(stringResource(R.string.home_inner_apps), style = MaterialTheme.typography.titleLarge)
            Text(stringResource(R.string.home_inner_apps_caption), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        apps.forEach { ranked ->
            val app = ranked.app
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ApplicationIcon(app.packageName, app.label, 36.dp)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(app.label, Modifier.weight(1f), style = MaterialTheme.typography.titleSmall, maxLines = 1)
                        Text(app.innerMillis.toDurationText(resources), style = MaterialTheme.typography.labelLarge)
                    }
                    AppRankingBar(ranked.rankingMillis, apps.first().rankingMillis, postureColors().inner)
                }
            }
        }
        AppEntry(state, onClick)
    }
}

@Composable
private fun RecordingDetails(state: MainUiState) {
    val resources = LocalResources.current
    val coverage = state.periodSummary?.takeIf { it.observedMillis > 0 }?.let {
        resources.getString(R.string.value_percent_0, it.dataCoverageRatio * 100)
    } ?: stringResource(R.string.label_no_data)
    val title = if ((state.periodSummary?.observedMillis ?: 0L) > 0L) {
        stringResource(R.string.home_record_quality, coverage)
    } else {
        stringResource(R.string.home_record_details)
    }
    DetailDisclosure(title, tag = RECORD_DETAILS_TAG) {
        state.periodSummary?.let {
            InfoLine(stringResource(R.string.label_classified_time), it.classifiedMillis.toDurationText(resources))
            InfoLine(stringResource(R.string.label_data_coverage), coverage)
            Text(stringResource(R.string.display_classification_rate_note), style = MaterialTheme.typography.bodySmall)
        }
        Text(state.recordRangeText(resources), style = MaterialTheme.typography.bodySmall)
        Text(state.lastSuccessfulSyncMillis?.let { stringResource(R.string.last_updated, it.toTimeText(resources)) }
            ?: stringResource(R.string.never_synced), style = MaterialTheme.typography.bodySmall)
        LiveStateCard(state)
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
