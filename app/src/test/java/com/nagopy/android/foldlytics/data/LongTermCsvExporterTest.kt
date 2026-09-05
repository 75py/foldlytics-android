package com.nagopy.android.foldlytics.data

import com.nagopy.android.foldlytics.model.DailyPostureSummary
import java.io.IOException
import java.io.StringWriter
import java.io.Writer
import java.time.LocalDate
import java.time.ZoneOffset
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LongTermCsvExporterTest {
    @Test
    fun exportsSavedHistoryWithoutAnyPreloadedScreenState() = runBlocking {
        val output = RecordingOutput()
        val exporter = LongTermCsvExporter { listOf(summary(LocalDate.of(2024, 3, 1))) }

        exporter.export(output)

        val lines = output.written().lineSequence().filter(String::isNotEmpty).toList()
        assertEquals(2, lines.size)
        assertTrue(lines[1].startsWith("\"2024-03-01\",\"Z\""))
        assertTrue(output.isClosed)
    }

    @Test
    fun waitsForTheSavedHistoryBeforeOpeningTheOutput() = runBlocking {
        val loaded = CompletableDeferred<List<DailyPostureSummary>>()
        val output = RecordingOutput()
        val exporter = LongTermCsvExporter { loaded.await() }

        val export = launch { exporter.export(output) }
        yield()
        assertEquals(0, output.openCount)

        loaded.complete(listOf(summary(LocalDate.of(2024, 3, 2))))
        export.join()

        assertEquals(1, output.openCount)
        assertTrue(output.written().contains("\"2024-03-02\""))
    }

    @Test
    fun doesNotOpenTheOutputWhenLoadingFails() = runBlocking {
        val output = RecordingOutput()
        val exporter = LongTermCsvExporter { throw IOException("database unavailable") }

        val failure = runCatching { exporter.export(output) }.exceptionOrNull()

        assertTrue(failure is IOException)
        assertEquals(0, output.openCount)
    }

    @Test
    fun doesNotOpenTheOutputWhenCancelledWhileLoading() = runBlocking {
        val loaded = CompletableDeferred<List<DailyPostureSummary>>()
        val output = RecordingOutput()
        val exporter = LongTermCsvExporter { loaded.await() }

        val export = launch { exporter.export(output) }
        yield()
        export.cancel()
        export.join()

        assertTrue(export.isCancelled)
        assertEquals(0, output.openCount)
    }

    @Test
    fun doesNotOpenTheOutputWhenCancelledAfterLoading() = runBlocking {
        val output = RecordingOutput()
        lateinit var export: Job
        val exporter = LongTermCsvExporter {
            export.cancel()
            listOf(summary(LocalDate.of(2024, 3, 5)))
        }

        export = launch(start = CoroutineStart.LAZY) { exporter.export(output) }
        export.join()

        assertTrue(export.isCancelled)
        assertEquals(0, output.openCount)
    }

    @Test
    fun closesTheOutputWhenWritingFails() = runBlocking {
        val output = RecordingOutput(failOnWrite = true)
        val exporter = LongTermCsvExporter { listOf(summary(LocalDate.of(2024, 3, 3))) }

        val failure = runCatching { exporter.export(output) }.exceptionOrNull()

        assertTrue(failure is IOException)
        assertTrue(output.isClosed)
    }

    @Test
    fun propagatesAFailureToOpenTheOutput() = runBlocking {
        val exporter = LongTermCsvExporter { listOf(summary(LocalDate.of(2024, 3, 4))) }

        val failure = runCatching {
            exporter.export { throw IOException("no output stream") }
        }.exceptionOrNull()

        assertTrue(failure is IOException)
    }

    private fun summary(date: LocalDate): DailyPostureSummary {
        val zoneId = ZoneOffset.UTC
        return DailyPostureSummary(
            dayStartMillis = date.atStartOfDay(zoneId).toInstant().toEpochMilli(),
            dayEndMillis = date.plusDays(1).atStartOfDay(zoneId).toInstant().toEpochMilli(),
            zoneId = zoneId.id,
            coverMillis = 1_000L,
            innerMillis = 3_000L,
            excludedMillis = 1_000L,
            openedCount = 2,
            closedCount = 2,
            evidenceGapCount = 0,
        )
    }

    private class RecordingOutput(
        private val failOnWrite: Boolean = false,
    ) : CsvExportOutput {
        var openCount: Int = 0
            private set
        var isClosed: Boolean = false
            private set

        private val writer = StringWriter()

        fun written(): String = writer.toString()

        override fun openTruncating(): Writer {
            openCount += 1
            return object : Writer() {
                override fun write(cbuf: CharArray, off: Int, len: Int) {
                    if (failOnWrite) throw IOException("write failed")
                    writer.write(cbuf, off, len)
                }

                override fun flush() = Unit

                override fun close() {
                    isClosed = true
                }
            }
        }
    }
}
