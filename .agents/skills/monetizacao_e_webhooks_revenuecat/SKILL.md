---
name: monetizacao_e_webhooks_revenuecat
description: Diretrizes para integração de monetização, processamento de webhooks do RevenueCat e concessão de cortesia / passes temporários manuais no workspace.
---

# 💳 Monetização, RevenueCat Webhooks e Cortesia Manual

Esta skill define a arquitetura e as regras para gestão de assinaturas, planos premium e concessão manual de acessos (cortesia) no sistema.

---

## 🏗️ 1. Modelo de Assinatura e Integração

- **Gateway de Pagamento App Store / Google Play**: Gerenciado prioritariamente via **RevenueCat**.
- **Webhooks**: O backend escuta os eventos enviados pelo RevenueCat para sincronizar o status de assinatura do usuário no banco de dados local.
- **Entitlements**: O status premium é representado no domínio por entitlements/recursos ativos (ex: `PREMIUM_VIP`, `NO_ADS`, `UNLIMITED_BOLAO`).

---

## 🎟️ 2. Regra de Cortesia (VIP Pass Manual)

Além do fluxo automático via RevenueCat, o sistema permite a concessão manual de **Cortesia** (Passe Temporário / VIP Pass) através de operações administrativas no workspace.

### Principais Regras da Cortesia:
1. **Sobrescrita de Assinatura**: Quando um usuário possui um passe de cortesia válido e ativo (`cortesia_expiration > NOW()`), seu acesso aos recursos premium deve ser concedido **independentemente** do status no RevenueCat.
2. **Prioridade na Verificação**:
   - `isUserPremium(userId)` deve verificar:
     1. Existe registro de cortesia ativo e não expirado? $\rightarrow$ **Liberar acesso**.
     2. Caso não haja cortesia válida, verificar assinatura ativa via RevenueCat / Supabase status.
3. **Auditoria e Validade**:
   - Toda concessão de cortesia deve armazenar o ID do administrador concedente, motivo, data de concessão e data de expiração.
   - Quando a data de expiração da cortesia passar, o sistema deve automaticamente desconsiderá-la e voltar a checar a assinatura oficial do RevenueCat.

---

## ⚡ 3. Tratamento de Webhooks do RevenueCat

- **Idempotência**: Processar webhooks garantindo idempotência com base no `event_id` recebido da API do RevenueCat.
- **Autenticação de Webhook**: Validar a chave de autorização no cabeçalho HTTP (`Authorization: Bearer <REVENUECAT_WEBHOOK_SECRET>`).
- **Eventos Principais**:
  - `INITIAL_PURCHASE` / `RENEWAL`: Ativar status premium.
  - `CANCELLATION` / `EXPIRATION`: Desativar status premium (apenas se não houver cortesia manual ativa).
  - `PRODUCT_CHANGE`: Atualizar plano/tier do usuário.

---

## 🛡️ 4. Boas Práticas e Testes

- **Testes de Integração**: Testar o webhook do RevenueCat usando `@ServiceConnection` / Testcontainers e verificar se eventos de cortesia sobrescrevem corretamente acessos expirados ou inexistentes no RevenueCat.
- **Segurança**: Nunca expor endpoints de atribuição de cortesia sem autorização adequada de Admin no Spring Security.
