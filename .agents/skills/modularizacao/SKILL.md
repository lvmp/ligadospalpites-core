---
name: modularizacao
description: Diretrizes e boas práticas para estruturar monólitos modularizados, facilitando a migração futura para microsserviços.
---

# Modularização de Monólitos para Evolução Futura (Kotlin & Spring Boot)

Esta skill orienta o agente na estruturação de sistemas sob a arquitetura de **Monólito Modular** (Modular Monolith) utilizando **Kotlin** e **Spring Boot**, seguindo boas práticas de design de software que minimizam o acoplamento e pavimentam o caminho para uma transição suave para **Microsserviços** no futuro.

---

## 💡 Princípios Fundamentais

Para que um monólito possa ser facilmente decomposto em microsserviços, os módulos devem se comportar como se já fossem serviços independentes, embora rodem no mesmo processo/repositório.

### 1. Contextos Delimitados (Bounded Contexts)
- Cada módulo deve corresponder a um domínio de negócio bem definido (Domain-Driven Design).
- Evite módulos genéricos (ex: `helpers`, `utils` gigantes). Prefira módulos focados em capacidades de negócio (ex: `predictions`, `groups`, `users`, `billing`).

### 2. Autonomia de Dados (Sem Compartilhamento de Banco de Dados)
- **Regra de Ouro:** Um módulo não deve ler ou escrever diretamente nas tabelas/dados de outro módulo.
- Se o Módulo A precisa de dados do Módulo B, ele deve solicitá-los via API (chamada de método/função em memória) ou reagir a eventos emitidos pelo Módulo B.
- No banco de dados, garanta a separação lógica (schemas diferentes, prefixos de tabelas ou mesmo bancos separados se viável).

### 3. Comunicação via Contratos (Interfaces Públicas)
- Cada módulo deve expor apenas uma **API Pública** ou **Fachada (Facade)** e esconder seus detalhes de implementação (classes internas, JPA entities, infraestrutura, etc.).
- Outros módulos interagem exclusivamente por meio de interfaces/DTOs públicos.
- Em Kotlin, aproveite o modificador de visibilidade `internal` para ocultar classes que não devem ser expostas fora do módulo.
- A adoção do **Spring Modulith** ajuda a forçar essa visibilidade verificando e validando as dependências de pacotes em tempo de build/teste.

### 4. Comunicação Assíncrona e Eventos (Loose Coupling)
- Utilize comunicação baseada em eventos (Event-Driven) para operações que não exigem consistência imediata.
- No Spring Boot, publique eventos da aplicação usando o `ApplicationEventPublisher`. Módulos interessados escutam usando `@EventListener` ou `@TransactionalEventListener`.
- Isso elimina a dependência temporal e direta entre os módulos, tornando a extração para um microsserviço (ex: via Kafka/RabbitMQ) extremamente simples no futuro.

### 5. Inversão de Dependência (Dependency Inversion)
- Módulos de domínio não devem depender de detalhes de infraestrutura (banco de dados, chamadas HTTP, SDKs externos).
- Defina interfaces (Ports) no domínio e implemente-as na camada de infraestrutura (Adapters), injetando-as via injeção de dependência do Spring.

### 6. Configuração Independente
- Evite arquivos `application.yml` monolíticos e confusos.
- Cada módulo deve declarar e gerenciar suas próprias propriedades e beans de configuração (usando `@Configuration` locais do Spring).

---

## 🛠️ Estruturas de Diretórios Recomendadas

### Opção A: Gradle Multi-project (Mais Recomendada para Migração Física)

Fisicamente separa cada módulo em subprojetos Gradle compilados de forma independente. Isso impede fisicamente imports inválidos e simplifica a extração para um microsserviço próprio.

```text
ligadospalpites-core/
│
├── settings.gradle.kts          # Define todos os submódulos do projeto
├── build.gradle.kts             # Configurações compartilhadas e plugins do Spring
│
├── apps/
│   └── main-app/                # O módulo principal (runner) que inicializa o Spring Boot
│       ├── src/main/kotlin/com/ligadospalpites/app/Application.kt
│       └── build.gradle.kts     # Depende de :features:predictions, :features:groups, etc.
│
└── packages/ (ou modules/)
    ├── shared/                  # Módulos transversais compartilhados
    │   └── core/                # Tipos base, utilitários reais de baixo nível
    │
    └── features/                # Módulos de negócio específicos
        ├── predictions/         # Módulo de palpites (predictions)
        │   ├── src/main/kotlin/
        │   │   └── com/ligadospalpites/feature/predictions/
        │   │       ├── api/     # Interface pública e DTOs (acessíveis por outros módulos)
        │   │       └── internal/# Implementação, JPA entities, controllers (visibilidade 'internal')
        │   └── build.gradle.kts
        │
        └── groups/              # Módulo de grupos (groups)
            ├── src/main/kotlin/
            │   └── com/ligadospalpites/feature/groups/
            └── build.gradle.kts
```

### Opção B: Spring Modulith (Monoprojeto Modularizado)

Utiliza um único projeto Gradle/Maven estruturado por pacotes estruturais controlados pelo Spring Modulith.

```text
ligadospalpites-core/
└── src/main/kotlin/com/ligadospalpites/
    ├── Application.kt           # Anotado com @SpringBootApplication
    │
    ├── shared/                  # Pacote compartilhado por todos (comum)
    │
    ├── predictions/             # Módulo 'predictions' (Spring Modulith trata como módulo)
    │   ├── PredictionsService.kt# Exposto (público) para outros módulos
    │   └── internal/            # Invisível/inacessível fora do pacote predictions
    │       ├── PredictionsRepository.kt
    │       └── PredictionsController.kt
    │
    └── groups/                  # Módulo 'groups'
        ├── GroupsService.kt     # Exposto (público)
        └── internal/
```

---

## 🚀 Passo a Passo para Modularizar uma Funcionalidade

Ao receber uma tarefa para criar ou refatorar uma funcionalidade sob esta skill, siga estes passos:

### Passo 1: Delimitação do Domínio
1. Identifique o domínio da funcionalidade (ex: `predictions`).
2. Liste quais dados ele gerencia exclusivamente (entidades JPA de propriedade do módulo).
3. Identifique as dependências externas (quais informações de outros domínios ele precisa).

### Passo 2: Definição da API Pública (Contrato)
1. Crie o pacote/subprojeto `api` ou exponha apenas interfaces e DTOs no pacote raiz do módulo.
2. Defina os métodos de negócio que serão oferecidos a outros módulos.
3. Garanta que nenhuma entidade JPA (`@Entity`) ou detalhes internos do banco vazem nesta interface. Use DTOs públicos específicos de integração.

### Passo 3: Isolamento do Código e Dados
1. Crie o subprojeto Gradle ou pacote apropriado.
2. Mova a lógica de negócio (Use Cases/Services) para o novo espaço, marcando as implementações como `internal`.
3. Configure a injeção de dependências usando classes `@Configuration` locais do próprio módulo, registrando seus próprios beans.

### Passo 4: Integração via DI / Eventos
1. No `build.gradle.kts` do módulo principal (`main-app`) ou módulo consumidor, declare a dependência para o módulo de feature.
2. Injete a interface de serviço necessária via injeção de construtor do Spring.
3. Para comunicação assíncrona, use `ApplicationEventPublisher` para publicar eventos e `@TransactionalEventListener` nos módulos ouvintes.

---

## ⚠️ O que EVITAR (Anti-patterns)
- ❌ **Imports cruzados profundos:** Módulo A importando diretamente classes internas ou repositories do Módulo B (ex: `import com.ligadospalpites.feature.modulo_b.internal.ModuloBRepository`).
- ❌ **Joins de Banco de Dados entre Módulos:** Fazer consultas JPA/SQL (como `@ManyToOne` ou `@ManyToMany` mapeando entidades de domínios diferentes) ou Joins nativos que unam tabelas sob a propriedade de domínios diferentes.
- ❌ **Dependência Circular:** Módulo A depende de Módulo B, e Módulo B depende de Módulo A. Resolva usando eventos ou movendo o contrato compartilhado para um módulo `core` ou `shared`.
