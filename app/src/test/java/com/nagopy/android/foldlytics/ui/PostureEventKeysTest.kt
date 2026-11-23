package com.nagopy.android.foldlytics.ui

import com.nagopy.android.foldlytics.model.DisplayPosture
import com.nagopy.android.foldlytics.model.PostureCheckpointSource
import com.nagopy.android.foldlytics.model.PostureEvent
import com.nagopy.android.foldlytics.model.PostureEventSource
import org.junit.Assert.assertEquals
import org.junit.Test

/** Diagnostics list keys must follow events, not positions, so a refresh keeps the scroll. */
class PostureEventKeysTest {
    @Test
    fun prependingNewerEventsKeepsTheKeysOfExistingEvents() {
        // Newest first, as the analyzer lists them, with a same-millisecond duplicate.
        val existing = listOf(
            event(3_000L),
            event(2_000L),
            event(2_000L),
            event(2_000L, PostureEventSource.CHECKPOINT, PostureCheckpointSource.APP_LAUNCH),
            event(1_000L),
        )
        val refreshed = listOf(event(5_000L), event(4_000L)) + existing

        val keys = postureEventKeys(existing)
        val refreshedKeys = postureEventKeys(refreshed)

        assertEquals(keys, refreshedKeys.drop(2))
        assertEquals(existing.size, keys.toSet().size)
        assertEquals(refreshed.size, refreshedKeys.toSet().size)
    }

    @Test
    fun eventMovingFromTheRangeIntoTheLookbackKeepsItsKey() {
        val inRange = listOf(event(2_000L), event(1_000L))
        val moved = listOf(event(3_000L), event(2_000L, isBeforeRange = true), event(1_000L, isBeforeRange = true))

        assertEquals(postureEventKeys(inRange), postureEventKeys(moved).drop(1))
    }

    @Test
    fun duplicatesAreToldApartByTheirOrderFromTheOldest() {
        val duplicates = List(3) { event(2_000L) }

        val keys = postureEventKeys(duplicates)

        assertEquals(3, keys.toSet().size)
        // A newer duplicate at the same millisecond takes a new key; the older ones keep theirs.
        assertEquals(keys, postureEventKeys(listOf(event(2_000L)) + duplicates).drop(1))
    }

    private fun event(
        timestampMillis: Long,
        source: PostureEventSource = PostureEventSource.CONFIGURATION_CHANGE,
        checkpointSource: PostureCheckpointSource? = null,
        isBeforeRange: Boolean = false,
    ) = PostureEvent(
        timestampMillis = timestampMillis,
        source = source,
        posture = DisplayPosture.COVER,
        configuration = null,
        checkpointSource = checkpointSource,
        rawEventType = if (source == PostureEventSource.CONFIGURATION_CHANGE) 5 else null,
        isBeforeRange = isBeforeRange,
    )
}
