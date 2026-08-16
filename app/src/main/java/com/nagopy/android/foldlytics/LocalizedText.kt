package com.nagopy.android.foldlytics

import android.content.res.Resources
import com.nagopy.android.foldlytics.model.AnalysisPeriod
import com.nagopy.android.foldlytics.model.DisplayConfiguration
import com.nagopy.android.foldlytics.model.DisplayPosture
import com.nagopy.android.foldlytics.model.PostureCheckpointSource
import com.nagopy.android.foldlytics.model.PostureEventSource
import com.nagopy.android.foldlytics.model.UnknownPostureReason
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

internal val Resources.primaryLocale: Locale
    get() = configuration.locales[0]

internal val DisplayPosture.labelRes: Int
    get() = when (this) {
        DisplayPosture.COVER -> R.string.posture_cover
        DisplayPosture.INNER -> R.string.posture_inner
        DisplayPosture.UNKNOWN -> R.string.posture_unknown
    }

internal val UnknownPostureReason.labelRes: Int
    get() = when (this) {
        UnknownPostureReason.NO_BASELINE -> R.string.unknown_reason_no_baseline
        UnknownPostureReason.AFTER_DEVICE_RESTART -> R.string.unknown_reason_after_restart
        UnknownPostureReason.COLLECTION_INTERRUPTION -> R.string.unknown_reason_collection_interruption
        UnknownPostureReason.CONFIGURATION_UNAVAILABLE -> R.string.unknown_reason_configuration_unavailable
        UnknownPostureReason.CLASSIFICATION_UNAVAILABLE -> R.string.unknown_reason_classification_unavailable
    }

internal val PostureCheckpointSource.labelRes: Int
    get() = when (this) {
        PostureCheckpointSource.APP_LAUNCH -> R.string.checkpoint_source_app_launch
        PostureCheckpointSource.APP_FOREGROUND -> R.string.checkpoint_source_app_foreground
        PostureCheckpointSource.APP_BACKGROUND -> R.string.checkpoint_source_app_background
        PostureCheckpointSource.CALIBRATION_COVER -> R.string.checkpoint_source_calibration_cover
        PostureCheckpointSource.CALIBRATION_INNER -> R.string.checkpoint_source_calibration_inner
        PostureCheckpointSource.MANUAL_REFRESH -> R.string.checkpoint_source_manual_refresh
        PostureCheckpointSource.MEASUREMENT_START -> R.string.checkpoint_source_measurement_start
    }

internal val PostureEventSource.labelRes: Int
    get() = when (this) {
        PostureEventSource.CONFIGURATION_CHANGE -> R.string.event_source_configuration_change
        PostureEventSource.CHECKPOINT -> R.string.event_source_checkpoint
        PostureEventSource.COLLECTION_INTERRUPTION -> R.string.event_source_collection_interruption
        PostureEventSource.DEVICE_STARTUP -> R.string.event_source_device_startup
        PostureEventSource.DEVICE_SHUTDOWN -> R.string.event_source_device_shutdown
    }

internal val AnalysisPeriod.labelRes: Int
    get() = when (this) {
        AnalysisPeriod.HOURS_1 -> R.string.period_1_hour
        AnalysisPeriod.HOURS_6 -> R.string.period_6_hours
        AnalysisPeriod.HOURS_24 -> R.string.period_24_hours
        AnalysisPeriod.DAYS_7 -> R.string.period_7_days
        AnalysisPeriod.DAYS_30 -> R.string.period_30_days
        AnalysisPeriod.DAYS_90 -> R.string.period_90_days
        AnalysisPeriod.DAYS_365 -> R.string.period_1_year
        AnalysisPeriod.CUSTOM -> R.string.period_custom
    }

internal fun DisplayConfiguration.toDisplayText(resources: Resources): String {
    val orientationText = when (orientation) {
        1 -> resources.getString(R.string.orientation_portrait)
        2 -> resources.getString(R.string.orientation_landscape)
        else -> resources.getString(R.string.orientation_unknown, orientation)
    }
    val smallestText = when {
        smallestScreenWidthDp > 0 -> resources.getString(
            R.string.configuration_dp,
            smallestScreenWidthDp,
        )

        effectiveSmallestScreenWidthDp > 0 -> resources.getString(
            R.string.configuration_unavailable_fallback,
            effectiveSmallestScreenWidthDp,
        )

        else -> resources.getString(R.string.status_unavailable)
    }
    val densityText = densityDpi.takeIf { it > 0 }?.toString()
        ?: resources.getString(R.string.status_unavailable)
    return resources.getString(
        R.string.configuration_summary,
        screenWidthDp,
        screenHeightDp,
        orientationText,
        smallestText,
        densityText,
    )
}

internal fun Long.toDurationText(resources: Resources): String {
    val totalSeconds = (this / 1_000L).coerceAtLeast(0L)
    val hours = totalSeconds / 3_600L
    val minutes = (totalSeconds % 3_600L) / 60L
    val seconds = totalSeconds % 60L
    return when {
        hours > 0 -> resources.getString(R.string.duration_hours_minutes, hours, minutes)
        minutes > 0 -> resources.getString(R.string.duration_minutes_seconds, minutes, seconds)
        else -> resources.getString(R.string.duration_seconds, seconds)
    }
}

internal fun Long.toTimeText(resources: Resources): String =
    DateTimeFormatter.ofPattern(
        resources.getString(R.string.time_pattern),
        resources.primaryLocale,
    )
        .withZone(ZoneId.systemDefault())
        .format(Instant.ofEpochMilli(this))

internal fun Long.toShortDateText(resources: Resources): String =
    DateTimeFormatter.ofPattern(
        resources.getString(R.string.short_date_pattern),
        resources.primaryLocale,
    )
        .withZone(ZoneId.systemDefault())
        .format(Instant.ofEpochMilli(this))
