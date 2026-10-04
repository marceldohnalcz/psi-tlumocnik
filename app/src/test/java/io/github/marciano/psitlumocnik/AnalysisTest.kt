package io.github.marciano.psitlumocnik

import io.github.marciano.psitlumocnik.analysis.BarkAnalysis
import io.github.marciano.psitlumocnik.analysis.Dsp
import io.github.marciano.psitlumocnik.analysis.Interpreter
import io.github.marciano.psitlumocnik.data.Analysis
import io.github.marciano.psitlumocnik.data.Recording
import io.github.marciano.psitlumocnik.data.Situation
import io.github.marciano.psitlumocnik.data.Source
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

class AnalysisTest {

    private val sr = 16_000

    private fun tone(f: Float, ms: Int, amp: Float = 0.5f, noise: Float = 0f, rnd: Random = Random(1)) =
        FloatArray(sr * ms / 1000) { i ->
            val t = i.toFloat() / sr
            // harmonický signál jako zjednodušený štěk
            amp * (0.6f * sin(2 * PI * f * t).toFloat() + 0.3f * sin(4 * PI * f * t).toFloat() + 0.1f * sin(6 * PI * f * t).toFloat()) +
                noise * (rnd.nextFloat() * 2 - 1)
        }

    private fun silence(ms: Int, rnd: Random = Random(2)) = FloatArray(sr * ms / 1000) { (rnd.nextFloat() * 2 - 1) * 0.002f }

    /** Série štěků: výška, délka štěku, pauza, šum. */
    private fun series(f: Float, n: Int, barkMs: Int, gapMs: Int, noise: Float = 0f): FloatArray {
        val parts = mutableListOf(silence(500))
        repeat(n) { parts += tone(f, barkMs, noise = noise, rnd = Random(it)); parts += silence(gapMs) }
        parts += silence(500)
        return parts.fold(FloatArray(0)) { acc, a -> acc + a }
    }

    @Test fun yinFindsPitch() {
        val y = Dsp.yin(tone(440f, 40), sr)
        assertEquals(440f, y.pitchHz, 8f)
        assertTrue("čistý tón má nízkou neperiodicitu", y.aperiodicity < 0.15f)
    }

    @Test fun noiseIsAperiodic() {
        val rnd = Random(3)
        val y = Dsp.yin(FloatArray(640) { rnd.nextFloat() * 2 - 1 }, sr)
        assertTrue("šum má vysokou neperiodicitu (${y.aperiodicity})", y.aperiodicity > 0.3f)
    }

    @Test fun detectsBarksAndRhythm() {
        val x = series(500f, 4, 150, 350)
        val ranges = BarkAnalysis.detect(x, sr)
        assertEquals(4, ranges.size)
        val barks = ranges.map { BarkAnalysis.features(x, it, sr) }
        barks.forEach { assertEquals(500f, it.pitchHz, 15f) }
        val s = BarkAnalysis.summarize(barks)
        assertEquals(2f, s.barkRate, 0.25f) // štěk každých 500 ms
        assertTrue(s.intervalCv < 0.1f)
        assertTrue(s.meanTonality > 0.7f)
    }

    @Test fun harshBarkIsLessTonal() {
        val clean = BarkAnalysis.summarize(series(400f, 3, 150, 300).let { x -> BarkAnalysis.detect(x, sr).map { BarkAnalysis.features(x, it, sr) } })
        val harsh = BarkAnalysis.summarize(series(400f, 3, 150, 300, noise = 0.6f).let { x -> BarkAnalysis.detect(x, sr).map { BarkAnalysis.features(x, it, sr) } })
        assertTrue("chraplavý ${harsh.meanTonality} < čistý ${clean.meanTonality}", harsh.meanTonality < clean.meanTonality)
    }

    private fun analysisOf(x: FloatArray): Analysis {
        val barks = BarkAnalysis.detect(x, sr).map { BarkAnalysis.features(x, it, sr) }
        val s = BarkAnalysis.summarize(barks)
        return Analysis(x.size * 1000 / sr, barks, s.barkRate, s.meanIntervalMs, s.intervalCv, s.meanPitchHz, s.meanTonality,
            s.meanCentroidHz, s.meanFlatness, s.meanDurationMs, s.meanLoudnessDb, 0f, emptyList(), emptyList(), emptyList())
    }

    @Test fun learnsTwoSituations() {
        // „Cizí“: hluboký, chraplavý, rychlý. „Samota“: vysoký, čistý, pomalý.
        val recs = (0 until 6).flatMap { i ->
            listOf(
                Recording("c$i", "d", 0, 0, Source.NAHRAVKA, Situation.CIZI, analysis = analysisOf(series(300f + i * 10, 6, 120, 150, noise = 0.4f))),
                Recording("s$i", "d", 0, 0, Source.NAHRAVKA, Situation.SAMOTA, analysis = analysisOf(series(800f + i * 10, 3, 250, 900))),
            )
        }
        val model = Interpreter.buildModel(recs)
        assertEquals(1f, model.leaveOneOutAccuracy()!!, 0.01f)
        val newCizi = analysisOf(series(320f, 5, 120, 160, noise = 0.4f))
        assertEquals(Situation.CIZI, Interpreter.interpret(newCizi, recs).top)
        val newSamota = analysisOf(series(820f, 3, 260, 950))
        assertEquals(Situation.SAMOTA, Interpreter.interpret(newSamota, recs).top)
    }
}
