#!/usr/bin/env bash
# Runs Gradle under a machine-wide lock, so parallel agents (and their git
# worktrees) build one at a time and share a single Gradle daemon and Kotlin
# daemon. WSL has 8 GB; three concurrent builds would exhaust it.
#
#   scripts/gradle-locked.sh compileDebugKotlin
#   scripts/gradle-locked.sh testDebugUnitTest --tests 'com.primarysuite.app.games.reelrush.*' -PtestForks=1
#
# Waits up to 30 minutes for the lock. Run from the repo or worktree root.
set -euo pipefail
export ANDROID_HOME="${ANDROID_HOME:-$HOME/Android/Sdk}"
LOCK=/tmp/primary-suite-gradle.lock
cd "$(git rev-parse --show-toplevel)"
exec 9>"$LOCK"
if ! flock -n 9; then
  echo "[gradle-locked] another build is running; waiting for the lock..." >&2
  flock -w 1800 9 || { echo "[gradle-locked] gave up waiting after 30 min" >&2; exit 75; }
fi
echo "[gradle-locked] $(basename "$PWD"): gradlew $* (free: $(free -m | awk '/Mem:/{print $7}') MB)" >&2
./gradlew --console=plain -q "$@"
