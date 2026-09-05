package com.nagopy.android.foldlytics.data

import com.nagopy.android.foldlytics.model.DailyPostureSummary
import java.io.Writer
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/** Supplies the saved daily history an export must contain. */
fun interface SavedDailyHistorySource {
    suspend fun load(): List<DailyPostureSummary>
}

/** Opens the export destination, discarding whatever it already holds. */
fun interface CsvExportOutput {
    fun openTruncating(): Writer
}

/**
 * Writes the saved daily history as CSV.
 *
 * The history is loaded before the destination is opened. The destination is truncated as it is
 * opened, so opening it first would destroy an existing file whenever loading fails, is cancelled,
 * or simply has not finished yet - which is the normal state right after the process is recreated
 * while the document picker is open.
 */
class LongTermCsvExporter(
    private val history: SavedDailyHistorySource,
) {
    suspend fun export(output: CsvExportOutput) {
        val summaries = history.load()
        currentCoroutineContext().ensureActive()
        output.openTruncating().use { writer ->
            LongTermCsvWriter.write(summaries, writer)
        }
    }
}
