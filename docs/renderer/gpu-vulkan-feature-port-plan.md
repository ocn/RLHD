# Incremental 117HD Features on gpu-vulkan Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add 117HD rendering capabilities one reviewed feature at a time to the existing cross-platform `gpu-vulkan` renderer, without duplicating its platform/frame lifecycle or changing released 117HD OpenGL behavior.

**Architecture:** `gpu-vulkan` remains the deep platform module: it owns `DrawCallbacks`, Vulkan resources, surfaces, synchronization, UI, presentation, and teardown. HD modules supply semantic scene data and HD render passes through the maintainer-approved primary-renderer seam. The existing RLHD prepared-geometry fixture and oracle become conformance inputs, not production platform code.

**Tech Stack:** Java 11 bytecode built with JDK 21, RuneLite `DrawCallbacks`, `gpu-vulkan` main `12f8db08`, macOS preservation commit `3b5cbe5d`, LWJGL core/JAWT 3.3.2, LWJGL Vulkan 3.3.6 as currently pinned upstream, Vulkan, MoltenVK, GLSL-to-SPIR-V via `glslangValidator`.

## Global Constraints

- Work in a separate fork/worktree of the maintainer-selected `gpu-vulkan` or parent `runelite-vkport` repository; do not implement the port in RLHD until ownership changes.
- Keep released 117HD OpenGL unchanged on macOS, Windows, and Linux.
- Do not duplicate instance, device, surface, swapchain, frame sync, UI, readback, or presentation code in HD packages.
- Do not enable both `BaseRenderer` and an HD primary renderer for the same scene.
- Keep HD scene/pass code platform-neutral; Cocoa/JAWT/MoltenVK/Win32/X11 types are forbidden outside existing platform/backend packages.
- Treat PR #20 custom Metal presentation and standard `vkQueuePresentKHR` as separate risk rungs.
- Do not claim the relayed 200-hour soak as reproduced without commit-bound logs and matching configuration.
- One semantic feature per reviewed slice. A passing slice authorizes only the next feature.
- Add `Assisted-by:` commit trailers whenever an assistant materially shapes code or design, matching the repository contribution rule.

---

### Task 0: Confirm contribution repository, seam, and soak provenance

**Files:**
- Record in this package: `docs/renderer/maintainer-questions.md`
- Review: `docs/renderer/gpu-vulkan-pr20-assessment.md`

**Interfaces:**
- Consumes: maintainer recommendation and pinned source assessment.
- Produces: named repository/branch, named reviewer, selected primary-renderer model, and a commit-bound macOS baseline.

- [ ] Ask whether work targets standalone `gpu-vulkan` or the parent `runelite-vkport` tree; record one repository URL and branch.
- [ ] Ask whether HD features deepen `BaseRenderer` or use an exclusive primary-renderer slot. Record one answer; additive registration is rejected.
- [ ] Ask for the exact commit, macOS build, hardware, JDK, loader, MoltenVK version, present path, FPS mode, validation state, display topology, and workload behind the approximately 200-hour result.
- [ ] Ask who will review HD scene/material/shader work and whether local/standalone macOS delivery is the near-term target.
- [ ] Stop without code if the repository, primary-renderer model, or reviewer remains ambiguous.
- [ ] Commit the recorded answers before cloning or changing upstream code.

### Task 1: Establish an immutable gpu-vulkan baseline

**Files:**
- Create in selected repo: `docs/hd-port/baseline.md`
- Create in selected repo: `docs/hd-port/runs.jsonl`
- Modify in selected repo: `.gitignore` only if captured build artifacts need an ignored directory.

**Interfaces:**
- Consumes: exact repository, branch, and commits from Task 0.
- Produces: reproducible CPU/build baseline and a presentation-free artifact inventory.

- [ ] Create an isolated worktree at exact main commit `12f8db08c8bac0d0180f96738b1fd9b075215496`; cherry-pick macOS preservation commit `3b5cbe5d42c26915c6d34d1df6613db74dd8c48b`, whose PR base is older `a631534559474103cf05e6abd46962692ef5364e`, only on a named macOS branch and review every conflict.
- [ ] Record `git rev-parse HEAD`, `git status --porcelain`, JDK, Gradle, LWJGL, Vulkan loader, MoltenVK, shader compiler, native bridge SHA-256, `file`, `codesign -dv`, and linked frameworks in `baseline.md`.
- [ ] Run `JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home ./gradlew --no-daemon --rerun-tasks test`; require all tests to execute, not merely report `UP-TO-DATE`.
- [ ] Run the shader freshness/build path and `shadowJar`; verify Java 11 class version and that the expected SPIR-V/native resources are present.
- [ ] Assert that no test above opens an AWT window or enters surface/swapchain/presentation code.
- [ ] Commit the baseline document and no generated binaries beyond those the upstream repository intentionally tracks.

### Task 2: Make primary scene ownership explicit

**Files in selected gpu-vulkan repo:**
- Modify: `src/main/java/com/gpuvulkan/GpuVulkanPlugin.java`
- Modify: `src/main/java/com/gpuvulkan/extension/runtime/RenderExtensions.java`
- Modify or create according to Task 0's approved model: `src/main/java/com/gpuvulkan/scene/PrimarySceneRenderer.java`
- Test: `src/test/java/com/gpuvulkan/PrimarySceneOwnershipTest.java`

**Interfaces:**
- Consumes: Task 0's approved deepening of `BaseRenderer` or exclusive renderer model.
- Produces: exactly one primary scene owner per frame; auxiliary FSR/recording extensions remain additive.

The preferred exclusive interface, if approved, is:

```java
public interface PrimarySceneRenderer extends VulkanRenderExtension {}
```

`RenderExtensions` must hold one `PrimarySceneRenderer` separately from auxiliary extensions. Scene/model capture and `recordScenePass` go only to the primary renderer. `scenePassRedirect`, `recordAfterComposite`, and other auxiliary hooks continue to fan out.

- [ ] Write `PrimarySceneOwnershipTest` with a base fake, HD fake, and auxiliary fake; assert one scene capture/draw, auxiliary post hooks, rejection of a second primary, and close ordering.
- [ ] Run `./gradlew --no-daemon test --tests '*PrimarySceneOwnershipTest'`; require the test to fail because the primary slot does not exist.
- [ ] Implement only the approved ownership model. Do not add HD rendering yet.
- [ ] Run the focused test and full `./gradlew --no-daemon test`; require existing FSR/recorder registration behavior to remain green.
- [ ] Independently review callback ordering, failure removal, close behavior, and mid-startup registration before commit.
- [ ] Commit with `Assisted-by:` disclosure when applicable.

### Task 3: Migrate the fixed BASE conformance fixture

**Files in selected gpu-vulkan repo:**
- Create: `src/test/java/com/gpuvulkan/hd/Task1BaseFixture.java`
- Create: `src/test/java/com/gpuvulkan/hd/Task1BaseFixtureTest.java`
- Create: `src/test/java/com/gpuvulkan/hd/HdOpaqueOffscreenTest.java`
- Create: `src/main/java/com/gpuvulkan/hd/HdPreparedZone.java`
- Modify if required by the fixed 28-byte record: `src/main/java/com/gpuvulkan/gfx/RenderPipelineDesc.java`
- Modify if required by that public format: `src/main/java/com/gpuvulkan/backend/GfxRenderPipeline.java`
- Modify to expose backend-owned readback: `src/main/java/com/gpuvulkan/gfx/RenderTarget.java`
- Modify to implement readback: `src/main/java/com/gpuvulkan/backend/GfxRenderTarget.java`
- Create: `src/main/shaders/gpuvulkan/hd_opaque.vert`
- Create: `src/main/shaders/gpuvulkan/hd_opaque.frag`
- Generate using the existing shader task: matching committed `.spv` resources.

**Interfaces:**
- Consumes: gpu-vulkan `RenderDevice`, `RenderTarget`, `RenderEncoder`, and Task 2 primary ownership.
- Produces: immutable HD opaque-zone input and the same pinned semantic/image oracle proven in RLHD.

The fixture must retain these independent identifiers:

```text
opaque vertices: eb34eee67ce381cfeccf1a3917ca193fc47b4cd2f8badd64ea19b174a946db8f
face metadata:   0d44981e3cf76b1413b172a9db5a08b1e6de170a65e2c35e6f098df8d3c02d0c
oracle:          4577074de8e78763bc9a4c63aedcf179d68746ba6958d82dde2cc2539e51f2df
```

- [ ] Copy the numeric fixture and canonical oracle descriptor with the original RLHD source/commit attribution, preserving 18 vertices, 36 metadata integers, camera, 64x64 extent, all-pixel mask, samples, alpha, UI quadrants, and tolerances.
- [ ] Write CPU tests for exact hashes, immutability, byte order, vertex/metadata record sizes, and camera values; run them red before implementing `HdPreparedZone`.
- [ ] Add the minimum storage-buffer or vertex-format capability needed by the existing `RenderDevice`; do not expose raw Vulkan handles to the HD module.
- [ ] Add a surface-free offscreen readback test through backend-owned abstractions. If `RenderTarget` cannot read back, deepen that module with a test-only/readback result rather than reaching into raw handles from HD code.
- [ ] Run validation-enabled offscreen rendering and require the unchanged all-pixel oracle, zero validation warnings/errors, and balanced resource closure.
- [ ] Compare the new output with the committed RLHD `R-20260804-018` oracle; any tolerance change requires independent review before rerun.
- [ ] Commit fixture, implementation, SPIR-V, and evidence together.

### Task 4: Add one live opt-in HD opaque renderer

**Files in selected gpu-vulkan repo:**
- Create: `src/main/java/com/gpuvulkan/hd/HdOpaqueRenderer.java`
- Create: `src/main/java/com/gpuvulkan/hd/HdRendererConfig.java`
- Modify: `src/main/java/com/gpuvulkan/GpuVulkanPluginConfig.java`
- Modify: `src/main/java/com/gpuvulkan/GpuVulkanPlugin.java`
- Test: `src/test/java/com/gpuvulkan/hd/HdOpaqueRendererTest.java`

**Interfaces:**
- Consumes: Task 2 primary scene ownership and Task 3 prepared-zone pipeline.
- Produces: developer-only selection between stock `BaseRenderer` and one opaque HD primary renderer.

- [ ] Add an experimental config value with default `BASE`; normal startup must instantiate no HD shader/resource class.
- [ ] Test that `BASE` selects only `BaseRenderer`, HD selection selects only `HdOpaqueRenderer`, and initialization failure returns to `BASE` on a controlled restart without two primary renderers.
- [ ] Implement top-level static opaque capture only. Exclude textures, alpha, dynamic models, shadows, lights, water, FSR changes, and post-processing.
- [ ] Preserve backend-owned UI composition and screenshot behavior.
- [ ] Run CPU, shader, offscreen oracle, startup-failure, enable/disable, login, world-hop, zero-extent, and teardown tests.
- [ ] Review the diff against Vulkan best practices and explain each new resource, barrier, pass, and lifetime in the commit body.
- [ ] Commit with `Assisted-by:` disclosure when applicable.

### Task 5: Execute cross-platform and macOS risk gates

**Files in selected gpu-vulkan repo:**
- Append: `docs/hd-port/runs.jsonl`
- Create: `docs/hd-port/opaque-slice-results.md`

**Interfaces:**
- Consumes: Task 4 opt-in slice and existing panic/benchmark protocols.
- Produces: bounded correctness/lifecycle/performance evidence for one feature slice.

- [ ] Run Linux/X11 and Windows native Vulkan with validation, the same fixture hashes, and the same image oracle before macOS presentation.
- [ ] On macOS, run separate recorded rungs: offscreen; standard `vkQueuePresentKHR`; custom Metal present; sustained windowed; resize; suspend/restore; fullscreen; display migration. Change one variable per run.
- [ ] Never combine `vkgpu.disableCustomPresent`, resize, unlocked FPS, fullscreen, or display migration in the same first-isolation run.
- [ ] Record acquire/submit/present counts, validation, frame times, WindowServer CPU/footprint, process footprint, and terminal teardown state.
- [ ] Stop the macOS ladder on any missing terminal record or panic; attach the `.panic` signature before the next rung.
- [ ] Run identical stock `BaseRenderer`, HD opaque, and 117HD OpenGL scenes; do not infer full-HD performance from the opaque slice.
- [ ] Obtain maintainer review before committing the results or beginning Task 6.

### Task 6: Port the feature ladder one slice at a time

**Files:** Defined per feature in a new reviewed plan before implementation.

**Interfaces:**
- Consumes: accepted Task 5 results.
- Produces: one independently releasable semantic capability per plan.

- [ ] Port environment/fog/sky semantics first because they do not require the full material texture library.
- [ ] Port material/texture substitution and ground blending next, preserving third-party asset licenses.
- [ ] Port normal and parallax mapping only after material correctness passes.
- [ ] Port alpha and dynamic models with explicit ordering/culling tests.
- [ ] Port shadow maps, then dynamic/tiled lighting, then water.
- [ ] Reuse existing FSR; add other post-processing last.
- [ ] For every feature, write a separate plan containing fixed fixtures, preregistered tolerances, resource/lifecycle failure tests, Windows/Linux/macOS evidence, performance thresholds, rollback, and maintainer approval.

## Completion boundary

Completion of Tasks 0-5 proves only a stock-compatible Vulkan foundation plus one opaque HD slice. It does not mean 117HD has been ported, macOS native packaging has been accepted, Plugin Hub can ship JNI, or the long-uptime regression is fixed.
