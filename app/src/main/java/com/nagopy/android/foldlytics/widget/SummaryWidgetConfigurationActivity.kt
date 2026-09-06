package com.nagopy.android.foldlytics.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.nagopy.android.foldlytics.R
import com.nagopy.android.foldlytics.ui.FoldlyticsTheme

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
                var selected by rememberSaveable { mutableStateOf(preferences.period(id).name) }
                Surface(modifier = Modifier.fillMaxSize()) {
                    Column(Modifier.padding(horizontal = 24.dp, vertical = 56.dp)) {
                        Text(stringResource(R.string.widget_configuration_title), style = MaterialTheme.typography.headlineSmall)
                        Text(stringResource(R.string.widget_configuration_description), Modifier.padding(vertical = 16.dp))
                        WidgetPeriod.entries.forEach { period ->
                            Row(
                                modifier = Modifier.fillMaxWidth().clickable { selected = period.name }.padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                RadioButton(selected == period.name, onClick = { selected = period.name })
                                Text(stringResource(SummaryWidgetRenderer.periodLabel(period)))
                            }
                        }
                        Button(
                            onClick = {
                                preferences.setPeriod(id, WidgetPeriod.fromName(selected))
                                SummaryWidgetUpdater.enqueue(this@SummaryWidgetConfigurationActivity, sync = true)
                                setResult(RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id))
                                finish()
                            },
                            modifier = Modifier.align(Alignment.End).padding(top = 16.dp),
                        ) { Text(stringResource(R.string.widget_save)) }
                    }
                }
            }
        }
    }
}
