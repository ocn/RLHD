# Renderer benchmark protocol

Do not compare results until `scripts/renderer-preflight.sh --check` exits `0` and its complete output is saved with the run.

## Freeze the comparison

Record before each run group:

- RLHD, RuneLite, rlawt, shader, and backend commits plus dirty state;
- macOS/Windows/Linux version, CPU/GPU, RAM, display model/resolution/refresh, JVM vendor/version/architecture, and power/thermal state;
- LWJGL, Vulkan loader, MoltenVK, validation layer, shader compiler, and generated-shader hashes;
- account/world, client window position and pixel size, UI scale, camera position/heading/pitch/zoom, route, assets/cache state, and start tick;
- every 117HD setting, including draw distance, extended loading, textures, shadows, dynamic lights, tiled lighting, parallax, anti-aliasing, frame cap, and synchronization mode.

Use the same RuneLite build, account state, scene, camera, resolution, display, UI pixels, settings, written manual route checklist, and warm-up for both backends. Save configuration exports and a reference screenshot. Do not use automated gameplay input; the checklist is executed manually and records deviations. A changed scene, missing tool, x86_64 JVM on arm64, thermal event, background update, or feature mismatch invalidates the comparison and is reported separately.

## Required scenes

1. **POH/instance:** fixed room and camera; low scene complexity control.
2. **Dense outdoor city:** fixed tile/camera with NPC/player population recorded; repeat when population differs materially.
3. **Maximum extended loading:** five-chunk setting and draw distance 100; fixed overlook/camera.
4. **High dynamic-light density:** fixed location/time/configuration with light count recorded.
5. **Movement/teleport route:** manually follow a written camera zoom/movement and teleport checklist; record deviations and streaming/compile recovery separately from steady state.

For an incomplete vertical slice, run only supported scenes and label results diagnostic. Do not compare incomplete Vulkan output with fully featured OpenGL as a backend performance verdict.

## Short-run method

1. Start a fresh client and reach the scene without including asset/shader compilation in samples.
2. Warm up for 60 seconds at the fixed camera.
3. Capture at least five independent 120-second runs per backend/scene, alternating backend order between complete run groups.
4. Preserve raw per-frame CPU and GPU timings; do not report only FPS averages.
5. Record artifacts/crashes, compilation/streaming events, skipped frames, present mode, effective swapchain extent, and thermal/power changes.
6. Report medians across runs plus every raw run; do not discard an outlier without a written invalidation reason.

## Metrics

| Layer | Required measurements |
| --- | --- |
| Frame | average frame time, median, p95, p99, standard deviation, FPS, 1% low, 0.1% low, dropped/skipped frames |
| RuneLite process | total CPU, render-thread CPU where available, RSS/footprint, allocations/resource counters, thread count |
| GPU/backend | GPU frame time/utilization/power where available, acquire/submit/present latency, in-flight depth, validation/timestamp errors |
| WindowServer/compositor | CPU, footprint/RSS, attributed samples where available, IOSurface/layer observations |
| System | idle CPU, memory pressure, swap, thermal state, display refresh/mode, foreground/background state |
| Correctness | image/readback result, artifact count, lifecycle result, app-owned object balance |

Use `frame time = 1,000 / FPS` only as a presentation conversion, not as a substitute for raw frame samples.

## Thirty-minute lifecycle run

After 10 minutes of warm-up, run at least 30 minutes while cycling resize, minimize/zero extent, restore, fullscreen, present mode, plugin toggle where supported, and manually checked movement/teleports. Include a real multi-display/Retina migration. Sample process, WindowServer, and GPU/resource telemetry at a fixed cadence and evaluate the preregistered stability threshold in [go-no-go-gates.md](go-no-go-gates.md).

Report MoltenVK teardown diagnostics verbatim. App-owned counters reaching zero do not identify driver, compositor, layer, or portability-runtime ownership.

## Long-duration boundary protocol

At feature parity, run an 8-hour milestone and a 24-hour final soak in the same scene. The preregistered provisional pass condition is: final-hour p95 frame time and steady-state RuneLite/WindowServer footprint each stay within 10% of hour one, with no monotonic native/GPU/app-owned resource growth. An incomplete opaque slice records diagnostics but cannot pass this production gate.

Use the same scene/settings and a saved route to distinguish state boundaries:

1. **RuneLite process:** measure degraded and fresh scenes; restart RuneLite only and repeat.
2. **User session/WindowServer:** if degradation remains, log out/in without rebooting and repeat after the same warm-up.
3. **Kernel/AGX:** if degradation remains, reboot and repeat after the same warm-up.

Capture the pre/post boundary metrics, uptime, process IDs, logs, and settings each time. Do not infer the responsible layer merely because a boundary clears the symptom; use that result to narrow the next diagnosis. A modern backend is not credited with fixing long-uptime degradation unless identical-scene controlled runs reproduce and eliminate it across the relevant boundary.

## Result validity and fallback

- The control is the unchanged OpenGL renderer.
- A Vulkan run must also test failed preflight/startup and successful return to OpenGL.
- Compare performance only at equivalent feature parity; otherwise report feasibility/correctness metrics only.
- Apply the preregistered feature-parity thresholds: no p95 regression over 5% in any scene, plus either at least 15% p95 improvement or at least 20% combined RuneLite+WindowServer CPU reduction in two demanding scenes, unless maintainers approved replacements before collection.
- Publish raw JSONL/CSV, exact commands, preflight output, screenshots/readbacks, and invalidated-run reasons.
- Any crash, corruption, validation warning/error, app-owned resource imbalance, or unbounded resource trend fails that run group.
