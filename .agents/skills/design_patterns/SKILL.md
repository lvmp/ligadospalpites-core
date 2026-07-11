---
name: design_patterns
description: Diretrizes e boas práticas para aplicação de Padrões de Projeto (Design Patterns) clássicos e modernos em Kotlin e Spring Boot.
---

# Padrões de Projeto (Design Patterns) com Kotlin e Spring Boot

Esta skill orienta o agente na aplicação prática de **Design Patterns** (Padrões de Projeto) utilizando as facilidades idiomáticas do **Kotlin** combinadas com os recursos do ecossistema **Spring Boot**.

---

## 🏗️ Padrões Criacionais (Creational)

### 1. Builder Pattern (Nativo com Argumentos Nomeados)
No Java clássico, o Builder Pattern é muito usado para evitar construtores gigantes. Em Kotlin, este padrão é praticamente obsoleto devido a **Argumentos Nomeados (Named Arguments)** e **Valores Padrão (Default Parameters)**.

- **Abordagem Idiomática Kotlin:**
```kotlin
data class Palpite(
    val id: PalpiteId = PalpiteId.gerar(),
    val usuarioId: UsuarioId,
    val jogoId: JogoId,
    val placarMandante: Int = 0,
    val placarVisitante: Int = 0
)

// Criação do objeto fluida e segura, dispensando classes Builder adicionais
val palpite = Palpite(
    usuarioId = UsuarioId(userId),
    jogoId = JogoId(jogoId),
    placarMandante = 2
)
```

### 2. Factory Method (Companions e Extension Functions)
Use para encapsular a lógica de criação de agregados complexos ou para garantir que um agregado inicie em um estado consistente de negócio.

- **Abordagem Kotlin (`companion object`):**
```kotlin
class Grupo private constructor(
    val id: GrupoId,
    val nome: String,
    val codigoAcesso: String
) {
    companion object {
        // Factory Method que encapsula a geração do código de acesso
        fun criarNovo(nome: String): Grupo {
            val codigoGerado = gerarCodigoAlfanumericoUnico()
            return Grupo(
                id = GrupoId.gerar(),
                nome = nome,
                codigoAcesso = codigoGerado
            )
        }
    }
}
```

---

## 📐 Padrões Estruturais (Structural)

### 1. Adapter (Adaptador)
Essencial para implementar a **Inversão de Dependência** da Clean Architecture. Converte a interface de um sistema de terceiros ou driver de banco de dados na interface/porta esperada pelo domínio do negócio.

- **Aplicação:**
```kotlin
// Interface do Domínio (Port)
interface SMSGateway {
    fun enviarMensagem(telefone: String, texto: String)
}

// Adaptador de Infraestrutura (Adapter)
@Component
class TwilioSMSAdapter(
    private val twilioClient: TwilioClient
) : SMSGateway {
    override fun enviarMensagem(telefone: String, texto: String) {
        twilioClient.send(PhoneNumber(telefone), texto)
    }
}
```

### 2. Proxy (Delegates e AOP do Spring)
O Spring Boot utiliza muito Proxies sob o capô para implementar funcionalidades transversais (Aspect-Oriented Programming - AOP), como controle transacional (`@Transactional`) e segurança. Em Kotlin, também podemos usar **Class Delegation (`by`)** para delegar chamadas de forma limpa.

---

## 🏃‍♂️ Padrões Comportamentais (Behavioral)

### 1. Strategy (Estratégia)
Permite alternar algoritmos em tempo de execução. Muito simples de implementar no Spring injetando um mapa de componentes ou uma lista dinâmica.

- **Aplicação com Enums/Mapas no Spring:**
```kotlin
interface CalculadoraPontos {
    val tipoCalculo: TipoPalpite
    fun calcular(placarReal: Placar, palpite: Placar): Int
}

@Component
class MotorDeCalculo(
    calculadorasList: List<CalculadoraPontos>
) {
    // Mapeia as estratégias por chave
    private val calculadoras = calculadorasList.associateBy { it.tipoCalculo }

    fun processarPontos(tipo: TipoPalpite, real: Placar, palpite: Placar): Int {
        val estrategia = calculadoras[tipo] 
            ?: throw IllegalArgumentException("Calculadora não encontrada para: $tipo")
        return estrategia.calcular(real, palpite)
    }
}
```

### 2. Observer (Observador via Eventos do Spring)
Permite que múltiplos componentes reajam de forma desacoplada à alteração de estado de outro componente.

- **Aplicação no Spring:**
  - **Publisher:** Usa `ApplicationEventPublisher.publishEvent(event)`
  - **Listener:** Usa `@EventListener` ou `@TransactionalEventListener` para reagir de forma síncrona ou assíncrona.

---

## ⚠️ O que EVITAR (Anti-patterns)

- ❌ **Superengenharia (Patternitis)**: Tentar forçar padrões de projeto complexos em cenários onde uma lógica condicional simples (`if/else` ou `when`) resolveria perfeitamente. Use padrões apenas para resolver problemas reais de extensibilidade, acoplamento e manutenibilidade.
- ❌ **Singletons Globais Manuais (`object`) contendo Estado**: O Kotlin permite criar Singletons usando a palavra-chave `object`. Evite usar `object` para armazenar estado mutável compartilhado na aplicação. Deixe que o Spring controle o ciclo de vida dos beans como Singletons sem estado.
