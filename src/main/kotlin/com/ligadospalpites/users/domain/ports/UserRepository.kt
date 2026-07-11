package com.ligadospalpites.users.domain.ports

import com.ligadospalpites.users.domain.models.User
import java.util.UUID

interface UserRepository {
    fun findByFirebaseUid(firebaseUid: String): User?
    fun findById(id: UUID): User?
    fun save(user: User): User
}
