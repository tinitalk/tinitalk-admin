#!/bin/sh
set -eu

server_address=$1

# Create the TiniTalk system user.
if ! id tinitalk >/dev/null 2>&1; then
    useradd \
        --system \
        --home /var/lib/tinitalk \
        --shell /usr/sbin/nologin \
        tinitalk
fi

# Create the TiniTalk directories.
install -d -o tinitalk -g tinitalk -m 0700 /var/lib/tinitalk
install -d -o tinitalk -g tinitalk -m 0700 /var/lib/tinitalk/tls

# Copy the TLS certificate if it already exists.
certificate_dir="/etc/letsencrypt/live/$server_address"

if [ -f "$certificate_dir/fullchain.pem" ] &&
   [ -f "$certificate_dir/privkey.pem" ]; then
    install -m 0644 -o tinitalk -g tinitalk \
        "$certificate_dir/fullchain.pem" \
        /var/lib/tinitalk/tls/fullchain.pem

    install -m 0600 -o tinitalk -g tinitalk \
        "$certificate_dir/privkey.pem" \
        /var/lib/tinitalk/tls/privkey.pem
fi
