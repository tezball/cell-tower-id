#!/usr/bin/env bash
# Bump versionCode + versionName in app/build.gradle.kts, commit, and tag.
#
# Usage:   ./scripts/bump-version.sh <new-version-name>
# Example: ./scripts/bump-version.sh 1.0.1
#
# Behavior:
#   - Increments versionCode by 1.
#   - Sets versionName to the argument.
#   - Commits the change as "Bump version to vX.Y.Z".
#   - Creates an annotated git tag vX.Y.Z (which triggers the release workflow on push).
#
# Push with:  git push --follow-tags

set -euo pipefail

NEW_VERSION_NAME="${1:-}"
if [[ -z "$NEW_VERSION_NAME" ]]; then
  echo "Usage: $0 <new-version-name> (e.g. 1.0.1)" >&2
  exit 1
fi

if ! [[ "$NEW_VERSION_NAME" =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]]; then
  echo "Error: version must be SemVer X.Y.Z (got: $NEW_VERSION_NAME)" >&2
  exit 1
fi

REPO_ROOT="$(git rev-parse --show-toplevel)"
GRADLE_FILE="$REPO_ROOT/app/build.gradle.kts"

if [[ ! -f "$GRADLE_FILE" ]]; then
  echo "Error: $GRADLE_FILE not found" >&2
  exit 1
fi

if ! git diff --quiet || ! git diff --cached --quiet; then
  echo "Error: working tree has uncommitted changes. Commit or stash first." >&2
  exit 1
fi

CURRENT_CODE=$(grep -oE 'versionCode = [0-9]+' "$GRADLE_FILE" | grep -oE '[0-9]+')
CURRENT_NAME=$(grep -oE 'versionName = "[^"]+"' "$GRADLE_FILE" | sed -E 's/.*"([^"]+)"/\1/')

if [[ -z "$CURRENT_CODE" || -z "$CURRENT_NAME" ]]; then
  echo "Error: could not parse current version from $GRADLE_FILE" >&2
  exit 1
fi

NEW_CODE=$((CURRENT_CODE + 1))
TAG="v$NEW_VERSION_NAME"

if git rev-parse "$TAG" >/dev/null 2>&1; then
  echo "Error: tag $TAG already exists" >&2
  exit 1
fi

echo "Bumping: $CURRENT_NAME (code $CURRENT_CODE) -> $NEW_VERSION_NAME (code $NEW_CODE)"

# In-place edits (sed -i differs between BSD/macOS and GNU)
if [[ "$(uname)" == "Darwin" ]]; then
  sed -i '' -E "s/versionCode = $CURRENT_CODE/versionCode = $NEW_CODE/" "$GRADLE_FILE"
  sed -i '' -E "s/versionName = \"[^\"]+\"/versionName = \"$NEW_VERSION_NAME\"/" "$GRADLE_FILE"
else
  sed -i -E "s/versionCode = $CURRENT_CODE/versionCode = $NEW_CODE/" "$GRADLE_FILE"
  sed -i -E "s/versionName = \"[^\"]+\"/versionName = \"$NEW_VERSION_NAME\"/" "$GRADLE_FILE"
fi

git add "$GRADLE_FILE"
git commit -m "Bump version to $TAG (versionCode $NEW_CODE)"
git tag -a "$TAG" -m "Release $TAG"

echo
echo "Done. Pushed tag will trigger the release workflow."
echo "  git push --follow-tags"
