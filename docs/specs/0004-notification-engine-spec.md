# Spec-0004: Extensible Notification Engine

This specification details the REST API endpoint, database schemas, polymorphic strategy classes, and self-cleaning lifecycle mechanisms for the **Notification Module** (`notifications`) in **Spring Boot 4.1.0**.

---

## 1. REST Endpoint: Device Token Registration

The mobile application registers or updates its FCM token by sending a payload to this endpoint upon authentication or startup.

- **URL**: `POST /api/v1/notifications/devices`
- **Headers**:
  - `Authorization: Bearer <Firebase_ID_Token>`
  - `Content-Type: application/json`
- **Request Body**:
  ```json
  {
    "deviceId": "3a7b649d-213e-4562-b3fc-2c963f66afa6",
    "fcmToken": "fcm_token_string_from_firebase_sdk_here",
    "deviceType": "ANDROID"
  }
  ```
  *Note: `deviceType` must be either `ANDROID` or `IOS`.*
- **Response (`200 OK`)**:
  ```json
  {
    "status": "SUCCESS",
    "message": "Device token registered successfully."
  }
  ```

---

## 2. Notification Persistence Schema (PostgreSQL DDL)

We persist local device records and the in-app notification center feed:

```sql
-- Context: notifications (device links)
CREATE TABLE tbl_devices (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES tbl_users(id) ON DELETE CASCADE,
    device_id UUID NOT NULL UNIQUE,
    fcm_token VARCHAR(255) NOT NULL,
    device_type VARCHAR(20) NOT NULL, -- ANDROID, IOS
    registered_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL
);
CREATE INDEX idx_devices_user ON tbl_devices(user_id);
CREATE INDEX idx_devices_fcm_token ON tbl_devices(fcm_token);

-- Context: notifications (in-app feeds)
CREATE TABLE tbl_in_app_notifications (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES tbl_users(id) ON DELETE CASCADE,
    title VARCHAR(200) NOT NULL,
    content TEXT NOT NULL,
    is_read BOOLEAN DEFAULT FALSE NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL
);
CREATE INDEX idx_in_app_notifications_user_unread ON tbl_in_app_notifications(user_id, is_read) WHERE is_read = FALSE;
```

---

## 3. Polymorphic Sending (Strategy Pattern)

The `notifications` module decouples delivery. Spring Boot will automatically inject all implementers of `NotificationSender` into a list to route messages dynamically.

```kotlin
package com.ligadospalpites.feature.notifications.domain.ports

import com.ligadospalpites.feature.notifications.domain.models.Notification
import com.ligadospalpites.feature.notifications.domain.models.NotificationChannel
import com.ligadospalpites.feature.notifications.domain.models.RecipientContactInfo

interface NotificationSender {
    fun supports(channel: NotificationChannel): Boolean
    fun send(notification: Notification, recipient: RecipientContactInfo)
}
```

### Implementing Adapters

#### A. In-App Sender:
```kotlin
@Component
class InAppNotificationSender(
    private val inAppRepository: InAppNotificationRepository
) : NotificationSender {
    override fun supports(channel: NotificationChannel) = channel == NotificationChannel.IN_APP

    override fun send(notification: Notification, recipient: RecipientContactInfo) {
        inAppRepository.save(
            InAppNotificationEntity(
                id = UUID.randomUUID(),
                userId = notification.recipientUserId.value,
                title = notification.title,
                content = notification.content
            )
        )
    }
}
```

#### B. FCM Push Sender:
```kotlin
@Component
class FcmPushNotificationSender(
    private val firebaseMessaging: FirebaseMessaging, // Autowired Firebase SDK
    private val eventPublisher: ApplicationEventPublisher
) : NotificationSender {
    override fun supports(channel: NotificationChannel) = channel == NotificationChannel.PUSH

    override fun send(notification: Notification, recipient: RecipientContactInfo) {
        recipient.activeFcmTokens.forEach { token ->
            try {
                val message = Message.builder()
                    .setToken(token)
                    .setNotification(
                        com.google.firebase.messaging.Notification.builder()
                            .setTitle(notification.title)
                            .setBody(notification.content)
                            .build()
                    )
                    .build()
                firebaseMessaging.send(message)
            } catch (ex: FirebaseMessagingException) {
                // If token is expired or unregistered, trigger cleanup event
                if (ex.messagingErrorCode == MessagingErrorCode.UNREGISTERED || 
                    ex.messagingErrorCode == MessagingErrorCode.INVALID_ARGUMENT) {
                    eventPublisher.publishEvent(DeviceTokenExpiredEvent(token))
                }
            }
        }
    }
}
```

#### C. Email Sender:
```kotlin
@Component
class SmtpEmailNotificationSender(
    private val mailSender: JavaMailSender
) : NotificationSender {
    override fun supports(channel: NotificationChannel) = channel == NotificationChannel.EMAIL

    override fun send(notification: Notification, recipient: RecipientContactInfo) {
        if (recipient.email.isNullOrBlank()) return
        
        val message = SimpleMailMessage().apply {
            setTo(recipient.email)
            setSubject(notification.title)
            setText(notification.content)
        }
        mailSender.send(message)
    }
}
```

---

## 4. Self-Cleaning & Device Maintenance Logic

### A. Device Registration & Ownership Transfer (`POST` Handler)
When a registration payload arrives:
1. Lookup if `fcmToken` is already registered to a different `userId` (ownership transfer scenario).
2. If found, delete the old database entry to prevent sending predictions notifications to a logged-out user's token.
3. Upsert the token for the active requesting user.

```kotlin
@Transactional
fun registerDevice(userId: UUID, request: RegisterDeviceRequest) {
    // 1. Ownership Transfer check
    deviceRepository.findByFcmToken(request.fcmToken)?.let { existing ->
        if (existing.userId != userId) {
            deviceRepository.delete(existing)
        }
    }

    // 2. Upsert token linked to user
    val device = deviceRepository.findByDeviceId(request.deviceId)
        ?.copy(fcmToken = request.fcmToken, userId = userId, registeredAt = Instant.now())
        ?: DeviceEntity(
            id = UUID.randomUUID(),
            userId = userId,
            deviceId = request.deviceId,
            fcmToken = request.fcmToken,
            deviceType = request.deviceType
        )
    deviceRepository.save(device)
}
```

### B. Expired Token Pruning (Cleanup Observer)
Triggered by failure webhooks or SDK exception listeners asynchronously:

```kotlin
data class DeviceTokenExpiredEvent(val fcmToken: String)
```

```kotlin
@Component
class DeviceTokenCleanupObserver(private val deviceRepository: DeviceRepository) {

    @Async
    @EventListener
    @Transactional
    fun handleExpiredToken(event: DeviceTokenExpiredEvent) {
        deviceRepository.deleteByFcmToken(event.fcmToken)
    }
}
```
