---
name: agregados
description: Diretrizes e boas práticas para modelagem de Agregados (Aggregates) e Raízes de Agregados (Aggregate Roots) no domínio em Kotlin e Spring Boot.
---

# Agregados e Raízes de Agregados (Aggregate Roots)

Esta skill orienta o agente na modelagem de **Agregados** no Domain-Driven Design (DDD), estabelecendo limites transacionais claros, garantindo a consistência das regras de negócio e facilitando a futura decomposição em microsserviços.

---

## 💡 O que é um Agregado?

Um **Agregado** é um grupo de objetos de domínio (Entidades e Objetos de Valor) que são tratados como uma única unidade para fins de mudança de dados. 

Cada agregado possui:
- **Raiz do Agregado (Aggregate Root)**: A entidade externa que serve como o único ponto de entrada para o agregado. Objetos externos só podem manter referências para a Raiz, nunca para entidades internas do agregado.
- **Limite de Consistência (Consistency Boundary)**: Garante que todas as regras de negócio (invariantes) dentro do limite sejam respeitadas em cada transação.

---

## 🚀 Regras de Ouro para Modelagem de Agregados

### 1. Proteja as Invariantes do Domínio
Toda alteração de estado no agregado deve passar por métodos de negócio expostos na Raiz do Agregado. Nunca exponha setters públicos ou coleções mutáveis diretamente. A Raiz é responsável por validar e garantir que as regras sejam cumpridas.

### 2. Agregados Devem Ser Pequenos
Evite criar "Mega Agregados". Agregados grandes causam problemas de concorrência (bloqueios de banco de dados) e performance. Inclua dentro do agregado apenas as entidades que precisam ter **consistência imediata** (transacional). Para consistência entre agregados diferentes, use **consistência eventual** via eventos.

### 3. Referencie outros Agregados apenas por ID
Um agregado **nunca** deve conter uma referência direta de objeto para outra Raiz de Agregado. Em vez disso, use apenas o identificador (ID).
- ❌ **Incorreto:** `val grupo: Grupo` dentro da classe `Palpite`.
-  **Correto:** `val grupoId: GrupoId` dentro de `Palpite`.
*Benefício:* Desacopla a carga de dados (evita LazyInitializationException e queries gigantes do Hibernate) e torna a migração de um agregado para outro banco de dados/microsserviço muito fácil.

### 4. Modifique Apenas um Agregado por Transação
Uma transação de banco de dados deve alterar o estado de apenas uma única Raiz de Agregado. Se a alteração do Agregado A exige uma ação no Agregado B, publique um **Domain Event** a partir de A para que B seja atualizado de forma assíncrona/eventual.

---

## 🛠️ Implementação em Kotlin & Spring Boot

### 1. Encapsulamento de Estado no Aggregate Root
Em Kotlin, controle o acesso às coleções internas usando propriedades privadas mutáveis e exposição pública imutável.

```kotlin
class Grupo(
    val id: GrupoId,
    val nome: String,
    val criadorId: UsuarioId
) {
    // Lista mutável encapsulada
    private val _membros = mutableListOf<UsuarioId>()
    
    // Exposição imutável para leitura externa
    val membros: List<UsuarioId> get() = _membros.toList()

    // Método de negócio na Raiz que protege a invariante
    fun adicionarMembro(novoMembroId: UsuarioId) {
        require(novoMembroId != criadorId) { "O criador já é membro por padrão" }
        require(!_membros.contains(novoMembroId)) { "Usuário já é membro deste grupo" }
        require(_membros.size < 100) { "Limite máximo de membros atingido" }
        
        _membros.add(novoMembroId)
    }
}
```

### 2. Disparo de Eventos com Spring Data `AbstractAggregateRoot`
O Spring Data fornece uma classe base excelente para registrar eventos de domínio de dentro do Agregado. Ao salvar o Agregado através de um `Repository`, o Spring publica automaticamente os eventos registrados.

```kotlin
import org.springframework.data.domain.AbstractAggregateRoot

@Entity
class Palpite(
    @Id val id: PalpiteId,
    val usuarioId: UsuarioId,
    val jogoId: JogoId,
    var placarMandante: Int,
    var placarVisitante: Int
) : AbstractAggregateRoot<Palpite>() {

    fun registrarOuAtualizarPalpite(novoPlacarMandante: Int, novoPlacarVisitante: Int) {
        this.placarMandante = novoPlacarMandante
        this.placarVisitante = novoPlacarVisitante
        
        // Registra o evento de domínio
        registerEvent(PalpiteRegistradoEvent(palpiteId = this.id, usuarioId = this.usuarioId))
    }
}

// Evento de Domínio (VO Imutável)
data class PalpiteRegistradoEvent(val palpiteId: PalpiteId, val usuarioId: UsuarioId)
```

No módulo ou componente interessado, escute o evento usando `@TransactionalEventListener`:

```kotlin
@Component
class RankingService(private val rankingRepository: RankingRepository) {

    @TransactionalEventListener
    fun aoRegistrarPalpite(event: PalpiteRegistradoEvent) {
        // Atualiza a tabela/agregado de ranking de forma eventualmente consistente
        rankingRepository.atualizarPontuacao(event.usuarioId)
    }
}
```

---

## 💾 Padrão de Repositório (Repositories)

- **Regra:** Apenas Raízes de Agregados devem possuir Repositórios (`JpaRepository` / `CrudRepository`).
- Entidades internas do agregado devem ser carregadas e salvas sempre através da Raiz do Agregado. Nunca crie um repositório direto para uma entidade interna do agregado.

---

## ⚠️ O que EVITAR (Anti-patterns)

- ❌ **Navegação profunda de relacionamentos**: Ter entidades que conectam todo o banco de dados via referências JPA (`@ManyToOne`, `@OneToMany`) forçando carregamentos desnecessários em cascata.
- ❌ **Setters e Getters Públicos Irrestritos**: Permitir que qualquer service altere dados internos do agregado sem passar pelas validações de negócio da Raiz.
- ❌ **Salvar Múltiplos Agregados na mesma Transação Síncrona**: Acoplar transações de diferentes domínios, impedindo a escalabilidade e a migração futura para microsserviços.
