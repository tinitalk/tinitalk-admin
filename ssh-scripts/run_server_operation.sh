#!/bin/sh
set -eu

operation_dir=$1
script=$2
shift 2

trap 'rm -rf "$operation_dir"' EXIT
sh "$script" "$@"
