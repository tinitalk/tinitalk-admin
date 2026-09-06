# Сторонние компоненты и лицензии

Лицензия [0BSD](LICENSE) относится к собственному коду TiniTalk Admin.
Библиотеки, Gradle Wrapper и заимствованные ресурсы сохраняют лицензии своих авторов.

Полные тексты лицензий и уведомлений находятся в
[THIRD_PARTY_NOTICES.txt](app/src/main/assets/THIRD_PARTY_NOTICES.txt).
Этот файл включается во все варианты APK как `assets/THIRD_PARTY_NOTICES.txt`;
его можно прочитать, открыв APK как ZIP-архив.

## Библиотеки приложения

Список сверен 6 сентября 2026 года с разрешённым Gradle-графом
`releaseRuntimeClasspath`: 71 уникальный JAR/AAR, включая транзитивные зависимости.
R8 удаляет неиспользуемый код, поэтому список входов сборки не означает, что
каждый класс или каждая библиотека целиком остаётся в оптимизированном APK.
У `min` тот же набор зависимостей.

В первых двух колонках указаны `group` и `artifact` из Maven-координат
`group:artifact:version`.

| Группа | Артефакты | Версия | Лицензия |
| --- | --- | --- | --- |
| `androidx.activity` | `activity`, `activity-compose`, `activity-ktx` | 1.13.0 | Apache-2.0 |
| `androidx.annotation` | `annotation-experimental` | 1.4.1 | Apache-2.0 |
| `androidx.annotation` | `annotation-jvm` | 1.10.0 | Apache-2.0 |
| `androidx.arch.core` | `core-common`, `core-runtime` | 2.2.0 | Apache-2.0 |
| `androidx.autofill` | `autofill` | 1.0.0 | Apache-2.0 |
| `androidx.collection` | `collection-jvm`, `collection-ktx` | 1.5.0 | Apache-2.0 |
| `androidx.compose.animation` | `animation-android`, `animation-core-android` | 1.12.0 | Apache-2.0 |
| `androidx.compose.foundation` | `foundation-android`, `foundation-layout-android` | 1.12.0 | Apache-2.0 |
| `androidx.compose.material3` | `material3-android` | 1.4.0 | Apache-2.0 |
| `androidx.compose.material` | `material-ripple-android` | 1.12.0 | Apache-2.0 |
| `androidx.compose.runtime` | `runtime-android`, `runtime-annotation-android`, `runtime-retain-android`, `runtime-saveable-android` | 1.12.0 | Apache-2.0 |
| `androidx.compose.ui` | `ui-android`, `ui-geometry-android`, `ui-graphics-android`, `ui-text-android`, `ui-tooling-preview-android`, `ui-unit-android`, `ui-util-android` | 1.12.0 | Apache-2.0 |
| `androidx.concurrent` | `concurrent-futures` | 1.1.0 | Apache-2.0 |
| `androidx.core` | `core`, `core-ktx` | 1.19.0 | Apache-2.0 |
| `androidx.core` | `core-viewtree` | 1.0.0 | Apache-2.0 |
| `androidx.customview` | `customview-poolingcontainer` | 1.0.0 | Apache-2.0 |
| `androidx.emoji2` | `emoji2` | 1.4.0 | Apache-2.0 |
| `androidx.graphics` | `graphics-path` | 1.0.1 | Apache-2.0 |
| `androidx.interpolator` | `interpolator` | 1.0.0 | Apache-2.0 |
| `androidx.lifecycle` | `lifecycle-common-java8`, `lifecycle-common-jvm`, `lifecycle-livedata-core`, `lifecycle-process`, `lifecycle-runtime-android`, `lifecycle-runtime-compose-android`, `lifecycle-runtime-ktx-android`, `lifecycle-viewmodel-android`, `lifecycle-viewmodel-ktx`, `lifecycle-viewmodel-savedstate-android` | 2.11.0 | Apache-2.0 |
| `androidx.navigationevent` | `navigationevent-android`, `navigationevent-compose-android` | 1.0.0 | Apache-2.0 |
| `androidx.profileinstaller` | `profileinstaller` | 1.4.0 | Apache-2.0 |
| `androidx.savedstate` | `savedstate-android`, `savedstate-compose-android`, `savedstate-ktx` | 1.4.0 | Apache-2.0 |
| `androidx.startup` | `startup-runtime` | 1.1.1 | Apache-2.0 |
| `androidx.tracing` | `tracing` | 1.2.0 | Apache-2.0 |
| `androidx.versionedparcelable` | `versionedparcelable` | 1.1.1 | Apache-2.0 |
| `androidx.window` | `window`, `window-core-android` | 1.5.0 | Apache-2.0 |
| `com.google.code.gson` | `gson` | 2.14.0 | Apache-2.0 |
| `com.google.errorprone` | `error_prone_annotations` | 2.48.0 | Apache-2.0 |
| `com.google.guava` | `listenablefuture` | 1.0 | Apache-2.0 |
| `com.hierynomus` | `asn-one` | 0.6.0 | Apache-2.0 |
| `com.hierynomus` | `sshj` | 0.40.0 | Apache-2.0; BSD-3-Clause, MIT и ISC для включённого кода |
| `org.bouncycastle` | `bcpkix-jdk18on`, `bcutil-jdk18on` | 1.85 | MIT |
| `org.bouncycastle` | `bcprov-jdk18on` | 1.85.2 | MIT |
| `org.jetbrains.kotlin` | `kotlin-stdlib` | 2.4.10 | Apache-2.0 |
| `org.jetbrains.kotlinx` | `kotlinx-coroutines-android`, `kotlinx-coroutines-core-jvm` | 1.11.0 | Apache-2.0 |
| `org.jetbrains.kotlinx` | `kotlinx-serialization-core-jvm` | 1.7.3 | Apache-2.0 |
| `org.jetbrains` | `annotations` | 23.0.0 | Apache-2.0 |
| `org.jspecify` | `jspecify` | 1.0.0 | Apache-2.0 |
| `org.slf4j` | `slf4j-api` | 2.0.17 | MIT |

У `debug` дополнительно используются `androidx.compose.ui:ui-tooling-android`
и `androidx.compose.ui:ui-tooling-data-android` версии 1.12.0 (Apache-2.0).

BOM задают версии, но не добавляют код в APK:
`androidx.compose:compose-bom:2026.08.00`,
`org.bouncycastle:bc-jdk18on-bom:1.85.2`,
`org.jetbrains.kotlinx:kotlinx-coroutines-bom:1.11.0`,
`org.jetbrains.kotlinx:kotlinx-serialization-bom:1.7.3`.
Остальные узлы без JAR/AAR — метаданные выбора платформенных вариантов.

### Источники и уведомления

Версии и лицензии сверены по опубликованным POM, JAR/AAR и исходным
`-sources.jar` соответствующих координат:
[Google Maven](https://dl.google.com/dl/android/maven2/index.html) для AndroidX,
[Maven Central](https://repo.maven.apache.org/maven2/) для остальных библиотек.
Путь к публикации: `<group с заменой точек на />/<artifact>/<version>/`;
например,
[SSHJ 0.40.0](https://repo.maven.apache.org/maven2/com/hierynomus/sshj/0.40.0/).

- AndroidX, Compose и Material 3 — Android Open Source Project, Apache-2.0;
  [исходники AndroidX](https://android.googlesource.com/platform/frameworks/support/).
  `graphics-path` также поставляет нативную `libandroidx.graphics.path.so`;
  её C/C++-исходники версии 1.0.1 имеют ту же лицензию:
  [ревизия выпуска](https://android.googlesource.com/platform/frameworks/support/+/8a05a22af450d589ef911d772a001a49dcb05b71/graphics/graphics-path/).
- Kotlin, coroutines, serialization и JetBrains annotations — JetBrains и
  участники проектов, Apache-2.0. Компилятор Kotlin не входит в APK.
- Gson, Error Prone annotations и Guava ListenableFuture — Google и участники
  проектов; JSpecify — участники JSpecify. Лицензия Apache-2.0.
- [ASN.1 library](https://github.com/hierynomus/asn-one/tree/v0.6.0) — Apache-2.0.
- [SSHJ 0.40.0](https://github.com/hierynomus/sshj/tree/v0.40.0) — Apache-2.0.
  Его [NOTICE](https://github.com/hierynomus/sshj/blob/v0.40.0/NOTICE) содержит
  уведомления Apache MINA SSHD, Apache Commons Net, JCraft и Bouncy Castle.
  [BCrypt.java](https://github.com/hierynomus/sshj/blob/v0.40.0/src/main/java/com/hierynomus/sshj/userauth/keyprovider/bcrypt/BCrypt.java)
  содержит лицензию ISC Damien Miller. Эти тексты сохранены отдельно от общей
  Apache-2.0 в файле уведомлений.
- Bouncy Castle — The Legion of the Bouncy Castle Inc., MIT;
  сохранён `META-INF/LICENSE.md` из используемых JAR.
  [Лицензия проекта](https://www.bouncycastle.org/licence.html).
- SLF4J — QOS.ch Sarl, MIT; сохранён `META-INF/LICENSE.txt` из
  `slf4j-api-2.0.17.jar`.

## Ресурсы приложения

Иконки карандаша и перехода вправо, а также телефонная трубка в значке приложения
адаптированы из Google Material Design Icons, Apache-2.0.
Изменены формат векторных путей, оформление и композиция.

Исходные изображения в зафиксированной ревизии
`0cbb08816df07faaae3dca060d4ebb10b66c214f`:

- [Edit](https://github.com/google/material-design-icons/blob/0cbb08816df07faaae3dca060d4ebb10b66c214f/src/image/edit/materialicons/24px.svg)
  — `app/src/main/res/drawable/ic_edit.xml`.
- [Chevron right (round)](https://github.com/google/material-design-icons/blob/0cbb08816df07faaae3dca060d4ebb10b66c214f/src/navigation/chevron_right/materialiconsround/24px.svg)
  — `app/src/main/res/drawable/ic_chevron_right.xml`.
- [Phone](https://github.com/google/material-design-icons/blob/0cbb08816df07faaae3dca060d4ebb10b66c214f/src/communication/phone/materialicons/24px.svg)
  — телефонная трубка в `ic_launcher_foreground.xml` и `ic_launcher_monochrome.xml`.
- [Лицензия Material Design Icons](https://github.com/google/material-design-icons/blob/0cbb08816df07faaae3dca060d4ebb10b66c214f/LICENSE).

Эта ревизия служит ссылкой на проверенные оригиналы и лицензию, а не указанием
даты первоначального заимствования.

## Компоненты исходного репозитория

[Gradle Wrapper 9.7.1](https://github.com/gradle/gradle/tree/v9.7.1) распространяется
вместе с исходниками: `gradlew`, `gradlew.bat`, `gradle/wrapper/gradle-wrapper.jar`.
Лицензия Apache-2.0; исходные уведомления в скриптах и `META-INF/LICENSE` внутри
JAR сохранены. Wrapper нужен для сборки и не входит в APK.

Gradle, Android SDK, JDK, плагины сборки и тестовые зависимости загружаются
отдельно и не являются встроенными компонентами приложения.

## Компоненты VPS

Системные пакеты устанавливаются из репозиториев ОС и Snap Store, а серверный
бинарник выбирает пользователь. Они не включены в APK или исходный архив админки;
см. [README](README.md#компоненты-на-vps).

При изменении зависимостей или ресурсов нужно обновить этот список и тексты
уведомлений по фактическому графу и содержимому нового APK.
