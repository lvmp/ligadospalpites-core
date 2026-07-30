# SPEC-0013: eSports Sync & Series Scoring Spec

Este documento especifica a integração com a PandaScore API, vinculação de conta Riot e regras de pontuação para séries **MD1, MD3 e MD5** de eSports.

---

## 1. 🗄️ Estrutura do Banco de Dados (PostgreSQL)

### 1.1. Tabela `tbl_user_riot_profiles`
```sql
CREATE TABLE IF NOT EXISTS tbl_user_riot_profiles (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES tbl_users(id) ON DELETE CASCADE,
    puuid VARCHAR(255) NOT NULL UNIQUE,
    game_name VARCHAR(100) NOT NULL,
    tag_line VARCHAR(50) NOT NULL,
    lol_rank VARCHAR(50),
    valorant_rank VARCHAR(50),
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);
```

### 1.2. Campos na `tbl_matches`
- `number_of_games` (INT): 1, 3 ou 5 (BO1, BO3, BO5).
- `stream_url` (VARCHAR): URL da transmissão ao vivo (Twitch/YouTube).

---

## 2. ⚙️ Adaptador de Serviço (`PandaScoreSyncService.kt`)

```kotlin
@Service
class PandaScoreSyncService(
    private val matchRepository: SpringDataMatchRepository,
    private val pandaScoreClient: PandaScoreClient
) : LeagueSyncService {

    override fun supports(sportId: UUID, leagueId: UUID): Boolean {
        return sportId == UUID.fromString("9b1e3a11-b9db-44ab-ba02-411a0c0bcf14")
    }

    override fun syncMatches(sportId: UUID, leagueId: UUID) {
        val matches = pandaScoreClient.fetchMatches()
        // Salva séries BO3/BO5, número de mapas e stream URL
    }
}
```

---

## 3. 🎯 Motor de Pontuação de Séries (`ScoringEngine.kt`)

* **Placar Exato da Série (MD3/MD5)** (ex: Palpitou 2x1 em uma MD3, final 2x1): **25 pontos**.
* **Vencedor Correto com Saldo Diferente** (ex: Palpitou 2x0, final 2x1): **15 pontos**.
* **Apenas Vencedor**: **10 pontos**.
