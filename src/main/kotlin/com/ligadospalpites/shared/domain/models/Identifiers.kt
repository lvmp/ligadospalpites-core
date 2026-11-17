package com.ligadospalpites.shared.domain.models

import java.util.UUID

@JvmInline
value class UserId(val value: UUID) {
    override fun toString(): String = value.toString()
    companion object {
        fun fromString(str: String): UserId = UserId(UUID.fromString(str))
        fun random(): UserId = UserId(UUID.randomUUID())
    }
}

@JvmInline
value class MatchId(val value: UUID) {
    override fun toString(): String = value.toString()
    companion object {
        fun fromString(str: String): MatchId = MatchId(UUID.fromString(str))
        fun random(): MatchId = MatchId(UUID.randomUUID())
    }
}

@JvmInline
value class LeagueId(val value: UUID) {
    override fun toString(): String = value.toString()
    companion object {
        fun fromString(str: String): LeagueId = LeagueId(UUID.fromString(str))
        fun random(): LeagueId = LeagueId(UUID.randomUUID())
    }
}

@JvmInline
value class GroupId(val value: UUID) {
    override fun toString(): String = value.toString()
    companion object {
        fun fromString(str: String): GroupId = GroupId(UUID.fromString(str))
        fun random(): GroupId = GroupId(UUID.randomUUID())
    }
}
