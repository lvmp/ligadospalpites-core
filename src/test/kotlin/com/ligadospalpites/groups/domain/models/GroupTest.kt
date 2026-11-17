package com.ligadospalpites.groups.domain.models

import com.ligadospalpites.groups.domain.exceptions.NotGroupAdminException
import com.ligadospalpites.shared.domain.models.GroupId
import com.ligadospalpites.shared.domain.models.UserId
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class GroupTest {

    private val creatorId = UserId.random()
    private val otherUserId = UserId.random()

    @Test
    fun `should create valid group`() {
        val group = Group(
            id = GroupId.random(),
            name = "Galera do Futebol",
            creatorId = creatorId
        )

        assertEquals("Galera do Futebol", group.name)
        assertEquals(creatorId, group.creatorId)
    }

    @Test
    fun `should throw exception when group name is blank`() {
        assertThrows<IllegalArgumentException> {
            Group(
                name = "   ",
                creatorId = creatorId
            )
        }
    }

    @Test
    fun `should allow creator to expel members`() {
        val group = Group(
            name = "Amigos da Pelada",
            creatorId = creatorId
        )

        assertDoesNotThrow {
            group.validateCanExpel(creatorId)
        }
    }

    @Test
    fun `should throw NotGroupAdminException when non-creator tries to expel member`() {
        val group = Group(
            name = "Amigos da Pelada",
            creatorId = creatorId
        )

        assertThrows<NotGroupAdminException> {
            group.validateCanExpel(otherUserId)
        }
    }
}
