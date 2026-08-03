# Direct-Metal presentation control spike

This is a disposable, opt-in control for comparing direct Metal presentation with the later MoltenVK control. It borrows Task 2's `MacMetalSurface` `CAMetalLayer` while attached and does not enter the 117HD renderer, scene preparation, production shaders, default `test`, default `jar`, or Plugin Hub artifact. It has no LWJGL dependency. Java bytecode is release 11; native output is macOS arm64.

The native renderer is per-instance. It configures `MTLPixelFormatBGRA8Unorm`, compiles one fixed embedded MSL source once during initialization with `newLibraryWithSource:options:error:`, and builds separate animated-triangle and premultiplied-UI pipelines. Runtime source compilation is used because this machine lacks a complete offline `metal`/`metallib` path. It is unsuitable for final production shader packaging, startup behavior, or performance-readiness conclusions; a production design must precompile and package a pinned metallib.

The synthetic UI is regenerated every frame in Java as explicit premultiplied BGRA bytes. Its quadrants include transparent, half-alpha red, opaque blue, and changing premultiplied sentinels. Live and offscreen checks share the same native upload and encode helpers; the readback renders two distinct Java-generated frames and proves sentinel layout, blending, and animation through the GPU. UI blending is `ONE, ONE_MINUS_SRC_ALPHA`. Three independently owned UI texture slots bound in-flight work; a slot is never rebuilt or overwritten while its command buffer is live. Completion handlers release slots and record delayed `GPUStartTime`/`GPUEndTime` without calling Java.

`nextDrawable` never runs on the Java frame caller. Each renderer owns one serial native queue, permits at most one pending acquisition and one retained ready drawable, and schedules another request only when needed. Every request captures the active pixel extent and acquisition generation. Resize, Retina-scale change, and suspension invalidate that generation and release any ready drawable; stale worker results cannot publish, and `render` rechecks the ready texture dimensions before consumption. `render` consumes a current drawable or records one skipped attempt and returns; it has no retry loop or process-global worker. Close invalidates the acquisition generation and drains a pending request, bounded by `allowsNextDrawableTimeout=YES`, before releasing its queue.

`fifo-like` requests `displaySyncEnabled=YES`; `unlocked` requests `displaySyncEnabled=NO` only when the runtime layer responds to that API. The setter and getter run on AppKit/main, and effective mode comes from observed getter state rather than the requested value. Both use `presentsWithTransaction=NO` and `presentDrawable` before `commit`. Logs preserve requested and effective behavior. These labels describe this Metal control only and do not claim Vulkan FIFO semantics.

Every frame checks `MacMetalSurface.extent().suspended()`. Extent changes rebuild each UI slot only after that slot is free. The preferred layer device is rechecked per frame; a changed non-null device causes all device-owned objects to be rebuilt after in-flight work drains. Java must close the renderer before detaching or closing `MacMetalSurface`. Native create defensively retains the validated layer and terminal close clears its device and releases that retain on AppKit/main, preventing an Objective-C use-after-free if a caller violates the required order; this is hardening, not permission to detach early.

Close uses a caller-preallocated counter array. JNI validates and acquires that storage before consuming the handle; a pre-consumption failure leaves the renderer retryable. A successful native return is the exact consumption boundary: Java immediately zeros the handle and becomes terminal before counter-object materialization. Raw final values remain available without JNI. Native close rejects new work, invalidates/drains drawable acquisition, drains command-buffer callbacks, waits up to one second total for detached presentation tokens, emits `callback-timeout`/null timing when needed, releases texture slots, sampler, pipelines, library, queue, device and layer, then writes `run_end`. The final counter snapshot and `run_end` must report `live_native_objects=0`.

## Build, test, and run

Use a full native arm64 JDK. The path below is a session-local example, not a repository dependency:

```sh
export RLHD_METAL_JAVA_HOME=/absolute/path/to/arm64-jdk/Contents/Home
JAVA_HOME="$RLHD_METAL_JAVA_HOME" ./gradlew --no-daemon metalControlSpikeCheck metalControlSpikeJar
JAVA_HOME="$RLHD_METAL_JAVA_HOME" ./gradlew --no-daemon metalControlIntegrationTest
JAVA_HOME="$RLHD_METAL_JAVA_HOME" ./gradlew --no-daemon runMetalControlSpike --args='--seconds 1800 --log build/spikes/metal-control/manual-30m.jsonl'
```

`metalControlIntegrationTest` is explicitly headful and opens/resizes/fullscreens an AWT window. It runs 240 changing frames, toggles presentation behavior, suspends/restores, verifies actual presentation callbacks, performs the dual-Java-frame GPU readback, and exercises close/acquisition/mode/callback/upload/present failure seams. It is intentionally excluded from normal `test`, `check`, and `jar`.

The standalone program's default log is `build/spikes/metal-control/metal-control.jsonl`; the 30-minute command above uses `build/spikes/metal-control/manual-30m.jsonl`.

## Timing JSON Lines

Every line is one JSON object with `schema="rlhd.renderer.timing/v1"` and `backend="metal-control"`.

- `run_start`: monotonic `timestamp_ns`, requested and effective presentation modes.
- `frame`: `frame_id`, pixel `resolution`, requested/effective mode, `outcome`, CPU nanoseconds (`ui_generate`, `ui_upload`, `encode`, `submit`, `total`), delayed GPU start/end/duration or `null`, present requested/drawable-available flags, callback status (`presented`, `dropped`, `unsupported`, `callback-timeout`, or `not-requested`), actual presentation host time/latency or `null`, a complete counter snapshot, and `error`.
- `run_end`: monotonic `timestamp_ns`, final requested/effective mode, final counter snapshot, and `error`.

Frame outcomes are `submitted`, `skipped-suspended`, `skipped-in-flight`, `nil-drawable`, `rejected`, and `error`; exactly one record is emitted for every attempted frame. Submitted records wait for GPU completion and presentation settlement/fallback, so line order can reflect asynchronous completion. `run_end.error` reflects the initialization, shader, pipeline, and command error counters. Presentation timeout is a bounded fallback status with its own counter, not a renderer error. Counters additionally include presentation callback/drop/timeout counts, present-mode divergence, and drawable acquisition requests/completions.

`TimingJsonSchema` uses a spike-scoped Gson 2.14 strict `JsonReader` plus typed validation. It rejects duplicate/trailing/malformed JSON, wrong types or enums, negative/inconsistent timing and counters, incomplete records, invalid run ordering, and nonzero final live objects. The Gson and JUnit configurations remain isolated from production and add no LWJGL dependency.

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
