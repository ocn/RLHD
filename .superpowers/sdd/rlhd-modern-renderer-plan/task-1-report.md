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
