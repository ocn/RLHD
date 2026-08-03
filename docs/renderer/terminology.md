# Renderer terminology

- **OpenGL fallback:** the unchanged, default renderer path on every platform.
- **Control spike:** a disposable triangle/UI/lifecycle experiment that proves only its named surface, presentation, readback, and cleanup behaviors.
- **Vertical slice:** the first real-scene experiment: one static opaque top-level zone plus UI through a proposed backend, with OpenGL consuming the same prepared CPU data.
- **Full-scene backend:** an opt-in backend capable of rendering the complete 117HD scene lifecycle and advertised feature set, not a triangle, overlay, or opaque-zone slice.
- **Vulkan/MoltenVK:** Vulkan calls using MoltenVK's Metal-backed portability implementation on macOS; this is not direct Metal rendering.
- **Direct Metal presentation control:** a deliberately narrow macOS-only seam for presentation/window control, never the first full-scene backend.
- **Prepared zone geometry:** immutable CPU buffers, semantic draw ranges/material/pass IDs, and ownership metadata produced before API-specific upload; it contains no GL/Vulkan handles or synchronization.
- **Backend resource:** an API-owned buffer, image, sampler, shader/pipeline, descriptor, command object, synchronization primitive, or render target created from prepared data.
- **Surface provider:** the platform-owned AWT/native-view lifecycle that exposes an effective drawable extent to a backend without owning scene semantics.
- **Presentation:** queueing completed backend output to the platform/compositor; presentation-engine completion is distinct from queue/device idle.
- **SPIR-V:** Vulkan shader intermediate representation. `glslangValidator` is the planned GLSL-to-SPIR-V compiler; [glslang](https://github.com/KhronosGroup/glslang) is the Khronos reference front end and generator.
- **Readiness:** local tools needed for a reproducible performance measurement are present. It is not a claim of feature correctness or upstream acceptability.
- **Fork-first:** work may proceed in this authorized RLHD fork without representing it as accepted by Jagex, RuneLite, or Plugin Hub.
