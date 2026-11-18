#!/usr/bin/env bash
# Rewrites natives.sha256 from the DLLs under src/main/resources/natives/. Run it after rebuilding or replacing a DLL on
# purpose (native/build-*.sh, a new Streamline or FidelityFX drop) and commit both together: build.sh refuses to package
# DLLs that don't match the record, so a DLL can't change in the jar without it showing in the diff.
set -euo pipefail
HERE="$(cd "$(dirname "$0")" && pwd)"
cd "$HERE/src/main/resources"
if command -v sha256sum >/dev/null 2>&1; then SUM=(sha256sum); else SUM=(shasum -a 256); fi
find natives -name '*.dll' -print0 | LC_ALL=C sort -z | xargs -0 "${SUM[@]}" > "$HERE/natives.sha256"
echo "Wrote $HERE/natives.sha256 ($(wc -l < "$HERE/natives.sha256" | tr -d ' ') DLLs)"
