package com.nagopy.android.foldlytics.insight

/** Both 30-calendar-day periods are end-exclusive and exclude the current local day. */
data class UsageInsightRange(
    val currentStartMillis: Long,
    val currentEndMillis: Long,
    val previousStartMillis: Long,
    val zoneId: String,
)

/** Only deterministic, measured facts belong here; a model must not calculate new metrics. */
data class UsageInsightFact(
    val id: String,
    val evidenceText: String,
    val displayText: String,
)

data class UsageInsightFacts(
    val range: UsageInsightRange,
    val languageTag: String,
    val facts: List<UsageInsightFact>,
    val limitations: List<UsageInsightFact>,
    val canGenerate: Boolean,
    val fingerprintMaterial: String,
) {
    val promptEvidence: String
        get() = (facts + limitations).joinToString("\n") { "${it.id}: ${it.evidenceText}" }
}
