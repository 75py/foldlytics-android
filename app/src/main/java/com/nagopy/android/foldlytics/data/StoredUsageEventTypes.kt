package com.nagopy.android.foldlytics.data

import android.app.usage.UsageEvents
import com.nagopy.android.foldlytics.model.UsageEventKind

internal object StoredUsageEventTypes {
    private enum class Group {
        ACTIVITY,
        SCREEN_STATE,
        KEYGUARD_STATE,
        POSTURE_STATE,
    }

    private data class Definition(
        val rawEventType: Int,
        val kind: UsageEventKind,
        val group: Group,
    )

    private val definitions = listOf(
        Definition(
            UsageEvents.Event.ACTIVITY_RESUMED,
            UsageEventKind.ACTIVITY_RESUMED,
            Group.ACTIVITY,
        ),
        Definition(
            UsageEvents.Event.ACTIVITY_PAUSED,
            UsageEventKind.ACTIVITY_PAUSED,
            Group.ACTIVITY,
        ),
        Definition(
            UsageEvents.Event.ACTIVITY_STOPPED,
            UsageEventKind.ACTIVITY_STOPPED,
            Group.ACTIVITY,
        ),
        Definition(
            UsageEvents.Event.CONFIGURATION_CHANGE,
            UsageEventKind.CONFIGURATION_CHANGED,
            Group.POSTURE_STATE,
        ),
        Definition(
            UsageEvents.Event.SCREEN_INTERACTIVE,
            UsageEventKind.SCREEN_INTERACTIVE,
            Group.SCREEN_STATE,
        ),
        Definition(
            UsageEvents.Event.SCREEN_NON_INTERACTIVE,
            UsageEventKind.SCREEN_NON_INTERACTIVE,
            Group.SCREEN_STATE,
        ),
        Definition(
            UsageEvents.Event.KEYGUARD_HIDDEN,
            UsageEventKind.KEYGUARD_HIDDEN,
            Group.KEYGUARD_STATE,
        ),
        Definition(
            UsageEvents.Event.KEYGUARD_SHOWN,
            UsageEventKind.KEYGUARD_SHOWN,
            Group.KEYGUARD_STATE,
        ),
        Definition(
            UsageEvents.Event.DEVICE_STARTUP,
            UsageEventKind.DEVICE_STARTUP,
            Group.POSTURE_STATE,
        ),
        Definition(
            UsageEvents.Event.DEVICE_SHUTDOWN,
            UsageEventKind.DEVICE_SHUTDOWN,
            Group.POSTURE_STATE,
        ),
    )
    private val definitionsByRawEventType = definitions.associateBy(Definition::rawEventType)

    val all: List<Int> = definitions.map(Definition::rawEventType)
    val activity: List<Int> = rawEventTypes(Group.ACTIVITY)
    private val screenState: List<Int> = rawEventTypes(Group.SCREEN_STATE)
    private val keyguardState: List<Int> = rawEventTypes(Group.KEYGUARD_STATE)
    private val postureState: List<Int> = rawEventTypes(Group.POSTURE_STATE)
    val deviceStateGroups: List<List<Int>> = listOf(
        screenState,
        keyguardState,
        postureState,
    )

    init {
        check(definitionsByRawEventType.size == definitions.size) {
            "Stored usage event types must be unique"
        }
    }

    fun contains(rawEventType: Int): Boolean = rawEventType in definitionsByRawEventType

    fun kindOrNull(rawEventType: Int): UsageEventKind? =
        definitionsByRawEventType[rawEventType]?.kind

    private fun rawEventTypes(group: Group): List<Int> = definitions
        .filter { it.group == group }
        .map(Definition::rawEventType)
}

internal fun Int.toUsageEventKind(): UsageEventKind =
    StoredUsageEventTypes.kindOrNull(this) ?: UsageEventKind.OTHER
