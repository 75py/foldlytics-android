package com.nagopy.android.foldlytics

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Sharing prepares one report at a time and keeps it until a share sheet shows it. */
class ReportPreparationTest {
    @Test
    fun repeatedShareRequestsWhilePreparingStartOnePreparation() = runBlocking {
        val preparation = ReportPreparation(this)
        val gate = CompletableDeferred<Unit>()
        var started = 0

        val first = preparation.start {
            started += 1
            gate.await()
            "report"
        }
        val second = preparation.start {
            started += 1
            "other"
        }
        yield()

        assertTrue(first)
        assertFalse(second)
        assertTrue(preparation.preparing.value)
        assertNull(preparation.prepared.value)
        gate.complete(Unit)
        yield()
        assertEquals(1, started)
        assertFalse(preparation.preparing.value)
        assertEquals("report", preparation.prepared.value)
    }

    @Test
    fun preparedReportStaysForARecreatedActivityUntilConsumed() = runBlocking {
        val preparation = ReportPreparation(this)
        preparation.start { "report" }
        yield()

        // A recreated activity still finds the report, and a new request waits for it.
        assertEquals("report", preparation.prepared.value)
        assertFalse(preparation.start { "other" })

        preparation.consume("report")
        assertNull(preparation.prepared.value)
        assertTrue(preparation.start { "next" })
    }

    @Test
    fun failedPreparationSharesNothingAndAllowsARetry() = runBlocking {
        val preparation = ReportPreparation(this)
        preparation.start { null }
        yield()

        assertFalse(preparation.preparing.value)
        assertNull(preparation.prepared.value)
        assertTrue(preparation.start { "retried" })
        yield()
        assertEquals("retried", preparation.prepared.value)
    }
}
