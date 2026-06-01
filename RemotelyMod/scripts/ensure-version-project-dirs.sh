#!/usr/bin/env bash
set -euo pipefail
root="$(cd "$(dirname "$0")/.." && pwd)"
settings="$root/settings.gradle.kts"
config_dir="$root/version-config"
cd "$root"
while IFS= read -r version; do
  mkdir -p "versions/$version"
  if [[ -f "$config_dir/$version.properties" ]]; then
    cp "$config_dir/$version.properties" "versions/$version/gradle.properties"
  fi
done < <(grep -E '^\s+"[0-9]' "$settings" | sed -E 's/^[[:space:]]*"([^"]+)".*/\1/')
