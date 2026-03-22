#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")" && pwd)"
BUILD_DIR="$ROOT/build"
CLASSES_DIR="$BUILD_DIR/classes"
VERSION_FILE="$ROOT/.itemlwc_build_version"

# Keep existing versioned jars; only refresh compiled classes for this build.
rm -rf "$CLASSES_DIR"
mkdir -p "$CLASSES_DIR"

find "$ROOT/src/main/java" -name '*.java' -print0 | xargs -0 javac --release 8 -cp "$ROOT/libs/craftbukkit-1060 bukkit.jar:$ROOT/libs/LWC.jar:$ROOT/libs/iConomy.jar:$ROOT/libs/Essentials.jar:$ROOT/libs/Vault.jar" -d "$CLASSES_DIR"
cp -R "$ROOT/src/main/resources/." "$CLASSES_DIR/"

jar cf "$BUILD_DIR/ItemLWC.jar" -C "$CLASSES_DIR" .

if [[ -f "$VERSION_FILE" ]]; then
	LAST_BUILD_NUMBER="$(cat "$VERSION_FILE" 2>/dev/null || echo 0)"
else
	LAST_BUILD_NUMBER=0
fi

if ! [[ "$LAST_BUILD_NUMBER" =~ ^[0-9]+$ ]]; then
	LAST_BUILD_NUMBER=0
fi

NEXT_BUILD_NUMBER=$((LAST_BUILD_NUMBER + 1))
printf "%s" "$NEXT_BUILD_NUMBER" > "$VERSION_FILE"

VERSION_MAJOR=$((NEXT_BUILD_NUMBER / 1000))
VERSION_MINOR=$((NEXT_BUILD_NUMBER % 1000))
VERSION_LABEL="$(printf "%d.%03d" "$VERSION_MAJOR" "$VERSION_MINOR")"
VERSIONED_JAR="$BUILD_DIR/itemlwc_v${VERSION_LABEL}.jar"

cp "$BUILD_DIR/ItemLWC.jar" "$VERSIONED_JAR"

echo "Built $BUILD_DIR/ItemLWC.jar"
echo "Built $VERSIONED_JAR"