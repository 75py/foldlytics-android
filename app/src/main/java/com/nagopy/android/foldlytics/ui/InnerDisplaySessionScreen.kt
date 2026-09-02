package com.nagopy.android.foldlytics.ui

import android.content.res.Resources
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nagopy.android.foldlytics.MainUiState
import com.nagopy.android.foldlytics.R
import com.nagopy.android.foldlytics.model.InnerSessionAppUsage
import com.nagopy.android.foldlytics.model.InnerSessionDetail
import com.nagopy.android.foldlytics.model.InnerSessionSummary
import com.nagopy.android.foldlytics.toDurationText
import com.nagopy.android.foldlytics.toInnerSessionStartText

@Composable
internal fun InnerDisplaySessionScreen(
    state: MainUiState,
    scaffoldPadding: PaddingValues,
    listState: LazyListState = androidx.compose.foundation.lazy.rememberLazyListState(),
) {
    val summary = state.innerSessionSummary
    FoldlyticsLazyColumn(
        scaffoldPadding = scaffoldPadding,
        listState = listState,
        modifier = Modifier.testTag(INNER_DISPLAY_SESSION_SCREEN_TAG),
    ) {
        item {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                AnalysisPeriodContext(
                    period = state.periodSummary?.period ?: state.selectedPeriod,
                    rangeStartMillis = summary?.rangeStartMillis
                        ?: state.periodSummary?.rangeStartMillis
                        ?: state.customRangeStartMillis
                        ?: state.recordRangeStartMillis,
                    rangeEndMillis = summary?.rangeEndMillis
                        ?: state.periodSummary?.rangeEndMillis
                        ?: state.customRangeEndMillis
                        ?: state.recordRangeEndMillis,
                )
            }
        }
        if (summary == null) {
            item { HintCard(stringResource(R.string.inner_sessions_detail_empty)) }
        } else {
            item { InnerSessionOverviewCard(summary) }
            if (summary.completeSessionCount > 0) {
                item { InnerSessionLongSessionsCard(summary) }
            }
        }
        item { InnerSessionMethodCard() }
    }
}

@Composable
private fun InnerSessionOverviewCard(summary: InnerSessionSummary) {
    val resources = LocalResources.current
    val innerColor = postureColors().inner
    LabCard(
        title = stringResource(R.string.inner_sessions_overview_title),
        modifier = Modifier.testTag(INNER_SESSION_CARD_TAG),
    ) {
        Text(
            stringResource(R.string.inner_sessions_description),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.size(8.dp))

        val countText = stringResource(
            R.string.value_complete_inner_sessions,
            summary.completeSessionCount,
            summary.detectedOpenCount,
        )
        Text(
            countText,
            modifier = Modifier.testTag(INNER_SESSION_COUNT_TAG),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
        )
        if (summary.completeSessionCount == 0) {
            Text(
                text = if (summary.detectedOpenCount == 0) {
                    stringResource(R.string.inner_sessions_empty_no_opens)
                } else {
                    pluralStringResource(
                        R.plurals.inner_sessions_empty_no_complete,
                        summary.detectedOpenCount,
                        summary.detectedOpenCount,
                    )
                },
                modifier = Modifier.testTag(INNER_SESSION_EMPTY_TAG),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@LabCard
        }

        val medianText = summary.medianInnerActiveMillis
            ?.toDurationText(resources)
            ?: stringResource(R.string.label_no_data)
        val averageText = summary.averageInnerActiveMillis
            ?.toDurationText(resources)
            ?: stringResource(R.string.label_no_data)
        val longestText = summary.longestInnerActiveMillis
            ?.toDurationText(resources)
            ?: stringResource(R.string.label_no_data)
        val metricsDescription = stringResource(
            R.string.content_desc_inner_session_metrics,
            summary.completeSessionCount,
            summary.detectedOpenCount,
            medianText,
            averageText,
            longestText,
        )
        Box(Modifier.testTag(INNER_SESSION_METRICS_TAG)) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clearAndSetSemantics { contentDescription = metricsDescription },
            ) {
                BoxWithConstraints(Modifier.fillMaxWidth()) {
                    if (maxWidth >= 420.dp) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                        ) {
                            Metric(
                                stringResource(R.string.label_median_inner_session_time),
                                medianText,
                                innerColor,
                                Modifier.weight(1f),
                            )
                            Metric(
                                stringResource(R.string.label_average_inner_session_time),
                                averageText,
                                innerColor,
                                Modifier.weight(1f),
                            )
                            Metric(
                                stringResource(R.string.label_longest_inner_session_time),
                                longestText,
                                innerColor,
                                Modifier.weight(1f),
                            )
                        }
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Metric(
                                stringResource(R.string.label_median_inner_session_time),
                                medianText,
                                innerColor,
                            )
                            Metric(
                                stringResource(R.string.label_average_inner_session_time),
                                averageText,
                                innerColor,
                            )
                            Metric(
                                stringResource(R.string.label_longest_inner_session_time),
                                longestText,
                                innerColor,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun InnerSessionLongSessionsCard(summary: InnerSessionSummary) {
    val innerColor = postureColors().inner
    LabCard(
        title = stringResource(R.string.long_inner_sessions_title),
        modifier = Modifier.testTag(INNER_SESSION_LONG_SESSIONS_CARD_TAG),
    ) {
        val longSessions = summary.longSessions
        if (longSessions.isEmpty()) {
            Text(
                stringResource(R.string.long_inner_sessions_empty),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            if (longSessions.any { it.otherInnerActiveMillis > 0L }) {
                Text(
                    stringResource(R.string.inner_session_other_description),
                    modifier = Modifier.testTag(INNER_SESSION_OTHER_DESCRIPTION_TAG),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            longSessions.forEach { session ->
                InnerSessionDetailContent(
                    session = session,
                    color = innerColor,
                )
            }
        }
    }
}

@Composable
private fun InnerSessionMethodCard() {
    LabCard(
        title = stringResource(R.string.inner_sessions_method_title),
        modifier = Modifier.testTag(INNER_SESSION_METHOD_TAG),
    ) {
        Text(
            stringResource(R.string.inner_sessions_method_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun InnerSessionDetailContent(
    session: InnerSessionDetail,
    color: androidx.compose.ui.graphics.Color,
) {
    val resources = LocalResources.current
    val openedAtText = session.openedAtMillis.toInnerSessionStartText(resources)
    val durationText = session.innerActiveMillis.toDurationText(resources)
    val appDescription = session.appUsages
        .map { app ->
            resources.getString(
                R.string.content_desc_inner_session_app,
                app.label,
                app.innerActiveMillis.toDurationText(resources),
            )
        }
        .ifEmpty {
            listOf(resources.getString(R.string.content_desc_inner_session_no_apps))
        }
        .joinToString(resources.getString(R.string.content_desc_inner_session_app_separator))
    val detailDescription = if (session.otherInnerActiveMillis > 0L) {
        resources.getString(
            R.string.content_desc_inner_session_detail,
            openedAtText,
            durationText,
            appDescription,
            session.otherInnerActiveMillis.toDurationText(resources),
        )
    } else {
        resources.getString(
            R.string.content_desc_inner_session_detail_without_other,
            openedAtText,
            durationText,
            appDescription,
        )
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .testTag(
                "$INNER_SESSION_DETAIL_TAG_PREFIX" +
                    "${session.openedAtMillis}_${session.openedSequenceAtTimestamp}",
            )
            .clearAndSetSemantics { contentDescription = detailDescription },
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                openedAtText,
                modifier = Modifier.weight(1f),
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                stringResource(R.string.inner_session_duration, durationText),
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold,
                color = color,
            )
        }
        session.appUsages.forEach { app ->
            InnerSessionAppRow(app)
        }
        if (session.otherInnerActiveMillis > 0L) {
            InnerSessionOtherRow(
                session = session,
                resources = resources,
            )
        }
    }
}

@Composable
private fun InnerSessionAppRow(app: InnerSessionAppUsage) {
    val resources = LocalResources.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("$INNER_SESSION_APP_TAG_PREFIX${app.packageName}")
            .semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        ApplicationIcon(app.packageName, app.label)
        Text(
            app.label,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            app.innerActiveMillis.toDurationText(resources),
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun InnerSessionOtherRow(
    session: InnerSessionDetail,
    resources: Resources,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(
                "$INNER_SESSION_OTHER_TAG_PREFIX" +
                    "${session.openedAtMillis}_${session.openedSequenceAtTimestamp}",
            )
            .semantics(mergeDescendants = true) {},
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            stringResource(R.string.inner_session_other),
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            session.otherInnerActiveMillis.toDurationText(resources),
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
        )
    }
}
