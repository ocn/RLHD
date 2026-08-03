# Benchmark protocol

Do not compare results until `scripts/renderer-preflight.sh --check` exits `0` and its complete output is saved with the run.

1. Pin the source commits in `source-inventory.md`; record `git rev-parse HEAD` and dirty state.
2. Record macOS version, CPU/GPU model, display resolution/refresh, JVM vendor/version/architecture, Metal/MoltenVK, Vulkan loader, and shader-tool versions from preflight.
3. Use the same RuneLite revision, account state, scene/camera route, resolution, UI scale, draw distance, texture/shadow/anti-alias settings, and warm-up duration for OpenGL and the opt-in backend.
4. Warm up for 60 seconds; then capture at least five 120-second runs per backend. Do not mix renderer startup/asset-compilation time with steady-state frame data.
5. Save raw frame-time samples, p50/p95/p99 frame times, FPS, CPU/GPU utilization where available, dropped-frame count, crashes/artifacts, and exact command/configuration.
6. Report medians across runs and preserve raw data. A result from an x86_64 JVM on an arm64 host, a missing shader tool, or a missing portability runtime is diagnostic only, not a performance comparison.

The control is the unchanged OpenGL renderer. A full-scene Vulkan result must also exercise fallback selection and return to OpenGL after failure.
