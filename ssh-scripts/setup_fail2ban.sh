#!/bin/sh
set -eu

ssh_port=$1

# Install Fail2ban with support for systemd logs.
apt-get update
DEBIAN_FRONTEND=noninteractive apt-get install -y --no-install-recommends \
    fail2ban python3-systemd

# Protect SSH from repeated authentication failures.
cat > /etc/fail2ban/jail.d/tinitalk-admin.local <<EOF
[sshd]
enabled = true
backend = systemd
port = $ssh_port
banaction = ufw
maxretry = 5
findtime = 10m
bantime = 1h
EOF

fail2ban-client -t
systemctl enable fail2ban
systemctl restart fail2ban

# Wait until the Fail2ban control socket is ready.
for attempt in 1 2 3 4 5 6 7 8 9 10; do
    fail2ban-client ping >/dev/null 2>&1 && break
    sleep 1
done

fail2ban-client ping >/dev/null
fail2ban-client status sshd >/dev/null
