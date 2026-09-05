package com.nagopy.android.foldlytics.data

import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.withTransaction
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.nagopy.android.foldlytics.model.Calibration
import com.nagopy.android.foldlytics.model.DisplayConfiguration
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.OutputStream
import java.util.UUID
import java.util.zip.ZipInputStream
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DiagnosticArchiveExporterTest {
    private lateinit var context: Context
    private lateinit var database: FoldlyticsDatabase
    private lateinit var databaseName: String
    private lateinit var unpacked: File

    @Before
    fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        databaseName = "diagnostic-test-${UUID.randomUUID()}.db"
        database = Room.databaseBuilder(context, FoldlyticsDatabase::class.java, databaseName)
            .setJournalMode(RoomDatabase.JournalMode.WRITE_AHEAD_LOGGING)
            .build()
        unpacked = File(context.cacheDir, "diagnostic-test-${UUID.randomUUID()}")
        check(unpacked.mkdir())
    }

    @After
    fun tearDown() {
        database.close()
        context.deleteDatabase(databaseName)
        unpacked.deleteRecursively()
    }

    @Test
    fun exportsCommittedWalRowsAndEveryTableWithSchemaAndTypes() = runBlocking {
        val before = withContext(Dispatchers.IO) {
            val source = database.openHelper.writableDatabase
            source.query("PRAGMA wal_checkpoint(TRUNCATE)").close()
            database.withTransaction {
                source.execSQL(
                    "INSERT INTO usage_events VALUES " +
                        "('event', 123, 0, 1, 'missing.package', NULL, 0, NULL, NULL, NULL, NULL, NULL)",
                )
                source.execSQL("INSERT INTO usage_sync_state VALUES (1, 500, 600, 0, 1)")
                source.execSQL("INSERT INTO posture_checkpoints VALUES ('point', 123, 400, 900, 400, 1, 420, 'ACTIVITY')")
                source.execSQL("INSERT INTO daily_posture_summary VALUES (0, 500, 'UTC', 10, 20, 30, 1, 0, 2)")
                source.execSQL("INSERT INTO daily_app_usage_summary VALUES (0, 500, 'UTC', 'cache.package', 10, 20, 30)")
                source.execSQL("INSERT INTO inner_display_sessions VALUES (123, 0, NULL, 20)")
                source.execSQL("INSERT INTO inner_display_session_app_usage VALUES (123, 0, 'session.package', 20)")
                source.execSQL("INSERT INTO daily_summary_state VALUES (1, 500, 'key', 'UTC', 0, 1, 1)")
                source.execSQL("INSERT INTO sync_history VALUES (50, 600, 0, 500, 'SUCCESS', 1, 1, 600, 1, 1)")
                source.execSQL("INSERT INTO sync_history VALUES (99, 600, 0, 500, 'FAILED', 0, 0, NULL, NULL, NULL)")
                source.execSQL("DELETE FROM sync_history WHERE id = 99")
                source.execSQL("CREATE TABLE diagnostic_types (n, i, d, b, t)")
                source.execSQL(
                    "INSERT INTO diagnostic_types VALUES (?, ?, ?, ?, ?)",
                    arrayOf(null, Long.MAX_VALUE, 1.25, byteArrayOf(0, 1, -1), "quote'日本語"),
                )
            }
            assertTrue(File(context.getDatabasePath(databaseName).path + "-wal").length() > 0)
            captureSource(source)
        }
        val calibration = Calibration(cover = DisplayConfiguration(400, 900, 400, 1, 420))
        val entries = export(calibration)
        assertEquals(setOf("foldlytics.db", "metadata.json", "diagnostic-report.txt"), entries.keys)
        assertEquals("diagnostic details 日本語", entries.getValue("diagnostic-report.txt").toString(Charsets.UTF_8))
        val metadata = JSONObject(entries.getValue("metadata.json").toString(Charsets.UTF_8))
        assertEquals(1, metadata.getInt("formatVersion"))
        assertEquals(5, metadata.getInt("sourceSchemaVersion"))
        assertEquals(400, metadata.getJSONObject("calibration").getJSONObject("cover").getInt("screenWidthDp"))
        assertTrue(metadata.getJSONObject("calibration").isNull("inner"))
        assertNotNull(metadata.getJSONObject("app").getString("applicationId"))
        assertNotNull(metadata.getJSONObject("device").getString("model"))
        val packages = metadata.getJSONArray("packages")
        assertEquals(
            setOf("missing.package", "cache.package", "session.package"),
            (0 until packages.length()).map { packages.getJSONObject(it).getString("packageName") }.toSet(),
        )
        for (index in 0 until packages.length()) {
            assertFalse(packages.getJSONObject(index).getBoolean("isLauncherApp"))
        }
        val snapshot = File(unpacked, "foldlytics.db").apply { writeBytes(entries.getValue("foldlytics.db")) }
        SQLiteDatabase.openDatabase(snapshot.path, null, SQLiteDatabase.OPEN_READONLY).use { copied ->
            assertEquals("ok", copied.rawQuery("PRAGMA integrity_check", null).use { it.moveToFirst(); it.getString(0) })
            assertEquals(5, copied.version)
            assertEquals(before, capture { copied.rawQuery(it, null) })
            copied.rawQuery("SELECT seq FROM sqlite_sequence WHERE name = 'sync_history'", null).use {
                assertTrue(it.moveToFirst())
                assertEquals(99L, it.getLong(0))
            }
        }
        withContext(Dispatchers.IO) {
            assertEquals(before, captureSource(database.openHelper.writableDatabase))
            val reopened = Room.databaseBuilder(context, FoldlyticsDatabase::class.java, snapshot.absolutePath)
                .build()
            try {
                assertEquals(1, reopened.usageEventDao().loadEvents(0, 1000).size)
            } finally {
                reopened.close()
            }
        }
        assertNoTemporaryExports()
    }

    @Test
    fun exportsFreshEmptyDatabase() = runBlocking {
        val entries = export(Calibration())
        val snapshot = File(unpacked, "empty.db").apply { writeBytes(entries.getValue("foldlytics.db")) }
        withContext(Dispatchers.IO) {
            val reopened = Room.databaseBuilder(context, FoldlyticsDatabase::class.java, snapshot.absolutePath)
                .build()
            try {
                assertTrue(reopened.usageEventDao().loadEvents(0, Long.MAX_VALUE).isEmpty())
                assertEquals(5, reopened.openHelper.writableDatabase.version)
            } finally {
                reopened.close()
            }
        }
        assertNoTemporaryExports()
    }

    @Test
    fun snapshotFailureDoesNotOpenDestinationAndCleansTemporaryFiles() = runBlocking {
        // An invalid table declaration makes cloning fail without touching the destination.
        withContext(Dispatchers.IO) {
            val source = database.openHelper.writableDatabase
            source.execSQL("CREATE TABLE invalid_snapshot (value TEXT)")
            source.execSQL("PRAGMA writable_schema = ON")
            source.execSQL("UPDATE sqlite_master SET sql = 'INVALID SQL' WHERE name = 'invalid_snapshot'")
            source.execSQL("PRAGMA writable_schema = OFF")
        }
        var opened = false
        val result = runCatching {
            DiagnosticArchiveExporter(context, database).export(Calibration(), "report") {
                opened = true
                ByteArrayOutputStream()
            }
        }
        assertTrue(result.isFailure)
        assertFalse(opened)
        assertNoTemporaryExports()
    }

    @Test
    fun writeFailureClosesOutputAndCleansTemporaryFiles() = runBlocking {
        var closed = false
        val result = runCatching {
            DiagnosticArchiveExporter(context, database).export(Calibration(), "report") {
                object : OutputStream() {
                    override fun write(value: Int): Unit = throw IOException("Destination unavailable")
                    override fun close() { closed = true }
                }
            }
        }
        assertTrue(result.exceptionOrNull() is IOException)
        assertTrue(closed)
        assertNoTemporaryExports()
    }

    @Test
    fun cancellationClosesOutputAndCleansTemporaryFiles() = runBlocking {
        var closed = false
        val result = runCatching {
            DiagnosticArchiveExporter(context, database).export(Calibration(), "report") {
                object : OutputStream() {
                    override fun write(value: Int): Unit = throw CancellationException("Cancelled")
                    override fun close() { closed = true }
                }
            }
        }
        assertTrue(result.exceptionOrNull() is CancellationException)
        assertTrue(closed)
        assertNoTemporaryExports()
    }

    private suspend fun export(calibration: Calibration): Map<String, ByteArray> {
        val bytes = ByteArrayOutputStream()
        DiagnosticArchiveExporter(context, database).export(calibration, "diagnostic details 日本語") { bytes }
        return ZipInputStream(bytes.toByteArray().inputStream()).use { zip ->
            buildMap {
                while (true) {
                    val entry = zip.nextEntry ?: break
                    put(entry.name, zip.readBytes())
                }
            }
        }
    }

    private fun assertNoTemporaryExports() {
        assertTrue(context.cacheDir.listFiles().orEmpty().none { it.name.startsWith("diagnostic-export-") })
    }

    private fun captureSource(source: SupportSQLiteDatabase): Map<String, List<List<String>>> =
        capture(source::query)

    private fun capture(query: (String) -> Cursor): Map<String, List<List<String>>> {
        val tables = query("SELECT name FROM sqlite_master WHERE type = 'table' ORDER BY name").use { cursor ->
            buildList { while (cursor.moveToNext()) add(cursor.getString(0)) }
        }
        return buildMap {
            put("schema", rows(query("SELECT type, name, sql FROM sqlite_master ORDER BY name")))
            tables.forEach { table -> put(table, rows(query("SELECT * FROM \"$table\""))) }
        }
    }

    private fun rows(cursor: Cursor): List<List<String>> = cursor.use {
        buildList {
            while (cursor.moveToNext()) {
                add((0 until cursor.columnCount).map { column ->
                    when (cursor.getType(column)) {
                        Cursor.FIELD_TYPE_NULL -> "null"
                        Cursor.FIELD_TYPE_INTEGER -> "integer:${cursor.getLong(column)}"
                        Cursor.FIELD_TYPE_FLOAT -> "float:${cursor.getDouble(column)}"
                        Cursor.FIELD_TYPE_BLOB -> "blob:${cursor.getBlob(column).joinToString()}"
                        else -> "text:${cursor.getString(column)}"
                    }
                })
            }
        }.sortedBy { row -> row.joinToString() }
    }
}
