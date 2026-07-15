package com.ligadospalpites.payments.infrastructure.web

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty

@JsonIgnoreProperties(ignoreUnknown = true)
data class RevenueCatWebhookRequest(
    @JsonProperty("api_version")
    val apiVersion: String? = null,
    val event: RevenueCatEventDto = RevenueCatEventDto()
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class RevenueCatEventDto(
    val id: String? = null,
    val type: String? = null,
    @JsonProperty("app_user_id")
    val appUserId: String? = null,
    @JsonProperty("product_id")
    val productId: String? = null,
    @JsonProperty("entitlement_id")
    val entitlementId: String? = null,
    @JsonProperty("entitlement_ids")
    val entitlementIds: List<String>? = null,
    @JsonProperty("expiration_at_ms")
    val expirationAtMs: Long? = null,
    @JsonProperty("purchased_at_ms")
    val purchasedAtMs: Long? = null,
    val store: String? = null,
    val environment: String? = null
)
