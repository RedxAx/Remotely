#!/usr/bin/env bash
set -euo pipefail
root="$(cd "$(dirname "$0")/.." && pwd)"
cd "$root"
missing=0
while IFS= read -r version; do
  dir="versions/$version"
  if [[ ! -d "$dir" ]]; then
    echo "::error::Missing version directory $dir"
    missing=1
    continue
  fi
  if [[ "$version" == *-neoforge ]]; then
    props="$dir/gradle.properties"
    if [[ ! -f "$props" ]] || ! grep -q '^dgt\.neoforge\.version=' "$props"; then
      echo "::error::NeoForge version $version requires dgt.neoforge.version in $props"
      missing=1
    fi
  fi
  if [[ "$version" == "26.1.2-fabric" ]]; then
    props="$dir/gradle.properties"
    if [[ ! -f "$props" ]] || ! grep -q '^dgt\.fabric\.api\.version=' "$props"; then
      echo "::error::26.1.2-fabric requires dgt.fabric.api.version in $props"
      missing=1
    fi
  fi
done < <(grep -E '^\s+"[0-9]' "$root/settings.gradle.kts" | sed -E 's/^[[:space:]]*"([^"]+)".*/\1/')
if [[ "$missing" -ne 0 ]]; then
  exit 1
fi
