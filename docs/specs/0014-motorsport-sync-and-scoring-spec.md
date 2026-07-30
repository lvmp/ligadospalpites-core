# SPEC-0014: Motorsport Sync & Podium Scoring Spec

Este documento especifica a ingestão de GPs/Etapas de automobilismo, armazenamento do pódio em JSONB e regras de pontuação para corridas.

---

## 1. 🗄️ Estrutura do Banco de Dados (PostgreSQL)

### 1.1. Campo `podium_results_json` na `tbl_matches`
Armazena a classificação final da etapa/corrida:

```json
{
  "polePosition": "Max Verstappen",
  "winnerP1": "Max Verstappen",
  "podiumP2": "Lando Norris",
  "podiumP3": "Lewis Hamilton",
  "fastestLap": "Lando Norris"
}
```

---

## 2. ⚙️ Adaptador de Serviço (`MotorsportSyncService.kt`)

```kotlin
@Service
class MotorsportSyncService(
    private val matchRepository: SpringDataMatchRepository,
    private val ergastClient: ErgastClient
) : LeagueSyncService {

    override fun supports(sportId: UUID, leagueId: UUID): Boolean {
        return sportId == UUID.fromString("7c1e3a11-b9db-44ab-ba02-411a0c0bcf14")
    }

    override fun syncMatches(sportId: UUID, leagueId: UUID) {
        val races = ergastClient.fetchCurrentSeasonRaces()
        // Salva circuitos, datas de qualifying/corrida e resultados de pódio
    }
}
```

---

## 3. 🎯 Motor de Pontuação de Corridas (`ScoringEngine.kt`)

* **Pole Position Correta**: **10 pontos**.
* **Vencedor da Corrida (P1) Correto**: **25 pontos**.
* **Pódio Completo (P1, P2 e P3 na Ordem Exata)**: **50 pontos bônus**.
* **Volta Mais Rápida (*Fastest Lap*)**: **10 pontos**.
