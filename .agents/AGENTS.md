# Diretrizes Globais para Agentes de IA (Workspace Rules)

Esta documentação serve como guia obrigatório de comportamento e tomadas de decisão para qualquer agente autônomo de IA que atue neste repositório. O objetivo é garantir consistência de design, conformidade com os ADRs e economia de tokens de contexto.

---

## 🎯 1. Fluxo de Execução Obrigatório

1. **Passo 1: Entendimento e Contexto**:
   - Antes de iniciar qualquer alteração ou análise complexa, leia o índice de decisões em **[docs/adr/README.md](file:///c:/Users/Vinicius/workspace/ligadospalpites-core/docs/adr/README.md)**.
   - Identifique quais ADRs afetam a funcionalidade planejada e consulte **apenas** os arquivos de ADR correspondentes necessários.

2. **Passo 2: Verificação de Skills do Workspace**:
   - Antes de iniciar a implementação de códigos de banco de dados, testes, logs ou segurança, consulte a pasta **[.agents/skills/](file:///c:/Users/Vinicius/workspace/ligadospalpites-core/.agents/skills/)**.
   - Se houver uma skill relevante (ex: `testes_de_integracao` ou `integracao_dados_hibrida`), você **deve** usar a ferramenta `view_file` para carregar o arquivo `SKILL.md` correspondente e seguir suas instruções exatas.

3. **Passo 3: Planejamento (Planning Mode)**:
   - Apresente um plano de implementação (`implementation_plan.md`) para o usuário e espere sua aprovação formal antes de modificar o código fonte.
   - Utilize o `task.md` como harness de progresso durante a execução.

---

## 💾 2. Diretrizes Técnicas e Tecnologias

* **Stack**: Kotlin e **Spring Boot 4.1.0** (versão estável definida no PRD). Nunca proponha bibliotecas obsoletas ou anotações depreciadas.
* **Persistência**: Neon PostgreSQL (operacional/relacional) e Upstash Redis (Sorted Sets para rankings/leaderboards e cache). O Firestore **não** deve ser usado para dados operacionais ou leaderboards.
* **Autenticação**: Firebase Auth via JWT Claims decodificados no Spring Security.
* **Notificações**: Módulo `notifications` construído sob o padrão Strategy (abstraindo múltiplos remetentes - push, in-app, email).
* **Testes**: Testes de integração devem obrigatoriamente usar **Testcontainers** com `@ServiceConnection` herdando da classe de testes compartilhada (`BaseIntegrationTest`).

---

## 🔋 3. Regras de Economia de Contexto (Tokens)

* **Leituras Cirúrgicas**: Evite ler arquivos inteiros repetidamente. Prefira usar a ferramenta `view_file` especificando `StartLine` e `EndLine` para focar nas linhas modificadas.
* **Pesquisas Eficientes**: Use a ferramenta `grep_search` para buscar palavras-chave e encontrar o local correto de alteração antes de carregar o arquivo completo.
* **Evite Poluição**: Não instale dependências redundantes e mantenha comentários limpos e focados apenas nas alterações vigentes.
