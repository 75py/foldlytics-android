package com.nagopy.android.foldlytics.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class DeviceNameFormatterTest {
    @Test
    fun formatsManufacturerAndModelWithoutDuplicatingTheManufacturer() {
        assertEquals("Google Pixel Fold", formatDeviceName("Google", "Pixel Fold"))
        assertEquals("Samsung SM-F9560", formatDeviceName("samsung", "SM-F9560"))
        assertEquals("HUAWEI Mate X6", formatDeviceName("HUAWEI", "HUAWEI Mate X6"))
        assertEquals(
            "Samsung Galaxy Z Fold",
            formatDeviceName("samsung", "samsung Galaxy Z Fold"),
        )
    }
}
