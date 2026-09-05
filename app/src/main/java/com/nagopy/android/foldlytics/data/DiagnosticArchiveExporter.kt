package com.nagopy.android.foldlytics.data

import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.os.Build
import androidx.room.withTransaction
import androidx.sqlite.db.SupportSQLiteDatabase
import com.nagopy.android.foldlytics.BuildConfig
import com.nagopy.android.foldlytics.model.Calibration
import com.nagopy.android.foldlytics.model.DisplayConfiguration
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.time.Instant
import java.time.ZoneId
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

fun interface DiagnosticArchiveOutput {
    fun openTruncating(): OutputStream
}

/** Builds a standalone database from one transaction, including committed rows still in WAL. */
class DiagnosticArchiveExporter(
    private val context: Context,
    private val database: FoldlyticsDatabase,
) {
    suspend fun export(
        calibration: Calibration,
        diagnosticReport: String,
        output: DiagnosticArchiveOutput,
    ) = withContext(Dispatchers.IO) {
        val directory = File(context.cacheDir, "diagnostic-export-${UUID.randomUUID()}")
        check(directory.mkdir()) { "Could not create diagnostic export directory" }
        try {
            val snapshot = File(directory, "foldlytics.db")
            val captured = createSnapshot(snapshot)
            val metadata = createMetadata(calibration, captured)
            val archive = File(directory, "diagnostics.zip")
            ZipOutputStream(archive.outputStream().buffered()).use { zip ->
                zip.putNextEntry(ZipEntry("foldlytics.db"))
                snapshot.inputStream().use { it.copyCancellableTo(zip) }
                zip.closeEntry()
                zip.putNextEntry(ZipEntry("metadata.json"))
                metadata.toString(2).byteInputStream().use { it.copyCancellableTo(zip) }
                zip.closeEntry()
                zip.putNextEntry(ZipEntry("diagnostic-report.txt"))
                diagnosticReport.byteInputStream().use { it.copyCancellableTo(zip) }
                zip.closeEntry()
            }
            // A failed snapshot or ZIP construction must never truncate the selected document.
            currentCoroutineContext().ensureActive()
            output.openTruncating().use { destination ->
                archive.inputStream().use { it.copyCancellableTo(destination) }
            }
        } finally {
            directory.deleteRecursively()
        }
    }

    private suspend fun createSnapshot(file: File): SnapshotMetadata = database.withTransaction {
        val source = database.openHelper.writableDatabase
        val schema = source.query(
            "SELECT type, name, sql FROM sqlite_master " +
                "WHERE sql IS NOT NULL AND name NOT GLOB 'sqlite_*' ORDER BY name",
        ).use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    currentCoroutineContext().ensureActive()
                    add(SchemaObject(cursor.getString(0), cursor.getString(1), cursor.getString(2)))
                }
            }
        }
        val packages = sortedSetOf<String>()
        val version = source.version
        val createdAt = Instant.now().toString()
        SQLiteDatabase.openDatabase(
            file.absolutePath,
            null,
            SQLiteDatabase.CREATE_IF_NECESSARY or SQLiteDatabase.NO_LOCALIZED_COLLATORS,
        ).use { target ->
            target.beginTransaction()
            try {
                schema.filter { it.type == "table" }.forEach { table ->
                    currentCoroutineContext().ensureActive()
                    target.execSQL(table.sql)
                    copyTable(source, target, table.name, packages)
                }
                // Explicit indexes and triggers are installed after inserts so triggers cannot
                // change the copied rows. SQLite recreates implicit constraint indexes itself.
                schema.filter { it.type != "table" }.forEach { item ->
                    currentCoroutineContext().ensureActive()
                    target.execSQL(item.sql)
                }
                source.query("SELECT 1 FROM sqlite_master WHERE name = 'sqlite_sequence'")
                    .use { cursor ->
                        if (cursor.moveToFirst()) {
                            target.execSQL("DELETE FROM sqlite_sequence")
                            copyTable(source, target, "sqlite_sequence", packages)
                        }
                    }
                target.version = version
                target.setTransactionSuccessful()
            } finally {
                target.endTransaction()
            }
        }
        SnapshotMetadata(version, createdAt, packages)
    }

    private suspend fun copyTable(
        source: SupportSQLiteDatabase,
        target: SQLiteDatabase,
        tableName: String,
        packages: MutableSet<String>,
    ) {
        source.query("SELECT * FROM ${quoteIdentifier(tableName)}").use { cursor ->
            val columns = cursor.columnNames.joinToString(",", transform = ::quoteIdentifier)
            val placeholders = cursor.columnNames.joinToString(",") { "?" }
            val packageIndex = cursor.getColumnIndex("package_name")
            target.compileStatement(
                "INSERT INTO ${quoteIdentifier(tableName)} ($columns) VALUES ($placeholders)",
            ).use { insert ->
                while (cursor.moveToNext()) {
                    currentCoroutineContext().ensureActive()
                    insert.clearBindings()
                    for (column in 0 until cursor.columnCount) {
                        val parameter = column + 1
                        when (cursor.getType(column)) {
                            Cursor.FIELD_TYPE_NULL -> insert.bindNull(parameter)
                            Cursor.FIELD_TYPE_INTEGER -> insert.bindLong(parameter, cursor.getLong(column))
                            Cursor.FIELD_TYPE_FLOAT -> insert.bindDouble(parameter, cursor.getDouble(column))
                            Cursor.FIELD_TYPE_BLOB -> insert.bindBlob(parameter, cursor.getBlob(column))
                            Cursor.FIELD_TYPE_STRING -> insert.bindString(parameter, cursor.getString(column))
                            else -> error("Unsupported SQLite value type")
                        }
                    }
                    insert.executeInsert()
                    if (packageIndex >= 0 && !cursor.isNull(packageIndex)) {
                        packages.add(cursor.getString(packageIndex))
                    }
                }
            }
        }
    }

    private suspend fun createMetadata(
        calibration: Calibration,
        snapshot: SnapshotMetadata,
    ): JSONObject {
        val packages = JSONArray()
        for (packageName in snapshot.packages) {
            currentCoroutineContext().ensureActive()
            val label = try {
                @Suppress("DEPRECATION")
                val info = context.packageManager.getApplicationInfo(packageName, 0)
                context.packageManager.getApplicationLabel(info).toString()
            } catch (_: android.content.pm.PackageManager.NameNotFoundException) {
                packageName
            }
            packages.put(
                JSONObject()
                    .put("packageName", packageName)
                    .put("label", label)
                    .put(
                        "isLauncherApp",
                        context.packageManager.getLaunchIntentForPackage(packageName) != null,
                    ),
            )
        }
        return JSONObject()
            .put("formatVersion", 1)
            .put("createdAt", snapshot.createdAt)
            .put("sourceSchemaVersion", snapshot.schemaVersion)
            .put("timezone", ZoneId.systemDefault().id)
            .put(
                "app",
                JSONObject()
                    .put("applicationId", BuildConfig.APPLICATION_ID)
                    .put("versionName", BuildConfig.VERSION_NAME)
                    .put("versionCode", BuildConfig.VERSION_CODE)
                    .put("buildType", BuildConfig.BUILD_TYPE),
            )
            .put(
                "device",
                JSONObject()
                    .put("sdkInt", Build.VERSION.SDK_INT)
                    .put("manufacturer", Build.MANUFACTURER)
                    .put("model", Build.MODEL),
            )
            .put(
                "calibration",
                JSONObject()
                    .put("cover", calibration.cover.toJson())
                    .put("inner", calibration.inner.toJson()),
            )
            .put("packages", packages)
    }

    private fun DisplayConfiguration?.toJson(): Any = this?.let {
        JSONObject()
            .put("screenWidthDp", screenWidthDp)
            .put("screenHeightDp", screenHeightDp)
            .put("smallestScreenWidthDp", smallestScreenWidthDp)
            .put("orientation", orientation)
            .put("densityDpi", densityDpi)
    } ?: JSONObject.NULL

    private suspend fun InputStream.copyCancellableTo(output: OutputStream) {
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        while (true) {
            currentCoroutineContext().ensureActive()
            val count = read(buffer)
            if (count < 0) break
            output.write(buffer, 0, count)
        }
    }

    private fun quoteIdentifier(value: String): String = "\"${value.replace("\"", "\"\"")}\""

    private data class SchemaObject(val type: String, val name: String, val sql: String)

    private data class SnapshotMetadata(
        val schemaVersion: Int,
        val createdAt: String,
        val packages: Set<String>,
    )
}
