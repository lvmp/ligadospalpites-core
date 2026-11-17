# Domain Rules: Liga dos Palpites (Invariantes e Regras de Negócio)

Este documento reúne as regras canônicas de domínio da aplicação. Consulte este arquivo sob demanda sempre que criar ou alterar Casos de Uso, Entidades, Agregados ou Regras de Pontuação.

---

## 1. Identidade e Agregados (DDD & Clean Architecture)
- **Referência Estrita por ID**: Entidades e Raízes de Agregado não contêm referências diretas a outros agregados; usam Value Objects de ID (ex: `UserId(UUID)`, `LeagueId(UUID)`, `MatchId(UUID)`).
- **Sem Anotações de Framework no Domínio**: Entidades e VOs de domínio são classes puras Kotlin (`data class`, `@JvmInline value class`), sem acoplamento com JPA (`@Entity`, `@ManyToOne`), Spring ou Jackson.
- **Imutabilidade e Auto-Validação**: Value Objects são imutáveis e validam suas regras de integridade diretamente no bloco `init` (lançando exceções de domínio tipadas se inválidos).
- **Tratamento de Erros de Domínio**: Erros de negócio devem ser modelados explicitamente como exceções de domínio ou tipos de resultado (`Result<T>`), nunca vazando exceções genéricas de infraestrutura.

---

## 2. Regras de Palpites & Torneios
- **Bloqueio Temporal Estrito (Locking)**: O palpite para uma partida é trancado rigorosamente no momento do pontapé inicial (`match.startTime <= Instant.now()`). Nenhuma inclusão, alteração ou exclusão de palpite é permitida após este marco.
- **Score Imutável após Liquidação**: Pontuações calculadas para palpites tornam-se somente-leitura após a finalização da partida e liquidação reativa do evento esportivo.
- **Polimorfismo de Esportes**: Cada esporte implementa sua própria estratégia de pontuação, fases e liquidação via Strategy Pattern (`SportsDataSyncEngine`), sem ramificações condicionais (`if/else`) nos serviços centrais.
- **Consistência de Rodadas**: Partidas pertencem a rodadas ou fases identificadas de forma polimórfica (ex: fase de grupos, oitavas, quartas, temporada regular, playoffs).

---

## 3. Segregação de Dados & Armazenamento Híbrido
- **PostgreSQL (Supabase)**: Fonte da verdade relacional para dados operacionais (usuários, palpites, histórico transacional, torneios, partidas e auditoria). Alterações de schema exigem migrations no Flyway.
- **Redis (Upstash)**: Exclusivo para rankings/leaderboards em tempo real utilizando Sorted Sets (`ZSET`), controle de rate-limiting e cache de respostas agregadas de alta leitura (BFF Dashboard).
- **Firebase**: Utilizado estritamente para autenticação (validação de JWT Claims no Spring Security), push notifications (Firebase Cloud Messaging - FCM) e mídias estáticas (Storage). **Proibido gravar entidades de negócio operacionais no Firestore**.

---

## 4. Direitos de Acesso e Monetização (Entitlements)
- **Controle de Acesso por Entitlement**: A liberação de ligas, esportes premium ou recursos avançados é governada pelo agregado `UserEntitlement`.
- **Validação de Assinaturas e Webhooks**: Compras in-app são processadas via RevenueCat. A concessão ou cancelamento de direitos ocorre exclusivamente via processamento idempotente de Webhooks assinados ou concessões manuais auditadas.
- **Regra de Degradação Graciosa**: Se um plano expirar, o usuário mantém acesso histórico aos seus palpites passados, mas perde a permissão de submeter novos palpites para esportes/recursos restritos.
