# SPEC-0010: Event-Driven Matches & Goal Notification Spec

Este documento especifica a implementação técnica e fluxos operacionais para detecção de eventos em partidas esportivas, cálculo reativo de palpites de usuários e disparo automatizado de notificações push de acordo com as regras estabelecidas.

---

## 🛠️ Especificações de Eventos

Três classes de eventos principais do Spring serão criadas para representar as transições das partidas na base de dados:

```kotlin
package com.ligadospalpites.sportsfeed.domain.events

import java.util.UUID

data class MatchStartedEvent(
    val matchId: UUID,
    val homeTeamName: String,
    val awayTeamName: String,
    val sportId: UUID,
    val leagueId: UUID
)

data class MatchGoalEvent(
    val matchId: UUID,
    val homeTeamName: String,
    val awayTeamName: String,
    val homeScore: Int,
    val awayScore: Int,
    val scoringTeam: String, // ex: "HOME" ou "AWAY"
    val sportId: UUID,
    val leagueId: UUID
)

data class MatchFinishedEvent(
    val matchId: UUID,
    val homeTeamName: String,
    val awayTeamName: String,
    val homeScore: Int,
    val awayScore: Int,
    val sportId: UUID,
    val leagueId: UUID
)
```

---

## 🔄 Fluxo de Detecção de Mudanças

O método `performUpsert` no `FootballWorldCupSyncService` (e serviços equivalentes de simulação em ambiente de testes) comparará o registro de cada partida externa com o registro existente no banco antes de salvá-lo:

```kotlin
// Pseudocódigo de comparação
val matchMatch = existing.find { ext -> ... }
if (matchMatch != null) {
    // 1. Detectar Partida Iniciada
    if (matchMatch.status == MatchStatus.SCHEDULED && inc.status == MatchStatus.LIVE) {
        eventPublisher.publishEvent(MatchStartedEvent(matchMatch.id, inc.homeTeamName, inc.awayTeamName, inc.sportId, inc.leagueId))
    }
    
    // 2. Detectar Gols Marcados
    if (matchMatch.status == MatchStatus.LIVE && inc.status == MatchStatus.LIVE) {
        val oldHome = matchMatch.homeScore ?: 0
        val oldAway = matchMatch.awayScore ?: 0
        val newHome = inc.homeScore ?: 0
        val newAway = inc.awayScore ?: 0
        
        if (newHome > oldHome) {
            eventPublisher.publishEvent(MatchGoalEvent(matchMatch.id, inc.homeTeamName, inc.awayTeamName, newHome, newAway, "HOME", inc.sportId, inc.leagueId))
        } else if (newAway > oldAway) {
            eventPublisher.publishEvent(MatchGoalEvent(matchMatch.id, inc.homeTeamName, inc.awayTeamName, newHome, newAway, "AWAY", inc.sportId, inc.leagueId))
        }
    }
    
    // 3. Detectar Partida Finalizada
    if (matchMatch.status != MatchStatus.FINISHED && inc.status == MatchStatus.FINISHED) {
        eventPublisher.publishEvent(MatchFinishedEvent(matchMatch.id, inc.homeTeamName, inc.awayTeamName, inc.homeScore ?: 0, inc.awayScore ?: 0, inc.sportId, inc.leagueId))
    }
}
```

---

## 🏁 Cálculo Reativo de Pontos e Palpites

Um processador chamado `PredictionsProcessorService` escutará transacionalmente o evento de partida concluída:

```kotlin
@Component
class PredictionsProcessorService(
    private val predictionRepository: SpringDataPredictionRepository,
    private val eventPublisher: ApplicationEventPublisher
) {

    @Transactional
    @EventListener
    fun onMatchFinished(event: MatchFinishedEvent) {
        val predictions = predictionRepository.findByMatchId(event.matchId)
        val scoreUpdates = mutableListOf<UserScoreUpdateDto>()
        
        predictions.forEach { prediction ->
            if (!prediction.isProcessed) {
                val points = ScoringEngine.calculateMatchPoints(
                    predHome = prediction.predictedHomeScore,
                    predAway = prediction.predictedAwayScore,
                    realHome = event.homeScore,
                    realAway = event.awayScore,
                    isFinal = false // ou obter flag com base na fase do jogo
                )
                
                // Atualizar palpite
                predictionRepository.save(prediction.copy(
                    pointsAwarded = points,
                    isProcessed = true,
                    calculatedAt = Instant.now()
                ))
                
                scoreUpdates.add(UserScoreUpdateDto(prediction.userId, points))
            }
        }
        
        if (scoreUpdates.isNotEmpty()) {
            eventPublisher.publishEvent(PredictionsProcessedEvent(event.leagueId, scoreUpdates))
        }
    }
}
```

---

## 🔔 Ouvintes de Notificações Push (Push Listeners)

Os observadores de notificação escutarão os eventos de forma assíncrona (`@Async`) e usarão a infraestrutura de `NotificationDispatcherService` para direcionar os pushes com textos dinâmicos estruturados:

### 1. MatchStartedListener
* **Gatilho**: `MatchStartedEvent`
* **Público-alvo**: Usuários que realizaram palpites na partida (`matchId`).
* **Texto Dinâmico**:
  - *Título*: `⚽ JOGO INICIADO: ${homeTeamName} x ${awayTeamName}`
  - *Conteúdo*: `A bola está rolando pela Copa do Mundo! Fique ligado no seu palpite.`

### 2. MatchGoalListener
* **Gatilho**: `MatchGoalEvent`
* **Público-alvo**: Todos os usuários associados ao esporte/liga da partida (Segmentação `SPORT` / `LEAGUE`).
* **Texto Dinâmico**:
  - Se gol do mandante: *Título*: `⚽ GOOOL DO ${homeTeamName}! (${homeScore} x ${awayScore})`
  - Se gol do visitante: *Título*: `⚽ GOOOL DO ${awayTeamName}! (${homeScore} x ${awayScore})`
  - *Conteúdo*: `Atualização em tempo real do placar da partida!`

### 3. MatchFinishedPointsListener
* **Gatilho**: `PredictionsProcessedEvent`
* **Público-alvo**: Usuários que pontuaram na partida (enviado individualmente).
* **Texto Dinâmico**:
  - *Título*: `🏁 JOGO ENCERRADO & PONTOS CALCULADOS!`
  - *Conteúdo*: `Fim de jogo! Você conquistou ${pointsGained} pontos com o seu palpite.`
