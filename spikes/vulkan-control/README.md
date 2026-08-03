# Vulkan/MoltenVK presentation control spike

This disposable, opt-in spike compares Vulkan 1.2 through MoltenVK with the direct-Metal Task 3 control. It borrows the same Task 2 `MacMetalSurface`, calls the exact Task 3 `SyntheticUi` generator, and renders the same animated triangle plus premultiplied-BGRA UI workload. It does not enter the 117HD scene renderer, change the OpenGL default, enable the historical custom-present path, or enter normal `test`, `check`, `jar`, or Plugin Hub artifacts.

The renderer explicitly loads a Vulkan loader, enables `VK_KHR_surface`, `VK_EXT_metal_surface`, and `VK_KHR_portability_enumeration` with `VK_INSTANCE_CREATE_ENUMERATE_PORTABILITY_BIT_KHR`, then creates the Metal surface before selecting the physical device. The device must support Vulkan 1.2, graphics and surface presentation, `VK_KHR_swapchain`, `VK_FORMAT_B8G8R8A8_UNORM`, FIFO, and at least three swapchain images. `VK_KHR_portability_subset` is enabled only when advertised. The requested swapchain has three images and exactly two reusable frame slots.

Presentation always uses `vkQueuePresentKHR`. FIFO is the synchronized baseline. `unlocked` selects IMMEDIATE when advertised; otherwise the log records MAILBOX as a vblank-synchronized, uncapped fallback or FIFO as the final fallback. MAILBOX is never accepted as an unlocked request. Requested and effective modes remain separate in every record.

Each frame slot owns one command buffer, acquire semaphore, fence, timestamp query pool when supported, staging buffer, and UI image. Presentation-wait semaphores are owned per swapchain image, not per frame slot, so a re-acquired image cannot reuse a semaphore still owned by presentation. The fence is reset only after a successful image acquisition and command recording. Resize, scale, restore, fullscreen, or mode changes wait for the device, settle both frame slots, and destroy the old generation before replacement. A transient mismatch between the Task 2 extent snapshot and the window system's fixed current extent regenerates the exact synthetic UI at the effective swapchain extent instead of uploading stale-sized data.

SPIR-V is compiled and validated for Vulkan 1.2 from four spike-local GLSL files. The live path performs two passes: an animated triangle, then a full-screen UI composite with `ONE, ONE_MINUS_SRC_ALPHA` premultiplied blending. The automated GPU readback renders two distinct 8x8 Task 3 UI frames through the same pipelines and checks sentinel colors, blended triangle visibility, and animation.

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
JAVA_HOME="$RLHD_VULKAN_JAVA_HOME" ./gradlew --no-daemon vulkanControlIntegrationTest
JAVA_HOME="$RLHD_VULKAN_JAVA_HOME" ./gradlew --no-daemon runVulkanControlSpike --args='--seconds 1800 --log build/spikes/vulkan-control/manual-30m.jsonl --validation true'
```

`vulkanControlSpikeCheck` compiles and validates all SPIR-V, runs the pure capability/swapchain/fence/renderer/schema tests, builds the isolated artifact, and reuses the Task 2 surface checks. `vulkanControlIntegrationTest` is explicitly headful. It opens an AWT window, enables the validation layer when present, performs actual GPU readback, renders 199 submitted frames through resize, zero-extent suspend/restore, FIFO/IMMEDIATE toggles, and native fullscreen when supported, validates the complete JSONL log, and requires balanced completion/acquisition counters plus zero app-tracked live Vulkan objects. Both are excluded from the normal production lifecycle.

The runtime configuration uses LWJGL 3.3.2 core, its macOS arm64 core native, and the Java-only `lwjgl-vulkan` binding. It deliberately does not request a nonexistent `lwjgl-vulkan` native. The Vulkan loader and MoltenVK ICD are external test prerequisites and are not packaged by `vulkanControlSpikeJar`.

## Timing JSON Lines

Every record uses `schema="rlhd.renderer.timing/v1"` and `backend="vulkan-control"`.

- `run_start` records requested/effective modes, Vulkan 1.2, portability and surface/swapchain support, optional portability subset, format/color space, requested/actual image counts, two frame slots, standard queue presentation, queue-family selection, validation/debug status, timestamp support, available present modes, and physical device.
- `frame` records the effective pixel extent, requested/effective mode, outcome, CPU generation/upload/encode/submit/total times, guarded GPU timestamps when supported, unsupported presentation-callback fields, a complete Task 3-compatible counter snapshot, and an optional error.
- `run_end` records final modes, counters, and initialization/render error state. A valid final record requires `live_native_objects=0`.

The strict schema rejects malformed or duplicate JSON, missing/nullability mistakes, invalid modes/outcomes, negative or inconsistent timing, more than two frames in flight, counter inconsistencies, incorrect run order, custom-presentation capability claims, or nonzero app-tracked objects at shutdown. Vulkan offers no Task 3-style drawable presentation callback in this path, so those fields truthfully remain `unsupported`/null and their counters remain zero.

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

The automated M3 Ultra run destroyed every app-owned Vulkan handle tracked by the spike and passed validation, but MoltenVK 1.4.2 printed `50 MB of GPU memory still allocated` when its physical-device object was destroyed after a 5K fullscreen swapchain. The same run with MoltenVK placement heaps disabled reported 50 MB, while a window-only run reported 7 MB. This is consistent with unresolved MoltenVK/CAMetalLayer high-water retention at driver diagnostic time, not proof of an app leak or proof of zero GPU growth. The 30-minute and multi-display checks must watch process/GPU footprint before any stability claim.

Primary references: the [MoltenVK runtime guide](https://github.com/KhronosGroup/MoltenVK/blob/main/Docs/MoltenVK_Runtime_UserGuide.md), [MoltenVK configuration parameters](https://github.com/KhronosGroup/MoltenVK/blob/main/Docs/MoltenVK_Configuration_Parameters.md), Vulkan [`vkAcquireNextImageKHR`](https://docs.vulkan.org/refpages/latest/refpages/source/vkAcquireNextImageKHR.html), and Vulkan [`vkQueuePresentKHR`](https://docs.vulkan.org/refpages/latest/refpages/source/vkQueuePresentKHR.html).
