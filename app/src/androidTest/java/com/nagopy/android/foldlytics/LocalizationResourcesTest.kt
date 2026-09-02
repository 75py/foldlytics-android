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
        assertEquals("Most-used apps", context.getString(R.string.home_app_usage_link_title))
        assertEquals("App usage", context.getString(R.string.app_usage_screen_title))
        assertEquals("Total", context.getString(R.string.app_ranking_total))
        assertEquals("Overview", context.getString(R.string.inner_sessions_overview_title))
        assertEquals(
            "Inner-display use per opening",
            context.getString(R.string.inner_sessions_screen_title),
        )
        assertEquals(
            "Inner-display use per opening",
            context.getString(R.string.home_inner_sessions_link_title),
        )
        assertEquals("Inner share", context.getString(R.string.usage_trend_inner_ratio))
        assertEquals("Open count", context.getString(R.string.usage_trend_open_count))
        assertEquals(
            "How this is calculated",
            context.getString(R.string.inner_sessions_method_title),
        )
    }

    @Test
    fun providesJapaneseResources() {
        val context = targetContext.withLocale(Locale.JAPANESE)

        assertEquals("ホーム", context.getString(R.string.nav_home))
        assertEquals("利用サマリー", context.getString(R.string.summary_title))
        assertEquals("外側", context.getString(R.string.posture_cover))
        assertEquals("よく使ったアプリ", context.getString(R.string.home_app_usage_link_title))
        assertEquals("アプリの利用", context.getString(R.string.app_usage_screen_title))
        assertEquals("合計", context.getString(R.string.app_ranking_total))
        assertEquals("利用概要", context.getString(R.string.inner_sessions_overview_title))
        assertEquals(
            "開閉ごとの内側画面利用",
            context.getString(R.string.inner_sessions_screen_title),
        )
        assertEquals(
            "開閉ごとの内側画面利用",
            context.getString(R.string.home_inner_sessions_link_title),
        )
        assertEquals("内側割合", context.getString(R.string.usage_trend_inner_ratio))
        assertEquals("開いた回数", context.getString(R.string.usage_trend_open_count))
        assertEquals("集計方法", context.getString(R.string.inner_sessions_method_title))
    }

    private fun Context.withLocale(locale: Locale): Context {
        val localizedConfiguration = Configuration(resources.configuration).apply {
            setLocale(locale)
        }
        return createConfigurationContext(localizedConfiguration)
    }
}
