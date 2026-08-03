# Provenance register

| Claim / decision | Evidence | Scope |
| --- | --- | --- |
| MoltenVK maps Vulkan to Metal and has portability-enumeration requirements on macOS. | [Pinned MoltenVK README](https://github.com/KhronosGroup/MoltenVK/tree/df94d24c1c515eb9c6f89f82a965201e2fc43254) | Technical design input. |
| Plugin Hub forbids JNI for reviewability. | [RuneLite policy](https://github.com/runelite/runelite/wiki/Rejected-or-Rolled-Back-Features) | Plugin Hub acceptance only. |
| `glslangValidator` is appropriate to record for GLSL-to-SPIR-V preparation. | [glslang upstream](https://github.com/KhronosGroup/glslang) | Build-tool capability only. |
| Metal developer tools are provided with Xcode. | [Apple Metal tools](https://developer.apple.com/metal/tools/) | macOS toolchain check. |
| Current architecture ownership and selection seams. | Local `HdPlugin`, `Renderer`, `ZoneRenderer`, `LegacyRenderer` at pinned RLHD commit | This fork only. |
| rlawt/JAWT ordering and single-overlay constraint; per-instance `CAMetalLayer` lifecycle; exclusion of the global/custom-present experiment. | Local source audit and project-supplied architecture evidence | Must remain an implementation constraint until reconfirmed with maintainers. |
| ZoneRenderer timing, PR #990 fallback scope, and prior submission outcome. | Project-supplied historical evidence; links retained in ADR where available | Must be reconfirmed with maintainers before upstream action. |

This register distinguishes primary-source facts from local observations and historical project evidence. It does not treat a URL as approval by its owner.
