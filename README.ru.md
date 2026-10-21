<picture>
  <source media="(prefers-color-scheme: dark)" srcset="docs/assets/archgate/banner/archgate-banner-dark.png">
  <img src="docs/assets/archgate/banner/archgate-banner-light.png" alt="ArchGate — Architecture rules as a build gate" width="1280" height="320">
</picture>

![Java 21](https://img.shields.io/badge/Java-21-3FA66B?logo=openjdk&logoColor=white)
![Gradle 9.7+](https://img.shields.io/badge/Gradle-9.7%2B-3FA66B?logo=gradle&logoColor=white)
![Maven 3.9+](https://img.shields.io/badge/Maven-3.9%2B-3FA66B?logo=apachemaven&logoColor=white)
![ArchUnit 1.x](https://img.shields.io/badge/ArchUnit-1.x-3FA66B)
[![License: Apache-2.0](https://img.shields.io/badge/License-Apache--2.0-3FA66B)](LICENSE)

[English](README.md) · Русский

ArchGate запускает архитектурные правила как шаг сборки Maven или Gradle. Если код нарушает правило, сборка останавливается.
- Правило запускается, только если твой проект его перечислил, и встроенные правила тут не исключение.
- Встроенные правила можно совмещать с правилами, которые ты пишешь сам.
- Правила не привязаны к одному анализатору: встроенные используют [ArchUnit](https://www.archunit.org/), [JavaParser](https://javaparser.org/) или [ClassGraph](https://github.com/classgraph/classgraph), и каждый анализатор живёт в своём модуле.

## Встроенные правила

| Правило | Backend | Severity | Что проверяет | Документация |
|---|---|---|---|---|
| `NoThreadSleepRule` | ArchUnit | ERROR | main + test | [Без Thread.sleep](docs/rules/no-thread-sleep.ru.md) |
| `NoConsoleOutputRule` | ArchUnit | ERROR | main | [Без вывода в консоль](docs/rules/no-console-output.ru.md) |
| `NoJavaUtilDateRule` | ArchUnit | WARNING | main + test | [Без java.util.Date](docs/rules/no-java-util-date.ru.md) |
| `LoggerFieldNameRule` | ArchUnit | ERROR | main | [Имя поля логгера](docs/rules/logger-field-name.ru.md) |
| `TestClassNamingRule` | ArchUnit | ERROR | test | [Именование тестовых классов](docs/rules/test-class-naming.ru.md) |
| `ClassLocationBySuffixRule` | ArchUnit | ERROR | main | [Расположение класса по суффиксу](docs/rules/class-location-by-suffix.ru.md) |
| `ServiceRegistrationRule` | ArchUnit | ERROR | main + test | [Регистрация сервисов](docs/rules/service-registration.ru.md) |
| `NoDisabledTestsRule` | ArchUnit | ERROR | test | [Без отключённых тестов](docs/rules/no-disabled-tests.ru.md) |
| `NoDeprecatedApiUsageRule` | ArchUnit | WARNING | main + test | [Без использования deprecated API](docs/rules/no-deprecated-api-usage.ru.md) |
| `NoGenericThrowsRule` | ArchUnit | WARNING | main | [Без generic-исключений](docs/rules/no-generic-throws.ru.md) |
| `TestAssertionsRule` | ArchUnit | WARNING | test | [Проверки в тестах](docs/rules/test-assertions.ru.md) |
| `HashCodeEqualsTogetherRule` | ArchUnit | ERROR | main | [hashCode и equals вместе](docs/rules/hash-code-equals-together.ru.md) |
| `UtilityClassFinalityRule` | ArchUnit | WARNING | main | [Финальность utility-классов](docs/rules/utility-class-finality.ru.md) |
| `MaxMethodParametersRule` | ArchUnit | WARNING | main + test | [Максимум параметров метода](docs/rules/max-method-parameters.ru.md) |
| `NoWildcardImportsRule` | JavaParser | WARNING | main + test | [Без импортов с `*`](docs/rules/no-wildcard-imports.ru.md) |
| `ServiceFileConsistencyRule` | ClassGraph | ERROR | main + test | [Согласованность файлов services](docs/rules/service-file-consistency.ru.md) |

Каждый backend поставляется парой артефактов в группе `dev.quokkify.archgate`:

| Backend | Артефакт с правилами (пакет) | Артефакт контекста |
|---|---|---|
| ArchUnit | `archgate-rules-archunit` (`dev.quokkify.archgate.rules.archunit`) | `archgate-context-archunit` |
| JavaParser | `archgate-rules-javaparser` (`dev.quokkify.archgate.rules.javaparser`) | `archgate-context-javaparser` |
| ClassGraph | `archgate-rules-classgraph` (`dev.quokkify.archgate.rules.classgraph`) | `archgate-context-classgraph` |

Артефакт с правилами зависит от артефакта контекста, поэтому для запуска правил достаточно добавить его. Контекст отдельно нужен, чтобы писать собственные правила для этого backend. Если в classpath нет провайдера контекста для backend правила, сборка падает с сообщением `No ContextProvider for … is registered on the classpath. Add dev.quokkify.archgate:archgate-context-<backend> as a test dependency.`

## Использование с Maven

**1. Добавь плагин и правила в pom** модуля, где лежат твои тесты (Maven 3.9.0 или новее):

```xml
<dependencies>
  <dependency>
    <groupId>dev.quokkify.archgate</groupId>
    <artifactId>archgate-rules-archunit</artifactId>
    <version>0.1.0</version>
    <scope>test</scope>
  </dependency>
</dependencies>

<build>
  <plugins>
    <plugin>
      <groupId>dev.quokkify.archgate</groupId>
      <artifactId>archgate-maven-plugin</artifactId>
      <version>0.1.0</version>
      <executions><execution><goals><goal>check</goal></goals></execution></executions>
      <configuration>
        <rootPackage>com.acme.tests</rootPackage>
      </configuration>
    </plugin>
  </plugins>
</build>
```

**2. Перечисли правила для запуска** в `src/test/resources/META-INF/services/dev.quokkify.archgate.api.ArchitectureRule`:

```
dev.quokkify.archgate.rules.archunit.NoThreadSleepRule
dev.quokkify.archgate.rules.archunit.NoConsoleOutputRule
```

**3. Запусти** `mvn verify`. Проверка выполняется на фазе `verify`, в том числе с `-DskipTests`:

```
[PASS]  MAIN+TEST     41 ms  No Thread.sleep
[PASS]  MAIN          12 ms  No console output
============================================================
Gate PASSED: 2 rules, no findings
Failure policy (failOn=WARNING):
  ERROR    fails the build
  WARNING  fails the build
  INFO     reported only
```

Отчёт также записывается в `target/archgate/report.txt`.

## Использование с Gradle

Нужен Gradle 9.7.1 или новее.

**1. Подключи плагин и правила** в `build.gradle` модуля, где лежат твои тесты:

```groovy
plugins {
    id 'java'
    id 'dev.quokkify.archgate' version '0.1.0'
}

archgate {
    rootPackage = 'com.acme.tests'
    failOn = 'WARNING'            // или -Parchgate.failOn=ERROR
    skip = false                  // или -Parchgate.skip=true
    modules(':api', ':domain')    // необязательно
}

dependencies {
    testImplementation platform('dev.quokkify.archgate:archgate-bom:0.1.0')
    testImplementation 'dev.quokkify.archgate:archgate-rules-archunit'
}
```

**2. Перечисли правила для запуска** в `src/test/resources/META-INF/services/dev.quokkify.archgate.api.ArchitectureRule`, это тот же файл, что и для Maven.

**3. Запусти** `./gradlew check`. Задача `archgateCheck` (группа `verification`) зависит от `testClasses`, подключена к `check` и печатает тот же отчёт, что и плагин Maven. Отчёт также записывается в `build/reports/archgate/report.txt`. Задача кэшируется и совместима с configuration cache.

`rootPackage` обязателен. Модули из `modules(...)` должны сами подключать плагин, а модули, которые только отдают свои классы проверке, задают `archgate { skip = true }`. Подробнее в [Configuration](docs/configuration.ru.md#gradle).

Чтобы поменять параметр или severity встроенного правила, унаследуйся от него и зарегистрируй наследника вместо оригинала. Как это сделать, показано на странице каждого правила.

## Как написать своё правило

В многомодульном проекте храни кастомные правила в отдельном последнем модуле с именем `<project>-archgate-rules`, где префикс — имя проекта. Например, для проекта `platform` используй `platform-archgate-rules`. Подробнее в [Configuration → Multi-module projects](docs/configuration.ru.md#многомодульные-проекты).

**1. Добавь контекст backend** как test-зависимость (он приносит `archgate-api`):

```xml
<dependency>
  <groupId>dev.quokkify.archgate</groupId>
  <artifactId>archgate-context-archunit</artifactId>
  <version>0.1.0</version>
  <scope>test</scope>
</dependency>
```

**2. Реализуй `ArchitectureRule`** в `src/test/java`. Параметр типа — контекст того backend, который использует правило:

```java
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.lang.ArchRule;
import dev.quokkify.archgate.api.ArchitectureRule;
import dev.quokkify.archgate.api.Severity;
import dev.quokkify.archgate.context.archunit.ArchUnitContext;
import dev.quokkify.archgate.context.archunit.support.ArchRuleChecks;

public class NoRestAssuredInTestsRule implements ArchitectureRule<ArchUnitContext> {

  @Override
  public String name() {
    return "Tests do not call RestAssured directly";
  }

  @Override
  public Severity severity() {
    return Severity.ERROR;
  }

  @Override
  public Class<ArchUnitContext> contextType() {
    return ArchUnitContext.class;
  }

  @Override
  public void verify(ArchUnitContext context) {
    ArchRule rule = noClasses().that().resideInAPackage(context.rootPackage() + ".tests..")
        .should().dependOnClassesThat().resideInAPackage("io.restassured..")
        .allowEmptyShould(true);
    checkViolations("Tests call RestAssured only through steps", ArchRuleChecks.violations(rule, context.test()));
  }
}
```

**3. Зарегистрируй его** в том же файле services. Вложенные `public static` классы правил тоже нужно перечислять, по бинарному имени (`Outer$Inner`):

```
com.acme.tests.arch.NoRestAssuredInTestsRule
```

Если ты написал правило, но не зарегистрировал, сборка упадёт и подскажет, какую строку добавить.

Что доступно внутри `verify`, по backend-ам:

| Контекст | Метод | Что возвращает |
|---|---|---|
| `ArchUnitContext` | `rootPackage()` | настроенный `rootPackage` |
| | `main()`, `test()`, `all()` | скомпилированные классы внутри `rootPackage` (ArchUnit `JavaClasses`) |
| | `layout()` | `ProjectLayout`: каталоги классов и исходников по срезам |
| `JavaParserContext` | `compilationUnits(Scope)` | разобранные исходники среза (JavaParser `CompilationUnit`) |
| | `relativePath(unit)` | путь единицы относительно её каталога исходников |
| `ClassGraphContext` | `scan(Scope)` | `ScanResult` ClassGraph по каталогам классов среза |
| | `layout()` | `ProjectLayout` |

Правило сообщает о нарушениях, выбрасывая `ArchitectureViolation` (это делает за тебя `checkViolations`) или `AssertionError` из ArchUnit. Если правилу нечего проверять, например нет классов в нужном срезе, оно выбрасывает `UnableToVerifyError`. Это всегда роняет сборку.

## Ещё

- [Configuration](docs/configuration.ru.md): параметры плагинов Maven и Gradle, `failOn`, многомодульные проекты, как читать отчёт.
- [Development](docs/development.ru.md): локальная сборка, тесты, pre-push hook.
- [Releasing](docs/releasing.ru.md): как готовится и публикуется релиз.
- [FAQ](docs/faq.ru.md) ([in English](docs/faq.md)): ответы на частые вопросы.
- [Архитектурные решения](docs/adr/0001-opt-in-rule-registration.md): почему дизайн устроен именно так.

Лицензия: [Apache License, Version 2.0](LICENSE). Copyright 2026 quokkify.
