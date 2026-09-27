package com.nagopy.android.foldlytics.insight

import com.google.mlkit.genai.common.FeatureStatus
import com.google.mlkit.genai.prompt.Generation
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertTrue
import org.junit.Test

/** Uses the real SDK with the merged manifest; never generates text or downloads a model. */
class MlKitAvailabilityTest {
    @Test fun checksRealSdkAvailabilityWithoutUsageEvidence() = runBlocking {
        val client = Generation.getClient()
        try {
            val status = withTimeout(30_000L) { client.checkStatus() }
            assertTrue(status in setOf(
                FeatureStatus.AVAILABLE, FeatureStatus.UNAVAILABLE,
                FeatureStatus.DOWNLOADABLE, FeatureStatus.DOWNLOADING,
            ))
        } finally {
            client.close()
        }
    }
}
