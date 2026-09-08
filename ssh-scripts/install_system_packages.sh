#!/bin/sh
set -eu

# Install required system packages.
apt-get update
DEBIAN_FRONTEND=noninteractive apt-get install -y \
    curl logrotate procps snapd systemd-timesyncd ufw

# Enable automatic time synchronization.
systemctl enable --now systemd-timesyncd
timedatectl set-ntp true

# Install Certbot from the official snap package.
snap list snapd >/dev/null 2>&1 || snap install snapd
snap list certbot >/dev/null 2>&1 || snap install --classic certbot

ln -sf /snap/bin/certbot /usr/local/bin/certbot
