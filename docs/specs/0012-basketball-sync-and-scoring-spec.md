# SPEC-0012: Basketball Sync & Point Margin Scoring Spec

Este documento especifica a implementação técnica, mappers de DTO, armazenamento de parciais por quarto e regras de pontuação para **Basquete**.

---

## 1. 🗄️ Estrutura do Banco de Dados (PostgreSQL)

### 1.1. Campo `period_scores_json` na `tbl_matches`
Armazena a pontuação de cada período (Q1, Q2, Q3, Q4, OT) em formato JSONB:

```json
{
  "home": [28, 30, 22, 25],
  "away": [24, 25, 29, 21],
  "homeOvertime": 0,
  "awayOvertime": 0
}
```

---

## 2. ⚙️ Adaptador de Serviço (`EspnBasketballSyncService.kt`)

```kotlin
@Service
class EspnBasketballSyncService(
    private val matchRepository: SpringDataMatchRepository,
    private val espnBasketballClient: EspnBasketballClient,
    private val eventPublisher: ApplicationEventPublisher
) : LeagueSyncService {

    override fun supports(sportId: UUID, leagueId: UUID): Boolean {
        return sportId == UUID.fromString("e5284bf1-d576-4740-97cc-f06bca181cb2")
    }

    override fun syncMatches(sportId: UUID, leagueId: UUID) {
        val scoreboard = espnBasketballClient.fetchNbaScoreboard()
        // Mapeia partidas, pontuação por quarto e salva na tbl_matches
    }
}
```

---

## 3. 🎯 Motor de Pontuação por Margem (`ScoringEngine.kt`)

Como no basquete não há empates e a pontuação é elevada, o cálculo do palpite é baseado em **Margem de Vitória**:

* **Vencedor Correto + Margem Exata de Vitória** (ex: Palpitou vitória por 5 pts, final +5): **25 pontos**.
* **Vencedor Correto + Margem Próxima** (diferença de até 3 pts na margem): **18 pontos**.
* **Apenas Vencedor Correto**: **10 pontos**.
