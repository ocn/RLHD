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
- Read the project-specific modules in [Road to Vulkan](https://dennisdevulder.github.io/road-to-vulkan/) before production changes, then verify every relied-on statement against the selected source commit using the [assessment](road-to-vulkan-assessment.md).
- Preserve the existing landmine invariants unless a dedicated red/green regression test proves the replacement.
- Keep VMA and dynamic-rendering refactors out of scope unless a separate maintainer-approved proposal demonstrates an HD requirement.
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
- [ ] Pin `road-to-vulkan` commit `083dd91c4506313d11066f6c6f8eee236c4ac92e` and maintain a curriculum-claim/current-source correction table for the selected renderer commit.
- [ ] Ask whether HD features deepen `BaseRenderer` or use an exclusive primary-renderer slot. Record one answer; additive registration is rejected.
- [ ] Ask for the exact commit, macOS build, hardware, JDK, loader, MoltenVK version, present path, FPS mode, validation state, display topology, and workload behind the approximately 200-hour result.
- [ ] Ask who will review HD scene/material/shader work and whether local/standalone macOS delivery is the near-term target.
- [ ] Read the plugin overview, byte-level deep dive, and source handover. For each planned first-slice change, identify the current callback, resource owner, synchronization edge, failure path, and close action in pinned source.
- [ ] Stop without code if the repository, primary-renderer model, or reviewer remains ambiguous.
- [ ] Commit the recorded answers before cloning or changing upstream code.

### Task 1: Establish an immutable gpu-vulkan baseline

**Files:**
- Create in selected repo: `docs/hd-port/baseline.md`
- Create in selected repo: `docs/hd-port/runs.jsonl`
- Create in selected repo: `docs/hd-port/invariant-ledger.md`
- Modify in selected repo: `.gitignore` only if captured build artifacts need an ignored directory.

**Interfaces:**
- Consumes: exact repository, branch, and commits from Task 0.
- Produces: reproducible CPU/build baseline and a presentation-free artifact inventory.

- [ ] Create an isolated worktree at exact main commit `12f8db08c8bac0d0180f96738b1fd9b075215496`; cherry-pick macOS preservation commit `3b5cbe5d42c26915c6d34d1df6613db74dd8c48b`, whose PR base is older `a631534559474103cf05e6abd46962692ef5364e`, only on a named macOS branch and review every conflict.
- [ ] Record `git rev-parse HEAD`, `git status --porcelain`, JDK, Gradle, LWJGL, Vulkan loader, MoltenVK, shader compiler, native bridge SHA-256, `file`, `codesign -dv`, and linked frameworks in `baseline.md`.
- [ ] Pin in `invariant-ledger.md` the existing startup/teardown order, per-slot fence-before-write rules, UNORM/reverse-Z state, zero-offset vertex-arena addressing, canvas-pixel sizing/Y inversion, NEAREST texture magnification, UI staging order, and macOS drawable/autoreleasepool obligations. Link each item to exact source lines in the selected commit.
- [ ] Run `JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home ./gradlew --no-daemon --rerun-tasks test`; require all tests to execute, not merely report `UP-TO-DATE`.
- [ ] Run the shader freshness/build path and `shadowJar`; verify Java 11 class version and that the expected SPIR-V/native resources are present.
- [ ] Inspect both slim and standalone JAR contents. Record whether a Vulkan loader, MoltenVK runtime, JNI bridge, LWJGL bindings/natives, and each committed SPIR-V artifact are actually present; do not infer packaging from dependency names or the curriculum.
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

`RenderExtensions` must hold one `PrimarySceneRenderer` separately from auxiliary extensions. Scene/model capture and `recordScenePass` go only to the primary renderer. Backend-owned UI upload, layout transition, and composition must remain exactly once per frame even when `BaseRenderer` is not the primary scene renderer. `scenePassRedirect`, `recordAfterComposite`, and other auxiliary hooks continue to fan out. Do not route all `BaseRenderer` callbacks away without first separating its current scene and UI responsibilities.

- [ ] Write `PrimarySceneOwnershipTest` with a base fake, HD fake, UI fake, and auxiliary fake; assert exactly one scene capture/draw, one UI upload/transition/draw, auxiliary post hooks, rejection of a second primary, and close ordering.
- [ ] Run `./gradlew --no-daemon test --tests '*PrimarySceneOwnershipTest'`; require the test to fail because the primary slot does not exist.
- [ ] Implement only the approved ownership model. Do not add HD rendering yet.
- [ ] Run the focused test and full `./gradlew --no-daemon test`; require existing FSR/recorder registration behavior to remain green.
- [ ] Independently review callback ordering, failure removal, close behavior, and mid-startup registration before commit.
- [ ] Demonstrate that the ownership change does not alter frame-slot writes, WSI/synchronization disposal order, UI staging, or either presentation path.
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
- [ ] Decide explicitly between a separate 28-byte HD vertex pipeline and conversion to the stock 24-byte `gpu-vulkan` record. Reinterpretation is forbidden; require exact byte-layout and conversion golden tests for the selected model.
- [ ] Write CPU tests for exact hashes, immutability, byte order, vertex/metadata record sizes, and camera values; run them red before implementing `HdPreparedZone`.
- [ ] Add the minimum storage-buffer or vertex-format capability needed by the existing `RenderDevice`; do not expose raw Vulkan handles to the HD module.
- [ ] Pin the BASE fixture's color-space, reverse-Z clear/compare, winding, texture filtering, and byte-offset/`firstVertex` behavior; a visually similar image is insufficient.
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
- [ ] Perform a backwards pass from retirement to `DrawCallbacks`: document close action, failure unwind, synchronization, layout/state transitions, owner, and source data for each changed resource.
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
- [ ] Verify per-frame acquire/fence resources separately from per-swapchain-image present semaphores; a submit fence is not evidence that standard presentation resources are retired.
- [ ] Treat clean Vulkan validation as necessary but insufficient; separately check shader semantics, AWT/Retina sizing and click alignment, UI flicker, MoltenVK translation, and presentation timing.
- [ ] Run correctness with core and synchronization validation enabled, then performance with validation disabled; record the mode with every result.
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
- [ ] Preserve NEAREST magnification for OSRS texture alpha/discard semantics until a dedicated material fixture proves an alternative.
- [ ] Port normal and parallax mapping only after material correctness passes.
- [ ] Port alpha and dynamic models with explicit ordering/culling tests.
- [ ] Preserve bucket/priority sorting and near-plane behavior in alpha/dynamic slices, with fixed regression fixtures.
- [ ] Port shadow maps, then dynamic/tiled lighting, then water.
- [ ] Reuse existing FSR; add other post-processing last.
- [ ] For every feature, write a separate plan containing fixed fixtures, preregistered tolerances, resource/lifecycle failure tests, Windows/Linux/macOS evidence, performance thresholds, rollback, and maintainer approval.

## Completion boundary

Completion of Tasks 0-5 proves only a stock-compatible Vulkan foundation plus one opaque HD slice. It does not mean 117HD has been ported, macOS native packaging has been accepted, Plugin Hub can ship JNI, or the long-uptime regression is fixed.
