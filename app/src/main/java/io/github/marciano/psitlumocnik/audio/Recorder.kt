package io.github.marciano.psitlumocnik.audio

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext
import kotlin.math.log10
import kotlin.math.sqrt

/** Mikrofon → float vzorky 16 kHz. Používá ho ruční nahrávání i hlídání. */
class MicStream {

    private val _level = MutableStateFlow(0f)
    /** Aktuální hlasitost 0..1 pro ukazatel. */
    val level: StateFlow<Float> = _level.asStateFlow()

    /**
     * Čte z mikrofonu, dokud běží korutina. Každý blok (100 ms) předá do [onBlock].
     * Pro hlasité psy bere UNPROCESSED zdroj, pokud ho zařízení umí (bez AGC a potlačení šumu).
     */
    @SuppressLint("MissingPermission")
    suspend fun run(onBlock: (FloatArray) -> Unit) = withContext(Dispatchers.IO) {
        val minBuf = AudioRecord.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val block = SAMPLE_RATE / 10
        val record = createRecord(MediaRecorder.AudioSource.UNPROCESSED, minBuf, block)
            ?: createRecord(MediaRecorder.AudioSource.MIC, minBuf, block)
            ?: throw IllegalStateException("Mikrofon se nepodařilo otevřít")
        val shorts = ShortArray(block)
        try {
            record.startRecording()
            while (coroutineContext.isActive) {
                val n = record.read(shorts, 0, block)
                if (n <= 0) continue
                val floats = FloatArray(n) { shorts[it] / 32768f }
                var sum = 0.0
                for (f in floats) sum += f * f
                val db = 20 * log10(sqrt(sum / n).coerceAtLeast(1e-6))
                _level.value = ((db + 60) / 60).toFloat().coerceIn(0f, 1f)
                onBlock(floats)
            }
        } finally {
            runCatching { record.stop() }
            record.release()
            _level.value = 0f
        }
    }

    @SuppressLint("MissingPermission")
    private fun createRecord(source: Int, minBuf: Int, block: Int): AudioRecord? = runCatching {
        val r = AudioRecord(source, SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, maxOf(minBuf, block * 4))
        if (r.state == AudioRecord.STATE_INITIALIZED) r else { r.release(); null }
    }.getOrNull()
}
