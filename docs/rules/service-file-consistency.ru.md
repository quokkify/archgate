# Согласованность файлов services

[English](service-file-consistency.md) · Русский

`dev.quokkify.archgate.rules.classgraph.ServiceFileConsistencyRule` · **ERROR** · main + test · backend: ClassGraph (`archgate-rules-classgraph`)

Каждый файл `META-INF/services/<interface>` в проверяемых каталогах классов должен перечислять все публичные неабстрактные классы внутри `rootPackage`, которые реализуют этот интерфейс. Класс, который реализует интерфейс, но отсутствует в файле, попадает в отчёт.

Это обратная проверка к [Регистрации сервисов](service-registration.ru.md): та проверяет, что каждая запись файла services указывает на существующий класс, реализующий интерфейс.

- Правило игнорирует файл services `dev.quokkify.archgate.api.ArchitectureRule`. У регистрации правил есть собственная проверка.
- Реализации ищутся транзитивно: класс, который наследует класс-реализацию интерфейса, тоже попадает в отчёт.
- Абстрактные классы, интерфейсы, enum и непубличные классы не сообщаются.
- Если в каталогах классов нет ни одного файла services, кроме файла ArchGate, сравнивать нечего. Правило сообщает *unable to verify*, и это всегда роняет сборку, так что неверный каталог классов не пройдёт молча.

**Зачем.** `ServiceLoader` загружает только классы, перечисленные в файле провайдеров. Добавленный, но не перечисленный провайдер не загружается, и ничто не жалуется, пока его не окажется нет в runtime.

```
# META-INF/services/com.acme.spi.Plugin
com.acme.plugins.AlphaPlugin
```

```java
// ✗ нарушение: реализует Plugin, но не перечислен
package com.acme.plugins;
public class BetaPlugin implements Plugin { /* ... */ }
```

```
META-INF/services/com.acme.spi.Plugin: com.acme.plugins.BetaPlugin is not listed
```

```
# ✓ исправление
com.acme.plugins.AlphaPlugin
com.acme.plugins.BetaPlugin
```

**Регистрация**

```
dev.quokkify.archgate.rules.classgraph.ServiceFileConsistencyRule
```

Добавь `dev.quokkify.archgate:archgate-rules-classgraph` как test-зависимость. Он приносит `archgate-context-classgraph`.

**Настройка.** У этого правила нет параметров, а класс `final`, поэтому наследоваться от него нельзя. Чтобы запустить его с другой severity, скопируй его в свой модуль правил и поменяй `severity()`.

[← Все правила](../../README.ru.md#встроенные-правила)
