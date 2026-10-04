package io.github.marciano.psitlumocnik.analysis

import android.content.Context
import android.util.Log
import io.github.marciano.psitlumocnik.audio.SAMPLE_RATE
import io.github.marciano.psitlumocnik.data.Analysis
import io.github.marciano.psitlumocnik.data.LabelScore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Obecné třídy YAMNet, které nic neříkají (u štěkotu jsou vždy vysoko). */
private val GENERIC = setOf("Animal", "Domestic animals, pets", "Silence")

/** Kompletní rozbor nahrávky: detekce štěknutí + akustika + YAMNet. */
class Analyzer(private val context: Context) {

    private val yamnet: YamNet? by lazy {
        runCatching { YamNet(context) }.onFailure { Log.e("Analyzer", "YAMNet se nenačetl", it) }.getOrNull()
    }

    val hasAi: Boolean get() = yamnet != null

    fun yamnetOrNull(): YamNet? = yamnet

    suspend fun analyze(samples: FloatArray, sensitivity: Int): Analysis = withContext(Dispatchers.Default) {
        val sr = SAMPLE_RATE
        var ranges = BarkAnalysis.detect(samples, sr, sensitivity)

        // YAMNet okna po 0,4875 s (polovina okna)
        val net = yamnet
        val windows = mutableListOf<Pair<Int, FloatArray>>()
        if (net != null && samples.isNotEmpty()) {
            val hop = YamNet.WINDOW / 2
            val maxWindows = 240 // ~2 minuty; delší nahrávky se hodnotí jen v okolí štěkotu
            val starts = if (samples.size / hop <= maxWindows) {
                (0 until maxOf(1, samples.size - YamNet.WINDOW / 2) step hop).toList()
            } else {
                ranges.flatMap { r -> listOf((r.first - hop).coerceAtLeast(0), r.first) }.distinct()
            }
            for (s in starts) {
                val w = FloatArray(YamNet.WINDOW) { i -> samples.getOrElse(s + i) { 0f } }
                windows += s to net.classify(w)
            }
            // Vyhodit „štěkoty“, ve kterých AI žádného psa neslyší (bouchnutí dveří, talíř…)
            if (windows.any { net.dogScore(it.second) > 0.3f }) {
                ranges = ranges.filter { r ->
                    val overl = windows.filter { (s, _) -> s < r.last && s + YamNet.WINDOW > r.first }
                    overl.isEmpty() || overl.maxOf { net.dogScore(it.second) } >= 0.08f
                }
            }
        }

        // Úseky s těžištěm pod 200 Hz jsou dunění / hučení, ne štěk
        val barks = ranges.map { BarkAnalysis.features(samples, it, sr) }.filter { it.centroidHz >= 200f }
        ranges = ranges.filter { r -> barks.any { it.startMs == r.first * 1000 / sr } }
        val summary = BarkAnalysis.summarize(barks)

        val relevant = windows.filter { (s, _) -> ranges.any { r -> s < r.last && s + YamNet.WINDOW > r.first } }
            .ifEmpty { windows }
        val embedding = if (relevant.isEmpty()) FloatArray(0) else FloatArray(YamNet.CLASSES) { c ->
            relevant.sumOf { it.second[c].toDouble() }.toFloat() / relevant.size
        }
        val top = if (net == null || embedding.isEmpty()) emptyList() else
            embedding.indices.filter { net.labels.getOrNull(it) !in GENERIC }.sortedByDescending { embedding[it] }.take(5)
                .map { LabelScore(net.labels.getOrElse(it) { "#$it" }, embedding[it]) }

        Analysis(
            durationMs = samples.size * 1000 / sr,
            barks = barks,
            barkRate = summary.barkRate,
            meanIntervalMs = summary.meanIntervalMs,
            intervalCv = summary.intervalCv,
            meanPitchHz = summary.meanPitchHz,
            meanTonality = summary.meanTonality,
            meanCentroidHz = summary.meanCentroidHz,
            meanFlatness = summary.meanFlatness,
            meanDurationMs = summary.meanDurationMs,
            meanLoudnessDb = summary.meanLoudnessDb,
            dogScore = if (net == null) 0f else windows.maxOfOrNull { net.dogScore(it.second) } ?: 0f,
            yamnetTop = top,
            embedding = embedding.toList(),
            envelope = BarkAnalysis.envelope(samples),
        )
    }
}
