package com.ligadospalpites.payments.domain.ports

import com.ligadospalpites.payments.domain.models.RevenueCatEvent

interface RevenueCatEventRepository {
    fun existsById(id: String): Boolean
    fun save(event: RevenueCatEvent): RevenueCatEvent
}
