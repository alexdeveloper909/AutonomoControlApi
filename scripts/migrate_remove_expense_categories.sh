#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" >/dev/null 2>&1 && pwd)"
API_DIR="$(cd -- "$SCRIPT_DIR/.." >/dev/null 2>&1 && pwd)"

CLI_ARGS="$*"

cd "$API_DIR"
./gradlew :app:migrateRemoveExpenseCategories -PcliArgs="$CLI_ARGS"

