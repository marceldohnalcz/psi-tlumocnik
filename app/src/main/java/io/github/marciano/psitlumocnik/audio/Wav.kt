package io.github.marciano.psitlumocnik.audio

import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

const val SAMPLE_RATE = 16_000

/** Zápis a čtení WAV (PCM 16 bit, mono). */
object Wav {

    fun write(file: File, samples: FloatArray, sampleRate: Int = SAMPLE_RATE) {
        val data = ByteBuffer.allocate(samples.size * 2).order(ByteOrder.LITTLE_ENDIAN)
        for (s in samples) data.putShort((s.coerceIn(-1f, 1f) * 32767f).toInt().toShort())
        file.outputStream().use { out ->
            out.write(header(samples.size * 2, sampleRate))
            out.write(data.array())
        }
    }

    fun header(dataBytes: Int, sampleRate: Int = SAMPLE_RATE): ByteArray =
        ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN).apply {
            put("RIFF".toByteArray()); putInt(36 + dataBytes); put("WAVE".toByteArray())
            put("fmt ".toByteArray()); putInt(16); putShort(1); putShort(1)
            putInt(sampleRate); putInt(sampleRate * 2); putShort(2); putShort(16)
            put("data".toByteArray()); putInt(dataBytes)
        }.array()

    /** Opraví velikosti v hlavičce po streamovaném zápisu. */
    fun finalize(file: File) {
        RandomAccessFile(file, "rw").use { raf ->
            val dataBytes = (raf.length() - 44).toInt()
            raf.seek(4); raf.write(le32(36 + dataBytes))
            raf.seek(40); raf.write(le32(dataBytes))
        }
    }

    private fun le32(v: Int) = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(v).array()

    /** Načte WAV, který zapsala tato aplikace (případně obecný PCM16 WAV). */
    fun read(file: File): FloatArray {
        val bytes = file.readBytes()
        val bb = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        var pos = 12
        var channels = 1
        var rate = SAMPLE_RATE
        var dataStart = 44
        var dataLen = bytes.size - 44
        while (pos + 8 <= bytes.size) {
            val id = String(bytes, pos, 4)
            val len = bb.getInt(pos + 4)
            if (id == "fmt ") {
                channels = bb.getShort(pos + 10).toInt()
                rate = bb.getInt(pos + 12)
            } else if (id == "data") {
                dataStart = pos + 8
                dataLen = minOf(len, bytes.size - dataStart)
                break
            }
            pos += 8 + len + (len and 1)
        }
        val frames = dataLen / (2 * channels)
        val out = FloatArray(frames)
        for (i in 0 until frames) {
            var sum = 0f
            for (c in 0 until channels) sum += bb.getShort(dataStart + (i * channels + c) * 2) / 32768f
            out[i] = sum / channels
        }
        return if (rate == SAMPLE_RATE) out else Resampler.resample(out, rate, SAMPLE_RATE)
    }
}

object Resampler {
    /** Lineární převzorkování s jednoduchým průměrováním při zmenšování (stačí pro analýzu štěkotu). */
    fun resample(input: FloatArray, fromRate: Int, toRate: Int): FloatArray {
        if (fromRate == toRate || input.isEmpty()) return input
        val ratio = fromRate.toDouble() / toRate
        val outLen = (input.size / ratio).toInt()
        val out = FloatArray(outLen)
        if (ratio > 1.0) {
            // Zmenšení: průměr přes okno = hrubý antialiasing
            for (i in 0 until outLen) {
                val start = (i * ratio).toInt()
                val end = minOf(((i + 1) * ratio).toInt(), input.size).coerceAtLeast(start + 1)
                var s = 0f
                for (j in start until end) s += input[j]
                out[i] = s / (end - start)
            }
        } else {
            for (i in 0 until outLen) {
                val x = i * ratio
                val i0 = x.toInt()
                val i1 = minOf(i0 + 1, input.size - 1)
                val f = (x - i0).toFloat()
                out[i] = input[i0] * (1 - f) + input[i1] * f
            }
        }
        return out
    }
}
