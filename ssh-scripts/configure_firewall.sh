#!/bin/sh
set -eu

ssh_port=$1
relay_ports=49152-49663
ufw_relay_ports=49152:49663
turn_udp_read_buffer=4194304
rmem_max=$(sysctl -n net.core.rmem_max)
if [ "$rmem_max" -lt "$turn_udp_read_buffer" ]; then
    rmem_max=$turn_udp_read_buffer
fi

# Keep the TURN relay ports free from outgoing system connections.
reserved_ports=$(sysctl -n net.ipv4.ip_local_reserved_ports)
case ",$reserved_ports," in
    *,"$relay_ports",*) ;;
    *) reserved_ports="${reserved_ports:+$reserved_ports,}$relay_ports" ;;
esac

printf 'net.ipv4.ip_local_reserved_ports=%s\nnet.core.rmem_max=%s\n' \
    "$reserved_ports" "$rmem_max" \
    > /etc/sysctl.d/99-tinitalk.conf
sysctl -q -w "net.ipv4.ip_local_reserved_ports=$reserved_ports"
sysctl -q -w "net.core.rmem_max=$rmem_max"

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
