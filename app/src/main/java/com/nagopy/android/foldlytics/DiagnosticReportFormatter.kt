package com.nagopy.android.foldlytics

import android.content.res.Resources
import com.nagopy.android.foldlytics.model.AnalysisPeriod
import com.nagopy.android.foldlytics.model.UnknownPostureReason
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private const val MAX_REPORT_POSTURE_EVENTS = 50
private const val MAX_REPORT_APPS = 100

/** Renders the shareable diagnostic report for a [MainUiState] snapshot. */
object DiagnosticReportFormatter {
    fun format(
        state: MainUiState,
        resources: Resources,
        zoneId: ZoneId = ZoneId.systemDefault(),
        createdAt: Instant = Instant.now(),
    ): String {
        val analysis = state.analysis
        val formatter = DateTimeFormatter.ofPattern(
            resources.getString(R.string.report_date_time_pattern),
            resources.primaryLocale,
        )
            .withZone(zoneId)
        fun formatInstant(timestampMillis: Long): String =
            formatter.format(Instant.ofEpochMilli(timestampMillis))
        return buildString {
            val displayedPeriod = state.displayedAnalysisPeriod

            fun appendField(labelRes: Int, value: String) {
                appendLine(
                    resources.getString(
                        R.string.report_field,
                        resources.getString(labelRes),
                        value,
                    ),
                )
            }

            appendLine(resources.getString(R.string.report_title))
            appendField(R.string.label_created, formatter.format(createdAt))
            appendField(
                R.string.label_usage_access,
                resources.getString(
                    if (state.hasUsageAccess) R.string.status_allowed else R.string.status_not_allowed,
                ),
            )
            appendField(
                R.string.label_current_posture,
                resources.getString(state.currentPosture.labelRes),
            )
            appendField(
                R.string.label_current_configuration,
                state.currentConfiguration?.toDisplayText(resources)
                    ?: resources.getString(R.string.posture_unknown),
            )
            appendField(
                R.string.label_cover_calibration,
                state.calibration.cover?.toDisplayText(resources)
                    ?: resources.getString(R.string.status_not_registered),
            )
            appendField(
                R.string.label_inner_calibration,
                state.calibration.inner?.toDisplayText(resources)
                    ?: resources.getString(R.string.status_not_registered),
            )
            appendField(
                R.string.label_classification_method,
                resources.getString(
                    if (state.calibration.isComplete) {
                        R.string.classification_saved_values
                    } else {
                        R.string.classification_automatic
                    },
                ),
            )
            appendField(
                R.string.label_data_source,
                resources.getString(R.string.data_source_on_device_database),
            )
            val selectedPeriodText = if (
                displayedPeriod == AnalysisPeriod.CUSTOM && state.periodSummary != null
            ) {
                val summary = state.periodSummary
                resources.getString(
                    R.string.custom_period_with_range,
                    resources.getString(displayedPeriod.labelRes),
                    formatInstant(summary.rangeStartMillis),
                    formatInstant(summary.rangeEndMillis),
                )
            } else {
                resources.getString(displayedPeriod.labelRes)
            }
            appendField(R.string.label_screen_period, selectedPeriodText)
            appendField(
                R.string.label_last_sync,
                state.lastSuccessfulSyncMillis?.let(::formatInstant)
                    ?: resources.getString(R.string.status_not_synced),
            )
            if (state.lastSyncQueryBeginMillis != null && state.lastSuccessfulSyncMillis != null) {
                appendField(
                    R.string.label_recent_sync_range,
                    resources.getString(
                        R.string.date_range,
                        formatInstant(state.lastSyncQueryBeginMillis),
                        formatInstant(state.lastSuccessfulSyncMillis),
                    ),
                )
                appendField(
                    R.string.label_recent_sync_saved,
                    resources.getString(
                        R.string.value_item_count,
                        state.lastSyncInsertedEventCount,
                    ),
                )
            }
            appendField(R.string.label_folding_feature, state.foldFeature.toString())
            appendField(
                R.string.label_hinge_angle,
                state.hingeAngle?.let { resources.getString(R.string.value_degrees, it) }
                    ?: resources.getString(R.string.status_not_acquired),
            )
            appendLine()
            if (analysis == null) {
                appendLine(resources.getString(R.string.report_no_analysis))
            } else {
                appendField(
                    R.string.label_diagnostic_period,
                    resources.getString(
                        R.string.duration_hours_only,
                        displayedPeriod.diagnosticHours,
                    ),
                )
                appendField(
                    R.string.label_analysis_range,
                    resources.getString(
                        R.string.date_range,
                        formatInstant(analysis.rangeStartMillis),
                        formatInstant(analysis.rangeEndMillis),
                    ),
                )
                appendField(
                    R.string.posture_cover,
                    analysis.coverMillis.toDurationText(resources),
                )
                appendField(
                    R.string.posture_inner,
                    analysis.innerMillis.toDurationText(resources),
                )
                appendField(
                    R.string.label_classified_time,
                    analysis.classifiedPostureMillis.toDurationText(resources),
                )
                appendField(
                    R.string.label_data_coverage,
                    resources.getString(
                        R.string.value_percent_1,
                        analysis.dataCoverageRatio * 100,
                    ),
                )
                appendField(
                    R.string.label_excluded_time,
                    analysis.excludedPostureMillis.toDurationText(resources),
                )
                UnknownPostureReason.entries.forEach { reason ->
                    val duration = analysis.excludedPostureMillisByReason[reason] ?: 0L
                    if (duration > 0L) {
                        appendLine(
                            resources.getString(
                                R.string.report_bullet_field,
                                resources.getString(reason.labelRes),
                                duration.toDurationText(resources),
                            ),
                        )
                    }
                }
                appendField(
                    R.string.label_opened,
                    resources.getString(R.string.value_open_count, analysis.openedCount),
                )
                appendField(
                    R.string.label_closed,
                    resources.getString(R.string.value_open_count, analysis.closedCount),
                )
                if (analysis.evidenceGapCount > 0) {
                    appendField(
                        R.string.label_evidence_gaps,
                        resources.getString(R.string.value_item_count, analysis.evidenceGapCount),
                    )
                }
                appendField(
                    R.string.label_event_count,
                    resources.getString(R.string.value_item_count, analysis.eventCount),
                )
                appendLine()
                appendLine(resources.getString(R.string.report_apps_heading))
                analysis.apps.take(MAX_REPORT_APPS).forEach { app ->
                    appendLine(
                        "${app.label} (${app.packageName}): " +
                            "${app.coverMillis.toDurationText(resources)} / " +
                            "${app.innerMillis.toDurationText(resources)} / " +
                            app.excludedMillis.toDurationText(resources),
                    )
                }
                if (analysis.apps.size > MAX_REPORT_APPS) {
                    appendLine(
                        resources.getQuantityString(
                            R.plurals.report_apps_omitted,
                            analysis.apps.size - MAX_REPORT_APPS,
                            analysis.apps.size - MAX_REPORT_APPS,
                        ),
                    )
                }
                appendLine()
                val inRangeEvents = analysis.postureEvents.count { !it.isBeforeRange }
                val seedEvents = analysis.postureEvents.size - inRangeEvents
                appendLine(
                    resources.getString(
                        R.string.report_posture_log_heading,
                        inRangeEvents,
                        seedEvents,
                        MAX_REPORT_POSTURE_EVENTS,
                    ),
                )
                analysis.postureEvents.take(MAX_REPORT_POSTURE_EVENTS).forEach { event ->
                    val rangeLabel = resources.getString(
                        if (event.isBeforeRange) R.string.status_range_seed else R.string.status_range_in,
                    )
                    val sourceLabel = event.checkpointSource?.let {
                        resources.getString(
                            R.string.event_source_with_checkpoint,
                            resources.getString(event.source.labelRes),
                            resources.getString(it.labelRes),
                        )
                    } ?: resources.getString(event.source.labelRes)
                    val reasonLabel = event.unknownReason
                        ?.let {
                            resources.getString(
                                R.string.report_reason_suffix,
                                resources.getString(it.labelRes),
                            )
                        }
                        .orEmpty()
                    val rawTypeLabel = event.rawEventType
                        ?.let { resources.getString(R.string.report_raw_type_suffix, it) }
                        .orEmpty()
                    appendLine(
                        resources.getString(
                            R.string.report_event_line,
                            formatInstant(event.timestampMillis),
                            rangeLabel,
                            sourceLabel,
                            resources.getString(event.posture.labelRes),
                            reasonLabel,
                            rawTypeLabel,
                        ),
                    )
                    appendLine(
                        resources.getString(
                            R.string.report_configuration_line,
                            event.configuration?.toDisplayText(resources)
                                ?: resources.getString(R.string.status_none),
                        ),
                    )
                    if (event.coverDistance != null || event.innerDistance != null) {
                        appendLine(
                            resources.getString(
                                R.string.report_distance_line,
                                event.coverDistance?.toString() ?: "—",
                                event.innerDistance?.toString() ?: "—",
                            ),
                        )
                    }
                }
            }
            state.longTermInsights?.let { insights ->
                appendLine()
                appendLine(
                    resources.getString(
                        R.string.report_usage_trends_heading,
                        resources.getString(displayedPeriod.labelRes),
                    ),
                )
                appendField(
                    R.string.posture_cover,
                    insights.coverMillis.toDurationText(resources),
                )
                appendField(
                    R.string.posture_inner,
                    insights.innerMillis.toDurationText(resources),
                )
                appendField(
                    R.string.label_inner_ratio,
                    resources.getString(R.string.value_percent_1, insights.innerRatio * 100),
                )
                appendField(
                    R.string.label_opened,
                    resources.getString(R.string.value_open_count, insights.openedCount),
                )
                appendField(
                    R.string.label_closed,
                    resources.getString(R.string.value_open_count, insights.closedCount),
                )
                appendLine(
                    resources.getString(
                        R.string.report_observed_days,
                        insights.observedDayCount,
                        insights.calendarDayCount,
                        insights.innerUsedDayCount,
                    ),
                )
                insights.thirtyDayInnerRatioDelta?.let { delta ->
                    appendField(
                        R.string.label_inner_ratio_change,
                        resources.getString(R.string.value_points, delta * 100),
                    )
                }
            }
            state.collectionHealth?.let { health ->
                appendLine()
                appendLine(resources.getString(R.string.report_collection_heading))
                appendField(
                    R.string.label_recorded_sync_attempts,
                    resources.getString(R.string.value_item_count, health.recordedAttemptCount),
                )
                appendField(
                    R.string.label_unsuccessful_sync_attempts,
                    resources.getString(R.string.value_item_count, health.unsuccessfulAttemptCount),
                )
                appendField(
                    R.string.label_collection_interruptions,
                    resources.getString(
                        R.string.value_item_count,
                        health.collectionInterruptionCount,
                    ),
                )
                health.longestSuccessfulSyncGapMillis?.let { gap ->
                    appendField(R.string.label_longest_sync_gap, gap.toDurationText(resources))
                }
                appendLine(resources.getString(R.string.collection_health_scope_note))
            }
        }
    }
}
