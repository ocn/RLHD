# Go/no-go gates and success criteria

Gate state is `GO`, `NO-GO`, or `NOT RUN`. Missing evidence is never `GO`.

## Entry gates for a real gpu-vulkan HD slice

| Gate | Evidence required | Current state |
| --- | --- | --- |
| Foundation decision | written decision to collaborate with `gpu-vulkan` rather than duplicate its platform/frame lifecycle | `GO` directionally; ADR-0002 records the pivot |
| Contribution repository | `gpu-vulkan` owner names standalone or parent `runelite-vkport` repository and branch | `NO-GO` until answered |
| Primary scene ownership | owner selects `BaseRenderer` deepening or an exclusive primary-renderer slot; additive double-rendering is impossible | `NO-GO` until answered |
| Review ownership | named `gpu-vulkan` and HD renderer reviewers accept per-slice review | `NO-GO` until answered |
| macOS baseline provenance | reproduce the exact PR #20 commit/configuration or obtain the complete environment behind the relayed 200-hour result | `NOT RUN`; maintainer testimony is not a reproducible baseline |
| Presentation isolation | compare standard `vkQueuePresentKHR` and custom Metal presentation as separate panic-ledger rungs | `NOT RUN` |
| Distribution | written local/standalone or upstream route for JNI, MoltenVK, signatures, SBOM, licenses, and updates | `NO-GO` until answered; Plugin Hub forbids JNI |
| Cross-platform baseline | pinned Windows/Linux native Vulkan and macOS MoltenVK builds pass before HD changes | `NOT RUN` |
| HD shader/fixture feasibility | migrate the fixed BASE fixture/oracle through backend-owned abstractions with zero validation warnings/errors | `NOT RUN`; the RLHD-local oracle is `GO` evidence only |
| Jagex-policy check | fresh policy review recorded separately from technical/upstream acceptance | `NOT RUN` |

Do not begin Task 1 in the [gpu-vulkan feature-port plan](gpu-vulkan-feature-port-plan.md) while Task 0's repository, seam, or review-owner gate is `NO-GO` or `NOT RUN`. The completed RLHD surface-free experiments remain evidence and do not override these gates.

## Vertical-slice success criteria

The slice succeeds only when all applicable items pass:

- one fixed opaque static top-level zone produces identical prepared vertex/UV/normal buffer hashes, draw ranges, semantic material IDs, and zone ownership events for OpenGL and Vulkan;
- Vulkan readback meets the pre-approved image tolerance and the UI control remains exact;
- login, world hop, resize, zero extent, fullscreen, Retina scaling, display migration, plugin toggle, startup failure, and shutdown complete without crash, stale image, or leaked app-owned resource;
- validation warning count, validation error count, timestamp-query error count, and final app-owned native-object count are all exactly zero;
- acquire, acquire-complete, submit, complete, and queued-presentation counters balance according to the reviewed lifecycle contract;
- normal macOS, Windows, and Linux builds still select OpenGL, pass existing tests, and do not resolve Vulkan, MoltenVK, or macOS JNI classes;
- raw benchmark and lifecycle artifacts identify exact commits, tool/runtime versions, settings, scene route, display, and hardware;
- a named renderer owner independently approves correctness, fallback, ownership, and raw evidence.

The slice does not pass merely because it renders a triangle, synthetic UI, or incomplete scene faster than OpenGL.

## Proposed resource-stability threshold

Approve or replace this threshold before collecting data:

- after a 10-minute warm-up, run at least 30 minutes of lifecycle cycling;
- over the final 20 minutes, fitted slopes for RuneLite RSS, WindowServer footprint, and available GPU allocation telemetry must each be at most 1 MiB/minute;
- the median of the last five post-cycle peaks must be within 5% of the median of the first five post-warm-up peaks;
- Vulkan app-owned object counters return to zero after each terminal close;
- any nonzero MoltenVK teardown diagnostic is reported verbatim and must be stable across repeated same-process instances.

Crossing a threshold is a `NO-GO`; meeting it is only evidence of a bounded run, not proof of lifetime leak freedom.

## Performance decision gate

Performance is recorded during the opaque slice but is not used to choose a production backend because feature sets differ. The following provisional feature-parity thresholds must be approved or replaced by maintainers before data collection:

- no scene has more than a 5% p95 frame-time regression from OpenGL; and
- in at least two demanding scenes, Vulkan provides either at least a 15% p95 frame-time improvement or at least a 20% reduction in combined RuneLite plus WindowServer CPU.

Before a feature-parity preview, maintainers must also preregister limits for median/p99, 1% low, GPU cost, and platform variance using [benchmark-protocol.md](benchmark-protocol.md). A production recommendation requires:

- five valid 120-second runs per backend/scene after warm-up;
- no agreed stability or tail-latency regression on any supported hardware class;
- at least one reproducible benefit large enough that maintainers accept the native/runtime/shader support burden;
- equivalent enabled features and settings.

No numeric threshold may be selected after viewing results.

## Long-soak gate

- Run an 8-hour milestone and a 24-hour final same-scene soak at feature parity.
- During the final hour, p95 frame time and steady-state RuneLite/WindowServer footprint must each remain within 10% of hour one.
- Native/GPU/app-owned resource telemetry must show no monotonic growth; app-owned counts must still balance at shutdown.
- Repeat the relevant restart, logout, and reboot boundary when degradation appears.

Passing the opaque incomplete slice or a 30-minute control does not satisfy this gate and cannot support a production performance/stability decision.

## Immediate no-go conditions

- no qualified reviewer, native owner, distribution route, or rollback owner;
- monotonic retained-resource growth or an unresolved validation/lifetime defect;
- required real shaders need an unacceptable MoltenVK fallback or parallel MSL corpus;
- OpenGL fallback or non-mac build/artifact isolation regresses;
- Vulkan surface/backend ownership cannot be separated from 117HD's `DrawCallbacks` ownership;
- policy or maintainer response rejects the proposed native/backend scope.

A `NO-GO` keeps OpenGL as the supported renderer and may motivate a narrower OpenGL optimization or diagnostic experiment. It does not authorize changing scope.
