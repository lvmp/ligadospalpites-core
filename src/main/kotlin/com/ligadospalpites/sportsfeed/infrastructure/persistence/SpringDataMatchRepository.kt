package com.ligadospalpites.sportsfeed.infrastructure.persistence

import com.ligadospalpites.sportsfeed.domain.models.MatchStatus
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.Instant
import java.util.UUID

interface SpringDataMatchRepository : JpaRepository<MatchJpaEntity, UUID> {
    fun findByLeagueId(leagueId: UUID): List<MatchJpaEntity>
    fun findBySeasonId(seasonId: UUID): List<MatchJpaEntity>

    @Query("""
        SELECT m FROM MatchJpaEntity m
        WHERE m.status IN :statuses
          AND m.kickoffTime >= :startTime
          AND m.kickoffTime <= :endTime
          AND m.leagueId IN :leagueIds
          AND (:sportId IS NULL OR m.sportId = :sportId)
        ORDER BY m.kickoffTime ASC
    """)
    fun findUpcomingMatchesByLeagueIds(
        @Param("statuses") statuses: List<MatchStatus>,
        @Param("startTime") startTime: Instant,
        @Param("endTime") endTime: Instant,
        @Param("leagueIds") leagueIds: Collection<UUID>,
        @Param("sportId") sportId: UUID?
    ): List<MatchJpaEntity>

    @Query("""
        SELECT m FROM MatchJpaEntity m
        WHERE ((m.status IN :liveStatuses) OR (m.status = :scheduledStatus AND m.kickoffTime >= :startTime AND m.kickoffTime <= :endTime))
          AND m.leagueId IN :leagueIds
          AND (:sportId IS NULL OR m.sportId = :sportId)
        ORDER BY 
          CASE WHEN m.status IN :liveStatuses THEN 0 ELSE 1 END ASC,
          m.kickoffTime ASC
    """)
    fun findDashboardMatchesByLeagueIds(
        @Param("liveStatuses") liveStatuses: List<MatchStatus>,
        @Param("scheduledStatus") scheduledStatus: MatchStatus,
        @Param("startTime") startTime: Instant,
        @Param("endTime") endTime: Instant,
        @Param("leagueIds") leagueIds: Collection<UUID>,
        @Param("sportId") sportId: UUID?
    ): List<MatchJpaEntity>

    @Query("""
        SELECT m FROM MatchJpaEntity m
        WHERE m.status IN :statuses
        AND m.kickoffTime >= :startTime
        AND m.kickoffTime <= :endTime
        AND (:sportId IS NULL OR m.sportId = :sportId)
        ORDER BY m.kickoffTime ASC
    """)
    fun findUpcomingMatches(
        @Param("statuses") statuses: List<MatchStatus>,
        @Param("startTime") startTime: Instant,
        @Param("endTime") endTime: Instant,
        @Param("sportId") sportId: UUID?
    ): List<MatchJpaEntity>
}

