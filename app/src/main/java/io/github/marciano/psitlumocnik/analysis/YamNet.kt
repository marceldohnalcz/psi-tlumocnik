package io.github.marciano.psitlumocnik.analysis

import android.content.Context
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel

/**
 * Google YAMNet (AudioSet, 521 tříd) přes LiteRT. Vstup 0,975 s mono 16 kHz, výstup skóre tříd.
 * Model je v assets/yamnet.tflite (Apache 2.0).
 */
class YamNet(context: Context) {

    private val interpreter: Interpreter
    val labels: List<String>

    init {
        val fd = context.assets.openFd(MODEL)
        val buffer: MappedByteBuffer = FileInputStream(fd.fileDescriptor).channel
            .map(FileChannel.MapMode.READ_ONLY, fd.startOffset, fd.declaredLength)
        interpreter = Interpreter(buffer, Interpreter.Options().setNumThreads(2))
        labels = context.assets.open(LABELS).bufferedReader().readLines().drop(1).map { line ->
            // index,mid,display_name (název může být v uvozovkách s čárkou)
            val name = line.split(",", limit = 3).getOrElse(2) { "" }
            name.trim().removeSurrounding("\"")
        }
    }

    /** Skóre 521 tříd pro jedno okno [WINDOW] vzorků (kratší se doplní nulami). */
    @Synchronized
    fun classify(window: FloatArray): FloatArray {
        val input = if (window.size == WINDOW) window else FloatArray(WINDOW).also {
            System.arraycopy(window, 0, it, 0, minOf(window.size, WINDOW))
        }
        val output = Array(1) { FloatArray(CLASSES) }
        interpreter.run(input, output)
        return output[0]
    }

    /** Nejvyšší skóre psích tříd. */
    fun dogScore(scores: FloatArray): Float = DOG_CLASSES.maxOf { scores[it] }

    fun close() = interpreter.close()

    companion object {
        const val MODEL = "yamnet.tflite"
        const val LABELS = "yamnet_class_map.csv"
        const val WINDOW = 15_600
        const val CLASSES = 521

        /** Dog, Bark, Yip, Howl, Bow-wow, Growling, Whimper (dog). */
        val DOG_CLASSES = intArrayOf(69, 70, 71, 72, 73, 74, 75)
        const val BARK = 70
        const val YIP = 71
        const val HOWL = 72
        const val GROWL = 74
        const val WHIMPER = 75

        /** České názvy pro třídy, které se u psa objevují nejčastěji. */
        private val CZ = mapOf(
            "Dog" to "Pes", "Bark" to "Štěkot", "Yip" to "Vyjeknutí", "Howl" to "Vytí",
            "Bow-wow" to "Haf-haf", "Growling" to "Vrčení", "Whimper (dog)" to "Kňučení",
            "Animal" to "Zvíře", "Domestic animals, pets" to "Domácí zvíře", "Speech" to "Řeč",
            "Silence" to "Ticho", "Music" to "Hudba", "Cat" to "Kočka", "Meow" to "Mňoukání",
            "Vehicle" to "Vozidlo", "Car" to "Auto", "Doorbell" to "Zvonek", "Knock" to "Klepání",
            "Television" to "Televize", "Inside, small room" to "Malá místnost", "Wind" to "Vítr",
            "Thunder" to "Hrom", "Fireworks" to "Ohňostroj", "Child speech, kid speaking" to "Dětská řeč",
            "Wolf" to "Vlk", "Livestock, farm animals, working animals" to "Hospodářská zvířata",
            "Canidae, dogs, wolves" to "Psovití", "Wild animals" to "Divoká zvířata",
            "Snort" to "Odfrknutí", "Breathing" to "Dech", "Squeak" to "Pištění", "Whine" to "Kňourání",
            "Crying, sobbing" to "Pláč", "Screaming" to "Křik", "Shout" to "Výkřik", "Yell" to "Řev",
        )

        fun czech(label: String): String = CZ[label] ?: label
    }
}
