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
    fun `should calculate correct winner plus goal difference correctly`() {
        // Pred 3x1 (diff +2, winner 1), Real 2x0 (diff +2, winner 1) -> 15 points
        assertEquals(15, ScoringEngine.calculateMatchPoints(3, 1, 2, 0, false))
        // Final match: 30 points
        assertEquals(30, ScoringEngine.calculateMatchPoints(3, 1, 2, 0, true))
        
        // Draw with same goal difference (diff 0), e.g. 1x1 vs 2x2 -> 15 points
        assertEquals(15, ScoringEngine.calculateMatchPoints(1, 1, 2, 2, false))
    }

    @Test
    fun `should calculate correct winner or draw only correctly`() {
        // Pred 1x0 (diff +1, winner 1), Real 3x1 (diff +2, winner 1) -> 10 points
        assertEquals(10, ScoringEngine.calculateMatchPoints(1, 0, 3, 1, false))
        // Final match: 20 points
        assertEquals(20, ScoringEngine.calculateMatchPoints(1, 0, 3, 1, true))
    }

    @Test
    fun `should calculate isolated goals match correctly`() {
        // 5 points for matching home team goals exactly (but wrong winner)
        assertEquals(5, ScoringEngine.calculateMatchPoints(2, 0, 2, 3, false))
        // 5 points for matching home team goals (pred 2x2 vs real 2x1 -> wrong winner)
        assertEquals(5, ScoringEngine.calculateMatchPoints(2, 2, 2, 1, false))
    }

    @Test
    fun `should calculate basketball point margin scoring correctly`() {
        val bskSportId = ScoringEngine.BASKETBALL_SPORT_ID

        // 1. Exact Margin (pred 105x100 [margin +5], real 110x105 [margin +5]) -> 25 points
        assertEquals(25, ScoringEngine.calculateMatchPoints(105, 100, 110, 105, false, bskSportId))
        // Final match: 50 points
        assertEquals(50, ScoringEngine.calculateMatchPoints(105, 100, 110, 105, true, bskSportId))

        // 2. Close Margin within <= 3 points (pred 110x100 [margin +10], real 108x100 [margin +8] -> diff 2) -> 15 points
        assertEquals(15, ScoringEngine.calculateMatchPoints(110, 100, 108, 100, false, bskSportId))
        // Final match: 30 points
        assertEquals(30, ScoringEngine.calculateMatchPoints(110, 100, 108, 100, true, bskSportId))

        // 3. Winner Only (pred 115x100 [margin +15], real 105x100 [margin +5] -> diff 10) -> 10 points
        assertEquals(10, ScoringEngine.calculateMatchPoints(115, 100, 105, 100, false, bskSportId))

        // 4. Wrong Winner (pred 100x105 [away winner], real 110x100 [home winner]) -> 0 points
        assertEquals(0, ScoringEngine.calculateMatchPoints(100, 105, 110, 100, false, bskSportId))
    }
}
