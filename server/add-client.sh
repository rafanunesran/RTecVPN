#!/usr/bin/env bash
# Cria um cliente (peer) e mostra o QR code para importar no app.
#   sudo bash add-client.sh <nome>
set -euo pipefail

NAME="${1:-}"
WG_DIR="/etc/wireguard"
DNS="${DNS:-1.1.1.1, 9.9.9.9}"

if [[ $EUID -ne 0 ]]; then echo "Rode como root." >&2; exit 1; fi
if [[ ! "$NAME" =~ ^[A-Za-z0-9_-]+$ ]]; then
  echo "Uso: $0 <nome> (letras, números, - e _)" >&2; exit 1
fi
# shellcheck source=/dev/null
source "$WG_DIR/rtecvpn.env"

CONF="$WG_DIR/clients/$NAME.conf"
if [[ -f "$CONF" ]]; then echo "Cliente '$NAME' já existe: $CONF" >&2; exit 1; fi

# Próximo IP livre na rede /24
USED="$(grep -hoE "${WG_NET//./\\.}\.[0-9]+" "$WG_DIR/$WG_IF.conf" | awk -F. '{print $4}' | sort -n)"
IP=""
for n in $(seq 2 254); do
  if ! grep -qx "$n" <<<"$USED"; then IP="$n"; break; fi
done
if [[ -z "$IP" ]]; then echo "Sem IPs livres." >&2; exit 1; fi

umask 077
CLIENT_KEY="$(wg genkey)"
CLIENT_PUB="$(wg pubkey <<<"$CLIENT_KEY")"
PSK="$(wg genpsk)"
SERVER_PUB="$(cat "$WG_DIR/server.pub")"

cat >> "$WG_DIR/$WG_IF.conf" <<EOF

# cliente: ${NAME}
[Peer]
PublicKey = ${CLIENT_PUB}
PresharedKey = ${PSK}
AllowedIPs = ${WG_NET}.${IP}/32
EOF

cat > "$CONF" <<EOF
[Interface]
PrivateKey = ${CLIENT_KEY}
Address = ${WG_NET}.${IP}/32
DNS = ${DNS}

[Peer]
PublicKey = ${SERVER_PUB}
PresharedKey = ${PSK}
Endpoint = ${PUBLIC_IP}:${WG_PORT}
AllowedIPs = 0.0.0.0/0
PersistentKeepalive = 25
EOF

# Aplica sem derrubar quem já está conectado
wg syncconf "$WG_IF" <(wg-quick strip "$WG_IF")

echo "Cliente '$NAME' criado com IP ${WG_NET}.${IP}. Config em $CONF"
echo "Escaneie no app RTecVPN (ou no app oficial do WireGuard):"
qrencode -t ansiutf8 < "$CONF"
