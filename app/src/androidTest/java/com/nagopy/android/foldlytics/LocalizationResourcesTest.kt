package com.nagopy.android.foldlytics

import android.content.Context
import android.content.res.Configuration
import androidx.test.platform.app.InstrumentationRegistry
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test

class LocalizationResourcesTest {
    private val targetContext: Context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun usesEnglishBaseResourcesForUnsupportedLocales() {
        val context = targetContext.withLocale(Locale.FRENCH)

        assertEquals("Home", context.getString(R.string.nav_home))
        assertEquals("Usage summary", context.getString(R.string.summary_title))
        assertEquals("Outer", context.getString(R.string.posture_cover))
    }

    @Test
    fun providesJapaneseResources() {
        val context = targetContext.withLocale(Locale.JAPANESE)

        assertEquals("ホーム", context.getString(R.string.nav_home))
        assertEquals("利用サマリー", context.getString(R.string.summary_title))
        assertEquals("外側", context.getString(R.string.posture_cover))
    }

    private fun Context.withLocale(locale: Locale): Context {
        val localizedConfiguration = Configuration(resources.configuration).apply {
            setLocale(locale)
        }
        return createConfigurationContext(localizedConfiguration)
    }
}
