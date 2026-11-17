package com.ligadospalpites.groups.domain.models

import com.ligadospalpites.groups.domain.exceptions.NotGroupAdminException
import com.ligadospalpites.shared.domain.models.GroupId
import com.ligadospalpites.shared.domain.models.UserId
import java.time.Instant

data class Group(
    val id: GroupId = GroupId.random(),
    val name: String,
    val creatorId: UserId,
    val scoringRulesJson: String = "{}",
    val logoUrl: String? = null,
    val createdAt: Instant = Instant.now()
) {
    init {
        require(name.isNotBlank()) { "O nome do grupo não pode ser vazio." }
    }

    fun validateCanExpel(requesterId: UserId) {
        if (creatorId != requesterId) {
            throw NotGroupAdminException("Apenas o criador do grupo pode expulsar membros.")
        }
    }
}
