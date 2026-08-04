# Maintainer questions before a real-scene slice

Record a named owner and written answer for each question. Silence or ambiguity is a no-go for upstream-oriented work.

## 117HD

1. Who will review and maintain GPU/Vulkan resource lifetime, shaders, performance, and the macOS portability path?
2. Is an opt-in opaque-zone experiment acceptable while `ZoneRenderer` and `LegacyRenderer` remain unchanged defaults on every OS?
3. Is `SceneUploader` the acceptable boundary for immutable CPU `PreparedZoneGeometry`, or must that seam live elsewhere?
4. Which feature order, scene fixtures, hardware classes, numeric benchmark thresholds, and OpenGL fallback behaviors define acceptance?
5. **Answered directionally:** the relayed 117HD maintainer recommendation is to use [`gpu-vulkan`](https://github.com/dennisdevulder/gpu-vulkan/tree/12f8db08c8bac0d0180f96738b1fd9b075215496) and add HD features incrementally, not build another platform backend in RLHD. Still required: confirm whether contributions target standalone `gpu-vulkan` or its parent `runelite-vkport`, and name the reviewer.

## gpu-vulkan owner

6. Should HD capabilities deepen `BaseRenderer`, or should the backend add an exclusive primary-scene renderer slot? Current additive extensions cannot replace the always-registered base renderer.
7. Which exact commit, macOS build, hardware, JDK, Vulkan loader, MoltenVK version, present path, FPS mode, validation settings, and display topology produced the relayed approximately 200-hour result?
8. Did that soak use PR #20 custom Metal presentation, `vkgpu.disableCustomPresent=true`, or both?
9. Is local/standalone macOS distribution the accepted near-term target while JNI remains forbidden in Plugin Hub builds?
10. Will the owner review each HD material/pass slice, including generated SPIR-V, resource lifetime, and cross-platform behavior?

## RuneLite and rlawt

11. Should a backend-neutral AWT surface API live in [rlawt](https://github.com/runelite/rlawt/tree/ecb6599caaaa12b1ddfe4d955cceb2e69fb06702), RuneLite core, or nowhere upstream?
12. Who owns per-instance `CAMetalLayer` attachment, resize/Retina/display migration, drawable loss, teardown, and the Task 2 terminal-sentinel limitation?
13. Will RuneLite accept MoltenVK, a JAWT/JNI bridge, generated SPIR-V, or any platform-specific artifact? If yes, what review, signing, notarization, SBOM, license, update, and vulnerability-response process applies?
14. Does the current [Plugin Hub native/JNI policy](https://github.com/runelite/runelite/wiki/Rejected-or-Rolled-Back-Features) admit any exception, or is the only acceptable route RuneLite/rlawt core or a local/standalone fork?
15. Who provides qualified Vulkan review, given the review-ownership objection recorded on the closed [`gpu-vulkan` Plugin Hub submission](https://github.com/runelite/plugin-hub/pull/12885)?

## Cross-platform support

16. Which macOS versions and architectures remain supported, including Intel; which Windows and Linux AWT/window-system combinations must stay green?
17. Must each HD feature land simultaneously on Windows/Linux native Vulkan and macOS MoltenVK, or may an experimental slice trail on one platform?
18. Which real-hardware CI runners and maintainers own macOS arm64/Intel, Windows, and Linux GPU validation, lifecycle, and artifact-signing failures?
19. May the normal plugin jar contain dormant HD Java classes/SPIR-V, or must HD artifacts remain separately scoped until preview approval?

## Policy and release

20. Is an authorized local fork with pinned native libraries compliant with the current Jagex client policy? Record this separately from RuneLite/Plugin Hub acceptance.
21. Who can disable or roll back a preview, how quickly, and which crash, validation, resource-growth, correctness, or performance signals trigger it?
22. Who supports the backend and native dependencies after initial authors leave, including security updates and future LWJGL/MoltenVK changes?

The two controls prove bounded technical facts on one Apple Silicon system. They do not answer any question above.
