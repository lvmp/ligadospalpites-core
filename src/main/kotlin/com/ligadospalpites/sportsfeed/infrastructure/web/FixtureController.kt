package com.ligadospalpites.sportsfeed.infrastructure.web

import com.ligadospalpites.sportsfeed.infrastructure.persistence.SpringDataSportRepository
import com.ligadospalpites.sportsfeed.infrastructure.persistence.SpringDataLeagueRepository
import com.ligadospalpites.sportsfeed.infrastructure.persistence.SpringDataMatchRepository
import com.ligadospalpites.sportsfeed.infrastructure.persistence.MatchJpaEntity
import com.ligadospalpites.users.infrastructure.persistence.SpringDataUserEntitlementRepository
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.util.UUID

@RestController
@RequestMapping("/api/v1/sports")
class FixtureController(
    private val sportRepository: SpringDataSportRepository,
    private val leagueRepository: SpringDataLeagueRepository,
    private val matchRepository: SpringDataMatchRepository,
    private val entitlementRepository: SpringDataUserEntitlementRepository
) {

    // 1. Get leagues grouped by sport
    @GetMapping("/leagues")
    fun getLeaguesGroupedBySport(): ResponseEntity<List<SportWithLeaguesResponse>> {
        val activeLeagues = leagueRepository.findByIsActiveTrue()
        val sports = sportRepository.findAll()

        val grouped = sports.map { sport ->
            val leaguesForSport = activeLeagues
                .filter { it.sportId == sport.id }
                .map { LeagueResponse(it.id, it.name, it.isActive) }

            SportWithLeaguesResponse(
                sportId = sport.id,
                sportName = sport.name,
                leagues = leaguesForSport
            )
        }.filter { it.leagues.isNotEmpty() }

        return ResponseEntity.ok(grouped)
    }

    // 2. List fixtures for a sport/league (with active check and premium sport lock)
    @GetMapping("/fixtures")
    fun getFixtures(
        @RequestParam(required = false) sportId: UUID?,
        @RequestParam(required = false) leagueId: UUID?,
        @RequestHeader(value = "X-User-Id", required = false) userIdHeader: String?
    ): ResponseEntity<Any> {
        val userUUID = userIdHeader?.let { UUID.fromString(it) } ?: UUID.fromString("9b1deb4d-3b7d-4bad-9bdd-2b0d7b3dcb6d")

        // Validation A: If leagueId is specified, check if it's active
        if (leagueId != null) {
            val league = leagueRepository.findById(leagueId).orElse(null)
            if (league == null || !league.isActive) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(mapOf("error" to "LEAGUE_INACTIVE", "message" to "Esta liga está inativa."))
            }
        }

        // Validation B: Check MULTI_SPORT entitlement lock for non-default sports
        val footballId = UUID.fromString("f3b3b44b-6f81-42cb-b1b7-d1a1005a8f4c")
        if (sportId != null && sportId != footballId) {
            val entitlements = entitlementRepository.findByUserId(userUUID)
            val hasMultiSport = entitlements.any {
                it.entitlementType == com.ligadospalpites.users.domain.models.EntitlementType.PREMIUM ||
                (it.entitlementType == com.ligadospalpites.users.domain.models.EntitlementType.SPORT_PASS && it.sportId == sportId)
            }
            if (!hasMultiSport) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(mapOf("error" to "SPORT_LOCKED", "message" to "Assine o plano MULTI_SPORT para acessar este esporte."))
            }
        }

        val allMatches = if (leagueId != null) {
            matchRepository.findByLeagueId(leagueId)
        } else {
            matchRepository.findAll()
        }

        val filtered = allMatches.filter { match ->
            (sportId == null || match.sportId == sportId)
        }.map { MatchResponse.fromEntity(it) }

        return ResponseEntity.ok(filtered)
    }

    // 3. Standings (Tabela) for Group Stage
    @GetMapping("/standings")
    fun getStandings(@RequestParam leagueId: UUID): ResponseEntity<List<StandingRow>> {
        // Mock standings response structure based on matches and teams
        val rows = listOf(
            StandingRow(1, UUID.randomUUID(), "Brasil", 9, 3, 3, 0, 0, 8, 1, 7),
            StandingRow(2, UUID.randomUUID(), "França", 6, 3, 2, 0, 1, 5, 3, 2)
        )
        return ResponseEntity.ok(rows)
    }

    // 4. Bracket Match Tree for Knockout
    @GetMapping("/brackets")
    fun getBrackets(@RequestParam leagueId: UUID): ResponseEntity<BracketResponse> {
        val matches = matchRepository.findByLeagueId(leagueId)
        // Group matches into phases for the Flutter BracketBloc
        val stages = mapOf(
            "OITAVAS" to matches.filter { it.homeTeamName.contains("Oitavas") }.map { MatchResponse.fromEntity(it) },
            "QUARTAS" to matches.filter { it.homeTeamName.contains("Quartas") }.map { MatchResponse.fromEntity(it) },
            "SEMI" to matches.filter { it.homeTeamName.contains("Semi") }.map { MatchResponse.fromEntity(it) },
            "FINAL" to matches.filter { it.homeTeamName.contains("Final") }.map { MatchResponse.fromEntity(it) }
        )

        return ResponseEntity.ok(BracketResponse(leagueId, stages))
    }
}

// DTOs
data class SportWithLeaguesResponse(val sportId: UUID, val sportName: String, val leagues: List<LeagueResponse>)
data class LeagueResponse(val leagueId: UUID, val name: String, val isActive: Boolean)

data class MatchResponse(
    val matchId: UUID,
    val sportId: UUID,
    val leagueId: UUID,
    val homeTeam: String,
    val awayTeam: String,
    val kickoffTime: String,
    val status: String,
    val scoreHome: Int?,
    val scoreAway: Int?
) {
    companion object {
        fun fromEntity(entity: MatchJpaEntity) = MatchResponse(
            matchId = entity.id,
            sportId = entity.sportId,
            leagueId = entity.leagueId,
            homeTeam = entity.homeTeamName,
            awayTeam = entity.awayTeamName,
            kickoffTime = entity.kickoffTime.toString(),
            status = entity.status.name,
            scoreHome = entity.homeScore,
            scoreAway = entity.awayScore
        )
    }
}

data class StandingRow(
    val position: Int,
    val teamId: UUID,
    val teamName: String,
    val points: Int,
    val played: Int,
    val won: Int,
    val drawn: Int,
    val lost: Int,
    val goalsFor: Int,
    val goalsAgainst: Int,
    val goalDifference: Int
)

data class BracketResponse(
    val leagueId: UUID,
    val phases: Map<String, List<MatchResponse>>
)
