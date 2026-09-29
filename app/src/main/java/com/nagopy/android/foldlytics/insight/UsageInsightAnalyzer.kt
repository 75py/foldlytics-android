package com.nagopy.android.foldlytics.insight

import com.nagopy.android.foldlytics.model.AppUsage
import com.nagopy.android.foldlytics.model.DailyPostureSummary
import com.nagopy.android.foldlytics.model.InnerDisplaySession
import java.time.Instant
import java.time.ZoneId
import java.util.Locale

/** Pure extraction. Availability thresholds are product safeguards, not statistical confidence. */
class UsageInsightAnalyzer {
    /**
     * [apps] must contain aggregates for [resolveRange]'s current period only. Daily rows must
     * use [zoneId]; mismatched, partial, and duplicate calendar days are not usable evidence.
     * Supply the actual recording start when known; otherwise the first supplied day is treated
     * as possibly partial. Session caches must already exclude intervals with unknown activity.
     */
    fun analyze(
        dailySummaries: List<DailyPostureSummary>,
        apps: List<AppUsage>,
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
        val previous = summarize(usableRows, range.previousStartMillis, range.currentStartMillis)
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
        apps.filter { it.isLauncherApp && it.innerMillis > 0L && it.coverMillis >= 0L }
            .sortedWith(compareByDescending<AppUsage> { it.innerMillis }.thenBy { it.packageName })
            .take(3).forEachIndexed { index, app ->
                // Double arithmetic prevents a malformed aggregate overflowing its denominator.
                val appPercent = decimal(
                    app.innerMillis.toDouble() / (app.innerMillis.toDouble() + app.coverMillis) * 100.0,
                )
                val label = app.label.replace(Regex("[\\p{Cc}\\p{Cf}]"), " ").take(80)
                fact(
                    "inner_app_${index + 1}",
                    "rank_by_inner_app_time=${index + 1}; app_label_untrusted=${quoted(label)}; " +
                        "inner_ms=${app.innerMillis}; cover_ms=${app.coverMillis}; " +
                        "inner_percent_of_this_app_classified_time=$appPercent; " +
                        "app times may overlap and are not shares of device time; purpose and comfort unknown",
                    "内側での表示時間${index + 1}位は「$label」。このアプリの判定済み表示時間の$appPercent%が内側です。",
                    "No. ${index + 1} by inner-display app time: $label. $appPercent% of this app's classified time was on the inner display.",
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
        val comparable = synchronized && current.isComparable && previous.isComparable
        if (comparable) {
            val previousPercent = percent(previous.innerMillis, previous.classifiedMillis)
            val currentPercent = percent(current.innerMillis, current.classifiedMillis)
            val delta = decimal((current.innerRatio - previous.innerRatio) * 100.0)
            fact(
                "previous_period_comparison",
                "previous_30_day_inner_percent=$previousPercent; current_30_day_inner_percent=$currentPercent; " +
                    "change_percentage_points=$delta; previous_classified_usage_days=${previous.recordedDays}; " +
                    "current_classified_usage_days=${current.recordedDays}; no cause or preference inferred",
                "前の30日の内側割合は$previousPercent%、直近30日は$currentPercent%で、差は$delta ポイントです。",
                "Inner-display share was $previousPercent% in the previous 30 days and $currentPercent% in the latest 30 days, a change of $delta percentage points.",
            )
        } else {
            limitation(
                "comparison_unavailable",
                "previous period comparison unavailable: each period requires at least $MIN_COMPARISON_DAYS classified usage days, 95% classified observed time, no evidence-gap days, and sync through yesterday",
                "前期比較には、両期間で24日以上の記録、95%以上の判定済み観測時間、根拠欠損なし、昨日までの同期が必要です。",
                "Comparison requires at least 24 recorded days in each period, 95% classified observed time, no evidence gaps, and sync through yesterday.",
            )
        }
        val canGenerate = synchronized && current.recordedDays >= MIN_RECORDED_DAYS &&
            current.coverage >= MIN_COVERAGE
        // Raw aggregate material invalidates cached output even when rounded prose stays the same.
        val fingerprint = buildString {
            append("usage-insight-facts-v1|$range|$language|$canGenerate|$current|$previous\n")
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
        val innerRatio: Double get() = if (classifiedMillis > 0L) innerMillis.toDouble() / classifiedMillis else 0.0
        val isComparable: Boolean get() = recordedDays >= MIN_COMPARISON_DAYS && coverage >= 0.95 && gapDays == 0
    }

    companion object {
        const val PERIOD_DAYS = 30
        const val MIN_RECORDED_DAYS = 14
        const val MIN_COMPARISON_DAYS = 24
        const val MIN_COVERAGE = 0.90

        fun resolveRange(nowMillis: Long, zoneId: ZoneId): UsageInsightRange {
            val today = Instant.ofEpochMilli(nowMillis).atZone(zoneId).toLocalDate()
            return UsageInsightRange(
                currentStartMillis = today.minusDays(PERIOD_DAYS.toLong()).atStartOfDay(zoneId).toInstant().toEpochMilli(),
                currentEndMillis = today.atStartOfDay(zoneId).toInstant().toEpochMilli(),
                previousStartMillis = today.minusDays(PERIOD_DAYS * 2L).atStartOfDay(zoneId).toInstant().toEpochMilli(),
                zoneId = zoneId.id,
            )
        }

        private fun percent(numerator: Long, denominator: Long): String =
            decimal(if (denominator > 0L) numerator.toDouble() / denominator * 100.0 else 0.0)

        private fun decimal(value: Double): String = String.format(Locale.ROOT, "%.1f", value)

        private fun quoted(value: String): String =
            "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\""
    }
}
