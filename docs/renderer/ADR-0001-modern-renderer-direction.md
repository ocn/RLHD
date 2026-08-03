# ADR-0001: modern renderer direction

**Status:** hold production port; accepted for gated local-fork experiments only.

## Decision

Keep the existing OpenGL `ZoneRenderer` and `LegacyRenderer` as the default and fallback on macOS, Windows, and Linux. Do not begin a production renderer port.

If the gates in [go-no-go-gates.md](go-no-go-gates.md) pass, the next real-scene experiment should test Vulkan, using [MoltenVK](https://github.com/KhronosGroup/MoltenVK/tree/df94d24c1c515eb9c6f89f82a965201e2fc43254) on macOS. This is a preferred hypothesis, not a selected production backend. Direct Metal remains a macOS presentation and translation-overhead control. A shared RuneLite renderer abstraction is deferred until RuneLite and rlawt owners agree that its cross-plugin value justifies core ownership.

Jagex compliance, technical feasibility, and RuneLite/Plugin Hub acceptance are separate decisions. The [Jagex third-party client policy](https://secure.runescape.com/m=news/third-party-clients-update?oldschool=1) is a compliance input, not engineering approval.

## Options

| Option | Evidence and benefit | Cost or unresolved constraint | Decision |
| --- | --- | --- | --- |
| Retain OpenGL | It is the only complete 117HD renderer and preserves every current platform. Apple still exposes OpenGL on Apple silicon while marking it deprecated. [Apple porting guidance](https://developer.apple.com/documentation/apple-silicon/porting-your-macos-apps-to-apple-silicon) | macOS remains on a deprecated compatibility path; it does not test whether a modern API improves frame pacing or CPU/driver cost. | Default and rollback on every OS. Continue independent OpenGL optimization. |
| Direct Metal | Task 3 proved, on one M3 Ultra/macOS/JDK combination, per-instance `CAMetalLayer` presentation, exact synthetic Java UI upload, GPU readback, resize/suspend/fullscreen/mode changes, stale-drawable rejection, and deterministic app-owned cleanup. [Apple OpenGL migration overview](https://developer.apple.com/documentation/metal/migrating-opengl-code-to-metal) | macOS-only Objective-C/JNI ownership plus a second shader/resource implementation. It did not render a RuneLite scene, port 117HD shaders, test Intel macOS, prove packaging, or establish a performance or uptime benefit. | Keep as a bounded control; no full-scene port. |
| Vulkan/MoltenVK | Task 4 proved, on the same single Mac class, a Vulkan 1.2 control with standard swapchain presentation, triangle/UI passes, GPU readback, lifecycle recreation, presentation-fence retirement, failure cleanup, and zero validation warnings/errors in the final automated run. Vulkan offers one backend model that could later use native Vulkan on Windows/Linux. [MoltenVK runtime guide](https://github.com/KhronosGroup/MoltenVK/blob/v1.4.2/Docs/MoltenVK_Runtime_UserGuide.md) | MoltenVK is a portability subset over Metal; the control did not render 117HD scene data or exercise its 47-shader corpus. Manual 30-minute/multi-display checks are absent, and MoltenVK reported unresolved 50/49 MB retained-memory diagnostics. Native surface/runtime distribution and qualified review ownership remain unapproved. | Preferred next full-scene hypothesis after all entry gates; not production-selected. |
| Shared RuneLite renderer abstraction | Could centralize AWT/native surfaces and let more than 117HD reuse an accepted backend. | Broad RuneLite/rlawt API, lifecycle, packaging, and long-term support commitment; no maintainer has accepted that ownership. | Ask maintainers; do not make it a prerequisite or implement it in 117HD unilaterally. |

## Required architecture boundary

The external seam is semantic scene preparation, not a thin wrapper around OpenGL calls:

```text
RuneLite DrawCallbacks
        |
117HD scene preparation -> PreparedFrame / PreparedZoneGeometry
        |                    CPU buffers, draw ranges, material/pass IDs
        +-> OpenGL backend -> current rlawt GL context
        +-> Vulkan backend -> platform SurfaceProvider -> swapchain/present
```

`PreparedFrame` and `PreparedZoneGeometry` must not expose GL enums, Vulkan handles, descriptor bindings, or synchronization. Each backend owns upload resources, pipelines, render passes, synchronization, and presentation. 117HD owns scene/material semantics; RuneLite owns `DrawCallbacks`, GPU flags, and UI pixels; rlawt or RuneLite core should own an accepted backend-neutral AWT native surface if maintainers approve it.

`HdPlugin` currently creates rlawt's OpenGL context before renderer selection. A modern backend must be selected before `AWTContext.createGLContext()` because the Canvas has one JAWT overlay layer. Startup failure must return to OpenGL through a controlled restart/reselection path; the two contexts must not coexist.

## What the controls do not establish

- Neither control proves correctness for login, world hop, RuneLite scene callbacks, zone uploads, materials, shadows, tiled lighting, water, post-processing, or the real UI texture flow.
- Neither proves performance parity, a benefit over OpenGL, or a fix for the reported long-uptime degradation.
- Neither proves Intel macOS, Windows, Linux, packaging, signing, Plugin Hub acceptance, or a support owner.
- Task 4's app-tracked native objects reached zero and validation was clean, but the unresolved MoltenVK retained-memory diagnostic prevents a zero-growth claim.

## Prior-art corrections

- [`gpu-vulkan` PR #8](https://github.com/dennisdevulder/gpu-vulkan/pull/8), “Add Vulkan render backend extension API,” was merged on May 20, 2026 into that WIP renderer's own repository (`f60ab4afdbf2862fa181d25fabd49625c5d4cb3f`). It did not add Vulkan to 117HD or RuneLite. Its renderer already owns `DrawCallbacks`; evaluate collaboration or extraction of its platform/backend work before duplicating it, but do not assume coexistence or upstream acceptance.
- [`lwjgl3-awt` commit `05784f9`](https://github.com/LWJGLX/lwjgl3-awt/commit/05784f9517ac3fa6ce30fa17a2b61c5c2d10ee3e) is “Add macOS support (#23)” from August 26, 2020, not April 2026. Its JNI/Objective-C and bundled-dylib approach is useful historical prior art, not evidence that current RuneLite packaging accepts it.
- `ZoneRenderer` merged in [117HD commit `ea50dc4`](https://github.com/117HD/RLHD/commit/ea50dc40188e38d15191dbb2776bcd059c02902f) on November 5, 2025 and remains OpenGL. [PR #990](https://github.com/117HD/RLHD/pull/990) improved the macOS OpenGL fallback path; neither is a Metal or Vulkan backend.

## Revisit

Revisit only when every entry gate is recorded as passed, the minimal opaque-zone slice has independently reviewed correctness evidence, and same-scene measurements justify the added support burden. Follow [implementation-plan.md](implementation-plan.md), not a wholesale rewrite.
