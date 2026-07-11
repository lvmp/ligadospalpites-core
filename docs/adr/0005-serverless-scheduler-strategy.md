# ADR-0005: External HTTP Scheduler Strategy

## Status
Accepted

## Date
2026-07-10

## Context
The **Liga dos Palpites** core backend is designed to run in a cost-effective, serverless container environment (**Google Cloud Run**). It relies on periodic background tasks to synchronize live match data, process prediction scores, resolve tournament brackets, and fetch news feeds.

Traditional monolithic applications implement scheduling using standard Java framework annotations like Spring Boot's `@Scheduled`. However, in our serverless, auto-scaling architecture, this approach introduces two fatal limitations:

1. **Scale-to-Zero Silence**: Cloud Run suspends or terminates container instances when there is no incoming traffic to maintain a $0/month cost model. When instances are scaled down to 0, JVM background threads do not run, and all scheduled cron tasks are skipped.
2. **Concurrency Duplication**: When the application experiences heavy traffic (such as kickoff surges) and scales horizontally to multiple container replicas (pods), an internal `@Scheduled` task would trigger **on every single active pod** at the same time. This leads to concurrent database updates, write lock contention, API rate-limit exhaustion, and waste of compute resources.

We need a task execution model that operates under scale-to-zero, scales up compute instances on demand, and guarantees that a task is processed by a single instance at any given time.

## Decision
We will reject the use of internal, thread-based Spring `@Scheduled` annotations for background synchronizations. Instead, we will adopt a pull-based **External HTTP Scheduler** strategy.

### 1. HTTP Endpoint Ingestion
We will expose dedicated REST endpoints under an internal administration namespace (e.g., `POST /api/v1/internal/scheduler/process` and `POST /api/v1/internal/news/sync`). These endpoints will orchestrate the background execution logic when invoked.

### 2. External Cron Triggers
We will use an **External Cron Scheduler** (such as **Google Cloud Scheduler** or any other external scheduling daemon/service) to trigger these endpoints via periodic HTTP POST requests:
- **Offload execution**: The external cron daemon maintains the timer. 
- **Scale up on-demand**: When Cloud Scheduler fires, Google Cloud Run automatically boots an instance (if scaled to 0), handles the request, and allows it to scale down to 0 after processing.
- **Single-instance delivery**: In a horizontally scaled environment with multiple active replicas, the Google Cloud load balancer routes the HTTP request to **exactly one** instance. This guarantees that only one pod executes the sync task, completely eliminating duplicate runs and race conditions.

```
┌────────────────────────┐
│ GCP Cloud Scheduler    │
│ (Trigger: every 5 min) │
└──────────┬─────────────┘
           │
           │ 1. Secure HTTP POST
           ▼
┌────────────────────────┐
│  GCP Load Balancer     │
└──────────┬─────────────┘
           │
           │ 2. Routes to exactly one replica
           ▼
┌────────────────────────┐
│ Spring Boot Container  │
│ (Processes sync task)  │
└────────────────────────┘
```

### 3. Secure Internal Namespace
To prevent unauthorized users from triggering resource-heavy sync endpoints, we will secure these routes:
- The external scheduler must include a signed OIDC token in the authorization header.
- Spring Security will validate this token to verify the request comes from the configured cloud service account before executing the task.

## Consequences

### Positive (Benefits)
* **Scale-to-Zero Compatibility**: The application can sleep 24/7 and will only wake up when Cloud Scheduler calls the sync endpoints.
* **Concurrency Safety**: Load balancers route HTTP calls to a single pod, removing the need for distributed lock managers (like ShedLock or Redis locks) to prevent double-execution.
* **Independent Scalability**: Different tasks can be triggered at different intervals (e.g. news synced once a day, football live matches synced every minute during active windows) simply by setting up separate scheduler rules.

### Negative (Trade-offs)
* **API Overhead**: The sync endpoints must be public-facing, requiring security validation (OIDC verification) to be implemented and tested.
* **External Configuration**: Deployment scripts must manage the external scheduler job configurations alongside the code.
