package com.nagopy.android.foldlytics.ui

import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.nagopy.android.foldlytics.MainUiError
import com.nagopy.android.foldlytics.MainUiErrorKind
import com.nagopy.android.foldlytics.MainUiState
import com.nagopy.android.foldlytics.R
import com.nagopy.android.foldlytics.labelRes
import com.nagopy.android.foldlytics.model.AnalysisPeriod
import com.nagopy.android.foldlytics.model.CalibrationValidationFailure
import com.nagopy.android.foldlytics.model.DisplayPosture
import com.nagopy.android.foldlytics.model.PeriodUsageSummary
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

internal const val ANALYSIS_PROGRESS_DELAY_MILLIS = 400L
internal const val MAIN_ERROR_BANNER_TAG = "main_error_banner"
internal const val MAIN_ERROR_RETRY_TAG = "main_error_retry"
internal const val MAIN_ERROR_DISMISS_TAG = "main_error_dismiss"
internal const val DRAWER_CONTENT_TAG = "drawer_content"
internal const val USAGE_ACCESS_DISCLOSURE_BODY_TAG = "usage_access_disclosure_body"

private enum class ScreenDestination(val titleRes: Int) {
    HOME(R.string.app_name),
    DIAGNOSTICS(R.string.screen_diagnostics),
    CALIBRATION(R.string.screen_calibration),
    APP_USAGE(R.string.app_usage_screen_title),
    INNER_SESSIONS(R.string.inner_sessions_screen_title),
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
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
    onDismissError: () -> Unit = {},
    onShareSummary: suspend (Bitmap) -> Boolean = { false },
    appName: String? = null,
    screenshotSectionEndSpacing: Dp = 0.dp,
    screenshotHomeItemIndex: Int? = null,
    homeNavigationRequest: Int = 0,
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

    LaunchedEffect(homeNavigationRequest) {
        if (homeNavigationRequest > 0) {
            destination = ScreenDestination.HOME
            showUsageAccessDisclosure = false
            summaryShareSnapshot = null
            drawerState.close()
            homeListState.scrollToItem(0)
        }
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
                isDrawerVisible = drawerState.isOpen ||
                    drawerState.targetValue == DrawerValue.Open,
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
                val windowWidth = with(LocalDensity.current) {
                    LocalWindowInfo.current.containerSize.width.toDp()
                }
                val contentWidth = if (
                    destination == ScreenDestination.HOME && windowWidth >= TwoPaneMinWidth
                ) {
                    TwoPaneContentWidth
                } else {
                    MaxContentWidth
                }
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.background),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    TopAppBar(
                        modifier = Modifier.widthIn(max = contentWidth),
                        title = {
                            if (destination == ScreenDestination.HOME) {
                                Text(
                                    resolvedAppName,
                                    style = MaterialTheme.typography.titleLarge,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            } else {
                                Text(
                                    stringResource(destination.titleRes),
                                    style = MaterialTheme.typography.titleLarge,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        },
                        actions = {
                            if (destination == ScreenDestination.HOME) {
                                RefreshButton(
                                    enabled = state.hasUsageAccess && !state.isLoading,
                                    refreshing = state.isLoading,
                                    onClick = onRefresh,
                                )
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.background,
                        ),
                        navigationIcon = {
                            if (destination.isDetail()) {
                                DetailBackButton { destination = ScreenDestination.HOME }
                            } else {
                                MenuButton(onClick = { scope.launch { drawerState.open() } })
                            }
                        },
                    )
                    if (showAnalysisProgress) {
                        LinearWavyProgressIndicator(
                            modifier = Modifier
                                .widthIn(max = contentWidth)
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp)
                                .semantics {
                                    contentDescription = analysisProgressDescription
                                },
                            color = MaterialTheme.colorScheme.tertiary,
                            trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                        )
                    }
                    state.error?.let { error ->
                        MainErrorBanner(
                            error = error,
                            canRetry =
                                state.hasUsageAccess &&
                                    !state.isLoading &&
                                    !state.isAnalysisLoading,
                            onRetry = onRefresh,
                            onDismiss = onDismissError,
                            modifier = Modifier
                                .widthIn(max = contentWidth)
                                .padding(
                                    start = 16.dp,
                                    end = 16.dp,
                                    bottom = 8.dp,
                                ),
                        )
                    }
                }
            },
            containerColor = MaterialTheme.colorScheme.background,
        ) { scaffoldPadding ->
            val spatial = MaterialTheme.motionScheme.defaultSpatialSpec<IntOffset>()
            val effects = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
            AnimatedContent(
                targetState = destination,
                transitionSpec = {
                    // Detail screens slide in from the hinge side; drawer destinations cross-fade.
                    val direction = when {
                        targetState.isDetail() -> 1
                        initialState.isDetail() -> -1
                        else -> 0
                    }
                    (slideInHorizontally(spatial) { it / 8 * direction } + fadeIn(effects))
                        .togetherWith(
                            slideOutHorizontally(spatial) { -it / 8 * direction } + fadeOut(effects),
                        )
                },
                label = "destination",
            ) { shownDestination ->
                when (shownDestination) {
                    ScreenDestination.HOME -> HomeScreen(
                        state = state,
                        scaffoldPadding = scaffoldPadding,
                        listState = homeListState,
                        onRequestUsageAccess = { showUsageAccessDisclosure = true },
                        onPeriodChanged = onPeriodChanged,
                        onCustomPeriodChanged = onCustomPeriodChanged,
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
private fun MainErrorBanner(
    error: MainUiError,
    canRetry: Boolean,
    onRetry: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val canRefresh = when (error.kind) {
        MainUiErrorKind.SYNC, MainUiErrorKind.ANALYSIS -> true
        MainUiErrorKind.CHECKPOINT -> false
    }
    val titleRes = when (error.kind) {
        MainUiErrorKind.SYNC -> R.string.sync_error_title
        MainUiErrorKind.ANALYSIS -> R.string.analysis_error_title
        MainUiErrorKind.CHECKPOINT -> R.string.checkpoint_error_title
    }
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag(MAIN_ERROR_BANNER_TAG)
            .semantics { liveRegion = LiveRegionMode.Polite },
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = CardPadding, top = 16.dp, end = 12.dp, bottom = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                stringResource(titleRes),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                error.message,
                style = MaterialTheme.typography.bodySmall,
            )
            Row(
                modifier = Modifier.align(Alignment.End),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (canRefresh) {
                    TextButton(
                        enabled = canRetry,
                        onClick = onRetry,
                        modifier = Modifier.testTag(MAIN_ERROR_RETRY_TAG),
                    ) {
                        Text(stringResource(R.string.action_refresh))
                    }
                }
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.testTag(MAIN_ERROR_DISMISS_TAG),
                ) {
                    Text(stringResource(R.string.action_dismiss))
                }
            }
        }
    }
}

@Composable
private fun FoldlyticsDrawer(
    state: MainUiState,
    destination: ScreenDestination,
    isDrawerVisible: Boolean,
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
    val itemColors = NavigationDrawerItemDefaults.colors(
        selectedContainerColor = MaterialTheme.colorScheme.primary,
        selectedTextColor = MaterialTheme.colorScheme.onPrimary,
        unselectedContainerColor = Color.Transparent,
    )
    ModalDrawerSheet(
        drawerShape = RoundedCornerShape(topEnd = 36.dp, bottomEnd = 36.dp),
        drawerContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        // Short windows (landscape or split screen) and large font scales make the drawer taller
        // than the sheet, so the items scroll while the privacy note keeps its bottom placement
        // whenever the content fits. The closed sheet stays composed, so scrolling is enabled only
        // while it is visible and never reaches accessibility services behind the current screen.
        BoxWithConstraints {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState(), enabled = isDrawerVisible)
                    .heightIn(min = maxHeight)
                    .fillMaxWidth()
                    .testTag(DRAWER_CONTENT_TAG),
            ) {
                Row(
                    modifier = Modifier.padding(
                        start = 28.dp,
                        top = 32.dp,
                        end = 28.dp,
                        bottom = 20.dp,
                    ),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    FoldGlyph(state.currentPosture, MaterialTheme.colorScheme.primary)
                    Text(
                        appName,
                        style = MaterialTheme.typography.headlineMedium,
                    )
                }
                NavigationDrawerItem(
                    label = { Text(stringResource(R.string.nav_home)) },
                    selected = destination == ScreenDestination.HOME,
                    onClick = onHome,
                    modifier = Modifier.padding(horizontal = 12.dp),
                    colors = itemColors,
                )
                NavigationDrawerItem(
                    label = { Text(stringResource(R.string.screen_diagnostics)) },
                    selected = destination == ScreenDestination.DIAGNOSTICS,
                    onClick = onDiagnostics,
                    modifier = Modifier.padding(horizontal = 12.dp),
                    colors = itemColors,
                )
                NavigationDrawerItem(
                    label = { Text(stringResource(R.string.screen_calibration)) },
                    selected = destination == ScreenDestination.CALIBRATION,
                    onClick = onCalibration,
                    modifier = Modifier.padding(horizontal = 12.dp),
                    colors = itemColors,
                )
                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 28.dp, vertical = 12.dp),
                    color = MaterialTheme.colorScheme.outlineVariant,
                )
                Text(
                    stringResource(R.string.drawer_section_data),
                    modifier = Modifier.padding(horizontal = 28.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.labelMedium,
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
                    colors = itemColors,
                )
                NavigationDrawerItem(
                    label = { Text(stringResource(R.string.action_share_diagnostic_report)) },
                    selected = false,
                    onClick = onShare,
                    modifier = Modifier.padding(horizontal = 12.dp),
                    colors = itemColors,
                )
                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 28.dp, vertical = 12.dp),
                    color = MaterialTheme.colorScheme.outlineVariant,
                )
                NavigationDrawerItem(
                    label = { Text(stringResource(R.string.action_usage_access_settings)) },
                    selected = false,
                    onClick = onUsageAccess,
                    modifier = Modifier.padding(horizontal = 12.dp),
                    colors = itemColors,
                )
                NavigationDrawerItem(
                    label = { Text(stringResource(R.string.action_privacy_policy)) },
                    selected = false,
                    onClick = onPrivacyPolicy,
                    modifier = Modifier.padding(horizontal = 12.dp),
                    colors = itemColors,
                )
                NavigationDrawerItem(
                    label = { Text(stringResource(R.string.action_open_source_licenses)) },
                    selected = false,
                    onClick = onOssLicenses,
                    modifier = Modifier.padding(horizontal = 12.dp),
                    colors = itemColors,
                )
                Spacer(Modifier.weight(1f))
                Surface(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 20.dp),
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                ) {
                    Text(
                        stringResource(R.string.drawer_privacy_note),
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
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
    FoldlyticsLazyColumn(scaffoldPadding = scaffoldPadding) {
        item {
            LabCard(title = stringResource(R.string.calibration_intro_title)) {
                Text(
                    stringResource(R.string.calibration_intro_body),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(8.dp))
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
internal fun UsageAccessDisclosureDialog(
    hasAccess: Boolean,
    onDismiss: () -> Unit,
    onContinue: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = modifier,
        title = { Text(stringResource(R.string.usage_access_disclosure_title)) },
        text = {
            val bodyParagraphs = stringResource(R.string.usage_access_disclosure_body)
                .split("\n\n")
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .testTag(USAGE_ACCESS_DISCLOSURE_BODY_TAG),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                bodyParagraphs.forEach { paragraph -> Text(paragraph) }
            }
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
private fun CalibrationCard(
    state: MainUiState,
    onSaveCover: () -> Unit,
    onSaveInner: () -> Unit,
    onClear: () -> Unit,
) {
    val colors = postureColors()
    val palette = foldlyticsPalette
    LabCard(title = stringResource(R.string.calibration_values_title)) {
        Text(
            stringResource(R.string.calibration_instructions),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        state.calibrationValidationFailure?.let { failure ->
            Text(
                text = stringResource(
                    when (failure) {
                        CalibrationValidationFailure.CONFIGURATION_UNAVAILABLE ->
                            R.string.calibration_configuration_unavailable

                        CalibrationValidationFailure.ANCHORS_TOO_CLOSE ->
                            R.string.calibration_anchors_too_close
                    },
                ),
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
                fontWeight = FontWeight.SemiBold,
            )
        }
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth()) {
            CalibrationStatus(
                label = stringResource(R.string.posture_cover),
                posture = DisplayPosture.COVER,
                isRegistered = state.calibration.cover != null,
                color = colors.cover,
                modifier = Modifier.weight(1f),
            )
            CalibrationStatus(
                label = stringResource(R.string.posture_inner),
                posture = DisplayPosture.INNER,
                isRegistered = state.calibration.inner != null,
                color = colors.inner,
                modifier = Modifier.weight(1f),
            )
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilledTonalButton(
                onClick = onSaveCover,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = palette.coverContainer,
                    contentColor = palette.onCoverContainer,
                ),
            ) {
                Text(stringResource(R.string.action_record_cover), textAlign = TextAlign.Center)
            }
            FilledTonalButton(
                onClick = onSaveInner,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = palette.innerContainer,
                    contentColor = palette.onInnerContainer,
                ),
            ) {
                Text(stringResource(R.string.action_record_inner), textAlign = TextAlign.Center)
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
    posture: DisplayPosture,
    isRegistered: Boolean,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        FoldGlyph(posture, if (isRegistered) color else MaterialTheme.colorScheme.outline)
        Column {
            Text(label, style = MaterialTheme.typography.titleSmall)
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
