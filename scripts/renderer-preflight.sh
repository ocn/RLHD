#!/usr/bin/env bash
# Read-only renderer-toolchain inventory. It never installs, downloads, or edits.
set -u

mode="report"
case "${1:-}" in
  "") ;;
  --check|--strict) mode="check" ;;
  --help|-h)
    printf '%s\n' 'Usage: scripts/renderer-preflight.sh [--check]'
    printf '%s\n' 'Default mode always exits 0; --check exits 2 when performance prerequisites are missing.'
    exit 0 ;;
  *) printf 'error: unknown option: %s\n' "$1" >&2; exit 64 ;;
esac

missing=0
pass() { printf 'PASS  %s: %s\n' "$1" "$2"; }
note() { printf 'INFO  %s: %s\n' "$1" "$2"; }
fail() { printf 'MISSING  %s: %s\n' "$1" "$2"; missing=1; }
command_version() { "$1" "$2" 2>&1 | sed -n '1p'; }

script_path="${BASH_SOURCE[0]}"
case "$script_path" in /*) ;; *) script_path="$(pwd)/$script_path" ;; esac
script_dir="$(cd "${script_path%/*}" && pwd)"
repo_root="$(cd "$script_dir/.." && pwd)"

printf '%s\n' 'RLHD modern-renderer preflight (read-only)'

os_name="$(uname -s 2>/dev/null || printf unknown)"
os_version="$(sw_vers -productVersion 2>/dev/null || uname -r 2>/dev/null || printf unknown)"
cpu_arch="$(uname -m 2>/dev/null || printf unknown)"
note 'OS' "$os_name $os_version"
note 'CPU architecture' "$cpu_arch"
if [ "$os_name" = Darwin ] && command -v sysctl >/dev/null 2>&1; then
  note 'CPU model' "$(sysctl -n machdep.cpu.brand_string 2>/dev/null || sysctl -n hw.model 2>/dev/null || printf unknown)"
fi

if command -v java >/dev/null 2>&1; then
  if java_properties="$(java -XshowSettings:properties -version 2>&1)"; then
    java_version="$(printf '%s\n' "$java_properties" | sed -n 's/^[[:space:]]*java.version = //p' | sed -n '1p')"
    java_arch="$(printf '%s\n' "$java_properties" | sed -n 's/^[[:space:]]*os.arch = //p' | sed -n '1p')"
    java_vendor="$(printf '%s\n' "$java_properties" | sed -n 's/^[[:space:]]*java.vendor = //p' | sed -n '1p')"
    if [ -n "$java_version" ] && [ -n "$java_arch" ]; then
      pass 'JVM' "${java_vendor:-unknown} $java_version ($java_arch)"
      if [ "$os_name" = Darwin ] && [ "$cpu_arch" = arm64 ] && [ "$java_arch" != aarch64 ] && [ "$java_arch" != arm64 ]; then
        fail 'Native JVM architecture' "host is $cpu_arch but java reports $java_arch; performance measurements are invalid"
      else
        pass 'Native JVM architecture' "host/JVM architecture is suitable for comparison"
      fi
    else
      fail 'JVM' 'java did not report java.version and os.arch properties'
    fi
  else
    fail 'JVM' 'java invocation failed'
  fi
else
  fail 'JVM' 'java is not on PATH'
fi

wrapper_properties="${PREFLIGHT_GRADLE_WRAPPER_PROPERTIES:-$repo_root/gradle/wrapper/gradle-wrapper.properties}"
if [ -r "$wrapper_properties" ]; then
  wrapper_url="$(sed -n 's/^distributionUrl=//p' "$wrapper_properties" | sed -n '1p')"
  wrapper_url="${wrapper_url//\\:/:}"
  wrapper_file="${wrapper_url##*/}"
  wrapper_remainder="${wrapper_url#*://}"
  wrapper_host="${wrapper_remainder%%/*}"
  case "$wrapper_file" in
    gradle-*-bin.zip) gradle_version="${wrapper_file#gradle-}"; gradle_version="${gradle_version%-bin.zip}" ;;
    gradle-*-all.zip) gradle_version="${wrapper_file#gradle-}"; gradle_version="${gradle_version%-all.zip}" ;;
    *) gradle_version='' ;;
  esac
  gradle_version="$(printf '%s\n' "$gradle_version" | sed -n '/^[0-9][0-9]*\.[0-9][0-9]*\(\.[0-9][0-9]*\)\{0,1\}\(-[A-Za-z0-9][A-Za-z0-9.-]*\)\{0,1\}$/p')"
  case "$wrapper_url" in
    http://*|https://*) ;;
    *) gradle_version='' ;;
  esac
  case "$wrapper_url" in *[[:space:]]*) gradle_version='' ;; esac
  if [ -n "$wrapper_host" ] && [ "$wrapper_remainder" != "$wrapper_host" ] && [ -n "$gradle_version" ]; then
    pass 'Gradle wrapper' "version $gradle_version ($wrapper_file)"
  else
    fail 'Gradle wrapper' "distribution URL is blank or malformed in $wrapper_properties"
  fi
elif command -v gradle >/dev/null 2>&1; then
  note 'Gradle' "$(command_version gradle --version)"
else
  fail 'Gradle' 'no wrapper properties and gradle is not on PATH'
fi

if [ "$os_name" = Darwin ]; then
  if command -v xcode-select >/dev/null 2>&1 && xcode-select -p >/dev/null 2>&1; then
    pass 'Xcode command-line tools' "$(xcode-select -p)"
  else
    fail 'Xcode command-line tools' 'xcode-select has no active developer directory'
  fi
  if command -v xcrun >/dev/null 2>&1 && xcrun -f metal >/dev/null 2>&1 && xcrun -f metallib >/dev/null 2>&1; then
    pass 'Metal compiler' "$(xcrun -f metal)"
  else
    fail 'Metal compiler (xcrun metal/metallib)' 'optional Xcode Metal toolchain is unavailable'
  fi
else
  note 'Metal compiler' 'not applicable outside macOS'
fi

if command -v glslangValidator >/dev/null 2>&1; then
  pass 'glslangValidator' "$(command_version glslangValidator --version)"
else
  fail 'glslangValidator' 'GLSL-to-SPIR-V compiler is not on PATH'
fi
if command -v spirv-val >/dev/null 2>&1; then
  pass 'spirv-val' "$(command_version spirv-val --version)"
else
  fail 'spirv-val' 'SPIR-V validation tool is not on PATH'
fi

if [ "$os_name" = Darwin ]; then
  vk_loader=''
  for candidate in /usr/local/lib/libvulkan.1.dylib /opt/homebrew/lib/libvulkan.1.dylib /usr/local/lib/libvulkan.dylib /opt/homebrew/lib/libvulkan.dylib; do
    [ -e "$candidate" ] && vk_loader="$candidate" && break
  done
  mvk_loader=''
  for candidate in /usr/local/lib/libMoltenVK.dylib /opt/homebrew/lib/libMoltenVK.dylib; do
    [ -e "$candidate" ] && mvk_loader="$candidate" && break
  done
  [ -n "$vk_loader" ] && pass 'Vulkan loader' "$vk_loader" || fail 'Vulkan loader' 'libvulkan dylib not found in standard Homebrew or /usr/local paths'
  [ -n "$mvk_loader" ] && pass 'MoltenVK' "$mvk_loader" || fail 'MoltenVK' 'libMoltenVK.dylib not found in standard Homebrew or /usr/local paths'
else
  if command -v vulkaninfo >/dev/null 2>&1; then pass 'Vulkan loader' "$(command_version vulkaninfo --summary)"; else fail 'Vulkan loader' 'vulkaninfo is not on PATH'; fi
  note 'MoltenVK' 'not applicable outside macOS'
fi

if command -v git >/dev/null 2>&1 && git rev-parse --is-inside-work-tree >/dev/null 2>&1; then
  commit="$(git rev-parse HEAD)"
  dirty="clean"
  [ -n "$(git status --porcelain)" ] && dirty="dirty"
  note 'Git' "$commit ($dirty)"
else
  note 'Git' 'not a Git worktree or git unavailable'
fi

if [ "$missing" -eq 0 ]; then
  printf '%s\n' 'READINESS: READY for local performance measurements'
else
  printf '%s\n' 'READINESS: NOT READY for local performance measurements; diagnostic inventory is still complete'
fi

if [ "$mode" = check ] && [ "$missing" -ne 0 ]; then exit 2; fi
exit 0
