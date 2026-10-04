package io.github.marciano.psitlumocnik.audio

import android.media.MediaPlayer
import java.io.File

/** Minimalistický přehrávač jedné nahrávky. */
class Player {
    private var mp: MediaPlayer? = null

    val isPlaying: Boolean get() = mp?.isPlaying == true
    val positionMs: Int get() = runCatching { mp?.currentPosition ?: 0 }.getOrDefault(0)

    fun play(file: File, fromMs: Int = 0, onDone: () -> Unit) {
        stop()
        mp = MediaPlayer().apply {
            setDataSource(file.absolutePath)
            setOnCompletionListener { onDone(); stop() }
            prepare()
            if (fromMs > 0) seekTo(fromMs)
            start()
        }
    }

    fun stop() {
        mp?.let { runCatching { it.stop() }; it.release() }
        mp = null
    }
}
