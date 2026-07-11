# ADR-0002: Serverless Cloud Deployment Strategy utilizing GCP Free-Tier, Neon, and Upstash

## Status
Accepted

## Date
2026-07-10

## Context
The **Liga dos Palpites** core backend is self-funded through advertising revenue, which requires a strict target of **$0/month infrastructure cost** during its initial phases. 

The application architecture requires:
1. **Compute Resource**: A container running the Kotlin Spring Boot application.
2. **Relational Database**: PostgreSQL (for persistent domain models).
3. **In-Memory Cache**: Redis (for user sessions and performance-critical leaderboard tables).

Traditional cloud options (like hosting a virtual machine on AWS EC2 or GCP Compute Engine, and running managed databases like AWS RDS or GCP Cloud SQL) are not viable long-term:
- AWS EC2/RDS free tiers expire after 12 months.
- GCP Cloud SQL has no free tier and costs a minimum of ~$10/month.
- A single GCP always-free VM (`e2-micro`) has only 1 GB RAM, which is insufficient to run a Spring Boot JVM, PostgreSQL, and Redis simultaneously without heavy swap usage and high risk of Out-Of-Memory (OOM) crashes.
- Running VMs 24/7 consumes credits/quotas continuously even when there is no user traffic.

We need a scalable, resilient deployment strategy that operates entirely within perpetual free tiers.

## Decision
We will adopt a **Serverless Stack** combining Google Cloud Platform (GCP) compute with specialized third-party serverless database providers. This allows the entire infrastructure to scale down to zero when idle, resulting in a true $0/month cost.

### 1. Compute: Google Cloud Run
- **Why**: Google Cloud Run is a managed serverless platform that runs containerized applications. It offers a permanent free tier of **2 million requests per month**, 360,000 vCPU-seconds, and 180,000 GiB-seconds.
- **Scale to Zero**: When no requests are incoming, Cloud Run scales down to 0 instances. In this state, we pay absolutely nothing.
- **Scaling Up**: During prediction surges (e.g., right before a major match kickoff), Cloud Run automatically scales up horizontally to handle thousands of requests per second.

### 2. Database: Neon Serverless PostgreSQL (`neon.tech`)
- **Why**: Neon provides serverless PostgreSQL. Its free tier offers 1 project, 10 branches, 0.5 GB of storage, and 100 active compute hours per month.
- **Scale to Zero**: Neon compute endpoints automatically suspend after 5 minutes of inactivity. When suspended, it consumes no active compute hours.
- **Connection Pooling**: Neon includes a built-in PgBouncer pooler. When Spring Boot scales horizontally in Cloud Run, we will connect to Neon’s pooled endpoint (`-pooler` suffix) to prevent "Too many connections" errors.

### 3. Caching & Leaderboards: Upstash Serverless Redis (`upstash.com`)
- **Why**: Upstash is a serverless Redis database designed for serverless architectures. The free tier offers up to **500,000 commands per day** and 256 MB of storage.
- **Compatibility**: Upstash Redis is compatible with standard Redis clients (like Spring Boot's Lettuce client) and does not require managing persistent TCP connections, which is ideal for stateless Cloud Run instances.

### 4. Configuration & Secret Management
We will inject connection parameters via environment variables inside the Cloud Run configuration:
- `SPRING_DATASOURCE_URL`: Neon pooled connection string (e.g., `jdbc:postgresql://ep-project-pooler.us-east-2.aws.neon.tech/neondb?sslmode=require`).
- `SPRING_DATA_REDIS_HOST` & `SPRING_DATA_REDIS_PORT` & `SPRING_DATA_REDIS_PASSWORD`: Upstash Redis credentials.

## Consequences

### Positive (Benefits)
* **True Zero-Cost Infrastructure**: With low-to-moderate traffic, the system runs completely free of charge indefinitely.
* **Automatic Elastic Scaling**: The system naturally absorbs huge traffic peaks (e.g. 5,000 rps before a game) by scaling Cloud Run instances, and scales down to zero when the game is live or overnight.
* **No Database Maintenance**: Both Neon and Upstash are fully managed serverless platforms. Backups, updates, and indexing are handled automatically.

### Negative (Trade-offs)
* **Cold Starts**: 
  - **Application Cold Start**: If the application has scaled to 0, the first request will experience a bootstrap latency of 3-7 seconds while Spring Boot starts up.
  - **Database Cold Start**: If the Neon database has suspended, the first connection query will have an additional 2-3 second delay to wake up the database compute instance.
  - *Mitigation*: We will optimize Spring Boot startup configurations (e.g., lazy initialization of non-critical beans, JVM memory optimizations).
* **Dependency on Multiple Providers**: The stack is split across GCP, Neon, and Upstash, requiring separate dashboards for billing monitoring.
* **Free Tier Limits**: If the application gains large popularity (e.g., exceeding 2 million requests or 500k Redis commands daily), we will transition to pay-as-you-go, which will be funded by the app's ad revenue.
