package com.ligadospalpites.shared.web

import com.ligadospalpites.predictions.domain.exceptions.LeagueNotFoundException
import com.ligadospalpites.predictions.domain.exceptions.MatchNotFoundException
import com.ligadospalpites.predictions.domain.exceptions.PredictionLockedException
import com.ligadospalpites.predictions.domain.exceptions.SpecialPredictionLockedException
import com.ligadospalpites.sportsfeed.domain.exceptions.LeagueInactiveException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.springframework.core.MethodParameter
import org.springframework.http.HttpStatus
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.mock.http.MockHttpInputMessage
import org.springframework.web.bind.MissingServletRequestParameterException
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException
import java.util.UUID

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

    @Test
    fun `should map MethodArgumentTypeMismatchException for UUID to 400 Bad Request with clear message`() {
        val param = Mockito.mock(MethodParameter::class.java)
        val ex = MethodArgumentTypeMismatchException(
            "libertadores",
            UUID::class.java,
            "leagueId",
            param,
            IllegalArgumentException("Invalid UUID string: libertadores")
        )
        val response = handler.handleMethodArgumentTypeMismatch(ex)

        assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
        assertEquals("INVALID_ARGUMENT", response.body?.error)
        assertEquals("O parâmetro 'leagueId' deve ser um UUID válido.", response.body?.message)
    }

    @Test
    fun `should map MethodArgumentTypeMismatchException for non-UUID to 400 Bad Request`() {
        val param = Mockito.mock(MethodParameter::class.java)
        val ex = MethodArgumentTypeMismatchException(
            "abc",
            Int::class.javaObjectType,
            "page",
            param,
            IllegalArgumentException("For input string: abc")
        )
        val response = handler.handleMethodArgumentTypeMismatch(ex)

        assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
        assertEquals("INVALID_ARGUMENT", response.body?.error)
        assertEquals("O parâmetro 'page' possui um formato inválido. Esperado: Integer.", response.body?.message)
    }

    @Test
    fun `should map LeagueNotFoundException to 404 Not Found`() {
        val ex = LeagueNotFoundException("Liga não encontrada.")
        val response = handler.handleLeagueNotFound(ex)

        assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
        assertEquals("LEAGUE_NOT_FOUND", response.body?.error)
        assertEquals("Liga não encontrada.", response.body?.message)
    }

    @Test
    fun `should map LeagueInactiveException to 403 Forbidden`() {
        val ex = LeagueInactiveException("Esta liga está inativa.")
        val response = handler.handleLeagueInactive(ex)

        assertEquals(HttpStatus.FORBIDDEN, response.statusCode)
        assertEquals("LEAGUE_INACTIVE", response.body?.error)
        assertEquals("Esta liga está inativa.", response.body?.message)
    }

    @Test
    fun `should map HttpMessageNotReadableException to 400 Bad Request`() {
        val ex = HttpMessageNotReadableException("Malformed JSON", MockHttpInputMessage(ByteArray(0)))
        val response = handler.handleHttpMessageNotReadable(ex)

        assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
        assertEquals("INVALID_ARGUMENT", response.body?.error)
        assertEquals("Corpo da requisição inválido ou mal formatado.", response.body?.message)
    }

    @Test
    fun `should map MissingServletRequestParameterException to 400 Bad Request`() {
        val ex = MissingServletRequestParameterException("sportId", "UUID")
        val response = handler.handleMissingParams(ex)

        assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
        assertEquals("INVALID_ARGUMENT", response.body?.error)
        assertEquals("O parâmetro obrigatório 'sportId' não foi informado.", response.body?.message)
    }
}
