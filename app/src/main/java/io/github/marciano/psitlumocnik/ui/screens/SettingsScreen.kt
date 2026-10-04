package io.github.marciano.psitlumocnik.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.BrightnessAuto
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import io.github.marciano.psitlumocnik.data.ThemeMode
import io.github.marciano.psitlumocnik.ui.AppViewModel
import io.github.marciano.psitlumocnik.ui.components.PillTab
import io.github.marciano.psitlumocnik.ui.components.PillTabs

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(vm: AppViewModel, onBack: () -> Unit) {
    val s by vm.settings.collectAsState()
    val context = LocalContext.current
    val version = runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: "?"

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Nastavení") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Zpět") } },
            )
        },
    ) { pad ->
        Column(
            Modifier.fillMaxSize().padding(pad).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Section("Vzhled") {
                PillTabs(
                    listOf(
                        PillTab("Systém", Icons.Filled.BrightnessAuto),
                        PillTab("Světlý", Icons.Filled.LightMode),
                        PillTab("Tmavý", Icons.Filled.DarkMode),
                    ),
                    ThemeMode.entries.indexOf(s.theme),
                    { i -> vm.updateSettings { it.copy(theme = ThemeMode.entries[i]) } },
                )
            }
            Section("Citlivost detekce štěkotu") {
                Text(
                    when (s.sensitivity) {
                        1 -> "1 – jen pořádné štěky, ideální do hlučného bytu"
                        2 -> "2 – spíš méně citlivá"
                        3 -> "3 – výchozí"
                        4 -> "4 – citlivější, chytne i tišší štěk"
                        else -> "5 – maximum, chytí i kňučení (a možná i lednici)"
                    }
                )
                Slider(
                    value = s.sensitivity.toFloat(), onValueChange = { v -> vm.updateSettings { it.copy(sensitivity = v.toInt()) } },
                    valueRange = 1f..5f, steps = 3,
                )
                Text("Po změně si u psa dej „Přepočítat všechny nahrávky“.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Section("Hlídání a sdílení") {
                SwitchRow("Nahrávky z hlídání předvyplnit jako „Samota“", s.watchAutoLabel) { v -> vm.updateSettings { it.copy(watchAutoLabel = v) } }
                SwitchRow("Do exportu profilu přibalit i zvuky", s.exportAudio) { v -> vm.updateSettings { it.copy(exportAudio = v) } }
                Text("Bez zvuků je soubor malý a kámoš dostane jen naučená data. Se zvuky jde všechno později přepočítat.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Section("O aplikaci") {
                Text("Psí tlumočník $version")
                Text(if (vm.hasAi) "AI model YAMNet: načten ✔" else "AI model YAMNet: nenačten ✘", color = if (vm.hasAi) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.error)
                Text(
                    "Appka měří výšku, chraplavost, délku a rytmus štěkotu a k tomu poslouchá model YAMNet (Google, Apache 2.0). " +
                        "Výklad vychází z poznatků etologů (Morton, Pongrácz, Molnár) a hlavně z toho, co ji naučíš na svém psovi. " +
                        "Všechno běží offline v mobilu, nic se nikam neposílá.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            content()
        }
    }
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f))
        Switch(checked, onChange)
    }
}
