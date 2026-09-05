package com.nagopy.android.foldlytics.data

import com.nagopy.android.foldlytics.model.DisplayConfiguration
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class DisplayConfigurationTrackerTest {
    private val inner = DisplayConfiguration(730, 820, 730, 1, 420)
    private val emptyDelta = DisplayConfiguration(0, 0, 0, 0, 0)

    @Test
    fun mergesEachChangedFieldAndPreservesOmittedFields() {
        val tracker = DisplayConfigurationTracker()
        tracker.applyDelta(inner)
        tracker.applyDelta(emptyDelta.copy(screenWidthDp = 750, densityDpi = 390))
        tracker.applyDelta(emptyDelta.copy(screenHeightDp = 840, smallestScreenWidthDp = 700))
        tracker.applyDelta(emptyDelta.copy(orientation = 2))

        assertEquals(DisplayConfiguration(750, 840, 700, 2, 390), tracker.applyDelta(emptyDelta))
    }

    @Test
    fun accumulatesPartialEvidenceWithoutInventingMissingDimensions() {
        val tracker = DisplayConfigurationTracker()

        assertFalse(requireNotNull(tracker.applyDelta(emptyDelta)).isUsable())
        assertFalse(requireNotNull(tracker.applyDelta(emptyDelta.copy(screenWidthDp = 730))).isUsable())
        assertEquals(
            DisplayConfiguration(730, 820, 0, 0, 0),
            tracker.applyDelta(emptyDelta.copy(screenHeightDp = 820)),
        )
    }

    @Test
    fun nullAndResetDiscardEveryPreviouslyKnownField() {
        val tracker = DisplayConfigurationTracker()
        tracker.applyDelta(inner)

        assertNull(tracker.applyDelta(null))
        assertEquals(emptyDelta, tracker.applyDelta(emptyDelta))

        tracker.applyDelta(inner)
        tracker.reset()

        assertEquals(emptyDelta, tracker.applyDelta(emptyDelta))
    }

    @Test
    fun checkpointReplacesOmittedFieldsInsteadOfInheritingThem() {
        val tracker = DisplayConfigurationTracker()
        tracker.applyDelta(inner)
        val snapshot = DisplayConfiguration(420, 890, 0, 0, 0)

        assertEquals(snapshot, tracker.replaceBaseline(snapshot))
        assertEquals(snapshot, tracker.applyDelta(emptyDelta))

        tracker.replaceBaseline(emptyDelta.copy(screenWidthDp = 500))

        assertEquals(emptyDelta.copy(screenWidthDp = 500), tracker.applyDelta(emptyDelta))
        assertFalse(requireNotNull(tracker.applyDelta(emptyDelta)).isUsable())
    }
}
