---
name: casos_de_uso
description: Diretrizes e boas práticas para estruturar a camada de aplicação utilizando Casos de Uso (Use Cases / Interactors) em Kotlin e Spring Boot.
---

# Casos de Uso (Use Cases / Interactors)

Esta skill orienta o agente na modelagem e implementação da **Camada de Aplicação** usando **Casos de Uso** (também chamados de *Interactors*). Eles servem para orquestrar o fluxo do sistema, garantindo a separação limpa entre a interface de entrega (Controllers/API) e as regras de negócio puras (Domínio).

---

## 💡 O que é um Caso de Uso?

Um Caso de Uso representa uma única operação de negócio realizável por um ator no sistema (ex: `RegistrarPalpite`, `CriarGrupo`, `VisualizarRanking`). 

Na Clean Architecture e no DDD, a camada de aplicação (onde os Casos de Uso vivem):
- **Não** contém regras de domínio complexas.
- **Orquestra** a recuperação de agregados via Repositórios, executa a lógica contida neles e persiste o novo estado.
- Define a **fronteira transacional** da operação (unidade de trabalho).

---

## 🚀 Princípios Fundamentais

### 1. Princípio da Responsabilidade Única (SRP)
Cada Caso de Uso deve fazer exatamente uma única coisa de negócio. Em vez de ter um `GrupoService` com 15 métodos diferentes, prefira classes isoladas como `CriarGrupoUseCase`, `AdicionarMembroAoGrupoUseCase`, `SairDoGrupoUseCase`.
*Benefício:* Legibilidade extrema, facilidade para testar unitariamente e menos conflitos de mesclagem (merge conflicts).

### 2. Orquestração, não Decisão de Domínio
O caso de uso deve atuar como um "gerente de fluxo":
1. Recebe um comando de entrada (DTO / Request).
2. Valida o formato/presença dos dados de entrada (validação sintática).
3. Busca os Agregados necessários no banco de dados.
4. Invoca o método de negócio do Agregado (ex: `grupo.adicionarMembro(usuarioId)`).
5. Salva o Agregado de volta através do repositório.
6. Retorna um resultado (DTO / Response).

### 3. Independência de Framework
O caso de uso não deve saber se está sendo chamado por um controller REST HTTP, por um consumidor de fila (RabbitMQ/Kafka) ou por uma CLI. Evite importar classes como `HttpServletRequest`, `@PathVariable` ou classes específicas de transporte.

---

## 🛠️ Implementação em Kotlin & Spring Boot

### 1. Kotlin `operator fun invoke`
Utilizar o operador `invoke` do Kotlin permite que o caso de uso seja chamado como uma função direta (ex: `registrarPalpiteUseCase(command)`), mantendo o código conciso.

### 2. Estrutura Padrão de Classe
Abaixo está o exemplo do caso de uso `RegistrarPalpiteUseCase`:

```kotlin
package com.ligadospalpites.feature.predictions.application

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class RegistrarPalpiteUseCase(
    private val palpiteRepository: PalpiteRepository,
    private val jogoRepository: JogoRepository
) {

    @Transactional
    operator fun invoke(command: Command): Result {
        // 1. Validações básicas de fluxo
        val jogo = jogoRepository.findById(JogoId(command.jogoId))
            ?: throw JogoNaoEncontradoException(command.jogoId)

        // 2. Recuperação ou criação do Agregado Root
        val palpite = palpiteRepository.findByUsuarioIdAndJogoId(
            UsuarioId(command.usuarioId), 
            jogo.id
        ) ?: Palpite.criarNovo(
            usuarioId = UsuarioId(command.usuarioId),
            jogoId = jogo.id
        )

        // 3. Delegação da regra de negócio para o Agregado
        palpite.registrarOuAtualizarPalpite(
            placarMandante = command.placarMandante,
            placarVisitante = command.placarVisitante
        )

        // 4. Persistência (salvar o agregado dispara os Domain Events correspondentes)
        val palpiteSalvo = palpiteRepository.save(palpite)

        return Result(
            palpiteId = palpiteSalvo.id.valor,
            usuarioId = palpiteSalvo.usuarioId.valor
        )
    }

    // DTOs de Entrada e Saída acoplados ao Caso de Uso
    data class Command(
        val usuarioId: UUID,
        val jogoId: UUID,
        val placarMandante: Int,
        val placarVisitante: Int
    )

    data class Result(
        val palpiteId: UUID,
        val usuarioId: UUID
    )
}
```

---

## 🧪 Estratégia de Testes Unitários

Como os Casos de Uso dependem apenas de interfaces (Repositories/Services), eles são extremamente fáceis de testar utilizando Mocks (com `MockK` em Kotlin).

```kotlin
class RegistrarPalpiteUseCaseTest {
    private val palpiteRepository = mockk<PalpiteRepository>()
    private val jogoRepository = mockk<JogoRepository>()
    private val useCase = RegistrarPalpiteUseCase(palpiteRepository, jogoRepository)

    @Test
    fun `deve registrar palpite com sucesso`() {
        // Arrange (Setup mocks e dados)
        ...
        // Act
        val result = useCase(command)

        // Assert (Verificar chamadas e resultados)
        verify(exactly = 1) { palpiteRepository.save(any()) }
    }
}
```

---

## ⚠️ O que EVITAR (Anti-patterns)

- ❌ **Regras de Negócio no Caso de Uso**: Fazer cálculos ou validações complexas de regras de domínio dentro do Caso de Uso. Mova-as para as Entidades ou Objetos de Valor.
- ❌ **Tratamento de Exceções HTTP**: Lançar ou tratar exceções HTTP (ex: `ResponseStatusException`) no Caso de Uso. Ele deve lançar exceções de negócio puras (ex: `PalpiteInvalidoException`), e a camada de Controller HTTP traduzirá isso para o código HTTP correto (ex: `400 Bad Request`).
- ❌ **Retornar Entidades de Domínio**: Retornar entidades JPA mutáveis diretamente para o Controller. Dê preferência a DTOs de saída (`Result`) imutáveis e limpos para evitar o problema de Lazy Loading no Jackson serializer.
