package com.nagopy.android.foldlytics.ui

import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.test.platform.app.InstrumentationRegistry
import com.nagopy.android.foldlytics.R
import com.nagopy.android.foldlytics.model.AnalysisPeriod
import com.nagopy.android.foldlytics.model.AppUsage
import com.nagopy.android.foldlytics.model.PeriodUsageSummary
import java.io.ByteArrayOutputStream
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SummaryShareImageTest {
    private val targetContext: Context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun createsJapaneseAndEnglishShareContentFromTheCurrentAppLocale() {
        val summary = summary(period = AnalysisPeriod.DAYS_30)
        val japanese = SummaryShareImageRenderer.createContent(
            localizedContext(Locale.JAPANESE).resources,
            summary,
            deviceName = "Google Pixel Fold",
        )
        val english = SummaryShareImageRenderer.createContent(
            localizedContext(Locale.US).resources,
            summary,
            deviceName = "Google Pixel Fold",
        )

        assertEquals("Foldlytics", japanese.brand)
        assertEquals("Google Pixel Fold", japanese.deviceName)
        assertTrue(japanese.period.startsWith("30日"))
        assertEquals("内側ディスプレイ", japanese.innerRatioLabel)
        assertEquals("開いた回数", japanese.openedCountLabel)
        assertEquals("Foldlytics", english.brand)
        assertEquals("Google Pixel Fold", english.deviceName)
        assertTrue(english.period.startsWith("30 days"))
        assertEquals("Inner display", english.innerRatioLabel)
        assertEquals("Opened", english.openedCountLabel)
    }

    @Test
    fun formatsManufacturerAndModelWithoutDuplicatingTheManufacturer() {
        assertEquals("Google Pixel Fold", formatDeviceName("Google", "Pixel Fold"))
        assertEquals("Samsung SM-F9560", formatDeviceName("samsung", "SM-F9560"))
        assertEquals("HUAWEI Mate X6", formatDeviceName("HUAWEI", "HUAWEI Mate X6"))
    }

    @Test
    fun rendersAllSupportedPeriodShapesAndLargeValuesWithoutTextOverflow() {
        val resources = localizedContext(Locale.US).resources
        val periods = listOf(
            AnalysisPeriod.HOURS_24,
            AnalysisPeriod.DAYS_30,
            AnalysisPeriod.DAYS_365,
            AnalysisPeriod.CUSTOM,
        )

        periods.forEach { period ->
            val result = SummaryShareImageRenderer.renderWithDiagnostics(
                resources,
                summary(
                    period = period,
                    coverMillis = hours(26_279L),
                    innerMillis = hours(21_987L),
                    openedCount = 987_654_321,
                ),
            )

            val failures = result.textMeasurements.filterNot(SummaryShareTextMeasurement::fits)
            assertTrue(
                "$period overflowed: $failures",
                failures.isEmpty(),
            )
        }
    }

    @Test
    fun rendersAnOpaqueDecodablePngAtExactly1200By675() {
        val bitmap = SummaryShareImageRenderer.render(
            localizedContext(Locale.JAPANESE).resources,
            summary(period = AnalysisPeriod.HOURS_24),
        )

        assertEquals(SUMMARY_SHARE_IMAGE_WIDTH, bitmap.width)
        assertEquals(SUMMARY_SHARE_IMAGE_HEIGHT, bitmap.height)
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        assertTrue(pixels.all { pixel -> pixel ushr 24 == 0xFF })

        val encoded = ByteArrayOutputStream().use { output ->
            assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output))
            output.toByteArray()
        }
        val decoded = BitmapFactory.decodeByteArray(encoded, 0, encoded.size)
        assertNotNull(decoded)
        assertEquals(SUMMARY_SHARE_IMAGE_WIDTH, decoded.width)
        assertEquals(SUMMARY_SHARE_IMAGE_HEIGHT, decoded.height)
    }

    @Test
    fun drawsOnlyTheApprovedSummaryFields() {
        val resources = localizedContext(Locale.ENGLISH).resources
        val summary = summary(period = AnalysisPeriod.CUSTOM).copy(
            excludedMillis = hours(99L),
            closedCount = 4321,
            apps = listOf(
                AppUsage(
                    packageName = "private.example.package",
                    label = "Private app name",
                    coverMillis = hours(4L),
                    innerMillis = hours(2L),
                    excludedMillis = hours(1L),
                ),
            ),
        )
        val content = SummaryShareImageRenderer.createContent(resources, summary)
        val result = SummaryShareImageRenderer.renderWithDiagnostics(resources, summary)
        val renderedTexts = result.textMeasurements.map(SummaryShareTextMeasurement::text)
        val combinedText = renderedTexts.joinToString(separator = " ")

        assertEquals(content.visibleTexts, renderedTexts)
        assertFalse(combinedText.contains("Private app name"))
        assertFalse(combinedText.contains("private.example.package"))
        assertFalse(combinedText.contains(resources.getString(R.string.label_classified_time)))
        assertFalse(combinedText.contains(resources.getString(R.string.label_data_coverage)))
        assertFalse(combinedText.contains(resources.getString(R.string.label_closed)))
    }

    @Test
    fun refusesToCreateAnImageWithoutClassifiedUsage() {
        val result = runCatching {
            SummaryShareImageRenderer.render(
                targetContext.resources,
                summary(
                    period = AnalysisPeriod.HOURS_24,
                    coverMillis = 0L,
                    innerMillis = 0L,
                ),
            )
        }

        assertTrue(result.isFailure)
    }

    private fun summary(
        period: AnalysisPeriod,
        coverMillis: Long = hours(18L),
        innerMillis: Long = hours(12L),
        openedCount: Int = 84,
    ): PeriodUsageSummary {
        val zoneId = ZoneId.of("Asia/Tokyo")
        val start = LocalDate.of(2023, 8, 22)
            .atStartOfDay(zoneId)
            .toInstant()
            .toEpochMilli()
        val end = LocalDate.of(2026, 8, 23)
            .atStartOfDay(zoneId)
            .toInstant()
            .toEpochMilli()
        return PeriodUsageSummary(
            period = period,
            rangeStartMillis = start,
            rangeEndMillis = end,
            coverMillis = coverMillis,
            innerMillis = innerMillis,
            excludedMillis = hours(2L),
            openedCount = openedCount,
            closedCount = 79,
            apps = emptyList(),
        )
    }

    private fun localizedContext(locale: Locale): Context {
        val configuration = Configuration(targetContext.resources.configuration).apply {
            setLocale(locale)
        }
        return targetContext.createConfigurationContext(configuration)
    }

    private fun hours(value: Long): Long = value * 60L * 60L * 1_000L
}
