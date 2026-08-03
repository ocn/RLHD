# Toolchain preflight

Run from the repository root:

```sh
scripts/renderer-preflight.sh
scripts/renderer-preflight.sh --check
scripts/tests/test_renderer_preflight.sh
```

The first command is an inventory and always exits `0`, including when optional tools are absent. `--check` exits `2` when performance prerequisites are missing and `64` for an invalid argument. The script only reads command/version, filesystem, system, and Git state; it never downloads, installs, starts a build, or changes configuration.

It reports host OS/CPU, JVM architecture/version/vendor, wrapper Gradle version, Xcode and Metal compiler discovery, GLSLang/SPIR-V tools, Vulkan loader/MoltenVK discovery, and Git commit/dirty state. On macOS, performance readiness requires a native JVM, active Xcode command-line tools with `metal` and `metallib`, `glslangValidator`, `spirv-val`, a Vulkan loader, and `libMoltenVK.dylib`.

## Initial captured baseline (2026-08-02)

The initial inventory found only x86_64 Corretto 16. The optional Xcode Metal toolchain and `glslangValidator` were absent, so that baseline was not ready for performance measurements.

## Provisioned session state (2026-08-02)

Project-local, temporary tools now exist under `/private/tmp/rlhd-toolchains` for this session:

- Temurin `21.0.12` arm64.
- Vulkan SDK `1.4.350.1`, including `glslangValidator` and SPIR-V Tools.
- A discoverable Vulkan loader and MoltenVK runtime.

This provisioning is under `/private/tmp`; it is not a system installation and must not be assumed to persist into another session. When these project-local paths are active, strict preflight remains **NOT READY only because the Metal compiler is unavailable**. `xcodebuild -downloadComponent metalToolchain` exits `70` on the current Xcode plugin/system-framework mismatch, so `xcrun -f metal` and `xcrun -f metallib` still fail. Apple documents Metal tooling as an Xcode component and the `metal`/`metallib` command-line flow. [Apple Metal tools](https://developer.apple.com/metal/tools/) · [Metal command-line tools](https://developer.apple.com/library/archive/documentation/Miscellaneous/Conceptual/MetalProgrammingGuide/Dev-Technique/Dev-Technique.html)
