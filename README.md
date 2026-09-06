# TiniTalk Admin

[![CI](https://github.com/tinitalk/tinitalk-admin/actions/workflows/ci.yml/badge.svg?branch=main&event=push)](https://github.com/tinitalk/tinitalk-admin/actions/workflows/ci.yml?query=branch%3Amain)
[![Release](https://img.shields.io/github/v/release/tinitalk/tinitalk-admin?include_prereleases&sort=semver)](https://github.com/tinitalk/tinitalk-admin/releases)

TiniTalk Admin - Android-приложение для настройки и администрирования серверов
[TiniTalk](https://github.com/tinitalk/tinitalk). Приложение подключается к VPS
напрямую и выполняет обычные административные действия без отдельного
управляющего сервера.

Возможности:

- добавление нового или уже настроенного VPS;
- первичная настройка TiniTalk на Debian/Ubuntu VPS;
- проверка состояния сервера и доступности TiniTalk;
- просмотр пользователей;
- добавление, удаление и переименование пользователей;
- смена токена пользователя.

Для работы нужен выделенный VPS с публичным IPv4 или доменом, Android 8.0 или
новее и первоначальный SSH-доступ от `root` либо пользователя с `sudo` без
пароля. Серверный бинарник TiniTalk выбирается из файлов на телефоне; он не
встроен в APK и не скачивается приложением.

## Использование

Добавьте сервер в приложении и запустите первичную настройку. Мастер подготовит
систему, firewall, Fail2ban, TLS-сертификат и systemd-сервис TiniTalk. После
настройки можно создать пользователей и передать им адрес сервера, логин и токен.

Серверные команды находятся в [ssh-scripts](ssh-scripts/). Они написаны как
простые shell-скрипты и выполняются на VPS по SSH.

TiniTalk использует входящие подключения:

- `80/tcp` для выпуска и продления TLS-сертификата;
- `443/tcp` для HTTPS API и сигналинга;
- `3478/tcp` и `3478/udp` для TURN;
- `5349/tcp` для TURN поверх TLS;
- `49152-49663/udp` для TURN relay.

Если у хостера есть отдельный firewall вне VPS, эти порты нужно открыть там
самостоятельно.

## Безопасность

Первоначальный SSH-пароль или импортированный ключ нужны только для добавления
сервера и не сохраняются приложением. После проверки доступа приложение создаёт
свой SSH-ключ в Android Keystore и добавляет публичную часть на VPS.

При изменении SSH host key приложение блокирует административные действия до
повторного добавления сервера. Удаление сервера из приложения удаляет только
локальную запись и ключ на телефоне; данные TiniTalk на VPS не удаляются.

Сохраните независимый доступ к VPS через панель хостера или обычный SSH. При
потере телефона, удалении приложения или очистке его данных локальные SSH-ключи
будут потеряны.

## Сборка

Для сборки нужны Git, JDK 17, GNU Make и Android SDK Platform 37.

```bash
git clone https://github.com/tinitalk/tinitalk-admin.git
cd tinitalk-admin
```

Dev-сборки:

```bash
make client
make client-min
```

- `make client` создаёт `dist/tinitalk-admin-debug.apk`;
- `make client-min` создаёт уменьшенный `dist/tinitalk-admin-min.apk` для ARM64.

Dev-сборки подписываются локальным debug-ключом и не требуют release-ключа.

### Release-сборка

Release APK подписывается постоянным ключом проекта. Ключ нужно создать один раз
до первой публикации и затем использовать для всех следующих версий.

Создайте локальный каталог и сгенерируйте хранилище ключа:

```bash
mkdir keystore
keytool -genkeypair -v -keystore keystore/tinitalk-admin-release.jks -alias tinitalk-admin-release -keyalg RSA -keysize 4096 -validity 36500 -dname "CN=TiniTalk Admin, OU=Release Signing, O=TiniTalk Open Source Project"
```

`keytool` попросит пароль для хранилища ключа.

Создайте файл `keystore/release.properties`:

```properties
storeFile=keystore/tinitalk-admin-release.jks
storePassword=ПАРОЛЬ
keyAlias=tinitalk-admin-release
keyPassword=ПАРОЛЬ
```

Соберите релиз:

```bash
make client-release
```

Результат:

```text
dist/tinitalk-admin-v0.1.0.apk
```

Каталог `keystore` добавлен в `.gitignore`. Файлы `*.jks`,
`release.properties` и пароли не должны попадать в Git.

После создания ключа сохраните резервную копию `tinitalk-admin-release.jks` и
паролей в защищённом месте. Потеря ключа не позволит выпускать обновления для
уже установленного приложения.

## Лицензия

TiniTalk Admin - бесплатное программное обеспечение с открытым исходным кодом.
Лицензия BSD Zero Clause разрешает использовать, копировать, изменять и
распространять проект, в том числе в коммерческих целях. Программное обеспечение
предоставляется без гарантий.

Полный текст лицензии: [LICENSE](LICENSE).

Зависимости распространяются под собственными лицензиями.
Список сторонних компонентов: [THIRD_PARTY_LICENSES.md](THIRD_PARTY_LICENSES.md).
