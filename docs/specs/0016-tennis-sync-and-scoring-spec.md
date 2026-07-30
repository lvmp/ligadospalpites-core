# SPEC-0016: Tennis Sync & Set Scoring Spec

Este documento especifica a ingestão de partidas de tênis (ATP/WTA), armazenamento de parciais de sets em JSONB e regras de pontuação por set.

---

## 1. 🗄️ Estrutura do Banco de Dados (PostgreSQL)

### 1.1. Campo `set_scores_json` na `tbl_matches`
```json
{
  "set1": "6-4",
  "set2": "3-6",
  "set3": "7-6"
}
```

---

## 2. ⚙️ Adaptador de Serviço (`TennisSyncService.kt`)

```kotlin
@Service
class TennisSyncService(
    private val matchRepository: SpringDataMatchRepository,
    private val espnTennisClient: EspnTennisClient
) : LeagueSyncService {

    override fun supports(sportId: UUID, leagueId: UUID): Boolean {
        return sportId == UUID.fromString("4c1e3a11-b9db-44ab-ba02-411a0c0bcf14")
    }

    override fun syncMatches(sportId: UUID, leagueId: UUID) {
        val scoreboard = espnTennisClient.fetchTennisScoreboard()
        // Salva confrontos, parciais por set e tenistas
    }
}
```

---

## 3. 🎯 Motor de Pontuação por Sets (`ScoringEngine.kt`)

* **Placar Exato de Sets em MD3** (ex: Palpitou 2x1, final 2x1): **25 pontos**.
* **Placar Exato de Sets em MD5** (ex: Palpitou 3x1, final 3x1): **30 pontos**.
* **Vencedor Correto com Placar de Sets Diferente**: **12 pontos**.
