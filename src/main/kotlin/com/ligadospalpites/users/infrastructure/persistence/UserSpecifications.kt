package com.ligadospalpites.users.infrastructure.persistence

import jakarta.persistence.criteria.Predicate
import org.springframework.data.jpa.domain.Specification
import java.util.UUID

object UserSpecifications {
    fun filterUsers(
        query: String? = null,
        name: String? = null,
        email: String? = null,
        id: UUID? = null
    ): Specification<UserJpaEntity> {
        return Specification { root, _, cb ->
            val predicates = mutableListOf<Predicate>()

            if (id != null) {
                predicates.add(cb.equal(root.get<UUID>("id"), id))
            }

            if (!name.isNullOrBlank()) {
                val nameTerm = "%${name.trim().lowercase()}%"
                predicates.add(cb.like(cb.lower(root.get("name")), nameTerm))
            }

            if (!email.isNullOrBlank()) {
                val emailTerm = "%${email.trim().lowercase()}%"
                predicates.add(cb.like(cb.lower(root.get("email")), emailTerm))
            }

            if (!query.isNullOrBlank()) {
                val qTrim = query.trim()
                val qLower = "%${qTrim.lowercase()}%"

                val queryUuid = try {
                    UUID.fromString(qTrim)
                } catch (_: Exception) {
                    null
                }

                val queryPredicates = mutableListOf<Predicate>(
                    cb.like(cb.lower(root.get("name")), qLower),
                    cb.like(cb.lower(root.get("email")), qLower)
                )

                if (queryUuid != null) {
                    queryPredicates.add(cb.equal(root.get<UUID>("id"), queryUuid))
                }

                predicates.add(cb.or(*queryPredicates.toTypedArray()))
            }

            if (predicates.isEmpty()) {
                cb.conjunction()
            } else {
                cb.and(*predicates.toTypedArray())
            }
        }
    }
}
