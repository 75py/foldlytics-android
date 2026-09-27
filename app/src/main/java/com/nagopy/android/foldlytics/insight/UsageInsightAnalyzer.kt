package com.nagopy.android.foldlytics.insight

import com.nagopy.android.foldlytics.model.DailyPostureSummary
import com.nagopy.android.foldlytics.model.InnerDisplaySession
import java.time.Instant
import java.time.ZoneId
import java.util.Locale

/** Pure extraction. Availability thresholds are product safeguards, not statistical confidence. */
class UsageInsightAnalyzer {
    /**
     * Daily rows must use [zoneId]; mismatched, partial, and duplicate calendar days are not usable evidence.
     * Supply the actual recording start when known; otherwise the first supplied day is treated
     * as possibly partial. Session caches must already exclude intervals with unknown activity.
     */
    fun analyze(
        dailySummaries: List<DailyPostureSummary>,
        completedSessions: List<InnerDisplaySession>,
        syncedThroughMillis: Long?,
        nowMillis: Long,
        zoneId: ZoneId,
        languageTag: String,
        recordingStartMillis: Long? = null,
    ): UsageInsightFacts {
        val range = resolveRange(nowMillis, zoneId)
        val language = if (Locale.forLanguageTag(languageTag).language == "ja") "ja" else "en"
        val japanese = language == "ja"
        fun localized(ja: String, en: String): String = if (japanese) ja else en
        val firstSuppliedDay = dailySummaries.minOfOrNull { it.dayStartMillis }
        val usableRows = dailySummaries.groupBy { it.dayStartMillis }.values.mapNotNull { rows ->
            val row = rows.singleOrNull() ?: return@mapNotNull null
            val date = Instant.ofEpochMilli(row.dayStartMillis).atZone(zoneId).toLocalDate()
            val fullStart = date.atStartOfDay(zoneId).toInstant().toEpochMilli()
            val fullEnd = date.plusDays(1).atStartOfDay(zoneId).toInstant().toEpochMilli()
            row.takeIf {
                it.zoneId == zoneId.id && it.dayStartMillis == fullStart &&
                    it.dayEndMillis == fullEnd && it.dayEndMillis <= (syncedThroughMillis ?: 0L) &&
                    it.coverMillis >= 0L && it.innerMillis >= 0L && it.excludedMillis >= 0L &&
                    it.evidenceGapCount >= 0 &&
                    it.coverMillis <= fullEnd - fullStart &&
                    it.innerMillis <= fullEnd - fullStart - it.coverMillis &&
                    it.excludedMillis <= fullEnd - fullStart - it.coverMillis - it.innerMillis &&
                    if (recordingStartMillis != null) {
                        it.dayStartMillis >= recordingStartMillis
                    } else {
                        it.dayStartMillis != firstSuppliedDay
                    }
            }
        }
        val current = summarize(usableRows, range.currentStartMillis, range.currentEndMillis)
        val synchronized = syncedThroughMillis != null && syncedThroughMillis >= range.currentEndMillis
        val facts = mutableListOf<UsageInsightFact>()
        val limitations = mutableListOf<UsageInsightFact>()
        fun fact(id: String, evidence: String, ja: String, en: String) {
            facts += UsageInsightFact(id, evidence, localized(ja, en))
        }
        fun limitation(id: String, evidence: String, ja: String, en: String) {
            limitations += UsageInsightFact(id, evidence, localized(ja, en))
        }
        fact(
            "recorded_days",
            "current_period_calendar_days=30; classified_usage_days=${current.recordedDays}; " +
                "inner_used_days=${current.innerDays}; only complete recorded calendar days included",
            "記録のある${current.recordedDays}日間のうち、内側画面を使った日は${current.innerDays}日でした。",
            "Of the past 30 days, ${current.recordedDays} had classified usage and ${current.innerDays} had inner-display usage.",
        )
        if (current.classifiedMillis > 0L) {
            val innerPercent = percent(current.innerMillis, current.classifiedMillis)
            val coverPercent = percent(current.coverMillis, current.classifiedMillis)
            fact(
                "display_share",
                "cover_ms=${current.coverMillis}; inner_ms=${current.innerMillis}; " +
                    "denominator_classified_device_ms=${current.classifiedMillis}; " +
                    "cover_percent=$coverPercent; inner_percent=$innerPercent; unknown time excluded",
                "利用時間の$innerPercent%が内側画面、$coverPercent%が外側画面でした。画面が不明な時間は除きます。",
                "Of classified device usage, $innerPercent% was on the inner display and $coverPercent% on the cover display.",
            )
        }
        val durations = completedSessions.asSequence().filter { session ->
            val closed = session.closedAtMillis
            session.openedAtMillis >= range.currentStartMillis && closed != null &&
                closed >= session.openedAtMillis && closed < range.currentEndMillis &&
                closed <= (syncedThroughMillis ?: 0L) && session.innerActiveMillis >= 0L &&
                session.innerActiveMillis <= closed - session.openedAtMillis
        }.map { it.innerActiveMillis }.sorted().toList()
        if (durations.isNotEmpty()) {
            val middle = durations.size / 2
            val median = if (durations.size % 2 == 1) durations[middle] else {
                durations[middle - 1] + (durations[middle] - durations[middle - 1]) / 2L
            }
            val seconds = decimal(median / 1_000.0)
            fact(
                "complete_session_median",
                "complete_session_count=${durations.size}; median_inner_active_ms=$median; " +
                    "median_inner_active_seconds=$seconds; zero-duration complete sessions included; " +
                    "incomplete or boundary-crossing sessions excluded",
                "完了を確認できた内側画面の利用は${durations.size}回で、利用時間の中央値は$seconds 秒でした。",
                "Across ${durations.size} confirmed complete inner-display sessions, median active time was $seconds seconds.",
            )
        } else {
            limitation(
                "no_complete_sessions", "no confirmed complete sessions within current period; no median available",
                "期間内に完了を確認できた内側利用がないため、中央値は出せません。",
                "No confirmed complete inner-display sessions are available for a median.",
            )
        }
        if (!synchronized) {
            limitation(
                "sync_incomplete", "sync has not reached the end of yesterday; generation unavailable",
                "昨日の終わりまで同期できていません。同期してから再確認してください。",
                "Sync has not reached the end of yesterday. Sync and check again.",
            )
        }
        if (current.recordedDays < MIN_RECORDED_DAYS) {
            limitation(
                "insufficient_days", "classified_usage_days=${current.recordedDays}; minimum_required=$MIN_RECORDED_DAYS",
                "過去30日内に、画面を判定できた利用がある日が$MIN_RECORDED_DAYS 日以上必要です。",
                "At least $MIN_RECORDED_DAYS days with classified usage are needed within the past 30 days.",
            )
        }
        if (current.recordedDays < PERIOD_DAYS) {
            limitation(
                "missing_days", "${PERIOD_DAYS - current.recordedDays} calendar days lack complete classified usage evidence; do not infer non-use",
                "全30日の利用を確認できていません。記録のない日を「使わなかった日」とは判断できません。",
                "Usage is not confirmed for all 30 days. Missing records do not mean the device was unused.",
            )
        }
        if (current.excludedMillis > 0L || current.gapDays > 0) {
            val coverage = percent(current.classifiedMillis, current.observedMillis)
            limitation(
                "incomplete_evidence",
                "classified_percent_of_observed=$coverage; excluded_ms=${current.excludedMillis}; evidence_gap_days=${current.gapDays}; no missing time reconstructed",
                "観測時間のうち画面を判定できた割合は$coverage%。根拠の欠損がある日は${current.gapDays}日です。",
                "$coverage% of observed time has a classified display; ${current.gapDays} days contain evidence gaps.",
            )
        }
        if (current.coverage < MIN_COVERAGE) {
            limitation(
                "insufficient_coverage", "generation requires at least 90% classified observed time",
                "文章の生成には、観測時間の90%以上で画面を判定できる記録が必要です。",
                "Generating an insight requires a classified display for at least 90% of observed time.",
            )
        }
        val canGenerate = synchronized && current.recordedDays >= MIN_RECORDED_DAYS &&
            current.coverage >= MIN_COVERAGE
        // Raw aggregate material invalidates cached output even when rounded prose stays the same.
        val fingerprint = buildString {
            append("usage-insight-facts-v2|$range|$language|$canGenerate|$current\n")
            (facts + limitations).forEach {
                append(it.id).append(':').append(it.evidenceText).append('\n')
            }
        }
        return UsageInsightFacts(range, language, facts, limitations, canGenerate, fingerprint)
    }

    private fun summarize(
        summaries: List<DailyPostureSummary>,
        startMillis: Long,
        endMillis: Long,
    ): PeriodEvidence {
        val rows = summaries.filter { it.dayStartMillis >= startMillis && it.dayEndMillis <= endMillis }
        return PeriodEvidence(
            coverMillis = rows.sumOf { it.coverMillis },
            innerMillis = rows.sumOf { it.innerMillis },
            excludedMillis = rows.sumOf { it.excludedMillis },
            recordedDays = rows.count { it.classifiedMillis > 0L },
            innerDays = rows.count { it.innerMillis > 0L },
            gapDays = rows.count { it.evidenceGapCount > 0 },
        )
    }

    private data class PeriodEvidence(
        val coverMillis: Long,
        val innerMillis: Long,
        val excludedMillis: Long,
        val recordedDays: Int,
        val innerDays: Int,
        val gapDays: Int,
    ) {
        val classifiedMillis: Long get() = coverMillis + innerMillis
        val observedMillis: Long get() = classifiedMillis + excludedMillis
        val coverage: Double get() = if (observedMillis > 0L) classifiedMillis.toDouble() / observedMillis else 0.0
    }

    companion object {
        const val PERIOD_DAYS = 30
        const val MIN_RECORDED_DAYS = 14
        const val MIN_COVERAGE = 0.90

        fun resolveRange(nowMillis: Long, zoneId: ZoneId): UsageInsightRange {
            val today = Instant.ofEpochMilli(nowMillis).atZone(zoneId).toLocalDate()
            return UsageInsightRange(
                currentStartMillis = today.minusDays(PERIOD_DAYS.toLong()).atStartOfDay(zoneId).toInstant().toEpochMilli(),
                currentEndMillis = today.atStartOfDay(zoneId).toInstant().toEpochMilli(),
                zoneId = zoneId.id,
            )
        }

        private fun percent(numerator: Long, denominator: Long): String =
            decimal(if (denominator > 0L) numerator.toDouble() / denominator * 100.0 else 0.0)

        private fun decimal(value: Double): String = String.format(Locale.ROOT, "%.1f", value)
    }
}
