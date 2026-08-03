# Renderer dependency and ownership map

| Area | Current ownership / seam | Modern-renderer boundary |
| --- | --- | --- |
| Plugin lifecycle | `rs117.hd.HdPlugin` currently calls rlawt `AWTContext.createGLContext()` before selecting `ZoneRenderer` or `LegacyRenderer` | Keep selection and OpenGL fallback unchanged in Task 1. A Vulkan/Metal branch must be selected before that call because a Canvas has one JAWT overlay layer; it cannot coexist with the rlawt GL context. |
| Renderer contract | `rs117.hd.renderer.Renderer` | A future backend must preserve callback/lifecycle semantics before it can be selectable. |
| Current scene renderer | `renderer/zone/ZoneRenderer`, `Zone`, `WorldViewContext` | OpenGL implementation; source of scene upload/draw behavior to measure, not replace yet. |
| Legacy renderer | `renderer/legacy/LegacyRenderer` | Existing OpenGL fallback; retain across all platforms. |
| Compute / shader support | `opengl/compute`, `opengl/shader` | OpenGL/OpenCL ownership remains untouched; shader conversion is a later build concern. |
| RuneLite API/client | RuneLite pinned source | External API and distribution ecosystem owner; no acceptance is implied. |
| Native windowing | [LWJGLX/lwjgl3-awt](https://github.com/LWJGLX/lwjgl3-awt/tree/7c3e75295da6a0af3b96a1dfa9decef331689e60) and current rlawt JAWT overlay | Candidate AWT/surface bridge. Selection must precede GL-context creation; audit lifecycle and packaging before use. |
| Vulkan on macOS | [KhronosGroup/MoltenVK](https://github.com/KhronosGroup/MoltenVK/tree/df94d24c1c515eb9c6f89f82a965201e2fc43254) | Vulkan-to-Metal portability layer; availability and portability flags are preflight gates. Direct Metal presentation, if used, owns a per-instance `CAMetalLayer` lifecycle. |
| Prior experiment | [dennisdevulder/gpu-vulkan](https://github.com/dennisdevulder/gpu-vulkan/tree/12f8db08c8bac0d0180f96738b1fd9b075215496) | Reference only. Exclude its global/custom-present path; do not inherit its support or distribution claims. |

`HdPlugin` currently creates the rlawt OpenGL context and then chooses between `LegacyRenderer` and `ZoneRenderer`; both are OpenGL-backed in this checkout. This map records seams, not permission to alter external projects.
