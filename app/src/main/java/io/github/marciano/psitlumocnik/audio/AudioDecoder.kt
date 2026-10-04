package io.github.marciano.psitlumocnik.audio

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import java.nio.ByteOrder

/**
 * Dekóduje libovolný zvukový soubor podporovaný Androidem (mp3, m4a, ogg, opus, wav, amr…)
 * na mono 16 kHz float pole.
 */
object AudioDecoder {

    /** Maximální délka importu – delší nahrávky se oříznou. */
    const val MAX_SECONDS = 600

    fun decode(context: Context, uri: Uri): FloatArray {
        val extractor = MediaExtractor()
        extractor.setDataSource(context, uri, null)
        val trackIndex = (0 until extractor.trackCount).firstOrNull {
            extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
        } ?: throw IllegalArgumentException("Soubor neobsahuje zvukovou stopu")
        extractor.selectTrack(trackIndex)
        val format = extractor.getTrackFormat(trackIndex)
        val mime = format.getString(MediaFormat.KEY_MIME)!!
        var sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
        var channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)

        val codec = MediaCodec.createDecoderByType(mime)
        codec.configure(format, null, null, 0)
        codec.start()

        val pcm = FloatList()
        val info = MediaCodec.BufferInfo()
        var inputDone = false
        var outputDone = false
        var pcmEncoding = 2 // AudioFormat.ENCODING_PCM_16BIT
        val maxFrames = MAX_SECONDS.toLong() * sampleRate

        try {
            while (!outputDone) {
                if (!inputDone) {
                    val inIdx = codec.dequeueInputBuffer(10_000)
                    if (inIdx >= 0) {
                        val buf = codec.getInputBuffer(inIdx)!!
                        val size = extractor.readSampleData(buf, 0)
                        if (size < 0) {
                            codec.queueInputBuffer(inIdx, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                        } else {
                            codec.queueInputBuffer(inIdx, 0, size, extractor.sampleTime, 0)
                            extractor.advance()
                        }
                    }
                }
                val outIdx = codec.dequeueOutputBuffer(info, 10_000)
                when {
                    outIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        val f = codec.outputFormat
                        sampleRate = f.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                        channels = f.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                        if (f.containsKey(MediaFormat.KEY_PCM_ENCODING)) pcmEncoding = f.getInteger(MediaFormat.KEY_PCM_ENCODING)
                    }
                    outIdx >= 0 -> {
                        val buf = codec.getOutputBuffer(outIdx)!!.order(ByteOrder.LITTLE_ENDIAN)
                        buf.position(info.offset); buf.limit(info.offset + info.size)
                        if (pcmEncoding == 4 /* PCM_FLOAT */) {
                            val fb = buf.asFloatBuffer()
                            while (fb.remaining() >= channels) {
                                var s = 0f
                                repeat(channels) { s += fb.get() }
                                pcm.add(s / channels)
                            }
                        } else {
                            val sb = buf.asShortBuffer()
                            while (sb.remaining() >= channels) {
                                var s = 0f
                                repeat(channels) { s += sb.get() / 32768f }
                                pcm.add(s / channels)
                            }
                        }
                        codec.releaseOutputBuffer(outIdx, false)
                        if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0 || pcm.size > maxFrames) outputDone = true
                    }
                }
            }
        } finally {
            runCatching { codec.stop() }
            codec.release()
            extractor.release()
        }
        return Resampler.resample(pcm.toArray(), sampleRate, SAMPLE_RATE)
    }
}

/** Rostoucí pole floatů bez boxování. */
class FloatList(initial: Int = 1 shl 16) {
    private var data = FloatArray(initial)
    var size = 0; private set
    fun add(v: Float) {
        if (size == data.size) data = data.copyOf(data.size * 2)
        data[size++] = v
    }
    fun addAll(src: FloatArray, count: Int = src.size) {
        while (size + count > data.size) data = data.copyOf(data.size * 2)
        System.arraycopy(src, 0, data, size, count); size += count
    }
    fun toArray(): FloatArray = data.copyOf(size)
}
