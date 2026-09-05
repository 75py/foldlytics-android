package com.nagopy.android.foldlytics.data

import com.nagopy.android.foldlytics.model.Calibration
import com.nagopy.android.foldlytics.model.DisplayConfiguration
import com.nagopy.android.foldlytics.model.DisplayPosture
import com.nagopy.android.foldlytics.model.InnerDisplaySession
import com.nagopy.android.foldlytics.model.PostureCheckpoint
import com.nagopy.android.foldlytics.model.UsageEventKind
import com.nagopy.android.foldlytics.model.UsageRecord

/**
 * Builds inner-display sessions from one ordered evidence stream.
 *
 * A single instance may process multiple adjacent chunks. This lets the repository bound each
 * database read without losing a session that crosses an aggregation-chunk boundary.
 */
class InnerDisplaySessionAnalyzer(
    private val calibration: Calibration,
    private val analysisStartMillis: Long,
) {
    private var lastTimestampMillis: Long? = null
    private var screenInteractive = EvidenceState.UNKNOWN
    private var keyguardHidden = EvidenceState.UNKNOWN
    private var posture = DisplayPosture.UNKNOWN
    private val configurationTracker = DisplayConfigurationTracker()
    private val activityTracker = ActivityVisibilityTracker()
    private var pendingSession: PendingSession? = null
    private val completedSessions = mutableListOf<InnerDisplaySession>()

    fun processChunk(
        records: List<UsageRecord>,
        checkpoints: List<PostureCheckpoint>,
        deviceStateCheckpoints: List<DeviceStateCheckpoint> = emptyList(),
        collectionGapStarts: List<Long>,
        chunkEndMillis: Long,
    ) {
        val entries = buildList<TimelineEntry> {
            records.asSequence()
                .filter { it.timestampMillis < chunkEndMillis }
                .forEach { add(TimelineEntry.Usage(it)) }
            checkpoints.asSequence()
                .filter { it.timestampMillis < chunkEndMillis }
                .forEach { add(TimelineEntry.Checkpoint(it)) }
            deviceStateCheckpoints.asSequence()
                .filter { it.observedAtMillis < chunkEndMillis }
                .forEach { add(TimelineEntry.DeviceState(it)) }
            collectionGapStarts.asSequence()
                .filter { it < chunkEndMillis }
                .forEach { add(TimelineEntry.CollectionGap(it)) }
        }.sortedWith(
            compareBy<TimelineEntry> { it.timestampMillis }
                .thenBy { it.order }
                .thenBy { it.sequenceAtTimestamp },
        )

        entries.forEach { entry ->
            advanceTo(entry.timestampMillis)
            when (entry) {
                is TimelineEntry.CollectionGap -> resetEvidence()
                is TimelineEntry.DeviceState -> {
                    screenInteractive = entry.checkpoint.screenInteractive.toEvidenceState()
                    keyguardHidden = entry.checkpoint.keyguardHidden.toEvidenceState()
                }

                is TimelineEntry.Checkpoint -> applyConfiguration(
                    timestampMillis = entry.checkpoint.timestampMillis,
                    configuration = entry.checkpoint.configuration,
                    isConfigurationEvent = false,
                    sequenceAtTimestamp = 0,
                )

                is TimelineEntry.Usage -> applyUsageRecord(entry.record)
            }
        }
        advanceTo(chunkEndMillis)
    }

    fun sessionsAtEnd(): List<InnerDisplaySession> = buildList {
        addAll(completedSessions)
        pendingSession
            ?.takeUnless(PendingSession::hasUnknownEvidence)
            ?.let { add(it.toModel(closedAtMillis = null)) }
    }

    private fun advanceTo(timestampMillis: Long) {
        val previousTimestamp = lastTimestampMillis
        if (previousTimestamp == null) {
            lastTimestampMillis = timestampMillis
            return
        }
        require(timestampMillis >= previousTimestamp) {
            "Session evidence must be processed in chronological order"
        }
        val duration = timestampMillis - previousTimestamp
        val pending = pendingSession
        if (duration > 0L && pending != null && posture == DisplayPosture.INNER) {
            when (innerIntervalState()) {
                InnerIntervalState.ACTIVE -> {
                    pending.innerActiveMillis += duration
                    val packageName = activityTracker.snapshot.singleDefinitePackageForSessionOrNull()
                    if (packageName != null) {
                        pending.appUsageMillis[packageName] =
                            pending.appUsageMillis.getOrDefault(packageName, 0L) + duration
                    }
                }

                InnerIntervalState.INACTIVE -> Unit
                InnerIntervalState.UNKNOWN -> pending.hasUnknownEvidence = true
            }
        }
        lastTimestampMillis = timestampMillis
    }

    private fun innerIntervalState(): InnerIntervalState = when {
        screenInteractive == EvidenceState.FALSE || keyguardHidden == EvidenceState.FALSE ->
            InnerIntervalState.INACTIVE

        screenInteractive == EvidenceState.TRUE && keyguardHidden == EvidenceState.TRUE ->
            InnerIntervalState.ACTIVE

        else -> InnerIntervalState.UNKNOWN
    }

    private fun applyUsageRecord(record: UsageRecord) {
        when (record.kind) {
            UsageEventKind.ACTIVITY_RESUMED -> activityTracker.apply(record)

            UsageEventKind.ACTIVITY_PAUSED,
            UsageEventKind.ACTIVITY_STOPPED,
            -> activityTracker.apply(record)

            UsageEventKind.CONFIGURATION_CHANGED -> applyConfiguration(
                timestampMillis = record.timestampMillis,
                configuration = record.configuration,
                isConfigurationEvent = true,
                sequenceAtTimestamp = record.sequenceAtTimestamp,
            )

            UsageEventKind.SCREEN_INTERACTIVE -> screenInteractive = EvidenceState.TRUE
            UsageEventKind.SCREEN_NON_INTERACTIVE -> screenInteractive = EvidenceState.FALSE
            UsageEventKind.KEYGUARD_HIDDEN -> keyguardHidden = EvidenceState.TRUE
            UsageEventKind.KEYGUARD_SHOWN -> keyguardHidden = EvidenceState.FALSE
            UsageEventKind.DEVICE_STARTUP,
            UsageEventKind.DEVICE_SHUTDOWN,
            -> resetEvidence()

            UsageEventKind.OTHER -> Unit
        }
    }

    private fun applyConfiguration(
        timestampMillis: Long,
        configuration: DisplayConfiguration?,
        isConfigurationEvent: Boolean,
        sequenceAtTimestamp: Int,
    ) {
        val previousPosture = posture
        val resolvedConfiguration = if (isConfigurationEvent) {
            configurationTracker.applyDelta(configuration)
        } else {
            configurationTracker.replaceBaseline(requireNotNull(configuration))
        }
        val nextPosture = calibration.classify(resolvedConfiguration)
        val confirmsClose = isConfigurationEvent &&
            previousPosture == DisplayPosture.INNER &&
            nextPosture == DisplayPosture.COVER

        pendingSession?.let { pending ->
            when {
                confirmsClose -> {
                    if (!pending.hasUnknownEvidence) {
                        completedSessions += pending.toModel(closedAtMillis = timestampMillis)
                    }
                    pendingSession = null
                }

                nextPosture != DisplayPosture.INNER -> pendingSession = null
            }
        }

        if (
            isConfigurationEvent &&
            timestampMillis >= analysisStartMillis &&
            previousPosture == DisplayPosture.COVER &&
            nextPosture == DisplayPosture.INNER
        ) {
            pendingSession = PendingSession(
                openedAtMillis = timestampMillis,
                openedSequenceAtTimestamp = sequenceAtTimestamp,
            )
        }
        posture = nextPosture
    }

    private fun resetEvidence() {
        screenInteractive = EvidenceState.UNKNOWN
        keyguardHidden = EvidenceState.UNKNOWN
        posture = DisplayPosture.UNKNOWN
        configurationTracker.reset()
        activityTracker.reset()
        pendingSession = null
    }

    private data class PendingSession(
        val openedAtMillis: Long,
        val openedSequenceAtTimestamp: Int,
        var innerActiveMillis: Long = 0L,
        val appUsageMillis: LinkedHashMap<String, Long> = linkedMapOf(),
        var hasUnknownEvidence: Boolean = false,
    ) {
        fun toModel(closedAtMillis: Long?): InnerDisplaySession = InnerDisplaySession(
            openedAtMillis = openedAtMillis,
            openedSequenceAtTimestamp = openedSequenceAtTimestamp,
            closedAtMillis = closedAtMillis,
            innerActiveMillis = innerActiveMillis,
            appUsageMillis = appUsageMillis.toMap(),
        )
    }

    private enum class EvidenceState {
        TRUE,
        FALSE,
        UNKNOWN,
    }

    private enum class InnerIntervalState {
        ACTIVE,
        INACTIVE,
        UNKNOWN,
    }

    private fun Boolean.toEvidenceState(): EvidenceState =
        if (this) EvidenceState.TRUE else EvidenceState.FALSE

    private sealed interface TimelineEntry {
        val timestampMillis: Long
        val order: Int
        val sequenceAtTimestamp: Int

        data class Usage(val record: UsageRecord) : TimelineEntry {
            override val timestampMillis: Long = record.timestampMillis
            override val order: Int = 1
            override val sequenceAtTimestamp: Int = record.sequenceAtTimestamp
        }

        data class Checkpoint(val checkpoint: PostureCheckpoint) : TimelineEntry {
            override val timestampMillis: Long = checkpoint.timestampMillis
            override val order: Int = 2
            override val sequenceAtTimestamp: Int = 0
        }

        data class DeviceState(val checkpoint: DeviceStateCheckpoint) : TimelineEntry {
            override val timestampMillis: Long = checkpoint.observedAtMillis
            // The observation precedes the query end. Raw events at the same millisecond win.
            override val order: Int = 0
            override val sequenceAtTimestamp: Int = 0
        }

        data class CollectionGap(
            override val timestampMillis: Long,
        ) : TimelineEntry {
            override val order: Int = 3
            override val sequenceAtTimestamp: Int = 0
        }
    }
}
