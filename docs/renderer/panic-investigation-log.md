# Renderer panic investigation log

This is the append-only human ledger for renderer-related host failures. The machine-readable lead-up is [panic-investigation-runs.jsonl](panic-investigation-runs.jsonl). Do not rewrite old incident conclusions; append a correction when later evidence changes them.

## Evidence rules

- A missing terminal record means only that the JVM did not finish. Correlate its final durable checkpoint with an actual `.panic` report before classifying it as a kernel panic.
- An `intent` without the matching `completed` record identifies the operation in flight. It does not by itself prove that operation caused the panic.
- Change one risk variable per run: surface attachment, first presentation, resize, present mode, fullscreen, display migration, duration, or concurrent RuneLite/OpenGL load.
- Record the exact commit, dirty state, command, OS build, display topology/mode/refresh rate, loader, MoltenVK build, validation state, and timing-log path.
- The safety journal uses a storage force at sparse lifecycle boundaries and one-second heartbeats. Do not use it as benchmark timing evidence.

## Risk ladder

1. CPU/offline checks; no native surface or GPU queue submission.
2. Vulkan instance and device discovery; no surface or swapchain.
3. Offscreen GPU render and readback; no WindowServer presentation.
4. Window and `CAMetalLayer` attachment; no Vulkan surface.
5. Vulkan Metal surface and swapchain creation; no presentation.
6. First FIFO presentation in a window.
7. Sustained windowed FIFO presentation.
8. Window resize and zero-extent suspend/restore.
9. Unlocked present-mode transition.
10. Native fullscreen enter/exit.
11. Display migration, mixed refresh/scaling, and concurrent RuneLite/OpenGL.

Advance one rung only after the prior run has a terminal record, zero validation errors, balanced resources, and a reviewed timing log. A kernel panic returns the next run to the last completed rung unless a single-variable reproduction is deliberately approved.

The existing Vulkan live controls expose rungs 5–10 as the mandatory Gradle property `-PvulkanLiveRiskLevel=<value>`: `surface-swapchain`, `first-fifo-present`, `sustained-fifo`, `resize-suspend-restore`, `unlocked-present`, or `fullscreen`. No value is the fail-closed default. Each process selects exactly one value; resize/suspend/restore, unlocked/FIFO restoration, and fullscreen enter/exit are mutually exclusive target actions. The selected timing-log path and validation request must have a durable `timing_log.selected` intent/completion pair before backend initialization. Rungs 2–4 require separate controls and rung 11 remains manual; neither may be inferred from a rung-5–10 run.

## Incidents

### P-0001 — WindowServer display-pipeline panic

- Date: 2026-08-03
- Host: Apple M3 Ultra Mac Studio
- OS build: macOS `25F84`; Darwin `25.5.0`
- Signature: `mismatched swapID's 10237 vs 10238` at `UnifiedPipeline.cpp:15810`
- Panicked task: `WindowServer`
- Backtrace: `IOMobileGraphicsFamily` and `AppleMobileDispT603C-DCP`
- Reported activity: renderer verification; the exact Gradle task, backend stage, window state, and display transition were not durably captured
- Conclusion: confirmed Apple display/DCP panic; renderer trigger and minimal reproducer remain unresolved
- Follow-up: all Vulkan headful tasks now append forced lifecycle checkpoints before further live testing

## Run review template

### R-YYYYMMDD-NNN — short description

- Commit and dirty state:
- Command:
- OS/build and uptime:
- Display topology/mode/refresh/scaling:
- Vulkan loader, MoltenVK, validation:
- Changed variable:
- Final durable checkpoint:
- Timing log:
- Panic artifact or normal terminal record:
- Result:
- Evidence-backed conclusion:
- Next single-variable test:

## Primary references

- [Java `FileChannel.force(boolean)`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/nio/channels/FileChannel.html#force(boolean)) — requests that file content, and optionally metadata, be forced to storage.
- [Apple Feedback Assistant](https://developer.apple.com/feedback-assistant/) — Apple requests system information with reports involving kernel panics and hardware issues.
- [Apple Metal command-buffer debugging](https://developer.apple.com/documentation/metal/command-buffer-debugging) — command labeling, errors, logs, and CPU/GPU scheduling timestamps for later native-Metal correlation.
