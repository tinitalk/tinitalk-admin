#!/bin/sh
set -eu

address_type=$1
address=$2

# Obtain a certificate for the server address.
case "$address_type" in
    domain)
        certbot certonly \
            --standalone \
            --non-interactive \
            --agree-tos \
            --register-unsafely-without-email \
            --keep-until-expiring \
            --cert-name "$address" \
            -d "$address"
        ;;
    ip)
        certbot certonly \
            --standalone \
            --non-interactive \
            --agree-tos \
            --register-unsafely-without-email \
            --keep-until-expiring \
            --preferred-profile shortlived \
            --cert-name "$address" \
            --ip-address "$address"
        ;;
    *)
        echo "Unsupported server address type" >&2
        exit 1
        ;;
esac

# Copy renewed certificates to TiniTalk and restart the service.
install -d -m 0755 /etc/letsencrypt/renewal-hooks/deploy
cat > /etc/letsencrypt/renewal-hooks/deploy/tinitalk <<'EOF'
#!/bin/sh
set -eu

[ -d /var/lib/tinitalk/tls ] || exit 0
id tinitalk >/dev/null 2>&1 || exit 0

install -m 0644 -o tinitalk -g tinitalk \
    "$RENEWED_LINEAGE/fullchain.pem" \
    /var/lib/tinitalk/tls/fullchain.pem

install -m 0600 -o tinitalk -g tinitalk \
    "$RENEWED_LINEAGE/privkey.pem" \
    /var/lib/tinitalk/tls/privkey.pem

if systemctl is-active --quiet tinitalk.service; then
    systemctl restart tinitalk.service
fi
EOF

chmod 0755 /etc/letsencrypt/renewal-hooks/deploy/tinitalk

# Copy the new certificate if TiniTalk is already prepared.
RENEWED_LINEAGE="/etc/letsencrypt/live/$address" \
    /etc/letsencrypt/renewal-hooks/deploy/tinitalk

# Verify automatic renewal.
certbot renew --dry-run --no-random-sleep-on-renew
