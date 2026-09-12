package com.nagopy.android.foldlytics.insight

import com.nagopy.android.foldlytics.model.DailyPostureSummary
import com.nagopy.android.foldlytics.model.InnerDisplaySession
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UsageInsightAnalyzerTest {
    private val zone = ZoneId.of("Asia/Tokyo")
    private val today = LocalDate.of(2026, 9, 12)
    private val now = start(today) + 12 * HOUR
    private val range = UsageInsightAnalyzer.resolveRange(now, zone)
    private val analyzer = UsageInsightAnalyzer()

    @Test
    fun excludesTodayAndUsesCalendarDaysAcrossBothDaylightSavingTransitions() {
        val newYork = ZoneId.of("America/New_York")
        val spring = UsageInsightAnalyzer.resolveRange(start(LocalDate.of(2026, 3, 20), newYork), newYork)
        val autumn = UsageInsightAnalyzer.resolveRange(start(LocalDate.of(2026, 11, 10), newYork), newYork)
        assertEquals(30 * 24 * HOUR - HOUR, spring.currentEndMillis - spring.currentStartMillis)
        assertEquals(30 * 24 * HOUR + HOUR, autumn.currentEndMillis - autumn.currentStartMillis)

        val result = analyze(rows(30) + day(today, inner = 10 * HOUR))
        assertEquals(start(today), result.range.currentEndMillis)
        assertTrue(result.fact("display_share").evidenceText.contains("inner_ms=${30 * HOUR}"))
    }

    @Test
    fun requiresFourteenRecordedDaysAndDoesNotInterpretMissingDaysAsNonUse() {
        val insufficient = analyze(rows(13))
        assertFalse(insufficient.canGenerate)
        assertTrue(insufficient.limitations.any { it.id == "insufficient_days" })
        val enough = analyze(rows(14))
        assertTrue(enough.canGenerate)
        assertTrue(enough.limitations.any { it.id == "missing_days" })
        assertTrue(enough.fact("recorded_days").evidenceText.contains("inner_used_days=14"))
    }

    @Test
    fun blocksGenerationWhenYesterdayIsNotFullySynchronized() {
        val result = analyze(rows(30), syncedThrough = range.currentEndMillis - 1L)
        assertFalse(result.canGenerate)
        assertTrue(result.limitations.any { it.id == "sync_incomplete" })
        assertTrue(result.fact("recorded_days").evidenceText.contains("classified_usage_days=29"))
        assertFalse(analyze(rows(30), syncedThrough = null).canGenerate)
    }

    @Test
    fun rejectsLowCoverageAndKeepsUnknownTimeOutOfDisplayShare() {
        val result = analyze(rows(30).map { it.copy(excludedMillis = HOUR) })
        assertFalse(result.canGenerate)
        assertTrue(result.fact("display_share").evidenceText.contains("inner_percent=25.0"))
        assertTrue(result.limitations.any { it.id == "insufficient_coverage" })
    }

    @Test
    fun firstRecordingPartialDayCannotSatisfyMinimumAndUnknownStartIsConservative() {
        val source = rows(14)
        assertFalse(analyze(source, recordingStart = source.first().dayStartMillis + 1L).canGenerate)
        assertFalse(analyze(source, recordingStart = null).canGenerate)
        assertTrue(analyze(source, recordingStart = source.first().dayStartMillis).canGenerate)
    }

    @Test
    fun duplicateWrongZoneAndPartialDaysCannotSatisfyMinimum() {
        val source = rows(14)
        assertFalse(analyze(source + source.first()).canGenerate)
        assertFalse(analyze(source.drop(1) + source.first().copy(zoneId = "UTC")).canGenerate)
        assertFalse(analyze(source.drop(1) + source.first().copy(dayEndMillis = source.first().dayEndMillis - 1L)).canGenerate)
    }

    @Test
    fun medianIncludesZeroAndExcludesIncompleteAndBoundaryCrossingSessions() {
        val start = range.currentStartMillis
        val end = range.currentEndMillis
        val result = analyze(
            rows(30),
            sessions = listOf(
                session(start, start, 0),
                session(start + 1, start + 2_001, 2_000),
                session(start - 1, start + 5_000, 5_000),
                session(end - 5_000, end, 5_000),
                session(start + 10_000, null, 5_000),
                session(start + 10_000, start + 9_000, 0),
            ),
        )
        val median = result.fact("complete_session_median").evidenceText
        assertTrue(median.contains("complete_session_count=2"))
        assertTrue(median.contains("median_inner_active_ms=1000"))
    }

    @Test
    fun fingerprintIsStableAcrossInputOrderAndChangesWithLanguageOrUnroundedEvidence() {
        val source = rows(30)
        val result = analyze(source)
        assertEquals(result.fingerprintMaterial, analyze(source.reversed()).fingerprintMaterial)
        assertNotEquals(result.fingerprintMaterial, analyze(source, language = "ja-JP").fingerprintMaterial)
        val changed = source.drop(1) + source.first().copy(innerMillis = HOUR + 1L)
        assertNotEquals(result.fingerprintMaterial, analyze(changed).fingerprintMaterial)
        assertEquals("ja", analyze(source, language = "ja-JP").languageTag)
    }

    private fun analyze(
        source: List<DailyPostureSummary>,
        sessions: List<InnerDisplaySession> = emptyList(),
        syncedThrough: Long? = range.currentEndMillis,
        recordingStart: Long? = range.currentStartMillis,
        language: String = "en",
    ) = analyzer.analyze(source, sessions, syncedThrough, now, zone, language, recordingStart)

    private fun rows(count: Int) = (count downTo 1).map { day(today.minusDays(it.toLong())) }

    private fun day(date: LocalDate, inner: Long = HOUR, cover: Long = 3 * HOUR) = DailyPostureSummary(
        dayStartMillis = start(date),
        dayEndMillis = start(date.plusDays(1)),
        zoneId = zone.id,
        coverMillis = cover,
        innerMillis = inner,
        excludedMillis = 0,
        openedCount = 1,
        closedCount = 1,
        evidenceGapCount = 0,
    )

    private fun session(opened: Long, closed: Long?, duration: Long) = InnerDisplaySession(opened, 0, closed, duration)

    private fun UsageInsightFacts.fact(id: String) = facts.single { it.id == id }

    private fun start(date: LocalDate, zoneId: ZoneId = zone): Long = date.atStartOfDay(zoneId).toInstant().toEpochMilli()

    companion object {
        private const val HOUR = 3_600_000L
    }
}
