#!/bin/sh
set -eu

upload_dir=$1

# Install the TiniTalk binary.
test -s "$upload_dir/tinitalk"
install -m 0755 "$upload_dir/tinitalk" /usr/local/bin/tinitalk

# Install both required Firebase configuration files.
test -s "$upload_dir/google-services.json"
test -s "$upload_dir/firebase-service-account.json"

install -m 0600 -o tinitalk -g tinitalk \
    "$upload_dir/google-services.json" \
    /var/lib/tinitalk/google-services.json

install -m 0600 -o tinitalk -g tinitalk \
    "$upload_dir/firebase-service-account.json" \
    /var/lib/tinitalk/firebase-service-account.json
