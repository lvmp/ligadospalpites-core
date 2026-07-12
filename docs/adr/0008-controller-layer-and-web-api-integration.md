# ADR-0008: Integração da Camada de Controllers REST e Mapeamento de APIs Web

## Status
`Draft`

## Contexto
Com a finalização de toda a estrutura de domínio, persistência em banco relacional (PostgreSQL) e em cache (Redis), e barramento de notificações polimórficas do monólito modularizado **Liga dos Palpites**, precisamos expor esses recursos de forma coesa e limpa para o aplicativo Flutter consumir. 

Os fluxos móveis exigem alta performance, validações rigorosas em relação a janelas de trancamento de palpites, segregação de rankings de ligas por fases de torneios (fase de grupos, mata-mata e classificação geral) e um ponto de agregação de latência ultra-baixa (BFF) para alimentar a tela principal sem sobrecarregar o app com múltiplas chamadas HTTP paralelas de rede celular.

Este documento registra as decisões arquiteturais para a implementação da camada de Web Controllers sob a ótica de Clean Architecture, as lógicas complexas de validação e o BFF Aggregator.

---

## Decisões Arquiteturais e Regras de Negócio Complexas

### 1. Descentralização de Controllers na Clean Architecture
Cada módulo de negócio no projeto (`sportsfeed`, `predictions`, `groups`, `users`, `notifications`) terá sua própria camada `infrastructure/web/` contendo seus respectivos `@RestController`s:
- Isso impede acoplamento indesejado entre os módulos.
- Os controllers convertem os payloads HTTP externos para DTOs internos e invocam as portas de entrada (Use Cases ou Services) da camada de aplicação do respectivo módulo.

---

### 2. Sincronismo e Atualização de Partidas (`sportsfeed`)
- **Controlador**: `FixtureController` (`/api/v1/sports/fixtures` e `/api/v1/sports/standings`)
- **Regras Complexas**:
  - Integrações externas recebem payloads de placar em tempo real e atualizam o estado das partidas (`tbl_matches`).
  - Quando uma partida transita para o status `FINISHED`, o adaptador correspondente dispara um evento interno do Spring (`MatchFinishedEvent`).
  - O módulo de palpites (`predictions`) escuta reativamente esse evento e processa em lote (batching) as pontuações de todos os usuários para aquele jogo usando o `ScoringEngine`.

---

### 3. Validação e Trancamento de Palpites (`predictions`)
- **Controlador**: `PredictionController` (`POST /api/v1/predictions` e `POST /api/v1/special-predictions`)
- **Regras Complexas**:
  - **Palpite de Partida**: O controller/use case consulta a partida (`tbl_matches`). Se o horário atual (`Instant.now()`) for maior ou igual ao horário do início do jogo (`kickoff_time`), retorna erro `400 Bad Request` com código `PREDICTION_LOCKED`.
  - **Palpites Especiais**: No caso de previsões de longo prazo (Ex: "Quem será o campeão?"), o palpite é trancado exatamente no início da **primeira partida do torneio geral** (metadado `tournament.startTime`). Qualquer submissão após esse marco é bloqueada.

---

### 4. Gerenciamento de Ligas e Rankings Divididos por Períodos (`groups`)
- **Controlador**: `GroupController` (`/api/v1/groups/...`)
- **Regras Complexas**:
  - **Administração**: Somente o criador do grupo (`creator_id` em `tbl_groups`) pode expulsar membros (`DELETE /api/v1/groups/{groupId}/members/{memberUserId}`).
  - **Pontuações por Períodos (Sub-Leaderboards)**: Para suportar telas de classificação segregadas por fase de torneio, mantemos três chaves distintas do Redis Sorted Set por grupo:
    - Fase de Grupos: `leaderboard:group:{groupId}:group-stage`
    - Mata-Mata: `leaderboard:group:{groupId}:knockout`
    - Geral/Acumulado: `leaderboard:group:{groupId}:overall`
  - Ao processar pontuações de palpites pós-jogo, o sistema identifica em qual fase a partida ocorreu e incrementa reativamente as chaves adequadas do Redis Sorted Set, além de atualizar o acumulador persistente no PostgreSQL.

---

### 5. BFF Dashboard Aggregator (`home`)
- **Controlador**: `DashboardController` (`GET /api/v1/home/dashboard`)
- **Regras Complexas**:
  - **Desempenho Assíncrono**: Para evitar gargalos e timeouts, o controller dispara requisições assíncronas paralelas usando `CompletableFuture` ou Coroutines para coletar:
    1. Perfil e pontuação atual do PostgreSQL.
    2. Ranking global do Redis.
    3. Próximo jogo de destaque do `sportsfeed`.
    4. Rankings resumidos dos grupos do usuário.
    5. Notícias atuais ingestadas pelo `sportsfeed`.
    6. Existência de notificações não lidas.
  - Consolida todas essas informações em um único payload unificado, economizando rádio e bateria nos aparelhos celulares.

---

## Consequências
- **Positivas**: Separação clara de responsabilidades, alta testabilidade unitária dos controladores, ótima performance de rede no app Flutter e garantia absoluta de integridade contra palpites enviados após o início dos jogos.
- **Negativas**: Leve aumento no número de arquivos de DTO e Controllers específicos por submódulo.
