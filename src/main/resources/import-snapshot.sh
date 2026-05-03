#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
APP_HOME="$SCRIPT_DIR"

resolve_config_file() {
  if [[ -f "$SCRIPT_DIR/application.yaml" ]]; then
    printf '%s\n' "$SCRIPT_DIR/application.yaml"
    return
  fi
  if [[ -f "$SCRIPT_DIR/application.yml" ]]; then
    printf '%s\n' "$SCRIPT_DIR/application.yml"
    return
  fi
  echo "Unable to find application.yaml or application.yml next to $(basename "$0")" >&2
  exit 1
}

CONFIG_FILE="$(resolve_config_file)"

require_command() {
  if ! command -v "$1" >/dev/null 2>&1; then
    echo "Missing required command: $1" >&2
    exit 1
  fi
}

extract_yaml_value() {
  local path="$1"
  awk -v target="$path" '
    function trim(value) {
      sub(/^[[:space:]]+/, "", value)
      sub(/[[:space:]]+$/, "", value)
      return value
    }
    {
      line = $0
      if (line ~ /^[[:space:]]*#/ || line ~ /^[[:space:]]*$/) {
        next
      }
      indent = match(line, /[^ ]/) - 1
      if (indent < 0) {
        next
      }
      content = substr(line, indent + 1)
      if (content !~ /^[^:]+:/) {
        next
      }
      split(content, parts, ":")
      key = trim(parts[1])
      depth = int(indent / 2) + 1
      keys[depth] = key
      for (i = depth + 1; i < 16; i++) {
        delete keys[i]
      }
      value = trim(substr(content, length(parts[1]) + 2))
      if (value == "") {
        next
      }
      current = keys[1]
      for (i = 2; i <= depth; i++) {
        current = current "." keys[i]
      }
      if (current == target) {
        print value
        exit
      }
    }
  ' "$CONFIG_FILE"
}

resolve_placeholder() {
  local raw="$1"
  local resolved="$raw"

  if [[ "$resolved" =~ ^\$\{([^:}]+):(.*)\}$ ]]; then
    local env_name="${BASH_REMATCH[1]}"
    local default_value="${BASH_REMATCH[2]}"
    resolved="${!env_name:-$default_value}"
  fi

  resolved="${resolved//'${user.dir}'/$APP_HOME}"
  printf '%s\n' "$resolved"
}

read_config() {
  local key="$1"
  local raw
  raw="$(extract_yaml_value "$key")"
  if [[ -z "$raw" ]]; then
    echo "Unable to resolve configuration key: $key" >&2
    exit 1
  fi
  resolve_placeholder "$raw"
}

parse_jdbc_url() {
  local jdbc_url="$1"
  if [[ ! "$jdbc_url" =~ ^jdbc:postgresql://([^/:?#]+)(:([0-9]+))?/([^?]+) ]]; then
    echo "Unsupported PostgreSQL JDBC URL: $jdbc_url" >&2
    exit 1
  fi
  DB_HOST="${BASH_REMATCH[1]}"
  DB_PORT="${BASH_REMATCH[3]:-5432}"
  DB_NAME="${BASH_REMATCH[4]}"
}

archive_path_from_arg() {
  local requested="${1:-}"
  if [[ -z "$requested" ]]; then
    echo "Usage: $(basename "$0") <snapshot.zip>" >&2
    exit 1
  fi
  if [[ ! -f "$requested" && "$requested" != *.zip && -f "$requested.zip" ]]; then
    requested="$requested.zip"
  fi
  if [[ "$requested" = /* ]]; then
    printf '%s\n' "$requested"
  else
    printf '%s\n' "$PWD/$requested"
  fi
}

guard_uploads_path() {
  local path="$1"
  if [[ -z "$path" || "$path" = "/" ]]; then
    echo "Refusing to operate on uploads path: $path" >&2
    exit 1
  fi
}

require_command psql
require_command unzip

sanitize_dump_file() {
  local input_file="$1"
  local sanitized_file="$2"
  awk '
    /^[[:space:]]*SET[[:space:]]+transaction_timeout[[:space:]]*=[[:space:]]*.*;[[:space:]]*$/ {
      next
    }
    { print }
  ' "$input_file" > "$sanitized_file"
}

ARCHIVE_PATH="$(archive_path_from_arg "${1:-}")"
if [[ ! -f "$ARCHIVE_PATH" ]]; then
  echo "Snapshot not found: $ARCHIVE_PATH" >&2
  exit 1
fi

DB_URL="$(read_config "spring.datasource.main.url")"
DB_USERNAME="$(read_config "spring.datasource.main.username")"
DB_PASSWORD="$(read_config "spring.datasource.main.password")"
UPLOADS_PATH="$(read_config "app.media.storage-path")"

guard_uploads_path "$UPLOADS_PATH"
parse_jdbc_url "$DB_URL"

TEMP_DIR="$(mktemp -d)"
trap 'rm -rf "$TEMP_DIR"' EXIT

echo "Extracting snapshot: $ARCHIVE_PATH"
unzip -q "$ARCHIVE_PATH" -d "$TEMP_DIR"

DUMP_FILE="$TEMP_DIR/database.sql"
SANITIZED_DUMP_FILE="$TEMP_DIR/database.sanitized.sql"
UPLOADS_SOURCE_DIR="$TEMP_DIR/uploads"

if [[ ! -f "$DUMP_FILE" ]]; then
  echo "Snapshot does not contain database.sql" >&2
  exit 1
fi

echo "Clearing database $DB_NAME@$DB_HOST:$DB_PORT"
PGPASSWORD="$DB_PASSWORD" psql \
  --host "$DB_HOST" \
  --port "$DB_PORT" \
  --username "$DB_USERNAME" \
  --dbname "$DB_NAME" \
  --set ON_ERROR_STOP=1 \
  --command "DROP SCHEMA IF EXISTS public CASCADE; CREATE SCHEMA public; GRANT ALL ON SCHEMA public TO CURRENT_USER; GRANT ALL ON SCHEMA public TO public;"

echo "Importing database dump"
sanitize_dump_file "$DUMP_FILE" "$SANITIZED_DUMP_FILE"
PGPASSWORD="$DB_PASSWORD" psql \
  --host "$DB_HOST" \
  --port "$DB_PORT" \
  --username "$DB_USERNAME" \
  --dbname "$DB_NAME" \
  --set ON_ERROR_STOP=1 \
  --file "$SANITIZED_DUMP_FILE"

echo "Restoring uploads into $UPLOADS_PATH"
rm -rf "$UPLOADS_PATH"
mkdir -p "$UPLOADS_PATH"
if [[ -d "$UPLOADS_SOURCE_DIR" ]]; then
  cp -R "$UPLOADS_SOURCE_DIR"/. "$UPLOADS_PATH"/
fi

echo "Snapshot restored from: $ARCHIVE_PATH"
