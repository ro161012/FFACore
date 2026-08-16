#!/usr/bin/env bash
# Copies the built jar + resource pack into the local test server's plugins
# folder and the PrismLauncher instance's resourcepacks folder for quick
# in-game testing.
#
# Override the destinations with FFACORE_PLUGINS_DIR / FFACORE_PACKS_DIR.
set -euo pipefail
cd "$(dirname "$0")/.."

VERSION=$(grep -m1 -oP '(?<=<version>)[0-9]+\.[0-9]+\.[0-9]+(?=</version>)' pom.xml)
JAR="target/FFACore-$VERSION.jar"
ZIP="target/FFACore-Resourcepack.zip"

PLUGINS_DIR="${FFACORE_PLUGINS_DIR:-/c/Users/roand/AppData/Roaming/Fork/servers/testarea/plugins}"
PACKS_DIR="${FFACORE_PACKS_DIR:-/c/Users/roand/AppData/Roaming/PrismLauncher/instances/1.21.11/minecraft/resourcepacks}"

test -f "$JAR" || { echo "ERROR: missing $JAR (run ./mvnw package first)" >&2; exit 1; }
test -f "$ZIP" || { echo "ERROR: missing $ZIP (run ./mvnw package first)" >&2; exit 1; }

mkdir -p "$PLUGINS_DIR" "$PACKS_DIR"

# Remove any older FFACore jar so the server never loads two versions at once.
rm -f "$PLUGINS_DIR"/FFACore-*.jar
cp -f "$JAR" "$PLUGINS_DIR/"
cp -f "$ZIP" "$PACKS_DIR/"

echo "==> Deployed $JAR -> $PLUGINS_DIR"
echo "==> Deployed $ZIP -> $PACKS_DIR"
