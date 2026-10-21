# Configuration

[English](configuration.md) · Русский

## Параметры

| Параметр | По умолчанию | Свойство Maven | Свойство Gradle |
|---|---|---|---|
| `rootPackage` | обязателен | `archgate.rootPackage` | `-Parchgate.rootPackage` |
| `failOn` | `WARNING` (`INFO`, `WARNING`, `ERROR`, `NEVER`) | `archgate.failOn` | `-Parchgate.failOn` |
| `modules` | Maven: текущий модуль и reactor-модули, от которых он напрямую зависит, кроме модулей с упаковкой `pom`; записи — artifactId, а заданный список полностью заменяет умолчание. Gradle: только текущий проект | — | — |
| `skip` | `false` | `archgate.skip` | `-Parchgate.skip` |

Проверка падает, если у любой находки severity не ниже `failOn`. С `NEVER` она только выводит отчёт. Например, `-Darchgate.failOn=ERROR` (Maven) или `-Parchgate.failOn=ERROR` (Gradle) пропускает предупреждения. `UnableToVerifyError` роняет сборку при любом `failOn`, в том числе при `NEVER`.

Плагин Maven — цель `check` артефакта `dev.quokkify.archgate:archgate-maven-plugin`, привязанная к `verify`. Плагин Gradle имеет id `dev.quokkify.archgate` и регистрирует задачу `archgateCheck`.

## Gradle

```groovy
plugins {
    id 'java'
    id 'dev.quokkify.archgate' version '0.1.0'
}

archgate {
    rootPackage = 'com.acme'
    failOn = 'WARNING'
    skip = false
    modules(':api', ':domain')
}
```

Поддерживаемая версия Gradle у потребителя: 9.7.1 и новее. Более старые версии не тестируются.

- `archgateCheck` (группа `verification`) зависит от `testClasses` и подключена к `check`.
- Задача кэшируется и безопасна для configuration cache. При втором запуске с теми же входами она `UP-TO-DATE`, а попадание в build cache — `FROM-CACHE`.
- Отчёт записывается в `build/reports/archgate/report.txt`. Отчёт в консоль печатается только когда задача выполняется; при `UP-TO-DATE` и `FROM-CACHE` он не воспроизводится, так что в этих случаях читай файл отчёта.
- Упавшая проверка бросает `GradleException`, в сообщении которого отчёт или причина, по которой правило не удалось проверить.

### Приоритеты

Оба плагина следуют одному правилу: значение из файла сборки сильнее, а свойство командной строки — лишь значение по умолчанию для параметра, который файл сборки не задаёт.

| Параметр | Порядок, от высшего приоритета |
|---|---|
| `rootPackage` | `archgate { rootPackage = … }`, затем `-Parchgate.rootPackage`; без значения сборка падает |
| `failOn` | `archgate { failOn = … }`, затем `-Parchgate.failOn`, затем `WARNING` |
| `skip` | `archgate { skip = … }`, затем `-Parchgate.skip`, затем `false` |

Maven ведёт себя так же: `<configuration>` в POM сильнее `-Darchgate.rootPackage`, `-Darchgate.failOn` и `-Darchgate.skip`. Чтобы управлять параметром из командной строки, не задавай его в файле сборки.

### `rootPackage` обязателен

В проекте, который не пропущен, незаданный `rootPackage` роняет сборку. Модуль, который только отдаёт свои классы проверке в другом модуле, задаёт:

```groovy
archgate {
    skip = true
}
```

### Выбор модулей

Без `modules` проверяется только текущий проект. Это намеренно отличается от Maven: в Gradle нет дешёвого и корректного способа добраться до тестовых классов соседних проектов. Перечисли нужные проекты явно:

```groovy
archgate {
    rootPackage = 'com.acme'
    modules(':api', ':domain')
}
```

- Каждый проект из `modules(...)` должен сам подключать `dev.quokkify.archgate`, обычно с `skip = true`. Иначе задача падает с сообщением, содержащим `does not apply dev.quokkify.archgate`.
- Такой проект отдаёт каталоги классов (main и test), каталоги их обработанных ресурсов (правила для файлов services читают из них `META-INF/services`) и каталоги исходников через consumable-конфигурацию, а проект с проверкой их читает.
- Известное ограничение: каталоги исходников соседних проектов фиксируются на этапе конфигурации. Каталог, добавленный в соседний проект после завершения его конфигурации, проверка не увидит.

### Загрузка классов

Правила выполняются в воркере Gradle с изоляцией class loader. Classpath воркера — собственные `core` и `api` плагина, а затем `testRuntimeClasspath` проекта, поэтому `api` плагина приоритетнее копии в classpath проекта.

## Maven

| Параметр | По умолчанию | Свойство |
|---|---|---|
| `rootPackage` | обязателен | `archgate.rootPackage` |
| `failOn` | `WARNING` | `archgate.failOn` |
| `modules` | текущий модуль и reactor-модули, от которых он напрямую зависит, кроме модулей с упаковкой `pom` | — |
| `skip` | `false` | `archgate.skip` |

- Минимальная версия Maven — 3.9.0.
- Цель выполняется на `verify`, в том числе с `-DskipTests`.
- Отчёт также записывается в `target/archgate/report.txt`.
- Упавшая проверка роняет сборку через `MojoFailureException`; ошибка регистрации и другие случаи «не удалось проверить» — через `MojoExecutionException`.
- `modules` полностью заменяет выбор по умолчанию. Записи — `artifactId` reactor-модулей. Текущий модуль проверяется, только если ты его перечислил; неизвестная запись роняет сборку.

## Выравнивание версий

Импортируй `archgate-bom`, чтобы все артефакты ArchGate брались в той же версии, что и плагин. В class loader плагина его собственный `archgate-api` имеет приоритет.

Maven:

```xml
<dependencyManagement>
  <dependencies>
    <dependency>
      <groupId>dev.quokkify.archgate</groupId>
      <artifactId>archgate-bom</artifactId>
      <version>0.1.0</version>
      <type>pom</type>
      <scope>import</scope>
    </dependency>
  </dependencies>
</dependencyManagement>
```

Gradle:

```groovy
dependencies {
    testImplementation platform('dev.quokkify.archgate:archgate-bom:0.1.0')
}
```

### Библиотеки правил

Jar с правилами указывает в `META-INF/MANIFEST.MF` версию `archgate-api`, против которой он собран.

Maven:

```xml
<plugin>
  <groupId>org.apache.maven.plugins</groupId>
  <artifactId>maven-jar-plugin</artifactId>
  <configuration>
    <archive>
      <manifestEntries>
        <Archgate-Api-Version>0.1.0</Archgate-Api-Version>
      </manifestEntries>
    </archive>
  </configuration>
</plugin>
```

Gradle:

```groovy
tasks.named('jar') {
    manifest {
        attributes('Archgate-Api-Version': '0.1.0')
    }
}
```

Так же свою версию указывают `archgate-api` и каждый jar `archgate-rules-*`. Прежде чем создать хоть одно правило, движок сравнивает значение каждой библиотеки с выполняющимся `archgate-api`. Если библиотеке нужен более новый API или значение не является версией, сборка падает и называет jar. Обоснование — в [ADR 0002](adr/0002-api-version-manifest.md).

- Формат: `X.Y.Z` или `dev-SNAPSHOT`. `dev-SNAPSHOT` считается новее любого релиза. Релизные сборки ArchGate записывают версию релиза, любая `-SNAPSHOT`-сборка — `dev-SNAPSHOT`.
- Плагин новее этого значения продолжает работать. Прежде чем поднять значение, пройди чек-лист ниже.
- Правило проверяется вместе с суперклассами, поэтому подкласс встроенного правила в твоём модуле всё равно проверяет jar `archgate-rules-*`, от которого он унаследован. Jar без атрибута не проверяются.

### Повышение archgate-api в библиотеке правил

Библиотека правил указывает версию `archgate-api`, против которой собрана, поэтому её повышение поднимает минимальную версию плагина у всех потребителей. Подтверждай обновление (например, галочку в Renovate Dependency Dashboard), только если выполнены все пункты:

- [ ] В [release notes](https://github.com/quokkify/archgate/releases) между текущей и новой версией нет удалённых или изменённых методов `archgate-api`, которые используют правила библиотеки.
- [ ] Все потребители библиотеки уже используют плагин ArchGate новой версии или новее либо готовы поднять его вместе с библиотекой; более старый плагин упадёт с «need a newer ArchGate API».
- [ ] Сборка самой библиотеки зелёная на ветке обновления.

Если какой-то пункт не выполнен, не подтверждай обновление: пока библиотека остаётся на текущей версии, ничего не ломается.

## Регистрация

Файл services `src/test/resources/META-INF/services/dev.quokkify.archgate.api.ArchitectureRule` содержит полный список правил, которые запускаются. Перед запуском любого правила движок проверяет этот список. Сборка падает в таких случаях:

| Условие | Что называет сообщение |
|---|---|
| В твоём модуле есть публичное неабстрактное правило с конструктором без аргументов, но в списке его нет | класс и строку, которую нужно добавить |
| Файл отсутствует или пуст | ожидаемый путь к файлу |
| Класс из списка не существует, абстрактный или не создаётся | строку и причину |
| Для backend, который нужен правилу, не зарегистрирован провайдер контекста | артефакт `archgate-context-<backend>`, который нужно добавить |

Правила из библиотек, например из `archgate-rules-archunit`, запускаются, только если ты их перечислил.

Вложенные классы правил тоже учитываются. Вложенный `public static` класс правила проверяется так же, как верхнеуровневый, и должен быть перечислен по бинарному имени, с `$` перед вложенным именем: `com.acme.tests.arch.Rules$NoFooRule`.

## Многомодульные проекты

Помести проверку в отдельный последний модуль с именем `<project>-archgate-rules`, где префикс — имя проекта. Например, для проекта `platform` используй `platform-archgate-rules`. Он зависит от проверяемых модулей и содержит плагин, файл services и собственные правила проекта.

```
platform/
├── api/
├── tests/
└── platform-archgate-rules/       depends on api and tests
    ├── pom.xml | build.gradle     plugin + archgate-rules-*
    └── src/test/
        ├── java/…/rules/          project rules
        └── resources/META-INF/services/dev.quokkify.archgate.api.ArchitectureRule
```

- В модуле проверки может быть только `src/test`.
- Проверяемые модули должны быть собраны, иначе сборка падает.
- Maven: `target/classes` каждого выбранного модуля импортируется как MAIN, а `target/test-classes` как TEST. Плагин находит их через Maven reactor.
- Gradle: перечисли проверяемые проекты в `modules(...)`, см. [Выбор модулей](#выбор-модулей).

## Всегда падает, что бы ни стояло в `failOn`

- У выбранного модуля нет скомпилированных классов. Сам модуль проверки исключение.
- В срезе MAIN или TEST нет ни одного класса внутри `rootPackage`, а правилу этот срез нужен.
- Запись в `modules` не входит в сборку (Maven: не входит в reactor; Gradle: не подключает плагин).
- Ошибка регистрации, описанная выше.
- Правило выбросило что-то, кроме нарушения.

## Как читать отчёт

```
============================================================
Architecture verification (2 rules, 8.1 s total)
============================================================
[PASS]  MAIN+TEST     41 ms  No Thread.sleep
[ERROR] TEST          18 ms  Tests do not call RestAssured directly
    Tests call RestAssured only through steps
    Violations (1):
        - Method <com.acme.tests.LoginTest.login()> calls method <io.restassured.RestAssured.given()> in (LoginTest.java:21)
Model build: ArchUnitContext 0 ms, main 380 ms, test 210 ms
============================================================
Gate FAILED: 2 rules, 1 error
Failure policy (failOn=WARNING):
  ERROR    fails the build
  WARNING  fails the build
  INFO     reported only
```

В каждой строке видны статус, срезы классов, которые проверило правило, время и название. Прошедшие правила идут первыми, находки — в конце, чтобы нарушения было легко найти при прокрутке. Нарушения перечисляются под правилом. Правила выполняются параллельно, результаты отсортированы по названию правила. Строка `Model build:` показывает, сколько времени заняла сборка каждой модели анализа. Строка вердикта повторяет счётчики, которые его определили, а таблица политики отказов показывает для каждой severity, роняет ли она сборку при текущем `failOn`.

Maven и Gradle печатают один и тот же текст для одних и тех же исходников, правил и `failOn`.
