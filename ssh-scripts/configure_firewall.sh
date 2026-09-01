#!/bin/sh
set -eu

ssh_port=$1
relay_ports=49152-49663
ufw_relay_ports=49152:49663

# Keep the TURN relay ports free from outgoing system connections.
reserved_ports=$(sysctl -n net.ipv4.ip_local_reserved_ports)
case ",$reserved_ports," in
    *,"$relay_ports",*) ;;
    *) reserved_ports="${reserved_ports:+$reserved_ports,}$relay_ports" ;;
esac

printf 'net.ipv4.ip_local_reserved_ports=%s\n' "$reserved_ports" \
    > /etc/sysctl.d/99-tinitalk.conf
sysctl -q -w "net.ipv4.ip_local_reserved_ports=$reserved_ports"

ufw default deny incoming
ufw default allow outgoing

ufw allow "$ssh_port/tcp"
ufw allow 80/tcp
ufw allow 443/tcp
ufw allow 3478/tcp
ufw allow 3478/udp
ufw allow 5349/tcp
ufw allow "$ufw_relay_ports/udp"

ufw --force enable
