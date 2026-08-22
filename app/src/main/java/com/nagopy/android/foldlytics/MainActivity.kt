package com.nagopy.android.foldlytics

import android.content.Intent
import android.content.res.Configuration
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.Rect
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.window.layout.FoldingFeature
import androidx.window.layout.WindowInfoTracker
import com.google.android.gms.oss.licenses.v2.OssLicensesMenuActivity
import com.nagopy.android.foldlytics.data.toDisplayConfiguration
import com.nagopy.android.foldlytics.share.SummaryImageShare
import com.nagopy.android.foldlytics.ui.FoldlyticsScreen
import com.nagopy.android.foldlytics.ui.FoldlyticsTheme
import java.time.LocalDate
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val PRIVACY_POLICY_URL = "https://www.nagopy.com/privacy-policy/"

class MainActivity : ComponentActivity(), SensorEventListener {
    private val viewModel: MainViewModel by viewModels()
    private lateinit var sensorManager: SensorManager
    private var hingeSensor: Sensor? = null
    private val createCsvDocument = registerForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv"),
    ) { uri ->
        if (uri == null) return@registerForActivityResult
        lifecycleScope.launch {
            val saved = try {
                withContext(Dispatchers.IO) {
                    contentResolver.openOutputStream(uri, "wt")
                        ?.bufferedWriter(Charsets.UTF_8)
                        ?.use(viewModel::writeLongTermCsv)
                        ?: error(getString(R.string.error_open_output))
                }
                true
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                false
            }
            Toast.makeText(
                this@MainActivity,
                if (saved) R.string.csv_saved else R.string.csv_save_failed,
                if (saved) Toast.LENGTH_SHORT else Toast.LENGTH_LONG,
            ).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        sensorManager = getSystemService(SensorManager::class.java)
        hingeSensor = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            sensorManager.getDefaultSensor(Sensor.TYPE_HINGE_ANGLE)
        } else {
            null
        }
        viewModel.updateHingeSensor(available = hingeSensor != null)
        viewModel.updateConfiguration(resources.configuration.toDisplayConfiguration())

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                WindowInfoTracker.getOrCreate(this@MainActivity)
                    .windowLayoutInfo(this@MainActivity)
                    .collect { layoutInfo ->
                        val feature = layoutInfo.displayFeatures
                            .filterIsInstance<FoldingFeature>()
                            .firstOrNull()
                        viewModel.updateFoldFeature(feature.toSnapshot(resources))
                    }
            }
        }

        setContent {
            FoldlyticsTheme {
                val state by viewModel.uiState.collectAsStateWithLifecycle()
                FoldlyticsScreen(
                    state = state,
                    onOpenUsageAccess = ::openUsageAccessSettings,
                    onSaveCover = viewModel::saveCurrentAsCover,
                    onSaveInner = viewModel::saveCurrentAsInner,
                    onClearCalibration = viewModel::clearCalibration,
                    onPeriodChanged = viewModel::setPeriod,
                    onCustomPeriodChanged = viewModel::setCustomPeriod,
                    onRefresh = viewModel::refreshFromCurrentState,
                    onShare = ::shareReport,
                    onExportCsv = ::exportLongTermCsv,
                    onOpenPrivacyPolicy = ::openPrivacyPolicy,
                    onOpenOssLicenses = ::openOssLicenses,
                    onShareSummary = ::shareSummaryImage,
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.updateConfiguration(resources.configuration.toDisplayConfiguration())
        viewModel.recordAppForegroundCheckpoint()
        viewModel.checkPermissionAndRefresh()
        hingeSensor?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
        }
    }

    override fun onPause() {
        viewModel.recordAppBackgroundCheckpoint()
        sensorManager.unregisterListener(this)
        super.onPause()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        viewModel.updateConfiguration(newConfig.toDisplayConfiguration())
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
            event.sensor.type == Sensor.TYPE_HINGE_ANGLE
        ) {
            viewModel.updateHingeSensor(available = true, angle = event.values.firstOrNull())
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    private fun openUsageAccessSettings() {
        val appPage = Intent(
            Settings.ACTION_USAGE_ACCESS_SETTINGS,
            "package:$packageName".toUri(),
        )
        runCatching { startActivity(appPage) }
            .onFailure { startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) }
    }

    private fun shareReport() {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, getString(R.string.share_report_subject))
            putExtra(Intent.EXTRA_TEXT, viewModel.diagnosticReport())
        }
        startActivity(Intent.createChooser(intent, getString(R.string.share_report_chooser)))
    }

    private suspend fun shareSummaryImage(bitmap: Bitmap): Boolean {
        val imageUri = runCatching {
            withContext(Dispatchers.IO) {
                SummaryImageShare.writeImage(this@MainActivity, bitmap)
            }
        }.getOrNull() ?: return false

        return runCatching {
            val sendIntent = SummaryImageShare.createSendIntent(this, imageUri)
            startActivity(
                Intent.createChooser(sendIntent, getString(R.string.share_summary_chooser)),
            )
        }.isSuccess
    }

    private fun exportLongTermCsv() {
        createCsvDocument.launch("Foldlytics-all-daily-${LocalDate.now()}.csv")
    }

    private fun openPrivacyPolicy() {
        runCatching {
            startActivity(Intent(Intent.ACTION_VIEW, PRIVACY_POLICY_URL.toUri()))
        }.onFailure {
            Toast.makeText(
                this,
                R.string.privacy_policy_open_failed,
                Toast.LENGTH_LONG,
            ).show()
        }
    }

    private fun openOssLicenses() {
        OssLicensesMenuActivity.setActivityTitle(getString(R.string.action_open_source_licenses))
        startActivity(Intent(this, OssLicensesMenuActivity::class.java))
    }
}

private fun FoldingFeature?.toSnapshot(resources: Resources): FoldFeatureSnapshot {
    if (this == null) return FoldFeatureSnapshot()
    return FoldFeatureSnapshot(
        present = true,
        state = when (state) {
            FoldingFeature.State.FLAT -> if (isSeparating) {
                resources.getString(R.string.fold_feature_separating, "FLAT")
            } else {
                "FLAT"
            }
            FoldingFeature.State.HALF_OPENED -> "HALF_OPENED"
            else -> state.toString()
        },
        orientation = when (orientation) {
            FoldingFeature.Orientation.HORIZONTAL ->
                resources.getString(R.string.orientation_horizontal)
            FoldingFeature.Orientation.VERTICAL ->
                resources.getString(R.string.orientation_vertical)
            else -> orientation.toString()
        },
        occlusion = when (occlusionType) {
            FoldingFeature.OcclusionType.FULL -> "FULL"
            FoldingFeature.OcclusionType.NONE -> "NONE"
            else -> occlusionType.toString()
        },
        bounds = bounds.toCompactText(),
    )
}

private fun Rect.toCompactText(): String = "[$left,$top–$right,$bottom]"
