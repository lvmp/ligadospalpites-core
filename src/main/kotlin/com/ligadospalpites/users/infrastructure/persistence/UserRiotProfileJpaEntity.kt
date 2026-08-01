package com.ligadospalpites.users.infrastructure.persistence

import com.ligadospalpites.users.domain.models.UserRiotProfile
import jakarta.persistence.*
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "tbl_user_riot_profiles")
class UserRiotProfileJpaEntity(
    @Id
    val id: UUID = UUID.randomUUID(),

    @Column(name = "user_id", nullable = false)
    val userId: UUID = UUID.randomUUID(),

    @Column(name = "puuid", nullable = false, unique = true, length = 255)
    val puuid: String = "",

    @Column(name = "game_name", nullable = false, length = 100)
    val gameName: String = "",

    @Column(name = "tag_line", nullable = false, length = 50)
    val tagLine: String = "",

    @Column(name = "lol_rank", length = 50)
    val lolRank: String? = null,

    @Column(name = "valorant_rank", length = 50)
    val valorantRank: String? = null,

    @Column(name = "updated_at", nullable = false)
    val updatedAt: Instant = Instant.now()
) {
    fun toDomain(): UserRiotProfile = UserRiotProfile(
        id = id,
        userId = userId,
        puuid = puuid,
        gameName = gameName,
        tagLine = tagLine,
        lolRank = lolRank,
        valorantRank = valorantRank,
        updatedAt = updatedAt
    )

    companion object {
        fun fromDomain(profile: UserRiotProfile): UserRiotProfileJpaEntity = UserRiotProfileJpaEntity(
            id = profile.id,
            userId = profile.userId,
            puuid = profile.puuid,
            gameName = profile.gameName,
            tagLine = profile.tagLine,
            lolRank = profile.lolRank,
            valorantRank = profile.valorantRank,
            updatedAt = profile.updatedAt
        )
    }
}
