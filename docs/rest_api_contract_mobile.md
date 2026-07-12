# Contrato de Integração API REST - Frontend Mobile Flutter

Este documento serve como guia oficial de integração entre o aplicativo móvel Flutter (`ligadospalpites`) e o backend modularizado (`ligadospalpites-core`).

---

## 🔑 Autenticação e Contexto do Usuário

O backend é preparado para autenticação stateless. 
* Em ambiente de **Produção**: O app deve enviar o JWT decodificado do Firebase no cabeçalho HTTP `Authorization: Bearer <JWT_TOKEN>`.
* Em ambiente de **Desenvolvimento**: Para facilitar testes locais e emuladores, os endpoints aceitam e resolvem o usuário através do cabeçalho **`X-User-Id`** com o UUID correspondente.

> **Importante**: Caso nenhum cabeçalho de autenticação seja enviado, o backend assumirá o usuário mock padrão: `9b1deb4d-3b7d-4bad-9bdd-2b0d7b3dcb6d` para fins de simulação rápida.

---

## 📊 1. BFF - Home Dashboard Aggregator

Este endpoint consolida de maneira paralela e concorrente todas as informações essenciais para alimentar a tela inicial do aplicativo em uma única chamada.

### `GET /api/v1/home/dashboard`

* **Headers Recomendados**: 
  - `X-User-Id: <UUID_DO_USUARIO>`
* **Resposta Esperada (`200 OK`)**:
```json
{
  "userId": "9b1deb4d-3b7d-4bad-9bdd-2b0d7b3dcb6d",
  "points": 120,
  "rankGlobal": 14,
  "hasUnreadNotifications": true,
  "nextMatch": {
    "matchId": "a1b2c3d4-e5f6-7a8b-9c0d-1e2f3a4b5c6d",
    "homeTeam": "Brasil",
    "awayTeam": "França",
    "kickoffTime": "2026-07-13T19:00:00Z"
  },
  "myGroupsHighlight": [
    {
      "groupId": "7b0a8f9-4b2e-4b67-8890-a54b3d7c588e",
      "groupName": "Bolão da Firma",
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
  ]
}
```

---

## ⚽ 2. Módulo `sportsfeed` (Ligas, Jogos e Chaves)

### `GET /api/v1/sports/leagues`
Retorna todas as ligas cadastradas agrupadas por Esporte. Útil para preencher as abas (Tabs) de segmentação visual na interface.

* **Resposta Esperada (`200 OK`)**:
```json
[
  {
    "sportId": "f3b3b44b-6f81-42cb-b1b7-d1a1005a8f4c",
    "sportName": "Futebol",
    "leagues": [
      {
        "leagueId": "e7b0a8f9-4b2e-4b67-8890-a54b3d7c588e",
        "name": "Copa do Mundo",
        "isActive": true
      }
    ]
  }
]
```

### `GET /api/v1/sports/fixtures`
Retorna o calendário de jogos. Pode ser filtrado por `leagueId` ou `sportId`.

* **Parâmetros Query**:
  - `leagueId` (opcional)
  - `sportId` (opcional)
* **Headers Recomendados**:
  - `X-User-Id: <UUID>` (Se o esporte for Premium, o backend validará se o usuário possui a assinatura `MULTI_SPORT` ativa).
* **Resposta Esperada (`200 OK`)**:
```json
[
  {
    "id": "e7b0a8f9-4b2e-4b67-8890-a54b3d7c588e",
    "homeTeamName": "Brasil",
    "awayTeamName": "França",
    "homeScore": null,
    "awayScore": null,
    "kickoffTime": "2026-07-13T19:00:00Z",
    "status": "SCHEDULED",
    "isFinalMatch": true
  }
]
```
* **Erros Possíveis**:
  - `403 Forbidden` (`{"error": "SPORT_LOCKED"}`) -> O esporte exige assinatura do pacote complementar.

### `GET /api/v1/sports/brackets?leagueId=<UUID>`
Retorna o chaveamento de fase mata-mata organizado por rodadas para alimentar o `BracketBloc` no Flutter.

* **Resposta Esperada (`200 OK`)**:
```json
{
  "leagueId": "e7b0a8f9-4b2e-4b67-8890-a54b3d7c588e",
  "rounds": {
    "OITAVAS": [
      {
        "matchId": "d1a1005a-42cb-b1b7-6f81-f3b3b44bf3b3",
        "homeTeam": "Holanda",
        "awayTeam": "Espanha",
        "kickoffTime": "2026-07-14T14:00:00Z",
        "status": "SCHEDULED"
      }
    ],
    "QUARTAS": [],
    "SEMI": [],
    "FINAL": [
      {
        "matchId": "a1b2c3d4-e5f6-7a8b-9c0d-1e2f3a4b5c6d",
        "homeTeam": "Brasil",
        "awayTeam": "França",
        "kickoffTime": "2026-07-13T19:00:00Z",
        "status": "SCHEDULED"
      }
    ]
  }
}
```

---

## 🔮 3. Módulo `predictions` (Palpites)

### `POST /api/v1/predictions`
Registra ou atualiza um palpite de placar para uma determinada partida.

* **Payload (JSON)**:
```json
{
  "matchId": "a1b2c3d4-e5f6-7a8b-9c0d-1e2f3a4b5c6d",
  "predictedHomeScore": 2,
  "predictedAwayScore": 1
}
```
* **Resposta Esperada (`200 OK`)**:
```json
{
  "status": "SUCCESS",
  "message": "Palpite registrado com sucesso!"
}
```
* **Erros Possíveis**:
  - `400 Bad Request` (`{"error": "PREDICTION_LOCKED"}`) -> Tentativa de palpite realizada após o horário de `kickoffTime` da partida.

### `POST /api/v1/special-predictions`
Registra palpites especiais/longo prazo para ligas inteiras (Ex: Campeão, Artilheiro).

* **Payload (JSON)**:
```json
{
  "leagueId": "e7b0a8f9-4b2e-4b67-8890-a54b3d7c588e",
  "type": "CHAMPION", 
  "predictionValue": "Brasil"
}
```
* **Resposta Esperada (`200 OK`)**:
```json
{
  "status": "SUCCESS",
  "message": "Palpite especial de CHAMPION registrado!"
}
```
* **Erros Possíveis**:
  - `400 Bad Request` (`{"error": "SPECIAL_PREDICTION_LOCKED"}`) -> Trancado porque a primeira partida do torneio já se iniciou.

---

## 👥 4. Módulo `groups` (Grupos e Ligas Privadas)

### `GET /api/v1/groups/{groupId}/leaderboard`
Obtém o ranking dos membros do grupo de palpites, segmentado opcionalmente por período ou fase da competição.

* **Parâmetros Query**:
  - `phase`: `overall` (padrão), `group-stage` (fase de grupos), `knockout` (mata-mata).
* **Resposta Esperada (`200 OK`)**:
```json
[
  {
    "position": 1,
    "userId": "9b1deb4d-3b7d-4bad-9bdd-2b0d7b3dcb6d",
    "displayName": "Você",
    "avatarUrl": "https://api.dicebear.com/7.x/bottts/svg?seed=9b1deb4d-3b7d-4bad-9bdd-2b0d7b3dcb6d",
    "score": 250
  },
  {
    "position": 2,
    "userId": "c0a8f9e7-4b2e-4b67-8890-a54b3d7c588e",
    "displayName": "Usuário c0a8f",
    "avatarUrl": "https://api.dicebear.com/7.x/bottts/svg?seed=c0a8f9e7-4b2e-4b67-8890-a54b3d7c588e",
    "score": 180
  }
]
```

### `DELETE /api/v1/groups/{groupId}/members/{memberUserId}`
Permite que o criador de um grupo remova/expulse um participante indesejado.

* **Headers Recomendados**:
  - `X-User-Id: <UUID_DO_CRIADOR>` *(O backend valida se quem está chamando é o criador do grupo).*
* **Resposta Esperada (`200 OK`)**:
```json
{
  "status": "SUCCESS",
  "message": "Membro removido com sucesso de todas as tabelas e leaderboards."
}
```
* **Erros Possíveis**:
  - `403 Forbidden` (`{"error": "NOT_GROUP_ADMIN"}`) -> Caso o requisitante não seja o criador original do grupo.

---

## 🔔 5. Módulo `notifications` (Mensageria e Multi-Canais)

Para que o backend possa enviar e-mails, SMS, ou Push Notifications baseadas em eventos de pontuação dos palpites, o aplicativo Flutter deve registrar o token do dispositivo e as preferências de notificação do usuário assim que o aplicativo inicia ou o usuário faz login.

### `POST /api/v1/devices/register`
Registra ou transfere a propriedade do token FCM do usuário logado.

* **Payload (JSON)**:
```json
{
  "deviceId": "uuid-unico-do-aparelho-mobile",
  "fcmToken": "fcm-token-gerado-pelo-firebase-messaging",
  "deviceType": "ANDROID", 
  "receiveEmail": true,
  "receiveSms": false,
  "receivePush": true
}
```
> **Nota**: 
> * **`deviceType`** assume as opções: `ANDROID`, `IOS`, `WEB`.
> * **`receiveEmail`**, **`receiveSms`** e **`receivePush`** definem os canais preferidos que o backend usará na estratégia de envio reativo polimórfico de pontuação e ranking do palpiteiro.

* **Resposta Esperada (`200 OK`)**:
```json
{
  "status": "SUCCESS",
  "message": "Dispositivo e canais de comunicação registrados com sucesso!"
}
```
