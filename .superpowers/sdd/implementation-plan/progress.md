# SDD ledger — plan: docs/renderer/implementation-plan.md

## Task 1 — Backend-neutral prepared geometry seam

- Status: in progress
- Authorization: user explicitly requested initiation of the first vertical slice.
- Scope override: Task 0 remains unresolved for native/headful work; this task is limited to CPU-side seam characterization and preserves existing OpenGL behavior.
- Safety: do not run Metal, Vulkan, fullscreen, or other headful presentation tests on this host.

### Review round 1

- Base: `7b2854c49b3c23980268fade96fdac16e5a5029a`
- Reviewed head: `2f07b1d06c4c79eb73f04bc1aa5ab7633a4d6842`
- Verdict: `CHANGES_REQUIRED`
- Important findings: invalid zone-wide material ID on draw ranges; coupled legacy-parity evidence; mandatory two-generation heap/copy overhead on normal OpenGL uploads.
- Required direction: preserve direct mapped OpenGL generation, make preparation explicit, publish the real per-face material association, and verify against a golden captured from the base uploader.

### Review round 2

- Reviewed head: `9e7475fe03de78107881442603af6ac99a55e268`
- Verdict: `CHANGES_REQUIRED`
- Prior three findings: resolved.
- Remaining Important finding: `VertexWriteCache` retains mutable aliases to no-copy prepared buffers, violating ownership and retaining full-zone arrays in the uploader pool.
- Required direction: explicitly detach all writer outputs after flushing and before publishing; regression-test alias removal and unchanged hashes.

### Final Task 1 gate

- Final head: `7818bd603fc8d456b64e3eac29e92e89bf351de2`
- Independent verdict: `APPROVED`
- Controller focused verification: 7 tests, 0 failures, 0 errors, 0 skips; 5/5 Gradle tasks executed.
- Controller safe matrix: 25/25 tasks executed; native lifecycle completed 100 cycles; offline Metal/Vulkan control and shader validation passed.
- Diff/seam checks: clean; worktree clean; no backend-specific handles, enums, descriptors, synchronization, or native references in the seam types.
- Status: Task 1 complete. Task 2 remains blocked by native/headful entry gates and host display-panic safety hold.

## Task 2 — One opaque static zone through Vulkan

- Status: Task 2A independently approved and controller-verified; Task 2B gated.
- Authorization: user explicitly requested proceeding to Task 2.
- Scope split: Task 2A may implement API-neutral frame data, prepared-zone Vulkan upload/resource ownership, real opaque shaders, offline compilation/reflection, and deterministic non-presenting tests. Task 2B owns live surface/swapchain/presentation/readback and validation-layer acceptance.
- Safety: no window, CAMetalLayer, Vulkan surface, swapchain, drawable, fullscreen transition, or presentation call may execute on this host.
- Acceptance boundary: Task 2 cannot be marked complete until Task 2B's live golden/readback and balanced lifecycle checks run safely; Task 2A may be independently reviewed and committed as partial progress.
- Base for Task 2A: `7818bd603fc8d456b64e3eac29e92e89bf351de2`.
- Task 2A implementation commit: `865fc6ff366dd2e85f3e40e4a70a0fb7938649ec`.
- Task 2A review-round-1 fix commit: `e86e9ce7fded0ba977411d1908b363f2af31a1c9`.
- Audit result: Task 2A contract is byte-exact and Vulkan-call-free; Vulkan-specific code/resources use an isolated source set and the normal JAR/runtime remain OpenGL-only.
- Deferred `NOT RUN`: physical-device format support, MoltenVK pipeline compilation, clipping/interpolation/culling/depth parity, BGRA/gamma/Retina/UI parity, validation-layer cleanliness, GPU readback, acquire/submit/present balance, and real GPU resource retirement.

### Task 2A review round 1

- Reviewed head: `e9772510`.
- Verdict: `CHANGES_REQUIRED`.
- Important findings: public frame data omits `sceneBase` required by the 72-byte push range; reflection assertions do not enforce exact interfaces or absence of extras; negative-viewport projected winding is untested.
- Required hardening: make zero-extent no-work evidence explicit and reject every production LWJGL/Vulkan/native or opaque-slice dependency/resource leak.

### Task 2A review round 1 fixes

- Fix head: `e86e9ce7fded0ba977411d1908b363f2af31a1c9`.
- Public frame data now supplies immutable scene-base X/Z values for the exact 72-byte shader push contract.
- Exact Gson-parsed validation covers every reflected interface and absence of unexpected descriptors, push blocks, specialization constants, inputs, and outputs; mutation tests prove drift rejection.
- Pure CPU negative-viewport projection pins clockwise framebuffer winding/back culling; zero extent leaves allocation, close, and work counters unchanged.
- Production isolation rejects all LWJGL/Vulkan/MoltenVK/native dependencies and slice/native JAR entries; Gson 2.14 is exact and isolated from production.
- Safe verification: 5 public contract tests, 13 isolated tests, 22-task offline check, and 12-test Task 1 BASE/public regression all passed.
- Status: fixes complete; independent rereview pending. Task 2B remains `NOT RUN`.

### Task 2A final rereview

- Reviewed head: `6c9e7604`.
- Verdict: `APPROVED`; no Critical, Important, or Minor findings.
- Confirmed: immutable scene-base/frame contract, exact parsed shader reflection with mutation rejection, negative-viewport winding/culling, zero-extent no-work evidence, production dependency/resource isolation, and explicit Task 2B `NOT RUN` boundary.

### Task 2A controller gate

- Approved code head: `6c9e7604`.
- Fresh command: `./gradlew --no-daemon --console=plain --rerun-tasks vulkanOpaqueSliceCheck test --tests '*PreparedZoneGeometryTest' --tests 'rs117.hd.renderer.PreparedFrameTest' --tests 'rs117.hd.renderer.RendererBackendContractTest'`.
- Result: `BUILD SUCCESSFUL`; 25/25 tasks executed, all four shaders freshly compiled/reflected/validated, exact reflection and production isolation passed, and focused tests passed.
- Safety: no integration, loader, device, surface, swapchain, window, fullscreen, submit, or presentation task ran.
- Boundary: Task 2A is complete as an offline partial slice. Full Task 2 remains incomplete while Task 2B is `NOT RUN`.
