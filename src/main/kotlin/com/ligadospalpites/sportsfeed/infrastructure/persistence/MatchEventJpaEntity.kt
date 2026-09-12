package com.ligadospalpites.sportsfeed.infrastructure.persistence

import com.ligadospalpites.sportsfeed.domain.models.MatchTimelineEvent
import com.ligadospalpites.sportsfeed.domain.models.TimelineEventType
import jakarta.persistence.*
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "tbl_match_events")
class MatchEventJpaEntity(
    @Id
    val id: UUID = UUID.randomUUID(),

    @Column(name = "match_id", nullable = false)
    val matchId: UUID = UUID.randomUUID(),

    @Column(name = "minute", nullable = false)
    val minute: Int = 0,

    @Column(name = "extra_minute")
    val extraMinute: Int? = null,

    @Column(name = "period", nullable = false, length = 20)
    val period: String = "1H",

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 50)
    val eventType: TimelineEventType = TimelineEventType.INFO,

    @Column(name = "team_name", length = 150)
    val teamName: String? = null,

    @Column(name = "player_name", length = 150)
    val playerName: String? = null,

    @Column(name = "player_assist_name", length = 150)
    val playerAssistName: String? = null,

    @Column(name = "description", nullable = false, columnDefinition = "text")
    val description: String = "",

    @Column(name = "is_important", nullable = false)
    val isImportant: Boolean = false,

    @Column(name = "created_at", nullable = false)
    val createdAt: Instant = Instant.now()
) {
    fun toDomain(): MatchTimelineEvent = MatchTimelineEvent(
        id = id,
        matchId = matchId,
        minute = minute,
        extraMinute = extraMinute,
        period = period,
        eventType = eventType,
        teamName = teamName,
        playerName = playerName,
        playerAssistName = playerAssistName,
        description = description,
        isImportant = isImportant,
        createdAt = createdAt
    )

    companion object {
        fun fromDomain(event: MatchTimelineEvent): MatchEventJpaEntity = MatchEventJpaEntity(
            id = event.id,
            matchId = event.matchId,
            minute = event.minute,
            extraMinute = event.extraMinute,
            period = event.period,
            eventType = event.eventType,
            teamName = event.teamName,
            playerName = event.playerName,
            playerAssistName = event.playerAssistName,
            description = event.description,
            isImportant = event.isImportant,
            createdAt = event.createdAt
        )
    }
}
