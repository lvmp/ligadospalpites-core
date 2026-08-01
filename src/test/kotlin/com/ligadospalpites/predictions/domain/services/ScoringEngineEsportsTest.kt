package com.ligadospalpites.predictions.domain.services

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.util.UUID

class ScoringEngineEsportsTest {

    private val esportsId = UUID.fromString("9b1e3a11-b9db-44ab-ba02-411a0c0bcf14")

    @Test
    fun `should calculate exact score match for BO3 MD3 series as 25 points`() {
        // Predicted 2-1, Real 2-1
        val points = ScoringEngine.calculateMatchPoints(
            predHome = 2, predAway = 1,
            realHome = 2, realAway = 1,
            isFinal = false,
            sportId = esportsId
        )
        assertEquals(25, points)
    }

    @Test
    fun `should calculate exact score match for BO3 MD3 final series as 50 points`() {
        val points = ScoringEngine.calculateMatchPoints(
            predHome = 2, predAway = 1,
            realHome = 2, realAway = 1,
            isFinal = true,
            sportId = esportsId
        )
        assertEquals(50, points)
    }

    @Test
    fun `should calculate correct winner with different score in MD3 as 15 points`() {
        // Predicted 2-0, Real 2-1
        val points = ScoringEngine.calculateMatchPoints(
            predHome = 2, predAway = 0,
            realHome = 2, realAway = 1,
            isFinal = false,
            sportId = esportsId
        )
        assertEquals(15, points)
    }

    @Test
    fun `should calculate correct winner with different score in MD5 as 15 points`() {
        // Predicted 3-0, Real 3-1
        val points = ScoringEngine.calculateMatchPoints(
            predHome = 3, predAway = 0,
            realHome = 3, realAway = 1,
            isFinal = false,
            sportId = esportsId
        )
        assertEquals(15, points)
    }

    @Test
    fun `should return 0 points for incorrect winner`() {
        // Predicted 2-1 (Home win), Real 0-2 (Away win)
        val points = ScoringEngine.calculateMatchPoints(
            predHome = 2, predAway = 1,
            realHome = 0, realAway = 2,
            isFinal = false,
            sportId = esportsId
        )
        assertEquals(0, points)
    }
}
