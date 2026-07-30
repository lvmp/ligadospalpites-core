# Índice de Decisões de Arquitetura (ADRs)

Este repositório utiliza **ADRs (Architecture Decision Records)** para registrar as decisões técnicas fundamentais tomadas durante a evolução da plataforma **Liga dos Palpites**.

Antes de propor alterações de banco de dados, infraestrutura ou padrões de código, leia a ementa abaixo para identificar quais documentos detalham as restrições arquiteturais aplicáveis à sua tarefa.

---

## 📂 Lista de Decisões Registradas

| Identificador & Documento | Status | Ementa / Decisão Principal |
| :--- | :--- | :--- |
| **[ADR-0001: Local Development & Testing](0001-local-development-and-testing-docker.md)** | `Accepted` | Padroniza o desenvolvimento local usando Docker Compose para PostgreSQL e Redis. Encoraja o uso de **Testcontainers** nos testes de integração para garantir isolamento e banco de dados limpos. |
| **[ADR-0002: Serverless Cloud Deployment](0002-cloud-deployment-free-tier.md)** | `Accepted` | Define a implantação na nuvem voltada para **custo zero (Free Tier)**. Hospeda a aplicação no **Google Cloud Run** (escala até zero), banco de dados no **Neon Postgres** (serverless) e cache no **Upstash Redis** (serverless). |
| **[ADR-0003: Data Strategy (Firebase vs. Postgres/Redis)](0003-data-strategy-firebase-postgres.md)** | `Accepted` | Resolve os gargalos de custos e concorrência do Firestore. Mantém no **Firebase** apenas autenticação (Auth), mídias (Storage) e pushes (FCM). Migra dados operacionais para **PostgreSQL** e rankings em tempo real para **Redis Sorted Sets (ZSET)**. |
| **[ADR-0004: Extensible Notification Delivery](0004-extensible-notification-delivery.md)** | `Accepted` | Desenha o módulo de notificações baseado no padrão **Strategy** (abstraindo In-app, Pushes e Emails). Define o ciclo de registro/atualização de tokens de dispositivos (`tbl_devices`) e a auto-limpeza de tokens expirados no banco. |
| **[ADR-0005: Serverless Scheduler Strategy](0005-serverless-scheduler-strategy.md)** | `Accepted` | Substitui cron interno do Spring (`@Scheduled`) por chamadas HTTP externas disparadas por schedulers em nuvem, garantindo compatibilidade com escala zero e execução em pod único. |
| **[ADR-0006: Polymorphic Sports Ingestion & Tournament Engine](0006-sports-data-sync-engine.md)** | `Accepted` | Transforma o processador monolítico do torneio em um motor polimórfico orientado a metadados (Strategy), suportando múltiplos esportes/regras de pontuação, sincronização e resolução dinâmica de chaves de mata-mata. |
| **[ADR-0007: App Dashboard and Modular BFF Gateway Architecture](0007-app-dashboard-bff-gateway.md)** | `Accepted` | Cria um agregador estilo BFF (Backend for Frontend) para consolidar a Home do aplicativo móvel em uma única requisição paralela, desacoplando DTOs externos para manter a compatibilidade do Flutter V1. |
| **[ADR-0008: REST Controller Layer & Web API Mapping](0008-controller-layer-and-web-api-integration.md)** | `Accepted` | Descentraliza controllers HTTP por submódulo em arquitetura limpa, definindo regras rígidas de trancamento de palpites, segregação de leaderboards por fase de torneio no Redis, e agregação assíncrona paralela do BFF Dashboard. |
| **[ADR-0009: RevenueCat Webhook Integration](0009-revenuecat-webhook-integration.md)** | `Accepted` | Consolida e simplifica a monetização da plataforma delegando a validação de compras nativas ao RevenueCat e recebendo eventos assíncronos protegidos via Webhook para atualizar direitos de acesso. |
| **[ADR-0010: Extensible Notification Dispatching and Targeting Engine](0010-notification-dispatching-and-targeting.md)** | `Accepted` | Define a arquitetura do motor de despacho e segmentação (targeting) de notificações de push em lote ou individuais (usuário, liga, esporte e broadcast geral). |
| **[ADR-0011: Automated Event-Driven Push Notifications](0011-automated-event-driven-push-notifications.md)** | `Accepted` | Define a arquitetura para envio automático de notificações push orientadas a eventos esportivos (início de jogo, gols marcados, fim de partida com cálculo reativo de palpites). |
| **[ADR-0012: Football Multi-League & Tournament Data Strategy](0012-football-multi-league-data-strategy.md)** | `Accepted` | Estratégia de dados de custo zero para 13 ligas de futebol via football-data.org, Libertadores via ESPN API e Copa do Brasil via Admin. |
| **[ADR-0013: Basketball Integration & Free Data Strategy](0013-basketball-data-strategy.md)** | `Accepted` | Estratégia para Basquete (NBA, WNBA, NCAA) via ESPN API + balldontlie, EuroLeague via EuroLeague JSON e NBB Brasil. |
| **[ADR-0014: eSports Pro Data & Riot Profiling Strategy](0014-esports-data-strategy.md)** | `Accepted` | Estratégia para eSports combinando PandaScore (torneios pro e séries BO1/BO3/BO5) e Riot Games API (perfis e elo de invocadores). |
| **[ADR-0015: Motorsport (F1, F2, FE, Stock Car) Data Strategy](0015-motorsport-data-strategy.md)** | `Accepted` | Estratégia para Automobilismo via Jolpica Ergast API (F1/F2), Formula E API e Stock Car Pro Series Brasil. |
| **[ADR-0016: American Football (NFL & College) Data Strategy](0016-american-football-data-strategy.md)** | `Accepted` | Estratégia para Futebol Americano (NFL e College) via ESPN Public API com parciais por quarto e escudos HD. |
| **[ADR-0017: Tennis (ATP, WTA & Grand Slams) Data Strategy](0017-tennis-data-strategy.md)** | `Accepted` | Estratégia para Tênis (ATP, WTA e Grand Slams) via ESPN Public API com acompanhamento parcial de sets. |

---

> [!NOTE]
> Para acessar o documento detalhado de cada decisão, clique nos links da tabela acima.
