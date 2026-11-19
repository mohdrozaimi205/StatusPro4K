# StatusPro 4K

Android app (Kotlin, Media3) that enhances a video before posting it to **WhatsApp Status** or **WhatsApp Business Status**.

## What the engine really does
| Stage | Technique | Runs on |
|---|---|---|
| Analyzer | MediaExtractor + MediaMetadataRetriever; FPS measured from sample timestamps | CPU |
| Noise reduction | 13-tap edge-preserving bilateral filter | GPU (OpenGL ES) |
| Colour | shadow lift, highlight recovery, soft S-curve, skin-safe vibrance; HDR10/HLG tone-mapped to SDR | GPU |
| Frame rate | true CFR resampling; raising FPS synthesises new frames with motion-compensated block-matching interpolation | GPU |
| Upscale | 16-tap Catmull-Rom with anti-ringing clamp (area filter for downscale) | GPU |
| Sharpen | Contrast Adaptive Sharpening (CAS) | GPU |
| Encode | H.264 High (when supported) + AAC, VBR, 1 s keyframes, encoder told the real FPS | MediaCodec (hardware) |
| Container | MP4 fast-start (moov moved to the front, chunk offsets patched) | CPU |
| Verify | output re-analysed; real resolution & FPS shown | CPU |

Automatic recovery: if the encoder fails or the phone gets too hot (thermal SEVERE), the export restarts on a lighter plan (4K60 → 4K30 → 2K → 1080p …).

The upscaler and interpolator are classical GPU algorithms, **not neural networks**. See "AI roadmap" below.

## Build
* Android Studio: open this folder → Run.
* GitHub: every push to `main` builds a signed APK and publishes it under **Releases**.

## AI roadmap (not included yet)
* Upscaling: Real-ESRGAN `realesr-general-x4v3` (BSD-3) via ncnn-Vulkan.
* Interpolation: RIFE v4.x via ncnn-Vulkan.
Both need native (NDK) code and run far slower than real time on phones.

## Note on signing
`keystore/statuspro4k.jks` is committed so CI can sign consistent updates. For a public production app, move it to GitHub Secrets.
