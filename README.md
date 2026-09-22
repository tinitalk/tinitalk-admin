# TiniTalk Admin

English | [Русский](README.ru.md)

[![CI](https://github.com/tinitalk/tinitalk-admin/actions/workflows/ci.yml/badge.svg?branch=main&event=push)](https://github.com/tinitalk/tinitalk-admin/actions/workflows/ci.yml?query=branch%3Amain)
[![Release](https://img.shields.io/github/v/release/tinitalk/tinitalk-admin?include_prereleases&sort=semver)](https://github.com/tinitalk/tinitalk-admin/releases)

TiniTalk Admin is an Android app for setting up and managing
[TiniTalk](https://github.com/tinitalk/tinitalk) servers. It connects directly
to your VPS and performs standard administrative tasks without a separate
management server.

Features:

- add a new or an already configured VPS;
- set up TiniTalk on a Debian/Ubuntu VPS;
- check server status and TiniTalk availability;
- view users;
- add, delete, and rename users;
- change a user's password.

You need a dedicated VPS with a public IPv4 address or domain name, Android 8.0
or later, and initial SSH access as `root` or a user with passwordless `sudo`.
The TiniTalk server binary is selected from files on your phone; it is not
bundled with the APK or downloaded by the app.

## Usage

Add your server in the app and start the initial setup. The wizard configures
the system, firewall, Fail2ban, TLS certificate, and TiniTalk systemd service.
Once setup is complete, you can create users and share the server address,
username, and password with them.

Server commands are in [ssh-scripts](ssh-scripts/). They are simple shell
scripts executed on the VPS over SSH.

TiniTalk uses the following inbound ports:

- `80/tcp` for issuing and renewing TLS certificates;
- `443/tcp` for the HTTPS API and signaling;
- `3478/tcp` and `3478/udp` for TURN;
- `5349/tcp` for TURN over TLS;
- `49152-49663/udp` for TURN relay.

If your hosting provider has a separate firewall outside the VPS, you need
to open these ports there yourself.

## Security

The initial SSH password or imported key is only needed when adding a server
and is not saved by the app. After verifying access, the app creates its own
SSH key in Android Keystore and adds the public key to the VPS.

If the SSH host key changes, the app blocks administrative actions until you
remove and re-add the server. Removing a server from the app only deletes
the local record and the key on your phone; it does not delete TiniTalk data
on the VPS.

Keep independent access to your VPS through your hosting provider's control
panel or regular SSH. Losing your phone, uninstalling the app, or clearing
its data will also remove the local SSH keys.

## Building

Building requires Git, JDK 17, GNU Make, and Android SDK Platform 37.

```bash
git clone https://github.com/tinitalk/tinitalk-admin.git
cd tinitalk-admin
```

Development builds:

```bash
make client
make client-min
```

- `make client` creates `dist/tinitalk-admin-debug.apk`;
- `make client-min` creates a smaller `dist/tinitalk-admin-min.apk` for ARM64.

Development builds are signed with a local debug key and do not require
a release key.

### Release build

Release APKs are signed with a permanent project key. Create the key once
before the first release and use it for all subsequent versions.

Create a local directory and generate the keystore:

```bash
mkdir keystore
keytool -genkeypair -v -keystore keystore/tinitalk-admin-release.jks -alias tinitalk-admin-release -keyalg RSA -keysize 4096 -validity 36500 -dname "CN=TiniTalk Admin, OU=Release Signing, O=TiniTalk Open Source Project"
```

`keytool` will prompt you for a keystore password.

Create `keystore/release.properties`:

```properties
storeFile=keystore/tinitalk-admin-release.jks
storePassword=PASSWORD
keyAlias=tinitalk-admin-release
keyPassword=PASSWORD
```

Build the release:

```bash
make client-release
```

Output:

```text
dist/tinitalk-admin-v0.5.0.apk
```

The `keystore` directory is included in `.gitignore`. Do not commit `*.jks`,
`release.properties`, or passwords to Git.

After creating the key, back up `tinitalk-admin-release.jks` and its passwords
in a secure location. Losing the key prevents you from releasing updates
for existing installations.

## License

TiniTalk Admin is free and open-source software. The BSD Zero Clause license
allows you to use, copy, modify, and distribute the project, including
commercially. The software is provided without warranty.

Full license text: [LICENSE](LICENSE).

Dependencies are distributed under their own licenses.
Third-party components: [THIRD_PARTY_LICENSES.md](THIRD_PARTY_LICENSES.md).
