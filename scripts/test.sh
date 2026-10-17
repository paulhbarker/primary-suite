#!/usr/bin/env bash
# Runs unit tests through scripts/gradle-locked.sh and prints a short summary
# with each failure. With arguments, runs only matching classes, in one fork:
#
#   scripts/test.sh                                   # whole suite, 2 forks
#   scripts/test.sh 'com.primarysuite.app.games.reelrush.*'
#
# Compile errors are printed as `e:` lines.
set -uo pipefail
cd "$(git rev-parse --show-toplevel)"
RESULTS=app/build/test-results/testDebugUnitTest
rm -rf "$RESULTS"
args=(testDebugUnitTest)
if [ $# -gt 0 ]; then
  for f in "$@"; do args+=(--tests "$f"); done
  args+=(-PtestForks=1)
fi
out=$(bash scripts/gradle-locked.sh "${args[@]}" 2>&1); code=$?
echo "$out" | grep -E '^e: |\[gradle-locked\]|BUILD FAILED|What went wrong|[Pp]ermission denied|No tests found' -A2 | grep -v '^--$' | head -40
python3 - "$RESULTS" <<'PY'
import glob, html, re, sys
total = failed = 0
for f in sorted(glob.glob(sys.argv[1] + "/*.xml")):
    s = open(f).read()
    m = re.search(r'tests="(\d+)" skipped="\d+" failures="(\d+)" errors="(\d+)"', s)
    total += int(m.group(1)); failed += int(m.group(2)) + int(m.group(3))
    for t in re.finditer(r'<testcase name="([^"]*)" classname="[^"]*\.([^".]*)"[^>]*>\s*<(?:failure|error) message="([^"]{0,400})', s):
        print("FAIL", t.group(2), "::", t.group(1), "::", html.unescape(t.group(3)).replace("\n", " "))
print(f"{total} tests, {failed} failed")
if total == 0:
    print("NO TESTS RAN: treat this as a failure (check the filter and the output above)")
    sys.exit(1)
PY
py=$?
[ $code -ne 0 ] && exit $code
exit $py
