package com.nagopy.android.foldlytics.data

import com.nagopy.android.foldlytics.model.CollectionHealth
import com.nagopy.android.foldlytics.model.DailyPostureSummary
import com.nagopy.android.foldlytics.model.LongTermBucket
import com.nagopy.android.foldlytics.model.LongTermInsights
import com.nagopy.android.foldlytics.model.LongTermPeriod
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.temporal.ChronoUnit

class LongTermAnalyzer {
    fun analyze(
        summaries: List<DailyPostureSummary>,
        period: LongTermPeriod,
        rangeEndMillis: Long,
        zoneId: ZoneId,
    ): LongTermInsights {
        val safeRangeEnd = rangeEndMillis.coerceAtLeast(0L)
        val endDate = Instant.ofEpochMilli((safeRangeEnd - 1L).coerceAtLeast(0L))
            .atZone(zoneId)
            .toLocalDate()
        val startDate = endDate.minusDays(period.days - 1L)

        return analyzeDates(
            summaries = summaries,
            startDate = startDate,
            endDate = endDate,
            rangeEndMillis = safeRangeEnd,
            zoneId = zoneId,
        )
    }

    fun analyzeRange(
        summaries: List<DailyPostureSummary>,
        rangeStartMillis: Long,
        rangeEndMillis: Long,
        zoneId: ZoneId,
    ): LongTermInsights {
        require(rangeStartMillis < rangeEndMillis) { "Analysis range must not be empty" }
        val startDate = Instant.ofEpochMilli(rangeStartMillis.coerceAtLeast(0L))
            .atZone(zoneId)
            .toLocalDate()
        val endDate = Instant.ofEpochMilli((rangeEndMillis - 1L).coerceAtLeast(0L))
            .atZone(zoneId)
            .toLocalDate()
        require(!startDate.isAfter(endDate)) { "Analysis range dates are reversed" }

        return analyzeDates(
            summaries = summaries,
            startDate = startDate,
            endDate = endDate,
            rangeEndMillis = rangeEndMillis,
            zoneId = zoneId,
        )
    }

    private fun analyzeDates(
        summaries: List<DailyPostureSummary>,
        startDate: LocalDate,
        endDate: LocalDate,
        rangeEndMillis: Long,
        zoneId: ZoneId,
    ): LongTermInsights {
        val datedSummaries = summaries.map { summary ->
            DatedSummary(
                date = Instant.ofEpochMilli(summary.dayStartMillis)
                    .atZone(zoneId)
                    .toLocalDate(),
                summary = summary,
            )
        }
        val periodSummaries = datedSummaries.filter { it.date in startDate..endDate }
        val buckets = createBucketRanges(startDate, endDate).map { range ->
            val values = periodSummaries
                .filter { it.date in range.startDate..range.endDateInclusive }
                .map(DatedSummary::summary)
            LongTermBucket(
                startMillis = range.startDate.atStartOfDay(zoneId).toInstant().toEpochMilli(),
                endMillis = minOf(
                    range.endDateInclusive.plusDays(1)
                        .atStartOfDay(zoneId)
                        .toInstant()
                        .toEpochMilli(),
                    rangeEndMillis,
                ),
                coverMillis = values.sumOf { it.coverMillis },
                innerMillis = values.sumOf { it.innerMillis },
                excludedMillis = values.sumOf { it.excludedMillis },
                openedCount = values.sumOf { it.openedCount },
                closedCount = values.sumOf { it.closedCount },
                observedDayCount = values.count { it.hasRecordedEvidence() },
                evidenceGapDayCount = values.count {
                    it.evidenceGapCount > 0 || it.excludedMillis > 0L
                },
            )
        }
        val values = periodSummaries.map(DatedSummary::summary)
        val comparison = thirtyDayComparison(datedSummaries, endDate)

        return LongTermInsights(
            rangeStartMillis = startDate.atStartOfDay(zoneId).toInstant().toEpochMilli(),
            rangeEndMillis = rangeEndMillis,
            coverMillis = values.sumOf { it.coverMillis },
            innerMillis = values.sumOf { it.innerMillis },
            excludedMillis = values.sumOf { it.excludedMillis },
            openedCount = values.sumOf { it.openedCount },
            closedCount = values.sumOf { it.closedCount },
            calendarDayCount = (ChronoUnit.DAYS.between(startDate, endDate) + 1L).toInt(),
            observedDayCount = values.count { it.hasRecordedEvidence() },
            innerUsedDayCount = values.count { it.innerMillis > 0L },
            evidenceGapDayCount = values.count {
                it.evidenceGapCount > 0 || it.excludedMillis > 0L
            },
            buckets = buckets,
            firstThirtyDayInnerRatio = comparison?.firstRatio,
            recentThirtyDayInnerRatio = comparison?.recentRatio,
        )
    }

    fun collectionHealth(attempts: List<SyncAttempt>): CollectionHealth {
        val orderedSuccesses = attempts
            .asSequence()
            .filter { it.status == SyncAttemptStatus.SUCCESS }
            .sortedBy { it.attemptedAtMillis }
            .toList()
        val longestGap = orderedSuccesses.zipWithNext { previous, next ->
            (next.attemptedAtMillis - previous.attemptedAtMillis).coerceAtLeast(0L)
        }.maxOrNull()
        return CollectionHealth(
            recordedAttemptCount = attempts.size,
            unsuccessfulAttemptCount = attempts.count {
                it.status != SyncAttemptStatus.SUCCESS
            },
            longestSuccessfulSyncGapMillis = longestGap,
            collectionInterruptionCount = detectCollectionGaps(attempts).size,
        )
    }

    private fun createBucketRanges(
        startDate: LocalDate,
        endDate: LocalDate,
    ): List<DateRange> {
        val calendarDayCount = ChronoUnit.DAYS.between(startDate, endDate) + 1L
        return when {
            calendarDayCount <= LongTermPeriod.DAYS_30.days ->
                generateSequence(startDate) { current ->
                    current.plusDays(1).takeIf { it <= endDate }
                }.map { date -> DateRange(date, date) }.toList()

            calendarDayCount <= LongTermPeriod.DAYS_90.days -> buildList {
                var cursor = startDate
                while (cursor <= endDate) {
                    val bucketEnd = minOf(cursor.plusDays(6), endDate)
                    add(DateRange(cursor, bucketEnd))
                    cursor = bucketEnd.plusDays(1)
                }
            }

            else -> buildList {
                var month = YearMonth.from(startDate)
                val lastMonth = YearMonth.from(endDate)
                while (month <= lastMonth) {
                    add(
                        DateRange(
                            startDate = maxOf(month.atDay(1), startDate),
                            endDateInclusive = minOf(month.atEndOfMonth(), endDate),
                        ),
                    )
                    month = month.plusMonths(1)
                }
            }
        }
    }

    private fun thirtyDayComparison(
        summaries: List<DatedSummary>,
        endDate: LocalDate,
    ): ThirtyDayComparison? {
        val observed = summaries.filter { it.summary.observedMillis > 0L }
        val firstDate = observed.minOfOrNull { it.date } ?: return null
        if (ChronoUnit.DAYS.between(firstDate, endDate) < 59L) return null

        val firstEnd = firstDate.plusDays(29)
        val recentStart = endDate.minusDays(29)
        val firstValues = summaries
            .filter { it.date in firstDate..firstEnd }
            .map(DatedSummary::summary)
        val recentValues = summaries
            .filter { it.date in recentStart..endDate }
            .map(DatedSummary::summary)
        val firstRatio = firstValues.innerRatioOrNull() ?: return null
        val recentRatio = recentValues.innerRatioOrNull() ?: return null
        return ThirtyDayComparison(firstRatio, recentRatio)
    }

    private fun List<DailyPostureSummary>.innerRatioOrNull(): Float? {
        val cover = sumOf { it.coverMillis }
        val inner = sumOf { it.innerMillis }
        val classified = cover + inner
        return if (classified == 0L) null else inner.toFloat() / classified
    }

    private fun DailyPostureSummary.hasRecordedEvidence(): Boolean =
        observedMillis > 0L || openedCount > 0 || closedCount > 0 || evidenceGapCount > 0

    private data class DatedSummary(
        val date: LocalDate,
        val summary: DailyPostureSummary,
    )

    private data class DateRange(
        val startDate: LocalDate,
        val endDateInclusive: LocalDate,
    )

    private data class ThirtyDayComparison(
        val firstRatio: Float,
        val recentRatio: Float,
    )
}
