package com.nagopy.android.foldlytics.data

import android.app.usage.UsageEvents
import com.nagopy.android.foldlytics.model.UsageEventKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UsageEventReaderTest {
    @Test
    fun storedEventDefinitionContainsExactlyTheAnalyzedEventTypes() {
        val expected = listOf(
            UsageEvents.Event.ACTIVITY_RESUMED to UsageEventKind.ACTIVITY_RESUMED,
            UsageEvents.Event.ACTIVITY_PAUSED to UsageEventKind.ACTIVITY_PAUSED,
            UsageEvents.Event.ACTIVITY_STOPPED to UsageEventKind.ACTIVITY_STOPPED,
            UsageEvents.Event.CONFIGURATION_CHANGE to UsageEventKind.CONFIGURATION_CHANGED,
            UsageEvents.Event.SCREEN_INTERACTIVE to UsageEventKind.SCREEN_INTERACTIVE,
            UsageEvents.Event.SCREEN_NON_INTERACTIVE to UsageEventKind.SCREEN_NON_INTERACTIVE,
            UsageEvents.Event.KEYGUARD_HIDDEN to UsageEventKind.KEYGUARD_HIDDEN,
            UsageEvents.Event.KEYGUARD_SHOWN to UsageEventKind.KEYGUARD_SHOWN,
            UsageEvents.Event.DEVICE_STARTUP to UsageEventKind.DEVICE_STARTUP,
            UsageEvents.Event.DEVICE_SHUTDOWN to UsageEventKind.DEVICE_SHUTDOWN,
        )

        assertEquals(expected.map { it.first }, StoredUsageEventTypes.all)
        assertEquals(
            expected.map { it.second },
            StoredUsageEventTypes.all.map(Int::toUsageEventKind),
        )
        assertTrue(StoredUsageEventTypes.all.all { StoredUsageEventTypes.contains(it) })

        val collector = UsageRecordCollector()
        expected.forEach { (rawEventType, _) ->
            collector.addIfStored(
                timestampMillis = 1_000L,
                rawEventType = rawEventType,
            )
        }
        val records = collector.toList()
        assertEquals(expected.map { it.first }, records.map { it.rawEventType })
        assertEquals(expected.map { it.second }, records.map { it.kind })
        assertEquals(expected.indices.toList(), records.map { it.sequenceAtTimestamp })
    }

    @Test
    fun filtersOutEventsThatAreNotAnalyzed() {
        val excludedEventTypes = listOf(
            UsageEvents.Event.NONE,
            UsageEvents.Event.USER_INTERACTION,
            UsageEvents.Event.FOREGROUND_SERVICE_START,
            UsageEvents.Event.FOREGROUND_SERVICE_STOP,
        )
        val collector = UsageRecordCollector()

        excludedEventTypes.forEach { rawEventType ->
            collector.addIfStored(
                timestampMillis = 1_000L,
                rawEventType = rawEventType,
            )
        }

        assertTrue(collector.toList().isEmpty())
        excludedEventTypes.forEach { rawEventType ->
            assertFalse(StoredUsageEventTypes.contains(rawEventType))
            assertEquals(UsageEventKind.OTHER, rawEventType.toUsageEventKind())
        }
    }

    @Test
    fun sequencesOnlyStoredEventsAtTheSameTimestamp() {
        val collector = UsageRecordCollector()

        collector.addIfStored(
            timestampMillis = 1_000L,
            rawEventType = UsageEvents.Event.ACTIVITY_RESUMED,
        )
        collector.addIfStored(
            timestampMillis = 1_000L,
            rawEventType = UsageEvents.Event.USER_INTERACTION,
        )
        collector.addIfStored(
            timestampMillis = 1_000L,
            rawEventType = UsageEvents.Event.ACTIVITY_PAUSED,
        )
        collector.addIfStored(
            timestampMillis = 2_000L,
            rawEventType = UsageEvents.Event.ACTIVITY_STOPPED,
        )

        val records = collector.toList()
        assertEquals(
            listOf(
                UsageEvents.Event.ACTIVITY_RESUMED,
                UsageEvents.Event.ACTIVITY_PAUSED,
                UsageEvents.Event.ACTIVITY_STOPPED,
            ),
            records.map { it.rawEventType },
        )
        assertEquals(listOf(0, 1, 0), records.map { it.sequenceAtTimestamp })
    }
}
