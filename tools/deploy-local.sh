#!/usr/bin/env bash
# Builds the jar + resource pack and copies them into the local test server
# and PrismLauncher folders. Use this after committing when you want a fresh
# local build without cutting a GitHub release.
set -euo pipefail
cd "$(dirname "$0")/.."

./mvnw -q clean package
bash tools/copy-artifacts.sh
