package com.ligadospalpites.predictions.domain.models

import java.util.UUID

@JvmInline
value class PredictionId(val value: UUID) {
    override fun toString(): String = value.toString()
    companion object {
        fun random(): PredictionId = PredictionId(UUID.randomUUID())
        fun fromString(str: String): PredictionId = PredictionId(UUID.fromString(str))
    }
}

@JvmInline
value class Score(val value: Int) {
    init {
        require(value >= 0) { "O placar previsto não pode ser negativo ($value)." }
        require(value <= 99) { "O placar previsto não pode exceder 99 ($value)." }
    }
    override fun toString(): String = value.toString()
}

data class MatchPredictionScore(
    val home: Score,
    val away: Score
) {
    fun isHomeWin(): Boolean = home.value > away.value
    fun isAwayWin(): Boolean = away.value > home.value
    fun isDraw(): Boolean = home.value == away.value

    companion object {
        fun of(home: Int, away: Int): MatchPredictionScore =
            MatchPredictionScore(Score(home), Score(away))
    }
}

@JvmInline
value class SpecialPredictionId(val value: UUID) {
    override fun toString(): String = value.toString()
    companion object {
        fun random(): SpecialPredictionId = SpecialPredictionId(UUID.randomUUID())
        fun fromString(str: String): SpecialPredictionId = SpecialPredictionId(UUID.fromString(str))
    }
}

@JvmInline
value class SpecialPredictionType(val value: String) {
    init {
        require(value.isNotBlank()) { "O tipo de palpite especial não pode ser vazio." }
    }
    override fun toString(): String = value

    companion object {
        val CHAMPION = SpecialPredictionType("CHAMPION")
        val SECOND_PLACE = SpecialPredictionType("SECOND_PLACE")
        val THIRD_PLACE = SpecialPredictionType("THIRD_PLACE")
        val FOURTH_PLACE = SpecialPredictionType("FOURTH_PLACE")

        fun of(type: String): SpecialPredictionType = SpecialPredictionType(type.trim().uppercase())
    }
}
