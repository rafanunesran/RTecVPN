#!/usr/bin/env bash
# Instala e configura um servidor WireGuard para o RTecVPN.
# Testado em Ubuntu 22.04/24.04 e Debian 12. Rode como root:
#   sudo bash install.sh
set -euo pipefail

WG_IF="${WG_IF:-wg0}"
WG_PORT="${WG_PORT:-51820}"
WG_NET="${WG_NET:-10.8.0}"          # rede interna /24 da VPN
WG_DIR="/etc/wireguard"

if [[ $EUID -ne 0 ]]; then
  echo "Rode como root (sudo bash install.sh)." >&2
  exit 1
fi

echo "==> Instalando pacotes"
apt-get update -y
DEBIAN_FRONTEND=noninteractive apt-get install -y wireguard qrencode iptables curl

# Interface de saída para a internet e IP público
WAN_IF="${WAN_IF:-$(ip -4 route show default | awk '{print $5; exit}')}"
PUBLIC_IP="${PUBLIC_IP:-$(curl -4 -fsS https://api.ipify.org || true)}"
if [[ -z "$PUBLIC_IP" ]]; then
  echo "Não consegui descobrir o IP público. Rode com PUBLIC_IP=x.x.x.x" >&2
  exit 1
fi

echo "==> Gerando chaves do servidor"
umask 077
mkdir -p "$WG_DIR/clients"
if [[ ! -f "$WG_DIR/server.key" ]]; then
  wg genkey | tee "$WG_DIR/server.key" | wg pubkey > "$WG_DIR/server.pub"
fi
SERVER_KEY="$(cat "$WG_DIR/server.key")"

if [[ ! -f "$WG_DIR/$WG_IF.conf" ]]; then
  cat > "$WG_DIR/$WG_IF.conf" <<EOF
[Interface]
Address = ${WG_NET}.1/24
ListenPort = ${WG_PORT}
PrivateKey = ${SERVER_KEY}
PostUp = iptables -A FORWARD -i %i -j ACCEPT; iptables -A FORWARD -o %i -j ACCEPT; iptables -t nat -A POSTROUTING -o ${WAN_IF} -j MASQUERADE
PostDown = iptables -D FORWARD -i %i -j ACCEPT; iptables -D FORWARD -o %i -j ACCEPT; iptables -t nat -D POSTROUTING -o ${WAN_IF} -j MASQUERADE
EOF
fi

# Guarda parâmetros para o add-client.sh
cat > "$WG_DIR/rtecvpn.env" <<EOF
WG_IF=${WG_IF}
WG_PORT=${WG_PORT}
WG_NET=${WG_NET}
PUBLIC_IP=${PUBLIC_IP}
EOF

echo "==> Habilitando encaminhamento de IP"
echo "net.ipv4.ip_forward=1" > /etc/sysctl.d/99-rtecvpn.conf
sysctl -p /etc/sysctl.d/99-rtecvpn.conf

# Libera a porta no firewall local (Oracle Cloud usa iptables com REJECT por padrão)
if ! iptables -C INPUT -p udp --dport "$WG_PORT" -j ACCEPT 2>/dev/null; then
  iptables -I INPUT -p udp --dport "$WG_PORT" -j ACCEPT
fi
if command -v netfilter-persistent >/dev/null; then netfilter-persistent save; fi
if command -v ufw >/dev/null && ufw status | grep -q active; then ufw allow "$WG_PORT/udp"; fi

echo "==> Subindo o WireGuard"
systemctl enable --now "wg-quick@${WG_IF}"

echo
echo "Servidor pronto em ${PUBLIC_IP}:${WG_PORT}/udp"
echo "Lembre de liberar a porta ${WG_PORT}/UDP também no painel do provedor (security list)."
echo "Crie um cliente com: sudo bash add-client.sh meu-celular"
