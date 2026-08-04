# Vulkan ZoneRenderer Vertical-Slice Implementation Plan

> **2026-08-04 pivot:** Tasks completed through the surface-free Task 1 oracle remain valid evidence. Do not execute the remaining RLHD-local Vulkan surface, swapchain, presentation, or backend-selection tasks. Maintainer guidance and source inspection selected `gpu-vulkan` as the foundation for future feature work. See [ADR-0002](ADR-0002-gpu-vulkan-foundation.md), the [PR #20 assessment](gpu-vulkan-pr20-assessment.md), and the [replacement feature-port plan](gpu-vulkan-feature-port-plan.md).

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Prove one real, static, opaque 117HD zone can be prepared once and rendered by either unchanged OpenGL or an opt-in Vulkan backend, without beginning a production port.

**Architecture:** Extract a small CPU-owned scene boundary from `SceneUploader`; keep API-specific allocation, synchronization, render passes, and presentation inside backends. Reuse the reviewed Vulkan control only after its entry gates pass. OpenGL remains the default and fallback on every operating system.

**Tech Stack:** Java 11, RuneLite `DrawCallbacks`, 117HD, rlawt/JAWT, LWJGL 3.3.2, Vulkan 1.2, MoltenVK 1.4.2 on macOS, GLSL-to-SPIR-V via `glslangValidator`.

## Global constraints

- Do not change default renderer selection or load Vulkan/native libraries on a normal OpenGL start.
- Do not remove or weaken `ZoneRenderer` or `LegacyRenderer` on macOS, Windows, or Linux.
- Do not expose GL/Vulkan handles or enums through the scene-preparation seam.
- Do not add JNI or native artifacts to a Plugin Hub submission without written RuneLite/rlawt acceptance.
- Treat performance as diagnostic until feature parity; do not claim an uptime-regression fix from the slice.
- Use written manual scene/movement checklists only; do not add automated gameplay input.
- Stop after each task for independent code/spec review. Do not start the next task when its gate fails.

---

## Ownership and proposed interfaces

| Owner | Responsibility |
| --- | --- |
| 117HD scene preparation | Convert RuneLite scene/model data into immutable CPU packed vertex/face streams, opaque draw ranges, an exact per-face material association, and camera/viewport/light values. |
| 117HD backend adapter | Upload prepared data; translate semantic passes into OpenGL or Vulkan resources and commands. |
| RuneLite | `DrawCallbacks`, render-thread lifecycle, GPU flags, Canvas, and UI pixel production. |
| rlawt or RuneLite core | Backend-neutral native AWT surface lifecycle and packaged native bridge, if accepted. |
| LWJGL | Java Vulkan bindings, aligned to RuneLite's 3.3.2 runtime; no shaded second copy. |
| MoltenVK | macOS Vulkan portability runtime; version, license, signing, and loader configuration are release inputs. |

The names below are proposed contracts for the slice; approve them in Task 1 before implementation:

```java
interface ZoneGeometrySink {
    void accept(PreparedZoneGeometry geometry);
}

final class PreparedZoneGeometry {
    IntBuffer opaqueVertices();
    IntBuffer alphaVertices();
    IntBuffer faceMetadata();
    List<PreparedDrawRange> drawRanges();
    List<PreparedFaceMaterials> faceMaterials();
}

final class PreparedFaceMaterials {
    int texturedFaceIndex();
    int materialIdA();
    int materialIdB();
    int materialIdC();
}

final class PreparedDrawRange {
    int firstVertex();
    int vertexCount();
    PreparedPass pass();
}

interface RendererBackend extends AutoCloseable {
	void uploadZone(ZoneKey key, PreparedZoneGeometry geometry);
	FrameOutcome render(PreparedFrame frame);
	void destroyZone(ZoneKey key);
}

interface SurfaceProvider extends AutoCloseable {
    SurfaceExtent extent();
    boolean isRenderable();
}

interface VulkanSurfaceProvider extends SurfaceProvider {
    long createSurface(long vkInstance);
}

final class PreparedFrame {
	ZoneKey zone();
	CameraUniforms camera();
	SurfaceExtent viewport();
	PreparedUiTexture ui();
}
```

Task 1 approves the packed-stream contract above. Each 28-byte vertex record contains one packed position, UVW,
normal, and textured-face index. Each 36-byte face-metadata record contains three independently packed material
words; `PreparedFaceMaterials.texturedFaceIndex()` is the exact index stored by the corresponding packed vertices,
and its A/B/C IDs preserve those three associations. Draw ranges do not claim a single material invariant.
`PreparedPass` initially has only `OPAQUE`. All returned buffers are zero-based read-only views whose private
mutable backing buffers are transferred to `PreparedZoneGeometry`; no mutable alias may escape preparation.
The ordinary OpenGL path writes directly to its existing mapped buffers through the shared generation routine,
while `prepareZone` is an explicit capture/backend path. `ZoneKey` contains world-view ID, zone X/Z, and a generation
so replacement cannot alias in-flight work. `PreparedFrame` carries immutable camera, viewport, one top-level zone
reference, and a fully described UI snapshot (dimensions, row stride, pixel format, and read-only pixels); it carries
no GPU ownership. `FrameOutcome` distinguishes rendered, suspended-zero-extent, rejected-input, and backend-failure
results. The Vulkan surface handle exists only between the platform adapter and Vulkan backend: the backend destroys
`VkSurfaceKHR` before the provider releases its borrowed native layer.

## Task 0: Resolve entry gates

**Files:**

- Review: `docs/renderer/go-no-go-gates.md`
- Record decisions: `docs/renderer/maintainer-questions.md`
- Review prior art: pinned `dennisdevulder/gpu-vulkan`

- [ ] Obtain named 117HD, RuneLite, and rlawt owners for renderer review, native-surface ownership, and packaging.
- [x] Record the directional decision to collaborate with `gpu-vulkan` rather than build independently. Exact contribution repository, primary-renderer seam, and reviewer remain blocking questions under ADR-0002.
- [ ] Complete the Task 4 30-minute lifecycle and multi-display checks; record footprint samples and whether the 50/49 MB diagnostic plateaus.
- [ ] Run `scripts/renderer-preflight.sh --check` and save its complete output.
- [ ] Stop with no code changes unless every entry gate is `GO`.

## Task 1: Characterize and extract the CPU seam

**Files:**

- Modify: `src/main/java/rs117/hd/renderer/zone/SceneUploader.java`
- Create: `src/main/java/rs117/hd/renderer/zone/PreparedZoneGeometry.java`
- Create: `src/main/java/rs117/hd/renderer/zone/PreparedDrawRange.java`
- Create: `src/main/java/rs117/hd/renderer/zone/PreparedFaceMaterials.java`
- Create: `src/main/java/rs117/hd/renderer/zone/ZoneGeometrySink.java`
- Test: `src/test/java/rs117/hd/renderer/zone/PreparedZoneGeometryTest.java`

- [ ] Capture BASE mapped-uploader goldens for a fixed scene, including packed streams, positions/limits, level/roof metadata, draw ranges, per-face A/B/C material IDs, and stable hashes.
- [ ] Implement immutable prepared geometry and a sink without moving GL allocation out of `ZoneUploadJob`; transfer private prepared-buffer ownership into zero-based read-only views.
- [ ] Keep ordinary OpenGL upload writing directly to its existing mapped buffers through the shared generation routine; make `prepareZone` explicit and assert both paths against the independent BASE goldens.
- [ ] Run `./gradlew test --tests '*PreparedZoneGeometryTest'` and the existing renderer tests; require exact parity.
- [ ] Independently review the seam for GL enums, handles, descriptor bindings, or synchronization; any occurrence is a task failure.

## Task 2: Render one opaque static zone through Vulkan

Task 2 is split because the current macOS host has repeatedly panicked in WindowServer/DCP presentation. Task 2A is
CPU/offline only and cannot establish renderer correctness. Task 2B's surface-free rung has now run; every WSI and
presentation rung remains `NOT RUN`. Full Task 2 completion requires both parts.

### Task 2A: Lock the opaque-zone contract offline

**Files:**

- Create: `src/main/java/rs117/hd/renderer/RendererBackend.java`
- Create: `src/main/java/rs117/hd/renderer/FrameOutcome.java`
- Create: `src/main/java/rs117/hd/renderer/ZoneKey.java`
- Create: `src/main/java/rs117/hd/renderer/CameraUniforms.java`
- Create: `src/main/java/rs117/hd/renderer/SurfaceExtent.java`
- Create: `src/main/java/rs117/hd/renderer/PreparedUiTexture.java`
- Create: `src/main/java/rs117/hd/renderer/PreparedFrame.java`
- Create: `spikes/vulkan-opaque-slice/src/main/java/rs117/hd/spikes/vulkan/opaque/VulkanOpaqueZoneContract.java`
- Create: `spikes/vulkan-opaque-slice/src/main/java/rs117/hd/spikes/vulkan/opaque/VulkanZoneResourcePlan.java`
- Create: `spikes/vulkan-opaque-slice/src/main/shaders/opaque.vert`
- Create: `spikes/vulkan-opaque-slice/src/main/shaders/opaque.frag`
- Create: `spikes/vulkan-opaque-slice/src/main/shaders/ui.vert`
- Create: `spikes/vulkan-opaque-slice/src/main/shaders/ui.frag`
- Test: `src/test/java/rs117/hd/renderer/RendererBackendContractTest.java`
- Test: `src/test/java/rs117/hd/renderer/PreparedFrameTest.java`
- Test: `spikes/vulkan-opaque-slice/src/test/java/rs117/hd/spikes/vulkan/opaque/VulkanOpaqueZoneContractTest.java`
- Modify: `build.gradle`

- [x] Write failing tests for immutable frame/UI snapshots, generation-safe zone identity, upload replacement/destroy semantics, unknown-zone rejection, zero-extent suspension, partial-upload rollback, aggregated teardown, and resource-ledger underflow.
- [x] Define the opaque vertex binding as one 28-byte record: signed-short position at offset 0, half-float UVW at 8, signed-short normal at 16, and signed face reference at 24. Serialize vertex and 36-byte face records little-endian and validate opaque counts, triangle-aligned ranges, face-reference bounds, reversed winding, and Task 1 BASE material associations.
- [x] Define a 72-byte vertex push range (`mat4 clipFromWorld` at 0 and `ivec2 sceneBase` at 64), reverse-Z depth (`D32_SFLOAT`, clear 0, `GREATER_OR_EQUAL`), negative-height dynamic viewport, BGRA8 sRGB-nonlinear color, and premultiplied UI composition as a pure manifest with CPU projection/depth/winding tests.
- [x] Compile and validate all four real shaders offline for Vulkan 1.2. Generate deterministic JSON reflection and fail on interface drift: opaque locations 0-3, scalar metadata `ArrayStride=4` at set 0/binding 0, push offsets/size, fragment output, and UI sampler set 0/binding 0. Java tests separately pin formats, offsets, stages, and pipeline state not represented by reflection.
- [x] Keep Vulkan-specific classes, SPIR-V, reflection JSON, LWJGL, and native dependencies out of the production JAR/runtime. Task 2A must never load a Vulkan loader, enumerate a device, create a native layer/surface/swapchain, submit, present, or open a window.
- [x] Independently review and commit Task 2A as partial progress only. At that checkpoint, record every physical-device, MoltenVK pipeline, clipping/interpolation/culling, gamma/orientation, UI blend, validation, readback, acquire/submit/present, and resource-retirement claim as `NOT RUN`; later live evidence is recorded separately below.

### Task 2B: Execute the live opaque-zone slice safely

> **Status:** completed surface-free evidence is retained; every uncompleted RLHD-local production/presentation item below is superseded and must not be executed.

**Files (provisional until owner and packaging gates resolve):**

- Create: `src/main/java/rs117/hd/renderer/vulkan/VulkanRendererBackend.java`
- Create: `src/main/java/rs117/hd/renderer/vulkan/VulkanZoneResources.java`
- Create: `src/main/java/rs117/hd/renderer/vulkan/VulkanSurfaceProvider.java`
- Test: `src/test/java/rs117/hd/renderer/vulkan/VulkanOpaqueZoneIntegrationTest.java`
- Maintain: `docs/renderer/panic-investigation-log.md`
- Append automatically: `docs/renderer/panic-investigation-runs.jsonl`

- [x] Establish a sparse, storage-forced safety journal with intent/completion records for lifecycle transitions and timing-log selection, a one-second presentation heartbeat, an append-only incident ledger, and a fail-closed one-variable risk ladder. Live Gradle runs require exactly one rung; resize/suspend/restore, unlocked mode, and fullscreen cannot be combined. A missing terminal record is evidence of an incomplete JVM run, not proof of a renderer-caused panic.
- [x] Register the bounded Task 1 BASE offscreen oracle in `shader-correctness-plan.md` and execute it on the surface-free M3 Ultra/MoltenVK host. OpenGL-equivalence tolerances and any presentation topology remain separate gates.
- [x] Write a failing integration test that uploads Task 1's fixed geometry, renders the exact UI over the opaque zone, and compares GPU readback with the pinned semantic, all-pixel coverage, sample, and composition expectations.
- [ ] Move only the reviewed Task 4 loader/device/surface/swapchain/synchronization/resource-lifetime mechanisms required by this path behind `SurfaceProvider` and `RendererBackend`; retain failure-injection tests.
- [ ] Implement staging uploads, one opaque pipeline, depth, viewport/camera push data, UI composition, offscreen readback, and presentation. Exclude alpha, textures/material effects, dynamic models, shadows, lighting, water, and post-processing.
- Surface-free implementation checkpoint: device/queue selection, resource allocation, opaque/depth/UI pipelines, row-stride-aware UI upload, queue submission, host readback, and tracked teardown compile in the isolated `vulkanOffscreenSlice` source set. Presentation and non-macOS GPU execution remain unverified and separate.
- Live checkpoint `R-20260803-007`: the normalized offscreen opaque/UI/readback test passed on Apple M3 Ultra through MoltenVK 1.4.2 with the Khronos validation layer enabled, zero validation warnings/errors from instance creation through destruction, and balanced app-tracked handles after close. Presentation, OpenGL full-image equivalence, production backend wiring, and non-macOS GPU execution remain unverified.
- Task 1 checkpoint `R-20260804-018`: fresh execution of commit `d8e430f67add211e02ac7f89f317fd85a8e9f191` passed exact mapped-uploader hashes, 18-vertex BASE geometry, all-pixel analytic coverage, two RGB samples at tolerance 2, exact alpha, exact UI-over-scene composition with one-value UNORM blend tolerance, full validation scope, and post-close handle balance. OpenGL full-image equivalence remains unverified.
- [ ] Run with validation enabled; require zero warnings/errors, golden/readback acceptance, balanced acquire/submit/present counters, and zero app-tracked live objects after close.
- [ ] Execute the live path in order: offscreen readback; layer attachment; surface/swapchain creation; first FIFO present; sustained windowed FIFO; resize/suspend/restore; unlocked mode; fullscreen; display migration. Review the journal and timing log before advancing one rung.

## Task 3: Add opt-in selection and safe fallback

> **Status:** superseded by the gpu-vulkan foundation decision. Do not add Vulkan selection to `HdPlugin`.

**Files:**

- Modify: `src/main/java/rs117/hd/HdPlugin.java`
- Modify: `src/main/java/rs117/hd/HdPluginConfig.java`
- Test: `src/test/java/rs117/hd/RendererSelectionTest.java`

- [ ] Write tests proving OpenGL is selected by default on macOS, Windows, and Linux and no Vulkan/native class initializes on those paths.
- [ ] Add a developer-only opt-in that chooses a backend before `AWTContext.createGLContext()`.
- [ ] Make Vulkan preflight/startup failure record its reason and trigger a controlled OpenGL reselection/restart without leaving a GL context and Metal/Vulkan surface on the same Canvas.
- [ ] Test plugin enable/disable, login, world hop, resize, zero extent, fullscreen, Retina scale, shutdown, and Vulkan-start failure.
- [ ] Build the normal jar and prove it retains existing OpenGL behavior on all CI operating systems.

## Task 4: Decide whether the slice warrants expansion

> **Status:** benchmark and correctness criteria remain reusable, but expansion occurs through the replacement gpu-vulkan feature-port plan.

**Files:**

- Record results: `docs/renderer/vertical-slice-results.md`
- Follow: `docs/renderer/benchmark-protocol.md`
- Follow: `docs/renderer/go-no-go-gates.md`

- [ ] Run five 120-second OpenGL and Vulkan captures after identical 60-second warm-ups in each required scene.
- [ ] Complete lifecycle, 30-minute movement/teleport, and long-duration restart/logout/reboot boundary tests.
- [ ] Compare correctness, CPU/GPU/WindowServer metrics, frame-time distributions, and resource growth without extrapolating missing features.
- [ ] Obtain independent renderer-owner review of code, raw data, shader artifacts, packaging boundary, and fallback.
- [ ] Stop and retain OpenGL-only production when any exit gate fails. A passing slice authorizes planning the next single feature, not a wholesale port.

The incomplete opaque slice cannot make a production performance decision. Feature-parity preview evidence later requires the preregistered 5% regression ceiling, 15% p95 or 20% combined-CPU benefit test, and 8-hour/24-hour soak thresholds in `go-no-go-gates.md`.

## Later slices, each separately gated

If Task 4 passes, add exactly one semantic capability per reviewed slice: textured materials; alpha/dynamic models; shadow maps; tiled/dynamic lighting; water; post-processing; then native Vulkan surface providers for Windows and Linux. Every slice repeats golden/readback, lifecycle, validation, resource-balance, fallback, and same-feature benchmark checks. OpenGL remains available until a separate production and deprecation decision is accepted.
