#!/usr/bin/env bash
set -euo pipefail
remodel_root="${1:?Remodel directory path required}"
build_file="$remodel_root/build.gradle"
if [[ ! -f "$build_file" ]]; then
  echo "::error::Remodel build.gradle not found at $build_file"
  exit 1
fi
if grep -q 'JavaLanguageVersion.of(21)' "$build_file"; then
  exit 0
fi
if ! grep -q '^test {' "$build_file"; then
  echo "::error::Remodel build.gradle has no test { block; cannot inject Java 21 toolchain"
  exit 1
fi
tmp="$(mktemp)"
awk '
/^test \{/ && !done {
  print "java {"
  print "    toolchain {"
  print "        languageVersion = JavaLanguageVersion.of(21)"
  print "    }"
  print "}"
  print ""
  done = 1
}
{ print }
' "$build_file" > "$tmp"
mv "$tmp" "$build_file"
echo "Injected Java 21 toolchain into Remodel build.gradle for CI compatibility"
