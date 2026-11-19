package com.mohdrozaimi.statuspro4k.engine

import android.content.Context
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import kotlin.math.roundToInt

/** Real, measured properties of a video file. */
data class VideoInfo(
    val uri: Uri,
    val displayName: String,
    val width: Int,            // as stored in the stream
    val height: Int,
    val rotation: Int,         // 0/90/180/270
    val fps: Float,            // measured from sample timestamps
    val fpsIsVariable: Boolean,
    val videoBitrate: Int,     // bits per second (measured if metadata missing)
    val totalBitrate: Int,
    val videoCodec: String,
    val audioCodec: String?,
    val audioSampleRate: Int,
    val audioChannels: Int,
    val durationMs: Long,
    val fileSizeBytes: Long,
    val isHdr: Boolean,
) {
    /** Width/height as the viewer sees it (rotation applied). */
    val displayWidth get() = if (rotation % 180 == 0) width else height
    val displayHeight get() = if (rotation % 180 == 0) height else width
    val isPortrait get() = displayHeight > displayWidth
    val shortSide get() = minOf(width, height)
    val longSide get() = maxOf(width, height)

    val resolutionLabel: String get() = resolutionLabel(shortSide)
    val orientationLabel get() = if (isPortrait) "Portrait" else if (displayWidth == displayHeight) "Square" else "Landscape"

    fun summary() = "$resolutionLabel | ${fps.roundToInt()} FPS | ${formatBitrate(videoBitrate)}"
}

fun resolutionLabel(shortSide: Int): String = when {
    shortSide >= 2100 -> "4K"
    shortSide >= 1400 -> "2K"
    shortSide >= 1000 -> "1080p"
    shortSide >= 700 -> "720p"
    shortSide >= 470 -> "480p"
    else -> "${shortSide}p"
}

fun formatBitrate(bps: Int): String =
    if (bps >= 1_000_000) String.format("%.1f Mbps", bps / 1_000_000f) else "${bps / 1000} kbps"

fun formatSize(bytes: Long): String = when {
    bytes >= 1L shl 30 -> String.format("%.2f GB", bytes / (1L shl 30).toFloat())
    bytes >= 1L shl 20 -> String.format("%.1f MB", bytes / (1L shl 20).toFloat())
    else -> "${bytes / 1024} KB"
}

fun formatDuration(ms: Long): String {
    val s = ms / 1000
    return String.format("%d:%02d", s / 60, s % 60)
}

object VideoAnalyzer {

    fun analyze(context: Context, uri: Uri): VideoInfo {
        var name = uri.lastPathSegment ?: "video.mp4"
        var size = -1L
        if (uri.scheme == "content") {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)
                ?.use { c ->
                    if (c.moveToFirst()) {
                        c.getString(0)?.let { name = it }
                        if (!c.isNull(1)) size = c.getLong(1)
                    }
                }
        } else if (uri.scheme == "file") {
            uri.path?.let { size = java.io.File(it).length() }
        }
        if (size <= 0) {
            size = runCatching {
                context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { it.length } ?: -1L
            }.getOrDefault(-1L)
        }

        val mmr = MediaMetadataRetriever()
        var rotation = 0
        var durationMs = 0L
        var metaBitrate = 0
        try {
            mmr.setDataSource(context, uri)
            rotation = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
            durationMs = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            metaBitrate = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE)?.toIntOrNull() ?: 0
        } finally {
            runCatching { mmr.release() }
        }

        val ex = MediaExtractor()
        var width = 0
        var height = 0
        var vCodec = "unknown"
        var aCodec: String? = null
        var aRate = 0
        var aCh = 0
        var vBitrate = 0
        var hdr = false
        var fps = 0f
        var variable = false
        var videoTrackBytes = 0L
        try {
            ex.setDataSource(context, uri, null)
            var videoTrack = -1
            for (i in 0 until ex.trackCount) {
                val f = ex.getTrackFormat(i)
                val mime = f.getString(MediaFormat.KEY_MIME) ?: continue
                if (mime.startsWith("video/") && videoTrack < 0) {
                    videoTrack = i
                    width = f.getInteger(MediaFormat.KEY_WIDTH)
                    height = f.getInteger(MediaFormat.KEY_HEIGHT)
                    vCodec = codecName(mime)
                    if (f.containsKey(MediaFormat.KEY_BIT_RATE)) vBitrate = f.getInteger(MediaFormat.KEY_BIT_RATE)
                    if (f.containsKey(MediaFormat.KEY_COLOR_TRANSFER)) {
                        val t = f.getInteger(MediaFormat.KEY_COLOR_TRANSFER)
                        hdr = t == MediaFormat.COLOR_TRANSFER_ST2084 || t == MediaFormat.COLOR_TRANSFER_HLG
                    }
                    if (f.containsKey(MediaFormat.KEY_FRAME_RATE)) {
                        fps = runCatching { f.getInteger(MediaFormat.KEY_FRAME_RATE).toFloat() }
                            .getOrElse { runCatching { f.getFloat(MediaFormat.KEY_FRAME_RATE) }.getOrDefault(0f) }
                    }
                    if (rotation == 0 && f.containsKey(MediaFormat.KEY_ROTATION)) rotation = f.getInteger(MediaFormat.KEY_ROTATION)
                } else if (mime.startsWith("audio/") && aCodec == null) {
                    aCodec = codecName(mime)
                    aRate = f.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                    aCh = f.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                }
            }
            if (videoTrack >= 0) {
                // Measure the real frame rate from sample timestamps (first ~10 s, max 900 frames).
                ex.selectTrack(videoTrack)
                val ts = ArrayList<Long>()
                while (ts.size < 900) {
                    val t = ex.sampleTime
                    if (t < 0) break
                    ts.add(t)
                    videoTrackBytes += ex.sampleSize.coerceAtLeast(0)
                    if (t > 10_000_000L) break
                    if (!ex.advance()) break
                }
                if (ts.size >= 3) {
                    ts.sort()
                    val deltas = (1 until ts.size).map { ts[it] - ts[it - 1] }.filter { it > 0 }.sorted()
                    if (deltas.isNotEmpty()) {
                        val span = (ts.last() - ts.first()).toFloat()
                        val measured = (ts.size - 1) * 1_000_000f / span
                        fps = snapFps(measured)
                        val median = deltas[deltas.size / 2].toFloat()
                        variable = deltas.count { kotlin.math.abs(it - median) > median * 0.25f } > deltas.size / 10
                        if (vBitrate <= 0 && span > 0) vBitrate = (videoTrackBytes * 8 * 1_000_000f / span).toInt()
                    }
                }
            }
        } finally {
            runCatching { ex.release() }
        }

        val total = if (metaBitrate > 0) metaBitrate else if (durationMs > 0 && size > 0) (size * 8000 / durationMs).toInt() else 0
        if (vBitrate <= 0) vBitrate = total

        return VideoInfo(
            uri = uri, displayName = name, width = width, height = height, rotation = rotation,
            fps = fps, fpsIsVariable = variable, videoBitrate = vBitrate, totalBitrate = total,
            videoCodec = vCodec, audioCodec = aCodec, audioSampleRate = aRate, audioChannels = aCh,
            durationMs = durationMs, fileSizeBytes = size, isHdr = hdr,
        )
    }

    private fun snapFps(f: Float): Float {
        val common = floatArrayOf(23.976f, 24f, 25f, 29.97f, 30f, 48f, 50f, 59.94f, 60f, 90f, 120f)
        val near = common.minByOrNull { kotlin.math.abs(it - f) }!!
        return if (kotlin.math.abs(near - f) < 0.6f) near else (f * 10).roundToInt() / 10f
    }

    private fun codecName(mime: String) = when (mime) {
        MediaFormat.MIMETYPE_VIDEO_AVC -> "H.264 / AVC"
        MediaFormat.MIMETYPE_VIDEO_HEVC -> "H.265 / HEVC"
        MediaFormat.MIMETYPE_VIDEO_VP9 -> "VP9"
        MediaFormat.MIMETYPE_VIDEO_AV1 -> "AV1"
        MediaFormat.MIMETYPE_VIDEO_MPEG4 -> "MPEG-4"
        "video/dolby-vision" -> "Dolby Vision"
        MediaFormat.MIMETYPE_AUDIO_AAC -> "AAC"
        MediaFormat.MIMETYPE_AUDIO_OPUS -> "Opus"
        MediaFormat.MIMETYPE_AUDIO_AMR_NB -> "AMR-NB"
        MediaFormat.MIMETYPE_AUDIO_AMR_WB -> "AMR-WB"
        MediaFormat.MIMETYPE_AUDIO_MPEG -> "MP3"
        MediaFormat.MIMETYPE_AUDIO_VORBIS -> "Vorbis"
        MediaFormat.MIMETYPE_AUDIO_FLAC -> "FLAC"
        else -> mime.substringAfter('/').uppercase()
    }
}
