package com.ligadospalpites.users.infrastructure.persistence

import com.ligadospalpites.users.domain.models.User
import jakarta.persistence.*
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "tbl_users")
class UserJpaEntity(
    @Id
    val id: UUID = UUID.randomUUID(),

    @Column(name = "firebase_uid", nullable = false, unique = true, length = 128)
    val firebaseUid: String = "",

    @Column(nullable = false, length = 255)
    val email: String = "",

    @Column(nullable = false, length = 255)
    val name: String = "",

    @Column(name = "avatar_url", nullable = true, length = 512)
    val avatarUrl: String? = null,

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant = Instant.now()
) {
    fun toDomain(): User = User(
        id = id,
        firebaseUid = firebaseUid,
        email = email,
        name = name,
        avatarUrl = avatarUrl,
        createdAt = createdAt
    )

    companion object {
        fun fromDomain(user: User): UserJpaEntity = UserJpaEntity(
            id = user.id,
            firebaseUid = user.firebaseUid,
            email = user.email,
            name = user.name,
            avatarUrl = user.avatarUrl,
            createdAt = user.createdAt
        )
    }
}
