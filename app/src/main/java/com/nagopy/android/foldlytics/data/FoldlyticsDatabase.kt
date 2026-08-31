package com.nagopy.android.foldlytics.data

import android.content.Context
import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Transaction
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.nagopy.android.foldlytics.model.DailyAppUsageSummary
import com.nagopy.android.foldlytics.model.DailyPostureSummary
import com.nagopy.android.foldlytics.model.DisplayConfiguration
import com.nagopy.android.foldlytics.model.InnerDisplaySession
import com.nagopy.android.foldlytics.model.PostureCheckpoint
import com.nagopy.android.foldlytics.model.PostureCheckpointSource
import com.nagopy.android.foldlytics.model.UsageRecord
import java.security.MessageDigest
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@Entity(
    tableName = "usage_events",
    indices = [
        Index(value = ["timestamp_millis", "sequence_at_timestamp"]),
        Index(value = ["raw_event_type", "timestamp_millis"]),
        Index(
            value = [
                "package_name",
                "class_name",
                "raw_event_type",
                "timestamp_millis",
                "sequence_at_timestamp",
            ],
        ),
    ],
)
data class UsageEventEntity(
    @PrimaryKey
    @ColumnInfo(name = "event_key")
    val eventKey: String,
    @ColumnInfo(name = "timestamp_millis")
    val timestampMillis: Long,
    @ColumnInfo(name = "sequence_at_timestamp")
    val sequenceAtTimestamp: Int,
    @ColumnInfo(name = "raw_event_type")
    val rawEventType: Int,
    @ColumnInfo(name = "package_name")
    val packageName: String?,
    @ColumnInfo(name = "class_name")
    val className: String?,
    @ColumnInfo(name = "has_configuration")
    val hasConfiguration: Boolean,
    @ColumnInfo(name = "screen_width_dp")
    val screenWidthDp: Int?,
    @ColumnInfo(name = "screen_height_dp")
    val screenHeightDp: Int?,
    @ColumnInfo(name = "smallest_screen_width_dp")
    val smallestScreenWidthDp: Int?,
    @ColumnInfo(name = "orientation")
    val orientation: Int?,
    @ColumnInfo(name = "density_dpi")
    val densityDpi: Int?,
)

@Entity(tableName = "usage_sync_state")
data class UsageSyncStateEntity(
    @PrimaryKey
    @ColumnInfo(name = "singleton_id")
    val singletonId: Int = SINGLETON_ID,
    @ColumnInfo(name = "last_successful_end_millis")
    val lastSuccessfulEndMillis: Long,
    @ColumnInfo(name = "last_successful_at_millis")
    val lastSuccessfulAtMillis: Long,
    @ColumnInfo(name = "last_query_begin_millis")
    val lastQueryBeginMillis: Long,
    @ColumnInfo(name = "last_inserted_event_count")
    val lastInsertedEventCount: Int,
) {
    companion object {
        const val SINGLETON_ID = 1
    }
}

@Entity(
    tableName = "posture_checkpoints",
    indices = [Index(value = ["timestamp_millis"])],
)
data class PostureCheckpointEntity(
    @PrimaryKey
    @ColumnInfo(name = "checkpoint_key")
    val checkpointKey: String,
    @ColumnInfo(name = "timestamp_millis")
    val timestampMillis: Long,
    @ColumnInfo(name = "screen_width_dp")
    val screenWidthDp: Int,
    @ColumnInfo(name = "screen_height_dp")
    val screenHeightDp: Int,
    @ColumnInfo(name = "smallest_screen_width_dp")
    val smallestScreenWidthDp: Int,
    @ColumnInfo(name = "orientation")
    val orientation: Int,
    @ColumnInfo(name = "density_dpi")
    val densityDpi: Int,
    @ColumnInfo(name = "source")
    val source: String,
)

@Entity(tableName = "daily_posture_summary")
data class DailyPostureSummaryEntity(
    @PrimaryKey
    @ColumnInfo(name = "day_start_millis")
    val dayStartMillis: Long,
    @ColumnInfo(name = "day_end_millis")
    val dayEndMillis: Long,
    @ColumnInfo(name = "zone_id")
    val zoneId: String,
    @ColumnInfo(name = "cover_millis")
    val coverMillis: Long,
    @ColumnInfo(name = "inner_millis")
    val innerMillis: Long,
    @ColumnInfo(name = "excluded_millis")
    val excludedMillis: Long,
    @ColumnInfo(name = "opened_count")
    val openedCount: Int,
    @ColumnInfo(name = "closed_count")
    val closedCount: Int,
    @ColumnInfo(name = "evidence_gap_count")
    val evidenceGapCount: Int,
)

@Entity(
    tableName = "daily_app_usage_summary",
    primaryKeys = ["day_start_millis", "package_name"],
    indices = [Index(value = ["package_name"])],
)
data class DailyAppUsageSummaryEntity(
    @ColumnInfo(name = "day_start_millis")
    val dayStartMillis: Long,
    @ColumnInfo(name = "day_end_millis")
    val dayEndMillis: Long,
    @ColumnInfo(name = "zone_id")
    val zoneId: String,
    @ColumnInfo(name = "package_name")
    val packageName: String,
    @ColumnInfo(name = "cover_millis")
    val coverMillis: Long,
    @ColumnInfo(name = "inner_millis")
    val innerMillis: Long,
    @ColumnInfo(name = "excluded_millis")
    val excludedMillis: Long,
)

@Entity(
    tableName = "inner_display_sessions",
    primaryKeys = ["opened_at_millis", "opened_sequence_at_timestamp"],
    indices = [
        Index(value = ["closed_at_millis"]),
    ],
)
data class InnerDisplaySessionEntity(
    @ColumnInfo(name = "opened_at_millis")
    val openedAtMillis: Long,
    @ColumnInfo(name = "opened_sequence_at_timestamp")
    val openedSequenceAtTimestamp: Int,
    @ColumnInfo(name = "closed_at_millis")
    val closedAtMillis: Long?,
    @ColumnInfo(name = "inner_active_millis")
    val innerActiveMillis: Long,
)

@Entity(
    tableName = "inner_display_session_app_usage",
    primaryKeys = [
        "opened_at_millis",
        "opened_sequence_at_timestamp",
        "package_name",
    ],
    foreignKeys = [
        ForeignKey(
            entity = InnerDisplaySessionEntity::class,
            parentColumns = ["opened_at_millis", "opened_sequence_at_timestamp"],
            childColumns = ["opened_at_millis", "opened_sequence_at_timestamp"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["opened_at_millis", "opened_sequence_at_timestamp"]),
        Index(value = ["package_name"]),
    ],
)
data class InnerDisplaySessionAppUsageEntity(
    @ColumnInfo(name = "opened_at_millis")
    val openedAtMillis: Long,
    @ColumnInfo(name = "opened_sequence_at_timestamp")
    val openedSequenceAtTimestamp: Int,
    @ColumnInfo(name = "package_name")
    val packageName: String,
    @ColumnInfo(name = "inner_active_millis")
    val innerActiveMillis: Long,
)

data class AggregatedAppUsage(
    @ColumnInfo(name = "package_name")
    val packageName: String,
    @ColumnInfo(name = "cover_millis")
    val coverMillis: Long,
    @ColumnInfo(name = "inner_millis")
    val innerMillis: Long,
    @ColumnInfo(name = "excluded_millis")
    val excludedMillis: Long,
)

@Entity(tableName = "daily_summary_state")
data class DailySummaryStateEntity(
    @PrimaryKey
    @ColumnInfo(name = "singleton_id")
    val singletonId: Int = SINGLETON_ID,
    @ColumnInfo(name = "last_aggregated_through_millis")
    val lastAggregatedThroughMillis: Long,
    @ColumnInfo(name = "calibration_key")
    val calibrationKey: String,
    @ColumnInfo(name = "zone_id")
    val zoneId: String,
    @ColumnInfo(name = "checkpoint_revision")
    val checkpointRevision: Long,
    @ColumnInfo(name = "aggregation_version")
    val aggregationVersion: Int,
) {
    companion object {
        const val SINGLETON_ID = 1
    }
}

@Entity(
    tableName = "sync_history",
    indices = [
        Index(value = ["attempted_at_millis"]),
        Index(value = ["device_state_observed_at_millis"]),
    ],
)
data class SyncHistoryEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long = 0L,
    @ColumnInfo(name = "attempted_at_millis")
    val attemptedAtMillis: Long,
    @ColumnInfo(name = "query_begin_millis")
    val queryBeginMillis: Long,
    @ColumnInfo(name = "query_end_millis")
    val queryEndMillis: Long,
    @ColumnInfo(name = "status")
    val status: String,
    @ColumnInfo(name = "read_event_count")
    val readEventCount: Int,
    @ColumnInfo(name = "inserted_event_count")
    val insertedEventCount: Int,
    @ColumnInfo(name = "device_state_observed_at_millis")
    val deviceStateObservedAtMillis: Long? = null,
    @ColumnInfo(name = "screen_interactive")
    val screenInteractive: Boolean? = null,
    @ColumnInfo(name = "keyguard_hidden")
    val keyguardHidden: Boolean? = null,
)

@Dao
interface UsageEventDao {
    @Query(
        """
        SELECT * FROM usage_events
        WHERE timestamp_millis >= :beginMillis AND timestamp_millis < :endMillis
        ORDER BY timestamp_millis ASC, sequence_at_timestamp ASC, event_key ASC
        """,
    )
    suspend fun loadEvents(beginMillis: Long, endMillis: Long): List<UsageEventEntity>

    @Query(
        """
        SELECT * FROM usage_events
        WHERE timestamp_millis >= :beginMillis AND timestamp_millis < :endMillis
            AND raw_event_type IN (:rawEventTypes)
        ORDER BY timestamp_millis ASC, sequence_at_timestamp ASC, event_key ASC
        """,
    )
    suspend fun loadDeviceEvents(
        beginMillis: Long,
        endMillis: Long,
        rawEventTypes: List<Int>,
    ): List<UsageEventEntity>

    @Query(
        """
        SELECT * FROM usage_events
        WHERE timestamp_millis < :endMillis
            AND raw_event_type IN (:rawEventTypes)
            AND timestamp_millis = (
                SELECT MAX(timestamp_millis) FROM usage_events
                WHERE timestamp_millis < :endMillis
                    AND raw_event_type IN (:rawEventTypes)
            )
        ORDER BY timestamp_millis ASC, sequence_at_timestamp ASC, event_key ASC
        """,
    )
    suspend fun loadLatestDeviceEventsBefore(
        endMillis: Long,
        rawEventTypes: List<Int>,
    ): List<UsageEventEntity>

    @Query(
        """
        SELECT candidate.* FROM usage_events AS candidate
        INNER JOIN (
            SELECT package_name, class_name, MAX(timestamp_millis) AS latest_timestamp
            FROM usage_events
            WHERE timestamp_millis < :endMillis
                AND raw_event_type IN (:rawEventTypes)
                AND package_name IS NOT NULL
            GROUP BY package_name, class_name
        ) AS latest
            ON candidate.package_name IS latest.package_name
            AND candidate.class_name IS latest.class_name
            AND candidate.timestamp_millis = latest.latest_timestamp
        WHERE candidate.raw_event_type IN (:rawEventTypes)
        ORDER BY candidate.timestamp_millis ASC,
            candidate.sequence_at_timestamp ASC,
            candidate.event_key ASC
        """,
    )
    suspend fun loadLatestActivityEventsBefore(
        endMillis: Long,
        rawEventTypes: List<Int>,
    ): List<UsageEventEntity>

    @Transaction
    suspend fun loadUsageEventsForAnalysis(
        beginMillis: Long,
        endMillis: Long,
    ): List<UsageEventEntity> {
        val seeds = buildList {
            StoredUsageEventTypes.deviceStateGroups.forEach { eventTypes ->
                addAll(loadLatestDeviceEventsBefore(beginMillis, eventTypes))
            }
            addAll(loadLatestActivityEventsBefore(beginMillis, StoredUsageEventTypes.activity))
        }
        return seeds.distinctBy(UsageEventEntity::eventKey) +
            loadDeviceEvents(beginMillis, endMillis, StoredUsageEventTypes.all)
    }

    @Query("SELECT MIN(timestamp_millis) FROM usage_events")
    suspend fun earliestEventTimestamp(): Long?

    @Query(
        """
        SELECT MIN(timestamp_millis) FROM usage_events
        WHERE raw_event_type IN (:rawEventTypes)
        """,
    )
    suspend fun earliestDeviceEventTimestamp(rawEventTypes: List<Int>): Long?

    @Query(
        "SELECT * FROM usage_sync_state WHERE singleton_id = " +
            UsageSyncStateEntity.SINGLETON_ID,
    )
    suspend fun loadSyncState(): UsageSyncStateEntity?

    @Query(
        "SELECT * FROM usage_sync_state WHERE singleton_id = " +
            UsageSyncStateEntity.SINGLETON_ID,
    )
    fun observeSyncState(): Flow<UsageSyncStateEntity?>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertEvents(events: List<UsageEventEntity>): List<Long>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSyncState(state: UsageSyncStateEntity)

    @Insert
    suspend fun insertSyncHistory(attempt: SyncHistoryEntity): Long

    @Query(
        """
        SELECT * FROM sync_history
        WHERE attempted_at_millis >= :beginMillis AND attempted_at_millis < :endMillis
        ORDER BY attempted_at_millis ASC, id ASC
        """,
    )
    suspend fun loadSyncHistory(beginMillis: Long, endMillis: Long): List<SyncHistoryEntity>

    @Query(
        """
        SELECT * FROM sync_history
        WHERE device_state_observed_at_millis >= :beginMillis
            AND device_state_observed_at_millis < :endMillis
            AND status = 'SUCCESS'
            AND screen_interactive IS NOT NULL
            AND keyguard_hidden IS NOT NULL
        ORDER BY device_state_observed_at_millis ASC, id ASC
        """,
    )
    suspend fun loadDeviceStateCheckpoints(
        beginMillis: Long,
        endMillis: Long,
    ): List<SyncHistoryEntity>

    @Query(
        """
        SELECT * FROM sync_history
        WHERE device_state_observed_at_millis < :endMillis
            AND status = 'SUCCESS'
            AND screen_interactive IS NOT NULL
            AND keyguard_hidden IS NOT NULL
        ORDER BY device_state_observed_at_millis DESC, id DESC
        LIMIT 1
        """,
    )
    suspend fun loadLatestDeviceStateCheckpointBefore(endMillis: Long): SyncHistoryEntity?

    @Transaction
    suspend fun loadDeviceStateCheckpointsForAnalysis(
        beginMillis: Long,
        endMillis: Long,
    ): List<SyncHistoryEntity> = buildList {
        loadLatestDeviceStateCheckpointBefore(beginMillis)?.let(::add)
        addAll(loadDeviceStateCheckpoints(beginMillis, endMillis))
    }

    @Query(
        """
        SELECT MIN(device_state_observed_at_millis) FROM sync_history
        WHERE status = 'SUCCESS'
            AND screen_interactive IS NOT NULL
            AND keyguard_hidden IS NOT NULL
        """,
    )
    suspend fun earliestDeviceStateCheckpointTimestamp(): Long?

    @Transaction
    suspend fun persistSuccessfulSync(
        events: List<UsageEventEntity>,
        state: UsageSyncStateEntity,
        attempt: SyncHistoryEntity,
    ): Int {
        val insertedCount = insertEvents(events).count { it != -1L }
        upsertSyncState(state.copy(lastInsertedEventCount = insertedCount))
        insertSyncHistory(attempt.copy(insertedEventCount = insertedCount))
        return insertedCount
    }
}

@Dao
interface PostureCheckpointDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(checkpoint: PostureCheckpointEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(checkpoints: List<PostureCheckpointEntity>): List<Long>

    @Query(
        """
        SELECT * FROM posture_checkpoints
        WHERE timestamp_millis >= :beginMillis AND timestamp_millis < :endMillis
        ORDER BY timestamp_millis ASC, checkpoint_key ASC
        """,
    )
    suspend fun load(beginMillis: Long, endMillis: Long): List<PostureCheckpointEntity>

    @Transaction
    suspend fun loadForAnalysis(
        beginMillis: Long,
        endMillis: Long,
    ): List<PostureCheckpointEntity> = buildList {
        latestBefore(beginMillis)?.let(::add)
        addAll(load(beginMillis, endMillis))
    }

    @Query(
        """
        SELECT * FROM posture_checkpoints
        WHERE timestamp_millis < :endMillis
        ORDER BY timestamp_millis DESC, checkpoint_key DESC
        LIMIT 1
        """,
    )
    suspend fun latestBefore(endMillis: Long): PostureCheckpointEntity?

    @Query(
        """
        SELECT * FROM posture_checkpoints
        WHERE source = :source
        ORDER BY timestamp_millis DESC
        LIMIT 1
        """,
    )
    suspend fun latest(source: String): PostureCheckpointEntity?

    @Query("SELECT COUNT(*) FROM posture_checkpoints")
    fun observeRevision(): Flow<Long>

    @Query("SELECT MIN(timestamp_millis) FROM posture_checkpoints")
    suspend fun earliestTimestamp(): Long?

    @Query("SELECT MAX(timestamp_millis) FROM posture_checkpoints")
    suspend fun latestTimestamp(): Long?
}

@Dao
interface DailyPostureSummaryDao {
    @Query(
        """
        SELECT * FROM daily_posture_summary
        WHERE day_start_millis < :endMillis AND day_end_millis > :beginMillis
        ORDER BY day_start_millis ASC
        """,
    )
    suspend fun load(beginMillis: Long, endMillis: Long): List<DailyPostureSummaryEntity>

    @Query("SELECT * FROM daily_posture_summary ORDER BY day_start_millis ASC")
    suspend fun loadAll(): List<DailyPostureSummaryEntity>

    @Query(
        """
        SELECT
            package_name,
            SUM(cover_millis) AS cover_millis,
            SUM(inner_millis) AS inner_millis,
            SUM(excluded_millis) AS excluded_millis
        FROM daily_app_usage_summary
        WHERE day_start_millis < :endMillis AND day_end_millis > :beginMillis
        GROUP BY package_name
        HAVING SUM(cover_millis) + SUM(inner_millis) + SUM(excluded_millis) > 0
        ORDER BY SUM(cover_millis) + SUM(inner_millis) DESC,
            SUM(cover_millis) + SUM(inner_millis) + SUM(excluded_millis) DESC,
            package_name ASC
        """,
    )
    suspend fun loadAggregatedAppUsage(
        beginMillis: Long,
        endMillis: Long,
    ): List<AggregatedAppUsage>

    @Query(
        """
        SELECT * FROM inner_display_sessions
        WHERE opened_at_millis >= :beginMillis
            AND closed_at_millis IS NOT NULL
            AND closed_at_millis < :endMillis
        ORDER BY opened_at_millis ASC, opened_sequence_at_timestamp ASC
        """,
    )
    suspend fun loadCompleteInnerSessions(
        beginMillis: Long,
        endMillis: Long,
    ): List<InnerDisplaySessionEntity>

    @Query(
        """
        SELECT app_usage.* FROM inner_display_session_app_usage AS app_usage
        INNER JOIN inner_display_sessions AS session
            ON session.opened_at_millis = app_usage.opened_at_millis
            AND session.opened_sequence_at_timestamp = app_usage.opened_sequence_at_timestamp
        WHERE session.opened_at_millis >= :beginMillis
            AND session.closed_at_millis IS NOT NULL
            AND session.closed_at_millis < :endMillis
        ORDER BY session.opened_at_millis ASC,
            session.opened_sequence_at_timestamp ASC,
            app_usage.package_name ASC
        """,
    )
    suspend fun loadCompleteInnerSessionAppUsages(
        beginMillis: Long,
        endMillis: Long,
    ): List<InnerDisplaySessionAppUsageEntity>

    @Query(
        """
        SELECT MIN(opened_at_millis) FROM inner_display_sessions
        WHERE closed_at_millis IS NULL OR closed_at_millis >= :beginMillis
        """,
    )
    suspend fun earliestInnerSessionStartOverlapping(beginMillis: Long): Long?

    @Query(
        "SELECT * FROM daily_summary_state WHERE singleton_id = " +
            DailySummaryStateEntity.SINGLETON_ID,
    )
    suspend fun loadState(): DailySummaryStateEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(summaries: List<DailyPostureSummaryEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllAppUsage(summaries: List<DailyAppUsageSummaryEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllInnerSessions(sessions: List<InnerDisplaySessionEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllInnerSessionAppUsages(
        appUsages: List<InnerDisplaySessionAppUsageEntity>,
    )

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertState(state: DailySummaryStateEntity)

    @Query("DELETE FROM daily_posture_summary")
    suspend fun deleteAll()

    @Query("DELETE FROM daily_app_usage_summary")
    suspend fun deleteAllAppUsage()

    @Query("DELETE FROM inner_display_sessions")
    suspend fun deleteAllInnerSessions()

    @Query("DELETE FROM inner_display_session_app_usage")
    suspend fun deleteAllInnerSessionAppUsages()

    @Query("DELETE FROM daily_posture_summary WHERE day_start_millis >= :beginMillis")
    suspend fun deleteFrom(beginMillis: Long)

    @Query("DELETE FROM daily_app_usage_summary WHERE day_start_millis >= :beginMillis")
    suspend fun deleteAppUsageFrom(beginMillis: Long)

    @Query("DELETE FROM inner_display_sessions WHERE opened_at_millis >= :beginMillis")
    suspend fun deleteInnerSessionsFrom(beginMillis: Long)

    @Query(
        "DELETE FROM inner_display_session_app_usage WHERE opened_at_millis >= :beginMillis",
    )
    suspend fun deleteInnerSessionAppUsagesFrom(beginMillis: Long)

    @Transaction
    suspend fun replaceAll(
        summaries: List<DailyPostureSummaryEntity>,
        appUsage: List<DailyAppUsageSummaryEntity>,
        innerSessions: List<InnerDisplaySessionEntity>,
        innerSessionAppUsages: List<InnerDisplaySessionAppUsageEntity>,
        state: DailySummaryStateEntity,
    ) {
        deleteAll()
        deleteAllAppUsage()
        deleteAllInnerSessionAppUsages()
        deleteAllInnerSessions()
        insertAll(summaries)
        insertAllAppUsage(appUsage)
        insertAllInnerSessions(innerSessions)
        insertAllInnerSessionAppUsages(innerSessionAppUsages)
        upsertState(state)
    }

    @Transaction
    suspend fun replaceAll(
        summaries: List<DailyPostureSummaryEntity>,
        appUsage: List<DailyAppUsageSummaryEntity>,
        innerSessions: List<InnerDisplaySessionEntity>,
        state: DailySummaryStateEntity,
    ) = replaceAll(
        summaries = summaries,
        appUsage = appUsage,
        innerSessions = innerSessions,
        innerSessionAppUsages = emptyList(),
        state = state,
    )

    @Transaction
    suspend fun replaceFrom(
        beginMillis: Long,
        summaries: List<DailyPostureSummaryEntity>,
        appUsage: List<DailyAppUsageSummaryEntity>,
        innerSessions: List<InnerDisplaySessionEntity>,
        innerSessionAppUsages: List<InnerDisplaySessionAppUsageEntity>,
        state: DailySummaryStateEntity,
    ) {
        deleteFrom(beginMillis)
        deleteAppUsageFrom(beginMillis)
        deleteInnerSessionAppUsagesFrom(beginMillis)
        deleteInnerSessionsFrom(beginMillis)
        insertAll(summaries)
        insertAllAppUsage(appUsage)
        insertAllInnerSessions(innerSessions)
        insertAllInnerSessionAppUsages(innerSessionAppUsages)
        upsertState(state)
    }

    @Transaction
    suspend fun replaceFrom(
        beginMillis: Long,
        summaries: List<DailyPostureSummaryEntity>,
        appUsage: List<DailyAppUsageSummaryEntity>,
        innerSessions: List<InnerDisplaySessionEntity>,
        state: DailySummaryStateEntity,
    ) = replaceFrom(
        beginMillis = beginMillis,
        summaries = summaries,
        appUsage = appUsage,
        innerSessions = innerSessions,
        innerSessionAppUsages = emptyList(),
        state = state,
    )
}

@Database(
    entities = [
        UsageEventEntity::class,
        UsageSyncStateEntity::class,
        PostureCheckpointEntity::class,
        DailyPostureSummaryEntity::class,
        DailyAppUsageSummaryEntity::class,
        InnerDisplaySessionEntity::class,
        InnerDisplaySessionAppUsageEntity::class,
        DailySummaryStateEntity::class,
        SyncHistoryEntity::class,
    ],
    version = 4,
    exportSchema = true,
)
abstract class FoldlyticsDatabase : RoomDatabase() {
    abstract fun usageEventDao(): UsageEventDao

    abstract fun postureCheckpointDao(): PostureCheckpointDao

    abstract fun dailyPostureSummaryDao(): DailyPostureSummaryDao

    companion object {
        @Volatile
        private var instance: FoldlyticsDatabase? = null

        fun getInstance(context: Context): FoldlyticsDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    FoldlyticsDatabase::class.java,
                    "foldlytics.db",
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
                    .build()
                    .also { instance = it }
            }
    }
}

internal val MIGRATION_1_2 = object : Migration(1, 2) {
    private val placeholders = StoredUsageEventTypes.all
        .joinToString(separator = ",") { "?" }
    private val bindArgs: Array<out Any?> = StoredUsageEventTypes.all
        .map { it.toLong() }
        .toTypedArray()

    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "DELETE FROM usage_events WHERE raw_event_type NOT IN ($placeholders)",
            bindArgs,
        )
    }
}

internal val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE sync_history ADD COLUMN device_state_observed_at_millis INTEGER")
        db.execSQL("ALTER TABLE sync_history ADD COLUMN screen_interactive INTEGER")
        db.execSQL("ALTER TABLE sync_history ADD COLUMN keyguard_hidden INTEGER")
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS " +
                "index_sync_history_device_state_observed_at_millis " +
                "ON sync_history (device_state_observed_at_millis)",
        )
    }
}

internal val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `inner_display_sessions` (
                `opened_at_millis` INTEGER NOT NULL,
                `opened_sequence_at_timestamp` INTEGER NOT NULL,
                `closed_at_millis` INTEGER,
                `inner_active_millis` INTEGER NOT NULL,
                PRIMARY KEY(`opened_at_millis`, `opened_sequence_at_timestamp`)
            )
            """.trimIndent(),
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_inner_display_sessions_closed_at_millis` " +
                "ON `inner_display_sessions` (`closed_at_millis`)",
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `inner_display_session_app_usage` (
                `opened_at_millis` INTEGER NOT NULL,
                `opened_sequence_at_timestamp` INTEGER NOT NULL,
                `package_name` TEXT NOT NULL,
                `inner_active_millis` INTEGER NOT NULL,
                PRIMARY KEY(`opened_at_millis`, `opened_sequence_at_timestamp`, `package_name`),
                FOREIGN KEY(`opened_at_millis`, `opened_sequence_at_timestamp`)
                    REFERENCES `inner_display_sessions`(`opened_at_millis`, `opened_sequence_at_timestamp`)
                    ON UPDATE NO ACTION ON DELETE CASCADE
            )
            """.trimIndent(),
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS " +
                "`index_inner_display_session_app_usage_opened_at_millis_opened_sequence_at_timestamp` " +
                "ON `inner_display_session_app_usage` " +
                "(`opened_at_millis`, `opened_sequence_at_timestamp`)",
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_inner_display_session_app_usage_package_name` " +
                "ON `inner_display_session_app_usage` (`package_name`)",
        )
    }
}

class RoomUsageEventStore(
    private val dao: UsageEventDao,
) : UsageEventStore {
    override suspend fun loadSyncState(): UsageSyncState? = dao.loadSyncState()?.toModel()

    override fun observeSyncState(): Flow<UsageSyncState?> =
        dao.observeSyncState().map { it?.toModel() }

    override suspend fun persistSuccessfulSync(
        records: List<UsageRecord>,
        state: UsageSyncState,
        attempt: SyncAttempt,
    ): Int = dao.persistSuccessfulSync(
        events = records.map(UsageRecord::toEntity),
        state = state.toEntity(),
        attempt = attempt.toEntity(),
    )

    override suspend fun recordSyncAttempt(attempt: SyncAttempt) {
        dao.insertSyncHistory(attempt.toEntity())
    }

    override suspend fun loadRecordsForAnalysis(
        beginMillis: Long,
        endMillis: Long,
    ): List<UsageRecord> =
        dao.loadUsageEventsForAnalysis(beginMillis, endMillis).map(UsageEventEntity::toModel)

    override suspend fun loadSyncAttempts(
        beginMillis: Long,
        endMillis: Long,
    ): List<SyncAttempt> = dao.loadSyncHistory(beginMillis, endMillis).map(SyncHistoryEntity::toModel)

    override suspend fun loadDeviceStateCheckpointsForAnalysis(
        beginMillis: Long,
        endMillis: Long,
    ): List<DeviceStateCheckpoint> =
        dao.loadDeviceStateCheckpointsForAnalysis(beginMillis, endMillis)
            .mapNotNull(SyncHistoryEntity::toDeviceStateCheckpoint)
}

internal fun UsageRecord.toEntity(): UsageEventEntity {
    val config = configuration
    return UsageEventEntity(
        eventKey = stableKey(
            timestampMillis.toString(),
            rawEventType.toString(),
            packageName,
            className,
            config?.screenWidthDp?.toString(),
            config?.screenHeightDp?.toString(),
            config?.smallestScreenWidthDp?.toString(),
            config?.orientation?.toString(),
            config?.densityDpi?.toString(),
        ),
        timestampMillis = timestampMillis,
        sequenceAtTimestamp = sequenceAtTimestamp,
        rawEventType = rawEventType,
        packageName = packageName,
        className = className,
        hasConfiguration = config != null,
        screenWidthDp = config?.screenWidthDp,
        screenHeightDp = config?.screenHeightDp,
        smallestScreenWidthDp = config?.smallestScreenWidthDp,
        orientation = config?.orientation,
        densityDpi = config?.densityDpi,
    )
}

internal fun UsageEventEntity.toModel(): UsageRecord = UsageRecord(
    timestampMillis = timestampMillis,
    kind = rawEventType.toUsageEventKind(),
    packageName = packageName,
    className = className,
    configuration = if (hasConfiguration) {
        DisplayConfiguration(
            screenWidthDp = requireNotNull(screenWidthDp),
            screenHeightDp = requireNotNull(screenHeightDp),
            smallestScreenWidthDp = requireNotNull(smallestScreenWidthDp),
            orientation = requireNotNull(orientation),
            densityDpi = requireNotNull(densityDpi),
        )
    } else {
        null
    },
    rawEventType = rawEventType,
    sequenceAtTimestamp = sequenceAtTimestamp,
)

private fun UsageSyncState.toEntity(): UsageSyncStateEntity = UsageSyncStateEntity(
    lastSuccessfulEndMillis = lastSuccessfulEndMillis,
    lastSuccessfulAtMillis = lastSuccessfulAtMillis,
    lastQueryBeginMillis = lastQueryBeginMillis,
    lastInsertedEventCount = lastInsertedEventCount,
)

private fun UsageSyncStateEntity.toModel(): UsageSyncState = UsageSyncState(
    lastSuccessfulEndMillis = lastSuccessfulEndMillis,
    lastSuccessfulAtMillis = lastSuccessfulAtMillis,
    lastQueryBeginMillis = lastQueryBeginMillis,
    lastInsertedEventCount = lastInsertedEventCount,
)

internal fun SyncAttempt.toEntity(): SyncHistoryEntity = SyncHistoryEntity(
    attemptedAtMillis = attemptedAtMillis,
    queryBeginMillis = queryBeginMillis,
    queryEndMillis = queryEndMillis,
    status = status.name,
    readEventCount = readEventCount,
    insertedEventCount = insertedEventCount,
    deviceStateObservedAtMillis = deviceStateCheckpoint?.observedAtMillis,
    screenInteractive = deviceStateCheckpoint?.screenInteractive,
    keyguardHidden = deviceStateCheckpoint?.keyguardHidden,
)

internal fun SyncHistoryEntity.toModel(): SyncAttempt = SyncAttempt(
    attemptedAtMillis = attemptedAtMillis,
    queryBeginMillis = queryBeginMillis,
    queryEndMillis = queryEndMillis,
    status = runCatching { SyncAttemptStatus.valueOf(status) }
        .getOrDefault(SyncAttemptStatus.FAILED),
    readEventCount = readEventCount,
    insertedEventCount = insertedEventCount,
    deviceStateCheckpoint = toDeviceStateCheckpoint(),
)

internal fun SyncHistoryEntity.toDeviceStateCheckpoint(): DeviceStateCheckpoint? {
    val observedAtMillis = deviceStateObservedAtMillis ?: return null
    val interactive = screenInteractive ?: return null
    val hidden = keyguardHidden ?: return null
    return DeviceStateCheckpoint(
        observedAtMillis = observedAtMillis,
        screenInteractive = interactive,
        keyguardHidden = hidden,
    )
}

internal fun PostureCheckpoint.toEntity(): PostureCheckpointEntity = PostureCheckpointEntity(
    checkpointKey = stableKey(
        timestampMillis.toString(),
        configuration.screenWidthDp.toString(),
        configuration.screenHeightDp.toString(),
        configuration.smallestScreenWidthDp.toString(),
        configuration.orientation.toString(),
        configuration.densityDpi.toString(),
        source.name,
    ),
    timestampMillis = timestampMillis,
    screenWidthDp = configuration.screenWidthDp,
    screenHeightDp = configuration.screenHeightDp,
    smallestScreenWidthDp = configuration.smallestScreenWidthDp,
    orientation = configuration.orientation,
    densityDpi = configuration.densityDpi,
    source = source.name,
)

internal fun PostureCheckpointEntity.toModel(): PostureCheckpoint = PostureCheckpoint(
    timestampMillis = timestampMillis,
    configuration = DisplayConfiguration(
        screenWidthDp = screenWidthDp,
        screenHeightDp = screenHeightDp,
        smallestScreenWidthDp = smallestScreenWidthDp,
        orientation = orientation,
        densityDpi = densityDpi,
    ),
    source = PostureCheckpointSource.valueOf(source),
)

internal fun DailyPostureSummary.toEntity(): DailyPostureSummaryEntity =
    DailyPostureSummaryEntity(
        dayStartMillis = dayStartMillis,
        dayEndMillis = dayEndMillis,
        zoneId = zoneId,
        coverMillis = coverMillis,
        innerMillis = innerMillis,
        excludedMillis = excludedMillis,
        openedCount = openedCount,
        closedCount = closedCount,
        evidenceGapCount = evidenceGapCount,
    )

internal fun DailyPostureSummaryEntity.toModel(): DailyPostureSummary =
    DailyPostureSummary(
        dayStartMillis = dayStartMillis,
        dayEndMillis = dayEndMillis,
        zoneId = zoneId,
        coverMillis = coverMillis,
        innerMillis = innerMillis,
        excludedMillis = excludedMillis,
        openedCount = openedCount,
        closedCount = closedCount,
        evidenceGapCount = evidenceGapCount,
    )

internal fun DailyAppUsageSummary.toEntity(): DailyAppUsageSummaryEntity =
    DailyAppUsageSummaryEntity(
        dayStartMillis = dayStartMillis,
        dayEndMillis = dayEndMillis,
        zoneId = zoneId,
        packageName = packageName,
        coverMillis = coverMillis,
        innerMillis = innerMillis,
        excludedMillis = excludedMillis,
    )

internal fun InnerDisplaySession.toEntity(): InnerDisplaySessionEntity =
    InnerDisplaySessionEntity(
        openedAtMillis = openedAtMillis,
        openedSequenceAtTimestamp = openedSequenceAtTimestamp,
        closedAtMillis = closedAtMillis,
        innerActiveMillis = innerActiveMillis,
    )

internal fun InnerDisplaySession.toAppUsageEntities(): List<InnerDisplaySessionAppUsageEntity> =
    appUsageMillis.asSequence()
        .filter { (_, millis) -> millis > 0L }
        .map { (packageName, millis) ->
            InnerDisplaySessionAppUsageEntity(
                openedAtMillis = openedAtMillis,
                openedSequenceAtTimestamp = openedSequenceAtTimestamp,
                packageName = packageName,
                innerActiveMillis = millis,
            )
        }
        .toList()

internal fun InnerDisplaySessionEntity.toModel(
    appUsages: List<InnerDisplaySessionAppUsageEntity> = emptyList(),
): InnerDisplaySession =
    InnerDisplaySession(
        openedAtMillis = openedAtMillis,
        openedSequenceAtTimestamp = openedSequenceAtTimestamp,
        closedAtMillis = closedAtMillis,
        innerActiveMillis = innerActiveMillis,
        appUsageMillis = appUsages.associate { it.packageName to it.innerActiveMillis },
    )

private fun stableKey(vararg fields: String?): String {
    val canonical = buildString {
        fields.forEach { field ->
            if (field == null) {
                append("-1:")
            } else {
                append(field.length).append(':').append(field)
            }
        }
    }
    return MessageDigest.getInstance("SHA-256")
        .digest(canonical.toByteArray(Charsets.UTF_8))
        .joinToString(separator = "") { byte ->
            (byte.toInt() and 0xff).toString(16).padStart(2, '0')
        }
}
