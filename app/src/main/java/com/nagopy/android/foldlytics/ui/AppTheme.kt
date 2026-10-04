@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.nagopy.android.foldlytics.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.intl.Locale
import androidx.compose.ui.text.intl.LocaleList
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Paper and ink: a warm page, navy ink, and the two panel colors of the launcher icon.
private val LightColors = lightColorScheme(
    primary = Color(0xFF14315E),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFD9E4F7),
    onPrimaryContainer = Color(0xFF0B1E3D),
    inversePrimary = Color(0xFFA9C7FF),
    secondary = Color(0xFF9A4300),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFFFDCC6),
    onSecondaryContainer = Color(0xFF331200),
    tertiary = Color(0xFF0067A5),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFCFE5FF),
    onTertiaryContainer = Color(0xFF001D33),
    error = Color(0xFFA8321F),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD4),
    onErrorContainer = Color(0xFF410002),
    background = Color(0xFFFAF5EC),
    onBackground = Color(0xFF1A1F2B),
    surface = Color(0xFFFAF5EC),
    onSurface = Color(0xFF1A1F2B),
    surfaceVariant = Color(0xFFE6DECF),
    onSurfaceVariant = Color(0xFF575B66),
    surfaceTint = Color(0xFF14315E),
    inverseSurface = Color(0xFF12233F),
    inverseOnSurface = Color(0xFFF6EFE3),
    outline = Color(0xFF8A8576),
    outlineVariant = Color(0xFFD9D0BE),
    scrim = Color(0xFF0A1220),
    surfaceBright = Color(0xFFFFFBF4),
    surfaceDim = Color(0xFFE2D9C8),
    surfaceContainerLowest = Color(0xFFFFFDF9),
    surfaceContainerLow = Color(0xFFF5EFE3),
    surfaceContainer = Color(0xFFEFE8DA),
    surfaceContainerHigh = Color(0xFFE9E1D1),
    surfaceContainerHighest = Color(0xFFE2D9C8),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFA9C7FF),
    onPrimary = Color(0xFF0A2A57),
    primaryContainer = Color(0xFF1F3E6E),
    onPrimaryContainer = Color(0xFFD9E4F7),
    inversePrimary = Color(0xFF14315E),
    secondary = Color(0xFFFFB077),
    onSecondary = Color(0xFF4F2100),
    secondaryContainer = Color(0xFF6B2F00),
    onSecondaryContainer = Color(0xFFFFDCC6),
    tertiary = Color(0xFF73C7FF),
    onTertiary = Color(0xFF00344F),
    tertiaryContainer = Color(0xFF004B73),
    onTertiaryContainer = Color(0xFFCFE5FF),
    error = Color(0xFFFFB4A6),
    onError = Color(0xFF5F1407),
    errorContainer = Color(0xFF7F2A19),
    onErrorContainer = Color(0xFFFFDAD4),
    background = Color(0xFF0D1118),
    onBackground = Color(0xFFECE6DA),
    surface = Color(0xFF0D1118),
    onSurface = Color(0xFFECE6DA),
    surfaceVariant = Color(0xFF2A303B),
    onSurfaceVariant = Color(0xFFB5B1A6),
    surfaceTint = Color(0xFFA9C7FF),
    inverseSurface = Color(0xFFECE6DA),
    inverseOnSurface = Color(0xFF1A1F2B),
    outline = Color(0xFF858277),
    outlineVariant = Color(0xFF353B47),
    scrim = Color(0xFF000000),
    surfaceBright = Color(0xFF2C3442),
    surfaceDim = Color(0xFF0D1118),
    surfaceContainerLowest = Color(0xFF080B10),
    surfaceContainerLow = Color(0xFF141922),
    surfaceContainer = Color(0xFF181E28),
    surfaceContainerHigh = Color(0xFF1F2632),
    surfaceContainerHighest = Color(0xFF283040),
)

/**
 * Colors outside the Material roles. The hero surface stays ink navy in both themes, so it carries
 * its own bright panel colors instead of the theme-dependent chart palette.
 */
@Immutable
internal data class FoldlyticsPalette(
    val hero: Color,
    val onHero: Color,
    val onHeroMuted: Color,
    val heroOutline: Color,
    val heroInner: Color,
    val onHeroInner: Color,
    val heroCover: Color,
    val onHeroCover: Color,
    val innerContainer: Color,
    val onInnerContainer: Color,
    val coverContainer: Color,
    val onCoverContainer: Color,
)

private val LightPalette = FoldlyticsPalette(
    hero = Color(0xFF12233F),
    onHero = Color(0xFFFAF5EC),
    onHeroMuted = Color(0xFFB4BED1),
    heroOutline = Color(0xFF2D4166),
    heroInner = Color(DisplayChartPalette.DARK_INNER),
    onHeroInner = Color(0xFF04263D),
    heroCover = Color(DisplayChartPalette.DARK_COVER),
    onHeroCover = Color(0xFF3A1600),
    innerContainer = Color(0xFFD6E9F8),
    onInnerContainer = Color(0xFF003A5E),
    coverContainer = Color(0xFFFFE0CC),
    onCoverContainer = Color(0xFF6E2A00),
)

private val DarkPalette = LightPalette.copy(
    hero = Color(0xFF182945),
    heroOutline = Color(0xFF334A73),
    innerContainer = Color(0xFF0E3A56),
    onInnerContainer = Color(0xFFCFE5FF),
    coverContainer = Color(0xFF52260A),
    onCoverContainer = Color(0xFFFFDCC6),
)

internal val LocalFoldlyticsPalette = staticCompositionLocalOf { LightPalette }

internal val foldlyticsPalette: FoldlyticsPalette
    @Composable
    @ReadOnlyComposable
    get() = LocalFoldlyticsPalette.current

// Generous outer corners; the tight "hinge" radius joins neighbouring surfaces like two panels.
internal val HingeCorner = 6.dp
internal val PanelCorner = 28.dp

private val FoldlyticsShapes = Shapes(
    extraSmall = RoundedCornerShape(HingeCorner),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(PanelCorner),
    extraLarge = RoundedCornerShape(36.dp),
)

@Composable
fun FoldlyticsTheme(content: @Composable () -> Unit) {
    val languageTag = LocalConfiguration.current.locales[0].toLanguageTag()
    val typography = remember(languageTag) {
        foldlyticsTypography().withLocaleList(LocaleList(Locale(languageTag)))
    }
    val dark = isSystemInDarkTheme()
    CompositionLocalProvider(
        LocalFoldlyticsPalette provides if (dark) DarkPalette else LightPalette,
    ) {
        MaterialExpressiveTheme(
            colorScheme = if (dark) DarkColors else LightColors,
            motionScheme = MotionScheme.expressive(),
            shapes = FoldlyticsShapes,
            typography = typography,
            content = content,
        )
    }
}

/** The serif used for figures and the wordmark; device fonts keep the app free of bundled assets. */
internal val FigureFontFamily = FontFamily.Serif

// Proportional kana and punctuation tighten Japanese headings; tabular figures keep columns steady.
private const val HEADING_FEATURES = "palt"
private const val DATA_FEATURES = "tnum"

private fun foldlyticsTypography(): Typography {
    val base = Typography()
    fun figure(size: Int, lineHeight: Int, tracking: Double) = TextStyle(
        fontFamily = FigureFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = size.sp,
        lineHeight = lineHeight.sp,
        letterSpacing = tracking.sp,
    )
    return Typography(
        displayLarge = figure(64, 68, -1.5),
        displayMedium = figure(48, 52, -1.0),
        displaySmall = figure(36, 40, -0.5),
        headlineLarge = figure(30, 36, -0.4),
        headlineMedium = figure(26, 32, -0.3),
        headlineSmall = base.headlineSmall.copy(
            fontWeight = FontWeight.Bold,
            fontSize = 22.sp,
            lineHeight = 28.sp,
            letterSpacing = (-0.2).sp,
            fontFeatureSettings = HEADING_FEATURES,
        ),
        titleLarge = base.titleLarge.copy(
            fontWeight = FontWeight.Bold,
            fontSize = 20.sp,
            lineHeight = 26.sp,
            letterSpacing = (-0.1).sp,
            fontFeatureSettings = HEADING_FEATURES,
        ),
        titleMedium = base.titleMedium.copy(
            fontWeight = FontWeight.Bold,
            fontSize = 16.sp,
            lineHeight = 22.sp,
            letterSpacing = 0.sp,
            fontFeatureSettings = HEADING_FEATURES,
        ),
        titleSmall = base.titleSmall.copy(
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp,
            lineHeight = 20.sp,
            letterSpacing = 0.sp,
            fontFeatureSettings = HEADING_FEATURES,
        ),
        bodyLarge = base.bodyLarge.copy(
            lineHeight = 25.sp,
            letterSpacing = 0.1.sp,
            fontFeatureSettings = DATA_FEATURES,
        ),
        bodyMedium = base.bodyMedium.copy(
            lineHeight = 21.sp,
            letterSpacing = 0.1.sp,
            fontFeatureSettings = DATA_FEATURES,
        ),
        bodySmall = base.bodySmall.copy(
            fontSize = 12.5.sp,
            lineHeight = 18.sp,
            letterSpacing = 0.15.sp,
            fontFeatureSettings = DATA_FEATURES,
        ),
        labelLarge = base.labelLarge.copy(
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.2.sp,
        ),
        labelMedium = base.labelMedium.copy(
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.6.sp,
        ),
        labelSmall = base.labelSmall.copy(
            fontWeight = FontWeight.Medium,
            letterSpacing = 0.5.sp,
            fontFeatureSettings = DATA_FEATURES,
        ),
    )
}

private fun Typography.withLocaleList(localeList: LocaleList): Typography = Typography(
    displayLarge = displayLarge.copy(localeList = localeList),
    displayMedium = displayMedium.copy(localeList = localeList),
    displaySmall = displaySmall.copy(localeList = localeList),
    headlineLarge = headlineLarge.copy(localeList = localeList),
    headlineMedium = headlineMedium.copy(localeList = localeList),
    headlineSmall = headlineSmall.copy(localeList = localeList),
    titleLarge = titleLarge.copy(localeList = localeList),
    titleMedium = titleMedium.copy(localeList = localeList),
    titleSmall = titleSmall.copy(localeList = localeList),
    bodyLarge = bodyLarge.copy(localeList = localeList),
    bodyMedium = bodyMedium.copy(localeList = localeList),
    bodySmall = bodySmall.copy(localeList = localeList),
    labelLarge = labelLarge.copy(localeList = localeList),
    labelMedium = labelMedium.copy(localeList = localeList),
    labelSmall = labelSmall.copy(localeList = localeList),
)
