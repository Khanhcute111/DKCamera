# DKCaMera AI MAX — Local Processing Requirements

## Product decision

- Profile: **AI MAX**
- Execution mode: **LOCAL ONLY**
- Target: Android app, Jetpack Compose, Camera2, minSdk 30.
- All photo pixels and inference inputs remain on the device. No photo upload, remote inference, cloud fallback, or network dependency is permitted in this pipeline.
- Preserve the captured original. AI output is a separate derivative and must never overwrite the source.

## User-facing behavior

1. Add an AI MAX enhancement action to the post-capture/edit flow.
2. Show progress and allow cancellation without corrupting the original.
3. Provide before/after comparison and a strength control.
4. Save the enhanced result as a new image, retaining EXIF orientation and copying safe metadata where supported.
5. Clearly disclose that generative reconstruction may invent plausible details. Do not present reconstructed detail as guaranteed sensor-captured truth.
6. Keep the UI in Vietnamese and expose unavailable capabilities honestly rather than showing fake progress or success.

## Processing pipeline

Use a staged, device-capability-aware pipeline. Do not run every stage blindly on every image.

1. Decode safely with bounds and memory budgeting; respect orientation and color space.
2. Analyze image quality and scene characteristics locally.
3. Apply local denoise/deblur and multi-frame fusion where valid source frames exist.
4. Apply super-resolution (2x baseline; higher scale only when memory, latency and model quality permit).
5. Run optional face restoration only when faces are detected and the user enables it.
6. Apply local tone mapping / HDR recovery and color grading.
7. Apply restrained final sharpening and export.
8. For any generative reconstruction stage, use a compatible on-device model only. Make its use explicit in the UI and keep the original available for comparison.

## Runtime and model requirements

- Prefer the repository's existing LiteRT CPU/GPU integration and existing native/GLES image pipeline where appropriate.
- Inspect existing model/runtime abstractions before introducing another inference stack.
- Models must be bundled or downloaded by an explicit user-initiated model-management flow; any download is model data only, never user photos. Explain model size and storage use before download.
- Verify model license, redistribution terms, input/output tensor shapes, supported operators, quantization, and device compatibility before adding model assets.
- Prefer quantized models and tiled/patch inference to control RAM on mobile devices.
- Select CPU/GPU delegate based on runtime availability; fall back to a supported local backend, not to a server.
- Check thermal state, available memory, image dimensions and battery constraints. Reduce tile size or pause safely when needed.
- Do not claim 4x/8x enhancement or generative reconstruction unless a real model and measured device validation support it.

## Privacy and network boundary

- AI enhancement code must not call HTTP, OkHttp, analytics, telemetry, or remote APIs with image bytes, thumbnails, embeddings, face crops, EXIF, or derived image features.
- Do not reuse any cloud image-generation feature in this local-only path.
- Add no new network permission solely for AI enhancement.
- Model downloads, if later implemented, must be separate from inference and must never transmit user content.
- Keep temporary files in app-private storage and delete them after completion/cancellation.

## Reliability and quality

- Handle unsupported formats, corrupt images, oversized images, OOM, model-load failure, delegate failure, cancellation and low-storage conditions.
- On failure, keep the original untouched and show a Vietnamese error with a useful recovery path.
- Avoid repeated lossy JPEG encode/decode between stages; retain a high-precision working representation where practical.
- Avoid halos, plastic skin, oversharpening, color shifts and invented facial identity changes. If generative mode is enabled, label the result as AI reconstructed.
- Log technical stage timings and failure codes only; do not log image content, paths containing personal data, or image-derived sensitive data.

## Acceptance criteria

- The complete enhancement path runs with network disabled after required model assets are available locally.
- No user image or derived image data is transmitted off-device.
- Original image remains byte-for-byte unchanged.
- Output is saved as a separate file and can be compared with the original.
- Cancellation and failures leave no partial output presented as successful.
- Memory use is bounded by tiling and device capability checks; OOM is handled without crashing the camera app.
- CPU/GPU fallback remains local.
- Vietnamese UI accurately reports progress, model availability, reconstruction behavior and errors.
- Build and device validation are required before calling the feature complete.

## Implementation sequence

1. Audit existing post-capture processing, LiteRT wrappers, model assets, image buffers and save/export flow.
2. Define a single AI MAX orchestration layer that reuses existing processing components.
3. Add local model capability detection and a model registry with license/provenance metadata.
4. Integrate stages incrementally, with explicit memory/thermal guards and cancellation.
5. Add Vietnamese controls, progress, comparison and export behavior.
6. Build the relevant Android variant and validate on supported physical hardware with network disabled.
7. Record measured latency, peak memory, output dimensions and known model limitations.

This document is a development requirement, not a claim that the AI MAX implementation or APK is already complete.
