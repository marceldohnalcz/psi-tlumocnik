package io.github.marciano.psitlumocnik.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Celebration
import androidx.compose.material.icons.filled.DoorFront
import androidx.compose.material.icons.filled.Hearing
import androidx.compose.material.icons.filled.NightsStay
import androidx.compose.material.icons.filled.SentimentVeryDissatisfied
import androidx.compose.material.icons.filled.SportsBaseball
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.marciano.psitlumocnik.data.Analysis
import io.github.marciano.psitlumocnik.data.Situation

val Situation.icon: ImageVector
    get() = when (this) {
        Situation.CIZI -> Icons.Filled.DoorFront
        Situation.POPLACH -> Icons.Filled.Hearing
        Situation.AGRESE -> Icons.Filled.Warning
        Situation.HRA -> Icons.Filled.SportsBaseball
        Situation.DOZADOVANI -> Icons.AutoMirrored.Filled.DirectionsWalk
        Situation.SAMOTA -> Icons.Filled.NightsStay
        Situation.STRACH -> Icons.Filled.SentimentVeryDissatisfied
        Situation.VITANI -> Icons.Filled.Celebration
    }

data class PillTab(val label: String, val icon: ImageVector)

/** Segmentový „pill“ přepínač s ikonami a popisky. */
@Composable
fun PillTabs(tabs: List<PillTab>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    SingleChoiceSegmentedButtonRow(modifier.fillMaxWidth()) {
        tabs.forEachIndexed { i, tab ->
            SegmentedButton(
                selected = i == selected,
                onClick = { onSelect(i) },
                shape = SegmentedButtonDefaults.itemShape(i, tabs.size),
                icon = { SegmentedButtonDefaults.Icon(i == selected) { Icon(tab.icon, null, Modifier.size(18.dp)) } },
                label = { Text(tab.label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            )
        }
    }
}

/** Obálka nahrávky se zvýrazněnými štěknutími a pozicí přehrávání. */
@Composable
fun Waveform(analysis: Analysis, playheadMs: Int?, modifier: Modifier = Modifier) {
    val wave = MaterialTheme.colorScheme.onSurfaceVariant
    val bark = MaterialTheme.colorScheme.primary
    val barkBg = MaterialTheme.colorScheme.primaryContainer
    val head = MaterialTheme.colorScheme.tertiary
    Canvas(
        modifier.fillMaxWidth().height(110.dp).clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
    ) {
        val total = analysis.durationMs.coerceAtLeast(1)
        for (b in analysis.barks) {
            val x0 = size.width * b.startMs / total
            val w = (size.width * b.durationMs / total).coerceAtLeast(3f)
            drawRoundRect(barkBg, Offset(x0, 0f), Size(w, size.height), CornerRadius(6f))
        }
        val env = analysis.envelope
        if (env.isNotEmpty()) {
            val step = size.width / env.size
            val mid = size.height / 2
            env.forEachIndexed { i, v ->
                val x = i * step + step / 2
                val t = i.toFloat() / env.size * total
                val inBark = analysis.barks.any { t >= it.startMs && t <= it.startMs + it.durationMs }
                val h = (v * (size.height * 0.85f)).coerceAtLeast(2f)
                drawLine(if (inBark) bark else wave.copy(alpha = 0.55f), Offset(x, mid - h / 2), Offset(x, mid + h / 2), strokeWidth = (step * 0.7f).coerceAtLeast(1.5f))
            }
        }
        if (playheadMs != null) {
            val x = size.width * playheadMs / total
            drawLine(head, Offset(x, 0f), Offset(x, size.height), strokeWidth = 4f)
        }
    }
}

/** Sloupcové pravděpodobnosti situací. */
@Composable
fun ProbabilityBars(probs: List<Pair<Situation, Float>>, max: Int = 4) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        probs.take(max).forEachIndexed { i, (sit, p) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(sit.icon, null, tint = if (i == 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Row {
                        Text(sit.label, Modifier.weight(1f), fontWeight = if (i == 0) FontWeight.Bold else FontWeight.Normal)
                        Text("${(p * 100).toInt()} %", fontWeight = FontWeight.SemiBold)
                    }
                    Spacer(Modifier.height(4.dp))
                    Box(Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(5.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh)) {
                        Box(
                            Modifier.fillMaxWidth(p.coerceIn(0.01f, 1f)).height(10.dp).clip(RoundedCornerShape(5.dp))
                                .background(if (i == 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary)
                        )
                    }
                }
            }
        }
    }
}

/** Ukazatel hlasitosti mikrofonu. */
@Composable
fun LevelMeter(level: Float, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(14.dp).clip(RoundedCornerShape(7.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh)) {
        Box(
            Modifier.fillMaxWidth(level.coerceIn(0.02f, 1f)).height(14.dp).clip(RoundedCornerShape(7.dp))
                .background(if (level > 0.85f) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.tertiary)
        )
    }
}

@Composable
fun StatTile(label: String, value: String, modifier: Modifier = Modifier, icon: ImageVector = Icons.Filled.Bolt) {
    Column(
        modifier.clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh).padding(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(6.dp))
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(4.dp))
        Text(value, style = MaterialTheme.typography.titleMedium)
    }
}
