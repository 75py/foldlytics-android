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
        require(MIGRATION_DATABASE_NAME != "foldlytics.db")
        require(SESSION_MIGRATION_DATABASE_NAME != "foldlytics.db")
        require(FRESH_DATABASE_NAME != "foldlytics.db")
        context.deleteDatabase(MIGRATION_DATABASE_NAME)
        context.deleteDatabase(SESSION_MIGRATION_DATABASE_NAME)
        context.deleteDatabase(FRESH_DATABASE_NAME)
    }

    @After
    fun tearDown() {
        context.deleteDatabase(MIGRATION_DATABASE_NAME)
        context.deleteDatabase(SESSION_MIGRATION_DATABASE_NAME)
        context.deleteDatabase(FRESH_DATABASE_NAME)
    }

    @Test
    fun migrationFromOneToTwoRemovesOnlyUnstoredUsageEvents() {
        val versionOne = migrationHelper.createDatabase(MIGRATION_DATABASE_NAME, 1)
        try {
            seedVersionOneDatabase(versionOne)
        } finally {
            versionOne.close()
        }

        val migrated = migrationHelper.runMigrationsAndValidate(
            MIGRATION_DATABASE_NAME,
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
    fun migrationFromTwoToThreeCreatesEmptySessionCacheAndPreservesCacheState() {
        val versionTwo = migrationHelper.createDatabase(SESSION_MIGRATION_DATABASE_NAME, 2)
        try {
            versionTwo.execSQL(
                """
                INSERT INTO daily_summary_state (
                    singleton_id,
                    last_aggregated_through_millis,
                    calibration_key,
                    zone_id,
                    checkpoint_revision,
                    aggregation_version
                ) VALUES (1, 9000, 'calibration', 'Asia/Tokyo', 7, 1)
                """.trimIndent(),
            )
        } finally {
            versionTwo.close()
        }

        val migrated = migrationHelper.runMigrationsAndValidate(
            SESSION_MIGRATION_DATABASE_NAME,
            3,
            true,
            MIGRATION_2_3,
        )
        try {
            assertEquals(3, migrated.version)
            migrated.query("SELECT COUNT(*) FROM inner_display_sessions").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(0, cursor.getInt(0))
                assertFalse(cursor.moveToNext())
            }
            migrated.query(
                """
                SELECT
                    last_aggregated_through_millis,
                    calibration_key,
                    zone_id,
                    checkpoint_revision,
                    aggregation_version
                FROM daily_summary_state
                WHERE singleton_id = 1
                """.trimIndent(),
            ).use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(9_000L, cursor.getLong(0))
                assertEquals("calibration", cursor.getString(1))
                assertEquals("Asia/Tokyo", cursor.getString(2))
                assertEquals(7L, cursor.getLong(3))
                assertEquals(1, cursor.getInt(4))
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
        const val MIGRATION_DATABASE_NAME = "foldlytics-usage-event-migration-test.db"
        const val SESSION_MIGRATION_DATABASE_NAME = "foldlytics-session-migration-test.db"
        const val FRESH_DATABASE_NAME = "foldlytics-usage-event-fresh-v3-test.db"
    }
}
