package com.nagopy.android.foldlytics.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CalibrationTest {
    private val cover = configuration(width = 420, height = 890, smallest = 420)

    @Test
    fun rejectsIdenticalOppositeAnchor() {
        val result = Calibration(cover = cover).withAnchor(CalibrationAnchor.INNER, cover)

        assertEquals(
            CalibrationUpdateResult.Rejected(
                CalibrationValidationFailure.ANCHORS_TOO_CLOSE,
            ),
            result,
        )
    }

    @Test
    fun rejectsRotatedDuplicateAnchor() {
        val rotated = configuration(
            width = 890,
            height = 420,
            smallest = 420,
            orientation = 2,
        )

        val result = Calibration(cover = cover).withAnchor(CalibrationAnchor.INNER, rotated)

        assertEquals(
            CalibrationUpdateResult.Rejected(
                CalibrationValidationFailure.ANCHORS_TOO_CLOSE,
            ),
            result,
        )
    }

    @Test
    fun rejectsAnchorsWithinIntegerDpResolution() {
        val almostSame = configuration(width = 421, height = 891, smallest = 421)

        val result = Calibration(cover = cover).withAnchor(
            CalibrationAnchor.INNER,
            almostSame,
        )

        assertEquals(
            CalibrationUpdateResult.Rejected(
                CalibrationValidationFailure.ANCHORS_TOO_CLOSE,
            ),
            result,
        )
    }

    @Test
    fun acceptsNearbyAnchorBeyondIntegerDpResolution() {
        val distinguishable = configuration(width = 422, height = 890, smallest = 420)

        val result = Calibration(cover = cover).withAnchor(
            CalibrationAnchor.INNER,
            distinguishable,
        )

        assertEquals(
            CalibrationUpdateResult.Accepted(
                Calibration(cover = cover, inner = distinguishable),
            ),
            result,
        )
    }

    @Test
    fun acceptsEffectiveSmallestWidthAsSoleDistinguishingDimension() {
        val distinguishable = configuration(width = 420, height = 890, smallest = 422)

        val result = Calibration(cover = cover).withAnchor(
            CalibrationAnchor.INNER,
            distinguishable,
        )

        assertEquals(
            CalibrationUpdateResult.Accepted(
                Calibration(cover = cover, inner = distinguishable),
            ),
            result,
        )
    }

    @Test
    fun undefinedSmallestWidthUsesShortSideForAnchorValidation() {
        val withoutSmallestWidth = configuration(width = 420, height = 890, smallest = 0)

        val result = Calibration(cover = cover).withAnchor(
            CalibrationAnchor.INNER,
            withoutSmallestWidth,
        )

        assertEquals(
            CalibrationUpdateResult.Rejected(
                CalibrationValidationFailure.ANCHORS_TOO_CLOSE,
            ),
            result,
        )
    }

    @Test
    fun rejectsUnavailableIncomingAnchor() {
        val unavailable = configuration(width = 0, height = 0, smallest = 0)

        val result = Calibration(cover = cover).withAnchor(
            CalibrationAnchor.INNER,
            unavailable,
        )

        assertEquals(
            CalibrationUpdateResult.Rejected(
                CalibrationValidationFailure.CONFIGURATION_UNAVAILABLE,
            ),
            result,
        )
    }

    @Test
    fun invalidStoredAnchorsUseAutomaticClassificationInsteadOfCoverTieBreak() {
        val invalidCalibration = Calibration(cover = cover, inner = cover)
        val historicalInner = configuration(width = 730, height = 820, smallest = 730)

        val result = invalidCalibration.classifyWithDetails(historicalInner)

        assertFalse(invalidCalibration.isComplete)
        assertEquals(
            CalibrationValidationFailure.ANCHORS_TOO_CLOSE,
            invalidCalibration.validationFailure,
        )
        assertEquals(DisplayPosture.INNER, result.posture)
        assertEquals(null, result.coverDistance)
        assertEquals(null, result.innerDistance)
    }

    @Test
    fun validAnchorsStillUseNearestAnchorClassification() {
        val inner = configuration(width = 730, height = 820, smallest = 730)
        val calibration = Calibration(cover = cover, inner = inner)

        assertTrue(calibration.isComplete)
        assertEquals(DisplayPosture.COVER, calibration.classify(cover))
        assertEquals(DisplayPosture.INNER, calibration.classify(inner))
    }

    @Test
    fun allowsUsableFullScreenConfigurationAsPostureEvidence() {
        assertTrue(cover.canBePostureEvidence(isInMultiWindowMode = false))
    }

    @Test
    fun rejectsUsableMultiWindowConfigurationAsPostureEvidence() {
        val innerSplitScreenWindow = configuration(width = 380, height = 900, smallest = 380)

        assertFalse(innerSplitScreenWindow.canBePostureEvidence(isInMultiWindowMode = true))
    }

    private fun configuration(
        width: Int,
        height: Int,
        smallest: Int,
        orientation: Int = 1,
    ) = DisplayConfiguration(
        screenWidthDp = width,
        screenHeightDp = height,
        smallestScreenWidthDp = smallest,
        orientation = orientation,
        densityDpi = 420,
    )
}
