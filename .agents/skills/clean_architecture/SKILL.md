---
name: clean_architecture
description: Diretrizes e boas práticas para estruturar o projeto seguindo a arquitetura limpa (Clean Architecture / Hexagonal) em Kotlin e Spring Boot.
---

# Clean Architecture (Arquitetura Limpa)

Esta skill orienta o agente na estruturação e implementação do sistema seguindo os princípios da **Clean Architecture** (Arquitetura Limpa) e **Ports and Adapters (Arquitetura Hexagonal)** utilizando **Kotlin** e **Spring Boot**. O objetivo principal é manter as regras de negócio isoladas, testáveis e independentes de frameworks, bancos de dados ou interfaces de entrega.

---

## 💡 A Regra de Dependência (Dependency Rule)

O coração da Clean Architecture é a **Regra de Dependência**:
> As dependências de código-fonte devem apontar apenas para dentro, em direção às políticas de nível mais alto (regras de negócio).

Nada nas camadas internas (Domínio e Aplicação) deve saber qualquer coisa sobre as camadas externas (Bancos de dados, Frameworks como Spring, APIs REST ou Mensageria).

```text
       ┌────────────────────────────────────────────────────────┐
       │                 INFRAESTRUTURA / ADAPTERS              │
       │  (Controllers REST, Entidades JPA, Clientes HTTP, etc) │
       │                                                        │
       │       ┌────────────────────────────────────────┐       │
       │       │                APLICAÇÃO               │       │
       │       │    (Casos de Uso, Portas/Interfaces)   │       │
       │       │                                        │       │
       │       │       ┌────────────────────────┐       │       │
       │       │       │        DOMÍNIO         │       │       │
       │       │       │ (Entidades, VOs, etc)  │       │       │
       │       │       └────────────────────────┘       │       │
       │       └────────────────────────────────────────┘       │
       └────────────────────────────────────────────────────────┘
                    A dependência aponta apenas para DENTRO
```

---

## 📂 Camadas do Sistema e suas Responsabilidades

### 1. Domínio (Domain)
É o núcleo do sistema, contendo as regras de negócio corporativas mais estáveis.
- **O que contém:** Entidades de Domínio, Objetos de Valor (VOs), Exceções de Domínio e Domain Services (lógicas que envolvem mais de um Agregado).
- **Regra:** **Puro Kotlin**. Absolutamente nenhuma anotação de framework (sem `@Entity`, `@Table`, `@Component`, `@Autowired`). Deve ser fácil de testar sem mocks complexos ou inicialização do Spring.

### 2. Aplicação (Application)
Contém as regras de negócio específicas da aplicação, coordenando o fluxo de dados.
- **O que contém:** Casos de Uso (Use Cases / Interactors) e **Ports (Portas)**:
  - **Output Ports (Portas de Saída):** Interfaces que definem o que a aplicação precisa do mundo externo (ex: `PalpiteRepository`, `NotificationGateway`).
  - **Input Ports (Portas de Entrada):** Interfaces ou contratos que expõem as capacidades da aplicação para o mundo externo (ex: o próprio Use Case).
- **Regra:** Não depende de frameworks externos. Depende apenas de interfaces/portas.

### 3. Infraestrutura / Adaptadores (Infrastructure / Adapters)
Traduz os dados entre os formatos convenientes para a aplicação/domínio e formatos externos (banco, web, etc.).
- **O que contém:** 
  - **Adapters de Entrada (Driving Adapters):** Controllers REST, Listeners de Fila (RabbitMQ/Kafka) que acionam os Casos de Uso.
  - **Adapters de Saída (Driven Adapters):** Implementações das Portas de Saída (ex: `JpaPalpiteRepositoryAdapter` que usa o `SpringDataJpaRepository`, clientes Retrofit/Feign para APIs externas).
  - **Entidades de Banco de Dados (JPA Entities):** Modelos anotados com `@Entity` e `@Column` otimizados para a persistência física.
- **Regra:** Aqui vive o Spring Boot. É permitido o uso de todas as anotações do framework.

---

## 🔄 Fluxo de Controle e Tradução de Dados (Mappers)

Como o Domínio é isolado da Infraestrutura de banco de dados, é recomendável manter uma separação clara entre a **Entidade de Domínio** e a **Entidade JPA**. Para transitar dados entre essas camadas, utilizamos **Mappers**.

```text
[Web (Controller)] ──> DTO de Entrada ──> [Caso de Uso]
                                                │
   JPA Entity <── Mapper ── Entidade de Domínio ┘
```

### Exemplo de Fluxo e Implementação:

#### 1. Interface / Port (Application Layer)
```kotlin
package com.ligadospalpites.domain.ports

interface PalpiteRepository {
    fun buscarPorId(id: UUID): Palpite?
    fun salvar(palpite: Palpite): Palpite
}
```

#### 2. Adapter de Persistência (Infrastructure Layer)
```kotlin
package com.ligadospalpites.infrastructure.persistence

@Component
class JpaPalpiteRepositoryAdapter(
    private val springDataRepository: SpringDataPalpiteRepository
) : PalpiteRepository {

    override fun buscarPorId(id: UUID): Palpite? {
        return springDataRepository.findById(id).map { it.toDomain() }.orElse(null)
    }

    override fun salvar(palpite: Palpite): Palpite {
        val jpaEntity = PalpiteJpaEntity.fromDomain(palpite)
        return springDataRepository.save(jpaEntity).toDomain()
    }
}
```

---

## 🚀 Estrutura de Pacotes Sugerida por Módulo

Para manter a Clean Architecture de forma clara dentro de um módulo (ex: `predictions`):

```text
predictions/
├── domain/                    # Puro Kotlin
│   ├── models/                # Entidades e VOs (ex: Palpite, Placar)
│   ├── exceptions/            # Exceções de domínio (ex: JogoJaIniciadoException)
│   └── ports/                 # Interfaces (ex: PalpiteRepository)
│
├── application/               # Casos de uso orquestrando o domínio
│   └── usecases/              # Ex: RegistrarPalpiteUseCase
│
└── infrastructure/            # Frameworks, Banco, REST
    ├── persistence/           # Entidades JPA, Spring Data Repositories e Adapters
    │   ├── PalpiteJpaEntity.kt
    │   └── JpaPalpiteRepositoryAdapter.kt
    └── web/                   # Controllers REST e DTOs de Request/Response
        ├── PalpiteController.kt
        └── dtos/
```

---

## ⚠️ O que EVITAR (Anti-patterns)

- ❌ **Entidades JPA no Domínio**: Misturar `@Entity` e lógica de banco de dados diretamente nos seus modelos de negócio. Isso acopla o domínio ao Hibernate/JPA. (Nota: Embora permitida em abordagens híbridas pragmáticas para economizar mapeamento, para Clean Arch pura, mantenha as entidades de persistência separadas das de domínio).
- ❌ **Vazamento do Spring**: Usar `@Service` ou `@Component` no seu pacote `domain`. Registre os beans do domínio e aplicação usando classes `@Configuration` na pasta `infrastructure`.
- ❌ **Controllers Acessando Repositórios Diretamente**: Pular a camada de Casos de Uso/Aplicação para fazer consultas ou persistência direto da Controller REST. Isso corrompe a legibilidade do fluxo e impede reutilização.
