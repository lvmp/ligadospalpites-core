package com.ligadospalpites.users.infrastructure.web

import com.ligadospalpites.users.application.usecases.FirebaseUserMigrationUseCase
import com.ligadospalpites.users.application.usecases.MigrationSummaryDto
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/internal/migrations")
class FirebaseMigrationController(
    private val migrationUseCase: FirebaseUserMigrationUseCase
) {

    @PostMapping("/firebase")
    fun runMigration(
        @RequestParam(name = "simulate", defaultValue = "false") simulate: Boolean
    ): ResponseEntity<MigrationSummaryDto> {
        val summary = migrationUseCase.execute(forceSimulation = simulate)
        return ResponseEntity.ok(summary)
    }
}
