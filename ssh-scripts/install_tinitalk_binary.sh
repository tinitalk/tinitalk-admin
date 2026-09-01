#!/bin/sh
set -eu

upload_dir=$1

# Install the selected TiniTalk server binary.
test -s "$upload_dir/tinitalk"
install -m 0755 "$upload_dir/tinitalk" /usr/local/bin/tinitalk
