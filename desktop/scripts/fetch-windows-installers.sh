#!/usr/bin/env bash
# Baixa os instaladores oficiais do OpenVPN e do WireGuard para irem dentro do RTecVPN
# para Windows. O app instala em silêncio (um pedido de UAC) na primeira conexão.
set -euo pipefail
HERE="$(cd "$(dirname "$0")" && pwd)"
# shellcheck source=desktop/scripts/versions.env
. "$HERE/versions.env"
OUT="$HERE/../src-tauri/resources/windows"
mkdir -p "$OUT"
curl -fsSL --retry 5 -o "$OUT/openvpn.msi" "https://swupdate.openvpn.org/community/releases/$OPENVPN_MSI"
curl -fsSL --retry 5 -o "$OUT/wireguard.msi" "https://download.wireguard.com/windows-client/$WIREGUARD_MSI"
ls -la "$OUT"
