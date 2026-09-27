package com.nagopy.android.foldlytics.insight

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.nagopy.android.foldlytics.FoldlyticsApplication
import java.time.ZoneId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

enum class InsightStatus { HIDDEN, CHECKING, INSUFFICIENT_DATA, GENERATING, READY, UNAVAILABLE }

data class UsageInsightUiState(
    val status: InsightStatus = InsightStatus.HIDDEN,
    val paragraphs: List<String> = emptyList(),
    val facts: UsageInsightFacts? = null,
)

/** Independent of period selection; work runs only while Home is resumed. */
class UsageInsightViewModel internal constructor(
    application: Application,
    private val source: InsightDataSource,
) : AndroidViewModel(application) {
    constructor(application: Application) : this(
        application, ProductionInsightDataSource(application as FoldlyticsApplication),
    )

    private val _state = MutableStateFlow(UsageInsightUiState())
    val state = _state.asStateFlow()
    private var job: Job? = null
    private val updateMutex = Mutex()
    private var homeVisible = false
    private var attemptedKey: String? = null
    private var displayedKey: String? = null

    fun setHomeVisible(visible: Boolean) {
        if (homeVisible == visible) return
        homeVisible = visible
        if (!visible) {
            job?.cancel()
            return
        }
        job = viewModelScope.launch {
            attemptedKey = null
            while (updateOnce()) {
                // Notice completed sync, midnight, calibration, or locale changes while visible.
                delay(60_000L)
            }
        }
    }

    /** False ends this foreground session's polling after any capability or generation failure. */
    internal suspend fun updateOnce(): Boolean = updateMutex.withLock {
        // Retain ownership through cancellation cleanup, even across repeated quick navigation.
        updateOnceLocked()
    }

    private suspend fun updateOnceLocked(): Boolean {
        var checkingAvailability = true
        try {
            val modelIdentity = withTimeout(15_000L) { source.availableModelIdentity() }
            if (modelIdentity == null) {
                displayedKey = null
                _state.value = UsageInsightUiState()
                return false
            }
            checkingAvailability = false
            if (_state.value.status == InsightStatus.HIDDEN) {
                _state.value = UsageInsightUiState(InsightStatus.CHECKING)
            }
            val evidence = withContext(Dispatchers.IO) { source.load() }
            val facts = evidence.facts
            if (!facts.canGenerate) {
                displayedKey = null
                _state.value = UsageInsightUiState(InsightStatus.INSUFFICIENT_DATA, facts = facts)
                return true
            }
            val key = InsightTextProtocol.key(evidence, modelIdentity)
            if (displayedKey == key) return true
            val saved = withContext(Dispatchers.IO) { source.readCache(key, facts) }
            if (saved != null) {
                displayedKey = key
                _state.value = UsageInsightUiState(InsightStatus.READY, saved.map { it.text }, facts)
                return true
            }
            if (attemptedKey == key) return false
            displayedKey = null
            attemptedKey = key
            _state.value = UsageInsightUiState(InsightStatus.GENERATING, facts = facts)
            val output = withTimeout(120_000L) {
                source.generate(
                    systemPrompt = InsightTextProtocol.systemPrompt(facts.languageTag),
                    userPrompt = InsightTextProtocol.userPrompt(facts),
                    maxOutputTokens = 256,
                )
            }
            val sentences = InsightTextProtocol.parse(output, facts) ?: error("Invalid generated explanation")
            withContext(Dispatchers.IO) { source.writeCache(key, output) }
            displayedKey = key
            _state.value = UsageInsightUiState(InsightStatus.READY, sentences.map { it.text }, facts)
            return true
        } catch (_: TimeoutCancellationException) {
            fail(checkingAvailability)
        } catch (error: CancellationException) {
            // Navigation cancellation can resume on the next Home visit.
            attemptedKey = null
            throw error
        } catch (_: InsightFeatureUnavailable) {
            fail(hidden = true)
        } catch (_: Exception) {
            fail(checkingAvailability)
        }
        return false
    }

    private fun fail(hidden: Boolean) {
        displayedKey = null
        _state.value = if (hidden) UsageInsightUiState() else {
            _state.value.copy(status = InsightStatus.UNAVAILABLE, paragraphs = emptyList())
        }
    }

    override fun onCleared() {
        job?.cancel()
        source.close()
    }
}

internal interface InsightDataSource {
    suspend fun availableModelIdentity(): String?
    suspend fun load(): InsightEvidence
    fun readCache(key: String, facts: UsageInsightFacts): List<InsightSentence>?
    suspend fun generate(systemPrompt: String, userPrompt: String, maxOutputTokens: Int): String
    fun writeCache(key: String, output: String)
    fun close() = Unit
}

private class ProductionInsightDataSource(private val app: FoldlyticsApplication) : InsightDataSource {
    private val repository = UsageInsightRepository(app)
    private val engine = MlKitInsightEngine()
    private val cache = InsightCache(app)

    override suspend fun availableModelIdentity() = engine.availableModelIdentity()
    override suspend fun load(): InsightEvidence = repository.load(
        System.currentTimeMillis(), ZoneId.systemDefault(),
        app.resources.configuration.locales[0].toLanguageTag(),
    )
    override fun readCache(key: String, facts: UsageInsightFacts) = cache.read(key, facts)
    override suspend fun generate(systemPrompt: String, userPrompt: String, maxOutputTokens: Int) =
        engine.generate(systemPrompt, userPrompt, maxOutputTokens)
    override fun writeCache(key: String, output: String) = cache.write(key, output)
    override fun close() = engine.close()
}
