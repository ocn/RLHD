# Renderer terminology

- **OpenGL fallback:** the unchanged, default renderer path on every platform.
- **Full-scene slice:** an opt-in backend capable of rendering the complete scene lifecycle, not a standalone triangle or overlay demo.
- **Vulkan/MoltenVK:** Vulkan calls using MoltenVK's Metal-backed portability implementation on macOS; this is not direct Metal rendering.
- **Direct Metal presentation control:** a deliberately narrow macOS-only seam for presentation/window control, never the first full-scene backend.
- **SPIR-V:** Vulkan shader intermediate representation. `glslangValidator` is the planned GLSL-to-SPIR-V compiler; [glslang](https://github.com/KhronosGroup/glslang) is the Khronos reference front end and generator.
- **Readiness:** local tools needed for a reproducible performance measurement are present. It is not a claim of feature correctness or upstream acceptability.
- **Fork-first:** work may proceed in this authorized RLHD fork without representing it as accepted by Jagex, RuneLite, or Plugin Hub.
