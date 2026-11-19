package com.mohdrozaimi.statuspro4k.engine

import android.app.ActivityManager
import android.content.Context
import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.media.MediaFormat
import android.os.Build
import android.os.PowerManager

/** What this phone's hardware H.264 encoder can really do. */
data class DeviceCapability(
    val encoderName: String,
    val isHardwareEncoder: Boolean,
    val maxShortSide: Int,
    val maxLongSide: Int,
    val supports4k30: Boolean,
    val supports4k60: Boolean,
    val supports1440p60: Boolean,
    val supports1080p60: Boolean,
    val maxBitrate: Int,
    val totalRamMb: Long,
    val gpuRenderer: String,
) {
    /** Is (shortSide x longSide) @ fps supported, either orientation. */
    fun supports(shortSide: Int, longSide: Int, fps: Int): Boolean =
        DeviceCapabilityDetector.encoderSupports(shortSide, longSide, fps.toDouble())

    val tierLabel: String
        get() = when {
            supports4k60 -> "4K60 capable"
            supports4k30 -> "4K30 capable"
            supports1440p60 -> "2K60 capable"
            supports1080p60 -> "1080p60 capable"
            else -> "1080p30"
        }
}

object DeviceCapabilityDetector {

    @Volatile private var cached: DeviceCapability? = null

    private fun avcEncoders(): List<MediaCodecInfo> =
        MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos.filter { info ->
            info.isEncoder && info.supportedTypes.any { it.equals(MediaFormat.MIMETYPE_VIDEO_AVC, true) }
        }.sortedByDescending { it.isHardwareAccelerated }

    fun encoderSupports(shortSide: Int, longSide: Int, fps: Double): Boolean =
        avcEncoders().any { info ->
            val vc = info.getCapabilitiesForType(MediaFormat.MIMETYPE_VIDEO_AVC).videoCapabilities ?: return@any false
            runCatching {
                vc.areSizeAndRateSupported(longSide, shortSide, fps) || vc.areSizeAndRateSupported(shortSide, longSide, fps)
            }.getOrDefault(false)
        }

    fun detect(context: Context): DeviceCapability {
        cached?.let { return it }
        val encs = avcEncoders()
        val best = encs.firstOrNull()
        var maxW = 1920
        var maxH = 1080
        var maxBr = 20_000_000
        if (best != null) {
            val vc = best.getCapabilitiesForType(MediaFormat.MIMETYPE_VIDEO_AVC).videoCapabilities
            maxW = vc.supportedWidths.upper
            maxH = vc.supportedHeights.upper
            maxBr = vc.bitrateRange.upper
        }
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val mi = ActivityManager.MemoryInfo().also { am.getMemoryInfo(it) }
        val cap = DeviceCapability(
            encoderName = best?.name ?: "none",
            isHardwareEncoder = best?.isHardwareAccelerated ?: false,
            maxShortSide = minOf(maxW, maxH),
            maxLongSide = maxOf(maxW, maxH),
            supports4k30 = encoderSupports(2160, 3840, 30.0),
            supports4k60 = encoderSupports(2160, 3840, 60.0),
            supports1440p60 = encoderSupports(1440, 2560, 60.0),
            supports1080p60 = encoderSupports(1080, 1920, 60.0),
            maxBitrate = maxBr,
            totalRamMb = mi.totalMem / (1024 * 1024),
            gpuRenderer = socModel(),
        )
        cached = cap
        return cap
    }

    /** 0 = none ... 6 = shutdown, as PowerManager.THERMAL_STATUS_*. */
    fun thermalStatus(context: Context): Int {
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        return pm.currentThermalStatus
    }

    fun thermalLabel(status: Int) = when (status) {
        PowerManager.THERMAL_STATUS_NONE -> "Normal"
        PowerManager.THERMAL_STATUS_LIGHT -> "Warm"
        PowerManager.THERMAL_STATUS_MODERATE -> "Hot"
        PowerManager.THERMAL_STATUS_SEVERE -> "Very hot"
        else -> "Critical"
    }
}

private fun socModel(): String =
    if (Build.VERSION.SDK_INT >= 31) "${Build.SOC_MANUFACTURER} ${Build.SOC_MODEL}" else Build.HARDWARE
