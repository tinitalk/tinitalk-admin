#!/bin/sh
set -u

server_address=$1
ssh_port=$2

print_status() {
    if "$2"; then
        printf '%s=yes\n' "$1"
    else
        printf '%s=no\n' "$1"
    fi
}

system_packages_ready() {
    command -v snap >/dev/null 2>&1 &&
        command -v ufw >/dev/null 2>&1 &&
        [ -x /usr/local/bin/certbot ] &&
        systemctl is-active --quiet systemd-timesyncd
}

firewall_rule_present() {
    ufw status | awk -v rule="$1" '
        $1 == rule && $2 == "ALLOW" { found = 1 }
        END { exit !found }
    '
}

firewall_ready() {
    command -v ufw >/dev/null 2>&1 &&
        ufw status | grep -q '^Status: active$' &&
        ufw status verbose | grep -q '^Default: deny (incoming), allow (outgoing)' &&
        firewall_rule_present "$ssh_port/tcp" &&
        firewall_rule_present "80/tcp" &&
        firewall_rule_present "443/tcp" &&
        firewall_rule_present "3478/tcp" &&
        firewall_rule_present "3478/udp" &&
        firewall_rule_present "5349/tcp" &&
        firewall_rule_present "49152:49663/udp"
}

certificate_ready() {
    [ -s "/etc/letsencrypt/live/$server_address/fullchain.pem" ] &&
        [ -s "/etc/letsencrypt/live/$server_address/privkey.pem" ]
}

prepare_ready() {
    id tinitalk >/dev/null 2>&1 &&
        [ -d /var/lib/tinitalk ] &&
        [ -s /var/lib/tinitalk/tls/fullchain.pem ] &&
        [ -s /var/lib/tinitalk/tls/privkey.pem ]
}

files_ready() {
    [ -x /usr/local/bin/tinitalk ] && {
        [ -s /var/lib/tinitalk/state.db ] || {
            [ -s /var/lib/tinitalk/google-services.json ] &&
                [ -s /var/lib/tinitalk/firebase-service-account.json ]
        }
    }
}

service_ready() {
    [ -s /var/lib/tinitalk/state.db ] &&
        systemctl cat tinitalk.service >/dev/null 2>&1 &&
        systemctl is-enabled --quiet tinitalk.service 2>/dev/null &&
        systemctl is-active --quiet tinitalk.service
}

print_status system_packages system_packages_ready
print_status firewall firewall_ready
print_status tls_certificate certificate_ready
print_status prepare_tinitalk prepare_ready
print_status upload_files files_ready
print_status start_tinitalk service_ready

if [ -x /usr/local/bin/tinitalk ]; then
    echo "binary=present"
else
    echo "binary=missing"
fi

if id tinitalk >/dev/null 2>&1; then
    echo "user=present"
else
    echo "user=missing"
fi

if [ -d /var/lib/tinitalk ]; then
    echo "data=present"
else
    echo "data=missing"
fi

if [ -s /var/lib/tinitalk/state.db ]; then
    echo "state=present"
else
    echo "state=missing"
fi

if [ -s /var/lib/tinitalk/tls/fullchain.pem ] &&
   [ -s /var/lib/tinitalk/tls/privkey.pem ]; then
    echo "tls=present"
else
    echo "tls=missing"
fi

if systemctl cat tinitalk.service >/dev/null 2>&1; then
    echo "service=present"
else
    echo "service=missing"
fi

if systemctl is-enabled --quiet tinitalk.service 2>/dev/null; then
    echo "enabled=yes"
else
    echo "enabled=no"
fi

if systemctl is-active --quiet tinitalk.service; then
    echo "running=yes"
else
    echo "running=no"
fi
