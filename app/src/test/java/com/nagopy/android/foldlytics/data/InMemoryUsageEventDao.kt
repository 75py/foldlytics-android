package com.nagopy.android.foldlytics.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/**
 * A [UsageEventDao] over in-memory rows with the same ordering, filters and seed-snapshot
 * invalidation triggers as Room. [rowsRead] counts every usage-event row a query materializes.
 */
internal class InMemoryUsageEventDao(
    initialEvents: List<UsageEventEntity> = emptyList(),
    private val triggersInstalled: Boolean = true,
) : UsageEventDao {
    private val events = linkedMapOf<String, UsageEventEntity>()
    private val snapshots = linkedMapOf<Long, AnalysisSeedSnapshotEntity>()
    private val snapshotEvents = linkedSetOf<AnalysisSeedSnapshotEventEntity>()

    var rowsRead: Long = 0L
        private set

    /** Thrown from the snapshot metadata write, after the snapshot's event rows are written. */
    var snapshotWriteFailure: Exception? = null

    /** Thrown from pruning old snapshot metadata. Nothing is rolled back, unlike Room. */
    var snapshotPruneFailure: Exception? = null

    /** Event-row counts of every readable snapshot, by boundary. */
    val snapshotEventCounts: Map<Long, Int>
        get() = snapshots.keys.sorted().associateWith(::snapshotEventCount)

    init {
        initialEvents.forEach { events[it.eventKey] = it }
    }

    val snapshotBoundaries: List<Long>
        get() = snapshots.keys.sorted()

    val snapshotEventBoundaries: Set<Long>
        get() = snapshotEvents.mapTo(sortedSetOf(), AnalysisSeedSnapshotEventEntity::boundaryMillis)

    fun snapshotEventCount(boundaryMillis: Long): Int =
        snapshotEvents.count { it.boundaryMillis == boundaryMillis }

    fun resetRowsRead() {
        rowsRead = 0L
    }

    fun deleteEvent(eventKey: String) {
        val removed = events.remove(eventKey) ?: return
        invalidateAfter(removed.timestampMillis)
    }

    private fun ordered(): Sequence<UsageEventEntity> = events.values.asSequence()
        .sortedWith(
            compareBy<UsageEventEntity> { it.timestampMillis }
                .thenBy { it.sequenceAtTimestamp }
                .thenBy { it.eventKey },
        )

    private fun Sequence<UsageEventEntity>.read(): List<UsageEventEntity> =
        toList().also { rowsRead += it.size }

    private fun invalidateAfter(timestampMillis: Long) {
        if (!triggersInstalled) return
        snapshots.keys.removeAll { it > timestampMillis }
        snapshotEvents.removeAll { it.boundaryMillis > timestampMillis }
    }

    override suspend fun loadEvents(beginMillis: Long, endMillis: Long): List<UsageEventEntity> =
        ordered().filter { it.timestampMillis in beginMillis until endMillis }.read()

    override suspend fun loadDeviceEvents(
        beginMillis: Long,
        endMillis: Long,
        rawEventTypes: List<Int>,
    ): List<UsageEventEntity> = ordered()
        .filter { it.timestampMillis in beginMillis until endMillis }
        .filter { it.rawEventType in rawEventTypes }
        .read()

    override suspend fun loadLatestDeviceEventsBefore(
        endMillis: Long,
        rawEventTypes: List<Int>,
    ): List<UsageEventEntity> {
        val matching = ordered()
            .filter { it.timestampMillis < endMillis && it.rawEventType in rawEventTypes }
            .toList()
        val latest = matching.maxOfOrNull(UsageEventEntity::timestampMillis)
            ?: return emptyList()
        return matching.asSequence().filter { it.timestampMillis == latest }.read()
    }

    override suspend fun loadActivityEventsBefore(
        endMillis: Long,
        rawEventTypes: List<Int>,
    ): List<UsageEventEntity> = ordered()
        .filter { it.timestampMillis < endMillis }
        .filter { it.rawEventType in rawEventTypes && it.packageName != null }
        .read()

    override suspend fun loadActivityEventsBetween(
        beginMillis: Long,
        endMillis: Long,
        rawEventTypes: List<Int>,
    ): List<UsageEventEntity> = ordered()
        .filter { it.timestampMillis in beginMillis until endMillis }
        .filter { it.rawEventType in rawEventTypes && it.packageName != null }
        .read()

    override suspend fun countAnalysisSeedSnapshotTriggers(): Int =
        if (triggersInstalled) AnalysisSeedSnapshotTriggers.names.size else 0

    override suspend fun loadAnalysisSeedSnapshotsAtOrBefore(
        boundaryMillis: Long,
        compactionVersion: Int,
    ): List<AnalysisSeedSnapshotEntity> = snapshots.values
        .filter { it.boundaryMillis <= boundaryMillis && it.compactionVersion == compactionVersion }
        .sortedByDescending(AnalysisSeedSnapshotEntity::boundaryMillis)

    override suspend fun loadAnalysisSeedSnapshotEvents(
        boundaryMillis: Long,
        rawEventTypes: List<Int>,
    ): List<UsageEventEntity> {
        val keys = snapshotEvents
            .filter { it.boundaryMillis == boundaryMillis }
            .mapTo(mutableSetOf(), AnalysisSeedSnapshotEventEntity::eventKey)
        return ordered()
            .filter { it.eventKey in keys && it.rawEventType in rawEventTypes }
            .read()
    }

    override suspend fun upsertAnalysisSeedSnapshot(snapshot: AnalysisSeedSnapshotEntity) {
        snapshotWriteFailure?.let { throw it }
        snapshots[snapshot.boundaryMillis] = snapshot
    }

    override suspend fun countAnalysisSeedSnapshotsAfter(boundaryMillis: Long): Int =
        snapshots.keys.count { it > boundaryMillis }

    override suspend fun deleteAnalysisSeedSnapshot(boundaryMillis: Long) {
        snapshots.remove(boundaryMillis)
    }

    override suspend fun insertAnalysisSeedSnapshotEvents(
        events: List<AnalysisSeedSnapshotEventEntity>,
    ) {
        snapshotEvents += events
    }

    override suspend fun deleteAnalysisSeedSnapshotEvents(boundaryMillis: Long) {
        snapshotEvents.removeAll { it.boundaryMillis == boundaryMillis }
    }

    override suspend fun deleteOrphanAnalysisSeedSnapshotEvents() {
        snapshotEvents.removeAll { it.boundaryMillis !in snapshots }
    }

    override suspend fun deleteAnalysisSeedSnapshotsBeyondNewest(keep: Int) {
        snapshotPruneFailure?.let { throw it }
        val kept = snapshots.keys.sortedDescending().take(keep).toSet()
        snapshots.keys.retainAll(kept)
    }

    override suspend fun earliestEventTimestamp(): Long? =
        events.values.minOfOrNull(UsageEventEntity::timestampMillis)

    override suspend fun earliestDeviceEventTimestamp(rawEventTypes: List<Int>): Long? =
        events.values.filter { it.rawEventType in rawEventTypes }
            .minOfOrNull(UsageEventEntity::timestampMillis)

    override suspend fun loadSyncState(): UsageSyncStateEntity? = null

    override fun observeSyncState(): Flow<UsageSyncStateEntity?> = flowOf(null)

    override fun observeSyncHistoryRevision(): Flow<Long> = flowOf(0L)

    override suspend fun insertEvents(events: List<UsageEventEntity>): List<Long> =
        events.map { event ->
            if (event.eventKey in this.events) {
                -1L
            } else {
                this.events[event.eventKey] = event
                invalidateAfter(event.timestampMillis)
                this.events.size.toLong()
            }
        }

    override suspend fun loadEventsAtTimestamps(timestamps: List<Long>): List<UsageEventEntity> =
        ordered().filter { it.timestampMillis in timestamps }.read()

    override suspend fun updateEvents(events: List<UsageEventEntity>): Int =
        events.count { event ->
            val previous = this.events[event.eventKey] ?: return@count false
            this.events[event.eventKey] = event
            invalidateAfter(minOf(previous.timestampMillis, event.timestampMillis))
            true
        }

    override suspend fun upsertSyncState(state: UsageSyncStateEntity) = Unit

    override suspend fun insertSyncHistory(attempt: SyncHistoryEntity): Long = 1L

    override suspend fun loadSyncHistory(
        beginMillis: Long,
        endMillis: Long,
    ): List<SyncHistoryEntity> = emptyList()

    override suspend fun loadDeviceStateCheckpoints(
        beginMillis: Long,
        endMillis: Long,
    ): List<SyncHistoryEntity> = emptyList()

    override suspend fun loadLatestDeviceStateCheckpointBefore(
        endMillis: Long,
    ): SyncHistoryEntity? = null

    override suspend fun earliestDeviceStateCheckpointTimestamp(): Long? = null

    override suspend fun latestSuccessfulSyncHistoryIdThrough(
        syncedThroughMillis: Long,
    ): Long? = null

    override suspend fun earliestSuccessfulSyncQueryBeginAfter(
        afterHistoryId: Long,
        throughHistoryId: Long,
        syncedThroughMillis: Long,
    ): Long? = null
}
