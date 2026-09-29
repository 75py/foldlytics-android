package com.nagopy.android.foldlytics.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.nagopy.android.foldlytics.R
import com.nagopy.android.foldlytics.insight.InsightStatus
import com.nagopy.android.foldlytics.insight.UsageInsightUiState
import java.time.Instant
import java.time.ZoneId

@Composable
internal fun UsageInsightCard(state: UsageInsightUiState) {
    if (state.status == InsightStatus.HIDDEN) return
    var showEvidence by remember { mutableStateOf(false) }
    var showLicenses by remember { mutableStateOf(false) }
    LabCard(
        title = stringResource(R.string.insight_title),
        modifier = Modifier.testTag("usage_insight_card"),
    ) {
        val facts = state.facts
        facts?.range?.let { range ->
            val zone = ZoneId.of(range.zoneId)
            val start = Instant.ofEpochMilli(range.currentStartMillis).atZone(zone).toLocalDate()
            val end = Instant.ofEpochMilli(range.currentEndMillis).atZone(zone).toLocalDate().minusDays(1)
            Text(
                stringResource(R.string.insight_period, start.toString(), end.toString()),
                style = MaterialTheme.typography.bodySmall,
            )
        }
        when (state.status) {
            InsightStatus.READY -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                state.paragraphs.forEach { Text(it) }
                Text(stringResource(R.string.insight_generated_note), style = MaterialTheme.typography.bodySmall)
            }
            InsightStatus.CHECKING, InsightStatus.PREPARING, InsightStatus.GENERATING -> {
                LinearProgressIndicator(Modifier.fillMaxWidth())
                Text(
                    stringResource(
                        if (state.status == InsightStatus.PREPARING) R.string.insight_preparing
                        else R.string.insight_generating,
                    ),
                )
            }
            InsightStatus.INSUFFICIENT_DATA -> Text(stringResource(R.string.insight_insufficient))
            InsightStatus.UNAVAILABLE -> Text(stringResource(R.string.insight_unavailable))
            InsightStatus.HIDDEN -> Unit
        }
        if (facts != null) {
            TextButton(onClick = { showEvidence = true }) {
                Text(stringResource(R.string.insight_evidence))
            }
        }
        TextButton(onClick = { showLicenses = true }) {
            Text(stringResource(R.string.insight_licenses))
        }
    }
    if (showEvidence) {
        AlertDialog(
            onDismissRequest = { showEvidence = false },
            title = { Text(stringResource(R.string.insight_evidence)) },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    state.facts?.let { facts ->
                        (facts.facts + facts.limitations).forEach { Text(it.displayText) }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showEvidence = false }) {
                    Text(stringResource(R.string.action_dismiss))
                }
            },
        )
    }
    if (showLicenses) {
        val context = LocalContext.current
        val licenses = remember(context) {
            listOf("LLAMA-LICENSE.txt", "QWEN-LICENSE.txt").joinToString("\n\n") { name ->
                context.assets.open(name).bufferedReader().use { it.readText() }
            }
        }
        AlertDialog(
            onDismissRequest = { showLicenses = false },
            title = { Text(stringResource(R.string.insight_licenses)) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    Text("llama.cpp (MIT) · Qwen3-0.6B Q8_0 (Apache-2.0)")
                    Text(licenses, style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = {
                TextButton(onClick = { showLicenses = false }) {
                    Text(stringResource(R.string.action_dismiss))
                }
            },
        )
    }
}
