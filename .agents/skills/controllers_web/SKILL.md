---
name: controllers_web
description: Diretrizes e boas práticas para estruturar a camada de controllers REST, validação de DTOs e mapeamento global de exceções em Kotlin e Spring Boot.
---

# Camada de Controllers REST (Web HTTP)

Esta skill orienta o agente na estruturação e padronização da camada de **Web Controllers** no projeto **Liga dos Palpites**, garantindo isolamento arquitetural e conformidade com as convenções RESTful.

---

## 💡 Princípios de Design e Estrutura

Os controllers REST atuam como adaptadores de interface que traduzem requisições HTTP em chamadas legíveis pelo sistema de domínio e aplicação.

### 1. Descentralização e Coesão Modular
Cada módulo (`sportsfeed`, `predictions`, `groups`, `users`, `notifications`, `payments`) deve gerenciar seus próprios controllers dentro da pasta `infrastructure/web/`.
- Evite criar controllers com múltiplas responsabilidades não coesas (ex: um único controller cuidando de grupos e palpites).

### 2. Isolamento de DTOs e Tipos do Framework
- **Regra Rígida**: Controllers **nunca** devem interagir diretamente ou expor Entidades JPA (ex: `UserJpaEntity`) ou agregados do Domínio.
- Mapeie sempre as requisições para **Request DTOs** e as respostas para **Response DTOs** imutáveis.
- Isso impede que alterações nas regras de dados do banco de dados quebrem o contrato com o Flutter.

---

## 🛠️ Validação de Inputs e Trancamento de Janelas

Utilize as anotações do Jakarta Validation (`@Valid`, `@NotNull`, `@Min`, etc.) para validação sintática das requisições na entrada do controller.

### Exemplo de Controller com Validação Temporal:
```kotlin
package com.ligadospalpites.predictions.infrastructure.web

import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.time.Instant

@RestController
@RequestMapping("/api/v1/predictions")
class PredictionController(
    private val submitPredictionUseCase: SubmitPredictionUseCase,
    private val matchRepository: MatchRepository
) {

    @PostMapping
    fun submitPrediction(
        @RequestHeader("X-User-Id") userId: String,
        @RequestBody @Valid request: SubmitPredictionRequest
    ): ResponseEntity<Any> {
        val match = matchRepository.findById(request.matchId) 
            ?: return ResponseEntity.notFound().build()

        // Validação da Janela de Tempo antes de processar
        if (Instant.now().isAfter(match.kickoffTime)) {
            return ResponseEntity.badRequest().body(
                mapOf("error" to "PREDICTION_LOCKED", "message" to "A partida já começou.")
            )
        }

        val result = submitPredictionUseCase(request.toCommand(userId))
        return ResponseEntity.ok(result)
    }
}
```

---

## 🎯 Tratamento Centralizado de Exceções de Negócio

Para que o aplicativo móvel reaja de forma estruturada a erros (como redirecionar o usuário para a tela de paywall), o backend traduz exceções de domínio em códigos de erro específicos através de um `@ControllerAdvice`.

### Padrão de Tratamento Global:
```kotlin
package com.ligadospalpites.shared.web

import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ControllerAdvice
import org.springframework.web.bind.annotation.ExceptionHandler

@ControllerAdvice
class GlobalExceptionHandler {

    @ExceptionHandler(SportLockedException::class)
    fun handleSportLocked(ex: SportLockedException): ResponseEntity<ErrorResponse> {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
            .body(ErrorResponse(
                error = "SPORT_LOCKED",
                message = ex.message ?: "Este esporte está bloqueado.",
                unlockedRequiredProductId = ex.requiredProductId
            ))
    }

    @ExceptionHandler(PredictionPeriodClosedException::class)
    fun handlePredictionClosed(ex: PredictionPeriodClosedException): ResponseEntity<ErrorResponse> {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(ErrorResponse(
                error = "PREDICTION_LOCKED",
                message = "O período de palpites foi encerrado."
            ))
    }
}

data class ErrorResponse(
    val error: String,
    val message: String,
    val unlockedRequiredProductId: String? = null
)
```

---

## ⚠️ O que EVITAR (Anti-patterns)

* ❌ **Fazer Queries Diretas no Controller**: Evite injetar e chamar `SpringDataRepositories` diretamente para executar transações complexas dentro do controller. Use sempre Casos de Uso/Portas.
* ❌ **Vazar Tipos e Detalhes do Framework**: Nunca lance ou propague exceções cruas do Spring Data ou do Flyway (ex: `DataIntegrityViolationException`) no payload HTTP do cliente, pois isso revela detalhes de implementação e causa falhas de segurança.
* ❌ **Importar Dependências do Servlet no Domínio**: Casos de uso e modelos do domínio nunca devem importar classes Web (`HttpServletRequest`, `ResponseEntity`, `@RequestParam`).
