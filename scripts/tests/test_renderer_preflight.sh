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

printf '%s\n' 'renderer-preflight shell tests: PASS'
