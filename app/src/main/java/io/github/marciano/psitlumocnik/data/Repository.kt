package io.github.marciano.psitlumocnik.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File
import java.util.UUID

/**
 * Jednoduché úložiště: metadata v jednom JSON souboru, zvuk jako WAV v adresáři audio/.
 * Pro stovky nahrávek je to bohatě dostačující a export profilu je pak triviální.
 */
class Repository(context: Context) {

    private val dataFile = File(context.filesDir, "data.json")
    val audioDir: File = File(context.filesDir, "audio").apply { mkdirs() }
    private val mutex = Mutex()

    private val _store = MutableStateFlow(load())
    val store: StateFlow<Store> = _store.asStateFlow()

    private fun load(): Store = runCatching {
        if (dataFile.exists()) json.decodeFromString(Store.serializer(), dataFile.readText()) else Store()
    }.getOrElse {
        // Poškozený soubor nezahazujeme, jen odložíme stranou.
        dataFile.copyTo(File(dataFile.parentFile, "data.broken.${System.currentTimeMillis()}.json"), overwrite = true)
        Store()
    }

    private suspend fun update(transform: (Store) -> Store) = mutex.withLock {
        val newStore = transform(_store.value)
        _store.value = newStore
        withContext(Dispatchers.IO) {
            val tmp = File(dataFile.parentFile, "data.json.tmp")
            tmp.writeText(json.encodeToString(Store.serializer(), newStore))
            if (!tmp.renameTo(dataFile)) {
                tmp.copyTo(dataFile, overwrite = true); tmp.delete()
            }
        }
    }

    fun audioFile(recordingId: String) = File(audioDir, "$recordingId.wav")

    fun dog(id: String): Dog? = _store.value.dogs.firstOrNull { it.id == id }
    fun recording(id: String): Recording? = _store.value.recordings.firstOrNull { it.id == id }
    fun recordingsOf(dogId: String): List<Recording> =
        _store.value.recordings.filter { it.dogId == dogId }.sortedByDescending { it.createdAt }

    suspend fun addDog(name: String, breed: String, note: String): Dog {
        val dog = Dog(newId(), name.trim(), breed.trim(), note.trim())
        update { it.copy(dogs = it.dogs + dog) }
        return dog
    }

    suspend fun updateDog(dog: Dog) = update { s -> s.copy(dogs = s.dogs.map { if (it.id == dog.id) dog else it }) }

    suspend fun deleteDog(dogId: String) {
        val toDelete = _store.value.recordings.filter { it.dogId == dogId }
        update { s -> s.copy(dogs = s.dogs.filterNot { it.id == dogId }, recordings = s.recordings.filterNot { it.dogId == dogId }) }
        withContext(Dispatchers.IO) { toDelete.forEach { audioFile(it.id).delete() } }
    }

    suspend fun addRecording(recording: Recording) = update { it.copy(recordings = it.recordings + recording) }

    suspend fun updateRecording(recording: Recording) =
        update { s -> s.copy(recordings = s.recordings.map { if (it.id == recording.id) recording else it }) }

    suspend fun deleteRecording(id: String) {
        update { s -> s.copy(recordings = s.recordings.filterNot { it.id == id }) }
        withContext(Dispatchers.IO) { audioFile(id).delete() }
    }

    /** Hromadné vložení (import profilu). */
    suspend fun importAll(dog: Dog, recordings: List<Recording>) =
        update { it.copy(dogs = it.dogs + dog, recordings = it.recordings + recordings) }

    companion object {
        val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; prettyPrint = false }
        fun newId(): String = UUID.randomUUID().toString()
    }
}
