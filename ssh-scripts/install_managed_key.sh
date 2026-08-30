#!/bin/sh
set -eu

key_file=$1
marker=$2
ssh_dir="$HOME/.ssh"
authorized_keys="$ssh_dir/authorized_keys"

mkdir -p "$ssh_dir"
chmod 700 "$ssh_dir"
touch "$authorized_keys"

sed -i "\| $marker$|d" "$authorized_keys" # Remove the old key with the same marker.
cat "$key_file" >> "$authorized_keys"

chmod 600 "$authorized_keys"
rm -f "$key_file"
