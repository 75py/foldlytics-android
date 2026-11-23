package com.nagopy.android.foldlytics.data

import com.nagopy.android.foldlytics.model.UsageEventKind

/** Stored rows before a boundary that carry the analyzers' state across that boundary. */
internal data class AnalysisSeedRows(
    val postureEvidence: List<UsageEventEntity> = emptyList(),
    val activity: List<UsageEventEntity> = emptyList(),
) {
    val eventKeys: List<String>
        get() = (postureEvidence + activity).map(UsageEventEntity::eventKey)
}

/**
 * Reduces the history before a boundary to the real rows that reproduce the state the usage and
 * inner-session analyzers hold at that boundary.
 *
 * Replaying the result in place of the full history leaves every tracker identical at the
 * boundary, for any continuation that applies the same later rows, posture checkpoints,
 * device-state checkpoints and at least one collection gap at or after the last gap before the
 * boundary:
 * - Device restarts and collection gaps clear every tracker. Rows before the latest clear are
 *   dropped, and the latest restart row is kept for its screen, keyguard and reason effects.
 * - Configuration deltas fold per dimension. The latest positive value of each dimension, the
 *   latest configuration row and the latest configuration row without values, which clears the
 *   fold, reproduce the resolved configuration, posture and unknown reason. A checkpoint
 *   replaces the whole baseline, so it masks dropped and kept rows alike, but a kept clear must
 *   still mask an earlier checkpoint.
 * - Activity state is tracked per activity and has finitely many values. An activity that returns
 *   to no evidence is identical to one never seen, and rows between two equal states form a loop
 *   that replay can skip. Each activity therefore keeps at most one row per reachable state, in
 *   original order, which also preserves the order in which activities first gained evidence.
 *
 * Screen and keyguard rows are not compacted because analysis already reads their latest values.
 */
internal object AnalysisSeedCompactor {
    /** Bump when the selection rules change, so persisted selections are rebuilt. */
    const val VERSION = 1

    fun compact(
        postureEvidence: List<UsageEventEntity>,
        activity: List<UsageEventEntity>,
        collectionGapStarts: List<Long>,
        boundaryMillis: Long,
    ): AnalysisSeedRows {
        // Match the analyzers' stable (timestamp, sequence) order over posture rows, then activity.
        val ordered = (postureEvidence + activity)
            .filter { it.timestampMillis < boundaryMillis }
            .sortedWith(
                compareBy<UsageEventEntity> { it.timestampMillis }
                    .thenBy { it.sequenceAtTimestamp },
            )
        val gaps = collectionGapStarts.filter { it < boundaryMillis }.sorted()
        var gapIndex = 0

        var lastRestart: UsageEventEntity? = null
        var lastConfiguration: UsageEventEntity? = null
        var lastConfigurationClear: UsageEventEntity? = null
        val lastPositiveDimension = arrayOfNulls<UsageEventEntity>(CONFIGURATION_DIMENSIONS)
        val activities = linkedMapOf<Pair<String, String>, ActivityRows>()

        fun clearAll() {
            lastRestart = null
            lastConfiguration = null
            lastConfigurationClear = null
            lastPositiveDimension.fill(null)
            activities.clear()
        }

        ordered.forEach { row ->
            // A gap follows raw events at its own millisecond.
            while (gapIndex < gaps.size && gaps[gapIndex] < row.timestampMillis) {
                clearAll()
                gapIndex += 1
            }
            when (row.rawEventType.toUsageEventKind()) {
                UsageEventKind.DEVICE_STARTUP,
                UsageEventKind.DEVICE_SHUTDOWN,
                -> {
                    clearAll()
                    lastRestart = row
                }

                UsageEventKind.CONFIGURATION_CHANGED -> {
                    lastConfiguration = row
                    if (row.hasConfiguration) {
                        row.configurationDimensions().forEachIndexed { index, value ->
                            if (value != null && value > 0) lastPositiveDimension[index] = row
                        }
                    } else {
                        lastConfigurationClear = row
                        lastPositiveDimension.fill(null)
                    }
                }

                UsageEventKind.ACTIVITY_RESUMED,
                UsageEventKind.ACTIVITY_PAUSED,
                UsageEventKind.ACTIVITY_STOPPED,
                -> {
                    val packageName = row.packageName ?: return@forEach
                    val key = packageName to row.className.orEmpty()
                    val path = activities.getOrPut(key) { ActivityRows() }
                    path.tracker.apply(row.toModel())
                    val state = path.tracker.activityStates
                    if (state.isEmpty()) {
                        activities.remove(key)
                        return@forEach
                    }
                    val loopStart = path.states.indexOf(state)
                    if (loopStart >= 0) {
                        path.rows.subList(loopStart + 1, path.rows.size).clear()
                        path.states.subList(loopStart + 1, path.states.size).clear()
                    } else {
                        path.rows += row
                        path.states += state
                    }
                }

                else -> Unit
            }
        }
        if (gapIndex < gaps.size) clearAll()

        val keptKeys = buildSet {
            lastRestart?.let { add(it.eventKey) }
            lastConfiguration?.let { add(it.eventKey) }
            lastConfigurationClear?.let { add(it.eventKey) }
            lastPositiveDimension.forEach { row -> row?.let { add(it.eventKey) } }
            activities.values.forEach { activityRows ->
                activityRows.rows.forEach { add(it.eventKey) }
            }
        }
        return AnalysisSeedRows(
            postureEvidence = postureEvidence.filter { it.eventKey in keptKeys },
            activity = activity.filter { it.eventKey in keptKeys },
        )
    }

    private fun UsageEventEntity.configurationDimensions(): List<Int?> = listOf(
        screenWidthDp,
        screenHeightDp,
        smallestScreenWidthDp,
        orientation,
        densityDpi,
    )

    private class ActivityRows {
        val tracker = ActivityVisibilityTracker()
        val rows = mutableListOf<UsageEventEntity>()
        val states = mutableListOf<Map<*, *>>()
    }

    private const val CONFIGURATION_DIMENSIONS = 5
}

/** Seed snapshots sit on UTC midnights so successive startups share them across time zones. */
internal fun analysisSeedBoundaryAtOrBefore(millis: Long): Long =
    Math.floorDiv(millis, ANALYSIS_SEED_BOUNDARY_MILLIS) * ANALYSIS_SEED_BOUNDARY_MILLIS

internal const val ANALYSIS_SEED_BOUNDARY_MILLIS: Long = 24L * 60L * 60L * 1000L
