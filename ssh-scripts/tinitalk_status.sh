#!/bin/sh
set -u

os="unknown"
if [ -r /etc/os-release ]; then
    os=$(sed -n 's/^PRETTY_NAME=//p' /etc/os-release | head -n 1 | tr -d '"')
fi
[ -n "$os" ] || os="unknown"

printf 'os=%s\n' "$os"
printf 'architecture=%s\n' "$(uname -m)"

if [ -x /usr/local/bin/tinitalk ]; then
    printf 'binary=installed\n'
else
    printf 'binary=missing\n'
fi

if systemctl is-active --quiet tinitalk.service; then
    printf 'service=running\n'
elif systemctl cat tinitalk.service >/dev/null 2>&1; then
    printf 'service=stopped\n'
else
    printf 'service=missing\n'
fi

if [ -d /var/lib/tinitalk ]; then
    printf 'data=present\n'
else
    printf 'data=missing\n'
fi
