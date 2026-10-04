package io.github.marciano.psitlumocnik.analysis

import io.github.marciano.psitlumocnik.data.BarkFeatures
import kotlin.math.abs

/**
 * Detekce jednotlivých štěknutí podle obálky hlasitosti a měření jejich vlastností.
 * Nezávislé na Androidu – testuje se v unit testech na syntetickém signálu.
 */
object BarkAnalysis {

    const val HOP = 160          // 10 ms při 16 kHz
    const val FRAME = 400        // 25 ms
    private const val YIN_FRAME = 640
    private const val MIN_BARK_MS = 60
    private const val MAX_BARK_MS = 3000
    private const val MERGE_GAP_MS = 40

    /** Odstup od šumového pozadí podle citlivosti 1 (hluché) – 5 (citlivé). */
    fun thresholdDb(sensitivity: Int) = when (sensitivity.coerceIn(1, 5)) {
        1 -> 20f; 2 -> 16f; 3 -> 12f; 4 -> 9f; else -> 7f
    }

    fun frameDb(x: FloatArray): FloatArray {
        val n = maxOf(0, (x.size - FRAME) / HOP + 1)
        return FloatArray(n) { Dsp.rmsDb(x, it * HOP, FRAME) }
    }

    /** Vrací úseky (indexy vzorků) s pravděpodobným štěkotem. */
    fun detect(x: FloatArray, sampleRate: Int, sensitivity: Int = 3): List<IntRange> {
        val db = frameDb(x)
        if (db.isEmpty()) return emptyList()
        val floor = Dsp.percentile(db, 0.2f).coerceAtLeast(-90f)
        val thr = maxOf(floor + thresholdDb(sensitivity), -55f)
        val framesPerMs = sampleRate / 1000f / HOP

        val raw = mutableListOf<IntRange>()
        var start = -1
        for (i in db.indices) {
            if (db[i] >= thr) { if (start < 0) start = i }
            else if (start >= 0) { raw += start until i; start = -1 }
        }
        if (start >= 0) raw += start until db.size

        // Slití úseků oddělených krátkou mezerou
        val merged = mutableListOf<IntRange>()
        val gapFrames = (MERGE_GAP_MS * framesPerMs).toInt().coerceAtLeast(1)
        for (r in raw) {
            val last = merged.lastOrNull()
            if (last != null && r.first - last.last <= gapFrames) merged[merged.size - 1] = last.first..r.last
            else merged += r
        }

        val minFrames = (MIN_BARK_MS * framesPerMs).toInt()
        val maxFrames = (MAX_BARK_MS * framesPerMs).toInt()
        return merged
            .filter { it.last - it.first + 1 >= minFrames }
            .filter { r -> (r.first..r.last).maxOf { db[it] } >= floor + thresholdDb(sensitivity) + 4 }
            .flatMap { r -> if (r.last - r.first + 1 > maxFrames) r.chunked(maxFrames) else listOf(r) }
            .map { r -> (r.first * HOP) until minOf(r.last * HOP + FRAME, x.size) }
    }

    private fun IntRange.chunked(size: Int): List<IntRange> =
        (first..last step size).map { it..minOf(it + size - 1, last) }

    fun features(x: FloatArray, range: IntRange, sampleRate: Int): BarkFeatures {
        val len = range.last - range.first + 1
        val frames = maxOf(1, (len - YIN_FRAME) / HOP + 1)
        val dbs = FloatArray(frames) { Dsp.rmsDb(x, range.first + it * HOP, FRAME) }
        val peak = dbs.max()
        val pitches = mutableListOf<Float>()
        val aper = mutableListOf<Float>()
        val centroids = mutableListOf<Float>()
        val flats = mutableListOf<Float>()
        for (f in 0 until frames) {
            if (dbs[f] < peak - 15f) continue
            val from = range.first + f * HOP
            val frame = FloatArray(YIN_FRAME) { i -> if (from + i < x.size) x[from + i] else 0f }
            val y = Dsp.yin(frame, sampleRate)
            aper += y.aperiodicity
            if (y.aperiodicity < 0.35f) pitches += y.pitchHz
            val s = Dsp.spectral(x, from, sampleRate)
            centroids += s.centroidHz; flats += s.flatness
        }
        val tonality = (1f - Dsp.median(aper)).coerceIn(0f, 1f)
        return BarkFeatures(
            startMs = range.first * 1000 / sampleRate,
            durationMs = len * 1000 / sampleRate,
            loudnessDb = peak,
            pitchHz = if (pitches.size >= 2) Dsp.median(pitches) else 0f,
            tonality = tonality,
            centroidHz = Dsp.median(centroids),
            flatness = Dsp.median(flats),
        )
    }

    data class Summary(
        val barkRate: Float, val meanIntervalMs: Float, val intervalCv: Float,
        val meanPitchHz: Float, val meanTonality: Float, val meanCentroidHz: Float,
        val meanFlatness: Float, val meanDurationMs: Float, val meanLoudnessDb: Float,
    )

    fun summarize(barks: List<BarkFeatures>): Summary {
        if (barks.isEmpty()) return Summary(0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, -120f)
        val starts = barks.map { it.startMs.toFloat() }
        val intervals = starts.zipWithNext { a, b -> b - a }
        val span = (starts.last() - starts.first()) / 1000f
        val meanInt = Dsp.mean(intervals)
        return Summary(
            barkRate = if (barks.size >= 2 && span > 0f) (barks.size - 1) / span else 0f,
            meanIntervalMs = meanInt,
            intervalCv = if (intervals.size >= 2 && meanInt > 0f) Dsp.std(intervals) / meanInt else 0f,
            meanPitchHz = Dsp.median(barks.map { it.pitchHz }.filter { it > 0f }),
            meanTonality = Dsp.mean(barks.map { it.tonality }),
            meanCentroidHz = Dsp.median(barks.map { it.centroidHz }),
            meanFlatness = Dsp.mean(barks.map { it.flatness }),
            meanDurationMs = Dsp.mean(barks.map { it.durationMs.toFloat() }),
            meanLoudnessDb = Dsp.mean(barks.map { it.loudnessDb }),
        )
    }

    /** Obálka pro graf – nejvýš [points] hodnot 0..1. */
    fun envelope(x: FloatArray, points: Int = 400): List<Float> {
        if (x.isEmpty()) return emptyList()
        val step = maxOf(1, x.size / points)
        val raw = (0 until x.size step step).map { s ->
            var m = 0f
            for (i in s until minOf(s + step, x.size)) m = maxOf(m, abs(x[i]))
            m
        }
        val max = (raw.maxOrNull() ?: 1f).coerceAtLeast(1e-4f)
        return raw.map { (it / max).coerceIn(0f, 1f) }
    }
}
