#!/usr/bin/env bash
# Compila o openvpn para macOS (binário universal arm64 + x86_64, OpenSSL/LZO/LZ4 estáticos)
# para ir dentro do RTecVPN.app, sem precisar de Homebrew.
set -euo pipefail
HERE="$(cd "$(dirname "$0")" && pwd)"
# shellcheck source=desktop/scripts/versions.env
. "$HERE/versions.env"
OUT="$HERE/../src-tauri/resources/macos/openvpn"
W="${WORK:-$HERE/build-macos}"
MIN=11.0
JOBS="$(sysctl -n hw.ncpu)"

mkdir -p "$W/src"
cd "$W/src"
get() { [ -f "$2" ] || curl -fsSL --retry 5 -o "$2" "$1"; tar xzf "$2"; }
get "https://github.com/openssl/openssl/releases/download/openssl-$OPENSSL_VERSION/openssl-$OPENSSL_VERSION.tar.gz" openssl.tgz
get "https://www.oberhumer.com/opensource/lzo/download/lzo-$LZO_VERSION.tar.gz" lzo.tgz
get "https://github.com/lz4/lz4/releases/download/v$LZ4_VERSION/lz4-$LZ4_VERSION.tar.gz" lz4.tgz
get "https://swupdate.openvpn.org/community/releases/openvpn-$OPENVPN_VERSION.tar.gz" openvpn.tgz

bins=()
for arch in arm64 x86_64; do
  host=$([ "$arch" = arm64 ] && echo aarch64-apple-darwin || echo x86_64-apple-darwin)
  P="$W/$arch"
  B="$W/build-$arch"
  rm -rf "$B" && mkdir -p "$B" "$P/include" "$P/lib"
  flags="-arch $arch -mmacosx-version-min=$MIN -O2"

  cp -R "$W/src/openssl-$OPENSSL_VERSION" "$B/openssl"
  (cd "$B/openssl" && ./Configure "darwin64-$arch-cc" no-shared no-tests no-docs --prefix="$P" --libdir=lib "-mmacosx-version-min=$MIN" \
     && make -j"$JOBS" >/dev/null && make install_sw >/dev/null)

  cp -R "$W/src/lzo-$LZO_VERSION" "$B/lzo"
  (cd "$B/lzo" && CFLAGS="$flags" ./configure --host="$host" --prefix="$P" --disable-shared >/dev/null \
     && make -j"$JOBS" >/dev/null && make install >/dev/null)

  cp -R "$W/src/lz4-$LZ4_VERSION" "$B/lz4"
  (cd "$B/lz4/lib" && make -j"$JOBS" liblz4.a CFLAGS="$flags" >/dev/null \
     && cp liblz4.a "$P/lib/" && cp lz4.h lz4hc.h lz4frame.h "$P/include/")

  cp -R "$W/src/openvpn-$OPENVPN_VERSION" "$B/openvpn"
  (cd "$B/openvpn" && CFLAGS="$flags" \
     OPENSSL_CFLAGS="-I$P/include" OPENSSL_LIBS="-L$P/lib -lssl -lcrypto" \
     LZO_CFLAGS="-I$P/include" LZO_LIBS="-L$P/lib -llzo2" \
     LZ4_CFLAGS="-I$P/include" LZ4_LIBS="-L$P/lib -llz4" \
     ./configure --host="$host" --disable-plugins --disable-pkcs11 --disable-debug >/dev/null \
     && make -j"$JOBS" >/dev/null)
  bins+=("$B/openvpn/src/openvpn/openvpn")
done

mkdir -p "$(dirname "$OUT")"
lipo -create "${bins[@]}" -output "$OUT"
codesign --force --sign - "$OUT"
lipo -info "$OUT"
"$OUT" --version | head -1 || true
