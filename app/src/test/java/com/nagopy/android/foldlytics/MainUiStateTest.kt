package com.nagopy.android.foldlytics

import com.nagopy.android.foldlytics.model.AnalysisPeriod
import com.nagopy.android.foldlytics.model.Calibration
import com.nagopy.android.foldlytics.model.DisplayConfiguration
import com.nagopy.android.foldlytics.model.DisplayPosture
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MainUiStateTest {
    private val coverConfiguration = DisplayConfiguration(
        screenWidthDp = 360,
        screenHeightDp = 800,
        smallestScreenWidthDp = 360,
        orientation = 1,
        densityDpi = 420,
    )
    private val innerConfiguration = DisplayConfiguration(
        screenWidthDp = 720,
        screenHeightDp = 830,
        smallestScreenWidthDp = 720,
        orientation = 1,
        densityDpi = 420,
    )
    private val calibration = Calibration(
        cover = coverConfiguration,
        inner = innerConfiguration,
    )
    private val calibratedState = MainUiState(calibration = calibration)

    @Test
    fun classifiesTheLiveConfigurationOutsideMultiWindowMode() {
        val state = calibratedState.withConfiguration(
            configuration = innerConfiguration,
            isInMultiWindowMode = false,
        )

        assertTrue(state.currentConfigurationCanBePostureEvidence)
        assertEquals(DisplayPosture.INNER, state.currentPosture)
    }

    @Test
    fun reportsUnknownForAMultiWindowConfiguration() {
        val state = calibratedState.withConfiguration(
            configuration = innerConfiguration,
            isInMultiWindowMode = true,
        )

        assertFalse(state.currentConfigurationCanBePostureEvidence)
        assertEquals(DisplayPosture.UNKNOWN, state.currentPosture)
    }

    @Test
    fun keepsTheMultiWindowConfigurationVisibleForDiagnostics() {
        val state = calibratedState.withConfiguration(
            configuration = innerConfiguration,
            isInMultiWindowMode = true,
        )

        assertEquals(innerConfiguration, state.currentConfiguration)
    }

    @Test
    fun reloadingCalibrationDoesNotClassifyAMultiWindowConfiguration() {
        val state = calibratedState
            .withConfiguration(configuration = coverConfiguration, isInMultiWindowMode = true)
            .withCalibration(calibration)

        assertEquals(DisplayPosture.UNKNOWN, state.currentPosture)
    }

    @Test
    fun clearingCalibrationDoesNotClassifyAMultiWindowConfiguration() {
        val state = calibratedState
            .withConfiguration(configuration = innerConfiguration, isInMultiWindowMode = true)
            .withCalibration(Calibration())

        assertEquals(DisplayPosture.UNKNOWN, state.currentPosture)
    }

    @Test
    fun leavingMultiWindowModeClassifiesTheLiveConfigurationAgain() {
        val state = calibratedState
            .withConfiguration(configuration = coverConfiguration, isInMultiWindowMode = true)
            .withConfiguration(configuration = coverConfiguration, isInMultiWindowMode = false)

        assertEquals(DisplayPosture.COVER, state.currentPosture)
    }

    @Test
    fun reportsUnknownForAnUnusableConfiguration() {
        val state = calibratedState.withConfiguration(
            configuration = coverConfiguration.copy(screenWidthDp = 0, screenHeightDp = 0),
            isInMultiWindowMode = false,
        )

        assertFalse(state.currentConfigurationCanBePostureEvidence)
        assertEquals(DisplayPosture.UNKNOWN, state.currentPosture)
    }

    @Test
    fun savingCalibrationClassifiesTheLiveConfiguration() {
        val state = MainUiState()
            .withConfiguration(configuration = innerConfiguration, isInMultiWindowMode = false)
            .withCalibration(calibration)

        assertEquals(DisplayPosture.INNER, state.currentPosture)
    }

    @Test
    fun retainsTheSuccessfulSnapshotPeriodWhileAnotherPeriodIsRequested() {
        val state = MainUiState(
            selectedPeriod = AnalysisPeriod.HOURS_1,
            analyzedPeriod = AnalysisPeriod.HOURS_24,
            isAnalysisLoading = true,
        )

        assertEquals(AnalysisPeriod.HOURS_24, state.displayedAnalysisPeriod)
    }
}
