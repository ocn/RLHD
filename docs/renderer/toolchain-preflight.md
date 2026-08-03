# Toolchain preflight

Run from the repository root:

```sh
scripts/renderer-preflight.sh
scripts/renderer-preflight.sh --check
scripts/tests/test_renderer_preflight.sh
```

The first command is an inventory and always exits `0`, including when optional tools are absent. `--check` exits `2` when performance prerequisites are missing and `64` for an invalid argument. The script only reads command/version, filesystem, system, and Git state; it never downloads, installs, starts a build, or changes configuration.

It reports host OS/CPU, JVM architecture/version/vendor, wrapper Gradle version, Xcode and Metal compiler discovery, GLSLang/SPIR-V tools, Vulkan loader/MoltenVK discovery, and Git commit/dirty state. On macOS, performance readiness requires a native JVM, active Xcode command-line tools with `metal` and `metallib`, `glslangValidator`, `spirv-val`, a Vulkan loader, and `libMoltenVK.dylib`.

## Current local baseline (2026-08-02)

Only x86_64 Corretto 16 is installed. The optional Xcode Metal toolchain and `glslangValidator` are absent. Therefore this machine is **not ready for performance measurements**; the default preflight remains successful so it can document the gap without side effects. Apple documents Metal tooling as part of Xcode, and `metal`/`metallib` command-line use. [Apple Metal tools](https://developer.apple.com/metal/tools/) · [Metal command-line tools](https://developer.apple.com/library/archive/documentation/Miscellaneous/Conceptual/MetalProgrammingGuide/Dev-Technique/Dev-Technique.html)
