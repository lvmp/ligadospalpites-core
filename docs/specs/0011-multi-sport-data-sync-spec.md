# SPEC-0011: Multi-Sport Data Sync & Domain Specification

Este documento especifica os detalhes técnicos de implementação, schema do banco de dados PostgreSQL, mappers DTO, extensões de entidades e observadores para a sincronização de múltiplos esportes conforme estabelecido na **[ADR-0012](file:///c:/Users/Vinicius/workspace/ligadospalpites-core/docs/adr/0012-zero-cost-multi-sport-api-strategy.md)**.

---

## 1. 🗄️ Extensões de Schema do Banco de Dados (PostgreSQL Migration)

### 1.1. Alterações na Tabela `tbl_matches`
Para suportar o formato polimórfico de pontuações e resultados por esporte, a tabela `tbl_matches` receberá colunas estruturadas em JSONB:

```sql
-- Migration V20__extend_matches_for_multi_sport_payloads.sql

ALTER TABLE tbl_matches 
  ADD COLUMN IF NOT EXISTS period_scores_json JSONB,
  ADD COLUMN IF NOT EXISTS set_scores_json JSONB,
  ADD COLUMN IF NOT EXISTS podium_results_json JSONB,
  ADD COLUMN IF NOT EXISTS number_of_games INT DEFAULT 1,
  ADD COLUMN IF NOT EXISTS stream_url VARCHAR(500),
  ADD COLUMN IF NOT EXISTS circuit_name VARCHAR(255);
```

### 1.2. Nova Tabela `tbl_user_riot_profiles`
Para suporte à integração com a Riot Games API (perfis e elo de invocadores de LoL/Valorant):

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

CREATE INDEX idx_user_riot_profiles_user_id ON tbl_user_riot_profiles(user_id);
```

---

## 2. ⚙️ Adaptadores de Sincronização por Esporte (`LeagueSyncService`)

Cada modalidade esportiva será implementada como uma classe `@Service` estendendo a interface `LeagueSyncService`:

### 2.1. eSports Adapter (`PandaScoreSyncService.kt`)
* **Responsabilidade**: Ingestão de torneios profissionais (CBLOL, VCT, CS2 Major, Worlds).
* **Endpoint Externo**: `GET https://api.pandascore.co/matches`
* **Mapeamento de Status**:
  * `not_started` ➔ `MatchStatus.SCHEDULED`
  * `running` ➔ `MatchStatus.LIVE`
  * `finished` ➔ `MatchStatus.FINISHED`
* **Campos Específicos**: `number_of_games` (1, 3 ou 5 em séries BO1, BO3, BO5) e `stream_url` (link da Twitch/YouTube).

### 2.2. Basquete Adapter (`EspnBasketballSyncService.kt`)
* **Responsabilidade**: Sincronização de jogos da NBA, WNBA e NCAA.
* **Endpoint Externo**: `GET https://site.api.espn.com/apis/site/v2/sports/basketball/nba/scoreboard`
* **Mapeamento de Pontuação**:
  * Grava em `period_scores_json`: `{"home": [28, 30, 22, 25], "away": [24, 25, 29, 21]}`.

### 2.3. Motorsport Adapter (`MotorsportSyncService.kt`)
* **Responsabilidade**: Ingestão de GPs da Fórmula 1, F2, Fórmula E e Stock Car.
* **Endpoint Externo**: `GET https://api.jolpi.ca/ergast/f1/current.json`
* **Mapeamento de Pódio**:
  * Grava em `podium_results_json`: `{"pole": "Verstappen", "p1": "Verstappen", "p2": "Norris", "p3": "Hamilton", "fastestLap": "Norris"}`.

### 2.4. Futebol Americano Adapter (`AmericanFootballSyncService.kt`)
* **Responsabilidade**: Jogos da NFL e College Football.
* **Endpoint Externo**: `GET https://site.api.espn.com/apis/site/v2/sports/football/nfl/scoreboard`
* **Mapeamento de Pontuação por Quarto**:
  * Grava em `period_scores_json`: `{"home": [7, 10, 3, 7], "away": [3, 7, 7, 7]}`.

### 2.5. Tênis Adapter (`TennisSyncService.kt`)
* **Responsabilidade**: Torneios ATP Tour, WTA Tour e Grand Slams.
* **Endpoint Externo**: `GET https://site.api.espn.com/apis/site/v2/sports/tennis/atp/scoreboard`
* **Mapeamento de Sets**:
  * Grava em `set_scores_json`: `{"set1": "6-4", "set2": "3-6", "set3": "7-6"}`.

---

## 3. 🎯 Motor Polimórfico de Cálculo de Palpites (`ScoringEngine.kt`)

O método `calculateMatchPoints` em `ScoringEngine.kt` aceitará parâmetros polimórficos baseados nas regras da liga (`ScoringRulesVO`):

```kotlin
object ScoringEngine {

    fun calculatePoints(
        sportCategory: SportCategory,
        prediction: PredictionEntity,
        match: MatchEntity
    ): Int {
        return when (sportCategory) {
            SportCategory.FOOTBALL -> calculateFootballPoints(prediction, match)
            SportCategory.ESPORTS -> calculateEsportsSeriesPoints(prediction, match)
            SportCategory.BASKETBALL -> calculateBasketballMarginPoints(prediction, match)
            SportCategory.MOTORSPORT -> calculateMotorsportPodiumPoints(prediction, match)
            SportCategory.AMERICAN_FOOTBALL -> calculateNflMarginPoints(prediction, match)
            SportCategory.TENNIS -> calculateTennisSetPoints(prediction, match)
        }
    }
}
```

---

## 4. 🚀 Endpoints REST Internos do Scheduler

Conforme **[ADR-0005](file:///c:/Users/Vinicius/workspace/ligadospalpites-core/docs/adr/0005-serverless-scheduler-strategy.md)**, o trigger de sincronização para os novos esportes ocorre via requisição HTTP protegida:

* `POST /api/v1/internal/scheduler/process?sportId=esports&leagueId={leagueId}`
* `POST /api/v1/internal/scheduler/process?sportId=basketball&leagueId={leagueId}`
* `POST /api/v1/internal/scheduler/process?sportId=motorsport&leagueId={leagueId}`
* `POST /api/v1/internal/scheduler/process?sportId=american_football&leagueId={leagueId}`
* `POST /api/v1/internal/scheduler/process?sportId=tennis&leagueId={leagueId}`
