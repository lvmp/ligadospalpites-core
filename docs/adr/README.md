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
| **[ADR-0004: Extensible Notification Module](0004-extensible-notification-delivery.md)** | `Accepted` | Desenha o módulo de notificações baseado no padrão **Strategy** (abstraindo In-app, Pushes e Emails). Define o ciclo de registro/atualização de tokens de dispositivos (`tbl_devices`) e a auto-limpeza de tokens expirados no banco. |
| **[ADR-0005: External HTTP Scheduler Strategy](0005-serverless-scheduler-strategy.md)** | `Accepted` | Substitui cron interno do Spring (`@Scheduled`) por chamadas HTTP externas disparadas por schedulers em nuvem, garantindo compatibilidade com escala zero e execução em pod único. |
| **[ADR-0006: Polymorphic Sports Ingestion & Tournament Engine](0006-sports-data-sync-engine.md)** | `Accepted` | Transforma o processador monolítico do torneio em um motor polimórfico orientado a metadados (Strategy), suportando múltiplos esportes/regras de pontuação, sincronização e resolução dinâmica de chaves de mata-mata. |
| **[ADR-0007: App Dashboard and Modular BFF Gateway Architecture](0007-app-dashboard-bff-gateway.md)** | `Accepted` | Cria um agregador estilo BFF (Backend for Frontend) para consolidar a Home do aplicativo móvel em uma única requisição paralela, desacoplando DTOs externos para manter a compatibilidade do Flutter V1. |

---

> [!NOTE]
> Para acessar o documento detalhado de cada decisão, clique nos links da tabela acima.
