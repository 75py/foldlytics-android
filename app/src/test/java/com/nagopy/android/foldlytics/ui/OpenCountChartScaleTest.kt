package com.nagopy.android.foldlytics.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class OpenCountChartScaleTest {
    @Test
    fun usesAnEvenIntegerScaleForZeroAndOne() {
        assertEquals(OpenCountChartScale(maximum = 2L, middle = 1L), openCountChartScale(0))
        assertEquals(OpenCountChartScale(maximum = 2L, middle = 1L), openCountChartScale(1))
    }

    @Test
    fun roundsOddMaximumUpSoTheMiddleTickIsInteger() {
        assertEquals(OpenCountChartScale(maximum = 4L, middle = 2L), openCountChartScale(3))
        assertEquals(OpenCountChartScale(maximum = 100L, middle = 50L), openCountChartScale(99))
        assertEquals(OpenCountChartScale(maximum = 100L, middle = 50L), openCountChartScale(100))
    }

    @Test
    fun promotesIntMaximumWithoutOverflow() {
        assertEquals(
            OpenCountChartScale(maximum = 2_147_483_648L, middle = 1_073_741_824L),
            openCountChartScale(Int.MAX_VALUE),
        )
    }
}
