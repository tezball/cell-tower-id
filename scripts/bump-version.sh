#!/usr/bin/env bash
# Bump versionName in app/build.gradle.kts, commit, and tag.
#
# versionCode is auto-managed by CI (github.run_number + 100); see the
# "Compute versionCode" step in .github/workflows/ci-cd.yml. This script
# only touches versionName.
#
# Usage:   ./scripts/bump-version.sh <new-version-name>
# Example: ./scripts/bump-version.sh 0.1.1
#
# Behavior:
#   - Sets versionName to the argument.
#   - Commits the change as "Bump versionName to vX.Y.Z".
#   - Creates an annotated git tag vX.Y.Z (triggers the release workflow on push).
#
# Push with:  git push --follow-tags

set -euo pipefail

NEW_VERSION_NAME="${1:-}"
if [[ -z "$NEW_VERSION_NAME" ]]; then
  echo "Usage: $0 <new-version-name> (e.g. 0.1.1)" >&2
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

CURRENT_NAME=$(grep -oE 'versionName = "[^"]+"' "$GRADLE_FILE" | sed -E 's/.*"([^"]+)"/\1/')

if [[ -z "$CURRENT_NAME" ]]; then
  echo "Error: could not parse current versionName from $GRADLE_FILE" >&2
  exit 1
fi

TAG="v$NEW_VERSION_NAME"

if git rev-parse "$TAG" >/dev/null 2>&1; then
  echo "Error: tag $TAG already exists" >&2
  exit 1
fi

echo "Bumping versionName: $CURRENT_NAME -> $NEW_VERSION_NAME"

# In-place edit (sed -i differs between BSD/macOS and GNU)
if [[ "$(uname)" == "Darwin" ]]; then
  sed -i '' -E "s/versionName = \"[^\"]+\"/versionName = \"$NEW_VERSION_NAME\"/" "$GRADLE_FILE"
else
  sed -i -E "s/versionName = \"[^\"]+\"/versionName = \"$NEW_VERSION_NAME\"/" "$GRADLE_FILE"
fi

git add "$GRADLE_FILE"
git commit -m "Bump versionName to $TAG"
git tag -a "$TAG" -m "Release $TAG"

echo
echo "Done. Pushed tag will trigger the release workflow."
echo "  git push --follow-tags"
