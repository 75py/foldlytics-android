package com.nagopy.android.foldlytics.ui

import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.nagopy.android.foldlytics.MainUiState
import com.nagopy.android.foldlytics.R
import com.nagopy.android.foldlytics.labelRes
import com.nagopy.android.foldlytics.model.AnalysisPeriod
import com.nagopy.android.foldlytics.model.PeriodUsageSummary
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

internal const val ANALYSIS_PROGRESS_DELAY_MILLIS = 400L

private enum class ScreenDestination(val titleRes: Int) {
    HOME(R.string.app_name),
    DIAGNOSTICS(R.string.screen_diagnostics),
    CALIBRATION(R.string.screen_calibration),
    APP_USAGE(R.string.app_usage_screen_title),
    INNER_SESSIONS(R.string.inner_sessions_screen_title),
}

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
    onShareSummary: suspend (Bitmap) -> Boolean = { false },
    appName: String? = null,
    screenshotSectionEndSpacing: Dp = 0.dp,
    screenshotHomeItemIndex: Int? = null,
) {
    val analysisProgressDescription = stringResource(R.string.content_desc_analysis_progress)
    val resolvedAppName = appName ?: stringResource(R.string.app_name)
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    var destination by rememberSaveable { mutableStateOf(ScreenDestination.HOME) }
    var showUsageAccessDisclosure by rememberSaveable { mutableStateOf(false) }
    var showAnalysisProgress by remember { mutableStateOf(false) }
    var summaryShareSnapshot by remember { mutableStateOf<PeriodUsageSummary?>(null) }
    val homeListState = rememberSaveable(saver = LazyListState.Saver) {
        LazyListState()
    }

    LaunchedEffect(state.isAnalysisLoading) {
        if (state.isAnalysisLoading) {
            delay(ANALYSIS_PROGRESS_DELAY_MILLIS)
            showAnalysisProgress = true
        } else {
            showAnalysisProgress = false
        }
    }
    LaunchedEffect(destination, screenshotHomeItemIndex) {
        if (destination == ScreenDestination.HOME && screenshotHomeItemIndex != null) {
            homeListState.scrollToItem(screenshotHomeItemIndex)
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
                            if (destination.isDetail()) {
                                DetailBackButton { destination = ScreenDestination.HOME }
                            } else {
                                MenuButton(onClick = { scope.launch { drawerState.open() } })
                            }
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
                ScreenDestination.HOME -> HomeScreen(
                    state = state,
                    scaffoldPadding = scaffoldPadding,
                    listState = homeListState,
                    onRequestUsageAccess = { showUsageAccessDisclosure = true },
                    onPeriodChanged = onPeriodChanged,
                    onCustomPeriodChanged = onCustomPeriodChanged,
                    onRefresh = onRefresh,
                    onShareSummaryRequested = { summaryShareSnapshot = it },
                    onOpenAppUsage = { destination = ScreenDestination.APP_USAGE },
                    onOpenInnerSessions = { destination = ScreenDestination.INNER_SESSIONS },
                    screenshotSectionEndSpacing = screenshotSectionEndSpacing,
                )

                ScreenDestination.APP_USAGE -> AppUsageScreen(
                    state = state,
                    scaffoldPadding = scaffoldPadding,
                )

                ScreenDestination.INNER_SESSIONS -> InnerDisplaySessionScreen(
                    state = state,
                    scaffoldPadding = scaffoldPadding,
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

    summaryShareSnapshot?.let { snapshot ->
        SummarySharePreviewDialog(
            summary = snapshot,
            canShare = !state.isLoading && !state.isAnalysisLoading,
            onDismiss = { summaryShareSnapshot = null },
            onShare = onShareSummary,
        )
    }
}

private fun ScreenDestination.isDetail(): Boolean = when (this) {
    ScreenDestination.APP_USAGE, ScreenDestination.INNER_SESSIONS -> true
    else -> false
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
private fun CalibrationContent(
    state: MainUiState,
    scaffoldPadding: PaddingValues,
    onSaveCover: () -> Unit,
    onSaveInner: () -> Unit,
    onClearCalibration: () -> Unit,
) {
    FoldlyticsLazyColumn(scaffoldPadding = scaffoldPadding) {
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
        text = { Text(stringResource(R.string.usage_access_disclosure_body)) },
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
                .background(
                    if (isRegistered) color else MaterialTheme.colorScheme.outline,
                    androidx.compose.foundation.shape.CircleShape,
                ),
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
