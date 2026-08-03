#!/usr/bin/env bash
set -euo pipefail

root="$(cd "$(dirname "$0")/../.." && pwd)"
script="$root/scripts/renderer-preflight.sh"

default_output="$($script)"
test $? -eq 0
printf '%s\n' "$default_output" | grep -F 'RLHD modern-renderer preflight (read-only)' >/dev/null
printf '%s\n' "$default_output" | grep -F 'READINESS:' >/dev/null

set +e
strict_output="$($script --check 2>&1)"
strict_status=$?
set -e
test "$strict_status" -eq 0 || test "$strict_status" -eq 2
printf '%s\n' "$strict_output" | grep -F 'READINESS:' >/dev/null

set +e
invalid_output="$($script --nope 2>&1)"
invalid_status=$?
set -e
test "$invalid_status" -eq 64
printf '%s\n' "$invalid_output" | grep -F 'error: unknown option: --nope' >/dev/null

fixture_bin="$root/scripts/tests/fixtures/preflight-missing"

set +e
fixture_output="$(PATH="$fixture_bin:/bin" "$script" --check 2>&1)"
fixture_status=$?
set -e
test "$fixture_status" -eq 2
printf '%s\n' "$fixture_output" | grep -F 'MISSING  Metal compiler (xcrun metal/metallib): optional Xcode Metal toolchain is unavailable' >/dev/null
printf '%s\n' "$fixture_output" | grep -F 'MISSING  glslangValidator: GLSL-to-SPIR-V compiler is not on PATH' >/dev/null
printf '%s\n' "$fixture_output" | grep -F 'MISSING  Native JVM architecture: host is arm64 but java reports x86_64; performance measurements are invalid' >/dev/null

ready_fixture="$root/scripts/tests/fixtures/preflight-ready"
set +e
ready_output="$(PATH="$ready_fixture:/bin" "$script" --check 2>&1)"
ready_status=$?
set -e
test "$ready_status" -eq 0
printf '%s\n' "$ready_output" | grep -F 'PASS  Gradle wrapper: version 8.10 (gradle-8.10-all.zip)' >/dev/null
printf '%s\n' "$ready_output" | grep -F 'READINESS: READY for local performance measurements' >/dev/null

outside_dir="$(cd "$root/.." && pwd)"
relative_script="./${root##*/}/scripts/renderer-preflight.sh"
set +e
relative_output="$(cd "$outside_dir" && unset PREFLIGHT_GRADLE_WRAPPER_PROPERTIES && PATH="$ready_fixture:/bin" "$relative_script" --check 2>&1)"
relative_status=$?
set -e
test "$relative_status" -eq 0
printf '%s\n' "$relative_output" | grep -F 'PASS  Gradle wrapper: version 8.10 (gradle-8.10-all.zip)' >/dev/null
printf '%s\n' "$relative_output" | grep -F 'READINESS: READY for local performance measurements' >/dev/null

broken_java_fixture="$root/scripts/tests/fixtures/preflight-broken-java"
set +e
broken_java_output="$(PATH="$broken_java_fixture:/bin" "$script" --check 2>&1)"
broken_java_status=$?
set -e
test "$broken_java_status" -eq 2
printf '%s\n' "$broken_java_output" | grep -F 'MISSING  JVM: java invocation failed' >/dev/null

malformed_wrapper="$root/scripts/tests/fixtures/gradle-wrapper-malformed.properties"
set +e
malformed_output="$(PREFLIGHT_GRADLE_WRAPPER_PROPERTIES="$malformed_wrapper" PATH="$fixture_bin:/bin" "$script" --check 2>&1)"
malformed_status=$?
set -e
test "$malformed_status" -eq 2
printf '%s\n' "$malformed_output" | grep -F "MISSING  Gradle wrapper: distribution URL is blank or malformed in $malformed_wrapper" >/dev/null

for invalid_wrapper in \
  "$root/scripts/tests/fixtures/gradle-wrapper-invalid-version.properties" \
  "$root/scripts/tests/fixtures/gradle-wrapper-invalid-url.properties"
do
  set +e
  invalid_wrapper_output="$(PREFLIGHT_GRADLE_WRAPPER_PROPERTIES="$invalid_wrapper" PATH="$ready_fixture:/bin" "$script" --check 2>&1)"
  invalid_wrapper_status=$?
  set -e
  test "$invalid_wrapper_status" -eq 2
  printf '%s\n' "$invalid_wrapper_output" | grep -F "MISSING  Gradle wrapper: distribution URL is blank or malformed in $invalid_wrapper" >/dev/null
  printf '%s\n' "$invalid_wrapper_output" | grep -F 'READINESS: NOT READY for local performance measurements' >/dev/null
done

printf '%s\n' 'renderer-preflight shell tests: PASS'
