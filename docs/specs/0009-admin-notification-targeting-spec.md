# Spec-0009: Admin Notification Targeting & Dispatching Engine

This specification details the design of the **Notification Dispatching & Targeting Engine** to allow manual or automated broadcast and group notifications inside the `notifications` bounded context in **Spring Boot 4.1.0**.

---

## 1. REST API: Admin Dispatch Notification

This administrative endpoint allows triggering push and/or in-app notifications targeting specific segments of users.

- **URL**: `POST /api/v1/notifications/dispatch`
- **Headers**:
  - `Content-Type: application/json`
  - `X-Admin-Secret: <ADMIN_SECRET_KEY>` *(Proteção simples contra chamadas indevidas)*
- **Request Body**:
  ```json
  {
    "target": "USER", // USER, LEAGUE, SPORT, ALL
    "targetId": "255bdfa4-ae3c-489c-8b14-c823c5fce453", // UUID de exemplo do target (Nulo se target for ALL)
    "title": "⚽ GOOOL do Brasileirão!",
    "content": "Confira quem balançou as redes na rodada de palpites!",
    "channels": ["PUSH", "IN_APP"]
  }
  ```
- **Response (`202 Accepted`)**:
  ```json
  {
    "status": "ACCEPTED",
    "message": "Notification dispatch started in background.",
    "estimatedRecipients": 1
  }
  ```

---

## 2. Targeting Data Queries & Logic

Depending on the `target` enum value, the engine will query the database to fetch the appropriate `userId`s and then load active `fcmToken`s from `tbl_devices`:

### A. TARGET: `USER`
Fetch devices directly for the given `userId`:
```sql
SELECT fcm_token FROM tbl_devices WHERE user_id = :userId;
```

### B. TARGET: `LEAGUE` (ou GROUP)
Fetch all member `userId`s enrolled in that specific league/group:
```sql
SELECT DISTINCT user_id FROM tbl_group_members WHERE group_id = :groupId;
```

### C. TARGET: `SPORT`
Fetch all `userId`s who have placed at least one prediction in a match belonging to that specific sport:
```sql
SELECT DISTINCT p.user_id 
FROM tbl_predictions p
WHERE p.match_id IN (
    SELECT m.id FROM tbl_matches m WHERE m.sport_id = :sportId
);
```

### D. TARGET: `ALL`
Fetch all active devices from `tbl_devices` directly:
```sql
SELECT fcm_token FROM tbl_devices;
```

---

## 3. Class Design & Architecture

```
                    ┌──────────────────────────┐
                    │NotificationTestController│
                    └────────────┬─────────────┘
                                 │ Dispatches (REST Request)
                                 ▼
                    ┌──────────────────────────┐
                    │NotificationDispatcher-   │
                    │Service                   │
                    └────────────┬─────────────┘
                                 │ Retrieves Recipients
                                 ▼
                     ┌────────────────────────┐
                     │  DeviceRepository      │
                     │  GroupMemberRepository │
                     │  PredictionRepository  │
                     └───────────┬────────────┘
                                 │
                   ┌─────────────┴─────────────┐
                   │                           │
                   ▼                           ▼
       ┌──────────────────────┐    ┌──────────────────────┐
       │FcmPushNotification-  │    │InAppNotification-    │
       │Sender                │    │Sender                │
       └──────────────────────┘    └──────────────────────┘
```

### A. Targeting Domain Enums & Models
```kotlin
enum class NotificationTarget {
    USER,
    LEAGUE,
    SPORT,
    ALL
}
```

### B. Extensões nos Repositórios
Adicionar queries para suportar a resolução eficiente dos targets:

1. **`SpringDataPredictionRepository`**:
   ```kotlin
   @Query("""
       SELECT DISTINCT p.userId 
       FROM PredictionJpaEntity p 
       WHERE p.matchId IN (
           SELECT m.id FROM MatchJpaEntity m WHERE m.sportId = :sportId
       )
   """)
   fun findUserIdsBySportId(@Param("sportId") sportId: UUID): List<UUID>
   ```

2. **`SpringDataGroupMemberRepository`**:
   ```kotlin
   @Query("SELECT DISTINCT g.userId FROM GroupMemberJpaEntity g WHERE g.groupId = :groupId")
   fun findUserIdsByGroupId(@Param("groupId") groupId: UUID): List<UUID>
   ```

3. **`SpringDataDeviceRepository`**:
   ```kotlin
   fun findAllByUserId(userId: UUID): List<DeviceJpaEntity>
   fun findByUserIdIn(userIds: List<UUID>): List<DeviceJpaEntity>
   ```

4. **`DeviceRepository`**:
   ```kotlin
   fun findAllByUserId(userId: UUID): List<Device>
   fun findAllByUserIds(userIds: List<UUID>): List<Device>
   fun findAll(): List<Device>
   ```

---

## 4. Verification Plan

### Automated Tests
- `NotificationTestIntegrationTest.kt` inside `src/test/kotlin/com/ligadospalpites/notifications/`:
  - Test registration of active devices for multiple users.
  - Test sending to a single user (`USER`).
  - Test sending to a group/league (`LEAGUE`).
  - Test sending to a sport (`SPORT`).
  - Test sending broadcast (`ALL`).
  - Test unauthorized call when `X-Admin-Secret` header is missing or incorrect.
