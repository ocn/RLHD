# ADR-0002: use gpu-vulkan as the Vulkan foundation

**Status:** accepted for planning and local-fork experiments; production distribution remains unapproved.

**Supersedes:** ADR-0001's proposed independent RLHD Vulkan platform/backend implementation.
**Does not supersede:** OpenGL default/fallback, feature-by-feature gates, benchmark requirements, or policy separation.

## Context

The 117HD maintainer discussion supplied a more mature prior implementation than the architecture inventory had established: [`gpu-vulkan`](https://github.com/dennisdevulder/gpu-vulkan/tree/12f8db08c8bac0d0180f96738b1fd9b075215496) is a Vulkan port of RuneLite's GPU plugin, and [PR #20](https://github.com/dennisdevulder/gpu-vulkan/pull/20) preserves its macOS JAWT/CAMetalLayer route. The maintainer recommends adding HD capabilities incrementally to that foundation.

Source inspection confirms that `gpu-vulkan` already owns `DrawCallbacks`, cross-platform surfaces, Vulkan lifecycle, synchronization, render targets, UI composition, screenshots, resize/rebuild behavior, and presentation. Rebuilding those modules inside RLHD would duplicate the highest-risk work and create two renderers competing for the same callback slot.

## Decision

Stop production work on an RLHD-local Vulkan backend. Do not implement the remaining `VulkanSurfaceProvider`, swapchain, presentation, or `HdPlugin` Vulkan-selection tasks from the original vertical-slice plan.

Use a maintainer-approved `gpu-vulkan` repository and branch as the Vulkan foundation. Add one HD semantic capability per reviewed slice. `gpu-vulkan` owns the platform/frame lifecycle; an HD primary renderer owns HD scene, material, and pass semantics. OpenGL 117HD remains unchanged and available on every supported operating system.

Before the first HD slice, agree with the `gpu-vulkan` owner whether the primary-scene seam is:

1. an exclusive renderer slot that replaces `BaseRenderer`; or
2. incremental feature work inside `BaseRenderer`.

Do not register an HD renderer additively under the current extension behavior because `BaseRenderer` is always registered and the scene would be submitted twice.

## Retained work

- `PreparedZoneGeometry` and its BASE mapped-uploader hashes remain the reference for 117HD geometry/material semantics.
- The Task 1 64x64 offscreen oracle remains a conformance fixture that can be migrated into the selected `gpu-vulkan` test surface.
- Shader correctness, benchmark, rollback, and panic-investigation documents remain mandatory.
- The isolated RLHD Vulkan implementation remains a learning and packing/readback proof only; it is not promoted into production.

## macOS constraint

PR #20 is a useful implementation, not an accepted release path. It is draft, has no public CI/review record, commits a JNI/JAWT dylib, and is excluded from the Plugin Hub submission. Its custom direct-Metal present path and standard `vkQueuePresentKHR` escape hatch remain competing presentation hypotheses on the M3 Ultra until the panic risk ladder distinguishes them. The relayed 200-hour soak is maintainer testimony pending commit-bound environment and result details. See [the source assessment](gpu-vulkan-pr20-assessment.md).

## Cross-platform consequence

HD feature code must use the backend's cross-platform rendering interface and pass the same semantic/correctness tests on Windows/Linux native Vulkan and macOS MoltenVK. No HD feature may depend directly on Cocoa, JAWT, `CAMetalLayer`, MoltenVK handles, Win32, or X11. Platform code stays in `gpu-vulkan`.

## Rejected alternatives

- **Continue the RLHD-local Vulkan backend:** rejected because it duplicates proven platform/frame modules and conflicts with maintainer direction.
- **Run unchanged RLHD and gpu-vulkan simultaneously:** rejected because both own `DrawCallbacks`.
- **Port only to direct Metal:** rejected because it loses Windows/Linux reuse and duplicates shaders/resources.
- **Transplant PR #20 blindly:** rejected because its native packaging is unapproved and its custom presentation route intersects the existing macOS panic investigation.

## Revisit

Revisit only if `gpu-vulkan` ownership refuses the required HD renderer seam, the selected repository is abandoned, or measured constraints make the shared backend incapable of an identified 117HD feature. Such a finding must name the missing capability and include a minimal reproducer; general implementation difficulty is not sufficient.
