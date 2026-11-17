package com.ligadospalpites.groups.domain.models

enum class LeaderboardPhase(val keySuffix: String) {
    OVERALL("overall"),
    GROUP_STAGE("group-stage"),
    KNOCKOUT("knockout");

    companion object {
        fun fromString(value: String?): LeaderboardPhase {
            if (value == null) return OVERALL
            return entries.firstOrNull { it.keySuffix.equals(value.trim(), ignoreCase = true) } ?: OVERALL
        }
    }
}
