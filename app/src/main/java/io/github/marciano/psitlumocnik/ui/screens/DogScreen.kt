package io.github.marciano.psitlumocnik.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.automirrored.filled.Help
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import io.github.marciano.psitlumocnik.analysis.Dsp
import io.github.marciano.psitlumocnik.analysis.Interpreter
import io.github.marciano.psitlumocnik.data.Recording
import io.github.marciano.psitlumocnik.data.Situation
import io.github.marciano.psitlumocnik.data.Source
import io.github.marciano.psitlumocnik.ui.AppViewModel
import io.github.marciano.psitlumocnik.ui.components.LevelMeter
import io.github.marciano.psitlumocnik.ui.components.PillTab
import io.github.marciano.psitlumocnik.ui.components.PillTabs
import io.github.marciano.psitlumocnik.ui.components.StatTile
import io.github.marciano.psitlumocnik.ui.components.icon
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val dateFmt = DateTimeFormatter.ofPattern("d. M. yyyy HH:mm", Locale.forLanguageTag("cs"))
fun formatDate(ms: Long): String = dateFmt.format(Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()))
fun formatDuration(ms: Long): String = "%d:%02d".format(ms / 60000, (ms / 1000) % 60)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DogScreen(vm: AppViewModel, dogId: String, snackbar: SnackbarHostState, onBack: () -> Unit, onOpenRecording: (String) -> Unit) {
    val store by vm.store.collectAsState()
    val dog = store.dogs.firstOrNull { it.id == dogId }
    if (dog == null) { LaunchedEffect(Unit) { onBack() }; return }
    val recordings = remember(store) { store.recordings.filter { it.dogId == dogId }.sortedByDescending { it.createdAt } }
    var tab by rememberSaveable { mutableStateOf(0) }
    var menu by remember { mutableStateOf(false) }
    var edit by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val record by vm.record.collectAsState()
    val saveLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri: Uri? ->
        uri?.let { vm.saveProfile(dogId, it) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(dog.name) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Zpět") } },
                actions = {
                    IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, "Další") }
                    DropdownMenu(menu, { menu = false }) {
                        DropdownMenuItem({ Text("Upravit psa") }, { menu = false; edit = true }, leadingIcon = { Icon(Icons.Filled.Edit, null) })
                        DropdownMenuItem({ Text("Poslat profil") }, { menu = false; vm.shareProfile(dogId) }, leadingIcon = { Icon(Icons.Filled.Share, null) })
                        DropdownMenuItem({ Text("Uložit profil do souboru") }, { menu = false; saveLauncher.launch("${dog.name}.psi") }, leadingIcon = { Icon(Icons.Filled.Save, null) })
                        DropdownMenuItem({ Text("Přepočítat všechny nahrávky") }, { menu = false; vm.reanalyzeAll(dogId) }, leadingIcon = { Icon(Icons.Filled.Refresh, null) })
                        DropdownMenuItem({ Text("Smazat psa") }, { menu = false; confirmDelete = true }, leadingIcon = { Icon(Icons.Filled.Delete, null) })
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { pad ->
        Column(Modifier.fillMaxSize().padding(pad)) {
            PillTabs(
                listOf(PillTab("Nahrát", Icons.Filled.Mic), PillTab("Historie", Icons.Filled.History), PillTab("Statistiky", Icons.Filled.BarChart)),
                tab, { tab = it }, Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            when (tab) {
                0 -> RecordTab(vm, dogId)
                1 -> HistoryTab(recordings, onOpenRecording)
                else -> StatsTab(recordings)
            }
        }
    }

    if (edit) DogDialog(dog, { edit = false }) { n, b, note -> vm.updateDog(dog.copy(name = n.trim(), breed = b.trim(), note = note.trim())); edit = false }
    if (confirmDelete) AlertDialog(
        onDismissRequest = { confirmDelete = false },
        title = { Text("Smazat ${dog.name}?") },
        text = { Text("Zmizí pes i všech ${recordings.size} nahrávek a všechno, co se appka o jeho hlasu naučila. Pokud chceš, nejdřív si ulož profil do souboru.") },
        confirmButton = { TextButton(onClick = { confirmDelete = false; vm.deleteDog(dogId) }) { Text("Smazat") } },
        dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Nechat") } },
    )
    record.processing?.let { ProcessingDialog(it) }
}

// ---------------- Nahrát ----------------

@Composable
private fun RecordTab(vm: AppViewModel, dogId: String) {
    val context = LocalContext.current
    val record by vm.record.collectAsState()
    val level by vm.micLevel.collectAsState()
    val watch by vm.watch.collectAsState()
    var pendingWatch by remember { mutableStateOf(false) }

    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        if (pendingWatch) { pendingWatch = false; vm.startWatch(dogId) }
    }
    val micLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (!granted) { pendingWatch = false; return@rememberLauncherForActivityResult }
        if (pendingWatch) {
            if (Build.VERSION.SDK_INT >= 33) notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            else { pendingWatch = false; vm.startWatch(dogId) }
        } else vm.startRecording()
    }
    val fileLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        uri?.let { vm.importAudio(dogId, it) }
    }
    fun hasMic() = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
    val watchingOther = watch.running && watch.dogId != dogId
    val watchingThis = watch.running && watch.dogId == dogId

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
            Column(Modifier.fillMaxWidth().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                FilledIconButton(
                    onClick = {
                        when {
                            record.recording -> vm.stopRecording(dogId)
                            watch.running -> {}
                            hasMic() -> vm.startRecording()
                            else -> micLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        }
                    },
                    enabled = !watch.running,
                    modifier = Modifier.size(120.dp),
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = if (record.recording) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                    ),
                ) {
                    Icon(if (record.recording) Icons.Filled.Stop else Icons.Filled.Mic, null, Modifier.size(56.dp))
                }
                Spacer(Modifier.height(12.dp))
                Text(
                    when {
                        record.recording -> "Nahrávám ${formatDuration(record.elapsedMs)} – ťukni pro konec"
                        watch.running -> "Mikrofon teď používá hlídání"
                        else -> "Ťukni a nahraj štěkot"
                    },
                    style = MaterialTheme.typography.titleMedium,
                )
                if (record.recording) {
                    Spacer(Modifier.height(12.dp))
                    LevelMeter(level)
                    Spacer(Modifier.height(8.dp))
                    TextButton(onClick = { vm.stopRecording(dogId, discard = true) }) {
                        Icon(Icons.Filled.Delete, null); Spacer(Modifier.width(6.dp)); Text("Zahodit")
                    }
                } else {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Mobil co nejblíž psovi, bez puštěné televize.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        OutlinedButton(onClick = { fileLauncher.launch(arrayOf("audio/*", "video/*")) }, Modifier.fillMaxWidth(), enabled = !record.recording) {
            Icon(Icons.Filled.AudioFile, null); Spacer(Modifier.width(8.dp)); Text("Nahrát ze souboru (WhatsApp, diktafon, video)")
        }

        Card(colors = CardDefaults.cardColors(containerColor = if (watchingThis) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.surfaceContainer)) {
            Column(Modifier.padding(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Shield, null, tint = MaterialTheme.colorScheme.tertiary)
                    Spacer(Modifier.width(8.dp))
                    Text("Hlídání", style = MaterialTheme.typography.titleLarge)
                }
                Spacer(Modifier.height(6.dp))
                Text("Nech mobil doma u psa. Každé štěkání se samo nahraje a rozebere – zjistíš, co dělá, když nejsi doma (a jestli tě pomlouvá sousedům).")
                Spacer(Modifier.height(12.dp))
                if (watchingThis) {
                    var now by remember { mutableStateOf(System.currentTimeMillis()) }
                    LaunchedEffect(Unit) { while (true) { delay(1000); now = System.currentTimeMillis() } }
                    Text("Běží ${formatDuration(now - watch.startedAt)} · zachyceno ${watch.clips}×" + if (watch.capturing) " · právě nahrávám" else "", fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(8.dp))
                    LevelMeter(watch.level)
                    Spacer(Modifier.height(12.dp))
                }
                watch.error?.let { Text("Chyba: $it", color = MaterialTheme.colorScheme.error); Spacer(Modifier.height(8.dp)) }
                if (watchingThis) {
                    Button(onClick = vm::stopWatch, Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) {
                        Icon(Icons.Filled.Stop, null); Spacer(Modifier.width(8.dp)); Text("Ukončit hlídání")
                    }
                } else {
                    Button(
                        onClick = {
                            // Žádost projde i při už uděleném oprávnění a zřetězí žádost o notifikace
                            pendingWatch = true
                            micLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        },
                        enabled = !record.recording && !watchingOther,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Filled.Shield, null); Spacer(Modifier.width(8.dp)); Text(if (watchingOther) "Hlídá se jiný pes" else "Spustit hlídání")
                    }
                }
            }
        }

        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.School, null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(8.dp))
                    Text("Jak appku naučit", style = MaterialTheme.typography.titleMedium)
                }
                Text("1. Po každé nahrávce označ, co se doopravdy dělo – ne co si myslíš, že chtěl říct.")
                Text("2. Přidej řeč těla (ocas, srst). Pomůže to zpětně ověřit výklad.")
                Text("3. Cíl: aspoň 10 nahrávek od každé situace. Pak už appka hádá podle tvého psa, ne podle učebnice.")
            }
        }
    }
}

// ---------------- Historie ----------------

private enum class HistoryFilter(val label: String) { ALL("Vše"), UNLABELED("Neoznačené"), AUTO("K potvrzení") }

@Composable
private fun HistoryTab(recordings: List<Recording>, onOpen: (String) -> Unit) {
    var filter by rememberSaveable { mutableStateOf(HistoryFilter.ALL) }
    var situation by rememberSaveable { mutableStateOf<Situation?>(null) }
    val shown = recordings.filter {
        when (filter) {
            HistoryFilter.ALL -> true
            HistoryFilter.UNLABELED -> it.situation == null
            HistoryFilter.AUTO -> it.autoLabeled
        } && (situation == null || it.situation == situation)
    }
    Column(Modifier.fillMaxSize()) {
        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(HistoryFilter.entries) { f ->
                FilterChip(filter == f && situation == null, { filter = f; situation = null }, { Text(f.label) })
            }
            items(Situation.entries) { s ->
                FilterChip(situation == s, { situation = if (situation == s) null else s; filter = HistoryFilter.ALL }, { Text(s.label) },
                    leadingIcon = { Icon(s.icon, null, Modifier.size(18.dp)) })
            }
        }
        if (shown.isEmpty()) {
            Text("Nic tu není. Pes asi mlčí, nebo jsi ještě nenahrával.", Modifier.padding(24.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        LazyColumn(contentPadding = PaddingValues(vertical = 8.dp)) {
            items(shown, key = { it.id }) { rec ->
                val guess = remember(rec, recordings.size) {
                    if (rec.situation == null && rec.analysis?.hasBarks == true) Interpreter.interpret(rec.analysis, recordings, rec.id).top else null
                }
                val barks = rec.analysis?.barks?.size ?: 0
                ListItem(
                    modifier = Modifier.clickable { onOpen(rec.id) },
                    leadingContent = {
                        Icon(rec.situation?.icon ?: Icons.AutoMirrored.Filled.Help, null,
                            tint = if (rec.situation != null && !rec.autoLabeled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline)
                    },
                    headlineContent = {
                        Text(when {
                            rec.situation != null && rec.autoLabeled -> "${rec.situation.label}? (k potvrzení)"
                            rec.situation != null -> rec.situation.label
                            guess != null -> "Neoznačeno · tipuju: ${guess.label}"
                            else -> "Neoznačeno"
                        })
                    },
                    supportingContent = {
                        Text("${formatDate(rec.createdAt)} · ${formatDuration(rec.durationMs.toLong())} · štěknutí: $barks")
                    },
                    trailingContent = {
                        Icon(when (rec.source) { Source.NAHRAVKA -> Icons.Filled.Mic; Source.IMPORT -> Icons.Filled.AudioFile; Source.HLIDANI -> Icons.Filled.Shield }, null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    },
                    colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface),
                )
            }
        }
    }
}

// ---------------- Statistiky ----------------

@Composable
private fun StatsTab(recordings: List<Recording>) {
    val training = recordings.filter { it.isTrainingSample }
    val model = remember(recordings) { Interpreter.buildModel(recordings) }
    val accuracy = remember(model) { model.leaveOneOutAccuracy() }
    val baseline = Interpreter.baselinePitch(recordings)
    val bySituation = training.groupBy { it.situation!! }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatTile("Nahrávek", "${recordings.size}", Modifier.weight(1f), Icons.Filled.Mic)
            StatTile("Označených", "${training.size}", Modifier.weight(1f), Icons.Filled.Verified)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatTile("Trefa modelu", accuracy?.let { "${(it * 100).toInt()} %" } ?: "málo dat", Modifier.weight(1f), Icons.Filled.School)
            StatTile("Jeho normální tón", if (baseline != Interpreter.DEFAULT_PITCH) "${baseline.toInt()} Hz" else "zatím neznám", Modifier.weight(1f), Icons.Filled.Timer)
        }
        if (accuracy != null) Text(
            "Trefa = kolik označených nahrávek by appka uhodla, kdyby je předtím neslyšela. Náhoda by dala ${100 / maxOf(2, model.classCount)} %.",
            color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium,
        )

        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Jak zní v jednotlivých situacích", style = MaterialTheme.typography.titleMedium)
                if (bySituation.isEmpty()) Text("Až označíš pár nahrávek, uvidíš tady jeho „slovník“.")
                Situation.entries.forEach { s ->
                    val list = bySituation[s].orEmpty()
                    val a = list.mapNotNull { it.analysis }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(s.icon, null, tint = if (list.isEmpty()) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text("${s.label} (${list.size})", fontWeight = FontWeight.SemiBold)
                            if (a.isNotEmpty()) {
                                val pitch = Dsp.median(a.map { it.meanPitchHz }.filter { it > 0f })
                                val ton = Dsp.mean(a.map { it.meanTonality })
                                val rate = Dsp.mean(a.filter { it.barks.size >= 2 }.map { it.barkRate })
                                Text(
                                    (if (pitch > 0f) "${pitch.toInt()} Hz · " else "") +
                                        "chraplavost ${((1 - ton) * 100).toInt()} %" +
                                        (if (rate > 0f) " · %.1f štěk/s".format(rate) else ""),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            } else Text("chybí nahrávky", color = MaterialTheme.colorScheme.outline)
                        }
                    }
                }
            }
        }

        val missing = Situation.entries.filter { (bySituation[it]?.size ?: 0) < 5 }
        if (missing.isNotEmpty()) Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
            Column(Modifier.padding(16.dp)) {
                Text("Co nahrát příště", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(6.dp))
                Text("Méně než 5 nahrávek má: " + missing.joinToString(", ") { it.label.lowercase() } + ".")
            }
        }
    }
}
