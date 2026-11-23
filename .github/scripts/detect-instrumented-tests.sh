#!/usr/bin/env bash
# Reports whether any instrumented test sources exist (src/androidTest/**/*.kt|java).
# Writes found=true|false to $GITHUB_OUTPUT. When none exist, a workflow warning and the
# job summary state clearly that NO instrumented tests were executed.
set -euo pipefail

count=$(find . -type f \( -name '*.kt' -o -name '*.java' \) -path '*/src/androidTest/*' | wc -l)

if [ "$count" -gt 0 ]; then
  echo "Found $count instrumented test source file(s)."
  echo "found=true" >> "${GITHUB_OUTPUT:-/dev/null}"
else
  msg="No instrumented tests exist yet (no src/androidTest sources). Emulator run SKIPPED, nothing was verified."
  echo "::warning title=No instrumented tests::$msg"
  {
    echo "### Instrumented tests: NOT RUN"
    echo "$msg"
  } >> "${GITHUB_STEP_SUMMARY:-/dev/null}"
  echo "found=false" >> "${GITHUB_OUTPUT:-/dev/null}"
fi
