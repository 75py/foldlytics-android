package com.nagopy.android.foldlytics.data

import android.annotation.SuppressLint
import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageEventsQuery
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.Process
import android.os.UserManager
import androidx.annotation.RequiresApi
import com.nagopy.android.foldlytics.model.DisplayConfiguration
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
            val events = queryEvents(beginMillis, endMillis)
                ?: return UsageReadResult.Unavailable(
                    UsageReadUnavailableReason.SYSTEM_UNAVAILABLE,
                )
            val event = UsageEvents.Event()
            val collector = UsageRecordCollector()
            while (events.hasNextEvent()) {
                events.getNextEvent(event)
                collector.addIfStored(
                    timestampMillis = event.timeStamp,
                    rawEventType = event.eventType,
                    packageName = event.packageName,
                    className = event.className,
                    configuration = event.configuration?.toDisplayConfiguration(),
                )
            }
            UsageReadResult.Success(collector.toList())
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

    private fun queryEvents(beginMillis: Long, endMillis: Long): UsageEvents? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
            queryEventsApi35(beginMillis, endMillis)
        } else {
            usageStatsManager.queryEvents(beginMillis, endMillis)
        }

    // Lint loses the EventType constants when the tested central whitelist becomes an IntArray.
    @SuppressLint("WrongConstant")
    @RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
    private fun queryEventsApi35(beginMillis: Long, endMillis: Long): UsageEvents? =
        usageStatsManager.queryEvents(
            UsageEventsQuery.Builder(beginMillis, endMillis)
                .setEventTypes(*StoredUsageEventTypes.all.toIntArray())
                .build(),
        )
}

internal class UsageRecordCollector {
    private val records = mutableListOf<UsageRecord>()
    private var lastStoredTimestamp: Long? = null
    private var sequenceAtTimestamp = 0

    fun addIfStored(
        timestampMillis: Long,
        rawEventType: Int,
        packageName: String? = null,
        className: String? = null,
        configuration: DisplayConfiguration? = null,
    ) {
        val kind = StoredUsageEventTypes.kindOrNull(rawEventType) ?: return
        sequenceAtTimestamp = if (lastStoredTimestamp == timestampMillis) {
            sequenceAtTimestamp + 1
        } else {
            0
        }
        lastStoredTimestamp = timestampMillis
        records += UsageRecord(
            timestampMillis = timestampMillis,
            kind = kind,
            packageName = packageName,
            className = className,
            configuration = configuration,
            rawEventType = rawEventType,
            sequenceAtTimestamp = sequenceAtTimestamp,
        )
    }

    fun toList(): List<UsageRecord> = records.toList()
}

fun Configuration.toDisplayConfiguration(): DisplayConfiguration =
    DisplayConfiguration(
        screenWidthDp = screenWidthDp,
        screenHeightDp = screenHeightDp,
        smallestScreenWidthDp = smallestScreenWidthDp,
        orientation = orientation,
        densityDpi = densityDpi,
    )
