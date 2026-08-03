# ADR-0001: modern renderer direction

**Status:** accepted for local fork exploration; no upstream or Plugin Hub submission decision.

## Decision

The first full-scene experiment uses Vulkan, with [MoltenVK](https://github.com/KhronosGroup/MoltenVK/tree/df94d24c1c515eb9c6f89f82a965201e2fc43254) on macOS. Direct Metal is bounded to presentation control only. The existing OpenGL renderer remains the default and fallback on every platform.

This is fork-first work. It is intentionally separable from RuneLite, Plugin Hub, and Jagex acceptance: (1) Jagex compliance, (2) technical feasibility, and (3) RuneLite/Plugin Hub acceptance require independent evidence and decisions. The [Jagex third-party client policy](https://secure.runescape.com/m=news/third-party-clients-update?oldschool=1) is a compliance input, not an engineering approval.

MoltenVK is a Vulkan portability implementation over Apple Metal and documents that it translates SPIR-V to MSL; it also documents portability-enumeration requirements on macOS. [MoltenVK upstream](https://github.com/KhronosGroup/MoltenVK/tree/df94d24c1c515eb9c6f89f82a965201e2fc43254)

## Constraints and consequences

- Keep `ZoneRenderer` and `LegacyRenderer` OpenGL paths intact. The modern backend is opt-in until performance and behavior evidence permits a separate decision.
- `HdPlugin` currently creates rlawt's JAWT overlay/OpenGL context before it chooses a renderer. A Canvas has one JAWT overlay layer, so a Vulkan/Metal selection must occur before `AWTContext.createGLContext()`; it cannot coexist with that rlawt context.
- A future macOS path owns a per-instance `CAMetalLayer` lifecycle. It excludes `gpu-vulkan`'s global/custom-present approach.
- The current `ZoneRenderer` merged in November 2025 and remains OpenGL. It is not a Vulkan precursor. PR [#990](https://github.com/117HD/RLHD/pull/990) is an OpenGL fallback improvement.
- Plugin Hub currently forbids JNI. The proposed Vulkan Plugin Hub submission was rejected because GPU/Vulkan review ownership was unavailable; that does not prohibit authorized local-fork work. [RuneLite rejected features policy](https://github.com/runelite/runelite/wiki/Rejected-or-Rolled-Back-Features)
- Any native binding or bundled runtime is therefore a local-fork feasibility concern first, not a Plugin Hub delivery path.

## Revisit when

Revisit this ADR after the full-scene slice has reproducible benchmark data, a renderer-owner review, and a fresh Jagex-policy check.
