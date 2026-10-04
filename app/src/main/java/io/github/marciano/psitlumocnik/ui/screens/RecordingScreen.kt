package io.github.marciano.psitlumocnik.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Waves
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.marciano.psitlumocnik.analysis.Interpreter
import io.github.marciano.psitlumocnik.audio.Player
import io.github.marciano.psitlumocnik.data.BodySignal
import io.github.marciano.psitlumocnik.data.Situation
import io.github.marciano.psitlumocnik.ui.AppViewModel
import io.github.marciano.psitlumocnik.ui.components.ProbabilityBars
import io.github.marciano.psitlumocnik.ui.components.StatTile
import io.github.marciano.psitlumocnik.ui.components.Waveform
import io.github.marciano.psitlumocnik.ui.components.icon
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun RecordingScreen(vm: AppViewModel, recordingId: String, snackbar: SnackbarHostState, onBack: () -> Unit) {
    val store by vm.store.collectAsState()
    val record by vm.record.collectAsState()
    val rec = store.recordings.firstOrNull { it.id == recordingId }
    if (rec == null) { LaunchedEffect(Unit) { onBack() }; return }
    val history = remember(store) { store.recordings.filter { it.dogId == rec.dogId } }
    val analysis = rec.analysis
    val interpretation = remember(rec, history) { analysis?.let { Interpreter.interpret(it, history, excludeId = rec.id) } }

    val player = remember { Player() }
    var playing by remember { mutableStateOf(false) }
    var playhead by remember { mutableStateOf<Int?>(null) }
    DisposableEffect(Unit) { onDispose { player.stop() } }
    LaunchedEffect(playing) {
        while (playing) { playhead = player.positionMs; delay(50) }
        playhead = null
    }

    var situation by remember(rec.id) { mutableStateOf(rec.situation) }
    var signals by remember(rec.id) { mutableStateOf(rec.bodySignals) }
    var note by remember(rec.id) { mutableStateOf(rec.note) }
    var confirmDelete by remember { mutableStateOf(false) }
    var showBarks by remember { mutableStateOf(false) }
    val dirty = situation != rec.situation || signals != rec.bodySignals || note != rec.note || rec.autoLabeled

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(formatDate(rec.createdAt)) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Zpět") } },
                actions = { IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Filled.Delete, "Smazat") } },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { pad ->
        Column(
            Modifier.fillMaxSize().padding(pad).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // ----- Zvuk -----
            if (analysis != null) Waveform(analysis, playhead)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                FilledTonalButton(onClick = {
                    if (playing) { player.stop(); playing = false }
                    else runCatching { player.play(vm.repo.audioFile(rec.id)) { playing = false }; playing = true }
                }) {
                    Icon(if (playing) Icons.Filled.Stop else Icons.Filled.PlayArrow, null)
                    Spacer(Modifier.width(6.dp)); Text(if (playing) "Zastavit" else "Přehrát")
                }
                OutlinedButton(onClick = { vm.reanalyze(rec) }) {
                    Icon(Icons.Filled.Refresh, null); Spacer(Modifier.width(6.dp)); Text("Rozebrat znovu")
                }
            }

            // ----- Výklad -----
            if (interpretation != null && analysis != null) {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                    Column(Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Psychology, null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
                            Spacer(Modifier.width(8.dp))
                            Text(
                                if (analysis.hasBarks) "Nejspíš: ${interpretation.top?.label}" else "Ticho po pěšině",
                                style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onPrimaryContainer,
                            )
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(
                            when {
                                interpretation.learnedWeight == 0f -> "Odhad podle obecných pravidel. Označuj nahrávky a appka se naučí tvého psa."
                                else -> "Naučeno z ${interpretation.learnedFrom} označených nahrávek (váha ${(interpretation.learnedWeight * 100).toInt()} %), zbytek obecná pravidla."
                            },
                            color = MaterialTheme.colorScheme.onPrimaryContainer, style = MaterialTheme.typography.bodyMedium,
                        )
                        if (rec.situation != null && !rec.autoLabeled && analysis.hasBarks) {
                            Spacer(Modifier.height(4.dp))
                            Text(
                                if (interpretation.top == rec.situation) "✔ Sedí s tím, co jsi označil." else "✘ Označil jsi „${rec.situation.label}“ – tady se appka ještě učí.",
                                fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onPrimaryContainer,
                            )
                        }
                        if (analysis.hasBarks) {
                            Spacer(Modifier.height(16.dp))
                            ProbabilityBars(interpretation.probabilities)
                        }
                    }
                }

                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Co jsem slyšel", style = MaterialTheme.typography.titleMedium)
                        interpretation.explanation.forEach { Text("• $it") }
                        if (!vm.hasAi) Text("• AI model YAMNet se nenačetl – jede se jen na měření.", color = MaterialTheme.colorScheme.error)
                    }
                }

                if (analysis.hasBarks) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        StatTile("Výška", if (analysis.meanPitchHz > 0) "${analysis.meanPitchHz.toInt()} Hz" else "šum", Modifier.weight(1f), Icons.Filled.GraphicEq)
                        StatTile("Čistota hlasu", "${(analysis.meanTonality * 100).toInt()} %", Modifier.weight(1f), Icons.Filled.Waves)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        StatTile("Tempo", if (analysis.barks.size >= 2) "%.1f /s".format(analysis.barkRate) else "–", Modifier.weight(1f), Icons.Filled.Speed)
                        StatTile("Délka štěku", "${analysis.meanDurationMs.toInt()} ms", Modifier.weight(1f), Icons.AutoMirrored.Filled.VolumeUp)
                    }
                    TextButton(onClick = { showBarks = !showBarks }) {
                        Icon(if (showBarks) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, null)
                        Spacer(Modifier.width(6.dp)); Text("Jednotlivá štěknutí (${analysis.barks.size})")
                    }
                    if (showBarks) Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        analysis.barks.forEachIndexed { i, b ->
                            Text(
                                "${i + 1}. v ${"%.2f".format(b.startMs / 1000f)} s · ${b.durationMs} ms · " +
                                    (if (b.pitchHz > 0) "${b.pitchHz.toInt()} Hz" else "šum") +
                                    " · čistota ${(b.tonality * 100).toInt()} % · těžiště ${b.centroidHz.toInt()} Hz",
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }
            }

            // ----- Označení -----
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Tune, null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(8.dp))
                        Text("Co se doopravdy dělo?", style = MaterialTheme.typography.titleMedium)
                    }
                    if (rec.autoLabeled) Text(
                        "Z hlídání je předvyplněno „${rec.situation?.label}“. Potvrď nebo oprav – teprve pak se z toho appka učí.",
                        color = MaterialTheme.colorScheme.tertiary,
                    )
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Situation.entries.forEach { s ->
                            FilterChip(
                                selected = situation == s,
                                onClick = { situation = if (situation == s) null else s },
                                label = { Text(s.label) },
                                leadingIcon = { Icon(s.icon, null, Modifier.size(18.dp)) },
                            )
                        }
                    }
                    situation?.let { Text(it.hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    Text("Řeč těla", style = MaterialTheme.typography.titleSmall)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        BodySignal.entries.forEach { b ->
                            FilterChip(
                                selected = b in signals,
                                onClick = { signals = if (b in signals) signals - b else signals + b },
                                label = { Text(b.label) },
                            )
                        }
                    }
                    OutlinedTextField(note, { note = it }, Modifier.fillMaxWidth(), label = { Text("Poznámka") })
                    Button(onClick = { vm.label(rec, situation, signals, note) }, enabled = dirty, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Filled.Check, null); Spacer(Modifier.width(8.dp))
                        Text(if (rec.autoLabeled) "Potvrdit a uložit" else "Uložit")
                    }
                }
            }
        }
    }

    if (confirmDelete) AlertDialog(
        onDismissRequest = { confirmDelete = false },
        title = { Text("Smazat nahrávku?") },
        text = { Text("Zmizí zvuk i označení. Appka se z ní přestane učit.") },
        confirmButton = { TextButton(onClick = { confirmDelete = false; player.stop(); vm.deleteRecording(rec.id); onBack() }) { Text("Smazat") } },
        dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Nechat") } },
    )
    record.processing?.let { ProcessingDialog(it) }
}
