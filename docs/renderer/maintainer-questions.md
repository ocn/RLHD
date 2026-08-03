# Maintainer questions before implementation or submission

1. Which maintainer owns review of a native GPU/Vulkan backend and its cross-platform support matrix?
2. Is a local fork with bundled native libraries acceptable under the applicable Jagex policy, separately from Plugin Hub distribution?
3. What artifact review, SBOM, license, signing, and update model would native libraries require?
4. Can the AWT/window lifecycle safely host a Vulkan surface on each supported OS without changing RuneLite ownership boundaries?
5. Who owns per-instance `CAMetalLayer` creation, resize, drawable loss, and teardown on macOS, given that it must be selected before rlawt creates the Canvas JAWT overlay?
6. Which OpenGL failure modes and user-visible configuration controls must remain guaranteed during an opt-in rollout?
7. Which scene routes, hardware classes, and regression signals constitute acceptance for a full-scene slice?

The historical Vulkan submission lacked GPU/Vulkan review ownership and was rejected. These questions must be answered by named owners; this document does not infer acceptance from technical feasibility.
