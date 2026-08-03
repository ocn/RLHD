# Task 4 report: Vulkan/MoltenVK control spike

## Outcome

Implemented the isolated Vulkan 1.2/MoltenVK presentation control under `spikes/vulkan-control`. It borrows the Task 2 per-instance `CAMetalLayer`, reuses the exact Task 3 `SyntheticUi`, renders matching triangle/UI passes, validates two distinct GPU-readback frames, uses only standard `vkQueuePresentKHR`, logs capabilities/timing/native ownership, and leaves production OpenGL tests and artifacts unchanged.

The focused, full non-headful, headful validation, shader, dependency, bytecode, and artifact gates pass. The manual 30-minute and multi-display checks were not executed. MoltenVK's nonzero GPU-memory teardown diagnostic remains an explicit concern and prevents a zero-growth claim.

## Implemented boundary

- Added isolated `vulkanControlSpike` and `vulkanControlSpikeTest` source sets, tasks, Java 11 bytecode, Java-only spike jar, and dependency-isolation gate.
- Reused Task 2 `MacMetalSurface`; `VkSurfaceKHR` is created from the borrowed `CAMetalLayer` before physical-device selection.
- Explicitly initializes LWJGL's Vulkan loader boundary and permits a caller-supplied absolute loader path.
- Requires Vulkan 1.2, `VK_KHR_surface`, `VK_KHR_portability_enumeration`, `VK_EXT_metal_surface`, `VK_KHR_swapchain`, `VK_FORMAT_B8G8R8A8_UNORM`, FIFO, and support for at least three swapchain images.
- Enables `VK_INSTANCE_CREATE_ENUMERATE_PORTABILITY_BIT_KHR`; enables `VK_KHR_portability_subset` only when advertised.
- Selects graphics and present queue families independently and uses concurrent swapchain sharing when they differ.
- Requests three images, verifies the actual count, and uses exactly two frame slots.
- Owns acquire semaphores/fences per frame and render-finished semaphores per acquired swapchain image.
- Uses FIFO for the baseline. An unlocked request uses IMMEDIATE when supported, otherwise truthfully records MAILBOX as vblank-synchronized fallback or FIFO.
- Handles fixed/variable surface extents, supported composite alpha, current transform, resize, scale, zero-extent suspension, restore, fullscreen, and present-mode recreation.
- Resolves the Task 2/AWT extent race by preserving both requested and effective swapchain extents and regenerating the exact synthetic UI at the effective extent when they briefly differ.
- Compiles four GLSL shaders with `glslangValidator --target-env vulkan1.2` and validates each with `spirv-val --target-env vulkan1.2`.
- Renders triangle then premultiplied UI with `ONE, ONE_MINUS_SRC_ALPHA` blending.
- Runs a real two-frame 8x8 GPU readback check for sentinel layout, alpha blending/triangle visibility, and UI animation.
- Uses timestamp queries only when the graphics queue reports nonzero valid bits and the physical device reports a finite positive timestamp period.
- Emits strict `rlhd.renderer.timing/v1` JSONL compatible with Task 3's outcomes/counters, with Vulkan capability fields and truthful unsupported presentation callback fields.
- Documents the opt-in build/run boundary, log schema, exact 30-minute lifecycle checklist, provenance, and unresolved driver diagnostic.

No 117HD renderer, production shader, production dependency, default task, or default artifact was changed.

## Test-driven progression

The recovered first red compile had 29 expected missing-symbol errors for the Task 4 types. Subsequent focused red/green slices covered:

- capability truth: missing MAILBOX/effective fallback and optional portability-subset extension selection;
- swapchain selection and synchronization: missing extent/image/composite-alpha selection and per-image semaphore/fence model;
- renderer seam: missing backend access, counter materialization, exact Task 3 UI generation, readback, and close lifecycle;
- telemetry: strict capability/mode/counter/nullability validation;
- exact buffering requirement: a focused test failed until surfaces capped below three images were rejected.

The first headful bring-up also exposed and fixed real integration defects:

1. LWJGL 3.3.2 tried its default `libvulkan.1.dylib` before the configured loader. Setting `Configuration.VULKAN_EXPLICIT_INIT` before first `VK` access allowed explicit initialization.
2. Descriptor writes omitted `descriptorCount(1)`. Validation identified the malformed writes; both live and readback writes now set it.
3. LWJGL's `VkSubmitInfo.pWaitSemaphores` does not populate `waitSemaphoreCount`, while `pCommandBuffers`, `pSignalSemaphores`, and `VkPresentInfoKHR.pWaitSemaphores` do populate their associated counts. The submit wait count is now explicit and the other generated setters remain authoritative.
4. Before that correction validation reported `UNASSIGNED-VkPresentInfoKHR-pImageIndices-MissingAcquireWait`, `VUID-vkAcquireNextImageKHR-semaphore-01286`, and an unassigned queue-submit presentable-image acquire-wait diagnostic. Fresh validation runs emit none of them.
5. AWT resized the live `CAMetalLayer` to 777x403 before Task 2's stored extent moved from 640x332. The effective/requested extent handling above removed the stale upload failure.
6. Gson's default omitted explicitly-added JSON nulls. A logger regression test and `serializeNulls()` restored required `presented_time_ns`, `latency_ns`, GPU, and error null fields.

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

Result: `BUILD SUCCESSFUL in 42s`, 30/30 tasks executed. This included production `test`/`jar`, Task 2's 100-cycle native harness, Task 2 Java tests, Task 3 pure tests, 25 Task 4 pure tests, all four fresh shader compile/validation pairs, dependency isolation, and the isolated jar.

Final headful validation run:

```sh
JAVA_HOME=... RLHD_VULKAN_SDK=... VK_DRIVER_FILES=... VK_LAYER_PATH=... \
DYLD_LIBRARY_PATH=... ./gradlew --no-daemon --rerun-tasks \
  -Drlhd.spike.vulkan.loader=.../libvulkan.1.4.350.dylib \
  vulkanControlIntegrationTest
```

Result: `BUILD SUCCESSFUL in 7s`, 15/15 tasks executed, one integration test passed in 3.975 seconds. Observed facts:

- MoltenVK 1.4.2 selected Apple M3 Ultra and created a Vulkan 1.2.357 instance.
- Validation requested/enabled: true/true; no validation warning/error messages in the final run.
- `VK_KHR_portability_subset` and `VK_KHR_swapchain` enabled on the device.
- Three images created for every swapchain generation.
- Actual GPU readback passed before live rendering.
- Resize, suspend/restore, IMMEDIATE/FIFO recreation, fullscreen entry/exit, and return to 777x403 completed.
- 199 submitted = 199 completed; 199 acquisition requests = 199 completions; `max_in_flight=2`; five recreations; zero command/init/shader/pipeline errors; final app-tracked `live_native_objects=0`.
- The complete JSONL run validated with a single run start/end and 200 attempted-frame records (199 submitted, one skipped suspended).

Artifact/dependency checks:

- Production `build/libs/hd-1.5.2.jar` contains no spike classes, Vulkan shaders, LWJGL Vulkan classes, or `SyntheticUi`.
- `build/libs/hd-1.5.2-vulkan-control-spike.jar` contains Task 2 surface classes, Task 4 classes/SPIR-V, and only Task 3 `SyntheticUi`; it contains no dylib/JNI or bundled LWJGL classes.
- `VulkanControlRenderer` class major version is 55 (Java 11).
- Runtime dependency set is exactly LWJGL core 3.3.2, LWJGL Vulkan Java 3.3.2, core `natives-macos-arm64`, and spike Gson; no `lwjgl-vulkan` native exists or is requested.
- `git diff --check` passed.

## Unresolved observation and manual work

MoltenVK prints `Destroyed VkPhysicalDevice ... with 50 MB of GPU memory still allocated` after the automated fullscreen lifecycle. All successful app `vkCreate*`/`vkAllocate*` calls were audited against destruction/free or descriptor-pool ownership, validation is clean, `vkDeviceWaitIdle` precedes retirement, and the app counter reaches zero. Disabling MoltenVK's documented placement-heap allocator with `MVK_CONFIG_USE_MTLHEAP=0` still reported 50 MB. A one-second window-only run reported 7 MB, so the diagnostic scales with the largest CAMetalLayer/swapchain history and occurs while the Task 2 layer must still remain attached until after Vulkan teardown. This evidence is consistent with MoltenVK/CAMetalLayer/driver high-water retention at diagnostic time, but it is not authoritative proof and is not a zero-GPU-growth result.

The manual 30-minute lifecycle/footprint checklist and multi-display migration remain unexecuted. They must explicitly watch process and GPU footprint and determine whether it stabilizes before Task 4 can support any soak/stability claim.

## References

- [MoltenVK runtime guide](https://github.com/KhronosGroup/MoltenVK/blob/main/Docs/MoltenVK_Runtime_UserGuide.md)
- [MoltenVK configuration parameters](https://github.com/KhronosGroup/MoltenVK/blob/main/Docs/MoltenVK_Configuration_Parameters.md)
- [Vulkan portability enumeration](https://docs.vulkan.org/refpages/latest/refpages/source/VK_KHR_portability_enumeration.html)
- [`VK_EXT_metal_surface`](https://docs.vulkan.org/refpages/latest/refpages/source/VK_EXT_metal_surface.html)
- [`vkAcquireNextImageKHR`](https://docs.vulkan.org/refpages/latest/refpages/source/vkAcquireNextImageKHR.html)
- [`vkQueuePresentKHR`](https://docs.vulkan.org/refpages/latest/refpages/source/vkQueuePresentKHR.html)
