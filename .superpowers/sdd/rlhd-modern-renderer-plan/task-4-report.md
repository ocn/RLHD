# Task 4 report: Vulkan/MoltenVK control spike

## Outcome

Implemented and hardened the isolated Vulkan 1.2/MoltenVK presentation control under `spikes/vulkan-control`. It borrows the Task 2 per-instance `CAMetalLayer`, reuses the exact Task 3 `SyntheticUi`, renders matching triangle/UI passes, validates two distinct GPU-readback frames, uses standard `vkQueuePresentKHR` with required maintenance1 presentation fences, logs capabilities/timing/native ownership, and leaves production OpenGL tests and artifacts unchanged.

The focused, full non-headful, headful validation, shader, dependency, bytecode, and artifact gates pass. The manual 30-minute and multi-display checks were not executed. MoltenVK's nonzero GPU-memory teardown diagnostic remains an explicit concern and prevents a zero-growth claim.

## Implemented boundary

- Added isolated `vulkanControlSpike` and `vulkanControlSpikeTest` source sets, tasks, Java 11 bytecode, Java-only spike jar, and exact dependency/artifact-isolation gate.
- Reused Task 2 `MacMetalSurface`; `VkSurfaceKHR` is created from the borrowed `CAMetalLayer` before physical-device selection.
- Explicitly initializes LWJGL's Vulkan loader boundary and permits a caller-supplied absolute loader path.
- Requires Vulkan 1.2, `VK_KHR_surface`, `VK_KHR_get_surface_capabilities2`, `VK_EXT_surface_maintenance1`, `VK_KHR_portability_enumeration`, `VK_EXT_metal_surface`, `VK_KHR_swapchain`, the `VK_EXT_swapchain_maintenance1` feature, `VK_FORMAT_B8G8R8A8_UNORM + VK_COLOR_SPACE_SRGB_NONLINEAR_KHR`, FIFO, and at least three swapchain images.
- Enables `VK_INSTANCE_CREATE_ENUMERATE_PORTABILITY_BIT_KHR`; enables `VK_KHR_portability_subset` only when advertised.
- Selects graphics and present queue families independently and uses concurrent swapchain sharing when they differ.
- Requests three images, verifies the actual count, and uses exactly two frame slots.
- Owns acquire semaphores/submission fences per frame and render-finished semaphores/presentation fences per acquired swapchain image. It waits/resets a live presentation fence on reacquisition and waits every live presentation fence before swapchain retirement.
- Consumes the acquire semaphore and calls `vkReleaseSwapchainImagesEXT` for failures after acquisition but before submission, then terminalizes without reusing poisoned synchronization.
- Publishes frame/swapchain ownership immediately; buffer, image, shader, readback, command, and partial swapchain creation all have local/idempotent rollback. Close is best-effort and distinguishes failures before versus after backend ownership is consumed.
- Uses FIFO for the baseline. An unlocked request uses IMMEDIATE when supported, otherwise truthfully records MAILBOX as vblank-synchronized fallback or FIFO.
- Handles fixed/variable surface extents, supported composite alpha, current transform, resize, scale, zero-extent suspension, restore, fullscreen, and present-mode recreation.
- Resolves the Task 2/AWT extent race by regenerating the exact synthetic UI whenever either requested dimension differs from the effective swapchain extent.
- Compiles four GLSL shaders with `glslangValidator --target-env vulkan1.2` and validates each with `spirv-val --target-env vulkan1.2`.
- Renders triangle then premultiplied UI with `ONE, ONE_MINUS_SRC_ALPHA` blending.
- Runs a real two-frame 8x8 GPU readback check through a compatible offscreen render pass and explicit color-write-to-transfer-read barrier.
- Uses timestamp queries only when the graphics queue reports nonzero valid bits and the physical device reports a finite positive timestamp period.
- Emits strict `rlhd.renderer.timing/v1` JSONL with required maintenance1 capability truth, balanced submission/presentation/acquisition invariants, separate validation warning/error counters, a dedicated timestamp-query-error counter, and truthful unsupported presentation callback fields.
- Documents the opt-in build/run boundary, log schema, exact 30-minute lifecycle checklist, provenance, and unresolved driver diagnostic.

No 117HD renderer, production shader, production dependency, default task, or default artifact was changed.

## Test-driven progression

The original recovered red compile had 29 expected missing-symbol errors. Review-round red/green added two expected missing-symbol errors for the explicit post-consumption close failure and then exercised:

- post-consumption close failures leave the wrapper closed while pre-consumption failures remain retryable;
- required maintenance1/presentation-fence capability fields and final counter invariants;
- live injected failures at partial swapchain-child creation, post-acquire, post-record, pre-submit, recreation, and post-consumption close;
- actual effective mode, recreation count, fullscreen submission, balanced submitted/completed/present/acquire counters, zero validation warning/error counts, and timestamp counter invariants;
- exact runtime dependency and production/spike artifact boundaries.

The unused `FrameSynchronization`, `FrameSlotTracker`, `SwapchainLifecycle`, and `VulkanCapabilities` models and their tests were removed. Failure coverage now exercises the live LWJGL backend rather than a parallel toy lifecycle.

The first headful bring-up also exposed and fixed real integration defects:

1. LWJGL 3.3.2 tried its default `libvulkan.1.dylib` before the configured loader. Setting `Configuration.VULKAN_EXPLICIT_INIT` before first `VK` access allowed explicit initialization.
2. Descriptor writes omitted `descriptorCount(1)`. Validation identified the malformed writes; both live and readback writes now set it.
3. LWJGL's `VkSubmitInfo.pWaitSemaphores` does not populate `waitSemaphoreCount`, while `pCommandBuffers`, `pSignalSemaphores`, and `VkPresentInfoKHR.pWaitSemaphores` do populate their associated counts. The submit wait count is now explicit and the other generated setters remain authoritative.
4. Before that correction validation reported `UNASSIGNED-VkPresentInfoKHR-pImageIndices-MissingAcquireWait`, `VUID-vkAcquireNextImageKHR-semaphore-01286`, and an unassigned queue-submit presentable-image acquire-wait diagnostic. Fresh validation runs emit none of them.
5. AWT resized the live `CAMetalLayer` to 777x403 before Task 2's stored extent moved from 640x332. The effective/requested extent handling above removed the stale upload failure.
6. Gson's default omitted explicitly-added JSON nulls. A logger regression test and `serializeNulls()` restored required `presented_time_ns`, `latency_ns`, GPU, and error null fields.
7. The first maintenance1 validation run exposed missing `VK_KHR_get_surface_capabilities2`, an instance dependency of `VK_EXT_surface_maintenance1`. The dependency is now required and enabled.
8. The first offscreen-pass revision differed from the live pipeline render pass by one subpass dependency. Validation reported four `VUID-vkCmdDraw-renderPass-02684` errors. The offscreen pass now has a compatible dependency and an explicit post-pass transfer barrier; the fresh run emits zero validation messages.

## Fresh verification

Environment used:

- Temurin 21.0.12+8 arm64: `/private/tmp/rlhd-toolchains/temurin-21/Contents/Home`
- Vulkan SDK 1.4.350.1: `/private/tmp/rlhd-toolchains/VulkanSDK/1.4.350.1/macOS`
- standalone MoltenVK 1.4.2: `/private/tmp/rlhd-toolchains/MoltenVK-1.4.2/MoltenVK/dylib/macOS`
- Apple M3 Ultra, validation layer enabled

Full non-headful matrix:

```sh
JAVA_HOME=... RLHD_VULKAN_SDK=... ./gradlew --no-daemon --rerun-tasks \
  test jar macSurfaceSpikeCheck metalControlSpikeCheck \
  vulkanControlSpikeCheck vulkanControlSpikeJar
```

Result: `BUILD SUCCESSFUL in 43s`, 30/30 tasks executed. This included production `test`/`jar`, Task 2's 100-cycle native harness, Task 2 Java tests, Task 3 pure tests, 15 Task 4 pure tests, all four fresh shader compile/validation pairs, exact dependency/artifact isolation, and the isolated jar.

Final headful validation run:

```sh
JAVA_HOME=... RLHD_VULKAN_SDK=... VK_DRIVER_FILES=... VK_LAYER_PATH=... \
DYLD_LIBRARY_PATH=... ./gradlew --no-daemon --rerun-tasks \
  -Drlhd.spike.vulkan.loader=.../libvulkan.1.4.350.dylib \
  vulkanControlIntegrationTest
```

Result: `BUILD SUCCESSFUL in 8s`, 15/15 tasks executed, two integration tests passed. Observed facts:

- MoltenVK 1.4.2 selected Apple M3 Ultra and created a Vulkan 1.2.357 instance.
- Validation requested/enabled: true/true; no validation warning/error messages in the final run.
- `VK_KHR_get_surface_capabilities2`, `VK_EXT_surface_maintenance1`, `VK_KHR_portability_subset`, `VK_KHR_swapchain`, and `VK_EXT_swapchain_maintenance1` enabled as appropriate; the maintenance1 feature was queried and enabled.
- Three images created for every swapchain generation.
- Actual GPU readback passed before live rendering.
- Resize, suspend/restore, IMMEDIATE/FIFO recreation, fullscreen entry/exit, and return to 777x403 completed.
- 199 submitted = 199 completed = 199 present requests; 199 acquisition requests = 199 completions; `max_in_flight=2`; at least five recreations; zero command/init/shader/pipeline, validation warning/error, or timestamp-query errors; final app-tracked `live_native_objects=0`.
- The complete JSONL run validated with a single run start/end and 200 attempted-frame records (199 submitted, one skipped suspended).
- A second live integration test proved deterministic cleanup for partial initialization and injected acquire/record/submit/recreate/close failures, including post-consumption wrapper state.

Artifact/dependency checks:

- Production `build/libs/hd-1.5.2.jar` contains no spike classes, Vulkan shaders, LWJGL Vulkan classes, or `SyntheticUi`.
- `build/libs/hd-1.5.2-vulkan-control-spike.jar` contains Task 2 surface classes, Task 4 classes/SPIR-V, and only Task 3 `SyntheticUi`; it contains no dylib/JNI or bundled LWJGL classes.
- `VulkanControlRenderer` class major version is 55 (Java 11).
- Runtime dependency set is exactly Gson 2.14.0 without transitives, LWJGL core 3.3.2, LWJGL Vulkan Java 3.3.2, and core `natives-macos-arm64`. The gate also rejects LWJGL in production runtime, Vulkan entries in the production jar, and undeclared spike-jar paths.
- `git diff --check` passed.

## Unresolved observation and manual work

After maintenance1 presentation-fence retirement, MoltenVK still printed `Destroyed VkPhysicalDevice ... with 50 MB of GPU memory still allocated` after the automated fullscreen lifecycle. Subsequent small-window injected-failure instances in the same process printed 49 MB after the fullscreen high-water. App-tracked ownership reached zero and validation was clean, but those facts do not identify who retained the reported memory. Earlier diagnostics reported 50 MB with `MVK_CONFIG_USE_MTLHEAP=0` and 7 MB for a separate window-only run. These observations neither prove an app leak nor justify attributing the retention to MoltenVK, CAMetalLayer, or the driver, and they do not support a zero-growth result.

The manual 30-minute lifecycle/footprint checklist and multi-display migration remain unexecuted. They must explicitly watch process and GPU footprint and determine whether it stabilizes before Task 4 can support any soak/stability claim.

## References

- [MoltenVK runtime guide](https://github.com/KhronosGroup/MoltenVK/blob/main/Docs/MoltenVK_Runtime_UserGuide.md)
- [MoltenVK configuration parameters](https://github.com/KhronosGroup/MoltenVK/blob/main/Docs/MoltenVK_Configuration_Parameters.md)
- [Vulkan portability enumeration](https://docs.vulkan.org/refpages/latest/refpages/source/VK_KHR_portability_enumeration.html)
- [`VK_EXT_metal_surface`](https://docs.vulkan.org/refpages/latest/refpages/source/VK_EXT_metal_surface.html)
- [`vkAcquireNextImageKHR`](https://docs.vulkan.org/refpages/latest/refpages/source/vkAcquireNextImageKHR.html)
- [`vkQueuePresentKHR`](https://docs.vulkan.org/refpages/latest/refpages/source/vkQueuePresentKHR.html)
- [Khronos swapchain semaphore reuse guide](https://docs.vulkan.org/guide/latest/swapchain_semaphore_reuse.html)
- [`VK_EXT_swapchain_maintenance1`](https://docs.vulkan.org/refpages/latest/refpages/source/VK_EXT_swapchain_maintenance1.html)
- [`VkSwapchainPresentFenceInfoEXT`](https://docs.vulkan.org/refpages/latest/refpages/source/VkSwapchainPresentFenceInfoEXT.html)
- [`vkReleaseSwapchainImagesEXT`](https://docs.vulkan.org/refpages/latest/refpages/source/vkReleaseSwapchainImagesEXT.html)
- [Vulkan render-pass compatibility](https://docs.vulkan.org/spec/latest/chapters/renderpass.html#renderpass-compatibility)
