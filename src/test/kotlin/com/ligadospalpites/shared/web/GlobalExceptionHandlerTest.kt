package com.ligadospalpites.shared.web

import com.ligadospalpites.predictions.domain.exceptions.MatchNotFoundException
import com.ligadospalpites.predictions.domain.exceptions.PredictionLockedException
import com.ligadospalpites.predictions.domain.exceptions.SpecialPredictionLockedException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus

class GlobalExceptionHandlerTest {

    private val handler = GlobalExceptionHandler()

    @Test
    fun `should map PredictionLockedException to 400 Bad Request with exact error code`() {
        val ex = PredictionLockedException("Este jogo já iniciou. Palpites trancados!")
        val response = handler.handlePredictionLocked(ex)

        assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
        assertEquals("PREDICTION_LOCKED", response.body?.error)
        assertEquals("Este jogo já iniciou. Palpites trancados!", response.body?.message)
    }

    @Test
    fun `should map SpecialPredictionLockedException to 400 Bad Request with exact error code`() {
        val ex = SpecialPredictionLockedException("O torneio já começou. Palpites de longo prazo trancados!")
        val response = handler.handleSpecialPredictionLocked(ex)

        assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
        assertEquals("SPECIAL_PREDICTION_LOCKED", response.body?.error)
        assertEquals("O torneio já começou. Palpites de longo prazo trancados!", response.body?.message)
    }

    @Test
    fun `should map MatchNotFoundException to 404 Not Found`() {
        val ex = MatchNotFoundException("Partida não encontrada.")
        val response = handler.handleMatchNotFound(ex)

        assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
        assertEquals("MATCH_NOT_FOUND", response.body?.error)
        assertEquals("Partida não encontrada.", response.body?.message)
    }
}
