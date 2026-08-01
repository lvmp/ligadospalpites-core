package com.ligadospalpites.users.infrastructure.web

import com.ligadospalpites.shared.identity.UserResolver
import com.ligadospalpites.users.application.usecases.GetUserEntitlementsUseCase
import com.ligadospalpites.users.application.usecases.GetUserRiotProfileUseCase
import com.ligadospalpites.users.application.usecases.GetUserStateUseCase
import com.ligadospalpites.users.application.usecases.LinkRiotProfileUseCase
import com.ligadospalpites.users.domain.models.UserRiotProfile
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import org.springframework.http.ResponseEntity
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.*
import java.util.UUID

data class LinkRiotProfileRequest(
    val gameName: String,
    val tagLine: String
)

@RestController
@RequestMapping("/api/v1/users")
class UserController(
    private val getUserEntitlementsUseCase: GetUserEntitlementsUseCase,
    private val getUserStateUseCase: GetUserStateUseCase,
    private val linkRiotProfileUseCase: LinkRiotProfileUseCase,
    private val getUserRiotProfileUseCase: GetUserRiotProfileUseCase,
    private val userResolver: UserResolver
) {

    @SecurityRequirement(name = "bearerAuth")
    @SecurityRequirement(name = "X-User-Id")
    @GetMapping("/me/entitlements")
    fun getMyEntitlements(
        @RequestHeader(value = "X-User-Id", required = false) userIdHeader: String?,
        authentication: Authentication?
    ): ResponseEntity<GetUserEntitlementsUseCase.UserEntitlementsResult> {
        val userUUID = userResolver.resolveAuthenticatedUser(userIdHeader, authentication)
        val result = getUserEntitlementsUseCase(userUUID)
        return ResponseEntity.ok(result)
    }

    @SecurityRequirement(name = "bearerAuth")
    @SecurityRequirement(name = "X-User-Id")
    @GetMapping("/me/state")
    fun getMyState(
        @RequestHeader(value = "X-User-Id", required = false) userIdHeader: String?,
        authentication: Authentication?
    ): ResponseEntity<GetUserStateUseCase.UserStateResult> {
        val userUUID = userResolver.resolveAuthenticatedUser(userIdHeader, authentication)
        val result = getUserStateUseCase(userUUID)
        return ResponseEntity.ok(result)
    }

    @SecurityRequirement(name = "bearerAuth")
    @SecurityRequirement(name = "X-User-Id")
    @GetMapping("/me/riot-profile")
    fun getMyRiotProfile(
        @RequestHeader(value = "X-User-Id", required = false) userIdHeader: String?,
        authentication: Authentication?
    ): ResponseEntity<UserRiotProfile> {
        val userUUID = userResolver.resolveAuthenticatedUser(userIdHeader, authentication)
        val profile = getUserRiotProfileUseCase.execute(userUUID)
            ?: return ResponseEntity.notFound().build()
        return ResponseEntity.ok(profile)
    }

    @SecurityRequirement(name = "bearerAuth")
    @SecurityRequirement(name = "X-User-Id")
    @PostMapping("/me/riot-profile")
    fun linkMyRiotProfile(
        @RequestHeader(value = "X-User-Id", required = false) userIdHeader: String?,
        authentication: Authentication?,
        @RequestBody request: LinkRiotProfileRequest
    ): ResponseEntity<UserRiotProfile> {
        val userUUID = userResolver.resolveAuthenticatedUser(userIdHeader, authentication)
        val profile = linkRiotProfileUseCase.execute(
            LinkRiotProfileUseCase.Command(
                userId = userUUID,
                gameName = request.gameName,
                tagLine = request.tagLine
            )
        )
        return ResponseEntity.ok(profile)
    }
}
