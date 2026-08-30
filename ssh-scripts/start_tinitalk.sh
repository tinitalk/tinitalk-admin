#!/bin/sh
set -eu

server_address=$1
public_ipv4=$2
data_dir=/var/lib/tinitalk
service_account=$data_dir/firebase-service-account.json
android_config=$data_dir/google-services.json

test -x /usr/local/bin/tinitalk
test -s $data_dir/tls/fullchain.pem
test -s $data_dir/tls/privkey.pem
systemctl stop tinitalk.service >/dev/null 2>&1 || true

# Import Firebase configuration during the first initialization.
if [ -f "$service_account" ] || [ -f "$android_config" ]; then
    test -s "$service_account"
    test -s "$android_config"

    runuser -u tinitalk -- /usr/local/bin/tinitalk init \
        --data-dir "$data_dir" \
        --fcm-service-account "$service_account" \
        --firebase-android-config "$android_config"

    rm -f "$service_account" "$android_config"
elif [ ! -s "$data_dir/state.db" ]; then
    echo "TiniTalk configuration files are missing" >&2
    exit 1
fi

# Configure the TiniTalk system service.
cat > /etc/systemd/system/tinitalk.service <<EOF
[Unit]
Description=TiniTalk self-hosted audio calls
After=network-online.target
Wants=network-online.target

[Service]
Type=simple
User=tinitalk
Group=tinitalk
WorkingDirectory=/var/lib/tinitalk
ExecStart=/usr/local/bin/tinitalk serve --data-dir /var/lib/tinitalk --addr :443 --tls-cert /var/lib/tinitalk/tls/fullchain.pem --tls-key /var/lib/tinitalk/tls/privkey.pem --turn-public-host $server_address --turn-public-ip $public_ipv4 --turn-addr :3478 --turn-tls-addr :5349
Restart=always
RestartSec=3
LimitNOFILE=4096
NoNewPrivileges=true
PrivateTmp=true
ProtectSystem=strict
ProtectHome=true
ReadWritePaths=/var/lib/tinitalk
AmbientCapabilities=CAP_NET_BIND_SERVICE
CapabilityBoundingSet=CAP_NET_BIND_SERVICE

[Install]
WantedBy=multi-user.target
EOF

# Start TiniTalk now and after every server reboot.
systemctl daemon-reload
systemctl enable --now tinitalk.service
systemctl is-active --quiet tinitalk.service
