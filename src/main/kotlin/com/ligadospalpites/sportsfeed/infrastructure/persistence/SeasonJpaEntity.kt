package com.ligadospalpites.sportsfeed.infrastructure.persistence

import com.ligadospalpites.sportsfeed.domain.models.Season
import jakarta.persistence.*
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "tbl_seasons")
class SeasonJpaEntity(
    @Id
    val id: UUID = UUID.randomUUID(),

    @Column(name = "league_id", nullable = false)
    val leagueId: UUID = UUID.randomUUID(),

    @Column(nullable = false, length = 50)
    val name: String = "",

    @Column(name = "start_date", nullable = false)
    val startDate: Instant = Instant.now(),

    @Column(name = "end_date", nullable = false)
    val endDate: Instant = Instant.now(),

    @Column(name = "is_active", nullable = false)
    val isActive: Boolean = true,

    @Column(name = "external_season_code", nullable = false)
    val externalSeasonCode: Int = 0,

    @Column(name = "created_at", nullable = false)
    val createdAt: Instant = Instant.now()
) {
    fun toDomain(): Season = Season(
        id = id,
        leagueId = leagueId,
        name = name,
        startDate = startDate,
        endDate = endDate,
        isActive = isActive,
        externalSeasonCode = externalSeasonCode,
        createdAt = createdAt
    )

    companion object {
        fun fromDomain(domain: Season): SeasonJpaEntity = SeasonJpaEntity(
            id = domain.id,
            leagueId = domain.leagueId,
            name = domain.name,
            startDate = domain.startDate,
            endDate = domain.endDate,
            isActive = domain.isActive,
            externalSeasonCode = domain.externalSeasonCode,
            createdAt = domain.createdAt
        )
    }
}
