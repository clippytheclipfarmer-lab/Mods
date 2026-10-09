#!/usr/bin/env bash
# Fills <project>/dev-libs with Palladium and the libraries bundled inside it, so `./gradlew runServer` and
# `./gradlew runSelftest` can run. Run it after one `./gradlew build` (that downloads Palladium into Gradle's cache).
#
#     bash tools/prepare-dev-libs.sh fisks-superheroes
set -euo pipefail
project="${1:?usage: prepare-dev-libs.sh <project-dir>}"
jar=$(find "${GRADLE_USER_HOME:-$HOME/.gradle}/caches/modules-2/files-2.1/net.threetag/palladium-fabric" -name '*-fabric.jar' | head -1)
[ -n "$jar" ] || { echo "Palladium is not in Gradle's cache yet: run ./gradlew build first" >&2; exit 1; }
mkdir -p "$project/dev-libs"
cp "$jar" "$project/dev-libs/palladium.jar"
# Palladium bundles its libraries as nested jars (some of which bundle more): unpack them all, level by level.
for _ in 1 2 3; do
  for j in "$project"/dev-libs/*.jar; do
    if unzip -l "$j" 2>/dev/null | grep -q 'META-INF/jars/.*\.jar'; then
      tmp=$(mktemp -d)
      unzip -o -q "$j" 'META-INF/jars/*.jar' -d "$tmp"
      mv -n "$tmp"/META-INF/jars/*.jar "$project/dev-libs/" || true
      rm -rf "$tmp"
    fi
  done
done
ls "$project/dev-libs"
