package io.github.marciano.psitlumocnik.ui

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.marciano.psitlumocnik.PsiApp
import io.github.marciano.psitlumocnik.audio.AudioDecoder
import io.github.marciano.psitlumocnik.audio.FloatList
import io.github.marciano.psitlumocnik.audio.MicStream
import io.github.marciano.psitlumocnik.audio.SAMPLE_RATE
import io.github.marciano.psitlumocnik.audio.Wav
import io.github.marciano.psitlumocnik.data.AppSettings
import io.github.marciano.psitlumocnik.data.BodySignal
import io.github.marciano.psitlumocnik.data.Dog
import io.github.marciano.psitlumocnik.data.Recording
import io.github.marciano.psitlumocnik.data.Repository
import io.github.marciano.psitlumocnik.data.Situation
import io.github.marciano.psitlumocnik.data.Source
import io.github.marciano.psitlumocnik.service.WatchService
import io.github.marciano.psitlumocnik.transfer.ProfileTransfer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class RecordState(
    val recording: Boolean = false,
    val elapsedMs: Long = 0,
    val processing: String? = null,
)

sealed interface UiEvent {
    data class Message(val text: String) : UiEvent
    data class OpenRecording(val id: String) : UiEvent
    data class OpenDog(val id: String) : UiEvent
    data class Share(val intent: Intent) : UiEvent
}

class AppViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as PsiApp
    val repo: Repository = app.repository
    val store = repo.store
    val settings: StateFlow<AppSettings> = app.settings.state
    val watch = app.watchState
    val hasAi: Boolean get() = app.analyzer.hasAi
    private val transfer = ProfileTransfer(application, repo)

    private val _record = MutableStateFlow(RecordState())
    val record: StateFlow<RecordState> = _record.asStateFlow()
    private val mic = MicStream()
    val micLevel = mic.level
    private var recJob: Job? = null
    private var buffer = FloatList()

    private val _events = Channel<UiEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    /** Nahrávka sdílená z jiné aplikace, čeká na výběr psa. */
    val pendingShared = MutableStateFlow<Uri?>(null)

    private fun message(text: String) { _events.trySend(UiEvent.Message(text)) }

    // ---------- Psi ----------

    fun addDog(name: String, breed: String, note: String) = viewModelScope.launch {
        val dog = repo.addDog(name, breed, note)
        _events.send(UiEvent.OpenDog(dog.id))
    }

    fun updateDog(dog: Dog) = viewModelScope.launch { repo.updateDog(dog) }

    fun deleteDog(id: String) = viewModelScope.launch {
        if (watch.value.dogId == id) WatchService.stop(app)
        repo.deleteDog(id)
    }

    // ---------- Nahrávání ----------

    fun startRecording() {
        if (_record.value.recording) return
        buffer = FloatList()
        val started = System.currentTimeMillis()
        _record.value = RecordState(recording = true)
        recJob = viewModelScope.launch {
            launch { while (true) { delay(200); _record.value = _record.value.copy(elapsedMs = System.currentTimeMillis() - started) } }
            try {
                mic.run { block ->
                    synchronized(buffer) { buffer.addAll(block) }
                    if (buffer.size > SAMPLE_RATE * MAX_RECORD_S) recJob?.cancel()
                }
            } catch (e: Exception) {
                message("Mikrofon: ${e.message}")
            }
        }
    }

    fun stopRecording(dogId: String, discard: Boolean = false) {
        val job = recJob ?: return
        viewModelScope.launch {
            job.cancel(); job.join()
            recJob = null
            val samples = synchronized(buffer) { buffer.toArray() }
            _record.value = RecordState()
            if (discard) return@launch
            if (samples.size < SAMPLE_RATE / 2) { message("To bylo moc krátké. Nahrávej aspoň pár sekund."); return@launch }
            saveAndAnalyze(dogId, samples, Source.NAHRAVKA)
        }
    }

    fun importAudio(dogId: String, uri: Uri) = viewModelScope.launch {
        _record.value = _record.value.copy(processing = "Načítám soubor…")
        try {
            val samples = withContext(Dispatchers.IO) { AudioDecoder.decode(app, uri) }
            saveAndAnalyze(dogId, samples, Source.IMPORT)
        } catch (e: Exception) {
            _record.value = RecordState()
            message("Soubor nejde přečíst: ${e.message ?: "neznámý formát"}")
        }
    }

    private suspend fun saveAndAnalyze(dogId: String, samples: FloatArray, source: Source) {
        _record.value = _record.value.copy(processing = "Poslouchám a počítám…")
        try {
            val analysis = app.analyzer.analyze(samples, settings.value.sensitivity)
            val id = Repository.newId()
            withContext(Dispatchers.IO) { Wav.write(repo.audioFile(id), samples) }
            repo.addRecording(
                Recording(id = id, dogId = dogId, createdAt = System.currentTimeMillis(), durationMs = analysis.durationMs, source = source, analysis = analysis)
            )
            _events.send(UiEvent.OpenRecording(id))
        } catch (e: Exception) {
            message("Rozbor selhal: ${e.message}")
        } finally {
            _record.value = RecordState()
        }
    }

    // ---------- Úpravy nahrávek ----------

    fun label(rec: Recording, situation: Situation?, signals: Set<BodySignal>, note: String) = viewModelScope.launch {
        repo.updateRecording(rec.copy(situation = situation, bodySignals = signals, note = note.trim(), autoLabeled = false))
        message(if (situation != null) "Uloženo. Zase o kousek chytřejší." else "Uloženo")
    }

    fun deleteRecording(id: String) = viewModelScope.launch { repo.deleteRecording(id) }

    fun reanalyze(rec: Recording) = viewModelScope.launch {
        _record.value = _record.value.copy(processing = "Znovu rozebírám…")
        try {
            val samples = withContext(Dispatchers.IO) { Wav.read(repo.audioFile(rec.id)) }
            val a = app.analyzer.analyze(samples, settings.value.sensitivity)
            repo.updateRecording(rec.copy(analysis = a, durationMs = a.durationMs))
        } catch (e: Exception) {
            message("Nepodařilo se: ${e.message}")
        } finally {
            _record.value = RecordState()
        }
    }

    /** Přepočítá všechny nahrávky psa (po změně citlivosti nebo po aktualizaci analyzátoru). */
    fun reanalyzeAll(dogId: String) = viewModelScope.launch {
        val recs = repo.recordingsOf(dogId)
        recs.forEachIndexed { i, rec ->
            _record.value = _record.value.copy(processing = "Přepočítávám ${i + 1}/${recs.size}…")
            runCatching {
                val samples = withContext(Dispatchers.IO) { Wav.read(repo.audioFile(rec.id)) }
                val a = app.analyzer.analyze(samples, settings.value.sensitivity)
                repo.updateRecording(rec.copy(analysis = a, durationMs = a.durationMs))
            }
        }
        _record.value = RecordState()
        message("Hotovo, přepočítáno ${recs.size} nahrávek")
    }

    // ---------- Hlídání ----------

    fun startWatch(dogId: String) = WatchService.start(app, dogId)
    fun stopWatch() = WatchService.stop(app)

    // ---------- Export / import ----------

    fun shareProfile(dogId: String) = viewModelScope.launch {
        try {
            val file = transfer.export(dogId, settings.value.exportAudio)
            val uri = FileProvider.getUriForFile(app, "${app.packageName}.files", file)
            val send = Intent(Intent.ACTION_SEND).apply {
                type = "application/octet-stream"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            _events.send(UiEvent.Share(Intent.createChooser(send, "Poslat profil psa")))
        } catch (e: Exception) {
            message("Export selhal: ${e.message}")
        }
    }

    fun saveProfile(dogId: String, target: Uri) = viewModelScope.launch {
        try {
            val file = transfer.export(dogId, settings.value.exportAudio)
            withContext(Dispatchers.IO) {
                app.contentResolver.openOutputStream(target)!!.use { out -> file.inputStream().use { it.copyTo(out) } }
            }
            message("Profil uložen")
        } catch (e: Exception) {
            message("Uložení selhalo: ${e.message}")
        }
    }

    fun importProfile(uri: Uri) = viewModelScope.launch {
        _record.value = _record.value.copy(processing = "Importuji profil…")
        try {
            val dog = transfer.import(uri)
            message("Vítej, ${dog.name}!")
            _events.send(UiEvent.OpenDog(dog.id))
        } catch (e: Exception) {
            message("Import selhal: ${e.message}")
        } finally {
            _record.value = RecordState()
        }
    }

    // ---------- Nastavení ----------

    fun updateSettings(transform: (AppSettings) -> AppSettings) = app.settings.update(transform)

    companion object {
        const val MAX_RECORD_S = 300
    }
}
