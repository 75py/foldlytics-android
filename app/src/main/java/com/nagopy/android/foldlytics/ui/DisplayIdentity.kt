@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.nagopy.android.foldlytics.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.nagopy.android.foldlytics.R

internal const val RECORD_DETAILS_TAG = "record_details"

/** A compact device diagram; deliberately illustrative, never an encoding of the share. */
@Composable
internal fun DisplayDuet(modifier: Modifier = Modifier) {
    val palette = foldlyticsPalette
    val outline = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.16f)
    Canvas(modifier.clearAndSetSemantics {}) {
        val u = size.width / 104f
        rotate(-10f, Offset(26f * u, 48f * u)) {
            drawRoundRect(palette.heroCover, Offset(7f * u, 15f * u), Size(35f * u, 63f * u), CornerRadius(8f * u))
            drawRoundRect(outline, Offset(7f * u, 15f * u), Size(35f * u, 63f * u), CornerRadius(8f * u), style = Stroke(u))
            drawCircle(palette.onHeroCover.copy(alpha = 0.5f), 1.5f * u, Offset(24f * u, 21f * u))
        }
        rotate(7f, Offset(67f * u, 45f * u)) {
            drawRoundRect(palette.heroInner, Offset(37f * u, 9f * u), Size(59f * u, 64f * u), CornerRadius(9f * u))
            drawLine(Color.White.copy(alpha = 0.35f), Offset(66.5f * u, 13f * u), Offset(66.5f * u, 69f * u), 1.2f * u)
            drawCircle(Color.White.copy(alpha = 0.65f), 1.5f * u, Offset(86f * u, 15f * u))
        }
    }
}

/** Fine vertical marks read as one continuous proportion; the split is never rounded to a mark. */
@Composable
internal fun DisplayRatioRibbon(coverFraction: Float, modifier: Modifier = Modifier) {
    val ratio by animateFloatAsState(
        coverFraction.coerceIn(0f, 1f),
        animationSpec = MaterialTheme.motionScheme.slowSpatialSpec(),
        label = "displayBalance",
    )
    val inner = postureColors().inner
    val cover = postureColors().cover
    Canvas(modifier.fillMaxWidth().height(22.dp).clearAndSetSemantics {}) {
        val step = size.width / 48f
        val gap = 2.dp.toPx().coerceAtMost(step * 0.4f)
        fun marks(color: Color) {
            repeat(48) { index ->
                drawRoundRect(color, Offset(index * step, 0f), Size(step - gap, size.height), CornerRadius(2.dp.toPx()))
            }
        }
        marks(inner)
        clipRect(right = size.width * ratio.coerceIn(0f, 1f)) { marks(cover) }
    }
}

@Composable
internal fun DetailDisclosure(
    title: String,
    modifier: Modifier = Modifier,
    tag: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val stateLabel = stringResource(if (expanded) R.string.action_hide_details else R.string.action_show_details)
    val angle by animateFloatAsState(if (expanded) 180f else 0f,
        animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(), label = "disclosure")
    Column(modifier.fillMaxWidth()) {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Row(
            Modifier.fillMaxWidth().heightIn(min = 48.dp)
                .then(if (tag != null) Modifier.testTag(tag) else Modifier)
                .clickable(role = Role.Button) { expanded = !expanded }
                .semantics { stateDescription = stateLabel }
                .padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(title, Modifier.weight(1f), style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Chevron(MaterialTheme.colorScheme.onSurfaceVariant, Modifier.rotate(angle), down = true)
        }
        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically(MaterialTheme.motionScheme.defaultSpatialSpec()) + fadeIn(),
            exit = shrinkVertically(MaterialTheme.motionScheme.defaultSpatialSpec()) + fadeOut(),
        ) {
            Column(Modifier.padding(bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
        }
    }
}

@Composable
internal fun RefreshButton(enabled: Boolean, refreshing: Boolean, onClick: () -> Unit) {
    val description = stringResource(if (refreshing) R.string.action_syncing else R.string.action_refresh)
    val color = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
    IconButton(onClick = onClick, enabled = enabled,
        modifier = Modifier.semantics { contentDescription = description }) {
        Canvas(Modifier.size(22.dp)) { drawRefreshIcon(color) }
    }
}

/** Sorting is a small filter, visually subordinate to switching the main view. */
@Composable
internal fun <T> FilterChoices(
    options: List<T>,
    selected: T,
    onSelected: (T) -> Unit,
    label: @Composable (T) -> String,
    optionModifier: @Composable (T) -> Modifier,
    modifier: Modifier = Modifier,
) {
    FlowRow(modifier.fillMaxWidth().selectableGroup(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { option ->
            val active = option == selected
            Row(
                Modifier.heightIn(min = 48.dp).clip(CircleShape)
                    .background(if (active) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
                    .then(optionModifier(option))
                    .selectable(active, role = Role.RadioButton, onClick = { onSelected(option) })
                    .padding(horizontal = 20.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                if (active) {
                    Text("✓", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.clearAndSetSemantics {})
                }
                Text(label(option), style = MaterialTheme.typography.labelLarge,
                    color = if (active) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
