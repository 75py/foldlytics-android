package com.nagopy.android.foldlytics.insight

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class InsightCacheTest {
    @get:Rule val directory = TemporaryFolder()

    @Test fun cacheRequiresMatchingEvidenceAndRejectsDamagedOutput() {
        val cache = InsightCache(directory.root)
        val facts = UsageInsightFacts(
            UsageInsightRange(0, 100, "UTC"), "en",
            listOf(
                UsageInsightFact("display_share", "70", "Inner use was 70%."),
                UsageInsightFact("recorded_days", "20", "Inner use was recorded on 20 days."),
            ), emptyList(), true, "fixture",
        )
        val output = "[display_share] Inner use was 70%.\n[recorded_days] Inner use was recorded on 20 days."
        assertNull(cache.read("key", facts))
        cache.write("key", output)
        assertNotNull(InsightCache(directory.root).read("key", facts))
        assertNull(cache.read("changed-key", facts))
        cache.write("key", "[unknown] Fabricated output")
        assertNull(cache.read("key", facts))
    }
}
