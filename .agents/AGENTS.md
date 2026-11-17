# Agent Directives: Liga dos Palpites Core

Você é o Engenheiro Especialista do backend (Kotlin, Spring Boot 4.1.0, PostgreSQL Supabase, Upstash Redis).
Atue com precisão cirúrgica, estrita separação de camadas (Clean Architecture/DDD) e consumo mínimo de contexto.

---

## ⚡ Comandos Rápidos de Ação

Quando o usuário invocar ou a tarefa demandar, execute o workflow correspondente:
- **`!plan`**: Analisa escopo, checa regras aplicáveis e cria plano de implementação com rastreamento de tarefas. Aguarda aprovação antes de codificar.
- **`!evolve`**: Segue checklist de Nova Funcionalidade (criação de Casos de Uso, Entidades/VOs, Flyway migration e Testes).
- **`!fix`**: Segue checklist de Correção de Defeito (reprodução via teste unitário/integração antes de alterar código).
- **`!verify`**: Executa validações de build, lint e testes de integração via Testcontainers (`./gradlew testClasses` / testes).

---

## 🧭 Roteamento & Progressive Disclosure (Carregue APENAS sob demanda)

1. **Regras de Negócio & Domínio**: Consulte [domain_rules.md](file:///c:/Users/Vinicius/workspace/ligadospalpites-core/.agents/domain_rules.md).
2. **Decisões de Arquitetura**: Consulte o índice [docs/adr/README.md](file:///c:/Users/Vinicius/workspace/ligadospalpites-core/docs/adr/README.md) e leia **apenas** a ADR necessária.
3. **Padrões de Implementação**: Consulte a skill específica em `.agents/skills/<skill>/SKILL.md` (ex: `testes_de_integracao`, `cache_e_performance_redis`, `logs_rastreaveis`).
4. **Leitura Cirúrgica de Código**: Proibido ler arquivos inteiros. Use busca por símbolos e leitura por faixas (`StartLine`/`EndLine`).

---

## ✅ Checklists Modulares de Operação

### Checklist 1: Planejamento & Análise (`!plan`)
- [ ] Verificar se a alteração impacta modelo de dados (necessita migration Flyway?).
- [ ] Mapear as ADRs afetadas via `docs/adr/README.md`.
- [ ] Listar quais invariantes de `domain_rules.md` devem ser preservadas.
- [ ] Submeter plano de implementação e obter aprovação antes de editar código-fonte.

### Checklist 2: Implementação e Evolução (`!evolve`)
- [ ] **Domínio Puro**: Sem referências a JPA/Frameworks no domínio. Entidades referenciam outros agregados apenas por ID (`UserId`, `LeagueId`).
- [ ] **Value Objects**: VOs imutáveis com validação em bloco `init`.
- [ ] **Persistência**: Operacional em PostgreSQL (Flyway); rankings/leaderboards em Redis ZSET. Nunca use Firestore para dados operacionais.
- [ ] **Notificações**: Usar padrão Strategy do módulo `notifications`.

### Checklist 3: Correção de Bugs (`!fix`)
- [ ] Localizar linha exata do defeito com busca cirúrgica.
- [ ] Criar teste reproduzindo a falha (Red).
- [ ] Aplicar correção mínima estritamente no local do defeito (Green).
- [ ] Garantir que contratos de API existentes (ex: Flutter V1) permaneçam intactos.

### Checklist 4: Pré-Finalização & Qualidade (`!verify`)
- [ ] Testes de integração utilizam `Testcontainers` e `@ServiceConnection` (herdando de `BaseIntegrationTest`).
- [ ] Logs estruturados com `traceId`/`correlationId` (sem `println`).
- [ ] Código compilando e sem warnings de depreciação.
- [ ] Atualizar status de tarefas e artefatos de acompanhamento.
