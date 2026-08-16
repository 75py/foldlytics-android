package com.nagopy.android.foldlytics.data

import com.nagopy.android.foldlytics.model.AppUsage
import com.nagopy.android.foldlytics.model.Calibration
import com.nagopy.android.foldlytics.model.DailyAppUsageSummary
import com.nagopy.android.foldlytics.model.DailyPostureSummary
import com.nagopy.android.foldlytics.model.DisplayPosture
import com.nagopy.android.foldlytics.model.FoldTransition
import com.nagopy.android.foldlytics.model.FoldTransitionDirection
import com.nagopy.android.foldlytics.model.PostureCheckpoint
import com.nagopy.android.foldlytics.model.PostureEvent
import com.nagopy.android.foldlytics.model.PostureEventSource
import com.nagopy.android.foldlytics.model.UnknownPostureReason
import com.nagopy.android.foldlytics.model.UsageAnalysis
import com.nagopy.android.foldlytics.model.UsageEventKind
import com.nagopy.android.foldlytics.model.UsageRecord
import java.time.Instant
import java.time.ZoneId

class UsageAnalyzer(
    private val isLauncherApp: (String) -> Boolean = { true },
    private val packageLabel: (String) -> String,
) {
    fun analyze(
        records: List<UsageRecord>,
        rangeStartMillis: Long,
        rangeEndMillis: Long,
        calibration: Calibration,
        checkpoints: List<PostureCheckpoint> = emptyList(),
        zoneId: ZoneId = ZoneId.systemDefault(),
        collectionGapStarts: List<Long> = emptyList(),
    ): UsageAnalysis {
        val orderedRecords = records
            .asSequence()
            .filter { it.timestampMillis < rangeEndMillis }
            .sortedWith(
                compareBy<UsageRecord> { it.timestampMillis }
                    .thenBy { it.sequenceAtTimestamp },
            )
            .toList()
        val orderedEntries = buildList<TimelineEntry> {
            orderedRecords.forEach { add(TimelineEntry.Usage(it)) }
            checkpoints.asSequence()
                .filter { it.timestampMillis < rangeEndMillis }
                .forEach { add(TimelineEntry.Checkpoint(it)) }
            collectionGapStarts.asSequence()
                .filter { it < rangeEndMillis }
                .forEach { add(TimelineEntry.CollectionGap(it)) }
        }.sortedWith(
            compareBy<TimelineEntry> { it.timestampMillis }
                .thenBy { it.order },
        )

        var lastTimestamp = orderedEntries.firstOrNull()?.timestampMillis ?: rangeStartMillis
        var screenInteractive = false
        var keyguardHidden = false
        var posture = DisplayPosture.UNKNOWN
        var unknownReason = UnknownPostureReason.NO_BASELINE
        val resumedActivities = linkedMapOf<String, String>()
        val accumulators = linkedMapOf<String, MutableAppUsage>()
        val postureEvents = mutableListOf<PostureEvent>()
        val foldTransitions = mutableListOf<FoldTransition>()
        val excludedMillisByReason = linkedMapOf<UnknownPostureReason, Long>()
        val dailyAccumulators = linkedMapOf<Long, MutableDailySummary>()

        var coverMillis = 0L
        var innerMillis = 0L
        var excludedMillis = 0L
        var multiResumeMillis = 0L
        var openedCount = 0
        var closedCount = 0
        var evidenceGapCount = 0

        fun dailyAccumulator(timestampMillis: Long): MutableDailySummary {
            val localDate = Instant.ofEpochMilli(timestampMillis).atZone(zoneId).toLocalDate()
            val dayStartMillis = localDate.atStartOfDay(zoneId).toInstant().toEpochMilli()
            return dailyAccumulators.getOrPut(dayStartMillis) {
                MutableDailySummary(
                    dayStartMillis = dayStartMillis,
                    dayEndMillis = localDate.plusDays(1)
                        .atStartOfDay(zoneId)
                        .toInstant()
                        .toEpochMilli(),
                )
            }
        }

        fun addDailyInterval(
            startMillis: Long,
            endMillis: Long,
            activePackages: Set<String>,
        ) {
            var cursor = startMillis
            while (cursor < endMillis) {
                val accumulator = dailyAccumulator(cursor)
                val sliceEnd = minOf(endMillis, accumulator.dayEndMillis)
                val duration = (sliceEnd - cursor).coerceAtLeast(0L)
                when (posture) {
                    DisplayPosture.COVER -> accumulator.coverMillis += duration
                    DisplayPosture.INNER -> accumulator.innerMillis += duration
                    DisplayPosture.UNKNOWN -> accumulator.excludedMillis += duration
                }
                activePackages.forEach { packageName ->
                    val appAccumulator = accumulator.apps.getOrPut(packageName) {
                        MutableAppUsage()
                    }
                    when (posture) {
                        DisplayPosture.COVER -> appAccumulator.coverMillis += duration
                        DisplayPosture.INNER -> appAccumulator.innerMillis += duration
                        DisplayPosture.UNKNOWN -> appAccumulator.excludedMillis += duration
                    }
                }
                cursor = sliceEnd
            }
        }

        fun addInterval(until: Long) {
            val start = maxOf(lastTimestamp, rangeStartMillis)
            val end = minOf(until, rangeEndMillis)
            val duration = (end - start).coerceAtLeast(0L)
            if (duration == 0L || !screenInteractive || !keyguardHidden) return

            when (posture) {
                DisplayPosture.COVER -> coverMillis += duration
                DisplayPosture.INNER -> innerMillis += duration
                DisplayPosture.UNKNOWN -> {
                    excludedMillis += duration
                    val reason = unknownReason
                    excludedMillisByReason[reason] =
                        excludedMillisByReason.getOrDefault(reason, 0L) + duration
                }
            }
            val activePackages = resumedActivities.values.toSet()
            addDailyInterval(start, end, activePackages)
            if (activePackages.size > 1) multiResumeMillis += duration

            activePackages.forEach { packageName ->
                val accumulator = accumulators.getOrPut(packageName) { MutableAppUsage() }
                when (posture) {
                    DisplayPosture.COVER -> accumulator.coverMillis += duration
                    DisplayPosture.INNER -> accumulator.innerMillis += duration
                    DisplayPosture.UNKNOWN -> accumulator.excludedMillis += duration
                }
            }
        }

        fun applyConfiguration(
            timestampMillis: Long,
            configuration: com.nagopy.android.foldlytics.model.DisplayConfiguration?,
            source: PostureEventSource,
            rawEventType: Int? = null,
            checkpoint: PostureCheckpoint? = null,
        ) {
            val classification = calibration.classifyWithDetails(configuration)
            val nextPosture = classification.posture
            val previousPosture = posture
            val nextUnknownReason = when {
                configuration == null || !configuration.isUsable() ->
                    UnknownPostureReason.CONFIGURATION_UNAVAILABLE
                nextPosture == DisplayPosture.UNKNOWN ->
                    UnknownPostureReason.CLASSIFICATION_UNAVAILABLE
                else -> null
            }
            if (source == PostureEventSource.CONFIGURATION_CHANGE &&
                timestampMillis in rangeStartMillis until rangeEndMillis
            ) {
                val direction = when {
                    previousPosture == DisplayPosture.COVER &&
                        nextPosture == DisplayPosture.INNER -> FoldTransitionDirection.OPENED
                    previousPosture == DisplayPosture.INNER &&
                        nextPosture == DisplayPosture.COVER -> FoldTransitionDirection.CLOSED
                    else -> null
                }
                if (direction != null) {
                    when (direction) {
                        FoldTransitionDirection.OPENED -> openedCount += 1
                        FoldTransitionDirection.CLOSED -> closedCount += 1
                    }
                    val accumulator = dailyAccumulator(timestampMillis)
                    when (direction) {
                        FoldTransitionDirection.OPENED -> accumulator.openedCount += 1
                        FoldTransitionDirection.CLOSED -> accumulator.closedCount += 1
                    }
                    foldTransitions += FoldTransition(
                        timestampMillis = timestampMillis,
                        direction = direction,
                        from = previousPosture,
                        to = nextPosture,
                    )
                }
                if (nextPosture == DisplayPosture.UNKNOWN) {
                    evidenceGapCount += 1
                    dailyAccumulator(timestampMillis).evidenceGapCount += 1
                }
            }
            posture = nextPosture
            unknownReason = nextUnknownReason ?: UnknownPostureReason.NO_BASELINE
            postureEvents += PostureEvent(
                timestampMillis = timestampMillis,
                source = source,
                posture = nextPosture,
                configuration = configuration,
                checkpointSource = checkpoint?.source,
                unknownReason = nextUnknownReason,
                rawEventType = rawEventType,
                isBeforeRange = timestampMillis < rangeStartMillis,
                coverDistance = classification.coverDistance,
                innerDistance = classification.innerDistance,
            )
        }

        orderedEntries.forEach { entry ->
            addInterval(entry.timestampMillis)

            if (entry is TimelineEntry.CollectionGap) {
                screenInteractive = false
                keyguardHidden = false
                posture = DisplayPosture.UNKNOWN
                unknownReason = UnknownPostureReason.COLLECTION_INTERRUPTION
                resumedActivities.clear()
                if (entry.timestampMillis in rangeStartMillis until rangeEndMillis) {
                    evidenceGapCount += 1
                    dailyAccumulator(entry.timestampMillis).evidenceGapCount += 1
                }
                postureEvents += PostureEvent(
                    timestampMillis = entry.timestampMillis,
                    source = PostureEventSource.COLLECTION_INTERRUPTION,
                    posture = posture,
                    configuration = null,
                    unknownReason = unknownReason,
                    isBeforeRange = entry.timestampMillis < rangeStartMillis,
                )
                lastTimestamp = entry.timestampMillis
                return@forEach
            }

            if (entry is TimelineEntry.Checkpoint) {
                applyConfiguration(
                    timestampMillis = entry.checkpoint.timestampMillis,
                    configuration = entry.checkpoint.configuration,
                    source = PostureEventSource.CHECKPOINT,
                    checkpoint = entry.checkpoint,
                )
                lastTimestamp = entry.timestampMillis
                return@forEach
            }

            val record = (entry as TimelineEntry.Usage).record

            when (record.kind) {
                UsageEventKind.ACTIVITY_RESUMED -> {
                    val packageName = record.packageName
                    if (packageName != null) {
                        resumedActivities[record.activityKey()] = packageName
                    }
                }

                UsageEventKind.ACTIVITY_PAUSED,
                UsageEventKind.ACTIVITY_STOPPED
                -> resumedActivities.remove(record.activityKey())

                UsageEventKind.CONFIGURATION_CHANGED -> {
                    applyConfiguration(
                        timestampMillis = record.timestampMillis,
                        configuration = record.configuration,
                        source = PostureEventSource.CONFIGURATION_CHANGE,
                        rawEventType = record.rawEventType,
                    )
                }

                UsageEventKind.SCREEN_INTERACTIVE -> screenInteractive = true
                UsageEventKind.SCREEN_NON_INTERACTIVE -> screenInteractive = false
                UsageEventKind.KEYGUARD_HIDDEN -> keyguardHidden = true
                UsageEventKind.KEYGUARD_SHOWN -> keyguardHidden = false

                UsageEventKind.DEVICE_STARTUP,
                UsageEventKind.DEVICE_SHUTDOWN
                -> {
                    screenInteractive = false
                    keyguardHidden = false
                    posture = DisplayPosture.UNKNOWN
                    unknownReason = UnknownPostureReason.AFTER_DEVICE_RESTART
                    resumedActivities.clear()
                    if (record.timestampMillis in rangeStartMillis until rangeEndMillis) {
                        evidenceGapCount += 1
                        dailyAccumulator(record.timestampMillis).evidenceGapCount += 1
                    }
                    postureEvents += PostureEvent(
                        timestampMillis = record.timestampMillis,
                        source = if (record.kind == UsageEventKind.DEVICE_STARTUP) {
                            PostureEventSource.DEVICE_STARTUP
                        } else {
                            PostureEventSource.DEVICE_SHUTDOWN
                        },
                        posture = posture,
                        configuration = null,
                        unknownReason = unknownReason,
                        rawEventType = record.rawEventType,
                        isBeforeRange = record.timestampMillis < rangeStartMillis,
                    )
                }

                UsageEventKind.OTHER -> Unit
            }

            lastTimestamp = record.timestampMillis
        }

        addInterval(rangeEndMillis)

        val appUsage = accumulators.map { (packageName, value) ->
            AppUsage(
                packageName = packageName,
                label = packageLabel(packageName),
                coverMillis = value.coverMillis,
                innerMillis = value.innerMillis,
                excludedMillis = value.excludedMillis,
                isLauncherApp = isLauncherApp(packageName),
            )
        }.filter { it.observedMillis > 0L }
            .sortedWith(
                compareByDescending<AppUsage> { it.classifiedMillis }
                    .thenByDescending { it.observedMillis },
            )
        val orderedDailyAccumulators = dailyAccumulators.values.sortedBy { it.dayStartMillis }
        val dailySummaries = orderedDailyAccumulators.map { accumulator ->
            DailyPostureSummary(
                dayStartMillis = accumulator.dayStartMillis,
                dayEndMillis = accumulator.dayEndMillis,
                zoneId = zoneId.id,
                coverMillis = accumulator.coverMillis,
                innerMillis = accumulator.innerMillis,
                excludedMillis = accumulator.excludedMillis,
                openedCount = accumulator.openedCount,
                closedCount = accumulator.closedCount,
                evidenceGapCount = accumulator.evidenceGapCount,
            )
        }
        val dailyAppSummaries = orderedDailyAccumulators.flatMap { accumulator ->
            accumulator.apps.map { (packageName, value) ->
                DailyAppUsageSummary(
                    dayStartMillis = accumulator.dayStartMillis,
                    dayEndMillis = accumulator.dayEndMillis,
                    zoneId = zoneId.id,
                    packageName = packageName,
                    coverMillis = value.coverMillis,
                    innerMillis = value.innerMillis,
                    excludedMillis = value.excludedMillis,
                )
            }
        }.filter { it.observedMillis > 0L }

        return UsageAnalysis(
            rangeStartMillis = rangeStartMillis,
            rangeEndMillis = rangeEndMillis,
            coverMillis = coverMillis,
            innerMillis = innerMillis,
            excludedPostureMillis = excludedMillis,
            excludedPostureMillisByReason = excludedMillisByReason,
            openedCount = openedCount,
            closedCount = closedCount,
            evidenceGapCount = evidenceGapCount,
            foldTransitions = foldTransitions,
            dailySummaries = dailySummaries,
            dailyAppSummaries = dailyAppSummaries,
            apps = appUsage,
            postureEvents = postureEvents.asReversed(),
            eventCount = orderedRecords.count { it.timestampMillis >= rangeStartMillis },
            multiResumeMillis = multiResumeMillis,
        )
    }

    private fun UsageRecord.activityKey(): String =
        "${packageName.orEmpty()}|${className.orEmpty()}"

    private data class MutableAppUsage(
        var coverMillis: Long = 0L,
        var innerMillis: Long = 0L,
        var excludedMillis: Long = 0L,
    )

    private data class MutableDailySummary(
        val dayStartMillis: Long,
        val dayEndMillis: Long,
        var coverMillis: Long = 0L,
        var innerMillis: Long = 0L,
        var excludedMillis: Long = 0L,
        var openedCount: Int = 0,
        var closedCount: Int = 0,
        var evidenceGapCount: Int = 0,
        val apps: MutableMap<String, MutableAppUsage> = linkedMapOf(),
    )

    private sealed interface TimelineEntry {
        val timestampMillis: Long
        val order: Int

        data class Usage(val record: UsageRecord) : TimelineEntry {
            override val timestampMillis: Long = record.timestampMillis
            override val order: Int = 0
        }

        data class Checkpoint(val checkpoint: PostureCheckpoint) : TimelineEntry {
            override val timestampMillis: Long = checkpoint.timestampMillis
            override val order: Int = 1
        }

        data class CollectionGap(
            override val timestampMillis: Long,
        ) : TimelineEntry {
            override val order: Int = 2
        }
    }
}
