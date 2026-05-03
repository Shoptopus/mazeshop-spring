#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
APP_DIR="${SHOPTOPUS_STOREFRONT_DIR:-$ROOT_DIR/../shoptopus-spring-storefront}"
DIST_DIR="$APP_DIR/dist"
TARGET_DIR="$ROOT_DIR/src/main/resources/static"

if [ ! -d "$APP_DIR" ]; then
  echo "Storefront app not found at $APP_DIR" >&2
  exit 1
fi

if [ -s "${NVM_DIR:-$HOME/.nvm}/nvm.sh" ]; then
  # Keep the frontend build on the expected Node runtime when nvm is available.
  # shellcheck disable=SC1090
  . "${NVM_DIR:-$HOME/.nvm}/nvm.sh"
  nvm use 20 >/dev/null
fi

mkdir -p "$TARGET_DIR"

if [ ! -d "$APP_DIR/node_modules" ]; then
  (cd "$APP_DIR" && npm ci)
fi

(cd "$APP_DIR" && VITE_APP_BASE=/ npm run build)

find "$TARGET_DIR" -mindepth 1 -maxdepth 1 ! -name studio -exec rm -rf {} +
cp -R "$DIST_DIR"/. "$TARGET_DIR"/
