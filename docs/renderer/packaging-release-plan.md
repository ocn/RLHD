# Packaging, CI, release, and rollback plan

## Acceptance boundary

Current [RuneLite rejected-features policy](https://github.com/runelite/runelite/wiki/Rejected-or-Rolled-Back-Features) disallows JNI, subprocesses, reflection-based bypasses, and runtime vendor downloads in Plugin Hub plugins. rlawt's native loading also belongs to RuneLite core because plugin-local JAWT loading conflicts with the core GPU path. Therefore the Task 2/3 native bridge is a local feasibility artifact, not an acceptable Plugin Hub package.

The Vulkan control is Java after surface creation, but macOS still needs an accepted `CAMetalLayer`/JAWT owner and a MoltenVK distribution route. No production packaging work starts until RuneLite/rlawt maintainers choose one of these outcomes in writing:

1. rlawt or RuneLite core owns, signs, and ships a backend-neutral native surface API;
2. an explicitly reviewed native exception and artifact process is created; or
3. upstream distribution is declined and the work remains a supported local fork.

The closed [Plugin Hub PR #12885](https://github.com/runelite/plugin-hub/pull/12885) shows that technical code alone is insufficient: qualified GPU/Vulkan review ownership is a release gate.

## Artifact model

| Artifact | Contents | Must not contain |
| --- | --- | --- |
| Normal 117HD plugin jar | current Java/OpenGL renderer and resources | Vulkan control/spike classes, SPIR-V, MoltenVK, JNI, second LWJGL runtime |
| Developer Vulkan slice jar | Java 11 Vulkan backend, approved SPIR-V, manifest | downloaded-at-runtime code, bundled validation SDK, unsigned/unreviewed natives |
| macOS platform runtime, if accepted | pinned universal or separately signed arm64/x86_64 surface bridge and MoltenVK | ad hoc binaries, unpinned dylibs, runtime compiler toolchain |
| Windows/Linux future runtime | Java Vulkan backend plus accepted native WSI/runtime inputs | macOS native code or unconditional platform class loading |

Every release input requires pinned source, license/notice, SHA-256, reproducible build provenance where available, SBOM entry, code-signing identity, minimum OS/architecture, and update owner. Use RuneLite's existing LWJGL 3.3.2 line; do not shade or load a second LWJGL core/native set.

## CI matrix

| Lane | Required checks |
| --- | --- |
| Java 11 all OS | existing unit/integration tests, normal jar content, no Vulkan/native class initialization on default path |
| macOS arm64 OpenGL | current renderer smoke, fallback selection, plugin lifecycle |
| macOS arm64 Vulkan opt-in | shader compile/validation, GPU readback, Vulkan validation, failure injection, resize/suspend/fullscreen/Retina/shutdown, resource balance |
| macOS x86_64, if retained | same lifecycle/correctness checks on real or approved hosted Intel hardware; Rosetta is reported separately |
| Windows x86_64 OpenGL | unchanged renderer and fallback tests; no macOS/Vulkan runtime resolution |
| Linux x86_64 OpenGL | unchanged renderer and fallback tests under supported AWT window systems |
| Future native Vulkan Windows/Linux | added only after a separately reviewed WSI slice; same validation/readback/fallback gates |
| Packaging | Java major version 55, exact dependency set, jar allow/deny lists, native signatures/hashes, licenses/SBOM, offline startup |

CI cannot substitute for the manual 30-minute macOS lifecycle soak, display migration, frame capture/profiling, or long-duration session-state tests.

## Release stages

1. **Disposable control:** isolated source sets only; never selected by the plugin.
2. **Local-fork vertical slice:** developer flag, one opaque zone, OpenGL default, no public performance claim.
3. **Maintainer test build:** named reviewers and support owner; signed/pinned artifacts; explicit unsupported-hardware behavior; raw benchmark bundle.
4. **Opt-in preview:** only after correctness parity for the advertised features, cross-platform default/fallback CI, soak evidence, and an accepted distribution route.
5. **Broader default consideration:** separate ADR after feature parity and statistically comparable benchmarks; never implied by a successful slice.

Each stage publishes supported OS/architectures, known missing features, current source/runtime hashes, rollback instructions, and an end-of-support owner.

## Startup and rollback

- Backend selection occurs before `AWTContext.createGLContext()`; never attach GL and Metal/Vulkan layers to the same Canvas.
- The persisted default remains `OPENGL`. Vulkan requires an explicit developer/preview choice.
- Preflight or startup failure records a bounded reason and returns to OpenGL through a controlled plugin restart/reselection. It must not repeatedly crash-loop.
- Runtime device/surface failure terminalizes the Vulkan backend, releases app-owned resources, and offers OpenGL restart; it does not hot-swap contexts mid-frame.
- Keep the previous signed artifact and configuration migration path for at least one release window.
- A remotely distributed kill switch is acceptable only if RuneLite maintainers already have an approved configuration mechanism; do not add a new remote-code/config channel for this project.

## Automatic rollback triggers

Disable the preview and restore OpenGL-only release selection when any of these occurs:

- validation warnings/errors, crash, rendering corruption, or resource growth is reproducible in a supported lifecycle;
- the native signature/hash, loader, MoltenVK, or shader manifest does not match the release record;
- a platform cannot start OpenGL after Vulkan failure;
- upstream policy, Jagex compliance, reviewer ownership, or native maintenance ownership is withdrawn;
- p95/p99 frame time, CPU/WindowServer cost, or stability materially regresses against the agreed same-feature threshold.

Rollback is a release action, not evidence that Vulkan caused the original long-uptime issue. Preserve logs and raw captures for diagnosis.
