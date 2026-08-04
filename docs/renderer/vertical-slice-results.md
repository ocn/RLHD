# Vulkan vertical-slice results

- Date: 2026-08-03
- Host: Apple M3 Ultra Mac Studio
- OS build: macOS 25F84
- Loader: Vulkan SDK 1.4.350.1 arm64 loader
- Driver: MoltenVK 1.4.2, reporting Vulkan 1.4.357
- Requested API: Vulkan 1.2

## Result

The isolated surface-free Vulkan slice is viable on this host. It created a portability-enumerated instance, selected the Apple M3 Ultra graphics queue, enabled `VK_KHR_portability_subset`, created opaque/depth/UI pipelines, uploaded packed geometry and premultiplied BGRA UI data, submitted offscreen commands, copied BGRA8 output to host memory, and destroyed every app-tracked handle.

Run `R-20260804-018` freshly executed all 19 tasks against pinned commit `d8e430f67add211e02ac7f89f317fd85a8e9f191`. The complete analytic scene mask and exact-UI-over-BASE composition passed with `VK_LAYER_KHRONOS_validation` and `VK_EXT_debug_utils` enabled: zero validation warnings and zero validation errors from instance creation through destruction, including teardown-time messages checked after close. It did not create an AWT window, native layer, Vulkan surface, swapchain, acquire operation, present operation, fullscreen state, or display transition. No host panic occurred.

The first two GPU runs isolated an incorrect CPU winding assumption. Clear/readback and UI composition passed while the original triangle produced zero colored pixels. Reversing the triangle rendered; the pure contract was corrected so `VK_FRONT_FACE_CLOCKWISE` with a negative-height viewport recognizes a positive value from this helper's y-down cross-product convention, which is opposite Vulkan's signed-area convention. The normalized and validation-enabled runs then passed.

## Verified

- Vulkan 1.2 instance, physical-device, logical-device, and graphics-queue lifecycle through MoltenVK.
- Required BGRA8, D32, signed-short, half-float, and signed-int format capabilities are checked before device selection.
- Exact 28-byte opaque vertex binding and scalar storage-buffer metadata interface compile into live pipelines.
- Exact Task 1 mapped-uploader vertex and face hashes, 18-vertex BASE fixture, all-pixel analytic coverage/background/alpha, two interior RGB samples at tolerance 2, and all-pixel exact-UI-over-scene composition in run `R-20260804-018`.
- Reverse-Z state: `D32_SFLOAT`, clear `0`, and `GREATER_OR_EQUAL`.
- Negative-height viewport, clockwise front face, and back-face culling with a live winding check.
- Premultiplied UI blend using `ONE` and `ONE_MINUS_SRC_ALPHA`.
- UI upload/composition passed live with tightly packed rows; a CPU unit test proves non-texel-aligned row padding is repacked tightly before upload.
- UI-only frames with empty opaque geometry.
- Offscreen transfer-to-host synchronization and BGRA8 readback.
- Transactional per-call cleanup, persistent-handle balance, process-global loader retention, and zero validation messages.
- Normal RLHD JAR/runtime contains no Vulkan, MoltenVK, offscreen-spike, or LWJGL dependency; the isolated runtime contains only LWJGL core and Vulkan bindings plus the host native classifier.

## Not verified

- OpenGL-versus-Vulkan full-image equivalence, edge mask, and broader-scene numeric tolerance.
- Alpha geometry, textures/material effects, dynamic models, shadows, tiled lighting, water, or post-processing.
- `RendererBackend` production wiring, scene generation replacement, or OpenGL fallback selection.
- Any native view, surface, swapchain, presentation, frame pacing, resize, Retina, fullscreen, multi-display, or WindowServer behavior.
- Windows or Linux GPU execution; only their dependency classifier and production isolation are represented in this checkpoint.
- Performance, long-duration stability, or the cause of panic `P-0001`.

## Commands

```sh
./gradlew --no-daemon vulkanOpaqueSliceCheck vulkanOffscreenSliceTest
./gradlew --no-daemon vulkanOffscreenIntegrationTest \
  -PvulkanOffscreenAcknowledgement=I_ACCEPT_OFFSCREEN_GPU_WORK
```

The append-only run evidence is in [panic-investigation-runs.jsonl](panic-investigation-runs.jsonl), runs `R-20260803-001` through `R-20260804-018`.
