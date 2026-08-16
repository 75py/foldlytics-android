package com.nagopy.android.foldlytics.data

import android.content.Context
import androidx.core.content.edit
import com.nagopy.android.foldlytics.model.Calibration
import com.nagopy.android.foldlytics.model.DisplayConfiguration

class CalibrationStore(context: Context) {
    private val preferences =
        context.getSharedPreferences("display_calibration", Context.MODE_PRIVATE)

    fun load(): Calibration = Calibration(
        cover = read("cover"),
        inner = read("inner"),
    )

    fun saveCover(configuration: DisplayConfiguration) {
        write("cover", configuration)
    }

    fun saveInner(configuration: DisplayConfiguration) {
        write("inner", configuration)
    }

    fun clear() {
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

    private fun write(prefix: String, configuration: DisplayConfiguration) {
        preferences.edit {
            putInt("${prefix}_width", configuration.screenWidthDp)
            putInt("${prefix}_height", configuration.screenHeightDp)
            putInt("${prefix}_smallest", configuration.smallestScreenWidthDp)
            putInt("${prefix}_orientation", configuration.orientation)
            putInt("${prefix}_density", configuration.densityDpi)
        }
    }
}
