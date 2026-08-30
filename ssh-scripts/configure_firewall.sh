#!/bin/sh
set -eu

ssh_port=$1

ufw default deny incoming
ufw default allow outgoing

ufw allow "$ssh_port/tcp"
ufw allow 80/tcp
ufw allow 443/tcp
ufw allow 3478/tcp
ufw allow 3478/udp
ufw allow 5349/tcp
ufw allow 49152:49663/udp

ufw --force enable
