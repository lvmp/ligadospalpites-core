package com.ligadospalpites.predictions.domain.services

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ScoringEngineTest {

    @Test
    fun `should calculate exact score match correctly`() {
        // Normal match: 25 points
        assertEquals(25, ScoringEngine.calculateMatchPoints(2, 1, 2, 1, false))
        // Final match: 50 points
        assertEquals(50, ScoringEngine.calculateMatchPoints(2, 1, 2, 1, true))
    }

    @Test
    fun `should calculate correct winner plus one correct score correctly`() {
        // Normal match: 15 points
        assertEquals(15, ScoringEngine.calculateMatchPoints(3, 1, 2, 1, false))
        // Final match: 30 points
        assertEquals(30, ScoringEngine.calculateMatchPoints(3, 1, 2, 1, true))
    }

    @Test
    fun `should calculate correct winner or draw only correctly`() {
        // Normal match: 10 points
        assertEquals(10, ScoringEngine.calculateMatchPoints(1, 0, 2, 1, false))
        // Final match: 20 points
        assertEquals(20, ScoringEngine.calculateMatchPoints(1, 0, 2, 1, true))
    }

    @Test
    fun `should calculate isolated goals match correctly`() {
        // 5 points for matching home team goals exactly (but wrong winner)
        assertEquals(5, ScoringEngine.calculateMatchPoints(2, 0, 2, 3, false))
        // 10 points for matching both goals (but wrong winner - wait, if you guess 2-2 but it's 2-1, you got correct home score 2, but wrong winner)
        assertEquals(5, ScoringEngine.calculateMatchPoints(2, 2, 2, 1, false))
    }
}
