package com.mohdrozaimi.statuspro4k.engine

import kotlin.math.roundToInt

enum class ExportMode(val title: String, val subtitle: String) {
    MAXIMUM_QUALITY("Maximum Quality", "Up to 4K · 60 FPS · high bitrate · for keeping"),
    WHATSAPP_OPTIMIZED("WhatsApp Optimized", "1080p · stable H.264 · efficient size · for Status"),
}

enum class TargetResolution(val shortSide: Int, val label: String) {
    ORIGINAL(0, "Original"),
    P720(720, "720p"),
    P1080(1080, "1080p"),
    K2(1440, "2K"),
    K4(2160, "4K");
}

/** 0 = keep original frame rate. */
val FPS_OPTIONS = listOf(0, 24, 25, 30, 50, 60)

data class EnhanceSettings(
    val sharpness: Float = 0.45f,
    val noiseReduction: Float = 0.25f,
    val colour: Float = 0.35f,
    val motionInterpolation: Boolean = true,
)

data class ExportConfig(
    val mode: ExportMode = ExportMode.WHATSAPP_OPTIMIZED,
    val resolution: TargetResolution = TargetResolution.P1080,
    val fps: Int = 60,
    val bitrate: Int = 0,               // 0 = automatic
    val enhance: EnhanceSettings = EnhanceSettings(),
    val trimToStatusLimit: Boolean = true,
) {
    companion object {
        /** WhatsApp Status currently allows clips up to 90 s. */
        const val STATUS_LIMIT_MS = 90_000L
        const val CODEC_LABEL = "H.264 / AVC High · AAC 48 kHz · MP4 fast-start"
    }
}

/** A fully resolved plan: exact output numbers that the engine will produce and then verify. */
data class ExportPlan(
    val shortSide: Int,
    val maxLongSide: Int,
    val outWidth: Int,     // display orientation
    val outHeight: Int,
    val fps: Float,
    val needsFrameRateConversion: Boolean,
    val videoBitrate: Int,
    val keyframeIntervalSec: Float,
    val clipEndMs: Long,   // C.TIME_END_OF_SOURCE when not clipped
    val estimatedBytes: Long,
    val notes: List<String>,
) {
    val label: String get() = "${outWidth}×${outHeight} · ${fps.roundToInt()} FPS · ${formatBitrate(videoBitrate)}"
    val resolutionLabel get() = resolutionLabel(minOf(outWidth, outHeight))
}

object ExportPlanner {

    private const val AUDIO_BITRATE = 192_000

    fun plan(src: VideoInfo, cfg: ExportConfig, cap: DeviceCapability, forceShortSide: Int? = null, forceFps: Int? = null): ExportPlan {
        val notes = mutableListOf<String>()

        // --- resolution
        var short = when {
            forceShortSide != null -> forceShortSide
            cfg.mode == ExportMode.WHATSAPP_OPTIMIZED -> 1080
            cfg.resolution == TargetResolution.ORIGINAL -> src.shortSide
            else -> cfg.resolution.shortSide
        }
        val aspect = src.longSide.toFloat() / src.shortSide.coerceAtLeast(1)
        var long = (short * aspect).roundToInt()
        if (long > cap.maxLongSide || short > cap.maxShortSide) {
            val s = minOf(cap.maxLongSide / aspect, cap.maxShortSide.toFloat())
            short = s.toInt()
            long = (short * aspect).roundToInt()
            notes += "Encoder limit: resolution capped to ${short}p on this phone."
        }
        val maxLong = maxOf(long, 2)

        // --- frame rate
        var fps = when {
            forceFps != null -> forceFps.toFloat()
            cfg.fps == 0 -> src.fps
            else -> cfg.fps.toFloat()
        }
        if (fps <= 0f) fps = 30f
        if (!DeviceCapabilityDetector.encoderSupports(short, long, fps.toDouble())) {
            val lower = listOf(50f, 30f, 25f, 24f).firstOrNull { it < fps && DeviceCapabilityDetector.encoderSupports(short, long, it.toDouble()) }
            if (lower != null) {
                notes += "This phone can't encode ${short}p at ${fps.roundToInt()} FPS — using ${lower.roundToInt()} FPS."
                fps = lower
            }
        }
        val needsFps = kotlin.math.abs(fps - src.fps) > 0.5f || src.fpsIsVariable
        if (fps > src.fps + 0.5f) {
            notes += if (cfg.enhance.motionInterpolation)
                "${src.fps.roundToInt()} → ${fps.roundToInt()} FPS with motion-compensated interpolation."
            else "${src.fps.roundToInt()} → ${fps.roundToInt()} FPS by frame resampling (interpolation off)."
        }
        if (src.fpsIsVariable) notes += "Variable frame rate detected — output will be constant (CFR)."

        // --- bitrate (bits-per-pixel model, capped by encoder)
        val pixels = short.toLong() * long
        val bpp = if (cfg.mode == ExportMode.MAXIMUM_QUALITY) 0.16 else 0.085
        var br = if (cfg.bitrate > 0) cfg.bitrate else (pixels * fps * bpp).toInt()
        if (cfg.mode == ExportMode.WHATSAPP_OPTIMIZED && cfg.bitrate == 0) br = br.coerceIn(6_000_000, 12_000_000)
        br = br.coerceIn(2_000_000, minOf(cap.maxBitrate, 120_000_000))

        // --- duration / clip
        var clipEnd = Long.MIN_VALUE // C.TIME_END_OF_SOURCE
        var durMs = src.durationMs
        if (cfg.mode == ExportMode.WHATSAPP_OPTIMIZED && cfg.trimToStatusLimit && src.durationMs > ExportConfig.STATUS_LIMIT_MS) {
            clipEnd = ExportConfig.STATUS_LIMIT_MS
            durMs = ExportConfig.STATUS_LIMIT_MS
            notes += "Trimmed to 90 s (WhatsApp Status limit)."
        }
        if (short > src.shortSide) notes += "Upscaled ${src.resolutionLabel} → ${resolutionLabel(short)} with GPU detail upscaler."

        val outW: Int
        val outH: Int
        if (src.isPortrait) { outW = short; outH = long } else { outW = long; outH = short }

        val est = ((br.toLong() + AUDIO_BITRATE) * durMs / 8000L)
        return ExportPlan(
            shortSide = short, maxLongSide = maxLong, outWidth = outW - outW % 2, outHeight = outH - outH % 2,
            fps = fps, needsFrameRateConversion = needsFps, videoBitrate = br,
            keyframeIntervalSec = 1f, clipEndMs = clipEnd, estimatedBytes = est, notes = notes,
        )
    }

    /** Fallback ladder used for automatic recovery (encoder errors, overheating). */
    fun fallbacks(first: ExportPlan): List<Pair<Int, Int>> {
        val ladder = listOf(2160, 1440, 1080, 720)
        val out = mutableListOf<Pair<Int, Int>>()
        val fps = first.fps.roundToInt()
        if (fps > 30) out += first.shortSide to 30
        for (s in ladder.filter { it < first.shortSide }) {
            out += s to fps
            if (fps > 30) out += s to 30
        }
        return out.distinct()
    }
}
