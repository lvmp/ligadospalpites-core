---
name: notificacoes_e_push_fcm
description: Diretrizes e boas práticas para gerenciamento de notificações In-App, Pushes via FCM com payload data para roteamento no Flutter e ciclo de vida de tokens de dispositivos.
---

# Notificações Push FCM e Feed In-App

Esta skill orienta o agente na implementação e manutenção do módulo de notificações (`notifications`) no repositório **Liga dos Palpites**, garantindo conformidade com as ADRs 0004, 0010 e 0011.

---

## 🎯 1. Arquitetura Polymorphic Strategy (Canais de Envio)

O módulo de notificações abstrai a entrega de mensagens através da interface `NotificationSender` (padrão Strategy):

```kotlin
interface NotificationSender {
    fun supports(channel: NotificationChannel): Boolean
    fun send(notification: Notification, recipient: RecipientContactInfo)
}
```

### Canais Disponíveis (`NotificationChannel`):
- **`PUSH`**: Enviado via Firebase Cloud Messaging (`FcmPushNotificationSender`) para os tokens ativos em `tbl_devices`.
- **`IN_APP`**: Persistido no banco de dados relacional (`tbl_in_app_notifications` via `InAppNotificationSender`) para consulta no feed do aplicativo.
- **`EMAIL`**: Enviado via SMTP (`SmtpEmailNotificationSender`).

---

## 📌 2. Regras de Persistência no Feed vs Alertas Instantâneos

Nem toda notificação deve ser gravada no histórico in-app do usuário. Siga estritamente esta divisão:

| Categoria da Notificação | Canais (`channels`) | Gravada no Feed (`tbl_in_app_notifications`)? |
| :--- | :--- | :--- |
| **Agenda do Dia** (`DispatchDailyAgendaPushUseCase`) | `PUSH` + `IN_APP` | **SIM (Longo Prazo)** |
| **Comunicados Admin & Sistema** (`DispatchAdminNotificationUseCase`) | `PUSH` + `IN_APP` | **SIM (Longo Prazo)** |
| **Eventos de Partida em Tempo Real** (Início, Intervalo, Gol) | `PUSH` | **NÃO** |
| **Apuração de Palpites & Pontuação** (`PredictionsProcessed`) | `PUSH` | **NÃO** |

---

## 📱 3. Payload de Roteamento FCM (`data`) para o Flutter

Todas as notificações Push enviadas via `FcmPushNotificationSender` **devem incluir o mapa `data`** no payload do Firebase Cloud Messaging para permitir que o aplicativo móvel Flutter navegue automaticamente para a tela correta ao ser clicado pelo usuário:

```kotlin
val dataMap = mutableMapOf<String, String>()
dataMap.putAll(notification.metadata)
if (!dataMap.containsKey("click_action")) {
    dataMap["click_action"] = "FLUTTER_NOTIFICATION_CLICK"
}
```

### Tabela de Metadados Obrigatórios:

- **Agenda do Dia / Comunicados**:
  `metadata = mapOf("type" to "daily_agenda")` ou `mapOf("type" to "announcement")` -> Navega para `/notifications`.
- **Eventos de Partida (Gols, Início, Intervalo)**:
  `metadata = mapOf("type" to "match_update", "matchId" to event.matchId.toString())` -> Navega para `/games?matchId=...`.
- **Lembrete de Palpites Pendentes**:
  `metadata = mapOf("type" to "pending_prediction", "matchId" to matchId.toString())` -> Navega para `/predictions?matchId=...`.
- **Atualizações de Ranking ou Grupo**:
  `metadata = mapOf("type" to "ranking_update", "leagueId" to leagueId.toString())` -> Navega para `/groups/{groupId}`.

> ⚠️ **Importante**: Todos os valores inseridos dentro do mapa `metadata` **devem obrigatoriamente ser de tipo String** (ex: `"matchId" to "123"`).

---

## 🔄 4. Ciclo de Vida e Auto-Limpeza de Tokens FCM

1. **Registro e Atualização (`POST /api/v1/notifications/devices`)**:
   - A aplicação móvel envia o `fcmToken` atual e o `deviceId`.
   - Se o token já pertencia a outro usuário, a associação antiga é removida e transferida para o novo usuário.
2. **Auto-Limpeza (Self-Cleaning)**:
   - Se o Firebase retornar erro `UNREGISTERED` ou `INVALID_ARGUMENT` durante o envio de um Push, o `FcmPushNotificationSender` dispara o evento `DeviceTokenExpiredEvent(fcmToken)`.
   - O listener de expiração remove imediatamente o token inválido de `tbl_devices` para evitar desperdício de chamadas futuras.

---

## 🌐 5. Endpoints REST do Painel de Notificações In-App

- **`GET /api/v1/notifications?page=0&size=20`**: Retorna as notificações In-App ordenadas por `createdAt DESC` e a contagem `unreadCount`.
- **`PATCH /api/v1/notifications/{id}/read`**: Marca notificação individual como lida.
- **`POST /api/v1/notifications/read-all`**: Marca todas as notificações do usuário como lidas.
