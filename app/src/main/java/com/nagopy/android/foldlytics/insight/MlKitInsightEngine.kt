package com.nagopy.android.foldlytics.insight

import com.google.mlkit.genai.common.FeatureStatus
import com.google.mlkit.genai.prompt.Generation
import com.google.mlkit.genai.prompt.TextPart
import com.google.mlkit.genai.prompt.generateContentRequest

/** Uses only the model already available through AICore; never requests a download. */
internal class MlKitInsightEngine {
    private val clientDelegate = lazy { Generation.getClient() }
    private val client by clientDelegate

    suspend fun availableModelIdentity(): String? =
        if (client.checkStatus() == FeatureStatus.AVAILABLE) {
            "mlkit-prompt-beta4:${client.getBaseModelName()}"
        } else {
            null
        }

    suspend fun generate(systemPrompt: String, userPrompt: String, maxOutputTokens: Int): String {
        if (client.checkStatus() != FeatureStatus.AVAILABLE) throw InsightFeatureUnavailable()
        // A plain text instruction works across Nano versions without optional system-prompt support.
        val response = client.generateContent(
            generateContentRequest(TextPart("$systemPrompt\n\n$userPrompt")) {
                temperature = 0.2f
                candidateCount = 1
                this.maxOutputTokens = maxOutputTokens
            },
        )
        return response.candidates.firstOrNull()?.text.orEmpty()
    }

    fun close() {
        if (clientDelegate.isInitialized()) runCatching { client.close() }
    }
}

internal class InsightFeatureUnavailable : Exception()
