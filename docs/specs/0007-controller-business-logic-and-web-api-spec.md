# Spec-0007: Mapeamento de Endpoints REST, DTOs e Regras Complexas de Negócio

Esta especificação define os contratos de API HTTP, os payloads JSON, as estruturas de DTOs, e as regras complexas de negócios que controlam os fluxos de Jogos, Palpites, Ligas e o Dashboard BFF (Home), totalmente integrados com as regras do app Flutter.

---

## 1. Mapeamento e Alinhamento com o App Flutter (`C:\Users\Vinicius\workspace\ligadospalpites`)
A lógica de pontos do backend (`ScoringEngine.kt`) está **100% alinhada** com as Regras de Ouro definidas no documento de regras do Flutter (`ai/manualRegrasDePontuacao.md`):
- **Placar Exato (Vitoria ou Empate)**: 25 pts (50 pts na Final).
- **Vencedor + Gols de um Time**: 15 pts (30 pts na Final).
- **Apenas Vencedor**: 10 pts (20 pts na Final).
- **Gols Isolados**: 5 pts (10 pts na Final).

---

## 2. Módulo `sportsfeed` (Ligas Ativas, Brackets & Standings)

### A. Estrutura de Esportes e Ligas (`tbl_sports` e `tbl_leagues`)
Adicionamos tabelas dedicadas com integridade referencial:
- `tbl_sports`: ID, Name.
- `tbl_leagues`: ID, Name, Sport_ID, Is_Active.

### B. Listagem de Ligas Agrupadas por Esporte
Para facilitar filtros em abas no Flutter, a listagem unifica e agrupa as ligas ativas dentro de seus respectivos esportes:
- **Endpoint**: `GET /api/v1/sports/leagues`
- **Formato da Resposta (`200 OK`)**:
  ```json
  [
    {
      "sportId": "f3b3b44b-6f81-42cb-b1b7-d1a1005a8f4c",
      "sportName": "Futebol",
      "leagues": [
        {
          "leagueId": "e7b0a8f9-4b2e-4b67-8890-a54b3d7c588e",
          "name": "Copa do Mundo",
          "isActive": true
        }
      ]
    }
  ]
  ```

### C. Classificação Geral e Fase Mata-mata (Brackets)
- **Tabela Clássica (Fase de Grupos)**:
  - **Endpoint**: `GET /api/v1/sports/standings?leagueId={leagueId}`
  - Retorna a pontuação clássica de clubes/seleções no grupo para a fase de grupos.
- **Chaveamento / Bracket (Fase de Mata-mata)**:
  - **Endpoint**: `GET /api/v1/sports/brackets?leagueId={leagueId}`
  - Retorna as partidas estruturadas em formato de árvore ou agrupadas pelas fases eliminatórias (`OITAVAS`, `QUARTAS`, `SEMI`, `FINAL`) para alimentar o `BracketBloc` no Flutter.
  - **Formato da Resposta (`200 OK`)**:
    ```json
    {
      "leagueId": "e7b0a8f9-4b2e-4b67-8890-a54b3d7c588e",
      "phases": {
        "OITAVAS": [
          {
            "matchId": "d0d6a9e1-2b3c-4d5e-6f7a-8b9c0d1e2f3a",
            "homeTeam": "Brasil",
            "awayTeam": "Chile",
            "kickoffTime": "2026-07-15T19:00:00Z",
            "status": "SCHEDULED",
            "scoreHome": null,
            "scoreAway": null
          }
        ],
        "FINAL": []
      }
    }
    ```

---

## 3. Módulo `predictions` (Palpites & Palpites Especiais)

### A. Criar/Atualizar Palpites de Partidas
- **Endpoint**: `POST /api/v1/predictions`
- **Request Body**:
  ```json
  {
    "matchId": "d0d6a9e1-2b3c-4d5e-6f7a-8b9c0d1e2f3a",
    "predictedHomeScore": 2,
    "predictedAwayScore": 1
  }
  ```
- **Lógica de Bloqueio**: Bloqueado no segundo em que o jogo inicia.

### B. Submeter Palpites Especiais (Mata-mata / Conquistas de Longo Prazo)
- **Endpoint**: `POST /api/v1/special-predictions`
- **Request Body**:
  ```json
  {
    "leagueId": "e7b0a8f9-4b2e-4b67-8890-a54b3d7c588e",
    "type": "CHAMPION", // CHAMPION, TOP_SCORER, REVELATION
    "predictionValue": "Brasil"
  }
  ```
- **Campos de Controle**:
  - `type` (String, obrigatório): Define a natureza do palpite especial conforme mapeado em `tbl_special_predictions`.
  - `predictionValue` (String, obrigatório): Valor cravado pelo usuário (ex: "Brasil" para campeão ou ID do artilheiro).
- **Lógica de Bloqueio**: Tranca no horário do primeiro jogo da competição.

---

## 4. Módulo `groups` (Ligas, Kicks & Rankings por Períodos)

### A. Expulsar Membro
- **Endpoint**: `DELETE /api/v1/groups/{groupId}/members/{memberUserId}`

### B. Rankings com Sub-Leaderboards (Redis Sorted Sets)
- **Endpoint**: `GET /api/v1/groups/{groupId}/leaderboard?phase={group-stage|knockout|overall}`

---

## 5. Módulo `Home` (BFF Aggregator Dashboard)
- **Endpoint**: `GET /api/v1/home/dashboard`
