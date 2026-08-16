package com.nagopy.android.foldlytics.data

import com.nagopy.android.foldlytics.model.DailyPostureSummary
import java.time.LocalDate
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LongTermCsvWriterTest {
    @Test
    fun exportsEveryDailyRowAcrossThreeYearsInDateOrder() {
        val zoneId = ZoneOffset.UTC
        val firstDate = LocalDate.of(2023, 1, 1)
        val summaries = (0L until 1_095L).map { offset ->
            val date = firstDate.plusDays(offset)
            val start = date.atStartOfDay(zoneId).toInstant().toEpochMilli()
            DailyPostureSummary(
                dayStartMillis = start,
                dayEndMillis = date.plusDays(1).atStartOfDay(zoneId).toInstant().toEpochMilli(),
                zoneId = zoneId.id,
                coverMillis = 1_000L,
                innerMillis = 3_000L,
                excludedMillis = 1_000L,
                openedCount = 2,
                closedCount = 2,
                evidenceGapCount = 0,
            )
        }.reversed()
        val output = StringBuilder()

        LongTermCsvWriter.write(summaries, output)

        val lines = output.lineSequence().filter(String::isNotEmpty).toList()
        assertEquals(1_096, lines.size)
        assertTrue(lines[1].startsWith("\"2023-01-01\",\"Z\""))
        assertTrue(lines.last().startsWith("\"2025-12-30\",\"Z\""))
        assertTrue(lines[1].contains(",4000,1000,0.750000,0.800000,2,2,0"))
    }
}
