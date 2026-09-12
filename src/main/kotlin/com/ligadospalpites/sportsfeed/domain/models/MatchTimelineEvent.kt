package com.ligadospalpites.sportsfeed.domain.models

import java.time.Instant
import java.util.UUID

enum class TimelineEventType {
    GOAL,
    YELLOW_CARD,
    RED_CARD,
    SUBSTITUTION,
    PENALTY,
    VAR,
    CHANCE,
    PERIOD_START,
    PERIOD_END,
    INFO
}

data class MatchTimelineEvent(
    val id: UUID = UUID.randomUUID(),
    val matchId: UUID,
    val minute: Int,
    val extraMinute: Int? = null,
    val period: String = "1H",
    val eventType: TimelineEventType,
    val teamName: String? = null,
    val playerName: String? = null,
    val playerAssistName: String? = null,
    val description: String,
    val isImportant: Boolean = false,
    val createdAt: Instant = Instant.now()
) {
    val displayMinute: String
        get() = if (extraMinute != null && extraMinute > 0) "$minute+$extraMinute'" else "$minute'"
}
