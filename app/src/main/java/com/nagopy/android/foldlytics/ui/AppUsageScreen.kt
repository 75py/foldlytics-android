package com.nagopy.android.foldlytics.ui

import android.content.res.Resources
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nagopy.android.foldlytics.MainUiState
import com.nagopy.android.foldlytics.R
import com.nagopy.android.foldlytics.model.AppUsage
import com.nagopy.android.foldlytics.toDurationText

private val AppRankingBasis.labelRes: Int
    get() = when (this) {
        AppRankingBasis.TOTAL -> R.string.app_ranking_total
        AppRankingBasis.COVER -> R.string.posture_cover
        AppRankingBasis.INNER -> R.string.posture_inner
    }

private val AppRankingBasis.selectorDescriptionRes: Int
    get() = when (this) {
        AppRankingBasis.TOTAL -> R.string.content_desc_total_app_ranking
        AppRankingBasis.COVER -> R.string.content_desc_cover_app_ranking
        AppRankingBasis.INNER -> R.string.content_desc_inner_app_ranking
    }

private val AppRankingBasis.displayNameRes: Int
    get() = when (this) {
        AppRankingBasis.COVER -> R.string.app_usage_outer_display
        AppRankingBasis.INNER -> R.string.app_usage_inner_display
        AppRankingBasis.TOTAL -> error("A total has no single display")
    }

private val AppRankingView.labelRes: Int
    get() = when (this) {
        AppRankingView.USAGE_TIME -> R.string.app_ranking_view_usage_time
        AppRankingView.DISPLAY_SHARE -> R.string.app_ranking_view_display_share
    }

private val AppRankingView.selectorDescriptionRes: Int
    get() = when (this) {
        AppRankingView.USAGE_TIME -> R.string.content_desc_usage_time_app_ranking
        AppRankingView.DISPLAY_SHARE -> R.string.content_desc_display_share_app_ranking
    }

private val AppDisplayMajority.basis: AppRankingBasis
    get() = when (this) {
        AppDisplayMajority.COVER -> AppRankingBasis.COVER
        AppDisplayMajority.INNER -> AppRankingBasis.INNER
        AppDisplayMajority.EVEN -> error("An even split has no ranking basis")
    }

private val AppDisplayMajority.labelRes: Int
    get() = when (this) {
        AppDisplayMajority.COVER -> R.string.app_ranking_cover_majority
        AppDisplayMajority.INNER -> R.string.app_ranking_inner_majority
        AppDisplayMajority.EVEN -> error("An even split is not selectable")
    }

private val AppDisplayMajority.otherBasis: AppRankingBasis
    get() = when (this) {
        AppDisplayMajority.COVER -> AppRankingBasis.INNER
        AppDisplayMajority.INNER -> AppRankingBasis.COVER
        AppDisplayMajority.EVEN -> error("An even split is not selectable")
    }

private val AppDisplayMajority.selectorDescriptionRes: Int
    get() = when (this) {
        AppDisplayMajority.COVER -> R.string.content_desc_cover_majority_app_ranking
        AppDisplayMajority.INNER -> R.string.content_desc_inner_majority_app_ranking
        AppDisplayMajority.EVEN -> error("An even split is not selectable")
    }

private fun Resources.formatDisplayShareValue(value: DisplayShareValue): String = when (value) {
    is DisplayShareValue.RoundedPercent -> getString(
        R.string.value_percent_1,
        value.tenthsOfPercent / 10.0,
    )
    DisplayShareValue.LessThanHalf -> getString(R.string.value_share_less_than_half)
    DisplayShareValue.MoreThanHalf -> getString(R.string.value_share_more_than_half)
    DisplayShareValue.LessThanPointOnePercent -> getString(
        R.string.value_share_less_than_point_one_percent,
    )
    DisplayShareValue.MoreThanNinetyNinePointNinePercent -> getString(
        R.string.value_share_more_than_ninety_nine_point_nine_percent,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AppUsageScreen(
    state: MainUiState,
    scaffoldPadding: PaddingValues,
    listState: LazyListState = androidx.compose.foundation.lazy.rememberLazyListState(),
) {
    var selectedView by rememberSaveable { mutableStateOf(AppRankingView.USAGE_TIME) }
    var selectedBasis by rememberSaveable { mutableStateOf(AppRankingBasis.TOTAL) }
    var selectedMajority by rememberSaveable { mutableStateOf(AppDisplayMajority.INNER) }
    val summary = state.periodSummary
    FoldlyticsLazyColumn(
        scaffoldPadding = scaffoldPadding,
        listState = listState,
        modifier = Modifier.testTag(APP_USAGE_SCREEN_TAG),
    ) {
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(APP_USAGE_PERIOD_TAG),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                AnalysisPeriodContext(
                    period = summary?.period ?: state.selectedPeriod,
                    rangeStartMillis = summary?.rangeStartMillis
                        ?: state.customRangeStartMillis
                        ?: state.recordRangeStartMillis,
                    rangeEndMillis = summary?.rangeEndMillis
                        ?: state.customRangeEndMillis
                        ?: state.recordRangeEndMillis,
                )
            }
        }
        item {
            AppRankingSelector(
                selectedView = selectedView,
                onViewSelected = { selectedView = it },
                selectedBasis = selectedBasis,
                onBasisSelected = { selectedBasis = it },
                selectedMajority = selectedMajority,
                onMajoritySelected = { selectedMajority = it },
            )
        }
        if (summary == null) {
            item { HintCard(stringResource(R.string.app_usage_detail_empty)) }
        } else {
            val effectiveBasis = if (selectedView == AppRankingView.USAGE_TIME) {
                selectedBasis
            } else {
                selectedMajority.basis
            }
            val rankedApps = if (selectedView == AppRankingView.USAGE_TIME) {
                rankAppsForDisplay(summary.apps, selectedBasis)
            } else {
                rankAppsForDisplayMajority(summary.apps, selectedMajority)
            }
            val hasMeasurableLauncherApp = summary.apps.any { app ->
                app.isLauncherApp && app.classifiedMillis > 0L
            }
            val maximumMillis = rankedApps.firstOrNull()?.rankingMillis ?: 0L
            if (rankedApps.isNotEmpty()) {
                itemsIndexed(
                    items = rankedApps,
                    key = { _, rankedApp -> rankedApp.app.packageName },
                    contentType = { _, _ -> "app-usage" },
                ) { _, rankedApp ->
                    AppUsageCard(
                        app = rankedApp.app,
                        rank = rankedApp.rank,
                        basis = effectiveBasis,
                        maximumMillis = maximumMillis,
                        showDisplayShareBar = selectedView == AppRankingView.DISPLAY_SHARE,
                    )
                }
            } else {
                item {
                    HintCard(
                        when {
                            selectedView == AppRankingView.DISPLAY_SHARE &&
                                hasMeasurableLauncherApp -> stringResource(
                                    R.string.no_display_majority_apps,
                                    stringResource(selectedMajority.basis.displayNameRes),
                                    stringResource(selectedMajority.otherBasis.displayNameRes),
                                )
                            selectedView == AppRankingView.DISPLAY_SHARE ||
                                selectedBasis == AppRankingBasis.TOTAL -> stringResource(
                                    R.string.no_ranked_apps_total,
                                )
                            else -> stringResource(
                                R.string.no_ranked_apps,
                                stringResource(selectedBasis.labelRes),
                            )
                        },
                    )
                }
            }
        }
    }
}

internal const val APP_USAGE_PERIOD_TAG = "app_usage_period"
internal const val APP_USAGE_RANKING_SELECTOR_TAG = "app_usage_ranking_selector"
internal const val APP_USAGE_VIEW_SELECTOR_TAG = "app_usage_view_selector"
internal const val APP_USAGE_TIME_VIEW_TAG = "app_usage_time_view"
internal const val APP_USAGE_DISPLAY_SHARE_VIEW_TAG = "app_usage_display_share_view"
internal const val APP_USAGE_TOTAL_SEGMENT_TAG = "app_usage_total_segment"
internal const val APP_USAGE_COVER_SEGMENT_TAG = "app_usage_cover_segment"
internal const val APP_USAGE_INNER_SEGMENT_TAG = "app_usage_inner_segment"
internal const val APP_USAGE_MAJORITY_SELECTOR_TAG = "app_usage_majority_selector"
internal const val APP_USAGE_COVER_MAJORITY_TAG = "app_usage_cover_majority"
internal const val APP_USAGE_INNER_MAJORITY_TAG = "app_usage_inner_majority"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppRankingSelector(
    selectedView: AppRankingView,
    onViewSelected: (AppRankingView) -> Unit,
    selectedBasis: AppRankingBasis,
    onBasisSelected: (AppRankingBasis) -> Unit,
    selectedMajority: AppDisplayMajority,
    onMajoritySelected: (AppDisplayMajority) -> Unit,
) {
    LabCard(title = stringResource(R.string.app_ranking_title)) {
        Overline(stringResource(R.string.app_ranking_view))
        Spacer(Modifier.height(4.dp))
        SegmentedToggle(
            options = AppRankingView.entries,
            selected = selectedView,
            onSelected = onViewSelected,
            label = { view -> stringResource(view.labelRes) },
            optionModifier = { view ->
                val selectorDescription = stringResource(view.selectorDescriptionRes)
                Modifier
                    .testTag(
                        when (view) {
                            AppRankingView.USAGE_TIME -> APP_USAGE_TIME_VIEW_TAG
                            AppRankingView.DISPLAY_SHARE -> APP_USAGE_DISPLAY_SHARE_VIEW_TAG
                        },
                    )
                    .semantics { contentDescription = selectorDescription }
            },
            modifier = Modifier.testTag(APP_USAGE_VIEW_SELECTOR_TAG),
        )
        Spacer(Modifier.height(10.dp))
        Overline(
            stringResource(
                if (selectedView == AppRankingView.USAGE_TIME) {
                    R.string.app_ranking_basis
                } else {
                    R.string.app_ranking_higher_share
                },
            ),
        )
        Spacer(Modifier.height(4.dp))
        if (selectedView == AppRankingView.USAGE_TIME) {
            UsageTimeBasisSelector(
                selectedBasis = selectedBasis,
                onBasisSelected = onBasisSelected,
            )
        } else {
            DisplayMajoritySelector(
                selectedMajority = selectedMajority,
                onMajoritySelected = onMajoritySelected,
            )
        }
        Spacer(Modifier.height(10.dp))
        Text(
            if (selectedView == AppRankingView.DISPLAY_SHARE) {
                stringResource(
                    R.string.app_ranking_majority_order,
                    stringResource(selectedMajority.basis.displayNameRes),
                    stringResource(selectedMajority.otherBasis.displayNameRes),
                )
            } else {
                when (selectedBasis) {
                    AppRankingBasis.TOTAL -> stringResource(R.string.app_ranking_total_order)
                    AppRankingBasis.COVER,
                    AppRankingBasis.INNER,
                    -> stringResource(
                        R.string.app_ranking_order,
                        stringResource(selectedBasis.displayNameRes),
                    )
                }
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            stringResource(R.string.app_ranking_measurement_note),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (selectedView == AppRankingView.DISPLAY_SHARE) {
            Text(
                stringResource(R.string.app_ranking_even_split_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun UsageTimeBasisSelector(
    selectedBasis: AppRankingBasis,
    onBasisSelected: (AppRankingBasis) -> Unit,
) {
    SegmentedToggle(
        options = AppRankingBasis.entries,
        selected = selectedBasis,
        onSelected = onBasisSelected,
        label = { basis -> stringResource(basis.labelRes) },
        optionModifier = { basis ->
            val selectorDescription = stringResource(basis.selectorDescriptionRes)
            Modifier
                .testTag(
                    when (basis) {
                        AppRankingBasis.TOTAL -> APP_USAGE_TOTAL_SEGMENT_TAG
                        AppRankingBasis.COVER -> APP_USAGE_COVER_SEGMENT_TAG
                        AppRankingBasis.INNER -> APP_USAGE_INNER_SEGMENT_TAG
                    },
                )
                .semantics { contentDescription = selectorDescription }
        },
        modifier = Modifier.testTag(APP_USAGE_RANKING_SELECTOR_TAG),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DisplayMajoritySelector(
    selectedMajority: AppDisplayMajority,
    onMajoritySelected: (AppDisplayMajority) -> Unit,
) {
    SegmentedToggle(
        options = listOf(AppDisplayMajority.COVER, AppDisplayMajority.INNER),
        selected = selectedMajority,
        onSelected = onMajoritySelected,
        label = { majority -> stringResource(majority.labelRes) },
        optionModifier = { majority ->
            val selectorDescription = stringResource(majority.selectorDescriptionRes)
            Modifier
                .testTag(
                    if (majority == AppDisplayMajority.COVER) {
                        APP_USAGE_COVER_MAJORITY_TAG
                    } else {
                        APP_USAGE_INNER_MAJORITY_TAG
                    },
                )
                .semantics { contentDescription = selectorDescription }
        },
        modifier = Modifier.testTag(APP_USAGE_MAJORITY_SELECTOR_TAG),
    )
}

@Composable
internal fun AppUsageCard(
    app: AppUsage,
    rank: Int,
    basis: AppRankingBasis,
    maximumMillis: Long,
    showDisplayShareBar: Boolean = false,
) {
    val resources = LocalResources.current
    val colors = postureColors()
    val selectedMillis = basis.selectedMillis(app)
    val sharePresentation = requireNotNull(app.displaySharePresentation()) {
        "A ranked app must have classified display time"
    }
    val coverShareText = resources.formatDisplayShareValue(sharePresentation.cover)
    val innerShareText = resources.formatDisplayShareValue(sharePresentation.inner)
    val primaryText = if (basis == AppRankingBasis.TOTAL) {
        resources.getString(
            R.string.app_usage_total,
            selectedMillis.toDurationText(resources),
        )
    } else {
        resources.getString(
            R.string.app_usage_selected,
            resources.getString(basis.labelRes),
            selectedMillis.toDurationText(resources),
        )
    }
    val contextText = resources.getString(
        R.string.app_usage_display_split,
        app.coverMillis.toDurationText(resources),
        coverShareText,
        app.innerMillis.toDurationText(resources),
        innerShareText,
    )
    val undeterminedText = app.excludedMillis.takeIf { it > 0L }?.let { excludedMillis ->
        resources.getString(
            R.string.app_usage_undetermined,
            excludedMillis.toDurationText(resources),
        )
    }
    val rankText = resources.getString(R.string.value_rank, rank)
    val cardDescription = if (undeterminedText == null) {
        resources.getString(
            R.string.content_desc_app_usage_card,
            rankText,
            app.label,
            primaryText,
            contextText,
        )
    } else {
        resources.getString(
            R.string.content_desc_app_usage_card_with_undetermined,
            rankText,
            app.label,
            primaryText,
            contextText,
            undeterminedText,
        )
    }
    val selectedColor = when (basis) {
        AppRankingBasis.TOTAL -> MaterialTheme.colorScheme.primary
        AppRankingBasis.COVER -> colors.cover
        AppRankingBasis.INNER -> colors.inner
    }
    Surface(
        modifier = Modifier
            .testTag("$APP_USAGE_CARD_TAG_PREFIX${app.packageName}")
            .clearAndSetSemantics { contentDescription = cardDescription },
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, top = 16.dp, end = CardPadding, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                FigureText(
                    rankText,
                    modifier = Modifier.widthIn(min = 40.dp),
                    figureStyle = MaterialTheme.typography.headlineLarge,
                    unitStyle = MaterialTheme.typography.labelMedium,
                    color = if (rank <= 3) selectedColor else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    textAlign = TextAlign.Center,
                )
                ApplicationIcon(app.packageName, app.label)
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        app.label,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        primaryText,
                        style = MaterialTheme.typography.labelLarge,
                        color = selectedColor,
                    )
                }
            }
            if (showDisplayShareBar) {
                AppDisplayShareBar(
                    coverFraction = sharePresentation.shares.cover.toFloat(),
                    colors = colors,
                )
            } else {
                AppRankingBar(
                    value = selectedMillis,
                    maximum = maximumMillis,
                    color = selectedColor,
                )
            }
            Text(
                contextText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            undeterminedText?.let { text ->
                Text(
                    text,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
