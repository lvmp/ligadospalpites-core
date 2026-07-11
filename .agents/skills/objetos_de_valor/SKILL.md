---
name: objetos_de_valor
description: Diretrizes sobre a importância e a implementação de Objetos de Valor (Value Objects) no domínio em Kotlin para evitar a obsessão por primitivos.
---

# Objetos de Valor (Value Objects) no Domínio

Esta skill orienta o agente sobre o uso de **Objetos de Valor (Value Objects)** no Domain-Driven Design (DDD), destacando sua importância para criar um domínio expressivo, seguro contra bugs de estado inválido e livre de **Obsessão por Primitivos**.

---

## 💡 O que é um Objeto de Valor?

Ao contrário de uma **Entidade** (que tem uma identidade única que persiste ao longo do tempo, como um `Cliente` com seu `id`), um **Objeto de Valor (Value Object - VO)** é definido inteiramente por seus atributos. Ele descreve ou quantifica algo no domínio.

### Características Principais:

1. **Sem Identidade**: Dois VOs são considerados idênticos se possuírem exatamente os mesmos valores. Não existe um campo `id`.
2. **Imutabilidade**: Seus valores não podem ser alterados após a criação. Para alterar o valor, cria-se uma nova instância do VO.
3. **Autovalidação (Invariância)**: Um VO não pode ser instanciado em um estado inválido. A validação ocorre no momento da criação (no construtor ou bloco de inicialização).
4. **Comportamento Rico**: VOs não são apenas sacos de dados (DTOs). Eles contêm lógica de negócio associada a esses dados (ex: comparar valores, realizar cálculos, formatações).

---

## 🚀 Por que os Objetos de Valor são Importantes?

### 1. Evita a Obsessão por Primitivos (Primitive Obsession)
Usar tipos genéricos (como `String`, `Int`, `BigDecimal`) para representar conceitos de negócio (como `Email`, `Cpf`, `Preco`) enfraquece o sistema. 
- *Sem VO:* Um método que recebe `String, String` para e-mail de origem e destino pode facilmente ter os parâmetros invertidos em tempo de compilação.
- *Com VO:* O compilador garante que `Email` só seja passado onde `Email` é esperado.

### 2. Validação Centralizada e Segura
Se o e-mail for uma `String`, a validação do formato precisa rodar em múltiplos lugares do sistema. Com o VO `Email`, a validação ocorre unicamente no construtor. Se você tem uma instância de `Email`, tem a garantia absoluta de que ela é válida.

### 3. Encapsulamento de Lógica de Negócio
Se você precisa formatar um CPF com máscara, ou somar valores monetários validando se a moeda (`BRL`, `USD`) é a mesma, essa lógica pertence ao VO e não a Services ou Controllers.

---

## 🛠️ Implementação em Kotlin

Kotlin oferece excelentes ferramentas nativas para criar VOs de forma concisa e eficiente.

### 1. Usando `data class` (Para VOs compostos por múltiplos campos)
Como os `data class` geram automaticamente `equals`, `hashCode`, `toString` e `copy()`, eles são perfeitos para VOs.

```kotlin
data class Dinheiro(
    val quantia: BigDecimal,
    val moeda: Currency
) {
    init {
        require(quantia >= BigDecimal.ZERO) { "A quantia não pode ser negativa" }
    }

    // Comportamento rico e imutabilidade
    operator fun plus(outro: Dinheiro): Dinheiro {
        require(this.moeda == outro.moeda) { "Não é possível somar moedas diferentes: ${this.moeda} e ${outro.moeda}" }
        return Dinheiro(this.quantia + outro.quantia, this.moeda)
    }
}
```

### 2. Usando `value class` (Inline Classes - Para VOs de um único valor)
Para evitar o overhead de alocação de memória no compilador para um único valor, use `value class`. Em tempo de execução, o Kotlin utiliza a primitiva pura sob o capô, mas mantém o tipo forte no compilador.

```kotlin
@JvmInline
value class Email(val valor: String) {
    init {
        require(valor.matches(Regex("^[A-Za-z0-9+_.-]+@(.+)$"))) {
            "Formato de e-mail inválido: $valor"
        }
    }
}
```

---

## 💾 Mapeamento JPA / Hibernate no Spring Boot

Para persistir VOs no banco de dados sem misturar a lógica de negócio com anotações pesadas do Hibernate:

### Abordagem A: `@Embeddable` e `@Embedded` (Para VOs compostos)

```kotlin
// Domínio / VO
@Embeddable
data class Endereco(
    val rua: String,
    val cidade: String,
    val cep: String
)

// Entidade JPA
@Entity
class Cliente(
    @Id val id: UUID,
    val nome: String,
    
    @Embedded
    val endereco: Endereco
)
```

### Abordagem B: `AttributeConverter` (Para VOs simples ou inline classes)

Excelente para persistir VOs como `Email` ou `Cpf` como uma `String` comum no banco de dados.

```kotlin
@Converter(autoApply = true)
class EmailConverter : AttributeConverter<Email, String> {
    override fun convertToDatabaseColumn(attribute: Email?): String? = attribute?.valor
    override fun convertToEntityAttribute(dbData: String?): Email? = dbData?.let { Email(it) }
}
```

---

## ⚠️ O que EVITAR (Anti-patterns)

- ❌ **Mutabilidade**: Nunca exponha propriedades mutáveis (`var`) em um VO. Todos os campos devem ser `val` e as coleções devem ser imutáveis.
- ❌ **Vazamento de Identidade**: Adicionar IDs aos seus VOs. Se precisa de ID, é uma **Entidade**, não um VO.
- ❌ **Múltiplos caminhos de criação inválida**: Ter construtores secundários que pulam a validação principal.
- ❌ **Acoplamento com Frameworks**: Tentar injetar Spring Beans (`@Autowired`) dentro de um Objeto de Valor. VOs devem ser classes puras (POJOs/Domain Models puros).
