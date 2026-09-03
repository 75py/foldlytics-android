package com.nagopy.android.foldlytics.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.nagopy.android.foldlytics.MainUiState
import com.nagopy.android.foldlytics.R
import com.nagopy.android.foldlytics.labelRes
import com.nagopy.android.foldlytics.model.DisplayPosture
import com.nagopy.android.foldlytics.model.PostureEvent
import com.nagopy.android.foldlytics.model.UnknownPostureReason
import com.nagopy.android.foldlytics.toDisplayText
import com.nagopy.android.foldlytics.toDurationText
import com.nagopy.android.foldlytics.toTimeText

@Composable
internal fun DiagnosticsContent(
    state: MainUiState,
    scaffoldPadding: PaddingValues,
) {
    val resources = LocalResources.current
    val analysis = state.analysis
    val events = analysis?.postureEvents.orEmpty()
    val inRangeCount = events.count { !it.isBeforeRange }
    val seedCount = events.size - inRangeCount
    FoldlyticsLazyColumn(scaffoldPadding = scaffoldPadding) {
        item {
            LabCard(title = stringResource(R.string.diagnostics_overview_title)) {
                Text(
                    stringResource(
                        R.string.diagnostics_overview_body,
                        state.selectedPeriod.diagnosticHours,
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                )
                InfoLine(
                    stringResource(R.string.label_in_range_posture_events),
                    resources.getString(R.string.value_item_count, inRangeCount),
                )
                InfoLine(
                    stringResource(R.string.label_lookback_events),
                    resources.getString(R.string.value_item_count, seedCount),
                )
                analysis?.let {
                    InfoLine(
                        stringResource(R.string.label_processed_usage_events),
                        resources.getString(R.string.value_item_count, it.eventCount),
                    )
                    if (it.multiResumeMillis > 0L) {
                        InfoLine(
                            stringResource(R.string.label_multi_app_display),
                            it.multiResumeMillis.toDurationText(resources),
                        )
                    }
                }
            }
        }
        item { CurrentConfigurationCard(state) }
        item { ExcludedDataCard(state) }
        item { CollectionDiagnosticsCard(state) }
        item { SectionTitle(stringResource(R.string.posture_events_title)) }
        if (events.isEmpty()) {
            item { HintCard(stringResource(R.string.posture_events_empty)) }
        } else {
            itemsIndexed(
                items = events,
                key = { index, event ->
                    "${event.timestampMillis}:${event.source}:${event.rawEventType}:$index"
                },
                contentType = { _, _ -> "posture-event" },
            ) { _, event ->
                PostureEventRow(event)
            }
        }
    }
}

@Composable
private fun CurrentConfigurationCard(state: MainUiState) {
    val resources = LocalResources.current
    LabCard(title = stringResource(R.string.current_detection_title)) {
        val colors = postureColors()
        val postureColor = when (state.currentPosture) {
            DisplayPosture.COVER -> colors.cover
            DisplayPosture.INNER -> colors.inner
            DisplayPosture.UNKNOWN -> colors.unknown
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                stringResource(R.string.label_current_posture),
                style = MaterialTheme.typography.bodySmall,
            )
            Text(
                stringResource(state.currentPosture.labelRes),
                color = postureColor,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        InfoLine(
            stringResource(R.string.label_current_configuration),
            state.currentConfiguration?.toDisplayText(resources)
                ?: stringResource(R.string.status_not_acquired),
        )
        InfoLine(
            stringResource(R.string.label_cover_calibration),
            state.calibration.cover?.toDisplayText(resources)
                ?: stringResource(R.string.status_not_registered),
        )
        InfoLine(
            stringResource(R.string.label_inner_calibration),
            state.calibration.inner?.toDisplayText(resources)
                ?: stringResource(R.string.status_not_registered),
        )
        HorizontalDivider(Modifier.padding(vertical = 8.dp))
        InfoLine(
            stringResource(R.string.label_folding_feature),
            if (state.foldFeature.present) {
                state.foldFeature.state
            } else {
                stringResource(R.string.status_not_detected)
            },
        )
        InfoLine(
            stringResource(R.string.label_hinge_orientation_occlusion),
            "${state.foldFeature.orientation} / ${state.foldFeature.occlusion}",
        )
        InfoLine(stringResource(R.string.label_bounds), state.foldFeature.bounds)
        InfoLine(
            stringResource(R.string.label_hinge_angle_sensor),
            when {
                !state.hingeSensorAvailable -> stringResource(R.string.status_unavailable)
                state.hingeAngle == null -> stringResource(R.string.status_detected_waiting)
                else -> resources.getString(R.string.value_degrees, state.hingeAngle)
            },
        )
    }
}

@Composable
private fun ExcludedDataCard(state: MainUiState) {
    val resources = LocalResources.current
    val analysis = state.analysis
    LabCard(title = stringResource(R.string.excluded_time_title)) {
        if (analysis == null || analysis.excludedPostureMillis == 0L) {
            Text(
                stringResource(R.string.excluded_time_empty),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            InfoLine(
                stringResource(R.string.label_total),
                analysis.excludedPostureMillis.toDurationText(resources),
            )
            UnknownPostureReason.entries.forEach { reason ->
                val duration = analysis.excludedPostureMillisByReason[reason] ?: 0L
                if (duration > 0L) {
                    InfoLine(
                        stringResource(reason.labelRes),
                        duration.toDurationText(resources),
                    )
                }
            }
            if (analysis.evidenceGapCount > 0) {
                InfoLine(
                    stringResource(R.string.label_evidence_gaps),
                    resources.getString(R.string.value_item_count, analysis.evidenceGapCount),
                )
            }
        }
    }
}

@Composable
private fun CollectionDiagnosticsCard(state: MainUiState) {
    val resources = LocalResources.current
    val insights = state.longTermInsights
    val health = state.collectionHealth
    LabCard(title = stringResource(R.string.collection_status_title)) {
        InfoLine(
            stringResource(R.string.label_last_sync),
            state.lastSuccessfulSyncMillis?.toTimeText(resources)
                ?: stringResource(R.string.status_not_synced),
        )
        if (state.lastSyncQueryBeginMillis != null) {
            InfoLine(
                stringResource(R.string.label_recent_sync_start),
                state.lastSyncQueryBeginMillis.toTimeText(resources),
            )
            InfoLine(
                stringResource(R.string.label_recent_sync_saved),
                resources.getString(R.string.value_item_count, state.lastSyncInsertedEventCount),
            )
        }
        if (insights != null) {
            InfoLine(
                stringResource(R.string.label_evidence_gap_days),
                resources.getString(R.string.value_day_count, insights.evidenceGapDayCount),
            )
        }
        if (health == null || health.recordedAttemptCount == 0) {
            Text(
                stringResource(R.string.sync_history_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            InfoLine(
                stringResource(R.string.label_recorded_sync_attempts),
                resources.getString(R.string.value_item_count, health.recordedAttemptCount),
            )
            InfoLine(
                stringResource(R.string.label_unsuccessful_sync_attempts),
                resources.getString(R.string.value_item_count, health.unsuccessfulAttemptCount),
            )
            InfoLine(
                stringResource(R.string.label_collection_interruptions),
                resources.getString(
                    R.string.value_item_count,
                    health.collectionInterruptionCount,
                ),
            )
            health.longestSuccessfulSyncGapMillis?.let { gap ->
                InfoLine(
                    stringResource(R.string.label_longest_sync_gap),
                    gap.toDurationText(resources),
                )
            }
        }
        Text(
            stringResource(R.string.backup_note),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun PostureEventRow(event: PostureEvent) {
    val resources = LocalResources.current
    val colors = postureColors()
    val postureColor = when (event.posture) {
        DisplayPosture.COVER -> colors.cover
        DisplayPosture.INNER -> colors.inner
        DisplayPosture.UNKNOWN -> colors.unknown
    }
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    event.timestampMillis.toTimeText(resources),
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    stringResource(event.posture.labelRes),
                    color = postureColor,
                    fontWeight = FontWeight.Bold,
                )
            }
            val rangeLabel = stringResource(
                if (event.isBeforeRange) R.string.status_range_seed else R.string.status_range_in,
            )
            val sourceLabel = event.checkpointSource?.let {
                resources.getString(
                    R.string.event_source_with_checkpoint,
                    resources.getString(event.source.labelRes),
                    resources.getString(it.labelRes),
                )
            } ?: stringResource(event.source.labelRes)
            Text(
                stringResource(R.string.event_summary, rangeLabel, sourceLabel),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                event.configuration?.toDisplayText(resources)
                    ?: stringResource(R.string.unknown_reason_configuration_unavailable),
                style = MaterialTheme.typography.bodySmall,
            )
            event.unknownReason?.let { reason ->
                Text(
                    stringResource(
                        R.string.excluded_reason,
                        stringResource(reason.labelRes),
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            if (event.coverDistance != null || event.innerDistance != null) {
                Text(
                    stringResource(
                        R.string.classification_distance,
                        event.coverDistance?.toString() ?: "—",
                        event.innerDistance?.toString() ?: "—",
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
