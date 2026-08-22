package com.nagopy.android.foldlytics.data

import android.app.usage.UsageEvents
import android.content.Context
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class UsageEventCleanupTest {
    private lateinit var context: Context
    private lateinit var database: FoldlyticsDatabase

    @Before
    fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        require(DATABASE_NAME != "foldlytics.db")
        context.deleteDatabase(DATABASE_NAME)
    }

    @After
    fun tearDown() {
        if (::database.isInitialized) database.close()
        context.deleteDatabase(DATABASE_NAME)
    }

    @Test
    fun reopeningRemovesOnlyUnstoredEventsAndCleanupIsIdempotent() = runBlocking {
        val excludedEventTypes = listOf(
            UsageEvents.Event.NONE,
            UsageEvents.Event.USER_INTERACTION,
            UsageEvents.Event.FOREGROUND_SERVICE_START,
        )
        val originalSyncState = UsageSyncStateEntity(
            lastSuccessfulEndMillis = 4_000L,
            lastSuccessfulAtMillis = 4_000L,
            lastQueryBeginMillis = 0L,
            lastInsertedEventCount = StoredUsageEventTypes.all.size + excludedEventTypes.size,
        )
        database = openDatabase(cleanUpUnstoredEvents = false)
        database.usageEventDao().insertEvents(
            (StoredUsageEventTypes.all + excludedEventTypes).mapIndexed { index, rawEventType ->
                event(index, rawEventType)
            },
        )
        database.usageEventDao().upsertSyncState(originalSyncState)
        assertEquals(
            StoredUsageEventTypes.all + excludedEventTypes,
            storedRawEventTypes(),
        )
        database.close()

        database = openDatabase(cleanUpUnstoredEvents = true)
        assertEquals(1, database.openHelper.readableDatabase.version)
        assertEquals(StoredUsageEventTypes.all, storedRawEventTypes())
        assertEquals(originalSyncState, database.usageEventDao().loadSyncState())
        database.close()

        database = openDatabase(cleanUpUnstoredEvents = true)
        assertEquals(StoredUsageEventTypes.all, storedRawEventTypes())
        assertEquals(originalSyncState, database.usageEventDao().loadSyncState())
    }

    private fun openDatabase(cleanUpUnstoredEvents: Boolean): FoldlyticsDatabase =
        Room.databaseBuilder(context, FoldlyticsDatabase::class.java, DATABASE_NAME)
            .apply {
                if (cleanUpUnstoredEvents) {
                    addCallback(RemoveUnstoredUsageEventsOnOpen)
                }
            }
            .build()

    private suspend fun storedRawEventTypes(): List<Int> =
        database.usageEventDao()
            .loadEvents(beginMillis = 0L, endMillis = Long.MAX_VALUE)
            .map(UsageEventEntity::rawEventType)

    private fun event(index: Int, rawEventType: Int): UsageEventEntity = UsageEventEntity(
        eventKey = "event-$index",
        timestampMillis = 1_000L + index,
        sequenceAtTimestamp = 0,
        rawEventType = rawEventType,
        packageName = null,
        className = null,
        hasConfiguration = false,
        screenWidthDp = null,
        screenHeightDp = null,
        smallestScreenWidthDp = null,
        orientation = null,
        densityDpi = null,
    )

    private companion object {
        const val DATABASE_NAME = "foldlytics-usage-event-cleanup-test.db"
    }
}
