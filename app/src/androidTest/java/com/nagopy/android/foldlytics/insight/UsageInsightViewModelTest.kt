package com.nagopy.android.foldlytics.insight

import android.app.Application
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UsageInsightViewModelTest {
    @Test fun unavailableModelAndAvailabilityErrorsStayHiddenWithoutReadingUsage() = runBlocking {
        val source = FakeSource().apply { modelIdentity = null }
        val model = UsageInsightViewModel(Application(), source)
        assertFalse(model.updateOnce())
        assertEquals(InsightStatus.HIDDEN, model.state.value.status)
        assertEquals(0, source.loads)
        source.failAvailability = true
        assertFalse(model.updateOnce())
        assertEquals(InsightStatus.HIDDEN, model.state.value.status)
        assertEquals(0, source.loads)
        assertEquals(0, source.generations)
    }

    @Test fun restoresReadyFromCacheAfterTemporaryReadFailure() = runBlocking {
        val source = FakeSource()
        val model = UsageInsightViewModel(Application(), source)
        assertTrue(model.updateOnce())
        assertEquals(InsightStatus.READY, model.state.value.status)
        source.failRead = true
        assertFalse(model.updateOnce())
        assertEquals(InsightStatus.UNAVAILABLE, model.state.value.status)
        source.failRead = false
        assertTrue(model.updateOnce())
        assertEquals(InsightStatus.READY, model.state.value.status)
        assertEquals(1, source.generations)
    }

    @Test fun modelChangesInvalidateCachedOutput() = runBlocking {
        val source = FakeSource()
        UsageInsightViewModel(Application(), source).updateOnce()
        val restored = UsageInsightViewModel(Application(), source)
        restored.updateOnce()
        assertEquals(1, source.generations)
        source.modelIdentity = "updated-model"
        restored.updateOnce()
        assertEquals(2, source.generations)
    }

    @Test fun insufficientEvidenceDoesNotGenerate() = runBlocking {
        val source = FakeSource().apply { canGenerate = false }
        val model = UsageInsightViewModel(Application(), source)
        assertTrue(model.updateOnce())
        assertEquals(InsightStatus.INSUFFICIENT_DATA, model.state.value.status)
        assertEquals(0, source.generations)
    }

    @Test fun cancelledGenerationResumesAndInvalidOutputEndsPollingWithoutRetrying() = runBlocking {
        val source = FakeSource().apply { cancel = true }
        val model = UsageInsightViewModel(Application(), source)
        try { model.updateOnce() } catch (_: CancellationException) { }
        source.cancel = false
        model.updateOnce()
        assertEquals(InsightStatus.READY, model.state.value.status)
        source.fingerprint = "changed"
        source.invalid = true
        assertFalse(model.updateOnce())
        assertEquals(InsightStatus.UNAVAILABLE, model.state.value.status)
        val attempts = source.generations
        assertFalse(model.updateOnce())
        assertEquals(attempts, source.generations)
        assertTrue(model.state.value.paragraphs.isEmpty())
    }

    @Test fun leavingHomeCancelsAndQuickReturnResumesWithoutConcurrentGeneration() = runBlocking {
        val source = FakeSource().apply { suspendGeneration = true }
        val model = UsageInsightViewModel(Application(), source)
        withContext(Dispatchers.Main) { model.setHomeVisible(true) }
        withTimeout(5_000L) { source.started.await() }
        withContext(Dispatchers.Main) {
            model.setHomeVisible(false)
            source.suspendGeneration = false
            model.setHomeVisible(true)
        }
        withTimeout(5_000L) { model.state.first { it.status == InsightStatus.READY } }
        withContext(Dispatchers.Main) {
            assertEquals(InsightStatus.READY, model.state.value.status)
            model.setHomeVisible(false)
        }
        assertEquals(2, source.generations)
        assertEquals(1, source.maximumConcurrentGenerations)
    }

    @Test fun repeatedNavigationWaitsForTheOldestGenerationCleanup() = runBlocking {
        val source = FakeSource().apply { suspendGeneration = true; suspendCleanup = true }
        val model = UsageInsightViewModel(Application(), source)
        withContext(Dispatchers.Main) { model.setHomeVisible(true) }
        withTimeout(5_000L) { source.started.await() }
        withContext(Dispatchers.Main) { model.setHomeVisible(false); model.setHomeVisible(true) }
        withTimeout(5_000L) { source.cleanupStarted.await() }
        withContext(Dispatchers.Main) {
            model.setHomeVisible(false)
            model.setHomeVisible(true)
            source.suspendGeneration = false
        }
        // Drain queued Main work before allowing the oldest request to finish cleanup.
        withContext(Dispatchers.Main) { assertEquals(1, source.generations) }
        source.finishCleanup.complete(Unit)
        withTimeout(5_000L) { model.state.first { it.status == InsightStatus.READY } }
        withContext(Dispatchers.Main) { model.setHomeVisible(false) }
        assertEquals(2, source.generations)
        assertEquals(1, source.maximumConcurrentGenerations)
    }

    private class FakeSource : InsightDataSource {
        var loads = 0
        var generations = 0
        var maximumConcurrentGenerations = 0
        var activeGenerations = 0
        var failRead = false
        var failAvailability = false
        var canGenerate = true
        var cancel = false
        var invalid = false
        var suspendGeneration = false
        var suspendCleanup = false
        var modelIdentity: String? = "fixture-model"
        var fingerprint = "fixture"
        val started = CompletableDeferred<Unit>()
        val completed = CompletableDeferred<Unit>()
        val cleanupStarted = CompletableDeferred<Unit>()
        val finishCleanup = CompletableDeferred<Unit>()
        private var cachedKey: String? = null
        private var cachedOutput: String? = null

        override suspend fun availableModelIdentity(): String? {
            if (failAvailability) throw SecurityException("synthetic unavailable service")
            return modelIdentity
        }

        override suspend fun load(): InsightEvidence {
            loads++
            if (failRead) error("temporary test failure")
            return InsightEvidence(
                UsageInsightFacts(
                    UsageInsightRange(0, 100, "UTC"), "en",
                    listOf(
                        UsageInsightFact("display_share", "70", "Inner use was 70%."),
                        UsageInsightFact("recorded_days", "20", "Inner use was recorded on 20 days."),
                    ), emptyList(), canGenerate, fingerprint,
                ), "calibration",
            )
        }

        override fun readCache(key: String, facts: UsageInsightFacts) =
            if (key == cachedKey) cachedOutput?.let { InsightTextProtocol.parse(it, facts) } else null

        override suspend fun generate(systemPrompt: String, userPrompt: String, maxOutputTokens: Int): String {
            generations++
            activeGenerations++
            maximumConcurrentGenerations = maxOf(maximumConcurrentGenerations, activeGenerations)
            try {
                started.complete(Unit)
                if (suspendGeneration) awaitCancellation()
                if (cancel) throw CancellationException("test")
                if (invalid) return "Fabricated text"
                return "[display_share] Inner use was 70%.\n[recorded_days] Inner use was recorded on 20 days."
            } finally {
                if (suspendCleanup) withContext(NonCancellable) {
                    cleanupStarted.complete(Unit)
                    finishCleanup.await()
                }
                activeGenerations--
            }
        }

        override fun writeCache(key: String, output: String) {
            cachedKey = key
            cachedOutput = output
            completed.complete(Unit)
        }
    }
}
