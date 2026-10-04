package io.github.marciano.psitlumocnik.data

import kotlinx.serialization.Serializable

/** Situace, ve které pes štěkal. Pořadí určuje pořadí v UI. */
@Serializable
enum class Situation(val label: String, val hint: String) {
    CIZI("Cizí člověk / zvonek", "Někdo u dveří, cizí na ulici, zvonek"),
    POPLACH("Zvuk venku", "Ruch na chodbě, auto, štěkot jiného psa"),
    AGRESE("Výhrůžka", "Brání se, hlídá žrádlo nebo hračku, vrčí"),
    HRA("Hra", "Přetahovaná, honička, hraní s jiným psem"),
    DOZADOVANI("Dožadování", "Chce žrádlo, ven, míček, pozornost"),
    SAMOTA("Samota", "Zůstal sám doma"),
    STRACH("Strach", "Bouřka, petardy, vysavač, veterinář"),
    VITANI("Vítání", "Přišel někdo známý"),
}

/** Řeč těla zaznamenaná k nahrávce – pomáhá zpětně ověřit výklad. */
@Serializable
enum class BodySignal(val label: String) {
    OCAS_VRTI("Vrtí ocasem"),
    OCAS_NAHORE("Ocas nahoře"),
    OCAS_STAZENY("Ocas stažený"),
    SRST("Naježená srst"),
    POSKAKUJE("Poskakuje"),
    ZTUHLY("Ztuhlý"),
    USI_DOZADU("Uši dozadu"),
    VRCI("Vrčí"),
}

@Serializable
enum class Source { NAHRAVKA, IMPORT, HLIDANI }

@Serializable
data class Dog(
    val id: String,
    val name: String,
    val breed: String = "",
    val note: String = "",
    val createdAt: Long = System.currentTimeMillis(),
)

@Serializable
data class LabelScore(val label: String, val score: Float)

/** Akustické vlastnosti jednoho štěknutí. */
@Serializable
data class BarkFeatures(
    val startMs: Int,
    val durationMs: Int,
    val loudnessDb: Float,
    /** Základní frekvence v Hz, 0 = nepodařilo se změřit (čistý šum). */
    val pitchHz: Float,
    /** 0 = čistý šum / chrapot, 1 = čistý tón. */
    val tonality: Float,
    val centroidHz: Float,
    val flatness: Float,
)

@Serializable
data class Analysis(
    val durationMs: Int,
    val barks: List<BarkFeatures>,
    val barkRate: Float,
    val meanIntervalMs: Float,
    val intervalCv: Float,
    val meanPitchHz: Float,
    val meanTonality: Float,
    val meanCentroidHz: Float,
    val meanFlatness: Float,
    val meanDurationMs: Float,
    val meanLoudnessDb: Float,
    /** Nejvyšší skóre psích tříd YAMNet (pes, štěkot, vrčení…). */
    val dogScore: Float,
    val yamnetTop: List<LabelScore>,
    /** Průměrné skóre všech 521 tříd YAMNet přes úseky se štěkotem. */
    val embedding: List<Float>,
    /** Zmenšená obálka hlasitosti pro vykreslení (0..1). */
    val envelope: List<Float>,
    val analyzerVersion: Int = ANALYZER_VERSION,
) {
    val hasBarks: Boolean get() = barks.isNotEmpty()
}

const val ANALYZER_VERSION = 1

@Serializable
data class Recording(
    val id: String,
    val dogId: String,
    val createdAt: Long,
    val durationMs: Int,
    val source: Source,
    val situation: Situation? = null,
    val bodySignals: Set<BodySignal> = emptySet(),
    val note: String = "",
    /** Situace doplněná automaticky (hlídání) – dokud ji člověk nepotvrdí, neučí se z ní. */
    val autoLabeled: Boolean = false,
    val analysis: Analysis? = null,
) {
    val isTrainingSample: Boolean
        get() = situation != null && !autoLabeled && analysis?.hasBarks == true
}

@Serializable
data class Store(
    val dogs: List<Dog> = emptyList(),
    val recordings: List<Recording> = emptyList(),
)
