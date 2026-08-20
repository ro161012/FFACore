#!/usr/bin/env bash
# Manual release script for FFACore.
#
# Bumps the minor version (X.Y.Z -> X.(Y+1).0), builds the jar + resource
# pack, commits the version bump, tags it, pushes, and creates a GitHub
# release with both artifacts attached.
#
# Usage:  tools/release.sh [version]
#   version   optional explicit version like 1.3.0; defaults to a minor bump
#
# Requires: the PAT embedded in the git remote URL (https://TOKEN@github.com/...)
# or the GITHUB_TOKEN environment variable.
set -euo pipefail
cd "$(dirname "$0")/.."

# --- Resolve version -------------------------------------------------------
CUR_VER=$(grep -m1 -oP '(?<=<version>)[0-9]+\.[0-9]+\.[0-9]+(?=</version>)' pom.xml)
if [ $# -ge 1 ]; then
  NEW_VER="$1"
else
  NEW_VER=$(echo "$CUR_VER" | awk -F. '{print $1 "." $2+1 ".0"}')
fi
echo "==> Releasing $CUR_VER -> $NEW_VER"

# --- Token -----------------------------------------------------------------
TOKEN="${GITHUB_TOKEN:-}"
if [ -z "$TOKEN" ]; then
  REMOTE=$(git remote get-url origin)
  TOKEN=$(echo "$REMOTE" | sed -nE 's#https://([^@]+)@github.com/.*#\1#p')
fi
if [ -z "$TOKEN" ]; then
  echo "ERROR: no GITHUB_TOKEN and no PAT in the git remote URL" >&2
  exit 1
fi
AUTH="Authorization: token $TOKEN"

# --- Full changelog ---------------------------------------------------------
# Every commit since the previous release tag becomes one bullet in the
# changelog; version-bump commits are noise and are filtered out.
CHANGELOG=$(git log --pretty=format:'- %s' "v$CUR_VER..HEAD" 2>/dev/null \
  | grep -v '^-[[:space:]]*Bump version' || true)
if [ -z "$CHANGELOG" ]; then
  CHANGELOG="- Initial release"
fi

# --- Update CHANGELOG.md ------------------------------------------------------
# Insert the new section directly under the [Unreleased] heading so the file
# always lists the newest release first.
CHANGELOG_FILE="CHANGELOG.md"
if [ -f "$CHANGELOG_FILE" ]; then
  {
    sed -n '1,/^## \[Unreleased\]/p' "$CHANGELOG_FILE"
    echo
    echo "## [$NEW_VER] - $(date +%Y-%m-%d)"
    printf '%s\n' "$CHANGELOG"
    echo
    sed -n '/^## \[Unreleased\]/,$p' "$CHANGELOG_FILE" | sed '1d'
  } > "$CHANGELOG_FILE.tmp"
  mv "$CHANGELOG_FILE.tmp" "$CHANGELOG_FILE"
fi

# --- Regenerate the merged pack + preview catalog ------------------------
# The nexo pack (../asdasdads, a sibling of the FFACore project folder) is
# merged into resourcepack/ and the preview-items.json catalog is rebuilt
# from it, so every release ships the latest items and the /ffa preview
# command matches the pack.
if [ -d "../asdasdads" ]; then
  echo "==> Merging nexo pack + regenerating preview catalog"
  node tools/merge_nexo_pack.js
  node tools/gen_preview_registry.js
else
  echo "==> No ../asdasdads pack found - keeping existing pack/catalog"
fi

# --- Build + commit + tag ---------------------------------------------------
sed -i "s#<version>$CUR_VER</version>#<version>$NEW_VER</version>#" pom.xml
./mvnw -q clean package
bash tools/copy-artifacts.sh
git add pom.xml CHANGELOG.md
git -c user.name="ro161012" -c user.email="ro161012@users.noreply.github.com" \
  commit -m "Bump version to $NEW_VER"
git -c user.name="ro161012" -c user.email="ro161012@users.noreply.github.com" \
  tag -a -m "FFACore $NEW_VER" "v$NEW_VER"
git push origin main
git push origin "v$NEW_VER"

# --- Release ----------------------------------------------------------------
JAR="target/FFACore-$NEW_VER.jar"
ZIP="target/FFACore-Resourcepack.zip"
test -f "$JAR" && test -f "$ZIP"

BODY="## FFACore $NEW_VER

### Changes
$CHANGELOG

### Installation
- **Jar** \`FFACore-$NEW_VER.jar\` -> your server's \`plugins/\` folder
- **Resource pack** \`FFACore-Resourcepack.zip\` -> client resource packs, then F3+T
- Built against Paper API 1.21.11, resource pack \`pack_format\` 75 (1.21.11)"

RELEASE=$(curl -s -X POST -H "$AUTH" -H "Accept: application/vnd.github+json" \
  -d "$(node -e 'console.log(JSON.stringify({tag_name:process.argv[1],name:process.argv[1],body:process.argv[2]}))' "v$NEW_VER" "$BODY")" \
  "https://api.github.com/repos/ro161012/FFACore/releases")
RELEASE_ID=$(echo "$RELEASE" | node -e 'let s="";process.stdin.on("data",d=>s+=d).on("end",()=>{const j=JSON.parse(s);console.log(j.id||"")})')
if [ -z "$RELEASE_ID" ]; then
  echo "ERROR: release creation failed:" >&2
  echo "$RELEASE" >&2
  exit 1
fi
echo "==> Release v$NEW_VER created (id $RELEASE_ID)"

for FILE in "$JAR" "$ZIP"; do
  NAME=$(basename "$FILE")
  echo "==> Uploading $NAME ..."
  curl -s -X POST -H "$AUTH" -H "Accept: application/vnd.github+json" \
    -H "Content-Type: application/octet-stream" \
    --data-binary "@$FILE" \
    "https://uploads.github.com/repos/ro161012/FFACore/releases/$RELEASE_ID/assets?name=$NAME" > /dev/null
done

echo "==> Done: https://github.com/ro161012/FFACore/releases/tag/v$NEW_VER"
