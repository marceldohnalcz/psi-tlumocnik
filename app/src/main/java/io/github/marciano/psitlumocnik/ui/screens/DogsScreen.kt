package io.github.marciano.psitlumocnik.ui.screens

import android.net.Uri
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.Pets
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.marciano.psitlumocnik.data.Dog
import io.github.marciano.psitlumocnik.ui.AppViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DogsScreen(vm: AppViewModel, snackbar: SnackbarHostState, onOpenDog: (String) -> Unit, onSettings: () -> Unit) {
    val store by vm.store.collectAsState()
    val watch by vm.watch.collectAsState()
    val shared by vm.pendingShared.collectAsState()
    val record by vm.record.collectAsState()
    var showAdd by rememberSaveable { mutableStateOf(false) }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        uri?.let(vm::importProfile)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Psí tlumočník") },
                actions = {
                    TextButton(onClick = onSettings) {
                        Icon(Icons.Filled.Settings, null); Spacer(Modifier.width(6.dp)); Text("Nastavení")
                    }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(onClick = { showAdd = true }, icon = { Icon(Icons.Filled.Add, null) }, text = { Text("Přidat psa") })
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { pad ->
        if (store.dogs.isEmpty()) {
            Column(
                Modifier.fillMaxSize().padding(pad).padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Icon(Icons.Filled.Pets, null, Modifier.size(72.dp), tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(16.dp))
                Text("Zatím tu není žádný pes", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(8.dp))
                Text(
                    "Přidej svého psa a začni nahrávat. Čím víc nahrávek označíš, tím líp ho appka pozná.",
                    textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(20.dp))
                OutlinedButton(onClick = { importLauncher.launch(arrayOf("*/*")) }) {
                    Icon(Icons.Filled.FileOpen, null); Spacer(Modifier.width(8.dp)); Text("Importovat profil od kámoše")
                }
            }
        } else {
            LazyColumn(
                Modifier.fillMaxSize().padding(pad),
                contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 96.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(store.dogs, key = { it.id }) { dog ->
                    val recs = store.recordings.filter { it.dogId == dog.id }
                    DogCard(dog, recs.size, recs.count { it.isTrainingSample }, watch.running && watch.dogId == dog.id) { onOpenDog(dog.id) }
                }
                item {
                    OutlinedButton(onClick = { importLauncher.launch(arrayOf("*/*")) }, Modifier.fillMaxWidth()) {
                        Icon(Icons.Filled.FileOpen, null); Spacer(Modifier.width(8.dp)); Text("Importovat profil psa")
                    }
                }
            }
        }
    }

    if (showAdd) DogDialog(null, onDismiss = { showAdd = false }) { name, breed, note ->
        vm.addDog(name, breed, note); showAdd = false
    }

    shared?.let { uri ->
        AlertDialog(
            onDismissRequest = { vm.pendingShared.value = null },
            title = { Text("Ke kterému psovi?") },
            text = {
                if (store.dogs.isEmpty()) Text("Nejdřív přidej psa, pak nahrávku pošli znovu.")
                else Column {
                    store.dogs.forEach { d ->
                        TextButton(onClick = { vm.pendingShared.value = null; vm.importAudio(d.id, uri) }, Modifier.fillMaxWidth()) {
                            Icon(Icons.Filled.Pets, null); Spacer(Modifier.width(8.dp)); Text(d.name, Modifier.weight(1f))
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { vm.pendingShared.value = null }) { Text("Zrušit") } },
        )
    }
    record.processing?.let { ProcessingDialog(it) }
}

@Composable
private fun DogCard(dog: Dog, total: Int, labeled: Int, watching: Boolean, onClick: () -> Unit) {
    Card(
        Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.size(56.dp)) {
                Column(verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(dog.name.take(1).uppercase(), style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
                }
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(dog.name, style = MaterialTheme.typography.titleLarge)
                if (dog.breed.isNotBlank()) Text(dog.breed, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(4.dp))
                Text("Nahrávek: $total · označených: $labeled", style = MaterialTheme.typography.bodyMedium)
            }
            if (watching) AssistChip(
                onClick = onClick, label = { Text("Hlídám") },
                leadingIcon = { Icon(Icons.Filled.Shield, null, Modifier.size(18.dp)) },
                colors = AssistChipDefaults.assistChipColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
            )
        }
    }
}

@Composable
fun DogDialog(dog: Dog?, onDismiss: () -> Unit, onSave: (String, String, String) -> Unit) {
    var name by remember { mutableStateOf(dog?.name ?: "") }
    var breed by remember { mutableStateOf(dog?.breed ?: "") }
    var note by remember { mutableStateOf(dog?.note ?: "") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (dog == null) "Nový pes" else "Upravit psa") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("Jméno") }, singleLine = true)
                OutlinedTextField(breed, { breed = it }, label = { Text("Plemeno / velikost") }, singleLine = true)
                OutlinedTextField(note, { note = it }, label = { Text("Poznámka (věk, povaha…)") })
            }
        },
        confirmButton = { TextButton(onClick = { onSave(name, breed, note) }, enabled = name.isNotBlank()) { Text("Uložit") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Zrušit") } },
    )
}

@Composable
fun ProcessingDialog(text: String) {
    AlertDialog(
        onDismissRequest = {},
        confirmButton = {},
        title = { Text("Moment…") },
        text = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                androidx.compose.material3.CircularProgressIndicator(Modifier.size(28.dp))
                Spacer(Modifier.width(16.dp))
                Text(text)
            }
        },
    )
}
