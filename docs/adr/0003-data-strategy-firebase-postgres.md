# ADR-0003: Data Partitioning and Strategy (Firebase vs. PostgreSQL/Redis)

## Status
Accepted

## Date
2026-07-10

## Context
The application was originally conceptualized around a pure Firebase stack (Firestore database and Firebase Authentication). However, storing core domain data (news, league groups, matches, predictions, and scores) directly in Firestore presents significant scalability and cost bottlenecks as the application grows:
1. **Firestore Pricing Model**: Firestore bills based on individual document reads, writes, and deletes. A match finishing with 10,000 active users predicting would require massive write operations to record points, and subsequent reads by users checking leaderboards would generate millions of billing events daily.
2. **Write Limits**: Firestore has a physical limit of 10,000 writes per second per database, and more critically, a limit of **1 write per second on a single document**. Real-time ranking tables inside groups would hit lock limits and contention errors.
3. **Complex Aggregations**: Firestore does not natively support complex aggregate queries (e.g., sum of points, ranking position, sorting relative values) without reading all documents or maintaining highly duplicated state counters.
4. **Relational Relationships**: League rules, match results, user predictions, and group memberships are highly relational in nature and fit best in a relational schema.

We need to partition our application data to maintain a robust, cost-effective, and highly scalable system.

## Decision
We will separate concerns by migrating operational domain data to a relational PostgreSQL database and utilizing Redis for caching and high-performance leaderboards, while keeping Firebase for stateless authentication, static asset hosting, and push messaging.

```mermaid
flowchart TD
    subgraph Mobile Client
        App[Mobile Application]
    end

    subgraph Firebase (SaaS Services)
        Auth[Firebase Auth]
        FCM[Firebase Cloud Messaging]
        Storage[Firebase Storage]
    end

    subgraph Spring Boot Backend (Cloud Run)
        API[REST API Controllers]
        EventPublisher[Spring Event Publisher]
        Observer[EventListener / Observer]
    end

    subgraph Relational Database (Neon)
        Postgres[(PostgreSQL)]
    end

    subgraph Cache & Rankings (Upstash)
        Redis[(Redis ZSETs)]
    end

    App -->|1. Login| Auth
    App -->|2. HTTP Request + JWT| API
    API -->|Read/Write Core Data| Postgres
    EventPublisher -->|Async Event| Observer
    Observer -->|3. Update Rankings| Redis
    API -->|Fast Read Leaderboards| Redis
    Observer -->|4. Push Event Alerts| FCM
    App -->|Get Images/Badges| Storage
```

### 1. What Remains in Firebase
- **Firebase Authentication**: We will continue to use Firebase Auth for identity management, login, password resets, and social OAuth providers (Google, Apple). The Spring Boot backend will act as a resource server validating the JWS ID Tokens sent by the client. This handles user sessions for free (up to 50k MAUs).
- **Firebase Cloud Messaging (FCM)**: Used strictly for dispatching push notifications. 
- **Firebase Storage**: Used for hosting raw media assets (e.g., news banner images, team badge PNGs, user avatars).

### 2. What Migrates to PostgreSQL (Neon)
All core transactional and operational entities will reside in PostgreSQL tables:
- **`tbl_users`**: Maps Firebase UID (as a string) to local user profiles, configurations, and favorite sports/leagues.
- **`tbl_matches` (Partidas)**: Ingested fixture schedules, team names, kickoff dates, current status, and final scores.
- **`tbl_predictions` (Palpites)**: Predictions submitted by users before a match. Includes a column for points awarded.
- **`tbl_news` (Notícias)**: Published news metadata and text contents.
- **`tbl_groups` (Grupos)**: Custom league tables created by users to compete with friends, configurations, and unique scoring rules.
- **`tbl_group_members`**: Join table mapping users to their respective groups.

### 3. Caching and Real-Time Leaderboards (Redis)
To offload PostgreSQL from heavy analytical queries, we will use serverless Redis:
- **Real-Time Leaderboards**: We will use **Redis Sorted Sets (ZSET)**.
  - For the **Global Leaderboard**, we will maintain a ZSET key `leaderboard:global` where members are `userId` (values) and scores are their accumulated points (double).
  - For each **Group Leaderboard**, we will maintain a ZSET key `leaderboard:group:<groupId>` containing members of that group and their score in it.
  - Leaderboard queries like "Fetch top 10 players" will use `ZREVRANGE leaderboard:global 0 9 WITHSCORES` which executes in $O(\log(N) + M)$ complexity with sub-millisecond response times.
  - Getting a user's specific ranking position will use `ZREVRANK leaderboard:global <userId>` in $O(1)$ complexity.

### 4. Leaderboard Consistency: Observer Pattern
To keep database transactions short and avoid blocking user prediction lookups during match scoring:
- When a match is marked as finished, the `sports-feed` module publishes a `MatchFinishedEvent`.
- The `predictions` module listens, calculates points for all corresponding predictions in PostgreSQL, and publishes a `PredictionsProcessedEvent`.
- **Asynchronous Observer**: We will implement the Observer pattern using Spring's `@EventListener` (configured with `@Async` or running via event listener thread executor) to process the leaderboard updates.
- The observer reads the calculated points and pushes the incremented scores to Redis using `ZADD` or `ZINCRBY`, and updates PostgreSQL group ranking aggregates asynchronously.

## Consequences

### Positive (Benefits)
* **High Write Throughput**: PostgreSQL handles high prediction submission peaks (e.g., 5,000 requests/sec) much better than Firestore, with low transaction latency.
* **Instant Leaderboard Renders**: Redis ZSETs completely eliminate heavy SQL aggregate queries (`SUM(...) GROUP BY ... ORDER BY ...`), allowing immediate leaderboard scrolling on mobile devices.
* **Minimal Cost**: Relational database operations and Redis commands fit comfortably within the free tier thresholds of Neon and Upstash.
* **Secure and Standard Login**: Leverages Firebase Auth's robust security without needing to implement security protocols (OAuth, MFA) on the backend.

### Negative (Trade-offs)
* **Hybrid Architecture Complexity**: The development stack requires maintaining mappings between Firebase UID and PostgreSQL local primary keys.
* **Eventually Consistent Leaderboards**: Rankings may take a few seconds to update after a match finishes due to the asynchronous observer processing. This is acceptable per NFR-1.2 (rankings updated within 5 minutes).
