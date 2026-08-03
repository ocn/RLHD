# Task 2A implementation report

Status: `IMPLEMENTED_OFFLINE_AWAITING_INDEPENDENT_REVIEW`

Base: `7818bd603fc8d456b64e3eac29e92e89bf351de2`

Implementation commit: `865fc6ff366dd2e85f3e40e4a70a0fb7938649ec`

Task 2A locks a CPU/offline contract only. It does not establish Vulkan renderer correctness, GPU compatibility,
presentation correctness, performance, or production readiness. Task 2B remains gated.

## Files

- `docs/renderer/implementation-plan.md`: controller-authored authoritative Task 2A/2B split.
- `build.gradle`: isolated `vulkanOpaqueSlice` Java/resource/test source sets; Vulkan 1.2 GLSL compilation,
  `spirv-val`, deterministic JSON reflection, tests, spike JAR, and production isolation gate.
- `src/main/java/rs117/hd/renderer/{RendererBackend,FrameOutcome,ZoneKey,CameraUniforms,SurfaceExtent,PreparedUiTexture,PreparedFrame}.java`:
  immutable API-neutral frame/backend contracts.
- `src/test/java/rs117/hd/renderer/{RendererBackendContractTest,PreparedFrameTest}.java`: public-contract tests.
- `spikes/vulkan-opaque-slice/src/main/java/rs117/hd/spikes/vulkan/opaque/VulkanOpaqueZoneContract.java`:
  byte-exact opaque upload validation/serialization and pure pipeline manifest.
- `spikes/vulkan-opaque-slice/src/main/java/rs117/hd/spikes/vulkan/opaque/VulkanZoneResourcePlan.java`:
  pure generation-safe replacement, rollback, teardown, and ledger model.
- `spikes/vulkan-opaque-slice/src/main/shaders/{opaque.vert,opaque.frag,ui.vert,ui.frag}`: real GLSL 450 shaders.
- `spikes/vulkan-opaque-slice/src/test/java/rs117/hd/spikes/vulkan/opaque/VulkanOpaqueZoneContractTest.java`:
  Task 1 BASE fixture, byte/layout/pipeline/reflection, winding/projection, and lifecycle/failure tests.

Generated SPIR-V and reflection JSON live only under the isolated source set's generated-resource output and its
explicit spike JAR. The ordinary production JAR/runtime has no opaque-slice class, resource, LWJGL dependency,
Vulkan dependency, or native artifact.

## Red/green evidence

Public seam RED:

```sh
./gradlew --no-daemon --console=plain compileTestJava
```

Result: expected failure with 30 missing-symbol errors for the new frame/backend contracts.

Public seam GREEN:

```sh
./gradlew --no-daemon --console=plain test --tests 'rs117.hd.renderer.PreparedFrameTest' --tests 'rs117.hd.renderer.RendererBackendContractTest'
```

Result: `BUILD SUCCESSFUL`; 5 tests, 0 failures/errors/skips.

Isolated seam RED:

```sh
env RLHD_VULKAN_SDK='.../VulkanSDK/1.4.350.1/macOS' ./gradlew --no-daemon --console=plain vulkanOpaqueSliceTest
```

Result after the tests were written first: expected failure at the reflection seam; 10 tests ran, 1 failed because
the first JSON reflector output did not report the computed 72-byte push range. The deterministic reflection step
was then made to derive the range from reflected member types and offsets. A later focused RED also caught
`B8G8R8A8_SRGB` versus the audited `B8G8R8A8_UNORM` attachment requirement before GREEN.

Isolated seam GREEN:

```sh
env RLHD_VULKAN_SDK='.../VulkanSDK/1.4.350.1/macOS' ./gradlew --no-daemon --console=plain vulkanOpaqueSliceCheck
```

Result: `BUILD SUCCESSFUL`; 21 tasks considered, 9 executed and 12 up-to-date; 10 tests passed; all four shaders
compiled with `--target-env vulkan1.2`, passed `spirv-val`, and produced deterministic reflection; JAR/runtime
isolation passed.

Task 1 BASE plus public regression:

```sh
./gradlew --no-daemon --console=plain test --tests '*PreparedZoneGeometryTest' --tests 'rs117.hd.renderer.PreparedFrameTest' --tests 'rs117.hd.renderer.RendererBackendContractTest'
```

Result: `BUILD SUCCESSFUL`; 12 tests, 0 failures/errors/skips.

Final hygiene: `git diff --check` and `git diff --cached --check` exited 0 before the implementation commit.

## Locked offline contract

- Vertex record: 28 bytes; `R16G16B16A16_SINT` offset 0, `R16G16B16A16_SFLOAT` offset 8,
  `R16G16B16A16_SINT` offset 16, and `R32_SINT` offset 24.
- Face record: nine little-endian scalar ints (36 bytes); reflected SSBO `ArrayStride` is 4 at set 0/binding 0.
- Push range: vertex-stage 72 bytes; `mat4 clipFromWorld` offset 0 and `ivec2 sceneBase` offset 64.
- Geometry: opaque count comes from `opaqueVertices().remaining() / 7`; alpha is rejected; ranges must be
  contiguous opaque triangles; signed references pin reversed winding, bounds, and BASE face/material associations.
- Shader color: packed alpha-HSL converts to linear RGB per vertex, interpolates, then explicitly converts to sRGB
  in the fragment shader. The attachment contract is therefore `B8G8R8A8_UNORM` with `SRGB_NONLINEAR` color space.
- Depth/raster/UI manifest: reverse-Z `D32_SFLOAT`, clear 0, `GREATER_OR_EQUAL`, negative-height viewport, and
  premultiplied UI factors `ONE` / `ONE_MINUS_SRC_ALPHA`.
- Lifecycle: exact-generation render identity, coordinate replacement, stale destroy safety, unknown-zone rejection,
  zero-extent suspension, partial allocation rollback, aggregated teardown, and ledger underflow are deterministic.

## Task 2B and live claims — NOT RUN

- Physical-device enumeration and format/feature support: `NOT RUN`.
- Vulkan loader/device creation and MoltenVK pipeline compilation: `NOT RUN`.
- Native layer, surface, swapchain, drawable, window, and display-topology behavior: `NOT RUN`.
- GPU clipping, interpolation, culling, winding, depth, and raster parity: `NOT RUN`.
- BGRA, gamma, orientation, Retina scaling, and premultiplied UI blend parity: `NOT RUN`.
- Validation-layer cleanliness and GPU/offscreen readback golden comparison: `NOT RUN`.
- Acquire, submit, present, presentation callbacks, and their balance: `NOT RUN`.
- Real GPU resource retirement, in-flight generation replacement, and zero live Vulkan objects: `NOT RUN`.
- Performance, frame-time, CPU/GPU/WindowServer footprint, movement, lifecycle, and soak conclusions: `NOT RUN`.

No `IntegrationTest`, headful task, native loader, Vulkan loader, physical-device API, device API, surface API,
swapchain API, submit API, present API, window, or drawable was invoked during Task 2A.
