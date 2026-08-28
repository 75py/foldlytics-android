package com.nagopy.android.foldlytics.data

import android.app.usage.UsageEvents
import android.content.Context
import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class UsageEventCleanupTest {
    private lateinit var context: Context

    @get:Rule
    val migrationHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        FoldlyticsDatabase::class.java,
    )

    @Before
    fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        require(LEGACY_CLEANUP_DATABASE_NAME != "foldlytics.db")
        require(DEVICE_STATE_MIGRATION_DATABASE_NAME != "foldlytics.db")
        require(FRESH_DATABASE_NAME != "foldlytics.db")
        context.deleteDatabase(LEGACY_CLEANUP_DATABASE_NAME)
        context.deleteDatabase(DEVICE_STATE_MIGRATION_DATABASE_NAME)
        context.deleteDatabase(FRESH_DATABASE_NAME)
    }

    @After
    fun tearDown() {
        context.deleteDatabase(LEGACY_CLEANUP_DATABASE_NAME)
        context.deleteDatabase(DEVICE_STATE_MIGRATION_DATABASE_NAME)
        context.deleteDatabase(FRESH_DATABASE_NAME)
    }

    @Test
    fun migrationFromOneToTwoRemovesOnlyUnstoredUsageEvents() {
        val versionOne = migrationHelper.createDatabase(LEGACY_CLEANUP_DATABASE_NAME, 1)
        try {
            seedVersionOneDatabase(versionOne)
        } finally {
            versionOne.close()
        }

        val migrated = migrationHelper.runMigrationsAndValidate(
            LEGACY_CLEANUP_DATABASE_NAME,
            2,
            true,
            MIGRATION_1_2,
        )
        try {
            assertEquals(2, migrated.version)
            assertMigratedData(migrated)

            // Room runs this migration once; repeating its DELETE proves the data step is idempotent.
            MIGRATION_1_2.migrate(migrated)
            assertMigratedData(migrated)
        } finally {
            migrated.close()
        }
    }

    @Test
    fun migrationFromTwoToThreePreservesHistoryAndAddsDeviceStateStorage() {
        val versionTwo = migrationHelper.createDatabase(DEVICE_STATE_MIGRATION_DATABASE_NAME, 2)
        try {
            versionTwo.execSQL(
                """
                INSERT INTO sync_history (
                    id,
                    attempted_at_millis,
                    query_begin_millis,
                    query_end_millis,
                    status,
                    read_event_count,
                    inserted_event_count
                ) VALUES (1, 3000, 1000, 3000, 'SUCCESS', 12, 5)
                """.trimIndent(),
            )
        } finally {
            versionTwo.close()
        }

        val migrated = migrationHelper.runMigrationsAndValidate(
            DEVICE_STATE_MIGRATION_DATABASE_NAME,
            3,
            true,
            MIGRATION_2_3,
        )
        try {
            assertEquals(3, migrated.version)
            migrated.query(
                """
                SELECT
                    attempted_at_millis,
                    query_begin_millis,
                    query_end_millis,
                    read_event_count,
                    inserted_event_count,
                    device_state_observed_at_millis,
                    screen_interactive,
                    keyguard_hidden
                FROM sync_history
                WHERE id = 1
                """.trimIndent(),
            ).use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(3_000L, cursor.getLong(0))
                assertEquals(1_000L, cursor.getLong(1))
                assertEquals(3_000L, cursor.getLong(2))
                assertEquals(12, cursor.getInt(3))
                assertEquals(5, cursor.getInt(4))
                assertTrue(cursor.isNull(5))
                assertTrue(cursor.isNull(6))
                assertTrue(cursor.isNull(7))
                assertFalse(cursor.moveToNext())
            }

            migrated.execSQL(
                """
                INSERT INTO sync_history (
                    attempted_at_millis,
                    query_begin_millis,
                    query_end_millis,
                    status,
                    read_event_count,
                    inserted_event_count,
                    device_state_observed_at_millis,
                    screen_interactive,
                    keyguard_hidden
                ) VALUES (4000, 2000, 4000, 'SUCCESS', 0, 0, 3900, 1, 0)
                """.trimIndent(),
            )
            migrated.query(
                """
                SELECT device_state_observed_at_millis, screen_interactive, keyguard_hidden
                FROM sync_history
                WHERE device_state_observed_at_millis IS NOT NULL
                """.trimIndent(),
            ).use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(3_900L, cursor.getLong(0))
                assertEquals(1, cursor.getInt(1))
                assertEquals(0, cursor.getInt(2))
                assertFalse(cursor.moveToNext())
            }
        } finally {
            migrated.close()
        }
    }

    @Test
    fun reopeningFreshVersionThreeDatabaseDoesNotRunLegacyCleanup() = runBlocking {
        var database = openLatestDatabase(FRESH_DATABASE_NAME)
        try {
            assertEquals(3, database.openHelper.writableDatabase.version)
            database.usageEventDao().insertEvents(
                listOf(
                    event(
                        eventKey = "future-event",
                        rawEventType = UsageEvents.Event.USER_INTERACTION,
                    ),
                ),
            )
        } finally {
            database.close()
        }

        database = openLatestDatabase(FRESH_DATABASE_NAME)
        try {
            assertEquals(
                listOf(UsageEvents.Event.USER_INTERACTION),
                database.usageEventDao()
                    .loadEvents(beginMillis = 0L, endMillis = Long.MAX_VALUE)
                    .map(UsageEventEntity::rawEventType),
            )
        } finally {
            database.close()
        }
    }

    private fun seedVersionOneDatabase(database: SupportSQLiteDatabase) {
        val excludedEventTypes = listOf(
            UsageEvents.Event.NONE,
            UsageEvents.Event.USER_INTERACTION,
            UsageEvents.Event.FOREGROUND_SERVICE_START,
        )
        (StoredUsageEventTypes.all + excludedEventTypes).forEachIndexed { index, rawEventType ->
            database.execSQL(
                """
                INSERT INTO usage_events (
                    event_key,
                    timestamp_millis,
                    sequence_at_timestamp,
                    raw_event_type,
                    package_name,
                    class_name,
                    has_configuration,
                    screen_width_dp,
                    screen_height_dp,
                    smallest_screen_width_dp,
                    orientation,
                    density_dpi
                ) VALUES (?, ?, 0, ?, NULL, NULL, 0, NULL, NULL, NULL, NULL, NULL)
                """.trimIndent(),
                arrayOf<Any>("event-$index", 1_000L + index, rawEventType.toLong()),
            )
        }
        database.execSQL(
            """
            INSERT INTO usage_sync_state (
                singleton_id,
                last_successful_end_millis,
                last_successful_at_millis,
                last_query_begin_millis,
                last_inserted_event_count
            ) VALUES (1, 4000, 4000, 0, 13)
            """.trimIndent(),
        )
        database.execSQL(
            """
            INSERT INTO posture_checkpoints (
                checkpoint_key,
                timestamp_millis,
                screen_width_dp,
                screen_height_dp,
                smallest_screen_width_dp,
                orientation,
                density_dpi,
                source
            ) VALUES ('checkpoint', 3000, 443, 994, 443, 1, 420, 'MANUAL_REFRESH')
            """.trimIndent(),
        )
    }

    private fun assertMigratedData(database: SupportSQLiteDatabase) {
        assertEquals(StoredUsageEventTypes.all, database.storedRawEventTypes())

        database.query(
            """
            SELECT
                last_successful_end_millis,
                last_successful_at_millis,
                last_query_begin_millis,
                last_inserted_event_count
            FROM usage_sync_state
            WHERE singleton_id = 1
            """.trimIndent(),
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(4_000L, cursor.getLong(0))
            assertEquals(4_000L, cursor.getLong(1))
            assertEquals(0L, cursor.getLong(2))
            assertEquals(13, cursor.getInt(3))
            assertFalse(cursor.moveToNext())
        }

        database.query(
            """
            SELECT timestamp_millis, screen_width_dp, screen_height_dp, source
            FROM posture_checkpoints
            WHERE checkpoint_key = 'checkpoint'
            """.trimIndent(),
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(3_000L, cursor.getLong(0))
            assertEquals(443, cursor.getInt(1))
            assertEquals(994, cursor.getInt(2))
            assertEquals("MANUAL_REFRESH", cursor.getString(3))
            assertFalse(cursor.moveToNext())
        }
    }

    private fun SupportSQLiteDatabase.storedRawEventTypes(): List<Int> =
        query(
            """
            SELECT raw_event_type
            FROM usage_events
            ORDER BY timestamp_millis ASC, sequence_at_timestamp ASC, event_key ASC
            """.trimIndent(),
        ).use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    add(cursor.getInt(0))
                }
            }
        }

    private fun openLatestDatabase(name: String): FoldlyticsDatabase =
        Room.databaseBuilder(context, FoldlyticsDatabase::class.java, name)
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
            .build()

    private fun event(eventKey: String, rawEventType: Int): UsageEventEntity = UsageEventEntity(
        eventKey = eventKey,
        timestampMillis = 1_000L,
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
        const val LEGACY_CLEANUP_DATABASE_NAME = "foldlytics-usage-event-migration-test.db"
        const val DEVICE_STATE_MIGRATION_DATABASE_NAME =
            "foldlytics-device-state-migration-test.db"
        const val FRESH_DATABASE_NAME = "foldlytics-usage-event-fresh-v3-test.db"
    }
}
