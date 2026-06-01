#!/usr/bin/env bash
set -euo pipefail
root="$(cd "$(dirname "$0")/.." && pwd)"
settings="$root/settings.gradle.kts"
cd "$root"
while IFS= read -r version; do
  mkdir -p "versions/$version"
done < <(grep -E '^\s+"[0-9]' "$settings" | sed -E 's/^[[:space:]]*"([^"]+)".*/\1/')
