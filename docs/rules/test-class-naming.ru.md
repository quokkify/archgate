# Именование тестовых классов

[English](test-class-naming.md) · Русский

`dev.quokkify.archgate.rules.archunit.TestClassNamingRule` · **ERROR** · test

Класс с методами `@Test`, своими или унаследованными, должен иметь имя, заканчивающееся на `Test`. Распознаются `@Test` и из TestNG, и из JUnit Jupiter (JUnit 5 и 6).

**Зачем.** Генерация suite и surefire находят тестовые классы по имени. Класс `LoginChecks` с методами `@Test` молча никогда не запустится.

```java
// ✗ violation
public class LoginChecks { @Test void login() { … } }

// ✓ fix
public class LoginTest { @Test void login() { … } }
```

Абстрактные базовые классы пропускаются. Общий код клади в класс без методов `@Test`.

**Регистрация**

```
dev.quokkify.archgate.rules.archunit.TestClassNamingRule
```

**Настройка.** Можно задать другие тестовые аннотации и другой суффикс:

```java
public class AcmeTestClassNamingRule extends TestClassNamingRule {
  public AcmeTestClassNamingRule() {
    super(Set.of("org.testng.annotations.Test"), "IT");
  }
}
```

Если тестовых классов не найдено, правило сообщает *unable to verify*, и это всегда роняет сборку.

[← Все правила](../../README.ru.md#встроенные-правила)
