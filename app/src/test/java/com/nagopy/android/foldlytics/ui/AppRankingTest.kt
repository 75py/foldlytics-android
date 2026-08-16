package com.nagopy.android.foldlytics.ui

import com.nagopy.android.foldlytics.model.AppUsage
import org.junit.Assert.assertEquals
import org.junit.Test

class AppRankingTest {
    @Test
    fun ranksLauncherAppsByCoverTimeAndOmitsAppsWithoutCoverTime() {
        val apps = listOf(
            app("inner-first", coverMillis = 20L, innerMillis = 300L),
            app("cover-first", coverMillis = 200L, innerMillis = 10L),
            app("cover-second", coverMillis = 100L, innerMillis = 50L),
            app("inner-only", coverMillis = 0L, innerMillis = 500L),
            app("system", coverMillis = 1_000L, innerMillis = 1_000L, isLauncherApp = false),
        )

        val ranked = rankAppsForDisplay(apps, AppRankingBasis.COVER)

        assertEquals(
            listOf("cover-first", "cover-second", "inner-first"),
            ranked.map(AppUsage::packageName),
        )
    }

    @Test
    fun ranksLauncherAppsByInnerTimeAndUsesCoverTimeAsTieBreaker() {
        val apps = listOf(
            app("smaller-cover", coverMillis = 20L, innerMillis = 100L),
            app("inner-first", coverMillis = 0L, innerMillis = 300L),
            app("larger-cover", coverMillis = 50L, innerMillis = 100L),
            app("cover-only", coverMillis = 500L, innerMillis = 0L),
        )

        val ranked = rankAppsForDisplay(apps, AppRankingBasis.INNER)

        assertEquals(
            listOf("inner-first", "larger-cover", "smaller-cover"),
            ranked.map(AppUsage::packageName),
        )
    }

    private fun app(
        packageName: String,
        coverMillis: Long,
        innerMillis: Long,
        isLauncherApp: Boolean = true,
    ) = AppUsage(
        packageName = packageName,
        label = packageName,
        coverMillis = coverMillis,
        innerMillis = innerMillis,
        excludedMillis = 0L,
        isLauncherApp = isLauncherApp,
    )
}
