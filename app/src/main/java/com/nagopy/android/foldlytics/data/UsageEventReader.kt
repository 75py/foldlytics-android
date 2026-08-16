package com.nagopy.android.foldlytics.data

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.res.Configuration
import android.os.Process
import android.os.UserManager
import com.nagopy.android.foldlytics.model.DisplayConfiguration
import com.nagopy.android.foldlytics.model.UsageEventKind
import com.nagopy.android.foldlytics.model.UsageRecord

enum class UsageReadUnavailableReason {
    PERMISSION_DENIED,
    USER_LOCKED,
    SYSTEM_UNAVAILABLE,
}

sealed interface UsageReadResult {
    data class Success(val records: List<UsageRecord>) : UsageReadResult

    data class Unavailable(val reason: UsageReadUnavailableReason) : UsageReadResult

    data class Failure(val error: Exception) : UsageReadResult
}

interface UsageEventSource {
    fun hasUsageAccess(): Boolean

    fun read(beginMillis: Long, endMillis: Long): UsageReadResult
}

class UsageEventReader(private val context: Context) : UsageEventSource {
    private val usageStatsManager =
        context.getSystemService(UsageStatsManager::class.java)

    override fun hasUsageAccess(): Boolean {
        val appOps = context.getSystemService(AppOpsManager::class.java)
        return runCatching {
            appOps.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName,
            ) == AppOpsManager.MODE_ALLOWED
        }.getOrDefault(false)
    }

    override fun read(beginMillis: Long, endMillis: Long): UsageReadResult {
        if (!hasUsageAccess()) {
            return UsageReadResult.Unavailable(UsageReadUnavailableReason.PERMISSION_DENIED)
        }
        if (beginMillis >= endMillis) return UsageReadResult.Success(emptyList())
        val userManager = context.getSystemService(UserManager::class.java)
        if (!userManager.isUserUnlocked) {
            return UsageReadResult.Unavailable(UsageReadUnavailableReason.USER_LOCKED)
        }

        return try {
            val events = usageStatsManager.queryEvents(beginMillis, endMillis)
                ?: return UsageReadResult.Unavailable(
                    UsageReadUnavailableReason.SYSTEM_UNAVAILABLE,
                )
            val event = UsageEvents.Event()
            var lastTimestamp: Long? = null
            var sequenceAtTimestamp = 0
            val records = buildList {
                while (events.hasNextEvent()) {
                    events.getNextEvent(event)
                    sequenceAtTimestamp = if (lastTimestamp == event.timeStamp) {
                        sequenceAtTimestamp + 1
                    } else {
                        0
                    }
                    lastTimestamp = event.timeStamp
                    add(
                        UsageRecord(
                            timestampMillis = event.timeStamp,
                            kind = event.eventType.toUsageEventKind(),
                            packageName = event.packageName,
                            className = event.className,
                            configuration = event.configuration?.toDisplayConfiguration(),
                            rawEventType = event.eventType,
                            sequenceAtTimestamp = sequenceAtTimestamp,
                        ),
                    )
                }
            }
            UsageReadResult.Success(records)
        } catch (_: SecurityException) {
            UsageReadResult.Unavailable(UsageReadUnavailableReason.PERMISSION_DENIED)
        } catch (error: Exception) {
            UsageReadResult.Failure(error)
        }
    }

    fun packageLabel(packageName: String): String = runCatching {
        @Suppress("DEPRECATION")
        val info = context.packageManager.getApplicationInfo(packageName, 0)
        context.packageManager.getApplicationLabel(info).toString()
    }.getOrDefault(packageName)

    fun isLauncherApp(packageName: String): Boolean =
        context.packageManager.getLaunchIntentForPackage(packageName) != null
}

internal fun Int.toUsageEventKind(): UsageEventKind = when (this) {
    UsageEvents.Event.ACTIVITY_RESUMED -> UsageEventKind.ACTIVITY_RESUMED
    UsageEvents.Event.ACTIVITY_PAUSED -> UsageEventKind.ACTIVITY_PAUSED
    UsageEvents.Event.ACTIVITY_STOPPED -> UsageEventKind.ACTIVITY_STOPPED
    UsageEvents.Event.CONFIGURATION_CHANGE -> UsageEventKind.CONFIGURATION_CHANGED
    UsageEvents.Event.SCREEN_INTERACTIVE -> UsageEventKind.SCREEN_INTERACTIVE
    UsageEvents.Event.SCREEN_NON_INTERACTIVE -> UsageEventKind.SCREEN_NON_INTERACTIVE
    UsageEvents.Event.KEYGUARD_HIDDEN -> UsageEventKind.KEYGUARD_HIDDEN
    UsageEvents.Event.KEYGUARD_SHOWN -> UsageEventKind.KEYGUARD_SHOWN
    UsageEvents.Event.DEVICE_STARTUP -> UsageEventKind.DEVICE_STARTUP
    UsageEvents.Event.DEVICE_SHUTDOWN -> UsageEventKind.DEVICE_SHUTDOWN
    else -> UsageEventKind.OTHER
}

fun Configuration.toDisplayConfiguration(): DisplayConfiguration =
    DisplayConfiguration(
        screenWidthDp = screenWidthDp,
        screenHeightDp = screenHeightDp,
        smallestScreenWidthDp = smallestScreenWidthDp,
        orientation = orientation,
        densityDpi = densityDpi,
    )
