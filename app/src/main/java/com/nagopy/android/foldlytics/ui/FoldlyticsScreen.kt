package com.nagopy.android.foldlytics.ui

import android.content.res.Resources
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import com.nagopy.android.foldlytics.MainUiState
import com.nagopy.android.foldlytics.R
import com.nagopy.android.foldlytics.labelRes
import com.nagopy.android.foldlytics.model.AnalysisPeriod
import com.nagopy.android.foldlytics.model.AppUsage
import com.nagopy.android.foldlytics.model.DisplayPosture
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal const val ANALYSIS_PROGRESS_DELAY_MILLIS = 400L
internal const val CUSTOM_PERIOD_DIALOG_TITLE_TAG = "custom_period_dialog_title"
internal const val CUSTOM_PERIOD_DIALOG_GUIDANCE_TAG = "custom_period_dialog_guidance"
internal const val CUSTOM_PERIOD_DIALOG_CANCEL_TAG = "custom_period_dialog_cancel"
internal const val CUSTOM_PERIOD_DIALOG_APPLY_TAG = "custom_period_dialog_apply"
private val MaxContentWidth = 720.dp

private enum class ScreenDestination(val titleRes: Int) {
    HOME(R.string.app_name),
    DIAGNOSTICS(R.string.screen_diagnostics),
    CALIBRATION(R.string.screen_calibration),
}

internal enum class AppRankingBasis(
    val labelRes: Int,
    val counterpartLabelRes: Int,
    val selectorDescriptionRes: Int,
) {
    COVER(
        labelRes = R.string.posture_cover,
        counterpartLabelRes = R.string.posture_inner,
        selectorDescriptionRes = R.string.content_desc_cover_app_ranking,
    ),
    INNER(
        labelRes = R.string.posture_inner,
        counterpartLabelRes = R.string.posture_cover,
        selectorDescriptionRes = R.string.content_desc_inner_app_ranking,
    ),
    ;

    fun selectedMillis(app: AppUsage): Long = when (this) {
        COVER -> app.coverMillis
        INNER -> app.innerMillis
    }

    fun counterpartMillis(app: AppUsage): Long = when (this) {
        COVER -> app.innerMillis
        INNER -> app.coverMillis
    }
}

internal fun rankAppsForDisplay(
    apps: List<AppUsage>,
    basis: AppRankingBasis,
): List<AppUsage> = apps
    .asSequence()
    .filter { it.isLauncherApp && basis.selectedMillis(it) > 0L }
    .sortedWith(
        compareByDescending<AppUsage> { basis.selectedMillis(it) }
            .thenByDescending { basis.counterpartMillis(it) }
            .thenBy { it.label },
    )
    .toList()

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FoldlyticsScreen(
    state: MainUiState,
    onOpenUsageAccess: () -> Unit,
    onSaveCover: () -> Unit,
    onSaveInner: () -> Unit,
    onClearCalibration: () -> Unit,
    onPeriodChanged: (AnalysisPeriod) -> Unit,
    onCustomPeriodChanged: (Long, Long) -> Unit,
    onRefresh: () -> Unit,
    onShare: () -> Unit,
    onExportCsv: () -> Unit,
    onOpenPrivacyPolicy: () -> Unit,
    onOpenOssLicenses: () -> Unit,
    appName: String? = null,
    screenshotSectionEndSpacing: Dp = 0.dp,
) {
    val analysisProgressDescription = stringResource(R.string.content_desc_analysis_progress)
    val resolvedAppName = appName ?: stringResource(R.string.app_name)
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    var destination by rememberSaveable { mutableStateOf(ScreenDestination.HOME) }
    var showUsageAccessDisclosure by rememberSaveable { mutableStateOf(false) }
    var showAnalysisProgress by remember { mutableStateOf(false) }

    LaunchedEffect(state.isAnalysisLoading) {
        if (state.isAnalysisLoading) {
            delay(ANALYSIS_PROGRESS_DELAY_MILLIS)
            showAnalysisProgress = true
        } else {
            showAnalysisProgress = false
        }
    }
    BackHandler(enabled = destination != ScreenDestination.HOME) {
        destination = ScreenDestination.HOME
    }

    fun selectDestination(next: ScreenDestination) {
        destination = next
        scope.launch { drawerState.close() }
    }

    fun closeDrawerThen(action: () -> Unit) {
        scope.launch {
            drawerState.close()
            action()
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            FoldlyticsDrawer(
                state = state,
                destination = destination,
                appName = resolvedAppName,
                onHome = { selectDestination(ScreenDestination.HOME) },
                onDiagnostics = { selectDestination(ScreenDestination.DIAGNOSTICS) },
                onCalibration = { selectDestination(ScreenDestination.CALIBRATION) },
                onExportCsv = { closeDrawerThen(onExportCsv) },
                onShare = { closeDrawerThen(onShare) },
                onUsageAccess = {
                    closeDrawerThen { showUsageAccessDisclosure = true }
                },
                onPrivacyPolicy = { closeDrawerThen(onOpenPrivacyPolicy) },
                onOssLicenses = { closeDrawerThen(onOpenOssLicenses) },
            )
        },
    ) {
        Scaffold(
            topBar = {
                Column {
                    TopAppBar(
                        title = {
                            Text(
                                if (destination == ScreenDestination.HOME) {
                                    resolvedAppName
                                } else {
                                    stringResource(destination.titleRes)
                                },
                            )
                        },
                        navigationIcon = {
                            MenuButton(onClick = { scope.launch { drawerState.open() } })
                        },
                    )
                    if (showAnalysisProgress) {
                        LinearProgressIndicator(
                            modifier = Modifier
                                .fillMaxWidth()
                                .semantics {
                                    contentDescription = analysisProgressDescription
                                },
                        )
                    }
                }
            },
        ) { scaffoldPadding ->
            when (destination) {
                ScreenDestination.HOME -> HomeContent(
                    state = state,
                    scaffoldPadding = scaffoldPadding,
                    onRequestUsageAccess = { showUsageAccessDisclosure = true },
                    onPeriodChanged = onPeriodChanged,
                    onCustomPeriodChanged = onCustomPeriodChanged,
                    onRefresh = onRefresh,
                    screenshotSectionEndSpacing = screenshotSectionEndSpacing,
                )

                ScreenDestination.DIAGNOSTICS -> DiagnosticsContent(
                    state = state,
                    scaffoldPadding = scaffoldPadding,
                )

                ScreenDestination.CALIBRATION -> CalibrationContent(
                    state = state,
                    scaffoldPadding = scaffoldPadding,
                    onSaveCover = onSaveCover,
                    onSaveInner = onSaveInner,
                    onClearCalibration = onClearCalibration,
                )
            }
        }
    }

    if (showUsageAccessDisclosure) {
        UsageAccessDisclosureDialog(
            hasAccess = state.hasUsageAccess,
            onDismiss = { showUsageAccessDisclosure = false },
            onContinue = {
                showUsageAccessDisclosure = false
                onOpenUsageAccess()
            },
        )
    }
}

@Composable
private fun FoldlyticsDrawer(
    state: MainUiState,
    destination: ScreenDestination,
    appName: String,
    onHome: () -> Unit,
    onDiagnostics: () -> Unit,
    onCalibration: () -> Unit,
    onExportCsv: () -> Unit,
    onShare: () -> Unit,
    onUsageAccess: () -> Unit,
    onPrivacyPolicy: () -> Unit,
    onOssLicenses: () -> Unit,
) {
    val canExportCsv = state.periodSummary != null && !state.isAnalysisLoading
    ModalDrawerSheet {
        Text(
            appName,
            modifier = Modifier.padding(start = 28.dp, top = 28.dp, end = 28.dp, bottom = 16.dp),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
        )
        NavigationDrawerItem(
            label = { Text(stringResource(R.string.nav_home)) },
            selected = destination == ScreenDestination.HOME,
            onClick = onHome,
            modifier = Modifier.padding(horizontal = 12.dp),
        )
        NavigationDrawerItem(
            label = { Text(stringResource(R.string.screen_diagnostics)) },
            selected = destination == ScreenDestination.DIAGNOSTICS,
            onClick = onDiagnostics,
            modifier = Modifier.padding(horizontal = 12.dp),
        )
        NavigationDrawerItem(
            label = { Text(stringResource(R.string.screen_calibration)) },
            selected = destination == ScreenDestination.CALIBRATION,
            onClick = onCalibration,
            modifier = Modifier.padding(horizontal = 12.dp),
        )
        HorizontalDivider(Modifier.padding(horizontal = 28.dp, vertical = 12.dp))
        Text(
            stringResource(R.string.drawer_section_data),
            modifier = Modifier.padding(horizontal = 28.dp, vertical = 8.dp),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        NavigationDrawerItem(
            label = { Text(stringResource(R.string.action_save_all_csv)) },
            selected = false,
            onClick = { if (canExportCsv) onExportCsv() },
            modifier = Modifier
                .padding(horizontal = 12.dp)
                .alpha(if (canExportCsv) 1f else 0.38f)
                .semantics {
                    if (!canExportCsv) disabled()
                },
        )
        NavigationDrawerItem(
            label = { Text(stringResource(R.string.action_share_diagnostic_report)) },
            selected = false,
            onClick = onShare,
            modifier = Modifier.padding(horizontal = 12.dp),
        )
        HorizontalDivider(Modifier.padding(horizontal = 28.dp, vertical = 12.dp))
        NavigationDrawerItem(
            label = { Text(stringResource(R.string.action_usage_access_settings)) },
            selected = false,
            onClick = onUsageAccess,
            modifier = Modifier.padding(horizontal = 12.dp),
        )
        NavigationDrawerItem(
            label = { Text(stringResource(R.string.action_privacy_policy)) },
            selected = false,
            onClick = onPrivacyPolicy,
            modifier = Modifier.padding(horizontal = 12.dp),
        )
        NavigationDrawerItem(
            label = { Text(stringResource(R.string.action_open_source_licenses)) },
            selected = false,
            onClick = onOssLicenses,
            modifier = Modifier.padding(horizontal = 12.dp),
        )
        Spacer(Modifier.weight(1f))
        Text(
            stringResource(R.string.drawer_privacy_note),
            modifier = Modifier.padding(28.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun MenuButton(onClick: () -> Unit) {
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
internal fun FoldlyticsLazyColumn(
    scaffoldPadding: PaddingValues,
    content: LazyListScope.() -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(scaffoldPadding),
        contentAlignment = Alignment.TopCenter,
    ) {
        LazyColumn(
            modifier = Modifier
                .widthIn(max = MaxContentWidth)
                .fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            content = content,
        )
    }
}

@Composable
private fun HomeContent(
    state: MainUiState,
    scaffoldPadding: PaddingValues,
    onRequestUsageAccess: () -> Unit,
    onPeriodChanged: (AnalysisPeriod) -> Unit,
    onCustomPeriodChanged: (Long, Long) -> Unit,
    onRefresh: () -> Unit,
    screenshotSectionEndSpacing: Dp,
) {
    var appRankingBasis by rememberSaveable { mutableStateOf(AppRankingBasis.COVER) }
    FoldlyticsLazyColumn(scaffoldPadding) {
        if (!state.hasUsageAccess) {
            item { PermissionCard(onOpenSettings = onRequestUsageAccess) }
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
            item {
                Column {
                    SummaryCard(
                        summary = summary,
                        longTermInsights = state.longTermInsights,
                    )
                    if (screenshotSectionEndSpacing > 0.dp) {
                        Spacer(Modifier.height(screenshotSectionEndSpacing))
                    }
                }
            }
            if (summary.period.showsTrends) {
                state.longTermInsights?.let { insights ->
                    item { SectionTitle(stringResource(R.string.section_usage_trends)) }
                    item { InnerRatioTrendCard(insights) }
                    item {
                        Column {
                            OpenCountTrendCard(insights)
                            if (screenshotSectionEndSpacing > 0.dp) {
                                Spacer(Modifier.height(screenshotSectionEndSpacing))
                            }
                        }
                    }
                }
            }
            val rankedApps = rankAppsForDisplay(summary.apps, appRankingBasis)
            val maximumMillis = rankedApps.firstOrNull()?.let(appRankingBasis::selectedMillis) ?: 0L
            item {
                AppSectionHeader(
                    period = summary.period,
                    selectedBasis = appRankingBasis,
                    onBasisSelected = { appRankingBasis = it },
                )
            }
            if (rankedApps.isNotEmpty()) {
                itemsIndexed(rankedApps, key = { _, app -> app.packageName }) { index, app ->
                    AppUsageCard(
                        app = app,
                        rank = index + 1,
                        basis = appRankingBasis,
                        maximumMillis = maximumMillis,
                    )
                }
            } else {
                item {
                    HintCard(
                        stringResource(
                            R.string.no_ranked_apps,
                            stringResource(appRankingBasis.labelRes),
                        ),
                    )
                }
            }
        }
        if (!state.hasUsageAccess && state.periodSummary != null) {
            item {
                HintCard(stringResource(R.string.saved_data_usage_access_note))
            }
        }
        state.errorMessage?.let { message ->
            item {
                HintCard(stringResource(R.string.error_sync_read, message), isError = true)
            }
        }
    }
}

@Composable
private fun CalibrationContent(
    state: MainUiState,
    scaffoldPadding: PaddingValues,
    onSaveCover: () -> Unit,
    onSaveInner: () -> Unit,
    onClearCalibration: () -> Unit,
) {
    FoldlyticsLazyColumn(scaffoldPadding) {
        item {
            LabCard(title = stringResource(R.string.calibration_intro_title)) {
                Text(
                    stringResource(R.string.calibration_intro_body),
                    style = MaterialTheme.typography.bodyMedium,
                )
                InfoLine(
                    stringResource(R.string.label_current_posture),
                    stringResource(state.currentPosture.labelRes),
                )
                InfoLine(
                    stringResource(R.string.label_classification_method),
                    stringResource(
                        if (state.calibration.isComplete) {
                            R.string.classification_saved_values
                        } else {
                            R.string.classification_automatic
                        },
                    ),
                )
                Text(
                    stringResource(R.string.calibration_edge_case_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        item {
            CalibrationCard(
                state = state,
                onSaveCover = onSaveCover,
                onSaveInner = onSaveInner,
                onClear = onClearCalibration,
            )
        }
    }
}

@Composable
private fun UsageAccessDisclosureDialog(
    hasAccess: Boolean,
    onDismiss: () -> Unit,
    onContinue: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.usage_access_disclosure_title)) },
        text = {
            Text(stringResource(R.string.usage_access_disclosure_body))
        },
        confirmButton = {
            TextButton(onClick = onContinue) {
                Text(
                    stringResource(
                        if (hasAccess) {
                            R.string.action_open_settings
                        } else {
                            R.string.action_agree_and_open_settings
                        },
                    ),
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_not_now)) }
        },
    )
}

@Composable
private fun PermissionCard(onOpenSettings: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
        ),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                stringResource(R.string.usage_access_required_title),
                fontWeight = FontWeight.Bold,
            )
            Text(
                stringResource(R.string.usage_access_required_body),
                style = MaterialTheme.typography.bodySmall,
            )
            Button(onClick = onOpenSettings, modifier = Modifier.align(Alignment.End)) {
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
    val stateText = when (state.currentPosture) {
        DisplayPosture.COVER -> stringResource(R.string.live_state_cover)
        DisplayPosture.INNER -> stringResource(R.string.live_state_inner)
        DisplayPosture.UNKNOWN -> stringResource(R.string.live_state_unknown)
    }
    val classificationMethod = if (state.calibration.isComplete) {
        stringResource(R.string.classification_saved_values_detail)
    } else {
        stringResource(R.string.classification_automatic_detail)
    }
    LabCard(title = stringResource(R.string.live_state_title)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                Modifier
                    .size(14.dp)
                    .background(postureColor, CircleShape),
            )
            Column {
                Text(
                    stringResource(state.currentPosture.labelRes),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    stateText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    classificationMethod,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun CalibrationCard(
    state: MainUiState,
    onSaveCover: () -> Unit,
    onSaveInner: () -> Unit,
    onClear: () -> Unit,
) {
    val colors = postureColors()
    LabCard(title = stringResource(R.string.calibration_values_title)) {
        Text(
            stringResource(R.string.calibration_instructions),
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth()) {
            CalibrationStatus(
                label = stringResource(R.string.posture_cover),
                isRegistered = state.calibration.cover != null,
                color = colors.cover,
                modifier = Modifier.weight(1f),
            )
            CalibrationStatus(
                label = stringResource(R.string.posture_inner),
                isRegistered = state.calibration.inner != null,
                color = colors.inner,
                modifier = Modifier.weight(1f),
            )
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onSaveCover, modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.action_record_cover))
            }
            OutlinedButton(onClick = onSaveInner, modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.action_record_inner))
            }
        }
        if (state.calibration.cover != null || state.calibration.inner != null) {
            TextButton(onClick = onClear, modifier = Modifier.align(Alignment.End)) {
                Text(stringResource(R.string.action_clear_calibration))
            }
        }
    }
}

@Composable
private fun CalibrationStatus(
    label: String,
    isRegistered: Boolean,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Box(
            Modifier
                .size(9.dp)
                .background(if (isRegistered) color else MaterialTheme.colorScheme.outline, CircleShape),
        )
        Column {
            Text(label, fontWeight = FontWeight.SemiBold)
            Text(
                stringResource(
                    if (isRegistered) R.string.status_registered else R.string.status_not_registered,
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ResultHeader(
    state: MainUiState,
    onPeriodChanged: (AnalysisPeriod) -> Unit,
    onCustomPeriodChanged: (Long, Long) -> Unit,
    onRefresh: () -> Unit,
) {
    val resources = LocalResources.current
    var showCustomPeriodDialog by rememberSaveable { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SectionTitle(stringResource(R.string.section_analysis_period))
            AssistChip(
                onClick = onRefresh,
                label = {
                    Text(
                        stringResource(
                            if (state.isLoading) R.string.action_syncing else R.string.action_refresh,
                        ),
                    )
                },
                enabled = state.hasUsageAccess && !state.isLoading,
            )
        }
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            AnalysisPeriod.entries.forEach { period ->
                FilterChip(
                    selected = state.selectedPeriod == period,
                    onClick = {
                        if (period == AnalysisPeriod.CUSTOM) {
                            showCustomPeriodDialog = true
                        } else {
                            onPeriodChanged(period)
                        }
                    },
                    label = { Text(stringResource(period.labelRes)) },
                    enabled = period in state.availablePeriods,
                )
            }
        }
        Text(
            state.recordRangeText(resources),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (
            state.selectedPeriod == AnalysisPeriod.CUSTOM &&
            state.customRangeStartMillis != null &&
            state.customRangeEndMillis != null
        ) {
            Text(
                stringResource(
                    R.string.selected_date_range,
                    state.customRangeStartMillis.toShortDateText(resources),
                    (state.customRangeEndMillis - 1L).toShortDateText(resources),
                ),
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium,
            )
        }
        Text(
            state.lastSuccessfulSyncMillis?.let {
                stringResource(R.string.last_updated, it.toTimeText(resources))
            } ?: stringResource(R.string.never_synced),
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
    val pickerState = rememberDateRangePickerState(
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
        Column(modifier = Modifier.fillMaxSize()) {
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

private fun Long.toLocalDate(zoneId: ZoneId): LocalDate =
    Instant.ofEpochMilli(this).atZone(zoneId).toLocalDate()

private fun LocalDate.toDatePickerMillis(): Long =
    atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

private fun Long.toDatePickerLocalDate(): LocalDate =
    Instant.ofEpochMilli(this).atZone(ZoneOffset.UTC).toLocalDate()

@Composable
private fun SummaryCard(
    summary: PeriodUsageSummary,
    longTermInsights: LongTermInsights?,
) {
    val resources = LocalResources.current
    val colors = postureColors()
    val percent = resources.getString(R.string.value_percent_0, summary.innerRatio * 100)
    LabCard(title = stringResource(R.string.summary_title)) {
        if (longTermInsights != null) {
            InfoLine(
                stringResource(R.string.label_analysis_range),
                stringResource(
                    R.string.date_range,
                    summary.rangeStartMillis.toShortDateText(resources),
                    (summary.rangeEndMillis - 1L)
                        .coerceAtLeast(0L)
                        .toShortDateText(resources),
                ),
            )
        }
        PostureDonutWithLegend(
            segments = listOf(
                DonutSegment(
                    stringResource(R.string.posture_cover),
                    summary.coverMillis,
                    colors.cover,
                ),
                DonutSegment(
                    stringResource(R.string.posture_inner),
                    summary.innerMillis,
                    colors.inner,
                ),
            ),
            centerLabel = stringResource(R.string.posture_inner),
            centerValue = if (summary.classifiedMillis > 0L) percent else "—",
            description = stringResource(
                R.string.content_desc_summary,
                stringResource(summary.period.labelRes),
                summary.coverMillis.toDurationText(resources),
                summary.innerMillis.toDurationText(resources),
                percent,
            ),
            colors = colors,
            size = 164.dp,
        )
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth()) {
            Metric(
                stringResource(R.string.posture_cover),
                summary.coverMillis.toDurationText(resources),
                colors.cover,
                Modifier.weight(1f),
            )
            Metric(
                stringResource(R.string.posture_inner),
                summary.innerMillis.toDurationText(resources),
                colors.inner,
                Modifier.weight(1f),
            )
        }
        HorizontalDivider(Modifier.padding(vertical = 10.dp))
        InfoLine(
            stringResource(R.string.label_classified_time),
            summary.classifiedMillis.toDurationText(resources),
        )
        InfoLine(
            stringResource(R.string.label_data_coverage),
            resources.getString(R.string.value_percent_0, summary.dataCoverageRatio * 100),
        )
        Row(Modifier.fillMaxWidth()) {
            Metric(
                stringResource(R.string.label_opened),
                resources.getString(R.string.value_open_count, summary.openedCount),
                colors.inner,
                Modifier.weight(1f),
            )
            Metric(
                stringResource(R.string.label_closed),
                resources.getString(R.string.value_open_count, summary.closedCount),
                colors.cover,
                Modifier.weight(1f),
            )
        }
        longTermInsights?.let { insights ->
            InfoLine(
                stringResource(R.string.label_opened_per_observed_day),
                resources.getString(
                    R.string.value_average_open_count,
                    insights.averageOpenedPerObservedDay,
                ),
            )
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
            insights.thirtyDayInnerRatioDelta?.let { delta ->
                InfoLine(
                    stringResource(R.string.label_change_from_first_30_days),
                    resources.getString(R.string.value_points, delta * 100),
                )
            }
        }
    }
}

@Composable
private fun InnerRatioTrendCard(insights: LongTermInsights) {
    val colors = postureColors()
    LabCard(title = stringResource(R.string.inner_ratio_trend_title)) {
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            LegendDot(colors.inner, stringResource(R.string.legend_inner_ratio))
            LegendDot(colors.unknown, stringResource(R.string.label_no_data))
        }
        Spacer(Modifier.height(8.dp))
        InnerRatioTrendChart(insights.buckets, colors)
    }
}

@Composable
private fun OpenCountTrendCard(insights: LongTermInsights) {
    val resources = LocalResources.current
    val colors = postureColors()
    LabCard(title = stringResource(R.string.open_count_trend_title)) {
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            LegendDot(colors.inner, stringResource(R.string.legend_open_count))
            LegendDot(colors.unknown, stringResource(R.string.label_no_data))
        }
        Spacer(Modifier.height(8.dp))
        OpenCountTrendChart(insights.buckets, colors)
        InfoLine(
            stringResource(R.string.label_period_total),
            resources.getString(R.string.value_open_count, insights.openedCount),
        )
    }
}

@Composable
private fun PostureDonutWithLegend(
    segments: List<DonutSegment>,
    centerLabel: String,
    centerValue: String,
    description: String,
    colors: PostureColors,
    size: Dp,
) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        if (maxWidth >= 520.dp) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                DonutChart(
                    segments = segments,
                    centerLabel = centerLabel,
                    centerValue = centerValue,
                    description = description,
                    size = size,
                )
                Spacer(Modifier.width(32.dp))
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    LegendDot(colors.cover, stringResource(R.string.posture_cover))
                    LegendDot(colors.inner, stringResource(R.string.posture_inner))
                    LegendDot(colors.unknown, stringResource(R.string.label_no_data))
                }
            }
        } else {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                DonutChart(
                    segments = segments,
                    centerLabel = centerLabel,
                    centerValue = centerValue,
                    description = description,
                    size = size,
                )
                Spacer(Modifier.height(20.dp))
                PostureLegend(colors)
            }
        }
    }
}

@Composable
private fun AppSectionHeader(
    period: AnalysisPeriod,
    selectedBasis: AppRankingBasis,
    onBasisSelected: (AppRankingBasis) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        SectionTitle(stringResource(R.string.app_ranking_title))
        Text(
            stringResource(R.string.selected_period, stringResource(period.labelRes)),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            stringResource(R.string.app_ranking_basis),
            style = MaterialTheme.typography.labelLarge,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AppRankingBasis.entries.forEach { basis ->
                val selectorDescription = stringResource(basis.selectorDescriptionRes)
                FilterChip(
                    selected = selectedBasis == basis,
                    onClick = { onBasisSelected(basis) },
                    label = { Text(stringResource(basis.labelRes)) },
                    modifier = Modifier.semantics {
                        contentDescription = selectorDescription
                    },
                )
            }
        }
        Text(
            stringResource(
                R.string.app_ranking_order,
                stringResource(selectedBasis.labelRes),
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun AppUsageCard(
    app: AppUsage,
    rank: Int,
    basis: AppRankingBasis,
    maximumMillis: Long,
) {
    val resources = LocalResources.current
    val colors = postureColors()
    val selectedMillis = basis.selectedMillis(app)
    val counterpartMillis = basis.counterpartMillis(app)
    val selectedColor = when (basis) {
        AppRankingBasis.COVER -> colors.cover
        AppRankingBasis.INNER -> colors.inner
    }
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    resources.getString(R.string.value_rank, rank),
                    modifier = Modifier.width(32.dp),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = selectedColor,
                )
                ApplicationIcon(app.packageName, app.label)
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        app.label,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        stringResource(
                            R.string.app_usage_selected,
                            stringResource(basis.labelRes),
                            selectedMillis.toDurationText(resources),
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = selectedColor,
                    )
                }
            }
            AppRankingBar(
                value = selectedMillis,
                maximum = maximumMillis,
                color = selectedColor,
            )
            Text(
                stringResource(
                    R.string.app_usage_counterpart,
                    stringResource(basis.counterpartLabelRes),
                    counterpartMillis.toDurationText(resources),
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ApplicationIcon(packageName: String, label: String) {
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
                .background(MaterialTheme.colorScheme.secondaryContainer, RoundedCornerShape(11.dp)),
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
    color: Color,
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
        )
    }
    Spacer(Modifier.height(4.dp))
}

@Composable
internal fun LabCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
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
