#!/bin/sh
set -eu

marker=$1
authorized_keys="$HOME/.ssh/authorized_keys"

if [ -f "$authorized_keys" ]; then
    sed -i "\| $marker$|d" "$authorized_keys"
fi
