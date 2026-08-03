# Vulkan ZoneRenderer Vertical-Slice Implementation Plan

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
| 117HD scene preparation | Convert RuneLite scene/model data into immutable CPU vertex/UV/normal buffers, draw ranges, semantic material IDs, camera/viewport/light values. |
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
    IntBuffer vertices();
    FloatBuffer uvs();
    FloatBuffer normals();
    List<PreparedDrawRange> drawRanges();
}

final class PreparedDrawRange {
    int firstVertex();
    int vertexCount();
    int materialId();
    PreparedPass pass();
}

interface RendererBackend extends AutoCloseable {
    void uploadZone(int zoneId, PreparedZoneGeometry geometry);
    void render(PreparedFrame frame);
    void destroyZone(int zoneId);
}

interface SurfaceProvider extends AutoCloseable {
    SurfaceExtent extent();
    boolean isRenderable();
}

interface VulkanSurfaceProvider extends SurfaceProvider {
    long createSurface(long vkInstance);
}

final class PreparedFrame {
    int zoneId();
    CameraUniforms camera();
    SurfaceExtent viewport();
    IntBuffer uiPixels();
}
```

`PreparedPass` initially has only `OPAQUE`. All returned buffers are read-only views. `PreparedFrame` carries camera, viewport, one top-level zone reference, and UI pixels; it carries no GPU ownership. The Vulkan surface handle exists only between the platform adapter and Vulkan backend: the backend destroys `VkSurfaceKHR` before the provider releases its borrowed native layer.

## Task 0: Resolve entry gates

**Files:**

- Review: `docs/renderer/go-no-go-gates.md`
- Record decisions: `docs/renderer/maintainer-questions.md`
- Review prior art: pinned `dennisdevulder/gpu-vulkan`

- [ ] Obtain named 117HD, RuneLite, and rlawt owners for renderer review, native-surface ownership, and packaging.
- [ ] Record whether to collaborate with `gpu-vulkan`, extract a bounded module, or build independently, including license/API reasons.
- [ ] Complete the Task 4 30-minute lifecycle and multi-display checks; record footprint samples and whether the 50/49 MB diagnostic plateaus.
- [ ] Run `scripts/renderer-preflight.sh --check` and save its complete output.
- [ ] Stop with no code changes unless every entry gate is `GO`.

## Task 1: Characterize and extract the CPU seam

**Files:**

- Modify: `src/main/java/rs117/hd/renderer/zone/SceneUploader.java`
- Create: `src/main/java/rs117/hd/renderer/zone/PreparedZoneGeometry.java`
- Create: `src/main/java/rs117/hd/renderer/zone/PreparedDrawRange.java`
- Create: `src/main/java/rs117/hd/renderer/zone/ZoneGeometrySink.java`
- Test: `src/test/java/rs117/hd/renderer/zone/PreparedZoneGeometryTest.java`

- [ ] Add a characterization test that records vertex/UV/normal counts, draw ranges, material IDs, and stable buffer hashes for a fixed scene fixture through the current uploader.
- [ ] Implement immutable prepared geometry and a sink without moving GL allocation out of `ZoneUploadJob` yet.
- [ ] Route the same CPU data to the existing GL upload and assert the characterization values are unchanged.
- [ ] Run `./gradlew test --tests '*PreparedZoneGeometryTest'` and the existing renderer tests; require exact parity.
- [ ] Independently review the seam for GL enums, handles, descriptor bindings, or synchronization; any occurrence is a task failure.

## Task 2: Render one opaque static zone through Vulkan

**Files:**

- Create: `src/main/java/rs117/hd/renderer/vulkan/VulkanRendererBackend.java`
- Create: `src/main/java/rs117/hd/renderer/vulkan/VulkanZoneResources.java`
- Create: `src/main/java/rs117/hd/renderer/vulkan/VulkanSurfaceProvider.java`
- Create: `src/main/java/rs117/hd/renderer/PreparedFrame.java`
- Create: `src/main/resources/rs117/hd/renderer/vulkan/opaque.vert.glsl`
- Create: `src/main/resources/rs117/hd/renderer/vulkan/opaque.frag.glsl`
- Test: `src/test/java/rs117/hd/renderer/vulkan/VulkanOpaqueZoneIntegrationTest.java`

- [ ] Write a failing integration test that uploads Task 1's fixed prepared geometry, renders one top-level opaque static zone plus the exact UI texture, and compares GPU readback with the approved golden/tolerance in `shader-correctness-plan.md`.
- [ ] Move only the reviewed Task 4 surface/swapchain/resource-lifetime mechanisms required by this path behind `SurfaceProvider` and `RendererBackend`; retain their failure-injection tests.
- [ ] Compile and validate the two real shaders offline for Vulkan 1.2; fail the build on `spirv-val` or reflection-layout mismatch.
- [ ] Implement one vertex buffer, one opaque pipeline, depth, viewport/camera data, UI composition, and presentation. Exclude alpha, textures/material effects, dynamic models, shadows, lighting, water, and post-processing.
- [ ] Run the integration test with validation enabled; require zero warnings/errors, balanced acquire/submit/present counters, and zero app-tracked live objects after close.

## Task 3: Add opt-in selection and safe fallback

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
