package io.github.marciano.psitlumocnik.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class ThemeMode(val label: String) { SYSTEM("Podle systému"), LIGHT("Světlý"), DARK("Tmavý") }

data class AppSettings(
    val theme: ThemeMode = ThemeMode.SYSTEM,
    /** Citlivost detekce štěkotu 1–5 (3 = výchozí). */
    val sensitivity: Int = 3,
    /** Nahrávky z hlídání rovnou předvyplnit jako „Samota“. */
    val watchAutoLabel: Boolean = true,
    /** Při exportu profilu přibalit i zvukové soubory. */
    val exportAudio: Boolean = true,
)

class SettingsStore(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
    private val _state = MutableStateFlow(read())
    val state: StateFlow<AppSettings> = _state.asStateFlow()

    private fun read() = AppSettings(
        theme = runCatching { ThemeMode.valueOf(prefs.getString("theme", ThemeMode.SYSTEM.name)!!) }.getOrDefault(ThemeMode.SYSTEM),
        sensitivity = prefs.getInt("sensitivity", 3).coerceIn(1, 5),
        watchAutoLabel = prefs.getBoolean("watchAutoLabel", true),
        exportAudio = prefs.getBoolean("exportAudio", true),
    )

    fun update(transform: (AppSettings) -> AppSettings) {
        val s = transform(_state.value)
        prefs.edit()
            .putString("theme", s.theme.name)
            .putInt("sensitivity", s.sensitivity)
            .putBoolean("watchAutoLabel", s.watchAutoLabel)
            .putBoolean("exportAudio", s.exportAudio)
            .apply()
        _state.value = s
    }
}
