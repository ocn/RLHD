# Modern renderer planning package

**Current recommendation:** hold the production port. Keep OpenGL as the default and fallback on every operating system. After all entry gates pass, test Vulkan/MoltenVK only as the next real-scene hypothesis; keep direct Metal as a presentation control.

Review in this order:

1. [ADR-0001](ADR-0001-modern-renderer-direction.md) — decision, option comparison, evidence limits, and prior-art corrections.
2. [Source inventory](source-inventory.md) and [provenance](provenance.md) — pinned inputs and claim boundaries.
3. [Dependency/ownership map](dependency-ownership-map.md) and [terminology](terminology.md) — seams and stable language.
4. [Go/no-go gates](go-no-go-gates.md) and [maintainer questions](maintainer-questions.md) — blockers before real renderer work.
5. [Vertical-slice implementation plan](implementation-plan.md) — bounded opaque-zone experiment, not a wholesale rewrite.
6. [Shader/correctness plan](shader-correctness-plan.md) — SPIR-V workflow and test matrix.
7. [Packaging/release plan](packaging-release-plan.md) — native policy, CI, artifacts, rollout, and rollback.
8. [Benchmark protocol](benchmark-protocol.md) — identical-scene, lifecycle, and long-duration measurements.
9. [Toolchain preflight](toolchain-preflight.md) and [binding compatibility](binding-compatibility.md) — reproducible local prerequisites.
10. [Panic investigation log](panic-investigation-log.md) — append-only incidents, durable pre-panic checkpoints, and the staged live-test risk ladder.
11. [Vulkan vertical-slice results](vertical-slice-results.md) — bounded surface-free GPU evidence and explicit unverified claims.

Task 2/3/4 controls live under `spikes/`. Their results are bounded technical evidence; they do not imply Jagex, RuneLite, Plugin Hub, or maintainer acceptance.
