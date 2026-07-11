---
name: solid
description: Diretrizes e boas práticas para aplicar os princípios S.O.L.I.D. no desenvolvimento de software com Kotlin e Spring Boot.
---

# Princípios S.O.L.I.D. com Kotlin e Spring Boot

Esta skill orienta o agente na aplicação prática dos princípios **S.O.L.I.D.** de design orientado a objetos no ecossistema **Kotlin** e **Spring Boot**, ajudando a criar códigos limpos, modulares, testáveis e fáceis de manter.

---

## 🛠️ Os Cinco Princípios

### 1. Single Responsibility Principle (Princípio da Responsabilidade Única - SRP)
> "Uma classe deve ter apenas um motivo para mudar."

Uma classe deve ser responsável por apenas um ator ou por uma única parte da funcionalidade do software.

- **Aplicação no Spring Boot:** 
  - Evite serviços gigantes (`UserService` contendo lógica de banco, lógica de envio de email, criptografia de senha e geração de relatórios).
  - Divida em componentes especializados: `RegistrarUsuarioUseCase` (lógica de fluxo), `PasswordHasher` (segurança), `EmailSender` (comunicação), `UsuarioRepository` (persistência).
  - Em Kotlin, use funções de arquivo único ou classes utilitárias menores para responsabilidades puramente acessórias.

---

### 2. Open/Closed Principle (Princípio Aberto/Fechado - OCP)
> "Entidades de software (classes, módulos, funções) devem estar abertas para extensão, mas fechadas para modificação."

Você deve ser capaz de estender o comportamento de um sistema sem precisar reescrever ou alterar o código existente.

- **Aplicação no Spring Boot:**
  - Use o poder do **polimorfismo** e da injeção de dependência do Spring injetando listas de implementações.
  - *Exemplo:* Validar regras de palpite no sistema.

```kotlin
interface RegraValidacaoPalpite {
    fun validar(palpite: Palpite)
}

@Component
class ValidadorDePalpite(
    // O Spring injeta automaticamente todas as classes que implementam a interface
    private val regras: List<RegraValidacaoPalpite>
) {
    fun validar(palpite: Palpite) {
        regras.forEach { it.validar(palpite) }
    }
}
```
*Para adicionar uma nova regra de validação, basta criar uma nova classe anotada com `@Component` que implemente `RegraValidacaoPalpite`. O validador original permanece intocado.*

---

### 3. Liskov Substitution Principle (Princípio da Substituição de Liskov - LSP)
> "Objetos em um programa devem ser substituíveis por instâncias de seus subtipos, sem alterar a correção do programa."

Uma classe filha deve poder ser usada no lugar de sua classe pai sem quebrar o sistema (não altere o comportamento esperado das interfaces).

- **Aplicação no Kotlin:**
  - Evite criar subclasses que herdam uma interface mas lançam `UnsupportedOperationException` ou `NotImplementedError` para alguns de seus métodos.
  - Não mude as regras das pré-condições (parâmetros de entrada mais restritivos) ou pós-condições (retornos menos restritivos) nas classes derivadas.
  - Em Kotlin, prefira a palavra-chave `open` apenas quando a extensão por herança for realmente necessária e testada. O padrão do Kotlin é fechar as classes para herança (`final` por padrão), o que desencoraja o mau uso do LSP.

---

### 4. Interface Segregation Principle (Princípio da Segregação de Interfaces - ISP)
> "Clientes não devem ser forçados a depender de interfaces que não utilizam."

Muitas interfaces específicas são melhores do que uma interface única geral.

- **Comportamento Recomendado:**
  - Se você tem uma classe que gerencia relatórios e também executa exclusões, não crie uma única interface contendo ambas as operações se o consumidor de exclusões não precisar visualizar relatórios.
  - Em Kotlin, faça uso de interfaces pequenas e focadas, ou herança múltipla de interfaces.

```kotlin
interface LeitorPalpite {
    fun buscarPorId(id: PalpiteId): Palpite?
}

interface GravadorPalpite {
    fun salvar(palpite: Palpite)
}

// A implementação pode implementar ambas, mas o consumidor só consome o que precisa
@Component
class PalpiteRepositoryAdapter : LeitorPalpite, GravadorPalpite { ... }
```

---

### 5. Dependency Inversion Principle (Princípio da Inversão de Dependência - DIP)
> "Dependa de abstrações, não de implementações concretas."

Módulos de alto nível não devem depender de módulos de baixo nível. Ambos devem depender de abstrações.

- **Aplicação no Spring Boot:**
  - O Spring Boot facilita isso nativamente via Injeção de Dependência (DI).
  - Nunca instancie classes de infraestrutura com `new` / `()` dentro de serviços de aplicação ou domínio.
  - Sempre injete a **Interface** de um repositório ou serviço externo no seu Use Case/Service. O framework se encarrega de fornecer a implementação em tempo de execução.
  - *Exemplo:* O caso de uso depende da interface `EmailSenderPort`, e o `SendGridEmailAdapter` (infraestrutura) implementa essa interface. O caso de uso não conhece o SendGrid.

---

## ⚠️ O que EVITAR (Anti-patterns)

- ❌ **Interfaces com apenas uma implementação por obrigação**: Criar interfaces para absolutamente todas as classes do sistema (como `UserServiceImpl` implementando `UserService` onde não há outros polimorfismos e ambos estão na mesma camada) adiciona complexidade desnecessária. Use interfaces principalmente para limites de arquitetura (DIP/Hexagonal) ou polimorfismo real (OCP).
- ❌ **Classes utilitárias gigantes (God Classes)**: Agrupar centenas de métodos estáticos não relacionados em classes como `Utils` ou `Helper`. Prefira pequenas extensões Kotlin (`Extension Functions`) focadas nos tipos adequados.
- ❌ **Injeção de dependência via campo (`@Autowired` no atributo)**: Dificulta testes unitários por requerer reflexão. Use sempre **injeção via construtor** (que é o padrão nativo do Spring e do Kotlin ao declarar propriedades no construtor primário).
