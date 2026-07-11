# ADR-0007: App Dashboard and Modular BFF Gateway Architecture

## Status
Accepted

## Date
2026-07-10

## Context
The **Liga dos Palpites** Flutter mobile application features 5 primary user menus:
1. **Home**: An aggregated dashboard displaying active news, user statistics, the next upcoming match kickoff, highlights of active leagues, and unread notifications count.
2. **Jogos (Fixtures)**: The schedule of upcoming, ongoing, and finished matches.
3. **Palpites (Predictions)**: A view where users manage their predictions and check their history.
4. **Ligas (Leagues)**: Public and private groups where users compete against friends.
5. **Perfil (Profile)**: User preferences, active entitlements (subscriptions/passes), and notifications center settings.

To serve these menus, the backend must expose APIs. However, resolving the **Home** dashboard presents architectural challenges:
- **Chatty Client Anti-Pattern**: If the mobile client executes 5 different network requests (to news, user, matches, leagues, and notifications endpoints) on every launch, it increases device battery drainage, cellular traffic, and experiences compounding latencies (cold starts in serverless Cloud Run).
- **Module Coupling**: Aggregating these values directly in the database is impossible due to our **Database Autonomy** rule (no cross-module joins between contexts).
- **V1 to V2 Migration**: The legacy Flutter client expected Firestore schemas. The new REST APIs must support translations to remain backwards compatible.

We need an API architecture that optimizes mobile performance, keeps feature modules decoupled, and supports compatibility layers.

## Decision
We will adopt a **Modular BFF (Backend For Frontend) / API Gateway Aggregator** architecture, paired with a strict separation between API REST DTOs and internal domain aggregates.

```
                  ┌──────────────────────┐
                  │ Flutter Mobile App   │
                  └──────────┬───────────┘
                             │
            ┌────────────────┴────────────────┐
            │ GET /api/v1/home/dashboard      │
            ▼                                 ▼
┌───────────────────────┐         ┌───────────────────────┐
│  BFF API Gateway      │         │ Feature Controllers   │
│  (Parallel Queries)   │         │ (Jogos, Palpites...)  │
└──────────┬────────────┘         └──────────┬────────────┘
           │                                 │
     ┌─────┼──────────────┬─────────────┐    │ (Direct calls)
     ▼     ▼              ▼             ▼    ▼
┌──────────────┐   ┌─────────────┐   ┌───────────────┐
│ sports-feed  │   │ predictions │   │ notifications │ ...
│ Bounded Ctxt │   │ Bounded Ctxt│   │ Bounded Ctxt  │
└──────────────┘   └─────────────┘   └───────────────┘
```

### 1. BFF Aggregator Endpoint for Home Menu
We will implement a single REST route `GET /api/v1/home/dashboard` inside the main application module (`apps/main-app` controller layer):
- This BFF controller does not contain business logic.
- It calls the public facades of the respective contexts (`SportsFeedFacade`, `PredictionsFacade`, `NotificationsFacade`) in parallel using **Kotlin Coroutines** (`async`/`await`).
- It merges the results into a single, cohesive dashboard payload and returns it to the client, solving the chatty client issue with a single HTTP call.

### 2. Bounded Context REST Endpoints
For the remaining menus, the client calls the dedicated feature controllers directly (which delegate tasks directly to their respective use cases):
- **Jogos**: Handles match results, listings, and league standings.
- **Palpites**: Handles predictions creation and edit validations.
- **Ligas**: Handles group administrator tasks, invitations, and leaderboard caching lookup.
- **Perfil**: Handles profile detail retrieval, subscription verification, and preferences.

### 3. Separation of DTOs for Backwards Compatibility
To prevent internal database or domain refactorings from crashing the mobile client, and to allow the new PostgreSQL/Redis backend to support the legacy V1 Flutter client:
- We will strictly enforce that no JPA Entity or Domain Model is serialized directly to JSON.
- Every API endpoint will map to distinct, versioned **REST DTO** classes (e.g. `UserDashboardResponseV1`, `UserDashboardResponseV2`).
- A controller translation layer will map domain results to the expected legacy format if requested by V1 headers or legacy endpoints, facilitating a seamless backend migration.

## Consequences

### Positive (Benefits)
* **High Performance**: A single request compiles the entire mobile home screen, reducing latency and mobile battery consumption.
* **Preserved Decoupling**: Bounded contexts do not depend on each other for aggregation; the BFF sits on top and queries them via clean public interfaces.
* **Safe Client Migrations**: Versioned DTOs and mapping layers allow us to upgrade the database schema without breaking legacy mobile applications.

### Negative (Trade-offs)
* **Minor Complexity**: Requires maintaining versioned DTOs and mapper classes for older client versions.
