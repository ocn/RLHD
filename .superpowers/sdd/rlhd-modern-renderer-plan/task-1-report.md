# Task 1 report: architecture evidence and read-only preflight

## Delivered

- Added renderer ADR, dependency/ownership map, terminology, pinned source inventory, provenance register, preflight guide, benchmark protocol, and maintainer questions under `docs/renderer/`.
- Recorded the fork-first decision: Vulkan/MoltenVK for the first full-scene slice, direct Metal only for presentation control, and unchanged OpenGL default/fallback on every platform.
- Recorded separate Jagex-compliance, technical-feasibility, and RuneLite/Plugin-Hub acceptance decisions; Plugin Hub's JNI prohibition; the unavailable GPU/Vulkan review ownership rejection; ZoneRenderer's November 2025 OpenGL status; and PR #990's OpenGL-fallback scope.
- Added `scripts/renderer-preflight.sh`: no download, install, build, configuration, or tool rewrite; default mode exits `0`, `--check` exits `2` when prerequisites are absent, and invalid options exit `64`.
- Added deterministic shell tests, including a missing-tool fixture that verifies explicit Metal, GLSLang, and native-JVM readiness failures.
- Recorded the local 2026-08-02 baseline: x86_64 Corretto 16 on an arm64 host, Xcode Metal compiler and GLSLang absent, Vulkan loader and MoltenVK present. This is not performance-ready.
- Recorded the rlawt/JAWT ownership constraint: Vulkan/Metal selection must happen before `AWTContext.createGLContext()`; a Canvas cannot coexist with the rlawt overlay. A future macOS path owns a per-instance `CAMetalLayer` lifecycle and excludes gpu-vulkan's global/custom-present path.

## Verification

| Command / evidence | Result |
| --- | --- |
| `bash -n scripts/renderer-preflight.sh scripts/tests/test_renderer_preflight.sh` | Exit `0`. |
| `scripts/tests/test_renderer_preflight.sh` | Exit `0`: `renderer-preflight shell tests: PASS`. |
| `scripts/renderer-preflight.sh` | Exit `0`; correctly reported `READINESS: NOT READY` with x86_64 JVM on arm64 host, missing Metal compiler, GLSLang, and SPIR-V validation tool. |
| `scripts/renderer-preflight.sh --check` | Exit `2`, as designed for missing performance prerequisites. |
| `./gradlew test --console=plain` | Invoked with cache access; compilation and test-class preparation completed and fresh test XML files show six suites with zero failures/errors. The controller instructed no concurrent rerun after temporary toolchains were installed, so no further Gradle invocation was made. |
| `git diff --check` | Exit `0`. |

## Scope and concerns

No renderer production code, dependencies, installed tools, or external repositories changed. The performance preflight remains intentionally failing until a native arm64 JVM, Xcode Metal tools, GLSLang/SPIR-V tooling, and the recorded runtime prerequisites are present. The full Gradle terminal did not print its usual completion line before the controller halted concurrent suites; XML evidence is clean, but a future owner may run it once in the settled toolchain environment if an explicit full-suite exit-code receipt is required.

## Review-fix verification

- The preflight now parses `gradle-<version>-bin.zip` or `gradle-<version>-all.zip` from `distributionUrl` and reports the version. A blank or malformed URL is `MISSING`, rather than a misleading pass.
- A Java invocation must exit successfully and report both `java.version` and `os.arch`; otherwise it is `MISSING` and cannot yield ready status.
- `scripts/tests/test_renderer_preflight.sh` now requires the current host's `--check` result to be `2`, and deterministically proves a ready Linux/toolchain fixture (`0`), a broken Java fixture (`2`), and malformed Gradle wrapper metadata (`2`).
- Fresh command: `bash -n scripts/renderer-preflight.sh scripts/tests/test_renderer_preflight.sh && scripts/tests/test_renderer_preflight.sh` — exit `0`, `renderer-preflight shell tests: PASS`.

## Review-fix verification: Round 2

- The default wrapper properties path is resolved from `scripts/renderer-preflight.sh` to the repository root, independent of caller working directory.
- `distributionUrl` must use `http` or `https`, contain a non-empty host and path, have no whitespace, and end in `gradle-<semver-like-version>-{bin,all}.zip`. The parser accepts the repository's `8.10` URL and rejects blank metadata, `gradle-not-a-version-bin.zip`, and a `file:` URL. This matches the [Gradle Wrapper distribution URL format](https://docs.gradle.org/current/userguide/gradle_wrapper.html).
- Live-host strict status is environment-portable (`0` or `2`); isolated fixtures continue to require exact ready (`0`) and not-ready (`2`) results.
- From repository root: `scripts/tests/test_renderer_preflight.sh` — exit `0`, `renderer-preflight shell tests: PASS`.
- From `scripts/tests`: `./test_renderer_preflight.sh` — exit `0`, `renderer-preflight shell tests: PASS`.

## Review-fix verification: Round 3

- Added `docs/renderer/binding-compatibility.md`, backed by pinned RuneLite commit `348035815f2caeaba26a3fdb05309e0f0c204562`: production and spike Java bytecode remains release 11; all LWJGL Vulkan/JAWT/core modules and transitives align to RuneLite's `3.3.2` line; no second or shaded LWJGL core/native set is allowed.
- Updated the toolchain guide to distinguish the initial baseline from the non-persistent `/private/tmp/rlhd-toolchains` session provisioning: Temurin `21.0.12` arm64 and Vulkan SDK `1.4.350.1` with GLSLang/SPIR-V Tools are available, while Metal remains unavailable after `xcodebuild -downloadComponent metalToolchain` exits `70` on the Xcode plugin/system-framework mismatch. Strict readiness therefore fails only on Metal when those paths are active.
- Added an outside-repository test that changes to the worktree's parent, unsets `PREFLIGHT_GRADLE_WRAPPER_PROPERTIES`, invokes `./RLHD-modern-renderer/scripts/renderer-preflight.sh` through a relative path with deterministic tool fixtures, and requires the Gradle wrapper pass plus ready status.
- Fresh syntax check: exit `0`.
- Fresh shell tests from repository root and from `scripts/tests`: both exit `0` with `renderer-preflight shell tests: PASS`.

## Evidence correction: Round 4

- Corrected the Metal toolchain evidence: `xcrun -f metal` exits `0` and resolves the Xcode-default `metal` path, while `xcrun metal --version` exits `72` reporting a missing Metal Toolchain and `xcrun -f metallib` fails. The component download remains blocked: `xcodebuild -downloadComponent metalToolchain` exits `70` on the `IDESimulatorFoundation`/`DVTDownloads` symbol mismatch.
