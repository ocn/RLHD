# Maintainer questions before a real-scene slice

Record a named owner and written answer for each question. Silence or ambiguity is a no-go for upstream-oriented work.

## 117HD

1. Who will review and maintain GPU/Vulkan resource lifetime, shaders, performance, and the macOS portability path?
2. Is an opt-in opaque-zone experiment acceptable while `ZoneRenderer` and `LegacyRenderer` remain unchanged defaults on every OS?
3. Is `SceneUploader` the acceptable boundary for immutable CPU `PreparedZoneGeometry`, or must that seam live elsewhere?
4. Which feature order, scene fixtures, hardware classes, numeric benchmark thresholds, and OpenGL fallback behaviors define acceptance?
5. Should the project collaborate with [`gpu-vulkan`](https://github.com/dennisdevulder/gpu-vulkan/tree/12f8db08c8bac0d0180f96738b1fd9b075215496), extract a bounded surface/backend module, or build independently? Who will contact its author and review license/API compatibility?

## RuneLite and rlawt

6. Should a backend-neutral AWT surface API live in [rlawt](https://github.com/runelite/rlawt/tree/ecb6599caaaa12b1ddfe4d955cceb2e69fb06702), RuneLite core, or nowhere upstream?
7. Who owns per-instance `CAMetalLayer` attachment, resize/Retina/display migration, drawable loss, teardown, and the Task 2 terminal-sentinel limitation?
8. How must backend selection occur before rlawt creates its OpenGL JAWT overlay, and what controlled restart/fallback behavior is acceptable after Vulkan startup failure?
9. Will RuneLite accept MoltenVK, a JAWT/JNI bridge, generated SPIR-V, or any platform-specific artifact? If yes, what review, signing, notarization, SBOM, license, update, and vulnerability-response process applies?
10. Does the current [Plugin Hub native/JNI policy](https://github.com/runelite/runelite/wiki/Rejected-or-Rolled-Back-Features) admit any exception, or is the only acceptable route RuneLite/rlawt core or a local fork?
11. Who provides qualified Vulkan review, given the review-ownership objection recorded on the closed [`gpu-vulkan` Plugin Hub submission](https://github.com/runelite/plugin-hub/pull/12885)?

## Cross-platform support

12. Which macOS versions and architectures remain supported, including Intel; which Windows and Linux AWT/window-system combinations must stay green?
13. Must a future Vulkan backend launch on Windows/Linux in the first preview, or may those users retain OpenGL while native Vulkan WSI is developed later?
14. Which real-hardware CI runners and maintainers own macOS arm64/Intel, Windows, and Linux GPU validation, lifecycle, and artifact-signing failures?
15. May the normal plugin jar contain dormant Vulkan Java classes/SPIR-V, or must modern-backend artifacts remain separately scoped until preview approval?

## Policy and release

16. Is an authorized local fork with pinned native libraries compliant with the current Jagex client policy? Record this separately from RuneLite/Plugin Hub acceptance.
17. Who can disable or roll back a preview, how quickly, and which crash, validation, resource-growth, correctness, or performance signals trigger it?
18. Who supports the backend and native dependencies after initial authors leave, including security updates and future LWJGL/MoltenVK changes?

The two controls prove bounded technical facts on one Apple Silicon system. They do not answer any question above.
