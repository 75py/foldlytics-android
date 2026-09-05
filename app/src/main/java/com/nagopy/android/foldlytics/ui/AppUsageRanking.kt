package com.nagopy.android.foldlytics.ui

import com.nagopy.android.foldlytics.model.AppUsage
import kotlin.math.roundToInt

internal enum class AppRankingBasis {
    TOTAL,
    COVER,
    INNER,
    ;

    fun selectedMillis(app: AppUsage): Long = when (this) {
        TOTAL -> app.classifiedMillis
        COVER -> app.coverMillis
        INNER -> app.innerMillis
    }
}

internal enum class AppRankingView {
    USAGE_TIME,
    DISPLAY_SHARE,
}

internal enum class AppDisplayMajority {
    COVER,
    INNER,
    EVEN,
}

internal data class RankedAppUsage(
    val app: AppUsage,
    val rank: Int,
    val rankingMillis: Long,
)

internal data class AppDisplayShares(
    val cover: Double,
    val inner: Double,
)

internal sealed interface DisplayShareValue {
    data class RoundedPercent(val tenthsOfPercent: Int) : DisplayShareValue

    data object LessThanHalf : DisplayShareValue

    data object MoreThanHalf : DisplayShareValue

    data object LessThanPointOnePercent : DisplayShareValue

    data object MoreThanNinetyNinePointNinePercent : DisplayShareValue
}

internal data class AppDisplaySharePresentation(
    val shares: AppDisplayShares,
    val cover: DisplayShareValue,
    val inner: DisplayShareValue,
)

/**
 * Ranks launchable apps by the selected measured duration. A ratio is deliberately not used for
 * ranking, so a brief inner-only use cannot outrank a longer measured inner-display use.
 *
 * Apps with the same measured duration share the same competition rank. Labels and package names
 * determine only their stable display order within that tie.
 */
internal fun rankAppsForDisplay(
    apps: List<AppUsage>,
    basis: AppRankingBasis,
): List<RankedAppUsage> {
    val orderedApps = apps
        .asSequence()
        .filter { it.isLauncherApp && basis.selectedMillis(it) > 0L }
        .sortedWith(
            compareByDescending<AppUsage> { basis.selectedMillis(it) }
                .thenBy { it.label }
                .thenBy { it.packageName },
        )
        .toList()

    var previousMillis: Long? = null
    var previousRank = 0
    return orderedApps.mapIndexed { index, app ->
        val rankingMillis = basis.selectedMillis(app)
        val rank = if (rankingMillis == previousMillis) previousRank else index + 1
        previousMillis = rankingMillis
        previousRank = rank
        RankedAppUsage(
            app = app,
            rank = rank,
            rankingMillis = rankingMillis,
        )
    }
}

/**
 * Selects apps whose classified usage has a strict majority on [majority], then ranks them by
 * measured time on that display. An even split and display-undetermined time do not qualify for
 * either list.
 */
internal fun rankAppsForDisplayMajority(
    apps: List<AppUsage>,
    majority: AppDisplayMajority,
): List<RankedAppUsage> {
    require(majority != AppDisplayMajority.EVEN) {
        "An even display split is not a ranking category"
    }
    val basis = when (majority) {
        AppDisplayMajority.COVER -> AppRankingBasis.COVER
        AppDisplayMajority.INNER -> AppRankingBasis.INNER
        AppDisplayMajority.EVEN -> error("Handled above")
    }
    return rankAppsForDisplay(
        apps = apps.filter { it.displayMajority() == majority },
        basis = basis,
    )
}

internal fun AppUsage.displayMajority(): AppDisplayMajority? {
    if (classifiedMillis <= 0L) return null
    return when {
        coverMillis > innerMillis -> AppDisplayMajority.COVER
        innerMillis > coverMillis -> AppDisplayMajority.INNER
        else -> AppDisplayMajority.EVEN
    }
}

/** Unknown display time is intentionally outside this denominator. */
internal fun AppUsage.displaySharesOfClassifiedTime(): AppDisplayShares? {
    val classified = coverMillis.toDouble() + innerMillis.toDouble()
    if (classified <= 0.0) return null
    return AppDisplayShares(
        cover = coverMillis.toDouble() / classified,
        inner = innerMillis.toDouble() / classified,
    )
}

/**
 * Produces complementary one-decimal percentages without obscuring a strict majority or a
 * non-zero minority at the rounding boundaries.
 */
internal fun AppUsage.displaySharePresentation(): AppDisplaySharePresentation? {
    val shares = displaySharesOfClassifiedTime() ?: return null
    val coverTenths = (shares.cover * PERCENT_TENTHS_TOTAL)
        .roundToInt()
        .coerceIn(0, PERCENT_TENTHS_TOTAL)
    val innerTenths = PERCENT_TENTHS_TOTAL - coverTenths
    return AppDisplaySharePresentation(
        shares = shares,
        cover = displayShareValue(
            millis = coverMillis,
            counterpartMillis = innerMillis,
            tenthsOfPercent = coverTenths,
        ),
        inner = displayShareValue(
            millis = innerMillis,
            counterpartMillis = coverMillis,
            tenthsOfPercent = innerTenths,
        ),
    )
}

private fun displayShareValue(
    millis: Long,
    counterpartMillis: Long,
    tenthsOfPercent: Int,
): DisplayShareValue = when {
    tenthsOfPercent == HALF_PERCENT_TENTHS && millis > counterpartMillis ->
        DisplayShareValue.MoreThanHalf
    tenthsOfPercent == HALF_PERCENT_TENTHS && millis < counterpartMillis ->
        DisplayShareValue.LessThanHalf
    tenthsOfPercent == 0 && millis > 0L -> DisplayShareValue.LessThanPointOnePercent
    tenthsOfPercent == PERCENT_TENTHS_TOTAL && counterpartMillis > 0L ->
        DisplayShareValue.MoreThanNinetyNinePointNinePercent
    else -> DisplayShareValue.RoundedPercent(tenthsOfPercent)
}

private const val PERCENT_TENTHS_TOTAL = 1_000
private const val HALF_PERCENT_TENTHS = 500

internal fun AppUsage.innerShareOfClassifiedTime(): Double? =
    displaySharesOfClassifiedTime()?.inner

/** Unknown display time is intentionally outside this denominator. */
internal fun AppUsage.coverShareOfClassifiedTime(): Double? =
    displaySharesOfClassifiedTime()?.cover
