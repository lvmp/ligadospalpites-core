# SPEC-0011: Football Multi-League Sync & Scoring Spec

Este documento especifica a implementação técnica, mappers de DTO, regras de pontuação de palpites e migração de banco de dados para a modalidade de **Futebol**.

---

## 1. 🗄️ Estrutura do Banco de Dados (Flyway Migration)

### 1.1. Migration SQL (`V19__seed_additional_free_football_leagues.sql`)

Popula as ligas de futebol do plano gratuito da `football-data.org` e as novas competições ativas:

```sql
-- Insert Additional Free Football Leagues
INSERT INTO tbl_leagues (id, name, sport_id, is_active) VALUES
('7acdf011-fbde-4122-83bc-c46b1ba847de', 'Ligue 1', 'f3b3b44b-6f81-42cb-b1b7-d1a1005a8f4c', true),
('8acdf011-fbde-4122-83bc-c46b1ba847de', 'Bundesliga', 'f3b3b44b-6f81-42cb-b1b7-d1a1005a8f4c', true),
('9acdf011-fbde-4122-83bc-c46b1ba847de', 'Serie A Italiana', 'f3b3b44b-6f81-42cb-b1b7-d1a1005a8f4c', true),
('aacdf011-fbde-4122-83bc-c46b1ba847de', 'Eredivisie', 'f3b3b44b-6f81-42cb-b1b7-d1a1005a8f4c', true),
('bacdf011-fbde-4122-83bc-c46b1ba847de', 'Primeira Liga', 'f3b3b44b-6f81-42cb-b1b7-d1a1005a8f4c', true),
('5acdf011-fbde-4122-83bc-c46b1ba847de', 'Championship', 'f3b3b44b-6f81-42cb-b1b7-d1a1005a8f4c', true),
('6acdf011-fbde-4122-83bc-c46b1ba847de', 'Eurocopa', 'f3b3b44b-6f81-42cb-b1b7-d1a1005a8f4c', true),
('b3cdf011-fbde-4122-83bc-c46b1ba847de', 'Copa do Brasil', 'f3b3b44b-6f81-42cb-b1b7-d1a1005a8f4c', true)
ON CONFLICT (id) DO NOTHING;
```

---

## 2. ⚙️ Adaptadores de Serviço (`FootballGenericSyncService.kt`)

O serviço `FootballGenericSyncService` encapsula a chamada para `FootballDataClient` e `EspnLibertadoresClient`:

```kotlin
@Service
class FootballGenericSyncService(
    private val matchRepository: SpringDataMatchRepository,
    private val footballDataClient: FootballDataClient,
    private val espnSoccerClient: EspnSoccerClient,
    private val eventPublisher: ApplicationEventPublisher
) : LeagueSyncService {

    override fun supports(sportId: UUID, leagueId: UUID): Boolean {
        return sportId == UUID.fromString("f3b3b44b-6f81-42cb-b1b7-d1a1005a8f4c")
    }

    override fun syncMatches(sportId: UUID, leagueId: UUID) {
        val metadata = leaguesMetadata[leagueId] ?: return
        if (metadata.footballDataCode != null) {
            fetchFromFootballData(sportId, leagueId, metadata.footballDataCode)
        } else if (metadata.isLibertadores) {
            fetchFromEspnLibertadores(sportId, leagueId)
        }
    }
}
```

---

## 3. 🎯 Motor de Pontuação de Palpites (`ScoringEngine.kt`)

Regras de pontuação para o futebol:

* **Placar Exato** (ex: Palpitou 2x1, final 2x1): **25 pontos**.
* **Vencedor Correto + Saldo de Gols** (ex: Palpitou 3x1, final 2x0): **18 pontos**.
* **Apenas Vencedor / Empate Correto** (ex: Palpitou 1x0, final 2x1): **10 pontos**.
