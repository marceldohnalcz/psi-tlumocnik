package io.github.marciano.psitlumocnik.analysis

import io.github.marciano.psitlumocnik.data.Analysis
import io.github.marciano.psitlumocnik.data.Recording
import io.github.marciano.psitlumocnik.data.Situation
import java.util.Locale
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.log2
import kotlin.math.sqrt

data class Interpretation(
    /** Pravděpodobnosti situací seřazené sestupně. */
    val probabilities: List<Pair<Situation, Float>>,
    /** Kolik označených nahrávek se použilo k učení. */
    val learnedFrom: Int,
    /** Váha naučeného modelu ve výsledku (0 = jen obecná pravidla). */
    val learnedWeight: Float,
    val explanation: List<String>,
) {
    val top: Situation? get() = probabilities.firstOrNull()?.first
}

/**
 * Výklad štěkotu: obecná pravidla (Mortonova motivačně-strukturní pravidla + poznatky etologů)
 * smíchaná s k-NN modelem naučeným z označených nahrávek konkrétního psa.
 */
object Interpreter {

    const val DEFAULT_PITCH = 450f

    // ---------- Obecná pravidla ----------

    private data class Proto(val pitch: Float, val harsh: Float, val rate: Float, val cv: Float)

    /** Prototypy v normalizovaném prostoru (relativní výška, chraplavost, tempo, nepravidelnost). */
    private val prototypes = mapOf(
        Situation.CIZI to Proto(-0.3f, 0.8f, 1.0f, -0.5f),
        Situation.POPLACH to Proto(0.0f, 0.5f, 0.3f, 0.3f),
        Situation.AGRESE to Proto(-1.2f, 1.2f, -0.3f, 0.0f),
        Situation.HRA to Proto(0.8f, -0.2f, 0.3f, 1.0f),
        Situation.DOZADOVANI to Proto(0.3f, -0.3f, -0.8f, -1.0f),
        Situation.SAMOTA to Proto(1.0f, -0.8f, -1.2f, 0.0f),
        Situation.STRACH to Proto(1.2f, -0.6f, 0.2f, 0.5f),
        Situation.VITANI to Proto(1.0f, -0.5f, 1.5f, 0.5f),
    )

    fun baselinePitch(history: List<Recording>): Float {
        val p = history.mapNotNull { it.analysis?.meanPitchHz?.takeIf { v -> v > 0f } }
        return if (p.size >= 3) Dsp.median(p) else DEFAULT_PITCH
    }

    private fun pitchOf(a: Analysis): Float =
        if (a.meanPitchHz > 0f) a.meanPitchHz else (a.meanCentroidHz / 2.5f).coerceIn(150f, 1500f)

    private fun yam(a: Analysis, idx: Int): Float = a.embedding.getOrElse(idx) { 0f }

    fun ruleProbabilities(a: Analysis, baseline: Float): Map<Situation, Float> {
        val p = log2(pitchOf(a) / baseline) / 0.5f
        val h = ((1f - a.meanTonality) - 0.5f) / 0.2f
        val r = if (a.barks.size >= 2) (a.barkRate.coerceAtMost(8f) - 2f) / 1.5f else -0.6f
        val cv = if (a.barks.size >= 3) (a.intervalCv.coerceAtMost(2f) - 0.4f) / 0.25f else 0f
        val growl = yam(a, YamNet.GROWL)
        val whimper = yam(a, YamNet.WHIMPER)
        val howl = yam(a, YamNet.HOWL)
        val yip = yam(a, YamNet.YIP)
        val raw = prototypes.mapValues { (sit, pr) ->
            val d2 = (sq(p - pr.pitch) + sq(h - pr.harsh) + sq(r - pr.rate) + sq(cv - pr.cv)) / 4f
            var s = exp(-0.5f * d2 * 2f)
            s *= when (sit) {
                Situation.AGRESE -> 1f + 4f * growl
                Situation.SAMOTA -> 1f + 3f * howl + 2f * whimper
                Situation.STRACH -> 1f + 3f * whimper
                Situation.VITANI -> 1f + 2f * yip
                Situation.HRA -> 1f + 1f * yip
                else -> 1f
            }
            s + 0.03f
        }
        return normalize(raw)
    }

    // ---------- Učení z označených nahrávek (k-NN) ----------

    private fun vector(a: Analysis): FloatArray = floatArrayOf(
        log2(pitchOf(a)),
        a.meanTonality,
        log2(a.meanCentroidHz.coerceAtLeast(100f)),
        a.meanFlatness,
        ln(a.meanDurationMs.coerceAtLeast(30f)),
        if (a.barks.size >= 2) a.barkRate.coerceAtMost(8f) else 0f,
        if (a.barks.size >= 2) ln(a.meanIntervalMs.coerceAtLeast(50f)) else 8f,
        a.intervalCv.coerceAtMost(2f),
        yam(a, YamNet.GROWL), yam(a, YamNet.WHIMPER), yam(a, YamNet.HOWL), yam(a, YamNet.YIP),
    )

    class Model(private val samples: List<Pair<Situation, Analysis>>) {
        private val vecs = samples.map { vector(it.second) }
        private val mean: FloatArray
        private val std: FloatArray

        init {
            val dim = vecs.firstOrNull()?.size ?: 0
            mean = FloatArray(dim) { d -> vecs.map { it[d] }.average().toFloat() }
            std = FloatArray(dim) { d ->
                val m = mean[d]
                sqrt(vecs.map { (it[d] - m) * (it[d] - m) }.average()).toFloat().coerceAtLeast(0.05f)
            }
        }

        val size get() = samples.size
        val classCount get() = samples.map { it.first }.distinct().size

        private fun distance(a: FloatArray, ea: List<Float>, i: Int): Float {
            val b = vecs[i]
            var s = 0f
            for (d in a.indices) s += sq((a[d] - b[d]) / std[d])
            var dist = sqrt(s / a.size)
            val eb = samples[i].second.embedding
            if (ea.isNotEmpty() && eb.size == ea.size) dist += 1.5f * (1f - cosine(ea, eb))
            return dist
        }

        fun predict(a: Analysis, exclude: Int = -1): Map<Situation, Float> {
            val v = vector(a)
            val neighbours = samples.indices.filter { it != exclude }
                .map { it to distance(v, a.embedding, it) }
                .sortedBy { it.second }
                .take(minOf(7, maxOf(1, samples.size / 3)))
            val votes = HashMap<Situation, Float>()
            for ((i, d) in neighbours) votes.merge(samples[i].first, 1f / (d + 0.1f), Float::plus)
            return normalize(votes)
        }

        /** Úspěšnost metodou leave-one-out – kolik nahrávek by model trefil, kdyby je neznal. */
        fun leaveOneOutAccuracy(): Float? {
            if (samples.size < 6 || classCount < 2) return null
            val hits = samples.indices.count { i ->
                predict(samples[i].second, exclude = i).maxByOrNull { it.value }?.key == samples[i].first
            }
            return hits.toFloat() / samples.size
        }
    }

    fun buildModel(history: List<Recording>, excludeId: String? = null): Model =
        Model(history.filter { it.isTrainingSample && it.id != excludeId }.map { it.situation!! to it.analysis!! })

    // ---------- Výsledek ----------

    fun interpret(a: Analysis, history: List<Recording>, excludeId: String? = null): Interpretation {
        val baseline = baselinePitch(history.filter { it.id != excludeId })
        val rules = ruleProbabilities(a, baseline)
        val model = buildModel(history, excludeId)
        val alpha = if (model.size >= 3 && model.classCount >= 2) model.size / (model.size + 12f) else 0f
        val combined = if (alpha > 0f) {
            val learned = model.predict(a)
            Situation.entries.associateWith { (1 - alpha) * (rules[it] ?: 0f) + alpha * (learned[it] ?: 0f) }
        } else rules
        return Interpretation(
            probabilities = normalize(combined).toList().sortedByDescending { it.second },
            learnedFrom = model.size,
            learnedWeight = alpha,
            explanation = explain(a, baseline, baseline != DEFAULT_PITCH),
        )
    }

    fun explain(a: Analysis, baseline: Float, personalBaseline: Boolean): List<String> {
        if (!a.hasBarks) return listOf("Žádné štěknutí jsem nenašel. Buď byl pes zticha, nebo byl mikrofon moc daleko.")
        val out = mutableListOf<String>()
        val n = a.barks.size
        out += "Štěknutí: $n " + when { n == 1 -> "(jedno, osamocené)"; n < 5 -> "(krátká série)"; else -> "(pořádná série)" }

        val pitch = pitchOf(a)
        val oct = log2(pitch / baseline)
        val ref = if (personalBaseline) "jeho obvyklým hlasem" else "běžným psem"
        out += "Výška: ${pitch.toInt()} Hz – " + when {
            oct < -0.35f -> "výrazně hlubší než s $ref → spíš sebevědomí, výhrůžka"
            oct < -0.1f -> "o něco hlubší než s $ref"
            oct > 0.35f -> "výrazně vyšší než s $ref → nejistota, prosba, vzrušení"
            oct > 0.1f -> "o něco vyšší než s $ref"
            else -> "zhruba jeho normál"
        } + if (a.meanPitchHz <= 0f) " (odhad ze spektra, štěkot byl hodně šumivý)" else ""

        val t = a.meanTonality
        out += "Hlas: " + when {
            t < 0.35f -> "chraplavý, drsný → vzrušení, hlídání, agrese"
            t < 0.6f -> "napůl chraplavý"
            else -> "čistý, skoro melodický → osamění, prosba, hra"
        }

        if (n >= 2) {
            out += "Tempo: ${fmt(a.barkRate)} štěknutí za sekundu – " + when {
                a.barkRate > 3f -> "kulomet, vysoké vzrušení"
                a.barkRate > 1.5f -> "svižné"
                else -> "pomalé, s pauzami"
            }
        }
        if (n >= 3) {
            out += "Rytmus: " + when {
                a.intervalCv < 0.25f -> "pravidelný jako metronom (typické pro dožadování a samotu)"
                a.intervalCv > 0.6f -> "nepravidelný, nahodilý (typické pro hru a reakce na podněty)"
                else -> "středně pravidelný"
            }
        }
        val dogClasses = a.yamnetTop.filter { it.score >= 0.1f }.take(3)
        if (dogClasses.isNotEmpty()) {
            out += "AI slyší: " + dogClasses.joinToString(", ") { "${YamNet.czech(it.label)} ${(it.score * 100).toInt()} %" }
        }
        return out
    }

    private fun fmt(v: Float) = String.format(Locale.forLanguageTag("cs"), "%.1f", v)
    private fun sq(v: Float) = v * v

    private fun cosine(a: List<Float>, b: List<Float>): Float {
        var dot = 0f; var na = 0f; var nb = 0f
        for (i in a.indices) { dot += a[i] * b[i]; na += a[i] * a[i]; nb += b[i] * b[i] }
        return if (na == 0f || nb == 0f) 0f else dot / sqrt(na * nb)
    }

    private fun normalize(m: Map<Situation, Float>): Map<Situation, Float> {
        val sum = m.values.sum()
        return if (sum <= 0f) m else m.mapValues { abs(it.value) / sum }
    }
}
