# macOS surface lifecycle spike

This opt-in spike owns only a per-instance AWT `Canvas`/`CAMetalLayer` lifecycle. It does not create a Metal device, request a drawable, implement a renderer, add LWJGL, or change the production OpenGL path.

`MacMetalSurface` is reusable across attach/detach cycles and is permanently closed by `close()`. Attach requires a displayable `Canvas`. Resize accepts non-negative logical dimensions and a finite positive backing scale; a zero width or height produces a suspended extent and zero frame/bounds in that dimension. `SurfaceExtent` snapshots are immutable and report logical dimensions, exact scaled pixel dimensions, backing scale, and suspension. `metalLayerHandle()` returns a borrowed Objective-C pointer: it is valid only while the surface remains attached and calls are serialized through the wrapper, becomes dangling after detach or close, must never be released by the caller, and is a macOS/CAMetalLayer-specific escape hatch rather than a backend-neutral handle.

On the tested Temurin/macOS runtime, assigning `CGSizeZero` to `CAMetalLayer.drawableSize` preserves its prior nonzero allocation rather than exposing a zero value. The bridge therefore leaves `drawableSize` unchanged while suspended, and the integration assertion verifies zero frame/bounds, lifecycle suspension, retained drawable size, scale, format, and installed-layer identity. Any later renderer must check `extent().suspended()` before calling `nextDrawable`; this bridge never acquires a drawable.

The native bridge loads the JDK's `jawt` library before `librlhd_mac_surface.dylib`. It retains `JAWT_SurfaceLayers` while the drawing surface is locked, releases all JAWT drawing-surface objects, and only then dispatches synchronously to the AppKit main queue. Callers holding the AWT tree lock are rejected before any native operation that can dispatch to AppKit. Detach replaces the CAMetalLayer with a hidden, zero-sized plain CALayer sentinel and removes that sentinel from its superlayer because the tested OpenJDK implementation leaves a dangling private pointer when the public layer property is set to `nil`; a later attach replaces and releases the valid sentinel through the public property. A terminal detach leaves at most one inert, unparented sentinel retain per Canvas. The current OpenJDK `AWTSurfaceLayers.dealloc` releases `windowLayer` but not `layer`, so without a later replacement that tiny sentinel survives peer destruction; this is the bounded public-ABI tradeoff.

Choose a caller-supplied full, native arm64 JDK and run the non-headful spike checks with it. The `/private/tmp/rlhd-toolchains/temurin-21/Contents/Home` path used during this spike was a session-specific example, not a durable project path:

```sh
export RLHD_MAC_SURFACE_JAVA_HOME=/absolute/path/to/arm64-jdk/Contents/Home
JAVA_HOME="$RLHD_MAC_SURFACE_JAVA_HOME" ./gradlew --no-daemon macSurfaceSpikeCheck macSurfaceSpikeJar
```

The explicit native integration test opens a small AWT window and performs 100 real JAWT attach/resize/suspend/restore/detach cycles:

```sh
JAVA_HOME="$RLHD_MAC_SURFACE_JAVA_HOME" ./gradlew --no-daemon macSurfaceIntegrationTest -PrendererHeadfulAcknowledgement=I_ACCEPT_KERNEL_PANIC_RISK
```

It is intentionally excluded from `test`, `check`, `jar`, and `macSurfaceSpikeCheck`. It is not suitable for unattended or headless Plugin Hub CI because a displayable macOS Canvas and active window server are required. The deterministic Java tests use an injected native boundary, and the native C harness exercises the same lifecycle state implementation without opening a window.

Normal `./gradlew test` and `./gradlew jar` remain production-only. The spike Java artifact is built only by `macSurfaceSpikeJar`; the native artifact is built only by `buildMacSurfaceNative`, `macSurfaceIntegrationTest`, or `macSurfaceSpikeCheck`.

Headful spike tasks are fail-closed because they create native presentation surfaces. Use the acknowledgement only on an expendable test host after reviewing its display topology and kernel-panic risk.
