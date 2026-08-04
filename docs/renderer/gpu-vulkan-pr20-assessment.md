# gpu-vulkan and macOS PR 20 assessment

**Inspected:** 2026-08-04

**gpu-vulkan main:** [`12f8db08c8bac0d0180f96738b1fd9b075215496`](https://github.com/dennisdevulder/gpu-vulkan/tree/12f8db08c8bac0d0180f96738b1fd9b075215496)

**macOS preservation branch:** [`3b5cbe5d42c26915c6d34d1df6613db74dd8c48b`](https://github.com/dennisdevulder/gpu-vulkan/tree/3b5cbe5d42c26915c6d34d1df6613db74dd8c48b)

**PR base:** [`a631534559474103cf05e6abd46962692ef5364e`](https://github.com/dennisdevulder/gpu-vulkan/tree/a631534559474103cf05e6abd46962692ef5364e)
**PR:** [#20, Preserve macOS native bridge](https://github.com/dennisdevulder/gpu-vulkan/pull/20)

## Finding

Use `gpu-vulkan` as the platform and frame-lifecycle foundation for further Vulkan work. Stop the planned RLHD-local implementation of Vulkan instance/device/surface/swapchain/synchronization/presentation. Port HD semantics incrementally on top of `gpu-vulkan` after its owner approves the renderer-replacement seam.

Keep 117HD OpenGL unchanged. The existing `PreparedZoneGeometry` work, fixed BASE fixture, offscreen oracle, shader correctness rules, crash ledger, and benchmark protocol remain useful conformance assets; they are not a competing production backend.

## Confirmed source facts

- `GpuVulkanPlugin` owns RuneLite's `DrawCallbacks` slot and the Vulkan instance, device, surface, swapchain, frame synchronization, scene callbacks, UI composition, screenshot readback, and presentation. It therefore cannot run beside an unchanged 117HD renderer that also owns `DrawCallbacks`. [GpuVulkanPlugin](https://github.com/dennisdevulder/gpu-vulkan/blob/12f8db08c8bac0d0180f96738b1fd9b075215496/src/main/java/com/gpuvulkan/GpuVulkanPlugin.java)
- The backend exposes `VulkanRenderBackend`, `VulkanRenderExtension`, `VulkanRenderContext`, and a higher-level `RenderDevice`. The interface covers scene/model capture, buffer and image resources, render and compute pipelines, offscreen targets, pass recording, scene redirection, and post-composite hooks. [Extension interface](https://github.com/dennisdevulder/gpu-vulkan/blob/12f8db08c8bac0d0180f96738b1fd9b075215496/src/main/java/com/gpuvulkan/extension/api/VulkanRenderExtension.java) · [RenderDevice](https://github.com/dennisdevulder/gpu-vulkan/blob/12f8db08c8bac0d0180f96738b1fd9b075215496/src/main/java/com/gpuvulkan/gfx/RenderDevice.java)
- FSR 1.0 is already a worked extension: it redirects the scene, performs EASU and RCAS passes, and leaves UI composition at native resolution. It should be studied and reused, not ported again. [FsrUpscalerExtension](https://github.com/dennisdevulder/gpu-vulkan/blob/12f8db08c8bac0d0180f96738b1fd9b075215496/src/main/java/com/gpuvulkan/extension/upscaling/FsrUpscalerExtension.java)
- Extensions currently fan out additively while `BaseRenderer` is always registered. Registering an HD renderer as another extension would double-render the scene. The first upstream design decision is an exclusive primary-scene renderer or an agreed incremental deepening of `BaseRenderer`. [RenderExtensions](https://github.com/dennisdevulder/gpu-vulkan/blob/12f8db08c8bac0d0180f96738b1fd9b075215496/src/main/java/com/gpuvulkan/extension/runtime/RenderExtensions.java)
- Current main reports Linux/X11 and Windows working, while macOS is not shipped in the Plugin Hub build. It also states that active development occurs in the parent `runelite-vkport` tree, so the correct contribution repository must be confirmed before implementation. [README](https://github.com/dennisdevulder/gpu-vulkan/blob/12f8db08c8bac0d0180f96738b1fd9b075215496/README.md)

## PR 20 scope and maturity

PR #20 restores:

- a JAWT/JNI Objective-C bridge that attaches a `CAMetalLayer`;
- `VK_EXT_metal_surface` surface creation;
- a universal arm64/x86_64, ad-hoc-signed `librlmtl.dylib` committed as a resource;
- resize, drawable acquisition, direct Metal presentation, detach, and rebuild logic. [PR body](https://github.com/dennisdevulder/gpu-vulkan/pull/20) · [native bridge](https://github.com/dennisdevulder/gpu-vulkan/blob/3b5cbe5d42c26915c6d34d1df6613db74dd8c48b/src/main/native/rlmtl.m) · [macOS surface](https://github.com/dennisdevulder/gpu-vulkan/blob/3b5cbe5d42c26915c6d34d1df6613db74dd8c48b/src/main/java/com/gpuvulkan/platform/MacOSPlatformSurface.java)

The default restored macOS route is not merely ordinary `vkQueuePresentKHR`. It imports `CAMetalDrawable` textures through `VK_EXT_metal_objects`, copies the rendered image into them, then schedules presentation through the native Metal queue. The branch includes `vkgpu.disableCustomPresent` as an escape hatch to standard Vulkan presentation. [VulkanRenderer](https://github.com/dennisdevulder/gpu-vulkan/blob/3b5cbe5d42c26915c6d34d1df6613db74dd8c48b/src/main/java/com/gpuvulkan/backend/VulkanRenderer.java) · [MetalDrawableSet](https://github.com/dennisdevulder/gpu-vulkan/blob/3b5cbe5d42c26915c6d34d1df6613db74dd8c48b/src/main/java/com/gpuvulkan/platform/MetalDrawableSet.java)

PR #20 is open and draft and is based on older commit `a6315345`, not current main `12f8db08`. As inspected, GitHub reported no status checks, reviews, or comments. Its body records only `./gradlew test`; this repository has one ordinary JUnit class plus a development launcher, not a committed GPU/lifecycle/soak suite. The maintainer's user-relayed statement that the macOS route ran for about 200 hours is valuable operating evidence, but no commit-bound logs, host matrix, settings, or frame/resource metrics were found publicly. Record it as maintainer testimony until those details are supplied.

The branch builds the native helper but does not package a MoltenVK/Vulkan-loader runtime. A local or standalone build still needs a compatible host loader/runtime. The Plugin Hub build remains blocked because RuneLite's current policy forbids JNI in hub plugins. [Build](https://github.com/dennisdevulder/gpu-vulkan/blob/3b5cbe5d42c26915c6d34d1df6613db74dd8c48b/build.gradle) · [RuneLite forbidden language features](https://github.com/runelite/runelite/wiki/Rejected-or-Rolled-Back-Features#forbidden-language-features)

## Ownership after the pivot

| Owner/module | Responsibility |
| --- | --- |
| `gpu-vulkan` backend | DrawCallbacks ownership, platform surfaces, instance/device, swapchain, synchronization, resource factories, frame passes, UI, readback, presentation, teardown, and platform fallbacks. |
| HD primary renderer | 117HD scene preparation, material/environment semantics, textures, alpha ordering, shadow passes, dynamic/tiled lighting, water, and HD shader behavior. |
| Auxiliary extensions | FSR and later post-processing/recording hooks that do not own primary scene capture. |
| 117HD OpenGL | Existing released renderer and rollback on macOS, Windows, and Linux; no dependency on `gpu-vulkan`. |
| RuneLite/Plugin Hub | Distribution and native-code acceptance; technical success does not imply approval. |

This is a deep seam: HD code receives backend-owned rendering capabilities and frame callbacks, but never owns or duplicates platform presentation. Platform differences remain inside `gpu-vulkan`; HD feature code must run through native Vulkan on Windows/Linux and MoltenVK on macOS.

## Questions that block implementation

1. Should contributions target this standalone repository or the parent `runelite-vkport` tree?
2. Is the intended HD approach an exclusive primary renderer selected in `gpu-vulkan`, or incremental additions directly to `BaseRenderer`?
3. Which exact commit, macOS build, hardware, JDK, Vulkan loader, MoltenVK version, present path, FPS mode, resize/fullscreen/display topology, and validation settings produced the approximately 200-hour result?
4. Was that soak on custom Metal presentation, `vkgpu.disableCustomPresent=true`, or both?
5. Will the owner review HD material/pass changes and the required extension/interface evolution?
6. Is local/standalone macOS distribution the accepted near-term target while JNI remains outside Plugin Hub policy?

## Consequence for current RLHD work

Do not execute the remaining RLHD-local `VulkanSurfaceProvider`, swapchain, presentation, or backend-selection tasks. Preserve commits `d8e430f6` and `c71b1005` as independently verified input/packing/readback evidence. The next code-producing task belongs in an isolated fork/worktree of the maintainer-selected `gpu-vulkan` repository and starts only after questions 1 and 2 are answered.
