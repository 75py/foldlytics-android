package com.nagopy.android.foldlytics.data

import com.nagopy.android.foldlytics.model.DailyPostureSummary
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

object LongTermCsvWriter {
    fun write(
        summaries: List<DailyPostureSummary>,
        output: Appendable,
    ) {
        output.appendLine(
            "date,zone_id,day_start_millis,day_end_millis,cover_millis,inner_millis," +
                "classified_millis,excluded_millis,inner_ratio,posture_coverage_ratio," +
                "opened_count,closed_count,evidence_gap_count",
        )
        summaries.sortedBy(DailyPostureSummary::dayStartMillis).forEach { summary ->
            val zoneId = ZoneId.of(summary.zoneId)
            val date = DateTimeFormatter.ISO_LOCAL_DATE.format(
                Instant.ofEpochMilli(summary.dayStartMillis).atZone(zoneId),
            )
            val observedMillis = summary.classifiedMillis + summary.excludedMillis
            val postureCoverageRatio = if (observedMillis == 0L) {
                0f
            } else {
                summary.classifiedMillis.toFloat() / observedMillis
            }
            output.append(date.toCsvField())
            output.append(',').append(summary.zoneId.toCsvField())
            output.append(',').append(summary.dayStartMillis.toString())
            output.append(',').append(summary.dayEndMillis.toString())
            output.append(',').append(summary.coverMillis.toString())
            output.append(',').append(summary.innerMillis.toString())
            output.append(',').append(summary.classifiedMillis.toString())
            output.append(',').append(summary.excludedMillis.toString())
            output.append(',').append(String.format(Locale.ROOT, "%.6f", summary.innerRatio))
            output.append(',').append(
                String.format(Locale.ROOT, "%.6f", postureCoverageRatio),
            )
            output.append(',').append(summary.openedCount.toString())
            output.append(',').append(summary.closedCount.toString())
            output.append(',').append(summary.evidenceGapCount.toString())
            output.appendLine()
        }
    }
}

private fun String.toCsvField(): String =
    "\"${replace("\"", "\"\"")}\""
