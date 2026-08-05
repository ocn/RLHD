# Road to Vulkan learning-log assessment

**Inspected:** 2026-08-05

**Source:** [`dennisdevulder/road-to-vulkan@083dd91c4506313d11066f6c6f8eee236c4ac92e`](https://github.com/dennisdevulder/road-to-vulkan/tree/083dd91c4506313d11066f6c6f8eee236c4ac92e), verified as `main`/`HEAD` on 2026-08-05.

**Deployment:** [Road to Vulkan](https://dennisdevulder.github.io/road-to-vulkan/), retrieved with HTTP `Last-Modified: Wed, 03 Jun 2026 14:52:49 GMT` and ETag `"6a203fc1-16690"`. The deployed `index.html` and pinned source file both hash to `3d9c7d8fb615d5caeb7871833a926a9c1a5c6db0772389a6db29d9a0a00c923c`.

## Evidence classification

This is versioned, maintainer-authored, project-specific orientation material. It is useful for learning the vocabulary, finding high-risk code, and understanding why existing implementation choices were made. It is not the `gpu-vulkan` specification: its prose and generated podcasts already drift from the renderer source. Every implementation decision still requires confirmation against the selected `gpu-vulkan` commit, current RuneLite API, and Khronos or platform documentation.

Several examples show why that distinction matters:

- the handover describes a 20-byte scene vertex, while pinned `ScenePipeline` uses 24 bytes; the RLHD fixture uses a separate 28-byte record;
- the handover names `CADisplayLink`, while PR #20's pinned bridge uses a 120 Hz `NSTimer`;
- the handover calls the Windows surface untested, while current pinned main reports Windows x64 tested on NVIDIA;
- the handover describes a standalone bundle as including MoltenVK, while pinned `build.gradle` adds LWJGL platform natives but no MoltenVK runtime. The pinned README still requires a host Vulkan loader.

Use the diary as a source-navigation map, not as current support or packaging proof.

## Curriculum map

| Module | Purpose for this port | Required before |
| --- | --- | --- |
| Geometry of Light | Vertices, coordinate spaces, rasterization, pipeline-state motivation | Reading or changing shaders |
| Vulkan Kitchen | Instance/device/queues, surface/swapchain, commands, memory, fences, semaphores | Reading backend lifecycle code |
| Spinning Cube | One complete frame, staging, per-frame resources, descriptors | Adding a resource or pass |
| How the Vulkan Plugin Actually Works | RuneLite takeover, scene capture, transparency, UI composition, macOS split | Designing the HD ownership seam |
| Deep Dive: Down to the Bytes | Packed vertices, reverse-Z, sorting, push constants, alpha and platform landmines | Migrating the BASE fixture |
| Handover: Guided Tour | Concrete class/method/data flow and failure history | Any production-code change |

The first three modules are education. The final three are mandatory code-navigation preparation for contributors to this port.

## Verified implementation invariants

| Invariant from the handover | Primary confirmation | Consequence for the HD port |
| --- | --- | --- |
| `GpuVulkanPlugin` owns the single RuneLite renderer callback and `draw(int)` is the final frame callback. | [RuneLite `DrawCallbacks`](https://static.runelite.net/runelite-api/apidocs/net/runelite/api/hooks/DrawCallbacks.html) and pinned [`GpuVulkanPlugin`](https://github.com/dennisdevulder/gpu-vulkan/blob/12f8db08c8bac0d0180f96738b1fd9b075215496/src/main/java/com/gpuvulkan/GpuVulkanPlugin.java) | HD rendering must replace or deepen the primary scene renderer; it cannot register another client renderer. |
| `BaseRenderer` owns both stock scene rendering and UI upload/composition, while extension callbacks are additive. | Pinned [`BaseRenderer`](https://github.com/dennisdevulder/gpu-vulkan/blob/12f8db08c8bac0d0180f96738b1fd9b075215496/src/main/java/com/gpuvulkan/scene/BaseRenderer.java) and [`RenderExtensions`](https://github.com/dennisdevulder/gpu-vulkan/blob/12f8db08c8bac0d0180f96738b1fd9b075215496/src/main/java/com/gpuvulkan/extension/runtime/RenderExtensions.java) | A primary-scene seam must retain exactly one backend-owned UI upload and draw; replacing all `BaseRenderer` callbacks would suppress UI. |
| Current stock scene vertices are 24 bytes; the existing RLHD conformance record is 28 bytes. | Pinned [`ScenePipeline`](https://github.com/dennisdevulder/gpu-vulkan/blob/12f8db08c8bac0d0180f96738b1fd9b075215496/src/main/java/com/gpuvulkan/scene/ScenePipeline.java) and [`SceneVertexPacker`](https://github.com/dennisdevulder/gpu-vulkan/blob/12f8db08c8bac0d0180f96738b1fd9b075215496/src/main/java/com/gpuvulkan/scene/SceneVertexPacker.java) | Task 3 must choose an independent HD pipeline or an explicit conversion adapter with byte-layout golden tests; reinterpretation is forbidden. |
| Frame-slot CPU writes must occur only after the corresponding in-flight fence completes. | Pinned [`VulkanRenderer`](https://github.com/dennisdevulder/gpu-vulkan/blob/12f8db08c8bac0d0180f96738b1fd9b075215496/src/main/java/com/gpuvulkan/backend/VulkanRenderer.java), [`SceneRenderer`](https://github.com/dennisdevulder/gpu-vulkan/blob/12f8db08c8bac0d0180f96738b1fd9b075215496/src/main/java/com/gpuvulkan/scene/SceneRenderer.java), and [Khronos synchronization guide](https://docs.vulkan.org/guide/latest/synchronization.html) | Every HD buffer/image ring needs an explicit slot owner, wait point, write point, submission, and retirement test. |
| Disposal order is part of correctness: `FrameSync` is registered before `Swapchain`, so reverse close order retires WSI before synchronization objects. | Pinned [`GpuVulkanPlugin`](https://github.com/dennisdevulder/gpu-vulkan/blob/12f8db08c8bac0d0180f96738b1fd9b075215496/src/main/java/com/gpuvulkan/GpuVulkanPlugin.java) | Ownership-seam changes must preserve construction, startup-recovery, and teardown order. |
| The renderer intentionally uses `B8G8R8A8_UNORM`, reverse-Z, and OSRS-specific texture sampling. | Pinned [`Swapchain`](https://github.com/dennisdevulder/gpu-vulkan/blob/12f8db08c8bac0d0180f96738b1fd9b075215496/src/main/java/com/gpuvulkan/backend/Swapchain.java), [`RenderPass`](https://github.com/dennisdevulder/gpu-vulkan/blob/12f8db08c8bac0d0180f96738b1fd9b075215496/src/main/java/com/gpuvulkan/backend/RenderPass.java), and [`TextureArray`](https://github.com/dennisdevulder/gpu-vulkan/blob/12f8db08c8bac0d0180f96738b1fd9b075215496/src/main/java/com/gpuvulkan/backend/TextureArray.java) | BASE and later material tests must pin color space, depth clear/compare, and magnification behavior instead of accepting a visually plausible image. |
| Large per-frame vertex arenas are bound at byte offset zero and selected with `firstVertex` because large offsets failed through MoltenVK. | Pinned [`SceneRenderer`](https://github.com/dennisdevulder/gpu-vulkan/blob/12f8db08c8bac0d0180f96738b1fd9b075215496/src/main/java/com/gpuvulkan/scene/SceneRenderer.java) | Do not redesign arena addressing while adding the primary-renderer seam or opaque HD slice. Add an Apple-Silicon regression fixture before changing it later. |
| Canvas pixel dimensions, matrix Y inversion, and UI upload ordering are deliberate AWT/RuneLite integration behavior. | Pinned [`VulkanRenderer`](https://github.com/dennisdevulder/gpu-vulkan/blob/12f8db08c8bac0d0180f96738b1fd9b075215496/src/main/java/com/gpuvulkan/backend/VulkanRenderer.java) and [`InterfaceRenderer`](https://github.com/dennisdevulder/gpu-vulkan/blob/12f8db08c8bac0d0180f96738b1fd9b075215496/src/main/java/com/gpuvulkan/ui/InterfaceRenderer.java) | Preserve backend-owned UI composition. Include click alignment, Retina resize, and UI staging races in live gates. |
| Standard presentation uses frame-indexed acquire resources but image-indexed present semaphores; a frame fence does not prove presentation completion. | Pinned [`FrameSync`](https://github.com/dennisdevulder/gpu-vulkan/blob/12f8db08c8bac0d0180f96738b1fd9b075215496/src/main/java/com/gpuvulkan/backend/FrameSync.java) and [Khronos swapchain-semaphore guidance](https://docs.vulkan.org/guide/latest/swapchain_semaphore_reuse.html) | Tests must keep frame-slot lifetime and swapchain-image presentation lifetime distinct, including teardown. |
| PR #20's custom macOS route imports Metal drawables, submits without Vulkan presentation semaphores, waits its fence, then presents on the exported Metal queue. | Pinned [`VulkanRenderer`](https://github.com/dennisdevulder/gpu-vulkan/blob/3b5cbe5d42c26915c6d34d1df6613db74dd8c48b/src/main/java/com/gpuvulkan/backend/VulkanRenderer.java), [`MetalDrawableSet`](https://github.com/dennisdevulder/gpu-vulkan/blob/3b5cbe5d42c26915c6d34d1df6613db74dd8c48b/src/main/java/com/gpuvulkan/platform/MetalDrawableSet.java), and [`VK_EXT_metal_objects`](https://docs.vulkan.org/refpages/latest/refpages/source/VK_EXT_metal_objects.html) | This is an implementation-specific MoltenVK/Metal hypothesis, not a general Vulkan guarantee, and remains a separate risk rung from `vkQueuePresentKHR`. HD work must not alter it during early slices. |
| Every drawable acquisition must reach release/presentation, and the JNI Metal entry points use autorelease pools. | Pinned [`MacOSMetalHelper`](https://github.com/dennisdevulder/gpu-vulkan/blob/3b5cbe5d42c26915c6d34d1df6613db74dd8c48b/src/main/java/com/gpuvulkan/platform/MacOSMetalHelper.java) and [`rlmtl.m`](https://github.com/dennisdevulder/gpu-vulkan/blob/3b5cbe5d42c26915c6d34d1df6613db74dd8c48b/src/main/native/rlmtl.m) | Failure injection must cover acquire-with-exception and repeated attach/draw/detach cycles before soak testing. |

## Required engineering practice

For every feature slice, the contributor must:

1. trace the changed data from the relevant `DrawCallbacks` entry through packing, slot ownership, barriers, draw, UI composition, and retirement;
2. list every new or changed resource, owner, per-frame slot, layout/state transition, synchronization edge, failure path, and close action;
3. preserve the verified landmine invariants unless a dedicated red/green regression test demonstrates the replacement;
4. run validation, but continue platform, presentation, shader-semantic, and visual checks because clean validation covers only part of the system;
5. record failed hypotheses instead of stacking unrelated plausible fixes;
6. explain the backwards pass in the commit body and use the repository's `Assisted-by:` disclosure.

This converts the maintainer's accountability guidance into reviewable gates without treating completion of the podcasts as implementation evidence.
