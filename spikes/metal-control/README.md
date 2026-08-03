# Direct-Metal presentation control spike

This is a disposable, opt-in control for comparing direct Metal presentation with the later MoltenVK control. It borrows Task 2's `MacMetalSurface` `CAMetalLayer` while attached and does not enter the 117HD renderer, scene preparation, production shaders, default `test`, default `jar`, or Plugin Hub artifact. It has no LWJGL dependency. Java bytecode is release 11; native output is macOS arm64.

The native renderer is per-instance. It configures `MTLPixelFormatBGRA8Unorm`, compiles one fixed embedded MSL source once during initialization with `newLibraryWithSource:options:error:`, and builds separate animated-triangle and premultiplied-UI pipelines. Runtime source compilation is used because this machine lacks a complete offline `metal`/`metallib` path. It is unsuitable for final production shader packaging, startup behavior, or performance-readiness conclusions; a production design must precompile and package a pinned metallib.

The synthetic UI is regenerated every frame as explicit premultiplied BGRA bytes. Its quadrants include transparent, half-alpha red, opaque blue, and changing premultiplied sentinels. UI blending is `ONE, ONE_MINUS_SRC_ALPHA`. Three independently owned UI texture slots bound in-flight work; a slot is never rebuilt or overwritten while its command buffer is live. Completion handlers release slots and record delayed `GPUStartTime`/`GPUEndTime` without calling Java. Suspended extents, unavailable slots, and `nextDrawable == nil` are one-shot skipped outcomes without retry loops or per-frame waits.

`fifo-like` requests `displaySyncEnabled=YES`; `unlocked` requests `displaySyncEnabled=NO` only when the runtime layer responds to that API. Both use `presentsWithTransaction=NO` and `presentDrawable` before `commit`. Logs preserve requested and effective behavior. These labels describe this Metal control only and do not claim Vulkan FIFO semantics.

Every frame checks `MacMetalSurface.extent().suspended()`. Extent changes rebuild each UI slot only after that slot is free. The preferred layer device is rechecked per frame; a changed non-null device causes all device-owned objects to be rebuilt after in-flight work drains. Java must close the renderer before detaching or closing `MacMetalSurface`. Close rejects new work, drains submitted command buffers, releases texture slots, sampler, pipelines, library, queue, and device, writes `run_end`, then relinquishes the borrowed layer. The final counter snapshot and `run_end` must report `live_native_objects=0`.

## Build, test, and run

Use a full native arm64 JDK. The path below is a session-local example, not a repository dependency:

```sh
export RLHD_METAL_JAVA_HOME=/absolute/path/to/arm64-jdk/Contents/Home
JAVA_HOME="$RLHD_METAL_JAVA_HOME" ./gradlew --no-daemon metalControlSpikeCheck metalControlSpikeJar
JAVA_HOME="$RLHD_METAL_JAVA_HOME" ./gradlew --no-daemon metalControlIntegrationTest
JAVA_HOME="$RLHD_METAL_JAVA_HOME" ./gradlew --no-daemon runMetalControlSpike --args='--seconds 1800 --log build/spikes/metal-control/manual-30m.jsonl'
```

`metalControlIntegrationTest` is explicitly headful and opens/resizes/fullscreens an AWT window. It runs 240 changing frames, toggles presentation behavior, suspends/restores, performs an offscreen GPU readback check for triangle/UI composition and BGRA/alpha orientation, drains, and validates the timing log. It is intentionally excluded from normal `test`, `check`, and `jar`.

The standalone program's default log is `build/spikes/metal-control/metal-control.jsonl`; the 30-minute command above uses `build/spikes/metal-control/manual-30m.jsonl`.

## Timing JSON Lines

Every line is one JSON object with `schema="rlhd.renderer.timing/v1"` and `backend="metal-control"`.

- `run_start`: monotonic `timestamp_ns`, requested and effective presentation modes.
- `frame`: `frame_id`, pixel `resolution`, requested/effective mode, `outcome`, CPU nanoseconds (`ui_generate`, `ui_upload`, `encode`, `submit`, `total`), delayed GPU start/end/duration or `null`, present requested/drawable-available flags, a complete counter snapshot, and `error`.
- `run_end`: monotonic `timestamp_ns`, final requested/effective mode, final counter snapshot, and `error`.

Frame outcomes are `submitted`, `skipped-suspended`, `skipped-in-flight`, `nil-drawable`, `rejected`, and `error`. Submitted frame records are emitted from the native completion handler, so GPU timing is populated and line order can reflect completion order. The counters are: `init_errors`, `shader_errors`, `pipeline_errors`, `submitted`, `completed`, `command_errors`, `present_requested`, `nil_drawable`, `skipped_suspended`, `skipped_in_flight`, `ui_upload_bytes`, `resize_rebuilds`, `device_rebuilds`, `live_native_objects`, `high_water_native_objects`, and `max_in_flight`.

## Manual 30-minute checklist

This checklist is provided for manual execution; it is not marked complete by the automated smoke.

- Minutes 0–3: start the exact 1800-second command; confirm the animated triangle/background and four UI sentinel regions are visible, with transparent UI revealing the triangle and half-alpha red blending rather than replacing it.
- Minutes 3–7: resize continuously through small, wide, tall, and Retina-scaled sizes. Confirm no stale-sized UI, stretching, flashes, or command errors.
- Minutes 7–10: minimize for at least 30 seconds, restore, and confirm rendering resumes without a busy loop or a burst of errors.
- Minutes 10–14: enter and exit native fullscreen twice. Confirm the image fills the drawable and returns to the window without channel or alpha changes.
- Minutes 14–19: observe the automatic FIFO-like/unlocked/FIFO-like transitions and inspect frame records. Confirm requested/effective values change; do not interpret either as Vulkan FIFO evidence.
- Minutes 19–24: move the window fully between displays, including a different scale or GPU when available. Pause on each display, resize, and confirm `device_rebuilds` changes only if the layer's preferred device changes. This multi-display step is manual because automation can exercise only the available display safely.
- Minutes 24–28: repeat minimize/restore, resize, and fullscreen while the UI continues changing. Confirm `max_in_flight <= 3`, `submitted >= completed`, and no retry-driven CPU spike.
- Minutes 28–30: close normally, then parse every JSON line and inspect `run_end`. Confirm `submitted == completed`, all error counters are zero, and `live_native_objects == 0`.

Useful final checks:

```sh
python3 -c 'import json,sys; [json.loads(line) for line in sys.stdin]' < build/spikes/metal-control/manual-30m.jsonl
tail -1 build/spikes/metal-control/manual-30m.jsonl
```

The implementation follows Apple's documented [`CAMetalLayer.nextDrawable`](https://developer.apple.com/documentation/quartzcore/cametallayer/nextdrawable()), [`displaySyncEnabled`](https://developer.apple.com/documentation/quartzcore/cametallayer/displaysyncenabled), [`MTLCommandBuffer.addCompletedHandler`](https://developer.apple.com/documentation/metal/mtlcommandbuffer/addcompletedhandler(_:)), and [`GPUStartTime`](https://developer.apple.com/documentation/metal/mtlcommandbuffer/gpustarttime) behavior.
