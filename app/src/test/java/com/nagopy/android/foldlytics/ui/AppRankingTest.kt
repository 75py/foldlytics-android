package com.nagopy.android.foldlytics.ui

import com.nagopy.android.foldlytics.model.AppUsage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class AppRankingTest {
    @Test
    fun ranksLauncherAppsByTotalClassifiedTimeWithStableTieBreakers() {
        val apps = listOf(
            app("zeta", coverMillis = 200L, innerMillis = 100L),
            app("higher-cover", coverMillis = 250L, innerMillis = 50L),
            app("alpha", coverMillis = 200L, innerMillis = 100L),
            app("total-first", coverMillis = 20L, innerMillis = 400L),
            app("zero", coverMillis = 0L, innerMillis = 0L),
            app("system", coverMillis = 1_000L, innerMillis = 1_000L, isLauncherApp = false),
        )

        val ranked = rankAppsForDisplay(apps, AppRankingBasis.TOTAL)

        assertEquals(
            listOf("total-first", "alpha", "higher-cover", "zeta"),
            ranked.map { it.app.packageName },
        )
        assertEquals(listOf(1, 2, 2, 2), ranked.map(RankedAppUsage::rank))
    }

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
            ranked.map { it.app.packageName },
        )
    }

    @Test
    fun ranksLauncherAppsByInnerTimeAndKeepsTiesInStableDisplayOrder() {
        val apps = listOf(
            app("smaller-cover", coverMillis = 20L, innerMillis = 100L),
            app("inner-first", coverMillis = 0L, innerMillis = 300L),
            app("larger-cover", coverMillis = 50L, innerMillis = 100L),
            app("cover-only", coverMillis = 500L, innerMillis = 0L),
        )

        val ranked = rankAppsForDisplay(apps, AppRankingBasis.INNER)

        assertEquals(
            listOf("inner-first", "larger-cover", "smaller-cover"),
            ranked.map { it.app.packageName },
        )
        assertEquals(listOf(1, 2, 2), ranked.map(RankedAppUsage::rank))
    }

    @Test
    fun innerMajorityRanksByInnerTimeInsteadOfShare() {
        val apps = listOf(
            app("long-sixty-percent", coverMillis = 240_000L, innerMillis = 360_000L),
            app("medium-eighty-percent", coverMillis = 30_000L, innerMillis = 120_000L),
            app("brief-one-hundred-percent", coverMillis = 0L, innerMillis = 5_000L),
            app("outer-majority", coverMillis = 600_000L, innerMillis = 300_000L),
        )

        val ranked = rankAppsForDisplayMajority(apps, AppDisplayMajority.INNER)

        assertEquals(
            listOf(
                "long-sixty-percent",
                "medium-eighty-percent",
                "brief-one-hundred-percent",
            ),
            ranked.map { it.app.packageName },
        )
        assertEquals(listOf(1, 2, 3), ranked.map(RankedAppUsage::rank))
    }

    @Test
    fun coverMajorityUsesCoverTimeAndAssignsCompetitionRanks() {
        val apps = listOf(
            app("zeta-tied", coverMillis = 300_000L, innerMillis = 100_000L),
            app("alpha-tied", coverMillis = 300_000L, innerMillis = 299_999L),
            app("third", coverMillis = 120_000L, innerMillis = 30_000L),
            app("inner-majority", coverMillis = 10_000L, innerMillis = 20_000L),
        )

        val ranked = rankAppsForDisplayMajority(apps, AppDisplayMajority.COVER)

        assertEquals(
            listOf("alpha-tied", "zeta-tied", "third"),
            ranked.map { it.app.packageName },
        )
        assertEquals(listOf(1, 1, 3), ranked.map(RankedAppUsage::rank))
    }

    @Test
    fun evenSplitAndUnknownOnlyUsageHaveNoDisplayMajority() {
        val even = app(
            "even",
            coverMillis = 60_000L,
            innerMillis = 60_000L,
            excludedMillis = 600_000L,
        )
        val unknownOnly = app(
            "unknown-only",
            coverMillis = 0L,
            innerMillis = 0L,
            excludedMillis = 600_000L,
        )

        assertEquals(AppDisplayMajority.EVEN, even.displayMajority())
        assertNull(unknownOnly.displayMajority())
        assertEquals(
            emptyList<RankedAppUsage>(),
            rankAppsForDisplayMajority(
                listOf(even, unknownOnly),
                AppDisplayMajority.INNER,
            ),
        )
        assertNull(unknownOnly.innerShareOfClassifiedTime())
        assertNull(unknownOnly.coverShareOfClassifiedTime())
    }

    @Test
    fun unknownTimeDoesNotChangeDisplayMajorityOrShares() {
        val app = app(
            "known-split-with-unknown",
            coverMillis = 40_000L,
            innerMillis = 60_000L,
            excludedMillis = 3_600_000L,
        )

        assertEquals(AppDisplayMajority.INNER, app.displayMajority())
        assertEquals(0.6, app.innerShareOfClassifiedTime() ?: 0.0, 1e-12)
        assertEquals(0.4, app.coverShareOfClassifiedTime() ?: 0.0, 1e-12)
    }

    @Test
    fun sharePresentationKeepsAStrictNearTieVisible() {
        val presentation = requireNotNull(
            app(
                "near-tie",
                coverMillis = 60_001L,
                innerMillis = 60_000L,
            ).displaySharePresentation(),
        )

        assertEquals(DisplayShareValue.MoreThanHalf, presentation.cover)
        assertEquals(DisplayShareValue.LessThanHalf, presentation.inner)
    }

    @Test
    fun sharePresentationDoesNotRoundMeasuredMinorityToZero() {
        val presentation = requireNotNull(
            app(
                "tiny-cover-share",
                coverMillis = 1L,
                innerMillis = 999_999L,
            ).displaySharePresentation(),
        )

        assertEquals(DisplayShareValue.LessThanPointOnePercent, presentation.cover)
        assertEquals(
            DisplayShareValue.MoreThanNinetyNinePointNinePercent,
            presentation.inner,
        )
    }

    @Test
    fun roundedSharePresentationIsComplementaryAndDeterministic() {
        val presentation = requireNotNull(
            app(
                "one-third-cover",
                coverMillis = 1L,
                innerMillis = 2L,
            ).displaySharePresentation(),
        )
        val cover = presentation.cover as DisplayShareValue.RoundedPercent
        val inner = presentation.inner as DisplayShareValue.RoundedPercent

        assertEquals(333, cover.tenthsOfPercent)
        assertEquals(667, inner.tenthsOfPercent)
        assertEquals(1_000, cover.tenthsOfPercent + inner.tenthsOfPercent)
    }

    @Test
    fun evenIsNotASelectableRankingCategory() {
        assertThrows(IllegalArgumentException::class.java) {
            rankAppsForDisplayMajority(emptyList(), AppDisplayMajority.EVEN)
        }
    }

    @Test
    fun homePreviewRanksByTotalTimeKeepsLauncherAppsOnlyReturnsThreeAndOmitsZeroTime() {
        val apps = listOf(
            app("total-second", coverMillis = 100L, innerMillis = 100L),
            app("total-first", coverMillis = 300L, innerMillis = 200L),
            app("total-third", coverMillis = 20L, innerMillis = 30L),
            app("total-fourth", coverMillis = 10L, innerMillis = 20L),
            app("zero-time", coverMillis = 0L, innerMillis = 0L),
            app("system", coverMillis = 10_000L, innerMillis = 10_000L, isLauncherApp = false),
        )

        val ranked = rankAppsForHomePreview(apps)

        assertEquals(
            listOf("total-first", "total-second", "total-third"),
            ranked.map(AppUsage::packageName),
        )
    }

    private fun app(
        packageName: String,
        coverMillis: Long,
        innerMillis: Long,
        excludedMillis: Long = 0L,
        isLauncherApp: Boolean = true,
    ) = AppUsage(
        packageName = packageName,
        label = packageName,
        coverMillis = coverMillis,
        innerMillis = innerMillis,
        excludedMillis = excludedMillis,
        isLauncherApp = isLauncherApp,
    )
}
