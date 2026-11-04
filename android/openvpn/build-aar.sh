#!/usr/bin/env bash
# Compila o motor OpenVPN do "OpenVPN for Android" (ics-openvpn, GPLv2) como biblioteca
# Android (.aar), para o RTecVPN conectar nos servidores do VPN Gate sem app extra.
#
# Requisitos: JDK 21, Android SDK (o Gradle baixa o NDK), swig e git.
# Saída: android/app/libs/ics-openvpn.aar
set -euo pipefail

ICS_TAG="${ICS_TAG:-$(cat "$(dirname "$0")/ICS_VERSION")}"
HERE="$(cd "$(dirname "$0")" && pwd)"
WORK="${WORK:-$HERE/build}"
OUT="$HERE/../app/libs/ics-openvpn.aar"

rm -rf "$WORK"
git clone --depth 1 --branch "$ICS_TAG" https://github.com/schwabe/ics-openvpn "$WORK"
cd "$WORK"
git submodule update --init --recursive --depth 1 || git submodule update --init --recursive

# Só o módulo do motor, como biblioteca
cp "$HERE/ics-library.gradle.kts" main/build.gradle.kts
cp "$HERE/consumer-rules.pro" main/consumer-rules.pro
cat > settings.gradle.kts <<'KTS'
pluginManagement { repositories { gradlePluginPortal(); google(); mavenCentral() } }
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories { google(); mavenCentral() }
}
include(":main")
KTS
cat > build.gradle.kts <<'KTS'
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
}
KTS

./gradlew --no-daemon :main:assembleSkeletonOvpn2Release
mkdir -p "$(dirname "$OUT")"
cp main/build/outputs/aar/main-skeleton-ovpn2-release.aar "$OUT"
echo "AAR pronto: $OUT"
