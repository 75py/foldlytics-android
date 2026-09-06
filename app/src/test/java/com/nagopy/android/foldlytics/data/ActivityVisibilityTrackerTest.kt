package com.nagopy.android.foldlytics.data

import com.nagopy.android.foldlytics.model.UsageEventKind
import com.nagopy.android.foldlytics.model.UsageRecord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ActivityVisibilityTrackerTest {
    private val tracker = ActivityVisibilityTracker()

    @Test
    fun sameClassDuplicateResumeKeepsPackageAssignableUntilTerminalEvent() {
        tracker.apply(record(UsageEventKind.ACTIVITY_RESUMED))
        tracker.apply(record(UsageEventKind.ACTIVITY_RESUMED))

        assertEquals(setOf("app.a"), tracker.snapshot.assignablePackages)
        assertEquals("app.a", tracker.snapshot.singleDefinitePackageForSessionOrNull())

        tracker.apply(record(UsageEventKind.ACTIVITY_PAUSED))

        assertEquals(emptySet<String>(), tracker.snapshot.assignablePackages)
        assertEquals(setOf("app.a"), tracker.snapshot.possiblePackages)
        assertNull(tracker.snapshot.singleDefinitePackageForSessionOrNull())
    }

    @Test
    fun pauseFollowedByStopKeepsDuplicateActivityUncertain() {
        tracker.apply(record(UsageEventKind.ACTIVITY_RESUMED))
        tracker.apply(record(UsageEventKind.ACTIVITY_RESUMED))
        tracker.apply(record(UsageEventKind.ACTIVITY_PAUSED))
        tracker.apply(record(UsageEventKind.ACTIVITY_STOPPED))

        assertEquals(emptySet<String>(), tracker.snapshot.assignablePackages)
        assertEquals(setOf("app.a"), tracker.snapshot.possiblePackages)
        assertNull(tracker.snapshot.singleDefinitePackageForSessionOrNull())
    }

    @Test
    fun pauseFollowedByStopAfterSingleResumeBecomesInactive() {
        tracker.apply(record(UsageEventKind.ACTIVITY_RESUMED))
        tracker.apply(record(UsageEventKind.ACTIVITY_PAUSED))
        tracker.apply(record(UsageEventKind.ACTIVITY_STOPPED))

        assertTrue(tracker.snapshot.candidatePackages.isEmpty())
    }

    @Test
    fun uncertainPackageDoesNotBlockSingleDefiniteSessionAllocation() {
        tracker.apply(record(UsageEventKind.ACTIVITY_RESUMED, packageName = "app.a"))
        tracker.apply(record(UsageEventKind.ACTIVITY_RESUMED, packageName = "app.a"))
        tracker.apply(record(UsageEventKind.ACTIVITY_PAUSED, packageName = "app.a"))
        tracker.apply(record(UsageEventKind.ACTIVITY_RESUMED, packageName = "app.b"))

        assertEquals(setOf("app.b"), tracker.snapshot.assignablePackages)
        assertEquals(setOf("app.a", "app.b"), tracker.snapshot.candidatePackages)
        assertTrue(tracker.snapshot.hasMultipleCandidatePackages)
        assertEquals("app.b", tracker.snapshot.singleDefinitePackageForSessionOrNull())
    }

    @Test
    fun samePackageDefiniteActivityCanReceiveTimeDespiteAnotherUncertainClass() {
        tracker.apply(record(UsageEventKind.ACTIVITY_RESUMED, className = "A"))
        tracker.apply(record(UsageEventKind.ACTIVITY_RESUMED, className = "A"))
        tracker.apply(record(UsageEventKind.ACTIVITY_PAUSED, className = "A"))
        tracker.apply(record(UsageEventKind.ACTIVITY_RESUMED, className = "B"))

        assertEquals(setOf("app.a"), tracker.snapshot.assignablePackages)
        assertEquals(setOf("app.a"), tracker.snapshot.candidatePackages)
        assertFalse(tracker.snapshot.hasMultipleCandidatePackages)
        assertEquals("app.a", tracker.snapshot.singleDefinitePackageForSessionOrNull())
    }

    @Test
    fun resumedEventRestoresDefiniteVisibilityButKeepsLatentAmbiguity() {
        tracker.apply(record(UsageEventKind.ACTIVITY_RESUMED))
        tracker.apply(record(UsageEventKind.ACTIVITY_RESUMED))
        tracker.apply(record(UsageEventKind.ACTIVITY_PAUSED))
        tracker.apply(record(UsageEventKind.ACTIVITY_RESUMED))

        assertEquals(setOf("app.a"), tracker.snapshot.assignablePackages)
        assertEquals("app.a", tracker.snapshot.singleDefinitePackageForSessionOrNull())

        tracker.apply(record(UsageEventKind.ACTIVITY_PAUSED))

        assertEquals(emptySet<String>(), tracker.snapshot.assignablePackages)
        assertEquals(setOf("app.a"), tracker.snapshot.possiblePackages)
        assertNull(tracker.snapshot.singleDefinitePackageForSessionOrNull())
    }

    @Test
    fun stopAfterPausedPredecessorAndNewResumeLeavesPackageUncertain() {
        tracker.apply(record(UsageEventKind.ACTIVITY_RESUMED))
        tracker.apply(record(UsageEventKind.ACTIVITY_PAUSED))
        tracker.apply(record(UsageEventKind.ACTIVITY_RESUMED))
        tracker.apply(record(UsageEventKind.ACTIVITY_STOPPED))

        assertEquals(emptySet<String>(), tracker.snapshot.assignablePackages)
        assertEquals(setOf("app.a"), tracker.snapshot.possiblePackages)
        assertNull(tracker.snapshot.singleDefinitePackageForSessionOrNull())
    }

    @Test
    fun pauseAfterPausedPredecessorAndNewResumeClearsVisiblePackage() {
        tracker.apply(record(UsageEventKind.ACTIVITY_RESUMED))
        tracker.apply(record(UsageEventKind.ACTIVITY_PAUSED))
        tracker.apply(record(UsageEventKind.ACTIVITY_RESUMED))
        tracker.apply(record(UsageEventKind.ACTIVITY_PAUSED))

        assertTrue(tracker.snapshot.candidatePackages.isEmpty())
    }

    @Test
    fun resumePauseResumeAtRepeatedTimestampLeavesPackageAssignable() {
        listOf(
            record(UsageEventKind.ACTIVITY_RESUMED, sequenceAtTimestamp = 0),
            record(UsageEventKind.ACTIVITY_PAUSED, sequenceAtTimestamp = 1),
            record(UsageEventKind.ACTIVITY_RESUMED, sequenceAtTimestamp = 2),
        ).sortedBy(UsageRecord::sequenceAtTimestamp)
            .forEach(tracker::apply)

        assertEquals(setOf("app.a"), tracker.snapshot.assignablePackages)
        assertEquals("app.a", tracker.snapshot.singleDefinitePackageForSessionOrNull())
    }

    @Test
    fun longSameClassHistoryKeepsBoundedEvidenceState() {
        repeat(10_000) {
            tracker.apply(record(UsageEventKind.ACTIVITY_RESUMED))
        }
        assertTrue(tracker.possibleStateCount <= 9)
        assertEquals(setOf("app.a"), tracker.snapshot.assignablePackages)

        repeat(10_000) { index ->
            val kind = if (index % 2 == 0) {
                UsageEventKind.ACTIVITY_PAUSED
            } else {
                UsageEventKind.ACTIVITY_STOPPED
            }
            tracker.apply(record(kind))
        }

        assertTrue(tracker.possibleStateCount <= 9)
    }

    @Test
    fun resetClearsUncertainEvidence() {
        tracker.apply(record(UsageEventKind.ACTIVITY_RESUMED))
        tracker.apply(record(UsageEventKind.ACTIVITY_RESUMED))
        tracker.apply(record(UsageEventKind.ACTIVITY_PAUSED))

        tracker.reset()

        assertTrue(tracker.snapshot.candidatePackages.isEmpty())
    }

    private fun record(
        kind: UsageEventKind,
        packageName: String = "app.a",
        className: String = "A",
        sequenceAtTimestamp: Int = 0,
    ) = UsageRecord(
        timestampMillis = 0L,
        kind = kind,
        packageName = packageName,
        className = className,
        rawEventType = 0,
        sequenceAtTimestamp = sequenceAtTimestamp,
    )
}
