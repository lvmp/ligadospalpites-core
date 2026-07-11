# Spec-0006: Mobile App Menus REST API & Domain Specifications

This specification details the endpoints, JSON payloads, business rules, and validation logic for the 5 menus of the mobile application (Jogos, Palpites, Ligas, Perfil, Home), targeting **Spring Boot 4.1.0** and **Kotlin 1.9+**.

---

## 1. Jogos Menu (Fixtures & Standings)

Serves sports matches and team standings in the tournament.

### A. List Fixtures
- **Endpoint**: `GET /api/v1/sports/fixtures?sportId={sportId}&leagueId={leagueId}`
- **Response Format (`200 OK`)**:
  ```json
  [
    {
      "matchId": "d0d6a9e1-2b3c-4d5e-6f7a-8b9c0d1e2f3a",
      "homeTeam": "Flamengo",
      "awayTeam": "Palmeiras",
      "kickoffTime": "2026-07-09T22:30:00Z",
      "status": "SCHEDULED", // SCHEDULED, LIVE, FINISHED, CANCELLED
      "scoreHome": null,
      "scoreAway": null
    }
  ]
  ```

### B. Get League Standings (Table)
- **Endpoint**: `GET /api/v1/sports/standings?sportId={sportId}&leagueId={leagueId}`
- **Response Format (`200 OK`)**:
  ```json
  [
    {
      "position": 1,
      "teamId": "a1b2c3d4-e5f6-7788-9900-aabbccddeeff",
      "teamName": "Brasil",
      "points": 9,
      "played": 3,
      "won": 3,
      "drawn": 0,
      "lost": 0,
      "goalsFor": 8,
      "goalsAgainst": 1,
      "goalDifference": 7
    }
  ]
  ```

---

## 2. Palpites Menu (Predictions & Special Predictions)

Handles user predictions and rules validation.

### A. Submit Match Prediction
- **Endpoint**: `POST /api/v1/predictions`
- **Request Body**:
  ```json
  {
    "matchId": "d0d6a9e1-2b3c-4d5e-6f7a-8b9c0d1e2f3a",
    "predictedHomeScore": 2,
    "predictedAwayScore": 1
  }
  ```
- **Business Rule & Validation**:
  - The backend must query the match kickoff time.
  - **Kickoff Constraint**: If `Instant.now() >= match.kickoffTime`, return `400 Bad Request` with error code `PREDICTION_LOCKED`.
- **Response (`200 OK`)**:
  ```json
  {
    "predictionId": "p1p2p3p4-q5q6-7788-9900-aabbccddeeff",
    "status": "SAVED"
  }
  ```

### B. Special Predictions (Context-Driven)
These predictions are not for matches, but for overall tournament outcomes (e.g., "Who will be the Champion?").

#### DDL:
```sql
CREATE TABLE tbl_special_predictions (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES tbl_users(id),
    league_id UUID NOT NULL,
    type VARCHAR(50) NOT NULL, -- e.g., CHAMPION, TOP_SCORER
    prediction_value VARCHAR(150) NOT NULL, -- e.g., team name or player id
    points_awarded INTEGER DEFAULT 0 NOT NULL,
    is_processed BOOLEAN DEFAULT FALSE NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT uq_user_special UNIQUE (user_id, league_id, type)
);
```

#### Submit Special Prediction
- **Endpoint**: `POST /api/v1/special-predictions`
- **Request Body**:
  ```json
  {
    "leagueId": "e7b0a8f9-4b2e-4b67-8890-a54b3d7c588e",
    "type": "CHAMPION",
    "predictionValue": "Brasil"
  }
  ```
- **Business Rule & Validation**:
  - **Tournament Kickoff Constraint**: Special predictions lock exactly at the kickoff time of the **very first match** of the tournament (e.g. World Cup Match 1). If `Instant.now() >= tournament.startTime`, block submission.

---

## 3. Ligas Menu (Groups & Sub-Leaderboards)

Manages group memberships and leaderboards split by phase.

### A. Admin Privilege: Kick Member
- **Endpoint**: `DELETE /api/v1/groups/{groupId}/members/{memberUserId}`
- **Security Check**:
  - Query `tbl_groups` to get `creator_id`.
  - Validate that the requesting user's `userId` equals `creator_id`. If not, return `403 Forbidden` with error code `NOT_GROUP_ADMIN`.

### B. Sub-Leaderboard Rankings via Redis ZSETs
To support distinct rankings screens, we maintain **three distinct Redis Sorted Set keys per group**:
- **Group Phase**: `leaderboard:group:{groupId}:group-stage`
- **Knockout Phase**: `leaderboard:group:{groupId}:knockout`
- **General/Overall**: `leaderboard:group:{groupId}:overall`

#### Fetch Leaderboard API
- **Endpoint**: `GET /api/v1/groups/{groupId}/leaderboard?phase={group-stage|knockout|overall}`
- **Implementation**:
  - The controller maps the `phase` query parameter to the corresponding Redis key.
  - Fetches the ranking from Redis using `reverseRangeWithScores` and retrieves user profile details (displayName, avatarUrl) from Firebase Firestore to map the response.
- **Response Format (`200 OK`)**:
  ```json
  {
    "groupId": "g1g2g3g4-h5h6-7788-9900-aabbccddeeff",
    "phase": "group-stage",
    "rankings": [
      {
        "rank": 1,
        "userId": "9b1deb4d-3b7d-4bad-9bdd-2b0d7b3dcb6d",
        "displayName": "John Doe",
        "avatarUrl": "https://cdn.com/avatar1.png",
        "score": 140
      }
    ]
  }
  ```

---

## 4. Perfil Menu (Preferences & Entitlements)

Separates transient user preferences and profiles from relational database details.

### A. Data Partitioning Strategy

```
                             ┌──────────────────────┐
                             │  Mobile Client App   │
                             └──────────┬───────────┘
                                        │
                 ┌──────────────────────┴──────────────────────┐
                 │                                             │
                 ▼                                             ▼
┌─────────────────────────────────┐           ┌─────────────────────────────────┐
│   Firebase Firestore            │           │  PostgreSQL (Neon)              │
│   (Read/Write Preferences)      │           │  (Transactional Operations)     │
├─────────────────────────────────┤           ├─────────────────────────────────┤
│ Path: users/{firebaseUid}       │           │ - tbl_users (UID local map)     │
│ - displayName: String           │           │ - tbl_user_entitlements (IAP)   │
│ - avatarUrl: String             │           │ - tbl_predictions               │
│ - preferences:                  │           │ - tbl_group_members             │
│   - favoriteSportId             │           └─────────────────────────────────┘
│   - favoriteLeagueIds           │
│   - notificationSettings        │
└─────────────────────────────────┘
```

- **Firebase Firestore**: Holds user-editable profile details and configurations. The mobile client writes directly to Firestore `users/{firebaseUid}` to configure options.
- **PostgreSQL**: Stores local index maps (`firebase_uid` -> `userId`) and active entitlements (`tbl_user_entitlements`) to enforce ad-free or multi-sport locks.

---

## 5. Home Menu (BFF Aggregator Dashboard)

Consolidates the start screen state with parallel non-blocking requests.

- **Endpoint**: `GET /api/v1/home/dashboard`
- **Response Format (`200 OK`)**:
  ```json
  {
    "userId": "9b1deb4d-3b7d-4bad-9bdd-2b0d7b3dcb6d",
    "points": 145,
    "rankGlobal": 234,
    "nextMatch": {
      "matchId": "d0d6a9e1-2b3c-4d5e-6f7a-8b9c0d1e2f3a",
      "homeTeam": "Brasil",
      "awayTeam": "França",
      "kickoffTime": "2026-07-10T22:30:00Z"
    },
    "myGroupsHighlight": [
      {
        "groupId": "g1g2g3g4-h5h6-7788-9900-aabbccddeeff",
        "groupName": "Família Silva",
        "userRank": 3,
        "totalMembers": 12
      }
    ],
    "news": [
      {
        "title": "Brasil se prepara para enfrentar a França na final da Copa",
        "url": "https://ge.globo.com/copa/news1.html",
        "urlToImage": "https://ge.globo.com/image1.png"
      }
    ],
    "hasUnreadNotifications": true
  }
  ```
