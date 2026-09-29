package com.nagopy.android.foldlytics.insight

import android.app.Application
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.nagopy.android.foldlytics.FoldlyticsApplication
import java.time.ZoneId
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

enum class InsightStatus { HIDDEN, CHECKING, INSUFFICIENT_DATA, PREPARING, GENERATING, READY, UNAVAILABLE }

data class UsageInsightUiState(
    val status: InsightStatus = InsightStatus.HIDDEN,
    val paragraphs: List<String> = emptyList(),
    val facts: UsageInsightFacts? = null,
)

/** Kept separate from period selection so the 30-day card cannot relabel other results. */
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
    private var attemptedKey: String? = null
    private var displayedKey: String? = null
    private var nativeUnavailable = false

    fun setHomeVisible(visible: Boolean) {
        if (!visible) {
            job?.cancel()
            job = null
            return
        }
        if (job?.isActive == true) return
        if (nativeUnavailable) return
        if (Build.SUPPORTED_ABIS.none { it == "arm64-v8a" || it == "x86_64" }) return
        if (!source.isSupported()) return
        job = viewModelScope.launch {
            // Poll only while home is visible. This also notices midnight, locale, and calibration
            // changes without starting inference for every foreground checkpoint or sync event.
            while (true) {
                updateOnce()
                if (nativeUnavailable) break
                delay(60_000L)
            }
        }
    }

    internal suspend fun updateOnce() {
        if (nativeUnavailable) return
        try {
            if (_state.value.status == InsightStatus.HIDDEN) {
                _state.value = UsageInsightUiState(InsightStatus.CHECKING)
            }
            val evidence = withContext(Dispatchers.IO) { source.load() }
            val facts = evidence.facts
            if (!facts.canGenerate) {
                displayedKey = null
                _state.value = UsageInsightUiState(InsightStatus.INSUFFICIENT_DATA, facts = facts)
                return
            }
            val key = InsightTextProtocol.key(evidence)
            if (displayedKey == key) return
            val saved = withContext(Dispatchers.IO) { source.readCache(key, facts) }
            if (saved != null) {
                displayedKey = key
                _state.value = UsageInsightUiState(InsightStatus.READY, saved.map { it.text }, facts)
                return
            }
            // Never retry the same failed input every minute; a changed input/process may retry.
            if (attemptedKey == key) return
            displayedKey = null
            attemptedKey = key
            _state.value = UsageInsightUiState(InsightStatus.PREPARING, facts = facts)
            val output = withTimeout(120_000L) {
                val model = withContext(Dispatchers.IO) { source.prepareModel() }
                _state.value = UsageInsightUiState(InsightStatus.GENERATING, facts = facts)
                source.generate(
                    modelFile = model,
                    systemPrompt = InsightTextProtocol.systemPrompt(facts.languageTag),
                    userPrompt = InsightTextProtocol.userPrompt(facts),
                    maxOutputTokens = 256,
                )
            }
            val sentences = InsightTextProtocol.parse(output, facts) ?: error("Invalid generated explanation")
            withContext(Dispatchers.IO) { source.writeCache(key, output) }
            displayedKey = key
            _state.value = UsageInsightUiState(InsightStatus.READY, sentences.map { it.text }, facts)
        } catch (error: kotlinx.coroutines.TimeoutCancellationException) {
            _state.value = _state.value.copy(status = InsightStatus.UNAVAILABLE, paragraphs = emptyList())
        } catch (error: CancellationException) {
            // A navigation cancellation is not a failed generation and can resume next time.
            if (_state.value.status == InsightStatus.GENERATING || _state.value.status == InsightStatus.PREPARING) {
                attemptedKey = null
            }
            throw error
        } catch (_: Exception) {
            displayedKey = null
            _state.value = _state.value.copy(status = InsightStatus.UNAVAILABLE, paragraphs = emptyList())
        } catch (_: UnsatisfiedLinkError) {
            nativeUnavailable = true
            _state.value = UsageInsightUiState(InsightStatus.HIDDEN)
        }
    }
}

internal interface InsightDataSource {
    fun isSupported(): Boolean = true
    suspend fun load(): InsightEvidence
    fun readCache(key: String, facts: UsageInsightFacts): List<InsightSentence>?
    suspend fun prepareModel(): File
    suspend fun generate(modelFile: File, systemPrompt: String, userPrompt: String, maxOutputTokens: Int): String
    fun writeCache(key: String, output: String)
}

private class ProductionInsightDataSource(private val app: FoldlyticsApplication) : InsightDataSource {
    private val repository = UsageInsightRepository(app)
    private val modelStore = InsightModelStore(app)
    private val cache = InsightCache(app)

    override fun isSupported(): Boolean {
        val manager = app.getSystemService(android.app.ActivityManager::class.java)
        val memory = android.app.ActivityManager.MemoryInfo()
        manager.getMemoryInfo(memory)
        // Avoid attempting a ~640 MB model on low-memory devices. This is only
        // an admission threshold; release devices still need performance testing.
        return !manager.isLowRamDevice && memory.totalMem >= 4L * 1024 * 1024 * 1024
    }

    override suspend fun load(): InsightEvidence = repository.load(
        System.currentTimeMillis(), ZoneId.systemDefault(),
        app.resources.configuration.locales[0].toLanguageTag(),
    )
    override fun readCache(key: String, facts: UsageInsightFacts) = cache.read(key, facts)
    override suspend fun prepareModel() = modelStore.prepare()
    override suspend fun generate(modelFile: File, systemPrompt: String, userPrompt: String, maxOutputTokens: Int) =
        LlamaInsightEngine().generate(modelFile, systemPrompt, userPrompt, maxOutputTokens)
    override fun writeCache(key: String, output: String) = cache.write(key, output)
}
