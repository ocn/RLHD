# Shader migration and correctness plan

## Strategy

The pinned checkout contains 47 GLSL files plus Java-side preprocessing, includes, uniforms, buffers, and OpenGL capability branches. Do not translate the corpus by hand into a parallel MSL implementation.

1. Freeze and test the existing include/preprocessor output for each feature variant.
2. Define semantic CPU/GPU layouts once: camera/frame values, packed vertices, materials, lights, draw ranges, textures, and pass outputs.
3. Compile Vulkan-targeted GLSL to SPIR-V offline with `glslangValidator --target-env vulkan1.2`; validate with `spirv-val --target-env vulkan1.2`.
4. Reflect every module and compare descriptor set, binding, storage class, member offset, matrix stride, array stride, and push-constant size with generated Java layout metadata. Fail the build on drift.
5. Let MoltenVK/SPIRV-Cross translate the validated SPIR-V on macOS. Keep direct MSL limited to the Task 3 control unless a measured incompatibility forces an explicitly reviewed exception.
6. Check generated SPIR-V into the release artifact only if maintainers accept generated shaders; never download or compile shaders from a vendor at runtime.

Avoid `#ifdef` branches keyed only to API names. Prefer feature/capability names and record any MoltenVK fallback. [`MoltenVK` documents SPIR-V conversion and implementation limitations](https://github.com/KhronosGroup/MoltenVK/blob/v1.4.2/Docs/MoltenVK_Runtime_UserGuide.md); support must be proven shader-by-shader.

## Migration order

| Slice | Shader/resource scope | Required proof before advancing |
| --- | --- | --- |
| Opaque static zone | vertex transform, packed geometry, depth, flat color, UI composite | exact prepared-buffer hashes; GPU readback; depth/viewport/Retina tests |
| Textured materials | texture arrays/atlases, samplers, UV animation, color space | material-ID parity; representative texture golden set; gamma/blend checks |
| Alpha and dynamic models | ordering, alpha blend/cutout, model updates | overlapping-transparent fixtures; movement and animation captures |
| Shadows | shadow targets, sampling, filtering, expanded drawing | all shadow qualities and light/camera edge cases; depth-bias artifacts absent |
| Tiled/dynamic lighting | storage buffers/images, light bins, compute where used | zero/one/max lights, tile-boundary cases, overflow handling, capability audit |
| Water and post-processing | intermediate targets, bloom/fog/color transforms | underwater/surface transitions, each effect toggle, HDR/color-space checks |

## Correctness oracle

OpenGL is a behavioral reference, not a requirement for byte-identical rasterization. Each fixture stores:

- prepared CPU buffer hashes and semantic draw counts, which must match exactly;
- an OpenGL and Vulkan GPU readback captured at the same resolution, camera, tick, settings, and asset revision;
- per-channel absolute-error image, maximum error, mean error, and percentage of pixels outside the reviewer-approved tolerance;
- a mask and written rationale for accepted API-dependent rasterization differences;
- validation messages, backend counters, and app-owned resource counts.

Approve numeric image tolerances before the first real-scene comparison. Do not raise them after seeing a failure without an independent rendering review. UI pixels and the synthetic Task 3/4 controls remain exact where no filtering or color-space conversion is expected.

### Task 1 BASE offscreen oracle

The fixed Task 1 BASE slice uses a bounded 64x64 oracle: exact mapped-uploader vertex and face hashes, exactly 18 opaque vertices, an all-pixel scene mask covering x/y 6 through 57, exact black outside that mask and exact alpha, two interior scene RGB samples with absolute per-channel tolerance 2, and an all-pixel exact-UI-over-scene composition check. Opaque and transparent UI quadrants are exact; the half-alpha premultiplied quadrant permits one integer channel value for UNORM rounding. The canonical descriptor is pinned by SHA-256 `4577074de8e78763bc9a4c63aedcf179d68746ba6958d82dde2cc2539e51f2df` in the test fixture.

This oracle detects packing, camera, coverage, color, UI blending, and readback regressions in the deterministic Vulkan slice. The paired BASE geometry and constant clip depth do not independently prove front-face selection or reverse-Z near/far ordering; dedicated control fixtures remain required for those states. It is not an OpenGL equivalence tolerance and cannot approve broader scene correctness. The first OpenGL-versus-Vulkan full-image comparison still requires separately registered maximum/mean/outlier thresholds and an independently reviewed edge mask.

## Shader build gates

- Compile every enumerated variant, including macOS fallback branches, on Java 11 CI.
- Run `spirv-val`; reject warnings as well as errors for release inputs.
- Reflect layouts and compare them with Java packing tests on little-endian arm64 and x86_64 hosts.
- Record `glslangValidator`, SPIR-V target, MoltenVK, Vulkan loader, LWJGL, and source commits in the artifact manifest.
- Reject runtime shader compiler/download dependencies unless separately approved and packaged.
- Preserve original GLSL and OpenGL compile tests until OpenGL retirement is separately decided.

## Correctness matrix

| Area | Cases | Evidence |
| --- | --- | --- |
| Client lifecycle | login screen, login, logout, world hop, game-state loss, plugin toggle, shutdown | no crash; expected callback order; no stale resources |
| Window lifecycle | resize, zero extent/minimize, restore, fullscreen enter/exit, Retina scale, display migration | readback at effective extent; no stretched/stale UI; validation clean |
| Scene ownership | base scene, instance/POH, zone load/unload, teleport, extended loading | exact prepared counts/ranges; balanced zone create/destroy |
| Geometry | terrain, static objects, dynamic models, animation, model sorting | approved golden comparisons and draw-order fixtures |
| Materials | untextured, textured, transparent/cutout, emissive, parallax | material-ID/layout parity and feature-toggle goldens |
| Lighting | none, one, dense/max dynamic lights, tiled boundaries | buffer-bound checks, overflow fixtures, approved images |
| Passes | opaque, alpha, shadows, water, UI, every post-process toggle | per-pass capture where possible; final golden/readback |
| Failure | unsupported feature, shader rejection, device/surface loss, acquire/submit/present failure | truthful error, deterministic cleanup, controlled OpenGL fallback |
| Platform | macOS arm64/MoltenVK; accepted Intel macOS; Windows and Linux OpenGL; later native Vulkan WSI | platform CI plus manual hardware evidence; default stays OpenGL |

## Acceptance rule

A shader slice advances only when every applicable matrix row has exact semantic-buffer parity, approved image results, zero Vulkan validation warnings/errors, no app-owned resource imbalance, and independent renderer-owner review. Missing hardware or lifecycle evidence is `NOT RUN`, never a pass.
