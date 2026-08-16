package com.nagopy.android.foldlytics.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.intl.Locale
import androidx.compose.ui.text.intl.LocaleList

private val LightColors = lightColorScheme(
    primary = Color(0xFF285EA8),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD7E3FF),
    onPrimaryContainer = Color(0xFF001B3F),
    secondary = Color(0xFF006B5F),
    secondaryContainer = Color(0xFF9EF2E1),
    tertiary = Color(0xFF7B4E00),
    background = Color(0xFFF9F9FF),
    surface = Color(0xFFF9F9FF),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFA9C7FF),
    primaryContainer = Color(0xFF00458A),
    secondary = Color(0xFF82D5C5),
    secondaryContainer = Color(0xFF005047),
    tertiary = Color(0xFFF2BD6C),
)

@Composable
fun FoldlyticsTheme(content: @Composable () -> Unit) {
    val languageTag = LocalConfiguration.current.locales[0].toLanguageTag()
    val typography = remember(languageTag) {
        Typography().withLocaleList(LocaleList(Locale(languageTag)))
    }
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        typography = typography,
        content = content,
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
