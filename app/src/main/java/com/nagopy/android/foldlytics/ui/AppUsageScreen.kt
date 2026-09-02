package com.nagopy.android.foldlytics.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nagopy.android.foldlytics.MainUiState
import com.nagopy.android.foldlytics.R
import com.nagopy.android.foldlytics.model.AppUsage
import com.nagopy.android.foldlytics.toDurationText

internal enum class AppRankingBasis(
    val labelRes: Int,
    val selectorDescriptionRes: Int,
) {
    TOTAL(
        labelRes = R.string.app_ranking_total,
        selectorDescriptionRes = R.string.content_desc_total_app_ranking,
    ),
    COVER(
        labelRes = R.string.posture_cover,
        selectorDescriptionRes = R.string.content_desc_cover_app_ranking,
    ),
    INNER(
        labelRes = R.string.posture_inner,
        selectorDescriptionRes = R.string.content_desc_inner_app_ranking,
    ),
    ;

    fun selectedMillis(app: AppUsage): Long = when (this) {
        TOTAL -> app.classifiedMillis
        COVER -> app.coverMillis
        INNER -> app.innerMillis
    }

    fun counterpartMillis(app: AppUsage): Long = when (this) {
        TOTAL -> 0L
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
            .thenBy { it.label }
            .thenBy { it.packageName },
    )
    .toList()

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AppUsageScreen(
    state: MainUiState,
    scaffoldPadding: PaddingValues,
    listState: LazyListState = androidx.compose.foundation.lazy.rememberLazyListState(),
) {
    var selectedBasis by rememberSaveable { mutableStateOf(AppRankingBasis.TOTAL) }
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
                selectedBasis = selectedBasis,
                onBasisSelected = { selectedBasis = it },
            )
        }
        if (summary == null) {
            item { HintCard(stringResource(R.string.app_usage_detail_empty)) }
        } else {
            val rankedApps = rankAppsForDisplay(summary.apps, selectedBasis)
            val maximumMillis = rankedApps.firstOrNull()?.let(selectedBasis::selectedMillis) ?: 0L
            if (rankedApps.isNotEmpty()) {
                itemsIndexed(
                    items = rankedApps,
                    key = { _, app -> app.packageName },
                    contentType = { _, _ -> "app-usage" },
                ) { index, app ->
                    AppUsageCard(
                        app = app,
                        rank = index + 1,
                        basis = selectedBasis,
                        maximumMillis = maximumMillis,
                    )
                }
            } else {
                item {
                    HintCard(
                        if (selectedBasis == AppRankingBasis.TOTAL) {
                            stringResource(R.string.no_ranked_apps_total)
                        } else {
                            stringResource(
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
internal const val APP_USAGE_TOTAL_SEGMENT_TAG = "app_usage_total_segment"
internal const val APP_USAGE_COVER_SEGMENT_TAG = "app_usage_cover_segment"
internal const val APP_USAGE_INNER_SEGMENT_TAG = "app_usage_inner_segment"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppRankingSelector(
    selectedBasis: AppRankingBasis,
    onBasisSelected: (AppRankingBasis) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            stringResource(R.string.app_ranking_title),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )
        Text(
            stringResource(R.string.app_ranking_basis),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        SingleChoiceSegmentedButtonRow(
            modifier = Modifier
                .fillMaxWidth()
                .testTag(APP_USAGE_RANKING_SELECTOR_TAG),
        ) {
            AppRankingBasis.entries.forEachIndexed { index, basis ->
                val selectorDescription = stringResource(basis.selectorDescriptionRes)
                SegmentedButton(
                    selected = selectedBasis == basis,
                    onClick = { onBasisSelected(basis) },
                    shape = SegmentedButtonDefaults.itemShape(index, AppRankingBasis.entries.size),
                    label = { Text(stringResource(basis.labelRes)) },
                    modifier = Modifier
                        .testTag(
                            when (basis) {
                                AppRankingBasis.TOTAL -> APP_USAGE_TOTAL_SEGMENT_TAG
                                AppRankingBasis.COVER -> APP_USAGE_COVER_SEGMENT_TAG
                                AppRankingBasis.INNER -> APP_USAGE_INNER_SEGMENT_TAG
                            },
                        )
                        .semantics { contentDescription = selectorDescription },
                )
            }
        }
        Text(
            if (selectedBasis == AppRankingBasis.TOTAL) {
                stringResource(R.string.app_ranking_total_order)
            } else {
                stringResource(
                    R.string.app_ranking_order,
                    stringResource(selectedBasis.labelRes),
                )
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
internal fun AppUsageCard(
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
        AppRankingBasis.TOTAL -> MaterialTheme.colorScheme.primary
        AppRankingBasis.COVER -> colors.cover
        AppRankingBasis.INNER -> colors.inner
    }
    Card(
        modifier = Modifier.testTag("$APP_USAGE_CARD_TAG_PREFIX${app.packageName}"),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
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
                        if (basis == AppRankingBasis.TOTAL) {
                            stringResource(
                                R.string.app_usage_total,
                                selectedMillis.toDurationText(resources),
                            )
                        } else {
                            stringResource(
                                R.string.app_usage_selected,
                                stringResource(basis.labelRes),
                                selectedMillis.toDurationText(resources),
                            )
                        },
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
                if (basis == AppRankingBasis.TOTAL) {
                    stringResource(
                        R.string.app_usage_display_breakdown,
                        app.coverMillis.toDurationText(resources),
                        app.innerMillis.toDurationText(resources),
                    )
                } else {
                    val counterpartLabelRes = if (basis == AppRankingBasis.COVER) {
                        R.string.posture_inner
                    } else {
                        R.string.posture_cover
                    }
                    stringResource(
                        R.string.app_usage_counterpart,
                        stringResource(counterpartLabelRes),
                        counterpartMillis.toDurationText(resources),
                    )
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
