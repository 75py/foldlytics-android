package com.nagopy.android.foldlytics.data

import com.nagopy.android.foldlytics.model.UsageEventKind
import com.nagopy.android.foldlytics.model.UsageRecord

internal class ActivityVisibilityTracker {
    private val activities = linkedMapOf<ActivityKey, ActivityState>()

    val snapshot: ActivityVisibilitySnapshot
        get() {
            val definitePackages = linkedSetOf<String>()
            val possiblePackages = linkedSetOf<String>()
            activities.forEach { (key, state) ->
                when (state) {
                    ActivityState.DEFINITE,
                    ActivityState.DUPLICATE,
                    -> definitePackages += key.packageName

                    ActivityState.UNCERTAIN -> possiblePackages += key.packageName
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
        activities[key] = when (activities[key]) {
            null,
            ActivityState.UNCERTAIN,
            -> ActivityState.DEFINITE

            ActivityState.DEFINITE,
            ActivityState.DUPLICATE,
            -> ActivityState.DUPLICATE
        }
    }

    private fun pauseOrStop(record: UsageRecord) {
        val key = record.activityKeyOrNull() ?: return
        activities[key] = when (activities[key]) {
            null -> return
            ActivityState.DEFINITE -> {
                activities.remove(key)
                return
            }

            ActivityState.DUPLICATE,
            ActivityState.UNCERTAIN,
            -> ActivityState.UNCERTAIN
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

    private enum class ActivityState {
        DEFINITE,
        // Same package/class duplicate resumes still prove the package is visible.
        DUPLICATE,
        // A later pause/stop may or may not leave another same-class instance resumed.
        UNCERTAIN,
    }
}

internal data class ActivityVisibilitySnapshot(
    val definitePackages: Set<String>,
    val possiblePackages: Set<String>,
) {
    val candidatePackages: Set<String> = definitePackages + possiblePackages

    val assignablePackages: Set<String> = definitePackages

    val hasMultipleCandidatePackages: Boolean = candidatePackages.size > 1

    fun exclusiveAssignablePackageOrNull(): String? =
        candidatePackages.singleOrNull()?.takeIf { packageName ->
            packageName in definitePackages
        }
}
