# TiniTalk Admin

TiniTalk Admin — Android-приложение для настройки и администрирования серверов TiniTalk напрямую по SSH, без отдельного управляющего сервера.

Приложение позволяет:
- добавлять VPS
- выполнять первоначальную установку TiniTalk
- проверять состояние сервера
- управлять пользователями

Для постоянного SSH-доступа используются ключи, защищённые Android Keystore.

## Сборка

Потребуются JDK 17, Android SDK и Make.

Собрать отладочный APK:

```sh
make client
```

Готовый файл появится в `dist/tinitalk-admin-debug.apk`.

Собрать уменьшенный оптимизированный APK:

```sh
make client-min
```

Результат: `dist/tinitalk-admin-min.apk`.
