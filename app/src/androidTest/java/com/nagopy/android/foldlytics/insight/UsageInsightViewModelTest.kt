package com.nagopy.android.foldlytics.insight

import android.app.Application
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UsageInsightViewModelTest {
    @Test fun restoresReadyAfterTemporaryReadFailure() = runBlocking {
        val source = FakeSource()
        val model = UsageInsightViewModel(Application(), source)
        model.updateOnce()
        assertEquals(InsightStatus.READY, model.state.value.status)
        source.failRead = true
        model.updateOnce()
        assertEquals(InsightStatus.UNAVAILABLE, model.state.value.status)
        source.failRead = false
        model.updateOnce()
        assertEquals(InsightStatus.READY, model.state.value.status)
        assertEquals(1, source.generations)
    }

    @Test fun unsupportedNativeStaysHiddenWithoutPollingAgain() = runBlocking {
        val source = FakeSource().apply { unsupported = true }
        val model = UsageInsightViewModel(Application(), source)
        model.updateOnce()
        assertEquals(InsightStatus.HIDDEN, model.state.value.status)
        model.updateOnce()
        assertEquals(InsightStatus.HIDDEN, model.state.value.status)
        assertEquals(1, source.loads)
    }

    @Test fun cancelledGenerationCanResumeAndInvalidOutputDoesNotRetryContinuously() = runBlocking {
        val source = FakeSource().apply { cancel = true }
        val model = UsageInsightViewModel(Application(), source)
        try { model.updateOnce() } catch (_: CancellationException) { }
        source.cancel = false
        model.updateOnce()
        assertEquals(InsightStatus.READY, model.state.value.status)
        source.fingerprint = "changed"
        source.invalid = true
        model.updateOnce()
        assertEquals(InsightStatus.UNAVAILABLE, model.state.value.status)
        val attempts = source.generations
        model.updateOnce()
        assertEquals(attempts, source.generations)
        assertTrue(model.state.value.paragraphs.isEmpty())
    }

    private class FakeSource : InsightDataSource {
        var loads = 0
        var generations = 0
        var failRead = false
        var unsupported = false
        var cancel = false
        var invalid = false
        var fingerprint = "fixture"
        private var cachedKey: String? = null
        private var cachedOutput: String? = null
        override suspend fun load(): InsightEvidence {
            loads++
            if (failRead) error("temporary test failure")
            return InsightEvidence(
                UsageInsightFacts(UsageInsightRange(0, 100, -100, "UTC"), "en",
                    listOf(
                        UsageInsightFact("display_share", "70", "Inner use was 70%."),
                        UsageInsightFact("recorded_days", "20", "Inner use was recorded on 20 days."),
                    ), emptyList(), true, fingerprint), "calibration",
            )
        }
        override fun readCache(key: String, facts: UsageInsightFacts) =
            if (key == cachedKey) cachedOutput?.let { InsightTextProtocol.parse(it, facts) } else null
        override suspend fun prepareModel() = File("synthetic-model")
        override suspend fun generate(modelFile: File, systemPrompt: String, userPrompt: String, maxOutputTokens: Int): String {
            generations++
            if (unsupported) throw UnsatisfiedLinkError("test")
            if (cancel) throw CancellationException("test")
            if (invalid) return "Fabricated text"
            return "[display_share] Inner use was 70%.\n[recorded_days] Inner use was recorded on 20 days."
        }
        override fun writeCache(key: String, output: String) {
            cachedKey = key
            cachedOutput = output
        }
    }
}
