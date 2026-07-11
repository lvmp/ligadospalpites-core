# Product Requirement Document (PRD): Liga dos Palpites

## 1. Vision & Executive Summary

**Liga dos Palpites** is a year-round **Sports Hub** designed to engage sports fans through news, schedules, real-time match tracking, and social prediction leagues ("palpites"). Originally launched with a focus on a single tournament (the FIFA World Cup), the application has evolved into a comprehensive multi-sport platform capable of aggregating multiple disciplines (e.g., Football/Soccer, Basketball, American Football) dynamically.

The application allows users to follow live scores, read news, and compete with friends in private or public prediction leagues. Each league can operate with its own unique set of scoring rules and tournament configurations.

To sustain the platform and incentivize growth, monetization is driven by mobile app store purchases (Apple App Store & Google Play Store), offering tiered subscription access, ad-removal packages, and sport-specific access passes ("Sport Passes").

---

## 2. Target Audience & Personas

- **The Casual Fan (Free Tier)**: Follows a single main sport (e.g., Football), configures their favorite league (e.g., Brasileirão), makes predictions to compete with friends, and tolerates ads.
- **The Focused Fan (Ad-Free Tier)**: Wants a cleaner user interface without ads, still focused primarily on their single preferred sport.
- **The Multi-Sport Enthusiast (Premium Tier / Sport Pass Buyer)**: Actively follows multiple sports (e.g., Premier League, NBA, NFL) and is willing to purchase Sport Passes or subscribe to Premium to unlock full access across disciplines.
- **The Administrator/Moderator**: Configures leagues, updates scores manually when needed, publishes news, and broadcasts official announcements or notifications.

---

## 3. Product Monetization & User Tiers

All subscription purchases, upgrades, and cancellations are handled strictly through native mobile app stores (**Apple In-App Purchases** and **Google Play Billing**).

The platform defines the following subscription tiers and purchase models:

| Tier / Product | Advertisements | Sport & League Access | Description & Entitlement rules |
| :--- | :--- | :--- | :--- |
| **Free Tier** | Enabled | **1 Active Sport** (All leagues within) | The user chooses their primary sport (e.g., Football) during onboarding. They have full access to view news, fixtures, and make predictions across all leagues in that sport. Access to other sports is locked. |
| **Ad-Free Tier** | Disabled | **1 Active Sport** (All leagues within) | Same access level as Free, but ads are removed from the interface. |
| **Sport Pass (IAP Add-on)** | Inherits from current tier | **+1 Unlocked Sport** | A one-time or subscription-based purchase that permanently or monthly unlocks an additional sport (e.g., Basketball) on the user's profile. |
| **Premium Hub (Subscription)** | Disabled | **All Sports & Leagues** | Unlocks all existing and future sports and leagues with an ad-free experience. |

### In-App Purchase (IAP) & Validation Flow
1. **Purchase Execution**: The user initiates a purchase/subscription (e.g., buying a "Basketball Pass" or upgrading to "Premium Hub") inside the Android or iOS mobile application.
2. **Receipt Generation**: The mobile OS executes the transaction through Google Play / Apple App Store and returns a payment receipt/token.
3. **Validation Call**: The mobile client sends the receipt token and User ID to the Liga dos Palpites backend.
4. **Backend Verification**: The backend calls Apple's App Store Server API / Google Play Developer API to verify the receipt's validity, expiration date, and product identifier.
5. **Subscription Activation**: If valid, the backend updates the user's plan state and unlocked sports in the database.
6. **Auto-Renewal & Webhooks**: The backend listens to App Store Server Notifications V2 and Google Play Developer Notifications (webhooks) to handle renewals, cancellations, refunds, and grace periods automatically.

---

## 4. Functional Requirements

### 4.1. Core Sports & League Management
- **FR-1.1**: The system must support modular sports ingestion, dynamically configuring sports (Football, Basketball, American Football, etc.) and their respective leagues.
- **FR-1.2**: Each league must have its own **scoring rules** for predictions:
  - *Exact Score Match* (e.g., guessing 2-1 and match ends 2-1).
  - *Goal Difference Match* (e.g., guessing 3-1, match ends 2-0 - correct winner and 2-goal margin).
  - *Winner/Draw Match* (e.g., guessing 1-0, match ends 2-1 - correct winner only).
  - *Custom weights* (different points for home vs. away predictions, or multiplier for high-scoring games).
- **FR-1.3**: Each league must support distinct **tournament formats**:
  - *Round-robin* (running league table where everyone plays/palpitates each round).
  - *Playoffs/Bracket* (knockout phases where predictions count towards head-to-head match-ups).

### 4.2. Onboarding & Preferences
- **FR-2.1**: **Onboarding Setup**: New users must select their preferred primary sport (e.g., Football) and at least one favorite league (e.g., Brasileirão) during registration/first-launch setup.
- **FR-2.2**: **Dynamic Initial Screen**: Upon app start, the client must query the user preferences and initialize directly into the active dashboard of their preferred sport and league.
- **FR-2.3**: **Fluid Exploration UI**: The system must expose catalog endpoints that allow the client to render a fluid sports/leagues exploration menu, marking content as "unlocked" or "locked" (with purchase triggers).
- **FR-2.4**: **Consolidated Home Dashboard (BFF)**: The backend must expose a single aggregated Backend-For-Frontend (BFF) endpoint to retrieve the complete Home dashboard state (news, next match countdown, active league ranks, notifications count) in a single request.

### 4.3. Prediction (Palpite) Engine
- **FR-3.1**: Registered users can submit or edit predictions for upcoming matches up until the match kickoff time.
- **FR-3.2**: The prediction interface must lock exactly at the match's official scheduled kickoff time (no late predictions allowed).
- **FR-3.3**: Upon completion of a match and result ingestion, the prediction engine must automatically calculate points for all user predictions in the associated leagues based on each league's specific scoring rules.
- **FR-3.4**: **Special Tournament Predictions**: The system must support special context-driven predictions (such as Champion prediction) that lock at the kickoff time of the first match of the tournament.

### 4.4. Plan & Access Controller
- **FR-4.1**: The system must enforce active sport permissions based on the user's subscription tier and purchased Sport Passes.
- **FR-4.2**: If a user attempts to access fixtures, rankings, predictions, or news for a sport they do not have unlocked, the system must return a locked-content code, preventing access and supplying the product identifier required to buy access.
- **FR-4.3**: The system must record and track receipt validation histories, active entitlement structures (unlocked sports, plans), and store product IDs.

### 4.5. Notification Center & Dispatch Engine
- **FR-5.1**: The system must support polymorphic delivery channels for notifications:
  - **In-App Notification Center**: A feed inside the app showing notifications (bell icon).
  - **Mobile Push Notifications**: Real-time push alerts sent to mobile devices using Firebase Cloud Messaging (FCM).
  - **Email Alerts**: Email messages sent to the user's registered address (e.g., for receipts, digests, and critical alerts).
- **FR-5.2**: The system must support two notification types:
  - **Administrative Broadcasts**: Sent by the system administrator to all users or specific segments (e.g., maintenance alerts, new features).
  - **Sports/League Event Alerts**: Automated triggers based on the user's active/associated sports or leagues (e.g., match kickoff reminder, goal alerts, final score, league ranking updates).
- **FR-5.3**: Users must be able to opt-in or opt-out of specific notification categories (e.g., mute goal alerts, keep ranking updates) and select their preferred delivery channels.

### 4.6. Content & News
- **FR-6.1**: The app must display a news feed containing sports news, league announcements, and match previews.
- **FR-6.2**: News articles can be created and published manually by administrators via the Admin Panel, or aggregated automatically from trusted RSS feeds/sports providers.

---

## 5. Non-Functional Requirements

### 5.1. Performance & Scalability
- **NFR-1.1**: The prediction submittal API must handle high write throughput in the minutes leading up to major matches (e.g., 5,000 requests per second with < 200ms latency).
- **NFR-1.2**: Rankings and leaderboards must be updated eventually consistent (within 5 minutes of a match finishing) to avoid heavy database locks.
- **NFR-1.3**: Push notifications must be dispatched to users within 60 seconds of a triggered event (e.g., match goal).

### 5.2. Security & Compliance
- **NFR-2.1**: Store receipt verification must occur over secure TLS 1.3 channels, validating cryptographic signatures of Apple and Google payloads.
- **NFR-2.2**: Access to administrative endpoints must be secured using Role-Based Access Control (RBAC) via OAuth2/JWT.
- **NFR-2.3**: Personal user data must be stored and processed in compliance with LGPD/GDPR guidelines.

### 5.3. Reliability & Availability
- **NFR-3.1**: The system must maintain 99.9% uptime for core prediction and result retrieval flows.
- **NFR-3.2**: In case of a third-party Sports API failure, the system must allow manual match scheduling and score updates via the Admin Panel to prevent blocking predictions.

### 5.4. Compatibility & Frameworks
- **NFR-4.1**: The backend services must target **Kotlin** and **Spring Boot 4.1.0** as the stable core framework version to ensure modern feature compatibility and prevent technical debt.

---

## 6. Integration Specifications

### 6.1. Third-Party Sports API (e.g., API-Sports)
- **Purpose**: Automate the ingestion of sports metadata (sports, countries, leagues, seasons), fixtures, match status, live updates, and official scores.
- **Mechanism**: Scheduled cron jobs poll the API for upcoming matches (daily) and real-time updates of live matches (every 60 seconds). A webhook mechanism is preferred if supported by the provider.

### 6.2. Firebase Cloud Messaging (FCM)
- **Purpose**: Dispatch real-time push alerts to iOS and Android applications.
- **Mechanism**: Users register their FCM device tokens upon login. Tokens are stored linked to the User ID. The notification engine maps user preferences to active tokens to deliver personalized push alerts.

### 6.3. Store Validation Engines (Apple & Google Play)
- **Apple App Store Server API**: Validates receipts and listens to App Store Server Notifications V2 notifications (JSON Web Signature - JWS format).
- **Google Play Developer API**: Verifies purchases using Google API Client libraries and listens to Real-time Developer Notifications via Google Cloud Pub/Sub.

### 6.4. External Cron Trigger Engine
- **Purpose**: Periodically invoke background synchronization jobs (such as match scoring, standings calculations, and news fetching) in a serverless container environment.
- **Mechanism**: An external cron service (like Google Cloud Scheduler) makes periodic, secure HTTP requests to internal endpoints, scaling up the container instances on-demand and routing execution to a single pod.

### 6.5. Firebase Firestore (User Profiles & Settings)
- **Purpose**: Persist user display details and application configuration preferences to minimize high-write load on the primary relational database.
- **Mechanism**: The mobile client writes settings and profile data (e.g. displayName, favoriteSport, notifications preferences) directly to Firestore. The Spring Boot backend queries this collections group data when aggregating leaderboards and profile outputs.

---

## 7. Open Questions / Future Enhancements

1. **Social Features**: Will the app support chat/comments inside the leagues in future phases?
2. **Offline Support**: Should predictions be cached locally and synced when the device regains connection, or is an active internet connection required? (Currently, active connection is assumed).
3. **External News Providers**: Which RSS feeds or sports news agencies will be integrated for the automated news section, or will it start as 100% manual admin entries?
