#!/bin/sh
set -eu

upload_dir=$1
server_address=$2

candidate="$upload_dir/tinitalk"
service=tinitalk.service
binary=/usr/local/bin/tinitalk
data_dir=/var/lib/tinitalk
database="$data_dir/state.db"
backup_root=/var/backups/tinitalk
backup_dir="$backup_root/previous"

service_stopped=false
backup_ready=false
replacement_started=false
finished=false

health_check() {
    response=$(curl \
        --fail \
        --silent \
        --noproxy '*' \
        --connect-to "$server_address:443:127.0.0.1:443" \
        --connect-timeout 3 \
        --max-time 5 \
        "https://$server_address/healthz") || return 1

    printf '%s' "$response" | grep -q '"service":"tinitalk"' &&
        printf '%s' "$response" | grep -q '"status":"ok"'
}

wait_for_health() {
    attempts=0
    while [ "$attempts" -lt 15 ]; do
        if systemctl is-active --quiet "$service" && health_check; then
            return 0
        fi
        attempts=$((attempts + 1))
        sleep 2
    done
    return 1
}

restore_on_failure() {
    status=$?
    trap - EXIT HUP INT TERM

    if [ "$finished" = true ]; then
        exit "$status"
    fi

    if [ "$service_stopped" = true ]; then
        systemctl stop "$service" >/dev/null 2>&1 || true

        if [ "$replacement_started" = true ] && [ "$backup_ready" = true ]; then
            if ! install -m 0755 "$backup_dir/tinitalk" "$binary" ||
               ! install -o tinitalk -g tinitalk -m 0600 \
                    "$backup_dir/state.db" "$database"; then
                exit 60
            fi
            rm -f "$database-journal" "$database-shm" "$database-wal"
        elif [ "$backup_ready" = false ]; then
            rm -rf "$backup_dir"
        fi

        if ! systemctl start "$service"; then
            exit 60
        fi
        if [ "$replacement_started" = true ] && ! wait_for_health; then
            exit 60
        fi
    fi

    exit "$status"
}

trap restore_on_failure EXIT
trap 'exit 1' HUP INT TERM

# Check the selected binary before stopping the running server.
test -s "$candidate" || exit 30
test -x "$binary" || exit 30
test -s "$database" || exit 30
chmod 0711 "$upload_dir"
chmod 0755 "$candidate"
timeout 10s runuser -u tinitalk -- "$candidate" --help >/dev/null 2>&1 || exit 30

# Install curl before the downtime if this older server does not have it yet.
if ! command -v curl >/dev/null 2>&1; then
    apt-get update || exit 10
    DEBIAN_FRONTEND=noninteractive apt-get install -y curl || exit 10
fi

# Check space for one previous binary and database, with a safety margin.
install -d -o root -g tinitalk -m 0710 "$backup_root"
database_kib=$(du -k "$database" | awk '{print $1}')
binary_kib=$(du -k "$binary" | awk '{print $1}')
previous_kib=$(du -sk "$backup_dir" 2>/dev/null | awk '{print $1}')
[ -n "$previous_kib" ] || previous_kib=0

backup_free_kib=$(df -Pk "$backup_root" | awk 'END {print $4}')
backup_free_kib=$((backup_free_kib + previous_kib))
backup_size_kib=$((database_kib + binary_kib))
safety_kib=$((backup_size_kib / 5))
if [ "$safety_kib" -lt 65536 ]; then
    safety_kib=65536
fi
required_kib=$((backup_size_kib + safety_kib))
if [ "$backup_free_kib" -lt "$required_kib" ]; then
    printf 'insufficient space: required=%s KiB available=%s KiB\n' \
        "$required_kib" "$backup_free_kib" >&2
    exit 20
fi

# Stop the service and create a verified backup with the old binary.
service_stopped=true
systemctl stop "$service" || exit 40
if systemctl is-active --quiet "$service"; then
    exit 40
fi

rm -rf "$backup_dir"
install -d -o tinitalk -g tinitalk -m 0700 "$backup_dir"
if ! runuser -u tinitalk -- "$binary" backup \
    --data-dir "$data_dir" \
    --out "$backup_dir/state.db"; then
    exit 40
fi
if ! install -m 0700 "$binary" "$backup_dir/tinitalk"; then
    exit 40
fi
chown root:root "$backup_dir" "$backup_dir/state.db" "$backup_dir/tinitalk"
chmod 0700 "$backup_dir" "$backup_dir/tinitalk"
chmod 0600 "$backup_dir/state.db"
backup_ready=true

# Replace the binary and start the new version.
replacement_started=true
install -m 0755 "$candidate" "$binary" || exit 50
systemctl start "$service" || exit 50
wait_for_health || exit 50

finished=true
service_stopped=false
trap - EXIT HUP INT TERM
