package com.ligadospalpites.shared.web

import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.time.Instant

@RestController
@RequestMapping("/api/v1/public")
class PingController {

    @GetMapping("/ping")
    fun ping(): ResponseEntity<PingResponse> {
        return ResponseEntity.ok(
            PingResponse(
                status = "UP",
                message = "Liga dos Palpites Core service is healthy",
                timestamp = Instant.now().toString()
            )
        )
    }
}

data class PingResponse(
    val status: String,
    val message: String,
    val timestamp: String
)
