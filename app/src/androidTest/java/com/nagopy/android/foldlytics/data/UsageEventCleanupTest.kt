package com.nagopy.android.foldlytics.data

import android.app.usage.UsageEvents
import android.content.Context
import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.nagopy.android.foldlytics.model.Calibration
import com.nagopy.android.foldlytics.model.DisplayConfiguration
import com.nagopy.android.foldlytics.model.UsageEventKind
import com.nagopy.android.foldlytics.model.UsageRecord
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
        require(SESSION_MIGRATION_DATABASE_NAME != "foldlytics.db")
        require(CACHE_CURSOR_MIGRATION_DATABASE_NAME != "foldlytics.db")
        require(LEGACY_MULTIPLICITY_MIGRATION_DATABASE_NAME != "foldlytics.db")
        require(FRESH_DATABASE_NAME != "foldlytics.db")
        context.deleteDatabase(LEGACY_CLEANUP_DATABASE_NAME)
        context.deleteDatabase(DEVICE_STATE_MIGRATION_DATABASE_NAME)
        context.deleteDatabase(SESSION_MIGRATION_DATABASE_NAME)
        context.deleteDatabase(CACHE_CURSOR_MIGRATION_DATABASE_NAME)
        context.deleteDatabase(LEGACY_MULTIPLICITY_MIGRATION_DATABASE_NAME)
        context.deleteDatabase(FRESH_DATABASE_NAME)
    }

    @After
    fun tearDown() {
        context.deleteDatabase(LEGACY_CLEANUP_DATABASE_NAME)
        context.deleteDatabase(DEVICE_STATE_MIGRATION_DATABASE_NAME)
        context.deleteDatabase(SESSION_MIGRATION_DATABASE_NAME)
        context.deleteDatabase(CACHE_CURSOR_MIGRATION_DATABASE_NAME)
        context.deleteDatabase(LEGACY_MULTIPLICITY_MIGRATION_DATABASE_NAME)
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
    fun migrationFromThreeToFourCreatesSessionTablesAndPreservesCacheState() {
        val versionThree = migrationHelper.createDatabase(SESSION_MIGRATION_DATABASE_NAME, 3)
        try {
            versionThree.execSQL(
                """
                INSERT INTO daily_summary_state (
                    singleton_id,
                    last_aggregated_through_millis,
                    calibration_key,
                    zone_id,
                    checkpoint_revision,
                    aggregation_version
                ) VALUES (1, 9000, 'calibration', 'Asia/Tokyo', 7, 2)
                """.trimIndent(),
            )
        } finally {
            versionThree.close()
        }

        val migrated = migrationHelper.runMigrationsAndValidate(
            SESSION_MIGRATION_DATABASE_NAME,
            4,
            true,
            MIGRATION_3_4,
        )
        try {
            assertEquals(4, migrated.version)
            migrated.query("SELECT COUNT(*) FROM inner_display_sessions").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(0, cursor.getInt(0))
                assertFalse(cursor.moveToNext())
            }
            migrated.query("SELECT COUNT(*) FROM inner_display_session_app_usage").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(0, cursor.getInt(0))
                assertFalse(cursor.moveToNext())
            }
            migrated.execSQL("PRAGMA foreign_keys = ON")
            migrated.execSQL(
                """
                INSERT INTO inner_display_sessions (
                    opened_at_millis,
                    opened_sequence_at_timestamp,
                    closed_at_millis,
                    inner_active_millis
                ) VALUES (1000, 2, 2000, 900)
                """.trimIndent(),
            )
            migrated.execSQL(
                """
                INSERT INTO inner_display_session_app_usage (
                    opened_at_millis,
                    opened_sequence_at_timestamp,
                    package_name,
                    inner_active_millis
                ) VALUES (1000, 2, 'app.example', 900)
                """.trimIndent(),
            )
            migrated.execSQL(
                "DELETE FROM inner_display_sessions " +
                    "WHERE opened_at_millis = 1000 AND opened_sequence_at_timestamp = 2",
            )
            migrated.query("SELECT COUNT(*) FROM inner_display_session_app_usage").use { cursor ->
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
                assertEquals(2, cursor.getInt(4))
                assertFalse(cursor.moveToNext())
            }
        } finally {
            migrated.close()
        }
    }

    @Test
    fun migrationFromFourToFivePreservesEventsAndInvalidatesSyncHistoryCursor() {
        val versionFour = migrationHelper.createDatabase(CACHE_CURSOR_MIGRATION_DATABASE_NAME, 4)
        try {
            versionFour.execSQL(
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
                ) VALUES (
                    '460e7d8ab98d298e5152883fcd089c271426a3bb5e8a13417b9ed7fe3a036738',
                    1000,
                    0,
                    1,
                    'example.app',
                    'ExampleActivity',
                    0,
                    NULL,
                    NULL,
                    NULL,
                    NULL,
                    NULL
                )
                """.trimIndent(),
            )
            versionFour.execSQL(
                """
                INSERT INTO sync_history (
                    id,
                    attempted_at_millis,
                    query_begin_millis,
                    query_end_millis,
                    status,
                    read_event_count,
                    inserted_event_count
                ) VALUES (7, 2000, 0, 2000, 'SUCCESS', 1, 1)
                """.trimIndent(),
            )
            versionFour.execSQL(
                """
                INSERT INTO daily_summary_state (
                    singleton_id,
                    last_aggregated_through_millis,
                    calibration_key,
                    zone_id,
                    checkpoint_revision,
                    aggregation_version
                ) VALUES (1, 2000, 'calibration', 'UTC', 0, 5)
                """.trimIndent(),
            )
        } finally {
            versionFour.close()
        }

        val migrated = migrationHelper.runMigrationsAndValidate(
            CACHE_CURSOR_MIGRATION_DATABASE_NAME,
            5,
            true,
            MIGRATION_4_5,
        )
        try {
            assertEquals(5, migrated.version)
            migrated.query(
                """
                SELECT aggregation_version, last_aggregated_sync_history_id
                FROM daily_summary_state
                WHERE singleton_id = 1
                """.trimIndent(),
            ).use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(5, cursor.getInt(0))
                assertEquals(0L, cursor.getLong(1))
                assertFalse(cursor.moveToNext())
            }
            migrated.query("SELECT event_key FROM usage_events").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(
                    "460e7d8ab98d298e5152883fcd089c271426a3bb5e8a13417b9ed7fe3a036738",
                    cursor.getString(0),
                )
                assertFalse(cursor.moveToNext())
            }
        } finally {
            migrated.close()
        }
    }

    @Test
    fun migratedLegacyEventsRecoverDuplicateWithCoherentSameTimestampOrder() = runBlocking {
        val cover = DisplayConfiguration(
            screenWidthDp = 443,
            screenHeightDp = 994,
            smallestScreenWidthDp = 443,
            orientation = 1,
            densityDpi = 420,
        )
        val baseline = listOf(
            usageRecord(
                timestampMillis = 0L,
                sequenceAtTimestamp = 0,
                kind = UsageEventKind.CONFIGURATION_CHANGED,
                rawEventType = UsageEvents.Event.CONFIGURATION_CHANGE,
                configuration = cover,
            ),
            usageRecord(
                timestampMillis = 0L,
                sequenceAtTimestamp = 1,
                kind = UsageEventKind.SCREEN_INTERACTIVE,
                rawEventType = UsageEvents.Event.SCREEN_INTERACTIVE,
            ),
            usageRecord(
                timestampMillis = 0L,
                sequenceAtTimestamp = 2,
                kind = UsageEventKind.KEYGUARD_HIDDEN,
                rawEventType = UsageEvents.Event.KEYGUARD_HIDDEN,
            ),
        )
        val legacyResume = usageRecord(
            timestampMillis = 1_000L,
            sequenceAtTimestamp = 100,
            kind = UsageEventKind.ACTIVITY_RESUMED,
            rawEventType = UsageEvents.Event.ACTIVITY_RESUMED,
            packageName = "example.app",
        )
        val legacyPause = usageRecord(
            timestampMillis = 1_000L,
            sequenceAtTimestamp = 101,
            kind = UsageEventKind.ACTIVITY_PAUSED,
            rawEventType = UsageEvents.Event.ACTIVITY_PAUSED,
            packageName = "example.app",
        )
        val versionFour = migrationHelper.createDatabase(
            LEGACY_MULTIPLICITY_MIGRATION_DATABASE_NAME,
            4,
        )
        try {
            (baseline + legacyResume + legacyPause).toEntities().forEach { entity ->
                insertUsageEvent(versionFour, entity)
            }
        } finally {
            versionFour.close()
        }
        migrationHelper.runMigrationsAndValidate(
            LEGACY_MULTIPLICITY_MIGRATION_DATABASE_NAME,
            5,
            true,
            MIGRATION_4_5,
        ).close()

        val database = openLatestDatabase(LEGACY_MULTIPLICITY_MIGRATION_DATABASE_NAME)
        try {
            val reread = listOf(
                legacyResume.copy(sequenceAtTimestamp = 0),
                legacyPause.copy(sequenceAtTimestamp = 1),
                legacyResume.copy(sequenceAtTimestamp = 2),
            )
            val store = RoomUsageEventStore(database.usageEventDao())
            val insertedCount = store.persistSuccessfulSync(
                records = reread,
                state = UsageSyncState(2_000L, 2_000L, 0L, 0),
                attempt = SyncAttempt(
                    attemptedAtMillis = 2_000L,
                    queryBeginMillis = 0L,
                    queryEndMillis = 2_000L,
                    status = SyncAttemptStatus.SUCCESS,
                    readEventCount = reread.size,
                ),
            )
            val repeatedInsertedCount = store.persistSuccessfulSync(
                records = reread.map {
                    it.copy(sequenceAtTimestamp = it.sequenceAtTimestamp + 100)
                },
                state = UsageSyncState(3_000L, 3_000L, 0L, 0),
                attempt = SyncAttempt(
                    attemptedAtMillis = 3_000L,
                    queryBeginMillis = 0L,
                    queryEndMillis = 3_000L,
                    status = SyncAttemptStatus.SUCCESS,
                    readEventCount = reread.size,
                ),
            )
            val records = database.usageEventDao()
                .loadEvents(0L, 2_000L)
                .map(UsageEventEntity::toModel)
            val recovered = records.filter { it.timestampMillis == 1_000L }
            val analysis = UsageAnalyzer(packageLabel = { it }).analyze(
                records = records,
                rangeStartMillis = 0L,
                rangeEndMillis = 2_000L,
                calibration = Calibration(cover = cover),
            )

            assertEquals(1, insertedCount)
            assertEquals(0, repeatedInsertedCount)
            assertEquals(
                listOf(
                    UsageEventKind.ACTIVITY_RESUMED,
                    UsageEventKind.ACTIVITY_PAUSED,
                    UsageEventKind.ACTIVITY_RESUMED,
                ),
                recovered.map(UsageRecord::kind),
            )
            assertEquals(listOf(0, 1, 2), recovered.map(UsageRecord::sequenceAtTimestamp))
            assertEquals(1_000L, analysis.apps.single().coverMillis)
        } finally {
            database.close()
        }
    }

    @Test
    fun reopeningFreshVersionFiveDatabaseDoesNotRunLegacyCleanup() = runBlocking {
        var database = openLatestDatabase(FRESH_DATABASE_NAME)
        try {
            assertEquals(5, database.openHelper.writableDatabase.version)
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

    private fun insertUsageEvent(
        database: SupportSQLiteDatabase,
        event: UsageEventEntity,
    ) {
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
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """.trimIndent(),
            arrayOf<Any?>(
                event.eventKey,
                event.timestampMillis,
                event.sequenceAtTimestamp,
                event.rawEventType,
                event.packageName,
                event.className,
                event.hasConfiguration,
                event.screenWidthDp,
                event.screenHeightDp,
                event.smallestScreenWidthDp,
                event.orientation,
                event.densityDpi,
            ),
        )
    }

    private fun usageRecord(
        timestampMillis: Long,
        sequenceAtTimestamp: Int,
        kind: UsageEventKind,
        rawEventType: Int,
        packageName: String? = null,
        configuration: DisplayConfiguration? = null,
    ) = UsageRecord(
        timestampMillis = timestampMillis,
        kind = kind,
        packageName = packageName,
        className = packageName?.let { "ExampleActivity" },
        configuration = configuration,
        rawEventType = rawEventType,
        sequenceAtTimestamp = sequenceAtTimestamp,
    )

    private fun openLatestDatabase(name: String): FoldlyticsDatabase =
        Room.databaseBuilder(context, FoldlyticsDatabase::class.java, name)
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
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
        const val SESSION_MIGRATION_DATABASE_NAME = "foldlytics-session-migration-test.db"
        const val CACHE_CURSOR_MIGRATION_DATABASE_NAME =
            "foldlytics-cache-cursor-migration-test.db"
        const val LEGACY_MULTIPLICITY_MIGRATION_DATABASE_NAME =
            "foldlytics-legacy-multiplicity-migration-test.db"
        const val FRESH_DATABASE_NAME = "foldlytics-usage-event-fresh-v5-test.db"
    }
}
