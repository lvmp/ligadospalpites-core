package com.ligadospalpites.predictions.application.usecases

import com.ligadospalpites.predictions.domain.exceptions.MatchNotFoundException
import com.ligadospalpites.predictions.domain.exceptions.PredictionLockedException
import com.ligadospalpites.predictions.domain.models.*
import com.ligadospalpites.predictions.domain.ports.PredictionRepository
import com.ligadospalpites.shared.domain.models.LeagueId
import com.ligadospalpites.shared.domain.models.MatchId
import com.ligadospalpites.shared.domain.models.UserId
import com.ligadospalpites.sportsfeed.domain.models.Match
import com.ligadospalpites.sportsfeed.domain.models.MatchStatus
import com.ligadospalpites.sportsfeed.domain.ports.MatchRepository
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

class SubmitPredictionUseCaseTest {

    private class FakePredictionRepository : PredictionRepository {
        val storage = mutableMapOf<UUID, Prediction>()

        override fun findById(id: PredictionId): Prediction? = storage[id.value]
        override fun findByMatchId(matchId: MatchId): List<Prediction> =
            storage.values.filter { it.matchId == matchId }

        override fun findByUserIdAndMatchId(userId: UserId, matchId: MatchId): Prediction? =
            storage.values.firstOrNull { it.userId == userId && it.matchId == matchId }

        override fun findByUserIdAndLeagueId(userId: UserId, leagueId: LeagueId): List<Prediction> =
            storage.values.filter { it.userId == userId && it.leagueId == leagueId }

        override fun findByUserIdAndLeagueIdIn(userId: UserId, leagueIds: Set<LeagueId>): List<Prediction> =
            storage.values.filter { it.userId == userId && it.leagueId in leagueIds }

        override fun save(prediction: Prediction): Prediction {
            storage[prediction.id.value] = prediction
            return prediction
        }

        override fun saveAll(predictions: List<Prediction>): List<Prediction> {
            predictions.forEach { save(it) }
            return predictions
        }
    }

    private class FakeMatchRepository : MatchRepository {
        val storage = mutableMapOf<UUID, Match>()

        override fun findById(id: UUID): Match? = storage[id]
        override fun findByLeagueId(leagueId: UUID): List<Match> =
            storage.values.filter { it.leagueId == leagueId }

        override fun findBySeasonId(seasonId: UUID): List<Match> =
            storage.values.filter { it.seasonId == seasonId }

        override fun save(match: Match): Match {
            storage[match.id] = match
            return match
        }
    }

    private lateinit var predictionRepository: FakePredictionRepository
    private lateinit var matchRepository: FakeMatchRepository
    private lateinit var useCase: SubmitPredictionUseCase

    private val userId = UserId.random()
    private val matchId = MatchId.random()
    private val leagueId = UUID.randomUUID()
    private val sportId = UUID.randomUUID()
    private val seasonId = UUID.randomUUID()

    @BeforeEach
    fun setUp() {
        predictionRepository = FakePredictionRepository()
        matchRepository = FakeMatchRepository()
        useCase = SubmitPredictionUseCase(predictionRepository, matchRepository)
    }

    @Test
    fun `should create and save new prediction when none exists`() {
        val futureKickoff = Instant.now().plus(2, ChronoUnit.HOURS)
        val match = Match(
            id = matchId.value,
            sportId = sportId,
            leagueId = leagueId,
            seasonId = seasonId,
            homeTeamName = "Brasil",
            awayTeamName = "França",
            kickoffTime = futureKickoff,
            status = MatchStatus.SCHEDULED,
            homeScore = null,
            awayScore = null
        )
        matchRepository.save(match)

        val result = useCase.execute(
            SubmitPredictionUseCase.Command(
                userId = userId,
                matchId = matchId,
                predictedHomeScore = 2,
                predictedAwayScore = 0
            )
        )

        assertNotNull(result)
        assertEquals(2, result.predictedHomeScore)
        assertEquals(0, result.predictedAwayScore)
        assertEquals(userId, result.userId)
        assertEquals(matchId, result.matchId)
        assertEquals(1, predictionRepository.storage.size)
    }

    @Test
    fun `should update existing prediction if match is not locked`() {
        val futureKickoff = Instant.now().plus(2, ChronoUnit.HOURS)
        val match = Match(
            id = matchId.value,
            sportId = sportId,
            leagueId = leagueId,
            seasonId = seasonId,
            homeTeamName = "Brasil",
            awayTeamName = "França",
            kickoffTime = futureKickoff,
            status = MatchStatus.SCHEDULED,
            homeScore = null,
            awayScore = null
        )
        matchRepository.save(match)

        val existing = Prediction.create(
            userId = userId,
            matchId = matchId,
            leagueId = LeagueId(leagueId),
            score = MatchPredictionScore.of(1, 1),
            kickoffTime = futureKickoff
        )
        predictionRepository.save(existing)

        val result = useCase.execute(
            SubmitPredictionUseCase.Command(
                userId = userId,
                matchId = matchId,
                predictedHomeScore = 3,
                predictedAwayScore = 2
            )
        )

        assertEquals(3, result.predictedHomeScore)
        assertEquals(2, result.predictedAwayScore)
        assertEquals(existing.id, result.id)
        assertEquals(1, predictionRepository.storage.size)
    }

    @Test
    fun `should throw MatchNotFoundException when match does not exist`() {
        assertThrows<MatchNotFoundException> {
            useCase.execute(
                SubmitPredictionUseCase.Command(
                    userId = userId,
                    matchId = matchId,
                    predictedHomeScore = 1,
                    predictedAwayScore = 0
                )
            )
        }
    }

    @Test
    fun `should throw PredictionLockedException when match has already started`() {
        val pastKickoff = Instant.now().minus(10, ChronoUnit.MINUTES)
        val match = Match(
            id = matchId.value,
            sportId = sportId,
            leagueId = leagueId,
            seasonId = seasonId,
            homeTeamName = "Brasil",
            awayTeamName = "França",
            kickoffTime = pastKickoff,
            status = MatchStatus.LIVE,
            homeScore = null,
            awayScore = null
        )
        matchRepository.save(match)

        assertThrows<PredictionLockedException> {
            useCase.execute(
                SubmitPredictionUseCase.Command(
                    userId = userId,
                    matchId = matchId,
                    predictedHomeScore = 1,
                    predictedAwayScore = 0
                )
            )
        }
    }
}
