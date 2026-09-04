package com.nagopy.android.foldlytics.data

import com.nagopy.android.foldlytics.model.Calibration
import com.nagopy.android.foldlytics.model.CalibrationUpdateResult
import com.nagopy.android.foldlytics.model.CalibrationValidationFailure
import com.nagopy.android.foldlytics.model.DisplayConfiguration
import org.junit.Assert.assertEquals
import org.junit.Test

class CalibrationStoreTest {
    private val cover = configuration(width = 420, height = 890, smallest = 420)
    private val inner = configuration(width = 730, height = 820, smallest = 730)

    @Test
    fun identicalReplacementIsRejectedWithoutChangingStoredCalibration() {
        val original = Calibration(cover = cover, inner = inner)
        val persistence = FakeCalibrationPersistence(original)
        val store = CalibrationStore(persistence)

        val result = store.saveCover(inner)

        assertEquals(
            CalibrationUpdateResult.Rejected(
                CalibrationValidationFailure.ANCHORS_TOO_CLOSE,
            ),
            result,
        )
        assertEquals(original, persistence.storedCalibration)
        assertEquals(0, persistence.saveCount)
    }

    @Test
    fun distinguishableNearbyAnchorIsSaved() {
        val nearbyInner = configuration(width = 422, height = 890, smallest = 420)
        val persistence = FakeCalibrationPersistence(Calibration(cover = cover))
        val store = CalibrationStore(persistence)

        val result = store.saveInner(nearbyInner)

        val expected = Calibration(cover = cover, inner = nearbyInner)
        assertEquals(CalibrationUpdateResult.Accepted(expected), result)
        assertEquals(expected, persistence.storedCalibration)
        assertEquals(1, persistence.saveCount)
    }

    @Test
    fun unusableStoredAnchorDoesNotBlockValidReplacement() {
        val unusableCover = configuration(width = 0, height = 0, smallest = 0)
        val persistence = FakeCalibrationPersistence(Calibration(cover = unusableCover))
        val store = CalibrationStore(persistence)

        assertEquals(Calibration(), store.load())

        val result = store.saveInner(inner)

        val expected = Calibration(inner = inner)
        assertEquals(CalibrationUpdateResult.Accepted(expected), result)
        assertEquals(expected, persistence.storedCalibration)
        assertEquals(1, persistence.saveCount)
    }

    @Test
    fun invalidStoredAnchorsDoNotReuseCalibratedSummaryCache() {
        val invalidCalibration = Calibration(cover = cover, inner = cover)

        assertEquals("cover=none|inner=none", invalidCalibration.dailySummaryCacheKey())
        assertEquals(
            Calibration().dailySummaryCacheKey(),
            invalidCalibration.dailySummaryCacheKey(),
        )
    }

    @Test
    fun partialCalibrationKeepsItsExistingSummaryCacheIdentity() {
        val partialCalibration = Calibration(cover = cover)

        assertEquals(
            "cover=420,890,420,1,420|inner=none",
            partialCalibration.dailySummaryCacheKey(),
        )
    }

    private fun configuration(
        width: Int,
        height: Int,
        smallest: Int,
    ) = DisplayConfiguration(
        screenWidthDp = width,
        screenHeightDp = height,
        smallestScreenWidthDp = smallest,
        orientation = 1,
        densityDpi = 420,
    )

    private class FakeCalibrationPersistence(
        var storedCalibration: Calibration,
    ) : CalibrationPersistence {
        var saveCount: Int = 0

        override fun load(): Calibration = storedCalibration

        override fun save(calibration: Calibration) {
            storedCalibration = calibration
            saveCount += 1
        }

        override fun clear() {
            storedCalibration = Calibration()
        }
    }
}
