# SPEC-0015: American Football Sync & Scoring Spec

Este documento especifica a ingestão de jogos da NFL, armazenamento de pontuação por quarto em JSONB e regras de pontuação para Futebol Americano.

---

## 1. 🗄️ Estrutura do Banco de Dados (PostgreSQL)

### 1.1. Campo `period_scores_json` na `tbl_matches`
```json
{
  "home": [7, 10, 3, 7],
  "away": [3, 7, 7, 7]
}
```

---

## 2. ⚙️ Adaptador de Serviço (`AmericanFootballSyncService.kt`)

```kotlin
@Service
class AmericanFootballSyncService(
    private val matchRepository: SpringDataMatchRepository,
    private val espnNflClient: EspnNflClient
) : LeagueSyncService {

    override fun supports(sportId: UUID, leagueId: UUID): Boolean {
        return sportId == UUID.fromString("6c1e3a11-b9db-44ab-ba02-411a0c0bcf14")
    }

    override fun syncMatches(sportId: UUID, leagueId: UUID) {
        val scoreboard = espnNflClient.fetchNflScoreboard()
        // Salva jogos da semana, pontuação por quarto e logos das franquias
    }
}
```

---

## 3. 🎯 Motor de Pontuação da NFL (`ScoringEngine.kt`)

* **Placar Exato de Pontos** (ex: Palpitou Chiefs 27x24, final 27x24): **30 pontos**.
* **Vencedor Correto + Margem Exata de Touchdown** (diferença de pontos exata): **20 pontos**.
* **Vencedor Correto + Margem Próxima**: **15 pontos**.
* **Apenas Vencedor**: **10 pontos**.
