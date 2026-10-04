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
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.intl.Locale
import androidx.compose.ui.text.intl.LocaleList
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nagopy.android.foldlytics.R

// Iris, citron and cool mineral surfaces: display identity stays stable across both themes.
private val LightColors = lightColorScheme(
    primary = Color(0xFF5141D9),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFE6E1FF),
    onPrimaryContainer = Color(0xFF27157F),
    inversePrimary = Color(0xFFC6BEFF),
    secondary = Color(0xFF52631C),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFE2EDB8),
    onSecondaryContainer = Color(0xFF202A00),
    tertiary = Color(0xFF435D91),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFD9E3FF),
    onTertiaryContainer = Color(0xFF102247),
    error = Color(0xFFA8321F),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD4),
    onErrorContainer = Color(0xFF410002),
    background = Color(0xFFF8F9FC),
    onBackground = Color(0xFF191B26),
    surface = Color(0xFFF8F9FC),
    onSurface = Color(0xFF191B26),
    surfaceVariant = Color(0xFFE5E6EF),
    onSurfaceVariant = Color(0xFF616575),
    surfaceTint = Color(0xFF5141D9),
    inverseSurface = Color(0xFF292052),
    inverseOnSurface = Color(0xFFF1EEFF),
    outline = Color(0xFF777C8C),
    outlineVariant = Color(0xFFDDE0EB),
    scrim = Color(0xFF151322),
    surfaceBright = Color(0xFFFFFFFF),
    surfaceDim = Color(0xFFD9DDE9),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF0F2F8),
    surfaceContainer = Color(0xFFE9ECF5),
    surfaceContainerHigh = Color(0xFFE2E6F1),
    surfaceContainerHighest = Color(0xFFD9DDE9),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFC6BEFF),
    onPrimary = Color(0xFF2A1C7F),
    primaryContainer = Color(0xFF3D2DAB),
    onPrimaryContainer = Color(0xFFE6E1FF),
    inversePrimary = Color(0xFF5141D9),
    secondary = Color(0xFFCADF8A),
    onSecondary = Color(0xFF293900),
    secondaryContainer = Color(0xFF3E5011),
    onSecondaryContainer = Color(0xFFE2EDB8),
    tertiary = Color(0xFFB1C6FF),
    onTertiary = Color(0xFF163061),
    tertiaryContainer = Color(0xFF2C4578),
    onTertiaryContainer = Color(0xFFD9E3FF),
    error = Color(0xFFFFB4A6),
    onError = Color(0xFF5F1407),
    errorContainer = Color(0xFF7F2A19),
    onErrorContainer = Color(0xFFFFDAD4),
    background = Color(0xFF101118),
    onBackground = Color(0xFFF0F0FA),
    surface = Color(0xFF101118),
    onSurface = Color(0xFFF0F0FA),
    surfaceVariant = Color(0xFF292C3A),
    onSurfaceVariant = Color(0xFFB8BDCF),
    surfaceTint = Color(0xFFC6BEFF),
    inverseSurface = Color(0xFFF0F0FA),
    inverseOnSurface = Color(0xFF191B26),
    outline = Color(0xFF888EA2),
    outlineVariant = Color(0xFF35394C),
    scrim = Color(0xFF000000),
    surfaceBright = Color(0xFF303349),
    surfaceDim = Color(0xFF101118),
    surfaceContainerLowest = Color(0xFF0B0C12),
    surfaceContainerLow = Color(0xFF181A27),
    surfaceContainer = Color(0xFF1E2030),
    surfaceContainerHigh = Color(0xFF25283B),
    surfaceContainerHighest = Color(0xFF2D3146),
)

/**
 * Colors outside the Material roles. The hero surface stays ink navy in both themes, so it carries
 * its own bright panel colors instead of the theme-dependent chart palette.
 */
@Immutable
internal data class FoldlyticsPalette(
    val heroInner: Color,
    val heroCover: Color,
    val onHeroCover: Color,
    val innerContainer: Color,
    val onInnerContainer: Color,
    val coverContainer: Color,
    val onCoverContainer: Color,
)

private val LightPalette = FoldlyticsPalette(
    heroInner = Color(0xFF7058F5),
    heroCover = Color(0xFFD7EB98),
    onHeroCover = Color(0xFF26340A),
    innerContainer = Color(0xFFE6E1FF),
    onInnerContainer = Color(0xFF342298),
    coverContainer = Color(0xFFE8EFCB),
    onCoverContainer = Color(0xFF3B4B16),
)

private val DarkPalette = LightPalette.copy(
    innerContainer = Color(0xFF322963),
    onInnerContainer = Color(0xFFD9E3FF),
    coverContainer = Color(0xFF323F20),
    onCoverContainer = Color(0xFFE2EDB8),
)

internal val LocalFoldlyticsPalette = staticCompositionLocalOf { LightPalette }

internal val foldlyticsPalette: FoldlyticsPalette
    @Composable
    @ReadOnlyComposable
    get() = LocalFoldlyticsPalette.current

// Generous outer corners; the tight "hinge" radius joins neighbouring surfaces like two panels.
internal val HingeCorner = 4.dp
internal val PanelCorner = 24.dp

private val FoldlyticsShapes = Shapes(
    extraSmall = RoundedCornerShape(HingeCorner),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(PanelCorner),
    extraLarge = RoundedCornerShape(32.dp),
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

/** Bundled Manrope keeps the same geometry offline; Japanese uses the system glyph fallback. */
@OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)
internal val FigureFontFamily = FontFamily(
    listOf(400, 500, 600, 700).map { weight ->
        Font(R.font.manrope, weight = FontWeight(weight),
            variationSettings = FontVariation.Settings(FontVariation.weight(weight)))
    },
)

// Proportional kana and punctuation tighten Japanese headings; tabular figures keep columns steady.
private const val HEADING_FEATURES = "palt"
private const val DATA_FEATURES = "tnum"

private fun foldlyticsTypography(): Typography {
    val base = Typography()
    fun figure(size: Int, lineHeight: Int, tracking: Double) = TextStyle(
        fontFamily = FigureFontFamily,
        fontWeight = FontWeight.Medium,
        fontFeatureSettings = DATA_FEATURES,
        fontSize = size.sp,
        lineHeight = lineHeight.sp,
        letterSpacing = tracking.sp,
    )
    return Typography(
        displayLarge = figure(88, 92, -4.5),
        displayMedium = figure(56, 60, -2.5),
        displaySmall = figure(40, 44, -1.5),
        headlineLarge = figure(32, 38, -1.0),
        headlineMedium = figure(26, 32, -0.6),
        headlineSmall = base.headlineSmall.copy(
            fontWeight = FontWeight.SemiBold,
            fontSize = 22.sp,
            lineHeight = 28.sp,
            letterSpacing = (-0.2).sp,
            fontFeatureSettings = HEADING_FEATURES,
        ),
        titleLarge = base.titleLarge.copy(
            fontWeight = FontWeight.SemiBold,
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
            letterSpacing = 0.2.sp,
        ),
        labelSmall = base.labelSmall.copy(
            fontWeight = FontWeight.Medium,
            letterSpacing = 0.5.sp,
            fontFeatureSettings = DATA_FEATURES,
        ),
    )
}

private fun Typography.withLocaleList(localeList: LocaleList): Typography = Typography(
    displayLarge = displayLarge.copy(localeList = localeList, fontFamily = FigureFontFamily),
    displayMedium = displayMedium.copy(localeList = localeList, fontFamily = FigureFontFamily),
    displaySmall = displaySmall.copy(localeList = localeList, fontFamily = FigureFontFamily),
    headlineLarge = headlineLarge.copy(localeList = localeList, fontFamily = FigureFontFamily),
    headlineMedium = headlineMedium.copy(localeList = localeList, fontFamily = FigureFontFamily),
    headlineSmall = headlineSmall.copy(localeList = localeList, fontFamily = FigureFontFamily),
    titleLarge = titleLarge.copy(localeList = localeList, fontFamily = FigureFontFamily),
    titleMedium = titleMedium.copy(localeList = localeList, fontFamily = FigureFontFamily),
    titleSmall = titleSmall.copy(localeList = localeList, fontFamily = FigureFontFamily),
    bodyLarge = bodyLarge.copy(localeList = localeList, fontFamily = FigureFontFamily),
    bodyMedium = bodyMedium.copy(localeList = localeList, fontFamily = FigureFontFamily),
    bodySmall = bodySmall.copy(localeList = localeList, fontFamily = FigureFontFamily),
    labelLarge = labelLarge.copy(localeList = localeList, fontFamily = FigureFontFamily),
    labelMedium = labelMedium.copy(localeList = localeList, fontFamily = FigureFontFamily),
    labelSmall = labelSmall.copy(localeList = localeList, fontFamily = FigureFontFamily),
)
