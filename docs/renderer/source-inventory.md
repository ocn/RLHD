# Pinned source inventory

All investigations and comparisons use the following immutable commit IDs. Refreshing a dependency requires an explicit inventory update and a separate review.

| Source | Pinned commit | Purpose |
| --- | --- | --- |
| [117HD/RLHD](https://github.com/117HD/RLHD/tree/0978ec2ec1745d6139b895acc4445789f45afc54) | `0978ec2ec1745d6139b895acc4445789f45afc54` | Fork baseline; this worktree is at this commit before Task 1. |
| [RuneLite](https://github.com/runelite/runelite/tree/348035815f2caeaba26a3fdb05309e0f0c204562) | `348035815f2caeaba26a3fdb05309e0f0c204562` | Client/API compatibility reference. |
| [Plugin Hub](https://github.com/runelite/plugin-hub/tree/7a8fbda730d52b851cbe70dc77120779130c252f) | `7a8fbda730d52b851cbe70dc77120779130c252f` | Master verified 2026-08-03 07:34:55 UTC; repository/distribution reference. Policy is maintained in the RuneLite wiki. |
| [runelite/rlawt](https://github.com/runelite/rlawt/tree/ecb6599caaaa12b1ddfe4d955cceb2e69fb06702) | `ecb6599caaaa12b1ddfe4d955cceb2e69fb06702` | AWT integration reference; repository owner and commit were verified with `git ls-remote` on 2026-08-02. |
| [LWJGLX/lwjgl3-awt](https://github.com/LWJGLX/lwjgl3-awt/tree/7c3e75295da6a0af3b96a1dfa9decef331689e60) | `7c3e75295da6a0af3b96a1dfa9decef331689e60` | AWT/native-surface reference. |
| [KhronosGroup/MoltenVK](https://github.com/KhronosGroup/MoltenVK/tree/df94d24c1c515eb9c6f89f82a965201e2fc43254) | `df94d24c1c515eb9c6f89f82a965201e2fc43254` | macOS Vulkan portability reference. |
| [dennisdevulder/gpu-vulkan main](https://github.com/dennisdevulder/gpu-vulkan/tree/12f8db08c8bac0d0180f96738b1fd9b075215496) | `12f8db08c8bac0d0180f96738b1fd9b075215496` | Selected Vulkan platform/frame foundation hypothesis after maintainer consultation. |
| [gpu-vulkan macOS preservation PR #20](https://github.com/dennisdevulder/gpu-vulkan/tree/3b5cbe5d42c26915c6d34d1df6613db74dd8c48b) | `3b5cbe5d42c26915c6d34d1df6613db74dd8c48b` | Draft JAWT/CAMetalLayer and custom Metal presentation reference; not Plugin Hub accepted. |
| [Road to Vulkan](https://github.com/dennisdevulder/road-to-vulkan/tree/083dd91c4506313d11066f6c6f8eee236c4ac92e) | `083dd91c4506313d11066f6c6f8eee236c4ac92e`; deployed HTML SHA-256 `3d9c7d8fb615d5caeb7871833a926a9c1a5c6db0772389a6db29d9a0a00c923c` | Maintainer-authored learning and source-navigation guide. Claims are rechecked against pinned renderer code and primary API documentation. |
| [Adoptium JDK 21u](https://github.com/adoptium/jdk21u/tree/04806bcb1d50b35efc1c22a4d3b082c9a9a47563) | `04806bcb1d50b35efc1c22a4d3b082c9a9a47563` | Exact source lineage recorded by the installed Temurin 21.0.12+8 macOS arm64 JDK; also the peeled `jdk-21.0.12+8_adopt` tag. |
| [Adoptium Temurin build](https://github.com/adoptium/temurin-build/tree/e6ba7dec3d07654074559310376a3ae89da5f4ac) | `e6ba7dec3d07654074559310376a3ae89da5f4ac` | Exact build lineage in the installed Temurin 21.0.12+8 arm64 JDK `release` metadata used for Task 3 Java/JNI builds. |
| [Apple Xcode 26.6 release notes](https://developer.apple.com/documentation/xcode-release-notes/xcode-26_6-release-notes) | Xcode build `17F113`; macOS SDK 26.5 build `25F70` | Installed Objective-C/Metal headers and arm64 clang toolchain used for the direct-Metal control. The build and SDK identifiers pin the local Apple input because Apple does not publish these SDK headers as a git repository. |

The `rlawt` primary repository is `runelite/rlawt`; its recorded commit remains the verified `master` tip used by this evidence set. No rlawt code is copied or added as a dependency here.

The initial 2026-08-02 Plugin Hub snapshot was `deec344b192762f822e5f6cdda19b8b4a6e7ecbd`. It is retained here only as provenance for early inspection; decisions use the refreshed commit above and the current [RuneLite rejected-features policy](https://github.com/runelite/runelite/wiki/Rejected-or-Rolled-Back-Features).
