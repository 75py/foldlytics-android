package com.nagopy.android.foldlytics.data

import com.nagopy.android.foldlytics.model.UsageEventKind
import com.nagopy.android.foldlytics.model.UsageRecord
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UsageSyncRepositoryTest {
    @Test
    fun firstSyncRequestsAllRecoverableHistoryAndAdvancesCursor() = runBlocking {
        val source = FakeUsageEventSource(
            UsageReadResult.Success(listOf(record(timestampMillis = 9_000_000L))),
        )
        val store = FakeUsageEventStore()
        val repository = repository(source, store, nowMillis = 10_000_000L)

        val result = repository.sync() as UsageSyncResult.Success

        assertEquals(0L to 10_000_000L, source.requestedRanges.single())
        assertEquals(1, result.insertedEventCount)
        assertEquals(10_000_000L, store.state?.lastSuccessfulEndMillis)
        assertEquals(0L, store.state?.lastQueryBeginMillis)
        assertEquals(SyncAttemptStatus.SUCCESS, store.attempts.single().status)
        assertEquals(1, store.attempts.single().insertedEventCount)
    }

    @Test
    fun laterSyncUsesOneHourOverlap() = runBlocking {
        val source = FakeUsageEventSource(UsageReadResult.Success(emptyList()))
        val store = FakeUsageEventStore(
            initialState = UsageSyncState(
                lastSuccessfulEndMillis = 10_000_000L,
                lastSuccessfulAtMillis = 10_000_000L,
                lastQueryBeginMillis = 0L,
                lastInsertedEventCount = 0,
            ),
        )
        val repository = repository(source, store, nowMillis = 14_000_000L)

        repository.sync()

        assertEquals(6_400_000L to 14_000_000L, source.requestedRanges.single())
        assertEquals(14_000_000L, store.state?.lastSuccessfulEndMillis)
    }

    @Test
    fun overlappingSyncDoesNotStoreTheSameEventTwice() = runBlocking {
        val usageRecord = record(timestampMillis = 9_000_000L)
        val source = FakeUsageEventSource(UsageReadResult.Success(listOf(usageRecord)))
        val store = FakeUsageEventStore()
        var nowMillis = 10_000_000L
        val repository = UsageSyncRepository(
            eventSource = source,
            eventStore = store,
            currentTimeMillis = { nowMillis },
            ioDispatcher = Dispatchers.Unconfined,
        )

        val first = repository.sync() as UsageSyncResult.Success
        nowMillis = 11_000_000L
        val second = repository.sync() as UsageSyncResult.Success

        assertEquals(1, first.insertedEventCount)
        assertEquals(0, second.insertedEventCount)
        assertEquals(1, store.records.size)
    }

    @Test
    fun overlappingSyncDeduplicatesAnEventWhenItsFilteredSequenceChanges() = runBlocking {
        val usageRecord = record(
            timestampMillis = 9_000_000L,
            sequenceAtTimestamp = 2,
        )
        val source = FakeUsageEventSource(UsageReadResult.Success(listOf(usageRecord)))
        val store = FakeUsageEventStore()
        var nowMillis = 10_000_000L
        val repository = UsageSyncRepository(
            eventSource = source,
            eventStore = store,
            currentTimeMillis = { nowMillis },
            ioDispatcher = Dispatchers.Unconfined,
        )

        val first = repository.sync() as UsageSyncResult.Success
        source.result = UsageReadResult.Success(
            listOf(usageRecord.copy(sequenceAtTimestamp = 0)),
        )
        nowMillis = 11_000_000L
        val second = repository.sync() as UsageSyncResult.Success

        assertEquals(1, first.insertedEventCount)
        assertEquals(0, second.insertedEventCount)
        assertEquals(listOf(usageRecord), store.records)
    }

    @Test
    fun unavailableReadDoesNotAdvanceCursor() = runBlocking {
        val initialState = UsageSyncState(
            lastSuccessfulEndMillis = 10_000_000L,
            lastSuccessfulAtMillis = 10_000_000L,
            lastQueryBeginMillis = 0L,
            lastInsertedEventCount = 4,
        )
        val source = FakeUsageEventSource(
            UsageReadResult.Unavailable(UsageReadUnavailableReason.PERMISSION_DENIED),
        )
        val store = FakeUsageEventStore(initialState)
        val repository = repository(source, store, nowMillis = 14_000_000L)

        val result = repository.sync()

        assertTrue(result is UsageSyncResult.Skipped)
        assertEquals(initialState, store.state)
        assertEquals(0, store.persistCallCount)
        assertEquals(SyncAttemptStatus.PERMISSION_DENIED, store.attempts.single().status)
    }

    @Test
    fun failedDatabaseWriteDoesNotAdvanceCursor() = runBlocking {
        val checkpoint = DeviceStateCheckpoint(
            observedAtMillis = 9_999_000L,
            screenInteractive = true,
            keyguardHidden = true,
        )
        val source = FakeUsageEventSource(
            UsageReadResult.Success(listOf(record(timestampMillis = 9_000_000L))),
            checkpoint,
        )
        val store = FakeUsageEventStore(failPersistence = true)
        val repository = repository(source, store, nowMillis = 10_000_000L)

        val result = repository.sync()

        assertTrue(result is UsageSyncResult.Failed)
        assertNull(store.state)
        assertTrue(store.records.isEmpty())
        assertTrue(store.attempts.isEmpty())
    }

    @Test
    fun failedReadIsAuditedWithoutAdvancingCursor() = runBlocking {
        val source = FakeUsageEventSource(
            UsageReadResult.Failure(IOException("reader failed")),
            DeviceStateCheckpoint(
                observedAtMillis = 9_999_000L,
                screenInteractive = true,
                keyguardHidden = true,
            ),
        )
        val store = FakeUsageEventStore()
        val repository = repository(source, store, nowMillis = 10_000_000L)

        val result = repository.sync()

        assertTrue(result is UsageSyncResult.Failed)
        assertNull(store.state)
        assertEquals(SyncAttemptStatus.FAILED, store.attempts.single().status)
        assertNull(store.attempts.single().deviceStateCheckpoint)
    }

    @Test
    fun failedReadUpdatesSyncHistoryObservationWithoutAdvancingCursor() = runBlocking {
        val source = FakeUsageEventSource(UsageReadResult.Failure(IOException("reader failed")))
        val store = FakeUsageEventStore()
        val repository = repository(source, store, nowMillis = 10_000_000L)
        val revisions = mutableListOf<Long>()
        val observation = launch {
            repository.observeSyncHistoryRevision().take(2).toList(revisions)
        }

        yield()
        repository.sync()
        observation.join()

        assertEquals(listOf(0L, 1L), revisions)
        assertNull(store.state)
    }

    @Test
    fun successfulSyncPersistsObservedDeviceStateWithEventsAndCursor() = runBlocking {
        val checkpoint = DeviceStateCheckpoint(
            observedAtMillis = 9_999_000L,
            screenInteractive = true,
            keyguardHidden = true,
        )
        val source = FakeUsageEventSource(
            result = UsageReadResult.Success(listOf(record(timestampMillis = 9_000_000L))),
            checkpoint = checkpoint,
        )
        val store = FakeUsageEventStore()
        val repository = repository(source, store, nowMillis = 10_000_000L)

        repository.sync()

        assertEquals(checkpoint, store.attempts.single().deviceStateCheckpoint)
        assertEquals(
            listOf(checkpoint),
            repository.loadDeviceStateCheckpointsForAnalysis(9_999_500L, 10_000_001L),
        )
    }

    @Test
    fun successfulCursorNeverPrecedesDeviceStateObservation() = runBlocking {
        val checkpoint = DeviceStateCheckpoint(
            observedAtMillis = 10_000_001L,
            screenInteractive = true,
            keyguardHidden = true,
        )
        val source = FakeUsageEventSource(
            result = UsageReadResult.Success(emptyList()),
            checkpoint = checkpoint,
        )
        val store = FakeUsageEventStore()
        val repository = repository(source, store, nowMillis = 10_000_000L)

        repository.sync()

        assertEquals(0L to checkpoint.observedAtMillis, source.requestedRanges.single())
        assertEquals(checkpoint.observedAtMillis, store.state?.lastSuccessfulEndMillis)
    }

    @Test
    fun eventIdentityIsStableAcrossQueryOrderingButIncludesEventPayload() {
        val first = record(timestampMillis = 1_000L, sequenceAtTimestamp = 0)
        val reorderedDuplicate = first.copy(sequenceAtTimestamp = 1)
        val differentEvent = first.copy(packageName = "another.app")

        assertEquals(first.toEntity().eventKey, reorderedDuplicate.toEntity().eventKey)
        assertNotEquals(first.toEntity().eventKey, differentEvent.toEntity().eventKey)
    }

    private fun repository(
        source: FakeUsageEventSource,
        store: FakeUsageEventStore,
        nowMillis: Long,
    ) = UsageSyncRepository(
        eventSource = source,
        eventStore = store,
        currentTimeMillis = { nowMillis },
        ioDispatcher = Dispatchers.Unconfined,
    )

    private fun record(
        timestampMillis: Long,
        sequenceAtTimestamp: Int = 0,
    ) = UsageRecord(
        timestampMillis = timestampMillis,
        kind = UsageEventKind.ACTIVITY_RESUMED,
        packageName = "example.app",
        className = "ExampleActivity",
        rawEventType = 1,
        sequenceAtTimestamp = sequenceAtTimestamp,
    )

    private class FakeUsageEventSource(
        var result: UsageReadResult,
        var checkpoint: DeviceStateCheckpoint? = null,
    ) : UsageEventSource {
        val requestedRanges = mutableListOf<Pair<Long, Long>>()

        override fun hasUsageAccess(): Boolean = true

        override fun observeDeviceState(): DeviceStateCheckpoint? = checkpoint

        override fun read(beginMillis: Long, endMillis: Long): UsageReadResult {
            requestedRanges += beginMillis to endMillis
            return result
        }
    }

    private class FakeUsageEventStore(
        initialState: UsageSyncState? = null,
        private val failPersistence: Boolean = false,
    ) : UsageEventStore {
        var state: UsageSyncState? = initialState
        val records = mutableListOf<UsageRecord>()
        var persistCallCount = 0
        private val stateFlow = MutableStateFlow(initialState)
        private val syncHistoryRevision = MutableStateFlow(0L)
        private val eventKeys = mutableSetOf<String>()

        override suspend fun loadSyncState(): UsageSyncState? = state

        override fun observeSyncState(): Flow<UsageSyncState?> = stateFlow

        override fun observeSyncHistoryRevision(): Flow<Long> = syncHistoryRevision

        override suspend fun persistSuccessfulSync(
            records: List<UsageRecord>,
            state: UsageSyncState,
            attempt: SyncAttempt,
        ): Int {
            persistCallCount += 1
            if (failPersistence) throw IOException("database unavailable")
            val newRecords = records.filter { eventKeys.add(it.toEntity().eventKey) }
            this.records += newRecords
            this.state = state.copy(lastInsertedEventCount = newRecords.size)
            attempts += attempt.copy(insertedEventCount = newRecords.size)
            stateFlow.value = this.state
            syncHistoryRevision.value += 1L
            return newRecords.size
        }

        val attempts = mutableListOf<SyncAttempt>()

        override suspend fun recordSyncAttempt(attempt: SyncAttempt) {
            attempts += attempt
            syncHistoryRevision.value += 1L
        }

        override suspend fun loadRecordsForAnalysis(
            beginMillis: Long,
            endMillis: Long,
        ): List<UsageRecord> = records.filter { it.timestampMillis in beginMillis until endMillis }

        override suspend fun loadSyncAttempts(
            beginMillis: Long,
            endMillis: Long,
        ): List<SyncAttempt> = attempts.filter {
            it.attemptedAtMillis in beginMillis until endMillis
        }

        override suspend fun loadDeviceStateCheckpointsForAnalysis(
            beginMillis: Long,
            endMillis: Long,
        ): List<DeviceStateCheckpoint> {
            val checkpoints = attempts.asSequence()
                .filter { it.status == SyncAttemptStatus.SUCCESS }
                .mapNotNull(SyncAttempt::deviceStateCheckpoint)
                .sortedBy(DeviceStateCheckpoint::observedAtMillis)
                .toList()
            return buildList {
                checkpoints.lastOrNull { it.observedAtMillis < beginMillis }?.let(::add)
                addAll(
                    checkpoints.filter { it.observedAtMillis in beginMillis until endMillis },
                )
            }
        }
    }
}
