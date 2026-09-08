---
name: seguranca_e_autenticacao_firebase
description: Diretrizes de segurança no Spring Security, resolução de contexto de usuário (UserResolver), validação de claims JWT do Firebase e migração lazy.
---

# 🔒 Segurança e Autenticação com Firebase Auth

Esta skill estabelece os requisitos e padrões para autenticação, autorização e gerenciamento de contexto de usuário na aplicação.

---

## 🔑 1. Autenticação e Spring Security

- **JWT do Firebase**: As requisições HTTP autenticadas enviam o Firebase ID Token no cabeçalho `Authorization: Bearer <token>`.
- **Spring Security Filter**: O token é decodificado e validado por um filtro customizado (`FirebaseAuthenticationFilter`), que injeta o objeto de autenticação no `SecurityContextHolder`.
- **Claims Personalizados**: Roles e privilégios (ex: `admin`, `premium`) são lidos das JWT claims ou resolvidos via serviço interno.

---

## 👤 2. Injeção de Usuário (`UserResolver` / Custom Annotation)

- **UserResolver**: Para evitar chamadas repetitivas e manuais ao `SecurityContextHolder` dentro dos Controllers, utilize anotações customizadas (ex: `@CurrentUser` ou `UserResolver`) resolvidas via `HandlerMethodArgumentResolver` do Spring Web.
- **Lazy Migration**: Ao autenticar usuários novos vindo do Firebase Auth, a persistência no banco Supabase (PostgreSQL) deve ocorrer de forma transparente/lazy na primeira interação que exigir registro local.

---

## 🔐 3. Gestão de Segredos e Chaves de Admin

- **Secrets**: Chaves de serviço (Firebase Admin SDK Service Account JSON, RevenueCat Webhook Keys, Supabase Service Keys) devem ser fornecidas via variáveis de ambiente/Spring Application Properties.
- **Endpoints Administrativos**: Endpoints com permissões privilegiadas (ex: conceder cortesia manual, forçar syncs de partidas) devem estar explicitamente protegidos com `@PreAuthorize("hasRole('ADMIN')")` ou rotas restritas no Spring Security.
