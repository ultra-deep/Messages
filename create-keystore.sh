#!/usr/bin/env bash
# Create keystore.jks from the existing keystore.properties file.
#
# app/build.gradle.kts loads keystore.properties from the repo root.
# storeFile is resolved from the app module, so ../keystore.jks in that
# file points at keystore.jks in the repo root.
#
# Usage:
#   ./create-keystore.sh
#   ./create-keystore.sh --force
#
# keystore.properties is the input. This script does not rewrite it.

set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROPERTIES_FILE="${PROPERTIES_FILE:-${ROOT}/keystore.properties}"
VALIDITY_DAYS="${VALIDITY_DAYS:-10000}"
KEY_ALG="${KEY_ALG:-RSA}"
KEY_SIZE="${KEY_SIZE:-2048}"
DNAME="${DNAME:-CN=org.fossify.messages, OU=Android, O=Fossify, C=US}"

FORCE=0

usage() {
    cat <<'EOF'
Create keystore.jks using alias and passwords from keystore.properties.

Expected keys:
  keyAlias
  storePassword
  keyPassword
  storeFile          Path resolved from the app module (../keystore.jks)

Options:
  --force            Replace an existing keystore.jks
  -h, --help         Show this help

Environment:
  VALIDITY_DAYS      Certificate validity in days (default: 10000)
  DNAME              Certificate distinguished name
  PROPERTIES_FILE    Properties path (default: ./keystore.properties)
EOF
}

while [[ $# -gt 0 ]]; do
    case "$1" in
        --force)
            FORCE=1
            shift
            ;;
        -h|--help)
            usage
            exit 0
            ;;
        *)
            echo "Unknown option: $1" >&2
            usage >&2
            exit 1
            ;;
    esac
done

if ! command -v keytool >/dev/null 2>&1; then
    echo "keytool was not found. Install a JDK and ensure it is on PATH." >&2
    exit 1
fi

if [[ ! -f "$PROPERTIES_FILE" ]]; then
    echo "Missing $PROPERTIES_FILE" >&2
    echo "Create it with keyAlias, storePassword, keyPassword, and storeFile." >&2
    exit 1
fi

read_property() {
    local key="$1"
    local line=""
    line="$(grep -E "^[[:space:]]*${key}=" "$PROPERTIES_FILE" | tail -n 1 || true)"
    line="${line#"${line%%[![:space:]]*}"}"
    line="${line%$'\r'}"
    if [[ "$line" != "${key}="* ]]; then
        echo "Missing ${key} in ${PROPERTIES_FILE}" >&2
        exit 1
    fi
    local value="${line#*=}"
    value="${value//\\\\/\\}"
    if [[ -z "$value" ]]; then
        echo "${key} in ${PROPERTIES_FILE} is empty." >&2
        exit 1
    fi
    if [[ "$value" == *$'\n'* ]]; then
        echo "${key} must be a single line." >&2
        exit 1
    fi
    printf '%s' "$value"
}

KEY_ALIAS="$(read_property keyAlias)"
STORE_PASSWORD="$(read_property storePassword)"
KEY_PASSWORD="$(read_property keyPassword)"
STORE_FILE_PROPERTY="$(read_property storeFile)"

if [[ "$STORE_FILE_PROPERTY" = /* ]]; then
    KEYSTORE_FILE="$STORE_FILE_PROPERTY"
else
    KEYSTORE_FILE="$(realpath -m "${ROOT}/app/${STORE_FILE_PROPERTY}")"
fi

if [[ -e "$KEYSTORE_FILE" ]]; then
    if [[ "$FORCE" -ne 1 ]]; then
        echo "Refusing to overwrite existing keystore:" >&2
        echo "  $KEYSTORE_FILE" >&2
        echo "Re-run with --force to replace it. Replacing a published key cannot be undone." >&2
        exit 1
    fi
    rm -f "$KEYSTORE_FILE"
fi

keytool -genkeypair \
    -alias "$KEY_ALIAS" \
    -keyalg "$KEY_ALG" \
    -keysize "$KEY_SIZE" \
    -validity "$VALIDITY_DAYS" \
    -keystore "$KEYSTORE_FILE" \
    -storetype JKS \
    -storepass "$STORE_PASSWORD" \
    -keypass "$KEY_PASSWORD" \
    -dname "$DNAME"

chmod 600 "$KEYSTORE_FILE"

echo
echo "Created $KEYSTORE_FILE"
echo "Alias: ${KEY_ALIAS}"
echo "Settings read from ${PROPERTIES_FILE}"
echo
echo "Release builds will use this keystore. Back it up before you publish."
