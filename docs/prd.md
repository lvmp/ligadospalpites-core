# Product Requirement Document (PRD): Liga dos Palpites

## 1. Vision & Executive Summary

**Liga dos Palpites** is a year-round, multi-sport **Sports Hub** designed to engage fans through real-time news, match schedules, live score tracking, and social prediction leagues ("palpites"). Originally conceived as a single-tournament app (FIFA World Cup), it has evolved into a comprehensive sports ecosystem aggregating multiple disciplines (**Football, eSports, Basketball, Motorsport, American Football, Tennis**) dynamically.

Rather than relying on client-side direct writes or decentralized synchronization, **the entire mobile application (Flutter) is powered by a robust, centralized Spring Boot REST Backend API**. This backend acts as the single source of truth, managing business logic, real-time rank updates, transactional predictions, and entitlement controls.

The platform sustains itself through native app store purchases (Apple App Store & Google Play Store), offering tiered subscription access, ad-removal packages, and sport-specific access passes ("Sport Passes").

---

## 2. Target Audience & Personas

- **The Casual Fan (Free Tier)**: Follows a single main sport (e.g., Football), configures their favorite league (e.g., Brasileirão), makes predictions to compete with friends, and tolerates ads.
- **The Focused Fan (Ad-Free Tier)**: Wants a cleaner user interface without ads, still focused primarily on their single preferred sport.
- **The Multi-Sport Enthusiast (Premium Tier / Sport Pass Buyer)**: Actively follows multiple sports (e.g., Premier League, NBA, eSports, F1, NFL) and is willing to purchase Sport Passes or subscribe to Premium to unlock full access across disciplines.
- **The Administrator/Moderator**: Configures leagues, updates scores manually when needed (e.g., Copa do Brasil, Stock Car), publishes news, and broadcasts official announcements or notifications.

---

## 3. Product Monetization & User Tiers

All subscription purchases, upgrades, and cancellations are handled strictly through native mobile app stores (**Apple In-App Purchases** and **Google Play Billing**).

The platform defines the following subscription tiers and purchase models:

| Tier / Product | Advertisements | Sport & League Access | Description & Entitlement rules |
| :--- | :--- | :--- | :--- |
| **Free Tier** | Enabled | **1 Active Sport** (All leagues within) | The user chooses their primary sport (e.g., Football) during onboarding. They have full access to view news, fixtures, and make predictions across all leagues in that sport. Access to other sports is locked. |
| **Ad-Free Tier** | Disabled | **1 Active Sport** (All leagues within) | Same access level as Free, but ads are removed from the interface. |
| **Sport Pass (IAP Add-on)** | Inherits from current tier | **+1 Unlocked Sport** | A one-time or subscription-based purchase that permanently or monthly unlocks an additional sport (e.g., Basketball or eSports) on the user's profile. |
| **Premium Hub (Subscription)** | Disabled | **All Sports & Leagues** | Unlocks all existing and future sports and leagues with an ad-free experience. |

---

## 4. Multi-Sport Ecosystem & Data Strategy

To maintain a zero-cost infrastructure footprint (conforme **[ADR-0002](file:///c:/Users/Vinicius/workspace/ligadospalpites-core/docs/adr/0002-cloud-deployment-free-tier.md)**), the system utilizes specialized, zero-cost / freemium APIs for each sport category.

### 📊 Master Sports Data Matrix

| Sport Category | Supported Leagues & Competitions | Primary API Provider | Secondary / Visual Provider | Detailed PRD Document |
| :--- | :--- | :--- | :--- | :--- |
| ⚽ **Football** | Brasileirão, Premier League, La Liga, Champions League, Ligue 1, Bundesliga, Serie A, Eredivisie, Primeira Liga, Championship | **football-data.org** | **TheSportsDB** (Logos/Stadiums) | **[docs/prd.md](file:///c:/Users/Vinicius/workspace/ligadospalpites-core/docs/prd.md)** |
| 🏆 **South American Football** | **Copa Libertadores** & **Copa do Brasil** | **ESPN Public API** (`conmebol.libertadores`) & Admin Panel / Seed | **TheSportsDB** | **[docs/prd.md](file:///c:/Users/Vinicius/workspace/ligadospalpites-core/docs/prd.md)** |
| 🎮 **eSports** | CBLOL, VCT Americas, CS2 Majors, Worlds, Dota 2 International | **PandaScore API** (Pro Matches & Fixtures) | **Riot Games API** (User Profiles & Ranks) | 📄 **[docs/esports_prd.md](file:///c:/Users/Vinicius/workspace/ligadospalpites-core/docs/esports_prd.md)** |
| 🏀 **Basketball** | NBA, NBB (Brasil), EuroLeague, WNBA, NCAA | **balldontlie.io** + **ESPN Public API** + **EuroLeague API** | **TheSportsDB** (Arena/Logos) | 📄 **[docs/basketball_prd.md](file:///c:/Users/Vinicius/workspace/ligadospalpites-core/docs/basketball_prd.md)** |
| 🏎️ **Motorsport** | Fórmula 1, Fórmula 2, Fórmula E, Stock Car Pro Series | **Jolpica Ergast API** + **Formula E API** + **Stock Car API** | **OpenF1** + **TheSportsDB** | 📄 **[docs/motorsport_prd.md](file:///c:/Users/Vinicius/workspace/ligadospalpites-core/docs/motorsport_prd.md)** |
| 🏈 **American Football** | NFL (Regular, Playoffs, Super Bowl) & College Football | **ESPN Public API** (`football/nfl`) | **ESPN CDN** (500x500 PNG) | 📄 **[docs/nfl_prd.md](file:///c:/Users/Vinicius/workspace/ligadospalpites-core/docs/nfl_prd.md)** |
| 🎾 **Tennis** | ATP Tour, WTA Tour, Grand Slams (Wimbledon, US Open, Roland Garros) | **ESPN Public API** (`tennis/atp` & `wta`) | **ESPN CDN** | 📄 **[docs/tennis_prd.md](file:///c:/Users/Vinicius/workspace/ligadospalpites-core/docs/tennis_prd.md)** |

---

## 5. Functional Requirements

### 5.1. Core Sports & League Management
- **FR-1.1**: The system must support modular sports ingestion, dynamically configuring sports (Football, eSports, Basketball, Motorsport, American Football, Tennis) and their respective leagues according to **[ADR-0006](file:///c:/Users/Vinicius/workspace/ligadospalpites-core/docs/adr/0006-sports-data-sync-engine.md)**.
- **FR-1.2**: Each league must have its own **scoring rules** for predictions:
  - *Football Exact Score Match* (e.g., 2-1 exact score = 25 pts).
  - *eSports Series Score* (e.g., 2-1 in a BO3 series = 25 pts).
  - *Basketball Point Margin* (e.g., guessing winner + exact victory margin).
  - *Motorsport Quali/Race Selection* (Pole Position, Race Winner, Top 3 Podium).
  - *Tennis Set Score* (e.g., 2-1 or 3-0 set score).
- **FR-1.3**: Each league must support distinct **tournament formats**:
  - *Round-robin* (running league table).
  - *Playoffs/Bracket* (knockout phases).
  - *Race GP Calendar* (Motorsport GP stages).

### 5.2. Onboarding & Preferences
- **FR-2.1**: **Onboarding Setup**: New users select their preferred primary sport (e.g., Football, eSports, NBA) and favorite league during onboarding.
- **FR-2.2**: **Dynamic Initial Screen**: Upon app start, the client initializes directly into the active dashboard of their preferred sport and league.
- **FR-2.3**: **Fluid Exploration UI**: Catalogs mark content as "unlocked" or "locked" (with purchase triggers for Sport Passes).
- **FR-2.4**: **Consolidated Home Dashboard (BFF)**: The backend exposes a single aggregated Backend-For-Frontend (BFF) endpoint to retrieve the complete Home dashboard state in a single request.

### 5.3. Prediction (Palpite) Engine
- **FR-3.1**: Registered users can submit or edit predictions up until match kickoff or race session start time.
- **FR-3.2**: Prediction interfaces lock automatically at kickoff/start time.
- **FR-3.3**: Upon completion of a match/race, the engine automatically calculates points based on league-specific scoring rules.

---

## 6. Non-Functional Requirements

### 6.1. Performance & Scalability
- **NFR-1.1**: High write throughput (< 200ms latency) leading up to major matches.
- **NFR-1.2**: Leaderboard updates processed inside **Upstash Redis ZSETs** within 5 minutes of a match finishing.
- **NFR-1.3**: Notification dispatch within 60 seconds of triggered events (goals, touchdowns, race winners).

### 6.2. Security & Reliability
- **NFR-2.1**: Store receipt verification over secure TLS 1.3 channels (Apple JWS & Google Play API).
- **NFR-2.2**: Resilience4j Circuit Breakers wrapping all external HTTP clients (ESPN, football-data, PandaScore, Ergast) to prevent cascade failures.

---

## 7. Sub-System PRD Links & Documentation Index

- 📄 **[docs/esports_prd.md](file:///c:/Users/Vinicius/workspace/ligadospalpites-core/docs/esports_prd.md)** — eSports (LoL, CS2, Valorant, Dota 2) Integration & Strategy.
- 📄 **[docs/basketball_prd.md](file:///c:/Users/Vinicius/workspace/ligadospalpites-core/docs/basketball_prd.md)** — Basketball (NBA, NBB, EuroLeague, WNBA) Integration & Strategy.
- 📄 **[docs/motorsport_prd.md](file:///c:/Users/Vinicius/workspace/ligadospalpites-core/docs/motorsport_prd.md)** — Motorsport (Fórmula 1, F2, Fórmula E, Stock Car) Integration & Strategy.
- 📄 **[docs/nfl_prd.md](file:///c:/Users/Vinicius/workspace/ligadospalpites-core/docs/nfl_prd.md)** — American Football (NFL & College Football) Integration & Strategy.
- 📄 **[docs/tennis_prd.md](file:///c:/Users/Vinicius/workspace/ligadospalpites-core/docs/tennis_prd.md)** — Tennis (ATP Tour, WTA Tour, Grand Slams) Integration & Strategy.
- 📄 **[docs/rest_api_contract_mobile.md](file:///c:/Users/Vinicius/workspace/ligadospalpites-core/docs/rest_api_contract_mobile.md)** — Official Mobile REST API Contract.
