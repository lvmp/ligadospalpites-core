# ADR-0018: Live Match Timeline & Commentary (Minuto a Minuto) Strategy

## Status
Accepted

## Date
2026-09-12

## Context
A plataforma **Liga dos Palpites** oferece aos usuários o acompanhamento de jogos e resultados para suas ligas de palpites. Para aumentar a retenção e o valor percebido pelos usuários pagantes, surgiu a necessidade de fornecer o recurso de **"Minuto a Minuto"** (narração textual dos lances, gols, cartões, pênaltis, substituições e intervenções de VAR em tempo real) para partidas em andamento, além de possibilitar a consulta histórica e análise pós-jogo.

O desafio principal consiste em:
1. Manter a premissa de **Custo Zero (Free Tier)** sem custos de APIs de dados esportivos.
2. Não violar as premissas do [ADR-0002](0002-cloud-deployment-free-tier.md) e [ADR-0005](0005-serverless-scheduler-strategy.md), evitando WebSockets persistentes ou loops pesados em instâncias Serverless do Cloud Run.
3. Proteger a funcionalidade por meio de paywall exclusivo para usuários assinantes (`PREMIUM` ou `SPORT_PASS`) e contemplados por Cortesia VIP ([ADR-0009](0009-revenuecat-webhook-integration.md)).
4. Oferecer alta disponibilidade durante o jogo (tempo real) e persistência definitiva para consultas históricas pós-jogo.

## Decision

Decidimos implementar uma arquitetura híbrida on-demand com cache distribuído e persistência definitiva:

### 1. Fonte de Dados Gratuita (ESPN Public API)
Adotamos como provedor primário a **ESPN Public API** (endpoints de summary e scoreboard de futebol e esportes integrados). Ela fornece eventos chave (`plays` / `commentary` / `keyEvents`) em tempo real, sem necessidade de chave de API e sem custo de assinatura. Em caso de indisponibilidade da API externa, o sistema utiliza gerador de timeline sintética de resiliência baseado nos eventos de ciclo de vida (`MatchStartedEvent`, `MatchGoalEvent`, `MatchHalfTimeEvent`, `MatchFinishedEvent`).

### 2. Ciclo de Ingestão e Consumo On-Demand
Para evitar polling desnecessário em jogos sem audiência:
- **Durante a Partida (`LIVE` / `HALF_TIME`)**:
  - O aplicativo móvel (Frontend Flutter) utiliza **Smart Short-Polling** (a cada 20 a 30 segundos) chamando `GET /api/v1/sports/matches/{matchId}/timeline`.
  - O backend utiliza o **Upstash Redis** (`match:{matchId}:timeline`) com TTL de 30 segundos. Milhares de requisições de clientes concorrentes compartilham o mesmo cache, resultando em apenas 1 chamada externa por minuto por partida.
- **Pós-Encerramento (`FINISHED`)**:
  - Ao término da partida, o evento `MatchFinishedEvent` aciona a consolidação dos lances, persistindo-os em lote na tabela relacional `tbl_match_events` no Supabase PostgreSQL.
  - As consultas históricas passam a ser servidas 100% pelo PostgreSQL indexado, com custo zero de API externa.

### 3. Controle de Acesso e Monetização (Entitlement Paywall)
O endpoint de timeline exige autenticação e valida se o usuário possui direito de acesso (`PREMIUM`, `SPORT_PASS` do esporte correspondente ou Cortesia ativa). Usuários gratuitos recebem HTTP `403 Forbidden` com código `PREMIUM_REQUIRED`, permitindo ao app renderizar o banner de conversão/paywall.

```mermaid
graph TD
    User[Usuário no Flutter App] -->|1. Polling a cada 25s| API[GET /matches/{id}/timeline]
    API --> Auth{Assinante ou Cortesia?}
    Auth -->|Não| Block[403 PREMIUM_REQUIRED]
    Auth -->|Sim| Cache{Existe no Redis?}
    Cache -->|Sim (TTL 30s)| ReturnCached[Retorna Lances em < 5ms]
    Cache -->|Não| CheckStatus{Status do Jogo?}
    CheckStatus -->|FINISHED| FetchDB[(Supabase PostgreSQL: tbl_match_events)]
    CheckStatus -->|LIVE / HALF_TIME| FetchESPN[ESPN Public API Summary]
    FetchESPN --> SaveRedis[Grava Redis com TTL 30s]
    FetchESPN --> ReturnLive[Retorna Timeline Atualizada]
    FetchDB --> ReturnHistory[Retorna Histórico Consolidado]
```

## Consequences

### Positive
- **Custo Zero Contínuo**: Sem taxas de provedores pagos como API-Sports ou Sportmonks.
- **Compatibilidade Serverless**: O padrão de short-polling com buffer Redis mantém a infraestrutura dentro da escala zero do Cloud Run.
- **Histórico Perpétuo**: Partidas encerradas ficam salvas localmente no banco para análise futura sem depender de APIs externas.
- **Conversão de Assinaturas**: Recurso premium de alto valor que impulsiona assinaturas via RevenueCat.

### Negative
- **Atraso inerente ao polling**: Atualizações ocorrem no intervalo de 20 a 30 segundos em vez de milissegundos (o que é perfeitamente aceitável para lances narrados de futebol).
