package com.nagopy.android.foldlytics.data

import com.nagopy.android.foldlytics.model.DisplayConfiguration

/** Reconstructs the configuration deltas emitted by UsageEvents in timeline order. */
internal class DisplayConfigurationTracker {
    private var configuration: DisplayConfiguration? = null

    fun applyDelta(delta: DisplayConfiguration?): DisplayConfiguration? {
        if (delta == null) {
            reset()
            return null
        }
        val previous = configuration
        // Android Configuration.generateDelta uses zero for unchanged fields. Keep partial
        // evidence even before both dimensions are known, so later deltas can complete it.
        return DisplayConfiguration(
            screenWidthDp = delta.screenWidthDp.updatedOr(previous?.screenWidthDp),
            screenHeightDp = delta.screenHeightDp.updatedOr(previous?.screenHeightDp),
            smallestScreenWidthDp =
                delta.smallestScreenWidthDp.updatedOr(previous?.smallestScreenWidthDp),
            orientation = delta.orientation.updatedOr(previous?.orientation),
            densityDpi = delta.densityDpi.updatedOr(previous?.densityDpi),
        ).also { configuration = it }
    }

    fun replaceBaseline(snapshot: DisplayConfiguration): DisplayConfiguration {
        // Checkpoints contain snapshots, including unavailable fields, rather than deltas.
        configuration = snapshot
        return snapshot
    }

    fun reset() {
        configuration = null
    }

    private fun Int.updatedOr(previous: Int?): Int = takeIf { it > 0 } ?: previous ?: 0
}
