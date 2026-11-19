@file:OptIn(UnstableApi::class)

package com.mohdrozaimi.statuspro4k.engine

import android.content.Context
import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.media.MediaFormat
import android.os.Handler
import android.os.Looper
import androidx.media3.common.Effect
import androidx.media3.common.Format
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.transformer.Codec
import androidx.media3.transformer.Composition
import androidx.media3.transformer.DefaultEncoderFactory
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EditedMediaItemSequence
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import androidx.media3.transformer.VideoEncoderSettings
import com.mohdrozaimi.statuspro4k.engine.effects.CasSharpenEffect
import com.mohdrozaimi.statuspro4k.engine.effects.ColorDenoiseEffect
import com.mohdrozaimi.statuspro4k.engine.effects.ColorParams
import com.mohdrozaimi.statuspro4k.engine.effects.DetailScaleEffect
import com.mohdrozaimi.statuspro4k.engine.effects.FrameRateEffect
import java.io.File

/** Builds the GPU effect chain shared by export and live preview. */
object EffectChain {
    /**
     * Order matters:
     *  1. noise reduction + colour at source resolution (cheap, and noise is not magnified)
     *  2. frame-rate conversion at source resolution (motion search is cheaper there)
     *  3. detail upscaler / resampler to the target size
     *  4. contrast-adaptive sharpening at the final size
     */
    fun build(enh: EnhanceSettings, plan: ExportPlan?, sourceShortSide: Int): List<Effect> {
        val list = mutableListOf<Effect>()
        val colour = ColorParams.from(enh.colour, enh.noiseReduction)
        if (!colour.isNoOp) list += ColorDenoiseEffect(colour)
        if (plan != null) {
            if (plan.needsFrameRateConversion) list += FrameRateEffect(plan.fps, enh.motionInterpolation)
            if (plan.shortSide != sourceShortSide) list += DetailScaleEffect(plan.shortSide, plan.maxLongSide)
            // Upscaled frames gain more from sharpening; scale strength gently with the factor.
            val factor = (plan.shortSide.toFloat() / sourceShortSide).coerceIn(0.5f, 3f)
            val s = (enh.sharpness * (if (factor > 1.2f) 1.0f else 0.8f)).coerceIn(0f, 1f)
            if (s > 0.001f) list += CasSharpenEffect(s)
        } else if (enh.sharpness > 0.001f) {
            list += CasSharpenEffect(enh.sharpness * 0.8f)
        }
        return list
    }
}

/** Tells the encoder the real output frame rate so its rate control budgets bits correctly. */
private class FrameRateHintEncoderFactory(
    private val base: Codec.EncoderFactory,
    private val fps: Float,
) : Codec.EncoderFactory {
    override fun createForAudioEncoding(format: Format): Codec = base.createForAudioEncoding(format)
    override fun createForVideoEncoding(format: Format): Codec =
        base.createForVideoEncoding(format.buildUpon().setFrameRate(fps).build())
    override fun audioNeedsEncoding(): Boolean = base.audioNeedsEncoding()
    override fun videoNeedsEncoding(): Boolean = true
}

class VideoEnhancer(private val context: Context) {

    interface Callback {
        fun onProgress(percent: Int)
        fun onCompleted(result: ExportResult)
        fun onError(e: ExportException)
    }

    private var transformer: Transformer? = null
    private val handler = Handler(Looper.getMainLooper())
    private var poll: Runnable? = null

    /** Must be called on the main thread. */
    fun start(src: VideoInfo, enh: EnhanceSettings, plan: ExportPlan, output: File, cb: Callback) {
        val settings = VideoEncoderSettings.Builder()
            .setBitrate(plan.videoBitrate)
            .setBitrateMode(MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_VBR)
            .setiFrameIntervalSeconds(plan.keyframeIntervalSec)
        avcHighProfileLevel(plan)?.let { (p, l) -> settings.setEncodingProfileLevel(p, l) }

        val encoderFactory = DefaultEncoderFactory.Builder(context)
            .setRequestedVideoEncoderSettings(settings.build())
            .setEnableFallback(true)
            .build()

        val listener = object : Transformer.Listener {
            override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                stopPolling(); cb.onCompleted(exportResult)
            }
            override fun onError(composition: Composition, exportResult: ExportResult, exportException: ExportException) {
                stopPolling(); cb.onError(exportException)
            }
        }

        val t = Transformer.Builder(context)
            .setVideoMimeType(MimeTypes.VIDEO_H264)
            .setAudioMimeType(MimeTypes.AUDIO_AAC)
            .setEncoderFactory(FrameRateHintEncoderFactory(encoderFactory, plan.fps))
            .addListener(listener)
            .build()
        transformer = t

        val itemBuilder = MediaItem.Builder().setUri(src.uri)
        if (plan.clipEndMs > 0) {
            itemBuilder.setClippingConfiguration(
                MediaItem.ClippingConfiguration.Builder().setEndPositionMs(plan.clipEndMs).build(),
            )
        }
        val edited = EditedMediaItem.Builder(itemBuilder.build())
            .setEffects(Effects(emptyList(), EffectChain.build(enh, plan, src.shortSide)))
            .build()
        val composition = Composition.Builder(EditedMediaItemSequence(listOf(edited)))
            // HDR-aware: HDR10/HLG sources are tone-mapped to SDR BT.709 so WhatsApp shows correct colours.
            .setHdrMode(Composition.HDR_MODE_TONE_MAP_HDR_TO_SDR_USING_OPEN_GL)
            .build()

        output.parentFile?.mkdirs()
        if (output.exists()) output.delete()
        t.start(composition, output.absolutePath)

        val holder = ProgressHolder()
        poll = object : Runnable {
            override fun run() {
                val tr = transformer ?: return
                if (tr.getProgress(holder) == Transformer.PROGRESS_STATE_AVAILABLE) cb.onProgress(holder.progress)
                handler.postDelayed(this, 400)
            }
        }.also { handler.post(it) }
    }

    fun cancel() {
        stopPolling()
        transformer?.cancel()
        transformer = null
    }

    private fun stopPolling() {
        poll?.let { handler.removeCallbacks(it) }
        poll = null
    }

    /** H.264 High profile at the level needed for the plan, only if the encoder advertises it. */
    private fun avcHighProfileLevel(plan: ExportPlan): Pair<Int, Int>? {
        val mbPerSec = ((plan.outWidth + 15) / 16).toLong() * ((plan.outHeight + 15) / 16) * plan.fps.toLong()
        val needed = when {
            mbPerSec <= 245_760 -> MediaCodecInfo.CodecProfileLevel.AVCLevel4
            mbPerSec <= 522_240 -> MediaCodecInfo.CodecProfileLevel.AVCLevel42
            mbPerSec <= 983_040 -> MediaCodecInfo.CodecProfileLevel.AVCLevel51
            mbPerSec <= 2_073_600 -> MediaCodecInfo.CodecProfileLevel.AVCLevel52
            else -> return null
        }
        val ok = MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos.any { info ->
            info.isEncoder && info.isHardwareAccelerated &&
                info.supportedTypes.any { it.equals(MediaFormat.MIMETYPE_VIDEO_AVC, true) } &&
                info.getCapabilitiesForType(MediaFormat.MIMETYPE_VIDEO_AVC).profileLevels.any {
                    it.profile == MediaCodecInfo.CodecProfileLevel.AVCProfileHigh && it.level >= needed
                }
        }
        return if (ok) MediaCodecInfo.CodecProfileLevel.AVCProfileHigh to needed else null
    }
}
