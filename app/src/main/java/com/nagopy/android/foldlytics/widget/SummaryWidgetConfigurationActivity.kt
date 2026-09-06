package com.nagopy.android.foldlytics.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.nagopy.android.foldlytics.R
import com.nagopy.android.foldlytics.ui.FoldlyticsTheme

internal const val WIDGET_CONFIGURATION_SCROLL_TAG = "widget_configuration_scroll"
internal const val WIDGET_CONFIGURATION_SAVE_TAG = "widget_configuration_save"

class SummaryWidgetConfigurationActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setResult(RESULT_CANCELED)
        val id = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        val provider = AppWidgetManager.getInstance(this).getAppWidgetInfo(id)?.provider
        if (id == AppWidgetManager.INVALID_APPWIDGET_ID || provider != ComponentName(this, SummaryWidgetProvider::class.java)) {
            finish()
            return
        }
        val preferences = SummaryWidgetPreferences(this)
        setContent {
            FoldlyticsTheme {
                SummaryWidgetConfigurationContent(
                    initialPeriod = preferences.period(id),
                    onSave = { period ->
                        preferences.setPeriod(id, period)
                        SummaryWidgetUpdater.enqueue(this@SummaryWidgetConfigurationActivity, sync = true)
                        setResult(RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id))
                        finish()
                    },
                )
            }
        }
    }
}

@Composable
internal fun SummaryWidgetConfigurationContent(
    initialPeriod: WidgetPeriod,
    onSave: (WidgetPeriod) -> Unit,
    modifier: Modifier = Modifier,
) {
    var selected by rememberSaveable { mutableStateOf(initialPeriod.name) }
    Surface(modifier = modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .verticalScroll(rememberScrollState())
                .testTag(WIDGET_CONFIGURATION_SCROLL_TAG)
                .padding(24.dp),
        ) {
            Text(
                stringResource(R.string.widget_configuration_title),
                style = MaterialTheme.typography.headlineSmall,
            )
            Text(
                stringResource(R.string.widget_configuration_description),
                Modifier.padding(vertical = 16.dp),
            )
            Column(Modifier.selectableGroup()) {
                WidgetPeriod.entries.forEach { period ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .selectable(
                                selected = selected == period.name,
                                role = Role.RadioButton,
                                onClick = { selected = period.name },
                            )
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = selected == period.name, onClick = null)
                        Text(stringResource(SummaryWidgetRenderer.periodLabel(period)))
                    }
                }
            }
            Button(
                onClick = { onSave(WidgetPeriod.fromName(selected)) },
                modifier = Modifier
                    .align(Alignment.End)
                    .padding(top = 16.dp)
                    .testTag(WIDGET_CONFIGURATION_SAVE_TAG),
            ) {
                Text(stringResource(R.string.widget_save))
            }
        }
    }
}
