# Development

[English](development.md) · Русский

## Требования

- JDK 21. Сборка использует toolchain Java 21 и компилирует с `--release 21`.
- Больше ничего: Gradle wrapper (`./gradlew`) скачивает Gradle, а интеграционные тесты скачивают дистрибутив Maven 3.9.x как зависимость Gradle.

## Локальная сборка

```
./gradlew build
```

`build` запускает `check`, а он включает:

- unit-тесты всех модулей;
- `functionalTest` в `:gradle-plugin`: общие фикстуры прогоняются через настоящие сборки Gradle с TestKit;
- `mavenIT` в `:maven-plugin`: те же фикстуры прогоняются через настоящую сборку Maven с `maven-verifier`;
- `:testing:parity:test`: каждая фикстура, кроме `skip`, запускается и под Maven, и под Gradle; отчёты должны совпасть, а для запуска с пометкой `unableToVerify=true` в `expected.properties` ни один инструмент не должен писать отчёт, и оба должны вывести одну и ту же строку сообщения;
- Spotless (palantir-java-format) и Checkstyle (`config/checkstyle/checkstyle.xml`);
- проверку покрытия JaCoCo (80 % строк) для `:api`, `:core`, `:context-*` и `:rules-*`;
- `DocsLinkTest`: относительные ссылки в Markdown-файлах ведут на существующие файлы, таблицы правил в README называют существующие классы, а `README.md` и `README.ru.md` перечисляют одни и те же правила;
- `denylistHookTest`: pre-push hook блокирует то, что должен.

Исправить форматирование: `./gradlew spotlessApply`.

### Полезные задачи

| Задача | Что делает |
|---|---|
| `./gradlew build` | всё перечисленное выше |
| `./gradlew :gradle-plugin:functionalTest` | плагин Gradle через TestKit |
| `./gradlew :maven-plugin:mavenIT` | плагин Maven через `maven-verifier` |
| `./gradlew :testing:parity:test` | результаты Maven и Gradle сравниваются для каждой фикстуры, кроме `skip` |
| `./gradlew publishToItRepo` | публикует каждый модуль в `build/it-repo`, локальный репозиторий, из которого берут артефакты интеграционные тесты |
| `./gradlew :testing:fixtures-support:test --tests '*DocsLinkTest'` | только проверки документации |

`functionalTest`, `mavenIT` и parity-тест зависят от `publishToItRepo`, поэтому всегда тестируют свежесобранные артефакты.

### Общие фикстуры

В `fixtures/<scenario>/` лежат исходники и файл services одного сценария, а также `fixture.properties` и `expected.properties`. Модуль поддержки тестов генерирует вокруг них `build.gradle` или `pom.xml`, так что один и тот же сценарий выполняется под обоими инструментами. Чтобы добавить сценарий, добавь каталог: его подхватят `functionalTest`, `mavenIT` и parity-тест. Запуск, который должен закончиться ошибкой «невозможно проверить», пометь `run.N.unableToVerify=true`: тогда parity-тест сравнит сообщение, а не отчёт.

## Версия

В `version.txt` лежит последняя выпущенная версия (`0.0.0` до первого релиза). Обычная сборка добавляет `-SNAPSHOT`. Релизная сборка передаёт `-Prelease=true` и использует версию как есть. Jar записывают `Archgate-Api-Version` в манифест: версию релиза для релизных сборок и `dev-SNAPSHOT` для любой `-SNAPSHOT`-сборки.

## Pre-push hook

Репозиторий публичный, поэтому перед каждым push проверяется локальный denylist имён, которых в нём быть не должно. Сам файл denylist локальный и никогда не коммитится.

1. Положи по одному регулярному выражению (extended, без учёта регистра) на строку в файл `info/archgate-denylist` внутри общего каталога git:

   ```
   "$(git rev-parse --git-common-dir)/info/archgate-denylist"
   ```

2. Установи hook:

   ```
   ln -s ../../scripts/pre-push-denylist.sh "$(git rev-parse --git-common-dir)/hooks/pre-push"
   ```

   Общий каталог git используется всеми связанными worktree, так что один denylist и один hook обслуживают их все.

При каждом push hook прогоняет denylist по отправляемому дереву и по отправляемым коммитам (`git log -p`). Совпадение, отсутствие файла denylist или ошибка инструмента блокируют push. Проверить вручную:

```
git grep -niEf "$(git rev-parse --git-common-dir)/info/archgate-denylist"
```

Команда не должна ничего печатать.

## CI

Pull request-ы и push в `main` запускают сборку Gradle на JDK 21. Workflow-ы сгенерированы из общего шаблона; релизные workflow и workflow публикации описаны в [Releasing](releasing.ru.md).
