#!/usr/bin/env bash
# Renders the launcher icon's vectors to PNG (via a Robolectric test, so it is
# Android's own renderer) and copies them into the mipmaps and the install page.
#   python3 scripts/icon/make_icon.py && scripts/icon/render.sh [--preview]
# --preview only writes /tmp/primary-suite-icon/{mockup,layers,icon-512}.png.
set -euo pipefail
cd "$(git rev-parse --show-toplevel)"
OUT=/tmp/primary-suite-icon
rm -rf "$OUT"; mkdir -p "$OUT"; touch "$OUT/REQUEST"
# --rerun: the request file isn't a task input, so Gradle would call the test up to date.
bash scripts/gradle-locked.sh testDebugUnitTest --rerun --tests 'com.primarysuite.app.tools.IconRenderTool' -PtestForks=1
[ -f "$OUT/mockup.png" ] || { echo "render failed: no $OUT/mockup.png" >&2; exit 1; }
echo "Preview: $OUT/mockup.png"
[ "${1:-}" = "--preview" ] && exit 0
for d in "$OUT"/mipmap-*; do cp "$d"/*.png "app/src/main/res/$(basename "$d")/"; done
cp "$OUT/icon.png" docs/icon.png
echo "Copied into app/src/main/res/mipmap-* and docs/icon.png"
