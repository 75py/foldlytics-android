package com.nagopy.android.foldlytics.data

import com.nagopy.android.foldlytics.model.UsageRecord
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

data class UsageSyncState(
    val lastSuccessfulEndMillis: Long,
    val lastSuccessfulAtMillis: Long,
    val lastQueryBeginMillis: Long,
    val lastInsertedEventCount: Int,
)

data class DeviceStateCheckpoint(
    val observedAtMillis: Long,
    val screenInteractive: Boolean,
    val keyguardHidden: Boolean,
)

enum class SyncAttemptStatus {
    SUCCESS,
    PERMISSION_DENIED,
    USER_LOCKED,
    SYSTEM_UNAVAILABLE,
    FAILED,
}

data class SyncAttempt(
    val attemptedAtMillis: Long,
    val queryBeginMillis: Long,
    val queryEndMillis: Long,
    val status: SyncAttemptStatus,
    val readEventCount: Int,
    val insertedEventCount: Int = 0,
    val deviceStateCheckpoint: DeviceStateCheckpoint? = null,
)

interface UsageEventStore {
    suspend fun loadSyncState(): UsageSyncState?

    fun observeSyncState(): Flow<UsageSyncState?>

    suspend fun persistSuccessfulSync(
        records: List<UsageRecord>,
        state: UsageSyncState,
        attempt: SyncAttempt,
    ): Int

    suspend fun recordSyncAttempt(attempt: SyncAttempt)

    suspend fun loadRecordsForAnalysis(beginMillis: Long, endMillis: Long): List<UsageRecord>

    suspend fun loadSyncAttempts(beginMillis: Long, endMillis: Long): List<SyncAttempt>

    suspend fun loadDeviceStateCheckpointsForAnalysis(
        beginMillis: Long,
        endMillis: Long,
    ): List<DeviceStateCheckpoint>
}

sealed interface UsageSyncResult {
    data class Success(
        val beginMillis: Long,
        val endMillis: Long,
        val readEventCount: Int,
        val insertedEventCount: Int,
    ) : UsageSyncResult

    data class Skipped(val reason: UsageReadUnavailableReason) : UsageSyncResult

    data class Failed(val error: Exception) : UsageSyncResult
}

class UsageSyncRepository(
    private val eventSource: UsageEventSource,
    private val eventStore: UsageEventStore,
    private val currentTimeMillis: () -> Long = System::currentTimeMillis,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    private val syncMutex = Mutex()

    suspend fun sync(): UsageSyncResult = syncMutex.withLock {
        withContext(ioDispatcher) {
            try {
                val previousState = eventStore.loadSyncState()
                val deviceStateCheckpoint = observeDeviceStateSafely()
                val endMillis = maxOf(
                    currentTimeMillis(),
                    deviceStateCheckpoint?.observedAtMillis ?: 0L,
                ).coerceAtLeast(0L)
                val beginMillis = previousState
                    ?.lastSuccessfulEndMillis
                    ?.coerceAtMost(endMillis)
                    ?.minus(SYNC_OVERLAP_MILLIS)
                    ?.coerceAtLeast(0L)
                    ?: 0L

                when (val readResult = eventSource.read(beginMillis, endMillis)) {
                    is UsageReadResult.Success -> {
                        val state = UsageSyncState(
                            lastSuccessfulEndMillis = endMillis,
                            lastSuccessfulAtMillis = endMillis,
                            lastQueryBeginMillis = beginMillis,
                            lastInsertedEventCount = 0,
                        )
                        val insertedCount = eventStore.persistSuccessfulSync(
                            records = readResult.records,
                            state = state,
                            attempt = SyncAttempt(
                                attemptedAtMillis = endMillis,
                                queryBeginMillis = beginMillis,
                                queryEndMillis = endMillis,
                                status = SyncAttemptStatus.SUCCESS,
                                readEventCount = readResult.records.size,
                                deviceStateCheckpoint = deviceStateCheckpoint,
                            ),
                        )
                        UsageSyncResult.Success(
                            beginMillis = beginMillis,
                            endMillis = endMillis,
                            readEventCount = readResult.records.size,
                            insertedEventCount = insertedCount,
                        )
                    }

                    is UsageReadResult.Unavailable -> {
                        recordSyncAttemptSafely(
                            SyncAttempt(
                                attemptedAtMillis = endMillis,
                                queryBeginMillis = beginMillis,
                                queryEndMillis = endMillis,
                                status = readResult.reason.toSyncAttemptStatus(),
                                readEventCount = 0,
                            ),
                        )
                        UsageSyncResult.Skipped(readResult.reason)
                    }

                    is UsageReadResult.Failure -> {
                        recordSyncAttemptSafely(
                            SyncAttempt(
                                attemptedAtMillis = endMillis,
                                queryBeginMillis = beginMillis,
                                queryEndMillis = endMillis,
                                status = SyncAttemptStatus.FAILED,
                                readEventCount = 0,
                            ),
                        )
                        UsageSyncResult.Failed(readResult.error)
                    }
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                UsageSyncResult.Failed(error)
            }
        }
    }

    fun hasUsageAccess(): Boolean = eventSource.hasUsageAccess()

    fun observeSyncState(): Flow<UsageSyncState?> = eventStore.observeSyncState()

    suspend fun loadRecordsForAnalysis(beginMillis: Long, endMillis: Long): List<UsageRecord> =
        eventStore.loadRecordsForAnalysis(beginMillis, endMillis)

    suspend fun loadSyncAttempts(beginMillis: Long, endMillis: Long): List<SyncAttempt> =
        eventStore.loadSyncAttempts(beginMillis, endMillis)

    suspend fun loadDeviceStateCheckpointsForAnalysis(
        beginMillis: Long,
        endMillis: Long,
    ): List<DeviceStateCheckpoint> =
        eventStore.loadDeviceStateCheckpointsForAnalysis(beginMillis, endMillis)

    private fun observeDeviceStateSafely(): DeviceStateCheckpoint? = try {
        eventSource.observeDeviceState()
    } catch (_: Exception) {
        // Current state is optional evidence; UsageEvents remain authoritative if it is unavailable.
        null
    }

    private suspend fun recordSyncAttemptSafely(attempt: SyncAttempt) {
        try {
            eventStore.recordSyncAttempt(attempt)
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            // The original read result remains authoritative when audit persistence fails.
        }
    }
}

private fun UsageReadUnavailableReason.toSyncAttemptStatus(): SyncAttemptStatus = when (this) {
    UsageReadUnavailableReason.PERMISSION_DENIED -> SyncAttemptStatus.PERMISSION_DENIED
    UsageReadUnavailableReason.USER_LOCKED -> SyncAttemptStatus.USER_LOCKED
    UsageReadUnavailableReason.SYSTEM_UNAVAILABLE -> SyncAttemptStatus.SYSTEM_UNAVAILABLE
}
