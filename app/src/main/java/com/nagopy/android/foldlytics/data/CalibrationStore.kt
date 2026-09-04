package com.nagopy.android.foldlytics.data

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.nagopy.android.foldlytics.model.Calibration
import com.nagopy.android.foldlytics.model.CalibrationAnchor
import com.nagopy.android.foldlytics.model.CalibrationUpdateResult
import com.nagopy.android.foldlytics.model.DisplayConfiguration

class CalibrationStore internal constructor(
    private val persistence: CalibrationPersistence,
) {
    constructor(context: Context) : this(SharedPreferencesCalibrationPersistence(context))

    fun load(): Calibration = persistence.load().usableAnchorsOnly()

    fun saveCover(configuration: DisplayConfiguration): CalibrationUpdateResult =
        save(CalibrationAnchor.COVER, configuration)

    fun saveInner(configuration: DisplayConfiguration): CalibrationUpdateResult =
        save(CalibrationAnchor.INNER, configuration)

    fun clear() {
        persistence.clear()
    }

    private fun save(
        anchor: CalibrationAnchor,
        configuration: DisplayConfiguration,
    ): CalibrationUpdateResult {
        val result = load().withAnchor(anchor, configuration)
        if (result is CalibrationUpdateResult.Accepted) {
            persistence.save(result.calibration)
        }
        return result
    }
}

internal interface CalibrationPersistence {
    fun load(): Calibration

    fun save(calibration: Calibration)

    fun clear()
}

private class SharedPreferencesCalibrationPersistence(context: Context) : CalibrationPersistence {
    private val preferences =
        context.getSharedPreferences("display_calibration", Context.MODE_PRIVATE)

    override fun load(): Calibration = Calibration(
        cover = read("cover"),
        inner = read("inner"),
    )

    override fun save(calibration: Calibration) {
        preferences.edit {
            write("cover", calibration.cover)
            write("inner", calibration.inner)
        }
    }

    override fun clear() {
        preferences.edit { clear() }
    }

    private fun read(prefix: String): DisplayConfiguration? {
        if (!preferences.contains("${prefix}_width")) return null
        return DisplayConfiguration(
            screenWidthDp = preferences.getInt("${prefix}_width", 0),
            screenHeightDp = preferences.getInt("${prefix}_height", 0),
            smallestScreenWidthDp = preferences.getInt("${prefix}_smallest", 0),
            orientation = preferences.getInt("${prefix}_orientation", 0),
            densityDpi = preferences.getInt("${prefix}_density", 0),
        )
    }

    private fun SharedPreferences.Editor.write(
        prefix: String,
        configuration: DisplayConfiguration?,
    ) {
        if (configuration == null) {
            remove("${prefix}_width")
            remove("${prefix}_height")
            remove("${prefix}_smallest")
            remove("${prefix}_orientation")
            remove("${prefix}_density")
        } else {
            putInt("${prefix}_width", configuration.screenWidthDp)
            putInt("${prefix}_height", configuration.screenHeightDp)
            putInt("${prefix}_smallest", configuration.smallestScreenWidthDp)
            putInt("${prefix}_orientation", configuration.orientation)
            putInt("${prefix}_density", configuration.densityDpi)
        }
    }
}
