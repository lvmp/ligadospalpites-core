package com.ligadospalpites.predictions.domain.models

import com.ligadospalpites.predictions.domain.exceptions.PredictionLockedException
import com.ligadospalpites.predictions.domain.exceptions.PredictionProcessedException
import com.ligadospalpites.shared.domain.models.LeagueId
import com.ligadospalpites.shared.domain.models.MatchId
import com.ligadospalpites.shared.domain.models.UserId
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Instant
import java.time.temporal.ChronoUnit

class PredictionTest {

    private val userId = UserId.random()
    private val matchId = MatchId.random()
    private val leagueId = LeagueId.random()

    @Test
    fun `should create prediction and validate score value objects`() {
        val score = MatchPredictionScore.of(2, 1)
        assertTrue(score.isHomeWin())
        assertFalse(score.isAwayWin())
        assertFalse(score.isDraw())

        val futureKickoff = Instant.now().plus(2, ChronoUnit.HOURS)
        val prediction = Prediction.create(
            userId = userId,
            matchId = matchId,
            leagueId = leagueId,
            score = score,
            kickoffTime = futureKickoff
        )

        assertEquals(2, prediction.predictedHomeScore)
        assertEquals(1, prediction.predictedAwayScore)
        assertFalse(prediction.isProcessed)
        assertEquals(0, prediction.pointsAwarded)
    }

    @Test
    fun `should throw exception when creating prediction with negative score`() {
        assertThrows<IllegalArgumentException> {
            MatchPredictionScore.of(-1, 2)
        }
    }

    @Test
    fun `should throw PredictionLockedException when match already started`() {
        val score = MatchPredictionScore.of(1, 0)
        val pastKickoff = Instant.now().minus(5, ChronoUnit.MINUTES)

        assertThrows<PredictionLockedException> {
            Prediction.create(
                userId = userId,
                matchId = matchId,
                leagueId = leagueId,
                score = score,
                kickoffTime = pastKickoff
            )
        }
    }

    @Test
    fun `should update score when match is before kickoff`() {
        val futureKickoff = Instant.now().plus(1, ChronoUnit.HOURS)
        val prediction = Prediction.create(
            userId = userId,
            matchId = matchId,
            leagueId = leagueId,
            score = MatchPredictionScore.of(0, 0),
            kickoffTime = futureKickoff
        )

        val updated = prediction.updateScore(
            newScore = MatchPredictionScore.of(3, 1),
            kickoffTime = futureKickoff
        )

        assertEquals(3, updated.predictedHomeScore)
        assertEquals(1, updated.predictedAwayScore)
    }

    @Test
    fun `should throw PredictionProcessedException when trying to update processed prediction`() {
        val futureKickoff = Instant.now().plus(1, ChronoUnit.HOURS)
        val prediction = Prediction.create(
            userId = userId,
            matchId = matchId,
            leagueId = leagueId,
            score = MatchPredictionScore.of(1, 1),
            kickoffTime = futureKickoff
        ).awardPoints(25)

        assertTrue(prediction.isProcessed)

        assertThrows<PredictionProcessedException> {
            prediction.updateScore(
                newScore = MatchPredictionScore.of(2, 2),
                kickoffTime = futureKickoff
            )
        }
    }
}
