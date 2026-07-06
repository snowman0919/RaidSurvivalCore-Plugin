#!/usr/bin/env sh
set -eu

APP_HOME=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
GRADLE_VERSION=9.6.1
DIST_DIR="$APP_HOME/.gradle/wrapper/dists/gradle-$GRADLE_VERSION-bin"
DIST_ZIP="$DIST_DIR/gradle-$GRADLE_VERSION-bin.zip"
GRADLE_HOME="$DIST_DIR/gradle-$GRADLE_VERSION"

if [ ! -x "$GRADLE_HOME/bin/gradle" ]; then
  mkdir -p "$DIST_DIR"
  if [ ! -f "$DIST_ZIP" ]; then
    curl -fsSL "https://services.gradle.org/distributions/gradle-$GRADLE_VERSION-bin.zip" -o "$DIST_ZIP"
  fi
  unzip -q "$DIST_ZIP" -d "$DIST_DIR"
fi

exec "$GRADLE_HOME/bin/gradle" "$@"
