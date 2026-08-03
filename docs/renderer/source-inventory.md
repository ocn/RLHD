# Pinned source inventory

All investigations and comparisons use the following immutable commit IDs. Refreshing a dependency requires an explicit inventory update and a separate review.

| Source | Pinned commit | Purpose |
| --- | --- | --- |
| [117HD/RLHD](https://github.com/117HD/RLHD/tree/0978ec2ec1745d6139b895acc4445789f45afc54) | `0978ec2ec1745d6139b895acc4445789f45afc54` | Fork baseline; this worktree is at this commit before Task 1. |
| [RuneLite](https://github.com/runelite/runelite/tree/348035815f2caeaba26a3fdb05309e0f0c204562) | `348035815f2caeaba26a3fdb05309e0f0c204562` | Client/API compatibility reference. |
| [Plugin Hub](https://github.com/runelite/plugin-hub/tree/deec344b192762f822e5f6cdda19b8b4a6e7ecbd) | `deec344b192762f822e5f6cdda19b8b4a6e7ecbd` | Distribution-policy reference. |
| [runelite/rlawt](https://github.com/runelite/rlawt/tree/ecb6599caaaa12b1ddfe4d955cceb2e69fb06702) | `ecb6599caaaa12b1ddfe4d955cceb2e69fb06702` | AWT integration reference; repository owner and commit were verified with `git ls-remote` on 2026-08-02. |
| [LWJGLX/lwjgl3-awt](https://github.com/LWJGLX/lwjgl3-awt/tree/7c3e75295da6a0af3b96a1dfa9decef331689e60) | `7c3e75295da6a0af3b96a1dfa9decef331689e60` | AWT/native-surface reference. |
| [KhronosGroup/MoltenVK](https://github.com/KhronosGroup/MoltenVK/tree/df94d24c1c515eb9c6f89f82a965201e2fc43254) | `df94d24c1c515eb9c6f89f82a965201e2fc43254` | macOS Vulkan portability reference. |
| [dennisdevulder/gpu-vulkan](https://github.com/dennisdevulder/gpu-vulkan/tree/12f8db08c8bac0d0180f96738b1fd9b075215496) | `12f8db08c8bac0d0180f96738b1fd9b075215496` | Prior Vulkan experiment reference. |

The `rlawt` primary repository is `runelite/rlawt`; its recorded commit remains the verified `master` tip used by this evidence set. No rlawt code is copied or added as a dependency here.
