# Modern renderer planning package

**Current recommendation:** keep OpenGL as the released default/fallback on every operating system. Stop the independent RLHD Vulkan backend and add HD capabilities incrementally to a maintainer-approved `gpu-vulkan` branch. Keep direct Metal and both macOS presentation routes as bounded controls until native packaging and the panic investigation are resolved.

Review in this order:

1. [ADR-0002](ADR-0002-gpu-vulkan-foundation.md) and [gpu-vulkan PR #20 assessment](gpu-vulkan-pr20-assessment.md) — maintainer-directed foundation pivot and current evidence limits.
2. [ADR-0001](ADR-0001-modern-renderer-direction.md) — retained option comparison, control evidence, and prior-art corrections.
3. [Source inventory](source-inventory.md) and [provenance](provenance.md) — pinned inputs and claim boundaries.
4. [Dependency/ownership map](dependency-ownership-map.md) and [terminology](terminology.md) — seams and stable language.
5. [Go/no-go gates](go-no-go-gates.md) and [maintainer questions](maintainer-questions.md) — blockers before real renderer work.
6. [gpu-vulkan feature-port plan](gpu-vulkan-feature-port-plan.md) — replacement implementation order; the [original vertical-slice plan](implementation-plan.md) remains historical evidence.
7. [Shader/correctness plan](shader-correctness-plan.md) — SPIR-V workflow and test matrix.
8. [Packaging/release plan](packaging-release-plan.md) — native policy, CI, artifacts, rollout, and rollback.
9. [Benchmark protocol](benchmark-protocol.md) — identical-scene, lifecycle, and long-duration measurements.
10. [Toolchain preflight](toolchain-preflight.md) and [binding compatibility](binding-compatibility.md) — reproducible local prerequisites.
11. [Panic investigation log](panic-investigation-log.md) — append-only incidents, durable pre-panic checkpoints, and the staged live-test risk ladder.
12. [Vulkan vertical-slice results](vertical-slice-results.md) — bounded surface-free GPU evidence and explicit unverified claims.

Task 2/3/4 controls live under `spikes/`. Their results are bounded technical evidence; they do not imply Jagex, RuneLite, Plugin Hub, or maintainer acceptance.
