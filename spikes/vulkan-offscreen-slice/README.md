# Surface-free Vulkan vertical slice

This slice implements Vulkan 1.2 instance/device selection, buffers, images, command submission, the Task 2 opaque and UI pipelines, and BGRA8 GPU readback. It deliberately contains no AWT, Metal layer, Vulkan surface, swapchain, acquire, or present call.

CPU/static verification does not initialize Vulkan:

```sh
./gradlew --no-daemon vulkanOffscreenSliceTest
```

The opt-in integration task requires the Khronos validation layer, creates a Vulkan device, submits 64x64 opaque and UI-only offscreen frames, waits for the graphics queue, checks the readback and validation counters, and tears every app-tracked handle down. It still does not create a window or enter the display presentation path:

```sh
./gradlew --no-daemon vulkanOffscreenIntegrationTest \
  -PvulkanOffscreenAcknowledgement=I_ACCEPT_OFFSCREEN_GPU_WORK
```

On macOS, `RLHD_VULKAN_LOADER` must identify the reviewed MoltenVK dynamic library. Windows and Linux use the host Vulkan loader. The loader is process-global and intentionally remains initialized until process exit; renderer instances own only their instance/device resources. This source set and its LWJGL/native dependencies remain outside the production RLHD artifact.
