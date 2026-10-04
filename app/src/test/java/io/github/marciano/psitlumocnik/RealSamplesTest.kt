package io.github.marciano.psitlumocnik

import io.github.marciano.psitlumocnik.analysis.BarkAnalysis
import io.github.marciano.psitlumocnik.audio.SAMPLE_RATE
import io.github.marciano.psitlumocnik.audio.Wav
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/** Spustí rozbor nad skutečnými nahrávkami, pokud je nastavená proměnná SAMPLES_DIR (jinak se přeskočí). */
class RealSamplesTest {
    @Test fun analyzeRealBarks() {
        val dir = System.getenv("SAMPLES_DIR")?.let(::File)
        assumeTrue(dir != null && dir.isDirectory)
        dir!!.listFiles { f -> f.extension == "wav" }!!.sorted().forEach { f ->
            val x = Wav.read(f)
            val barks = BarkAnalysis.detect(x, SAMPLE_RATE).map { BarkAnalysis.features(x, it, SAMPLE_RATE) }
            val s = BarkAnalysis.summarize(barks)
            println("${f.name}: ${x.size / SAMPLE_RATE}s, štěků=${barks.size}, F0=${s.meanPitchHz.toInt()} Hz, čistota=${"%.2f".format(s.meanTonality)}, tempo=${"%.2f".format(s.barkRate)}/s, cv=${"%.2f".format(s.intervalCv)}, délka=${s.meanDurationMs.toInt()} ms")
            barks.forEach { b -> println("   ${b.startMs} ms +${b.durationMs} ms  F0=${b.pitchHz.toInt()}  ton=${"%.2f".format(b.tonality)}  cent=${b.centroidHz.toInt()}") }
        }
    }
}
