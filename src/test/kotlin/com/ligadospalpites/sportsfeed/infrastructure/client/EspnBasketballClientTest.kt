package com.ligadospalpites.sportsfeed.infrastructure.client

import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class EspnBasketballClientTest {

    private val objectMapper = ObjectMapper()

    @Test
    fun `should serialize and deserialize PeriodScoresDto correctly`() {
        val dto = PeriodScoresDto(
            home = listOf(28, 30, 22, 25),
            away = listOf(24, 25, 29, 21),
            homeOvertime = 0,
            awayOvertime = 0
        )

        val json = objectMapper.writeValueAsString(dto)
        assertTrue(json.contains("\"home\":[28,30,22,25]"))
        assertTrue(json.contains("\"away\":[24,25,29,21]"))

        val read = objectMapper.readValue(json, PeriodScoresDto::class.java)
        assertEquals(dto.home, read.home)
        assertEquals(dto.away, read.away)
        assertEquals(0, read.homeOvertime)
        assertEquals(0, read.awayOvertime)
    }
}
