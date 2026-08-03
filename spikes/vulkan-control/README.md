# Vulkan/MoltenVK presentation control spike

This disposable, opt-in spike compares Vulkan 1.2 through MoltenVK with the direct-Metal Task 3 control. It borrows the same Task 2 `MacMetalSurface`, calls the exact Task 3 `SyntheticUi` generator, and renders the same animated triangle plus premultiplied-BGRA UI workload. It does not enter the 117HD scene renderer, change the OpenGL default, enable the historical custom-present path, or enter normal `test`, `check`, `jar`, or Plugin Hub artifacts.

The renderer explicitly loads a Vulkan loader; enables `VK_KHR_surface`, `VK_KHR_get_surface_capabilities2`, `VK_EXT_surface_maintenance1`, `VK_EXT_metal_surface`, and `VK_KHR_portability_enumeration` with `VK_INSTANCE_CREATE_ENUMERATE_PORTABILITY_BIT_KHR`; then creates the Metal surface before selecting the physical device. The device must support Vulkan 1.2, graphics and surface presentation, `VK_KHR_swapchain`, the `VK_EXT_swapchain_maintenance1` feature, `VK_FORMAT_B8G8R8A8_UNORM + VK_COLOR_SPACE_SRGB_NONLINEAR_KHR`, FIFO, and at least three swapchain images. The control fails initialization if presentation retirement cannot be guaranteed. `VK_KHR_portability_subset` is enabled only when advertised. The requested swapchain has three images and exactly two reusable frame slots.

Presentation always uses `vkQueuePresentKHR`. FIFO is the synchronized baseline. `unlocked` selects IMMEDIATE when advertised; otherwise the log records MAILBOX as a vblank-synchronized, uncapped fallback or FIFO as the final fallback. MAILBOX is never accepted as an unlocked request. Requested and effective modes remain separate in every record.

Each frame slot owns one command buffer, acquire semaphore, submission fence, timestamp query pool when supported, staging buffer, and UI image. Presentation-wait semaphores and maintenance1 presentation fences are owned per swapchain image. A re-acquired image waits/resets its prior presentation fence before reuse, and retirement waits every live presentation fence before destroying the swapchain or its semaphores. A post-acquire failure consumes the acquire semaphore and releases the unused image with `vkReleaseSwapchainImagesEXT`, then terminalizes the renderer. Resize, scale, restore, fullscreen, mode changes, and close settle frame submissions and presentation fences before replacement or teardown. A transient mismatch in either requested dimension regenerates the exact synthetic UI at the effective swapchain extent.

SPIR-V is compiled and validated for Vulkan 1.2 from four spike-local GLSL files. The live path performs two passes: an animated triangle, then a full-screen UI composite with `ONE, ONE_MINUS_SRC_ALPHA` premultiplied blending. GPU readback uses a compatible offscreen render pass ending in `COLOR_ATTACHMENT_OPTIMAL`, then an explicit color-write-to-transfer-read barrier before copying. It renders two distinct 8x8 Task 3 UI frames through the same pipelines and checks sentinel colors, blended triangle visibility, and animation.

## Build, test, and run

Use a native arm64 JDK, the Vulkan SDK shader tools and validation layer, and a standalone MoltenVK distribution. These paths are examples only:

```sh
export RLHD_VULKAN_JAVA_HOME=/absolute/path/to/arm64-jdk/Contents/Home
export RLHD_VULKAN_SDK=/absolute/path/to/VulkanSDK/macOS
export RLHD_MOLTENVK=/absolute/path/to/MoltenVK/dylib/macOS
export RLHD_VULKAN_LOADER="$RLHD_VULKAN_SDK/lib/libvulkan.dylib"
export VK_DRIVER_FILES="$RLHD_MOLTENVK/MoltenVK_icd.json"
export VK_LAYER_PATH="$RLHD_VULKAN_SDK/share/vulkan/explicit_layer.d"
export DYLD_LIBRARY_PATH="$RLHD_MOLTENVK:$RLHD_VULKAN_SDK/lib"

JAVA_HOME="$RLHD_VULKAN_JAVA_HOME" ./gradlew --no-daemon vulkanControlSpikeCheck vulkanControlSpikeJar
JAVA_HOME="$RLHD_VULKAN_JAVA_HOME" ./gradlew --no-daemon vulkanControlIntegrationTest -PrendererHeadfulAcknowledgement=I_ACCEPT_KERNEL_PANIC_RISK
JAVA_HOME="$RLHD_VULKAN_JAVA_HOME" ./gradlew --no-daemon runVulkanControlSpike -PrendererHeadfulAcknowledgement=I_ACCEPT_KERNEL_PANIC_RISK --args='--seconds 1800 --log build/spikes/vulkan-control/manual-30m.jsonl --validation true'
```

`vulkanControlSpikeCheck` compiles and validates all SPIR-V, runs the swapchain-selection/renderer/schema tests, builds and inspects the isolated artifact, verifies the exact runtime dependency set, and reuses the Task 2 surface checks. `vulkanControlIntegrationTest` is explicitly headful. Its main test performs GPU readback and renders 199 submitted frames through resize, zero-extent suspend/restore, FIFO/IMMEDIATE toggles, and native fullscreen when supported. A second live-backend test injects failures into command, buffer, image, shader-module, readback, and partial-swapchain allocation as well as acquire-record-submit, recreation, and post-consumption close. Every injected path requires zero validation warnings/errors and zero app-tracked live Vulkan objects; submitted paths also require balanced counters.

Headful spike tasks are fail-closed because they create native presentation surfaces. Use the acknowledgement only on an expendable test host after reviewing its display topology and kernel-panic risk.

The exact runtime configuration is Gson 2.14.0 without transitive annotations, LWJGL 3.3.2 core, its macOS arm64 core native, and the Java-only `lwjgl-vulkan` binding. It deliberately does not request a nonexistent `lwjgl-vulkan` native. The isolation gate rejects extra spike runtime jars, LWJGL in production runtime, spike content in the production jar, and undeclared paths in `vulkanControlSpikeJar`. The Vulkan loader and MoltenVK ICD are external prerequisites and are not packaged.

## Timing JSON Lines

Every record uses `schema="rlhd.renderer.timing/v1"` and `backend="vulkan-control"`.

- `run_start` records requested/effective modes, Vulkan 1.2, portability and surface/swapchain support, required maintenance1/presentation-fence support, optional portability subset, format/color space, requested/actual image counts, two frame slots, standard queue presentation, queue-family selection, validation/debug status, timestamp support, available present modes, and physical device.
- `init_failure` opens a failed-construction log with the error and final post-cleanup counters, without fabricating unavailable swapchain capabilities. It is followed directly by `run_end` and cannot contain frames.
- `frame` records the effective pixel extent, requested/effective mode, outcome, CPU generation/upload/encode/submit/total times, guarded GPU timestamps when supported, unsupported presentation-callback fields, a complete Task 3-compatible counter snapshot, and an optional error.
- `run_end` records final modes, counters, and initialization/render error state. A valid final record requires `live_native_objects=0`.

The strict schema rejects malformed or duplicate JSON, missing/nullability mistakes, invalid modes/outcomes, negative or inconsistent timing, more than two frames in flight, unbalanced submission/presentation/acquisition counters, incorrect run order, custom-presentation capability claims, or nonzero app-tracked objects at shutdown. Validation warnings, validation errors, and timestamp-query errors have dedicated counters; validation errors are not command errors. Vulkan offers no Task 3-style drawable presentation callback in this path, so those fields truthfully remain `unsupported`/null and their counters remain zero.

## Manual 30-minute checklist

This checklist is provided for manual execution and was not completed by the automated test.

- Minutes 0–3: start the exact 1800-second command. Confirm the animated triangle/background and four UI sentinel regions match the Metal control; transparent UI must reveal the triangle and half-alpha red must blend.
- Minutes 3–7: resize continuously through small, wide, tall, and Retina-scaled sizes. Confirm there is no stale-sized UI, stretching, flash, or validation output.
- Minutes 7–10: minimize for at least 30 seconds, restore, and confirm acquisition resumes without a busy loop or error burst.
- Minutes 10–14: enter and exit native fullscreen twice. Confirm the effective resolution follows the drawable and BGRA/alpha output remains stable.
- Minutes 14–19: observe FIFO/unlocked/FIFO transitions and inspect requested/effective records. IMMEDIATE is the only unlocked analogue; treat MAILBOX as vblank-synchronized fallback.
- Minutes 19–24: move the window fully between displays, including a different scale or GPU when available. Resize on each display and confirm queue/device capability truth. This step remains manual because automation exercised only the available main display.
- Minutes 24–28: repeat minimize/restore, resize, and fullscreen while the UI changes. Confirm `max_in_flight <= 2`, `submitted >= completed`, and acquisition request/completion counts remain balanced.
- Minutes 28–30: close normally, validate every JSON line, and inspect `run_end`. Require `submitted == completed`, all error counters zero, and `live_native_objects == 0`.

Useful final checks:

```sh
python3 -c 'import json,sys; [json.loads(line) for line in sys.stdin]' < build/spikes/vulkan-control/manual-30m.jsonl
tail -1 build/spikes/vulkan-control/manual-30m.jsonl
```

After maintenance1 present-fence retirement, the automated M3 Ultra fullscreen run destroyed every app-tracked Vulkan handle and passed validation, but MoltenVK 1.4.2 still printed `50 MB of GPU memory still allocated`. Subsequent small-window injected-failure instances in the same process printed 49 MB after the fullscreen high-water. Earlier diagnostics reported 50 MB with placement heaps disabled and 7 MB for a separate window-only run. These are observations, not evidence assigning ownership to MoltenVK, CAMetalLayer, or the driver, and they do not support a zero-growth claim. The 30-minute and multi-display checks remain unexecuted.

Primary references: the [MoltenVK runtime guide](https://github.com/KhronosGroup/MoltenVK/blob/main/Docs/MoltenVK_Runtime_UserGuide.md), [swapchain semaphore reuse guide](https://docs.vulkan.org/guide/latest/swapchain_semaphore_reuse.html), [`VK_EXT_swapchain_maintenance1`](https://docs.vulkan.org/refpages/latest/refpages/source/VK_EXT_swapchain_maintenance1.html), [`VkSwapchainPresentFenceInfoEXT`](https://docs.vulkan.org/refpages/latest/refpages/source/VkSwapchainPresentFenceInfoEXT.html), [`vkReleaseSwapchainImagesEXT`](https://docs.vulkan.org/refpages/latest/refpages/source/vkReleaseSwapchainImagesEXT.html), and [render-pass compatibility](https://docs.vulkan.org/spec/latest/chapters/renderpass.html#renderpass-compatibility).
