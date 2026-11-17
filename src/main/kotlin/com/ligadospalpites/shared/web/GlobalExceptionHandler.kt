package com.ligadospalpites.shared.web

import com.ligadospalpites.groups.domain.exceptions.GroupDomainException
import com.ligadospalpites.groups.domain.exceptions.GroupNotFoundException
import com.ligadospalpites.groups.domain.exceptions.NotGroupAdminException
import com.ligadospalpites.predictions.domain.exceptions.MatchNotFoundException
import com.ligadospalpites.predictions.domain.exceptions.PredictionDomainException
import com.ligadospalpites.predictions.domain.exceptions.PredictionLockedException
import com.ligadospalpites.predictions.domain.exceptions.SpecialPredictionLockedException
import com.ligadospalpites.sportsfeed.domain.exceptions.LeagueInactiveException
import com.ligadospalpites.sportsfeed.domain.exceptions.SportLockedException
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ControllerAdvice
import org.springframework.web.bind.annotation.ExceptionHandler

data class ErrorResponse(
    val error: String,
    val message: String
)

@ControllerAdvice
class GlobalExceptionHandler {

    private val log = LoggerFactory.getLogger(GlobalExceptionHandler::class.java)

    @ExceptionHandler(PredictionLockedException::class)
    fun handlePredictionLocked(ex: PredictionLockedException): ResponseEntity<ErrorResponse> {
        log.warn("Tentativa de palpite em jogo já trancado: {}", ex.message)
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(ErrorResponse(error = "PREDICTION_LOCKED", message = ex.message ?: "Este jogo já iniciou. Palpites trancados!"))
    }

    @ExceptionHandler(SpecialPredictionLockedException::class)
    fun handleSpecialPredictionLocked(ex: SpecialPredictionLockedException): ResponseEntity<ErrorResponse> {
        log.warn("Tentativa de palpite especial trancado: {}", ex.message)
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(ErrorResponse(error = "SPECIAL_PREDICTION_LOCKED", message = ex.message ?: "O torneio já começou. Palpites de longo prazo trancados!"))
    }

    @ExceptionHandler(MatchNotFoundException::class)
    fun handleMatchNotFound(ex: MatchNotFoundException): ResponseEntity<ErrorResponse> {
        log.warn("Partida não encontrada: {}", ex.message)
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
            .body(ErrorResponse(error = "MATCH_NOT_FOUND", message = ex.message ?: "Partida não encontrada."))
    }

    @ExceptionHandler(PredictionDomainException::class)
    fun handlePredictionDomainException(ex: PredictionDomainException): ResponseEntity<ErrorResponse> {
        log.warn("Erro de domínio de palpites: {}", ex.message)
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(ErrorResponse(error = "PREDICTION_ERROR", message = ex.message ?: "Erro nas regras de palpite."))
    }

    @ExceptionHandler(GroupNotFoundException::class)
    fun handleGroupNotFound(ex: GroupNotFoundException): ResponseEntity<ErrorResponse> {
        log.warn("Grupo não encontrado: {}", ex.message)
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
            .body(ErrorResponse(error = "GROUP_NOT_FOUND", message = ex.message ?: "Grupo não encontrado."))
    }

    @ExceptionHandler(NotGroupAdminException::class)
    fun handleNotGroupAdmin(ex: NotGroupAdminException): ResponseEntity<ErrorResponse> {
        log.warn("Acesso negado para administração de grupo: {}", ex.message)
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
            .body(ErrorResponse(error = "NOT_GROUP_ADMIN", message = ex.message ?: "Apenas o criador do grupo pode expulsar membros."))
    }

    @ExceptionHandler(SportLockedException::class)
    fun handleSportLocked(ex: SportLockedException): ResponseEntity<ErrorResponse> {
        log.warn("Tentativa de acesso a esporte bloqueado: {}", ex.message)
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
            .body(ErrorResponse(error = "SPORT_LOCKED", message = ex.message ?: "Assine o plano MULTI_SPORT para acessar este esporte."))
    }

    @ExceptionHandler(LeagueInactiveException::class)
    fun handleLeagueInactive(ex: LeagueInactiveException): ResponseEntity<ErrorResponse> {
        log.warn("Tentativa de acesso a liga inativa: {}", ex.message)
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
            .body(ErrorResponse(error = "LEAGUE_INACTIVE", message = ex.message ?: "Esta liga está inativa."))
    }

    @ExceptionHandler(GroupDomainException::class)
    fun handleGroupDomainException(ex: GroupDomainException): ResponseEntity<ErrorResponse> {
        log.warn("Erro de domínio de grupos: {}", ex.message)
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(ErrorResponse(error = "GROUP_ERROR", message = ex.message ?: "Erro nas regras do grupo."))
    }

    @ExceptionHandler(IllegalArgumentException::class)
    fun handleIllegalArgument(ex: IllegalArgumentException): ResponseEntity<ErrorResponse> {
        log.warn("Argumento inválido: {}", ex.message)
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(ErrorResponse(error = "INVALID_ARGUMENT", message = ex.message ?: "Requisição inválida."))
    }

    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleValidationErrors(ex: MethodArgumentNotValidException): ResponseEntity<ErrorResponse> {
        val errorMessage = ex.bindingResult.fieldErrors.joinToString("; ") { "${it.field}: ${it.defaultMessage}" }
        log.warn("Falha de validação de request: {}", errorMessage)
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(ErrorResponse(error = "VALIDATION_FAILED", message = errorMessage))
    }

    @ExceptionHandler(Exception::class)
    fun handleGenericException(ex: Exception): ResponseEntity<ErrorResponse> {
        log.error("Exceção não tratada capturada pelo GlobalExceptionHandler: {}", ex.message, ex)
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(ErrorResponse(error = "INTERNAL_SERVER_ERROR", message = "Ocorreu um erro interno."))
    }
}
