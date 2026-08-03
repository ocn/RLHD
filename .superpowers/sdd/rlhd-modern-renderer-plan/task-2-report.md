# Task 2 implementation report

Status: DONE_WITH_CONCERNS

Base: `6972d622d626b6c91fe82ab86bbacbef9256cb5d`

Worktree/branch: `/private/tmp/RLHD-modern-renderer` / `feature/modern-renderer`

## Files

- `build.gradle`: opt-in Java/test source sets, explicit native build and test tasks, spike JAR task, and arm64/full-JDK guards.
- `spikes/macos-surface/README.md`: scope, API contract, commands, packaging isolation, and headful-test requirements.
- `spikes/macos-surface/src/main/java/rs117/hd/spikes/macos/MacMetalSurface.java`: per-instance synchronized lifecycle wrapper.
- `spikes/macos-surface/src/main/java/rs117/hd/spikes/macos/SurfaceExtent.java`: immutable logical/pixel/scale/suspension snapshot.
- `spikes/macos-surface/src/main/java/rs117/hd/spikes/macos/NativeSurfaceAccess.java`: injectable native boundary for deterministic tests.
- `spikes/macos-surface/src/main/java/rs117/hd/spikes/macos/MacMetalSurfaceNative.java`: lazy macOS JNI loader; loads `jawt` before the helper.
- `spikes/macos-surface/src/main/native/mac_metal_surface.m`: serialized JAWT/AppKit/CAMetalLayer lifecycle.
- `spikes/macos-surface/src/main/native/surface_state.h` and `surface_state.c`: deterministic native lifecycle/extent state.
- `spikes/macos-surface/src/test/java/rs117/hd/spikes/macos/MacMetalSurfaceTest.java`: 100-cycle lifecycle, double-attach, closed-state, tree-lock, Canvas, and non-macOS guards.
- `spikes/macos-surface/src/test/java/rs117/hd/spikes/macos/SurfaceExtentTest.java`: scale/pixel and zero/invalid-extent behavior.
- `spikes/macos-surface/src/test/java/rs117/hd/spikes/macos/MacMetalSurfaceIntegrationTest.java`: opt-in 100-cycle real headful JAWT test.
- `spikes/macos-surface/src/test/native/surface_state_test.c`: 100-cycle native harness.
- `docs/renderer/provenance.md`: JAWT, OpenJDK compatibility, CAMetalLayer, and independent-implementation provenance.
- `docs/renderer/source-inventory.md`: corrected the rlawt primary repository to `runelite/rlawt` and verified the pinned commit on its `master` ref.

## Design and lifecycle

`MacMetalSurface` is `final`, per instance, and `AutoCloseable`. One lazily allocated native state remains with the Java instance across detach/reattach cycles and is freed by `close()`. There is no process-global layer, drawable, timer, cache, or renderer state. Public operations are synchronized, and the native state also has a per-instance mutex.

The state sequence is detached -> attached -> detached, repeatable until close. Double attach, detached resize/detach/layer access, every operation after close (including a second close), invalid dimensions/scale, a non-displayable Canvas, and calls made while holding the Canvas tree lock are rejected deterministically. Extent snapshots are immutable. Pixel dimensions are the exact double-precision product of logical dimensions and backing scale. Width or height zero is suspended.

Native loading occurs only when a default instance first attaches on macOS. `System.loadLibrary("jawt")` precedes the helper load. The non-macOS test forces `os.name=Linux` and confirms the platform rejection occurs before the native binding class initializes. Normal production source/test/JAR tasks do not compile, package, or load the spike.

Attach requests `JAWT_VERSION_1_4 | JAWT_MACOSX_USE_CALAYER`, obtains the Canvas drawing surface, locks it, gets `JAWT_DrawingSurfaceInfo`, and retains the macOS `JAWT_SurfaceLayers` object while locked. It then frees the drawing-surface info, unlocks, and frees the drawing surface before synchronously dispatching AppKit work to the main queue. Java rejects callers already holding the AWT tree lock. All JNI entries that touch Objective-C execute inside `@autoreleasepool`.

The AppKit block creates a `CAMetalLayer`, sets `MTLPixelFormatBGRA8Unorm`, disables implicit actions, starts at scale 1 and zero drawable size, and assigns it to `surfaceLayers.layer`. Resize sets logical bounds, `contentsScale`, and `drawableSize = logical extent * backing scale`. No code calls `nextDrawable`; zero extent therefore remains suspended without drawable acquisition.

Detach runs on the main queue, disables actions, replaces the CAMetalLayer with a fresh hidden zero-bounds/zero-frame plain CALayer sentinel through the public JAWT property, verifies getter identity/non-Metal type, removes that sentinel from its superlayer, releases the bridge's retained CAMetalLayer and surface-layers objects, and resets native extent state. OpenJDK's current `AWTSurfaceLayers.setLayer(nil)` releases the old layer but leaves its private ivar non-null; the first headful run exposed that as a second-attach use-after-free. The sentinel lets the public setter release the CAMetalLayer while storing a valid retained non-Metal pointer. A later attach replaces/releases the sentinel through the same public property. No private ivar name, offset, or raw memory mutation is used. Current OpenJDK `AWTSurfaceLayers.dealloc` releases `windowLayer` but not `layer`, so a terminal detach without later replacement leaks one tiny inert, hidden, unparented sentinel retain per Canvas even after peer destruction.

## Build and verification

All final Gradle work used the required native arm64 JDK:

```sh
JAVA_HOME=/private/tmp/rlhd-toolchains/temurin-21/Contents/Home ./gradlew --no-daemon --rerun-tasks macSurfaceSpikeCheck macSurfaceSpikeJar macSurfaceIntegrationTest test jar
```

Result: `BUILD SUCCESSFUL in 43s`, 14/14 tasks executed. The native harness printed `surface_state_test: 100 lifecycle cycles passed`. Java results were 9 deterministic spike tests, 1 headful integration test, and 20 root tests, all with zero skipped/failures/errors. The headful integration completed 100 attach/resize/zero/restore/detach cycles on one displayable Canvas.

The first native compile failed under `-Werror` because `CATransaction` needed its explicit header and the protocol type did not declare NSObject retain/release selectors; both were corrected with the header and typed casts. The first headful integration run then produced `SIGSEGV` in `AWTSurfaceLayers.setLayer` on the second attach. A diagnostic rerun with `NSZombieEnabled=YES` identified `-[CAMetalLayer removeFromSuperlayer]: message sent to deallocated instance`. The public-API sentinel compatibility fix above resolved it; the crash log was not retained in the repository.

The final sentinel path was also rerun with Objective-C zombie diagnostics:

```sh
NSZombieEnabled=YES JAVA_HOME=/private/tmp/rlhd-toolchains/temurin-21/Contents/Home ./gradlew --no-daemon --rerun-tasks macSurfaceSpikeCheck macSurfaceIntegrationTest
```

Result: `BUILD SUCCESSFUL in 4s`, 7/7 tasks executed, both 100-cycle suites passed, and no zombie-object diagnostic was emitted. AddressSanitizer is not practical for loading this helper into an existing unsanitized JVM, but the standalone native state harness was compiled and run under ASan:

```sh
xcrun clang -arch arm64 -std=c11 -Wall -Wextra -Werror -fsanitize=address -fno-omit-frame-pointer -I spikes/macos-surface/src/main/native spikes/macos-surface/src/main/native/surface_state.c spikes/macos-surface/src/test/native/surface_state_test.c -o /private/tmp/rlhd_surface_state_test_asan
ASAN_OPTIONS=detect_leaks=0 /private/tmp/rlhd_surface_state_test_asan
```

Result: `surface_state_test: 100 lifecycle cycles passed`, with no AddressSanitizer error.

Native artifact checks:

```sh
file build/spikes/macos-surface/native/librlhd_mac_surface.dylib build/spikes/macos-surface/native/surface_state_test
lipo -info build/spikes/macos-surface/native/librlhd_mac_surface.dylib
otool -L build/spikes/macos-surface/native/librlhd_mac_surface.dylib
nm -gU build/spikes/macos-surface/native/librlhd_mac_surface.dylib
```

Results: both artifacts are Mach-O arm64; `lipo` reports a non-fat arm64 dylib; `otool` reports Cocoa, QuartzCore, Metal, and system framework/runtime links; `nm` reports all six JNI exports (`nativeCreate`, `nativeAttach`, `nativeResize`, `nativeLayerHandle`, `nativeDetach`, `nativeClose`). The helper uses dynamic symbol lookup for JAWT, paired with the enforced Java-side `jawt`-first load order.

Java and artifact isolation checks:

```sh
find build/classes/java/macSurfaceSpike build/classes/java/macSurfaceSpikeTest -name '*.class' -exec file {} \;
jar tf build/libs/hd-1.5.2.jar
jar tf build/libs/hd-1.5.2-mac-surface-spike.jar
rg -n -i 'org\.lwjgl|lwjgl:' spikes/macos-surface build.gradle
```

Results: every spike/test class is bytecode version 55.0 (Java 11); the production JAR contains `HdPlugin`, `Renderer`, and `GLState` and contains no spike class or dylib; the explicit spike JAR contains the surface classes; no LWJGL dependency declaration was added. The global `tasks.withType(JavaCompile)` continues to set `options.release` to 11 for every source set.

Final hygiene:

```sh
git diff --check
git status --short
```

Result: `git diff --check` exited 0. Status contained only the intended `build.gradle`, provenance/source-inventory docs, spike tree, and this force-added ignored report before commit; there were no generated crash/native/build artifacts staged.

Primary-source pin check:

```sh
git ls-remote https://github.com/runelite/rlawt.git ecb6599caaaa12b1ddfe4d955cceb2e69fb06702 refs/heads/master refs/heads/main
```

Result: `ecb6599caaaa12b1ddfe4d955cceb2e69fb06702 refs/heads/master`; `docs/renderer/source-inventory.md` now uses the verified `runelite/rlawt` URL.

## Acceptance mapping

- Isolated spike build: sources live entirely under `spikes/macos-surface`; explicit tasks are not dependencies of normal `test`, `check`, `jar`, or `shadowJar`.
- Production OpenGL behavior/artifacts: no production Java source changed; ordinary root tests pass; the ordinary JAR retains OpenGL classes and contains no spike/native entries.
- Per-instance macOS surface: Java and native state are allocated per wrapper/Canvas; no global current layer/drawable/cache/timer exists.
- Lifecycle/API: valid-Canvas attach, logical/scale resize, immutable extent, suspension, detach, close, and guarded layer handle are implemented and tested.
- JAWT/AppKit ordering: retain occurs under drawing-surface lock; all JAWT drawing objects are unlocked/freed before synchronous main-queue work; tree-lock callers are rejected.
- CAMetalLayer contract: BGRA8Unorm, disabled implicit actions, scale, exact scaled drawable size, and zero-size suspension are implemented; no renderer or drawable request exists.
- arm64/toolchain: Gradle explicitly rejects non-macOS/non-arm64 native builds; final artifacts and JVM are arm64; only `xcrun clang` and Cocoa/QuartzCore/Metal are required.
- Java/runtime compatibility: all Java compiles use `--release 11`; no second LWJGL runtime or dependency exists; non-macOS rejects before native binding initialization.
- Tests: native and Java deterministic 100-cycle coverage passes; the explicit opt-in headful test also passes 100 real cycles.
- Provenance/notices: Apple/JAWT/OpenJDK inputs and the compatibility workaround are registered; no code was copied from gpu-vulkan, rlawt, or lwjgl3-awt; the repository BSD-2 license is unchanged and no third-party MIT/BSD source notice was removed.

## Concerns

- Detach leaves one hidden, inert, zero-sized, unparented plain CALayer sentinel owned by the Canvas's JAWT surface layers instead of a literal `nil`, because the tested OpenJDK nil setter retains a dangling pointer. The next attach replaces/releases it normally. Current `AWTSurfaceLayers.dealloc` does not release `layer`, so terminal detach without later replacement leaks one tiny sentinel retain per Canvas even after peer destruction. This bounded public-ABI tradeoff should be revalidated when the JDK implementation changes.
- Native output is intentionally arm64-only and local-fork-only. Universal/x86_64 packaging, code signing, notarization, and Plugin Hub distribution are outside Task 2; Plugin Hub's JNI restriction still applies.
- The optional offline Metal shader compiler remains unavailable and was not used; this task contains no shader or renderer.

## Review fixes

Status: DONE_WITH_CONCERNS

The review follow-up preserves the wrapper state when native close fails: `stateHandle`, attachment, Canvas, and extent are cleared only after `nativeClose` returns successfully. A fail-once fake-native test verifies that the original extent and borrowed layer handle remain usable after the exception, a resize still succeeds, close can be retried, and all operations reject only after the successful retry.

JAWT drawing-surface width and height are now copied while `JAWT_DrawingSurfaceInfo` remains locked. Attach creates the CAMetalLayer on AppKit with a zero anchor and origin-zero frame using those copied dimensions; resize updates the complete origin-zero frame, scale, and, for active extents, exact scaled `drawableSize`. A package-private test-only JNI assertion snapshots only public Objective-C properties and verifies installed-layer identity, frame, bounds, scale, `MTLPixelFormatBGRA8Unorm`, lifecycle suspension, and drawable size on every resize in all 100 headful cycles.

The first zero-extent assertion recorded an important runtime fact: on this Temurin/macOS combination, assigning `CGSizeZero` to `CAMetalLayer.drawableSize` left the prior `320x180` allocation visible even though frame and bounds became `0x0`. The final bridge therefore does not overwrite the last nonzero drawable allocation while suspended. Suspension is defined by the lifecycle extent plus a zero frame/bounds dimension; the bridge never calls `nextDrawable`, and the README requires a future renderer to gate acquisition on `extent().suspended()`. Restore again verifies the exact nonzero drawable size.

The JDK evidence is now immutable and exact. The installed Temurin `21.0.12+8` `release` file records Adoptium source commit `04806bcb1d50b35efc1c22a4d3b082c9a9a47563`; `git ls-remote` matched it to the peeled `jdk-21.0.12+8_adopt` tag. The pinned source at that revision confirms `AWTSurfaceLayers.setLayer(nil)` releases/removes the old layer without clearing its ivar and `dealloc` does not release `layer`. `provenance.md` and `source-inventory.md` now link that exact revision rather than mutable OpenJDK `master`.

Both native Gradle tasks declare `os.name`, `os.arch`, and `java.home` inputs. Running `buildMacSurfaceNative` with `-Dos.arch=x86_64` invalidated the prior output and executed the expected arm64 guard failure; rerunning both native build tasks with unchanged native arm64 JDK inputs reported both `UP-TO-DATE`. The README now requires a caller-supplied full native arm64 JDK, labels the `/private/tmp/rlhd-toolchains/...` value as this-session-only, and documents the CAMetalLayer handle as a borrowed, attached-lifetime, serialized, backend-specific pointer.

Fresh final verification:

```sh
JAVA_HOME=/private/tmp/rlhd-toolchains/temurin-21/Contents/Home ./gradlew --no-daemon --rerun-tasks macSurfaceSpikeCheck macSurfaceSpikeJar macSurfaceIntegrationTest test jar
```

Result: `BUILD SUCCESSFUL in 44s`, 14/14 tasks executed. The native harness completed 100 cycles; 10 deterministic spike tests, one 100-cycle headful geometry/lifecycle integration test, and 20 root tests passed with zero failures/errors/skips.

```sh
NSZombieEnabled=YES JAVA_HOME=/private/tmp/rlhd-toolchains/temurin-21/Contents/Home ./gradlew --no-daemon --rerun-tasks macSurfaceSpikeCheck macSurfaceIntegrationTest
```

Result: `BUILD SUCCESSFUL in 4s`, 7/7 tasks executed, both 100-cycle suites passed, and no zombie-object diagnostic was emitted. The standalone lifecycle harness also passed 100 cycles under AddressSanitizer with no diagnostic.

Artifact checks reconfirmed an arm64 Mach-O dylib and harness, a non-fat arm64 dylib, only the expected Cocoa/QuartzCore/Metal/system links, and seven JNI exports including the test-only `nativeAssertLayerState`. Every spike and spike-test class remains Java 11 bytecode version 55.0. The ordinary production JAR contains the existing OpenGL renderer and no spike/native entry; the explicit spike JAR contains only the surface classes. `git diff --check` passed.

The remaining concerns are unchanged except for the now-documented suspension contract: terminal detach can leak one tiny inert unparented sentinel retain per Canvas on this exact JDK implementation; native packaging remains arm64/local-fork-only; and any future renderer must never request a drawable while the extent is suspended.
