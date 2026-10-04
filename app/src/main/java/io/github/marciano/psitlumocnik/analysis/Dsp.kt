package io.github.marciano.psitlumocnik.analysis

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.sin
import kotlin.math.sqrt

/** Základní zpracování signálu – čistý Kotlin, testovatelné bez Androidu. */
object Dsp {

    fun rmsDb(x: FloatArray, from: Int, len: Int): Float {
        var s = 0.0
        val end = minOf(from + len, x.size)
        if (end <= from) return -120f
        for (i in from until end) s += x[i] * x[i]
        return (10 * log10((s / (end - from)).coerceAtLeast(1e-12))).toFloat()
    }

    data class Yin(val pitchHz: Float, val aperiodicity: Float)

    /**
     * YIN odhad základní frekvence. Vrací frekvenci a „neperiodicitu“ (0 = čistý tón, 1 = šum).
     * Rozsah 100–2000 Hz pokrývá štěkot od dogy po čivavu.
     */
    fun yin(frame: FloatArray, sampleRate: Int, minF: Float = 100f, maxF: Float = 2000f, threshold: Float = 0.15f): Yin {
        val tauMin = (sampleRate / maxF).toInt().coerceAtLeast(2)
        val tauMax = minOf((sampleRate / minF).toInt(), frame.size / 2)
        val w = frame.size - tauMax
        if (w <= 0 || tauMax <= tauMin + 2) return Yin(0f, 1f)
        val d = FloatArray(tauMax + 1)
        for (tau in 1..tauMax) {
            var s = 0f
            for (j in 0 until w) {
                val diff = frame[j] - frame[j + tau]
                s += diff * diff
            }
            d[tau] = s
        }
        val cmnd = FloatArray(tauMax + 1)
        cmnd[0] = 1f
        var running = 0f
        for (tau in 1..tauMax) {
            running += d[tau]
            cmnd[tau] = if (running > 0f) d[tau] * tau / running else 1f
        }
        var best = -1
        var tau = tauMin
        while (tau < tauMax) {
            if (cmnd[tau] < threshold) {
                while (tau + 1 < tauMax && cmnd[tau + 1] < cmnd[tau]) tau++
                best = tau; break
            }
            tau++
        }
        if (best < 0) {
            best = tauMin
            for (t in tauMin until tauMax) if (cmnd[t] < cmnd[best]) best = t
        }
        // Parabolická interpolace pro přesnější periodu
        val refined = if (best in 1 until tauMax) {
            val a = cmnd[best - 1]; val b = cmnd[best]; val c = cmnd[best + 1]
            val denom = a - 2 * b + c
            if (denom != 0f) best + 0.5f * (a - c) / denom else best.toFloat()
        } else best.toFloat()
        return Yin(sampleRate / refined, cmnd[best].coerceIn(0f, 1f))
    }

    /** In-place radix-2 FFT. */
    fun fft(re: FloatArray, im: FloatArray) {
        val n = re.size
        var j = 0
        for (i in 1 until n) {
            var bit = n shr 1
            while (j and bit != 0) { j = j xor bit; bit = bit shr 1 }
            j = j xor bit
            if (i < j) {
                var t = re[i]; re[i] = re[j]; re[j] = t
                t = im[i]; im[i] = im[j]; im[j] = t
            }
        }
        var len = 2
        while (len <= n) {
            val ang = -2 * PI / len
            val wr = cos(ang).toFloat(); val wi = sin(ang).toFloat()
            var i = 0
            while (i < n) {
                var cr = 1f; var ci = 0f
                for (k in 0 until len / 2) {
                    val ur = re[i + k]; val ui = im[i + k]
                    val vr = re[i + k + len / 2] * cr - im[i + k + len / 2] * ci
                    val vi = re[i + k + len / 2] * ci + im[i + k + len / 2] * cr
                    re[i + k] = ur + vr; im[i + k] = ui + vi
                    re[i + k + len / 2] = ur - vr; im[i + k + len / 2] = ui - vi
                    val ncr = cr * wr - ci * wi
                    ci = cr * wi + ci * wr; cr = ncr
                }
                i += len
            }
            len = len shl 1
        }
    }

    data class Spectral(val centroidHz: Float, val flatness: Float)

    /** Spektrální těžiště a plochost (Wiener entropy) okna délky 512 s Hannovým oknem. */
    fun spectral(x: FloatArray, from: Int, sampleRate: Int, n: Int = 512): Spectral {
        val re = FloatArray(n); val im = FloatArray(n)
        for (i in 0 until n) {
            val idx = from + i
            val v = if (idx < x.size) x[idx] else 0f
            re[i] = v * (0.5f - 0.5f * cos(2 * PI * i / (n - 1)).toFloat())
        }
        fft(re, im)
        var num = 0.0; var den = 0.0; var logSum = 0.0
        val bins = n / 2
        for (k in 1 until bins) {
            val p = (re[k] * re[k] + im[k] * im[k]).toDouble() + 1e-12
            val f = k.toDouble() * sampleRate / n
            num += f * p; den += p; logSum += ln(p)
        }
        val m = bins - 1
        val geo = exp(logSum / m)
        val arith = den / m
        return Spectral((num / den).toFloat(), (geo / arith).toFloat().coerceIn(0f, 1f))
    }

    fun median(values: List<Float>): Float {
        if (values.isEmpty()) return 0f
        val s = values.sorted()
        return if (s.size % 2 == 1) s[s.size / 2] else (s[s.size / 2 - 1] + s[s.size / 2]) / 2
    }

    fun mean(values: List<Float>): Float = if (values.isEmpty()) 0f else values.sum() / values.size

    fun std(values: List<Float>): Float {
        if (values.size < 2) return 0f
        val m = mean(values)
        return sqrt(values.sumOf { ((it - m) * (it - m)).toDouble() } / (values.size - 1)).toFloat()
    }

    fun percentile(values: FloatArray, p: Float): Float {
        if (values.isEmpty()) return 0f
        val s = values.sortedArray()
        return s[((s.size - 1) * p).toInt().coerceIn(0, s.size - 1)]
    }
}
