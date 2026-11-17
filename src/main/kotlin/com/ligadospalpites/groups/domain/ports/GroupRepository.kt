package com.ligadospalpites.groups.domain.ports

import com.ligadospalpites.groups.domain.models.Group
import com.ligadospalpites.shared.domain.models.GroupId

interface GroupRepository {
    fun findById(id: GroupId): Group?
    fun save(group: Group): Group
    fun existsById(id: GroupId): Boolean
    fun deleteById(id: GroupId)
}
