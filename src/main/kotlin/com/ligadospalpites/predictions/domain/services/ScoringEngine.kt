package com.ligadospalpites.predictions.domain.services

object ScoringEngine {

    fun calculateMatchPoints(
        predHome: Int, predAway: Int,
        realHome: Int, realAway: Int,
        isFinal: Boolean
    ): Int {
        // Rule 1: Exact Score Match (25 points / 50 for final)
        if (predHome == realHome && predAway == realAway) {
            return if (isFinal) 50 else 25
        }

        val predWinner = when {
            predHome > predAway -> 1
            predHome < predAway -> -1
            else -> 0
        }
        val realWinner = when {
            realHome > realAway -> 1
            realHome < realAway -> -1
            else -> 0
        }

        val predDiff = predHome - predAway
        val realDiff = realHome - realAway

        if (predWinner == realWinner) {
            // Rule 2: Correct Winner + Goal Difference / Saldo de Gols (15 points / 30 for final)
            if (predDiff == realDiff) {
                return if (isFinal) 30 else 15
            }
            // Rule 3: Correct Winner or Draw Only (10 points / 20 for final)
            return if (isFinal) 20 else 10
        }

        // Rule 4: Isolated Goals (5 points each / 10 for final)
        var points = 0
        if (predHome == realHome) points += 5
        if (predAway == realAway) points += 5
        return if (isFinal) points * 2 else points
    }
}
