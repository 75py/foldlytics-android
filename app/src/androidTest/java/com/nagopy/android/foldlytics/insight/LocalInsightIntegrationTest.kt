package com.nagopy.android.foldlytics.insight

import androidx.test.platform.app.InstrumentationRegistry
import com.nagopy.android.foldlytics.model.AppUsage
import com.nagopy.android.foldlytics.model.DailyPostureSummary
import com.nagopy.android.foldlytics.model.InnerDisplaySession
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Opt in only on an emulator/test device; all prompts and history in this test are synthetic. */
class LocalInsightIntegrationTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun generatesGroundedJapaneseAndEnglishOfflineAndCancels() = runBlocking {
        org.junit.Assume.assumeTrue(
            "Package the model with -PincludeInsightModelInApk=true and pass -e localLlm true",
            InstrumentationRegistry.getArguments().getString("localLlm") == "true",
        )
        val file = withContext(Dispatchers.IO) { InsightModelStore(context).prepare() }
        val engine = LlamaInsightEngine()
        val cancelled = async { engine.generate(file, "Describe usage", "Test", 256) }
        delay(50)
        cancelled.cancel()
        cancelled.join()
        assertTrue(cancelled.isCancelled)
        val report = StringBuilder("Synthetic on-device evaluation; no user usage data\n")
        for (language in listOf("ja", "en")) {
            for (innerMinutes in listOf(0L, 42L)) {
                val facts = fixture(language, innerMinutes)
                val start = System.nanoTime()
                val output = withTimeout(120_000) {
                    engine.generate(file, InsightTextProtocol.systemPrompt(language), InsightTextProtocol.userPrompt(facts), 256)
                }
                val elapsed = (System.nanoTime() - start) / 1_000_000
                report.append("$language innerMinutes=$innerMinutes elapsedMs=$elapsed\n$output\n\n")
                // Persist synthetic output to an app-private test report, never log user prompts.
                java.io.File(context.getExternalFilesDir(null), "insight-evaluation.txt").writeText(report.toString())
                assertNotNull("Invalid $language output: $output", InsightTextProtocol.parse(output, facts))
            }
        }
    }

    @Test fun cacheRequiresMatchingFactsAndRejectsDamagedOutput() {
        val cache = InsightCache(context)
        val facts = fixture("en", 42L)
        val output = "[display_share] Inner use was 70.0%.\n[recorded_days] Inner use was recorded on 30 days."
        cache.write("test-key", output)
        assertNotNull(cache.read("test-key", facts))
        assertNull(cache.read("different-key", facts))
        cache.write("test-key", "[unknown] Fabricated output")
        assertNull(cache.read("test-key", facts))
        assertEquals(true, facts.canGenerate)
    }

    private fun fixture(language: String, innerMinutes: Long): UsageInsightFacts {
        val zone = ZoneId.of("UTC")
        val today = LocalDate.of(2026, 9, 12)
        fun millis(date: LocalDate) = date.atStartOfDay(zone).toInstant().toEpochMilli()
        val days = (1L..61L).map { offset ->
            val date = today.minusDays(offset)
            DailyPostureSummary(millis(date), millis(date.plusDays(1)), zone.id,
                (60 - innerMinutes) * 60_000, innerMinutes * 60_000, 0, 2, 2, 0)
        }
        return UsageInsightAnalyzer().analyze(
            days,
            listOf(AppUsage("example.browser", "Browser", 18 * 60_000, innerMinutes * 60_000, 0)),
            if (innerMinutes == 0L) emptyList() else listOf(
                InnerDisplaySession(millis(today.minusDays(2)) + 60_000, 0,
                    millis(today.minusDays(2)) + 360_000, 300_000),
            ),
            millis(today), millis(today) + 60_000, zone, language,
        )
    }
}
