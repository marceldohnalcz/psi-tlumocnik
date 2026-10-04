package io.github.marciano.psitlumocnik.transfer

import android.content.Context
import android.net.Uri
import io.github.marciano.psitlumocnik.data.Dog
import io.github.marciano.psitlumocnik.data.Recording
import io.github.marciano.psitlumocnik.data.Repository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import java.io.File
import java.text.Normalizer
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

@Serializable
private data class ProfileFile(
    val format: Int = 1,
    val app: String = "psi-tlumocnik",
    val exportedAt: Long = System.currentTimeMillis(),
    val dog: Dog,
    val recordings: List<Recording>,
)

/**
 * Profil psa jako jeden soubor .psi (ZIP): profile.json + volitelně audio/<id>.wav.
 * Kámoš si ho importuje a má psa i se vším, co se appka o jeho hlasu naučila.
 */
class ProfileTransfer(private val context: Context, private val repo: Repository) {

    suspend fun export(dogId: String, includeAudio: Boolean): File = withContext(Dispatchers.IO) {
        val dog = repo.dog(dogId) ?: error("Pes nenalezen")
        val recs = repo.recordingsOf(dogId)
        val dir = File(context.cacheDir, "export").apply { mkdirs(); listFiles()?.forEach { it.delete() } }
        val out = File(dir, "${slug(dog.name)}.psi")
        ZipOutputStream(out.outputStream().buffered()).use { zip ->
            zip.putNextEntry(ZipEntry("profile.json"))
            zip.write(Repository.json.encodeToString(ProfileFile.serializer(), ProfileFile(dog = dog, recordings = recs)).toByteArray())
            zip.closeEntry()
            if (includeAudio) for (r in recs) {
                val f = repo.audioFile(r.id)
                if (!f.exists()) continue
                zip.putNextEntry(ZipEntry("audio/${r.id}.wav"))
                f.inputStream().use { it.copyTo(zip) }
                zip.closeEntry()
            }
        }
        out
    }

    /** Importuje profil jako nového psa (nová ID, takže se nic nepřepíše). Vrací importovaného psa. */
    suspend fun import(uri: Uri): Dog = withContext(Dispatchers.IO) {
        val tmpAudio = File(context.cacheDir, "import").apply { deleteRecursively(); mkdirs() }
        var profile: ProfileFile? = null
        context.contentResolver.openInputStream(uri)!!.use { input ->
            ZipInputStream(input.buffered()).use { zip ->
                while (true) {
                    val e = zip.nextEntry ?: break
                    when {
                        e.name == "profile.json" ->
                            profile = Repository.json.decodeFromString(ProfileFile.serializer(), zip.readBytes().decodeToString())
                        e.name.startsWith("audio/") && e.name.endsWith(".wav") && !e.name.contains("..") ->
                            File(tmpAudio, e.name.substringAfter("audio/")).outputStream().use { zip.copyTo(it) }
                    }
                }
            }
        }
        val p = profile ?: error("Tohle není profil Psího tlumočníka")
        val existingNames = repo.store.value.dogs.map { it.name }.toSet()
        val name = if (p.dog.name in existingNames) "${p.dog.name} (import)" else p.dog.name
        val dog = p.dog.copy(id = Repository.newId(), name = name)
        val recs = p.recordings.map { r ->
            val newId = Repository.newId()
            File(tmpAudio, "${r.id}.wav").takeIf { it.exists() }?.let { src ->
                val dst = repo.audioFile(newId)
                if (!src.renameTo(dst)) src.copyTo(dst, overwrite = true)
            }
            r.copy(id = newId, dogId = dog.id)
        }
        repo.importAll(dog, recs)
        tmpAudio.deleteRecursively()
        dog
    }

    private fun slug(s: String): String =
        Normalizer.normalize(s, Normalizer.Form.NFD).replace("\\p{M}".toRegex(), "")
            .replace("[^A-Za-z0-9]+".toRegex(), "-").trim('-').lowercase().ifBlank { "pes" }
}
