package com.nagopy.android.foldlytics.data

import com.nagopy.android.foldlytics.model.UsageEventKind
import com.nagopy.android.foldlytics.model.UsageRecord

internal class ActivityVisibilityTracker {
    private val activities = linkedMapOf<ActivityKey, ActivityState>()

    val possibleStateCount: Int
        get() = activities.values.sumOf(ActivityState::possibleStateCount)

    val snapshot: ActivityVisibilitySnapshot
        get() {
            val definitePackages = linkedSetOf<String>()
            val possiblePackages = linkedSetOf<String>()
            activities.forEach { (key, state) ->
                if (state.isDefinitelyVisible) {
                    definitePackages += key.packageName
                } else if (state.isPossiblyVisible) {
                    possiblePackages += key.packageName
                }
            }
            return ActivityVisibilitySnapshot(
                definitePackages = definitePackages,
                possiblePackages = possiblePackages,
            )
        }

    fun apply(record: UsageRecord) {
        when (record.kind) {
            UsageEventKind.ACTIVITY_RESUMED -> resume(record)
            UsageEventKind.ACTIVITY_PAUSED,
            UsageEventKind.ACTIVITY_STOPPED,
            -> pauseOrStop(record)

            else -> Unit
        }
    }

    fun reset() {
        activities.clear()
    }

    private fun resume(record: UsageRecord) {
        val key = record.activityKeyOrNull() ?: return
        update(key, (activities[key] ?: ActivityState.INACTIVE).onResume())
    }

    private fun pauseOrStop(record: UsageRecord) {
        val key = record.activityKeyOrNull() ?: return
        val state = activities[key] ?: ActivityState.INACTIVE
        val nextState = when (record.kind) {
            UsageEventKind.ACTIVITY_PAUSED -> state.onPause()
            UsageEventKind.ACTIVITY_STOPPED -> state.onStop()
            else -> state
        }
        update(key, nextState)
    }

    private fun update(key: ActivityKey, state: ActivityState) {
        if (state.hasEvidence) {
            activities[key] = state
        } else {
            activities.remove(key)
        }
    }

    private fun UsageRecord.activityKeyOrNull(): ActivityKey? {
        val packageName = packageName ?: return null
        return ActivityKey(
            packageName = packageName,
            className = className.orEmpty(),
        )
    }

    private data class ActivityKey(
        val packageName: String,
        val className: String,
    )

    private data class ActivityState(
        val possibleCounts: Set<ActivityCounts>,
    ) {
        val possibleStateCount: Int = possibleCounts.size

        val hasEvidence: Boolean = possibleCounts.any(ActivityCounts::hasEvidence)

        val isDefinitelyVisible: Boolean =
            possibleCounts.isNotEmpty() && possibleCounts.all(ActivityCounts::isVisible)

        val isPossiblyVisible: Boolean =
            possibleCounts.any(ActivityCounts::isVisible)

        fun onResume(): ActivityState = transform { counts ->
            buildList {
                if (counts.resumedCount.isPositive) {
                    add(counts)
                }
                if (counts.pausedAwaitingStopCount.isPositive) {
                    counts.pausedAwaitingStopCount.decrementPossibilities()
                        .forEach { pausedAfterResume ->
                            add(
                                counts.copy(
                                    resumedCount = counts.resumedCount.increment(),
                                    pausedAwaitingStopCount = pausedAfterResume,
                                ),
                            )
                        }
                }
                add(counts.copy(resumedCount = counts.resumedCount.increment()))
            }
        }

        fun onPause(): ActivityState = transform { counts ->
            if (counts.resumedCount.isPositive) {
                counts.resumedCount.decrementPossibilities().map { resumedAfterPause ->
                    counts.copy(
                        resumedCount = resumedAfterPause,
                        pausedAwaitingStopCount = counts.pausedAwaitingStopCount.increment(),
                    )
                }
            } else {
                listOf(
                    counts.copy(
                        pausedAwaitingStopCount = counts.pausedAwaitingStopCount.increment(),
                    ),
                )
            }
        }

        fun onStop(): ActivityState = transform { counts ->
            buildList {
                if (counts.pausedAwaitingStopCount.isPositive) {
                    counts.pausedAwaitingStopCount.decrementPossibilities()
                        .forEach { pausedAfterStop ->
                            add(counts.copy(pausedAwaitingStopCount = pausedAfterStop))
                        }
                }
                if (counts.resumedCount.isPositive) {
                    counts.resumedCount.decrementPossibilities()
                        .forEach { resumedAfterStop ->
                            add(counts.copy(resumedCount = resumedAfterStop))
                        }
                }
                if (
                    !counts.pausedAwaitingStopCount.isPositive &&
                    !counts.resumedCount.isPositive
                ) {
                    add(counts)
                }
            }
        }

        private fun transform(
            block: (ActivityCounts) -> List<ActivityCounts>,
        ): ActivityState = ActivityState(
            possibleCounts = possibleCounts.flatMapTo(linkedSetOf(), block),
        )

        companion object {
            val INACTIVE = ActivityState(setOf(ActivityCounts()))
        }
    }

    private data class ActivityCounts(
        val resumedCount: EvidenceCount = EvidenceCount.ZERO,
        val pausedAwaitingStopCount: EvidenceCount = EvidenceCount.ZERO,
    ) {
        val hasEvidence: Boolean =
            resumedCount.isPositive || pausedAwaitingStopCount.isPositive

        val isVisible: Boolean = resumedCount.isPositive
    }

    private enum class EvidenceCount {
        ZERO,
        ONE,
        MANY,
        ;

        val isPositive: Boolean
            get() = this != ZERO

        fun increment(): EvidenceCount = when (this) {
            ZERO -> ONE
            ONE,
            MANY,
            -> MANY
        }

        fun decrementPossibilities(): Set<EvidenceCount> = when (this) {
            ZERO -> setOf(ZERO)
            ONE -> setOf(ZERO)
            MANY -> setOf(ONE, MANY)
        }
    }
}

internal data class ActivityVisibilitySnapshot(
    val definitePackages: Set<String>,
    val possiblePackages: Set<String>,
) {
    val candidatePackages: Set<String> = definitePackages + possiblePackages

    val assignablePackages: Set<String> = definitePackages

    val hasMultipleCandidatePackages: Boolean = candidatePackages.size > 1

    /**
     * Returns the package to use for a session interval when exactly one package is definitely
     * resumed. Packages supported only by historical, unresolved activity evidence do not make
     * the interval multi-resume.
     */
    fun singleDefinitePackageForSessionOrNull(): String? = definitePackages.singleOrNull()
}
