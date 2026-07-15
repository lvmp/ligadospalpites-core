---
name: bff_gateway
description: Diretrizes e padrões arquiteturais para estruturar a camada de BFF (Backend-For-Frontend) Gateway com execução paralela assíncrona em Kotlin e Spring Boot.
---

# BFF (Backend-For-Frontend) Gateway

Esta skill orienta o agente no design e na implementação de agregadores do padrão **BFF Gateway**, essenciais para otimizar o consumo de dados por aplicativos móveis no ecossistema da **Liga dos Palpites**.

---

## 💡 O que é o BFF Gateway?

O padrão BFF cria uma camada de agregação otimizada especificamente para o cliente móvel (Flutter). Em vez do aplicativo realizar de 5 a 10 requisições paralelas de rede celular para carregar a Home (o que gera latência cumulativa e alto consumo de bateria), ele faz uma única chamada ao endpoint unificado do BFF.

O BFF então se comunica internamente com os diferentes módulos de negócio, consolida as respostas e devolve um único payload JSON sob medida para os blocos visuais da UI.

---

## ⚡ Princípio da Execução Paralela Assíncrona

Para que o BFF seja eficiente, ele **nunca** deve executar as buscas internas de forma serial (síncrona sequencial). Se cada busca levar 50ms e fizermos 4 buscas, a chamada BFF levará 200ms. 

Executando em paralelo, o tempo total do BFF será apenas o tempo da chamada mais lenta (aproximadamente 50ms).

### Padrão de Codificação em Kotlin com `CompletableFuture`:

No Spring Boot, utilize o `CompletableFuture.supplyAsync` fornecendo o `taskExecutor` configurado no ecossistema do Spring para gerenciar threads com eficiência de forma assíncrona.

```kotlin
package com.ligadospalpites.shared.bff

import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RestController
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executor

@RestController
class DashboardController(
    private val userQueryService: UserQueryService,
    private val fixtureQueryService: FixtureQueryService,
    private val newsQueryService: NewsQueryService,
    private val taskExecutor: Executor // Thread pool gerenciado pelo Spring
) {

    @GetMapping("/api/v1/home/dashboard")
    fun getDashboard(@RequestHeader("X-User-Id") userId: String): CompletableFuture<ResponseEntity<DashboardResponse>> {
        
        // 1. Despacha as tarefas I/O em paralelo para o pool de threads
        val profileFuture = CompletableFuture.supplyAsync({
            userQueryService.getUserProfile(userId)
        }, taskExecutor)

        val nextMatchFuture = CompletableFuture.supplyAsync({
            fixtureQueryService.getNextFeaturedMatch()
        }, taskExecutor)

        val newsFuture = CompletableFuture.supplyAsync({
            newsQueryService.getLatestNews()
        }, taskExecutor)

        // 2. Unifica os resultados quando TODOS terminarem
        return CompletableFuture.allOf(profileFuture, nextMatchFuture, newsFuture)
            .thenApply {
                val profile = profileFuture.join()
                val nextMatch = nextMatchFuture.join()
                val news = newsFuture.join()

                ResponseEntity.ok(
                    DashboardResponse(
                        profile = UserProfileDto(profile.name, profile.points, profile.rank),
                        featuredMatch = MatchDto.fromDomain(nextMatch),
                        news = news.map { NewsDto.fromDomain(it) }
                    )
                )
            }
    }
}
```

---

## 🚀 Diretrizes para o Agente

1. **Desacoplamento de DTOs**: DTOs expostos pelo BFF Gateway (`DashboardResponse`) devem ser isolados dos DTOs internos dos domínios. Isso garante que mudanças no visual do Flutter não quebrem as interfaces lógicas internas do sistema.
2. **Resiliência a Falhas (Graceful Degradation)**: Se um componente não essencial (como o feed de notícias) falhar na execução assíncrona, utilize `.exceptionally` ou trate o erro para retornar um valor vazio/padrão, impedindo que a tela inteira do usuário quebre devido a um módulo secundário instável.
3. **Foco em Operações de Leitura (Queries)**: O BFF serve majoritariamente para agregar dados de leitura. Evite utilizá-lo para comandos de escrita complexos ou transações.

---

## ⚠️ O que EVITAR (Anti-patterns)

* ❌ **Fazer Varreduras de Banco Sequenciais (Blocking Calls)**: Encadear chamadas síncronas bloqueantes dentro do BFF.
* ❌ **Falta de Pool de Threads Dedicado**: Executar tarefas assíncronas no pool padrão de threads do Java (`ForkJoinPool.commonPool()`) sem especificar um `Executor` gerenciado, o que pode exaurir threads do servidor Tomcat/Undertow.
* ❌ **Poluir com Lógica de Negócio**: Colocar validações de regras de negócio ou de persistência no BFF. Ele deve apenas chamar métodos de consulta já validados e formatar respostas.
