@file:OptIn(UnstableApi::class)

package com.mohdrozaimi.statuspro4k.engine.effects

import android.content.Context
import android.opengl.GLES20
import androidx.media3.common.VideoFrameProcessingException
import androidx.media3.common.util.GlProgram
import androidx.media3.common.util.GlUtil
import androidx.media3.common.util.Size
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.BaseGlShaderProgram
import androidx.media3.effect.GlEffect
import androidx.media3.effect.GlShaderProgram
import kotlin.math.roundToInt

/** Small helper so every pass draws a full-screen quad the same way. */
internal fun GlProgram.drawQuad() {
    bindAttributesAndUniforms()
    GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
    GlUtil.checkGlError()
}

internal fun newProgram(fragment: String): GlProgram =
    GlProgram(Shaders.VERTEX, fragment).apply {
        setBufferAttribute(
            "aFramePosition",
            GlUtil.getNormalizedCoordinateBounds(),
            GlUtil.HOMOGENEOUS_COORDINATE_VECTOR_SIZE,
        )
    }

// ---------------------------------------------------------------- Colour + noise reduction

data class ColorParams(
    val denoise: Float = 0f,
    val contrast: Float = 0f,
    val vibrance: Float = 0f,
    val shadows: Float = 0f,
    val highlights: Float = 0f,
) {
    val isNoOp get() = denoise <= 0.001f && contrast <= 0.001f && vibrance <= 0.001f && shadows <= 0.001f && highlights <= 0.001f

    companion object {
        /** Maps the single "Colour Enhancement" slider (0..1) to a natural, not over-saturated grade. */
        fun from(colour: Float, denoise: Float) = ColorParams(
            denoise = denoise,
            contrast = colour * 0.8f,
            vibrance = colour * 0.7f,
            shadows = colour * 0.7f,
            highlights = colour * 0.8f,
        )
    }
}

class ColorDenoiseEffect(private val params: ColorParams) : GlEffect {
    override fun toGlShaderProgram(context: Context, useHdr: Boolean): GlShaderProgram =
        ColorDenoiseProgram(params, useHdr)

    override fun isNoOp(inputWidth: Int, inputHeight: Int) = params.isNoOp
}

private class ColorDenoiseProgram(private val p: ColorParams, useHdr: Boolean) :
    BaseGlShaderProgram(useHdr, /* texturePoolCapacity= */ 2) {

    private val program = try { newProgram(Shaders.COLOR_DENOISE) } catch (e: Exception) {
        throw VideoFrameProcessingException(e)
    }
    private var w = 1
    private var h = 1

    override fun configure(inputWidth: Int, inputHeight: Int): Size {
        w = inputWidth; h = inputHeight
        return Size(inputWidth, inputHeight)
    }

    override fun drawFrame(inputTexId: Int, presentationTimeUs: Long) {
        try {
            program.use()
            program.setSamplerTexIdUniform("uTexSampler", inputTexId, 0)
            program.setFloatsUniform("uTexel", floatArrayOf(1f / w, 1f / h))
            program.setFloatUniform("uDenoise", p.denoise)
            program.setFloatUniform("uContrast", p.contrast)
            program.setFloatUniform("uVibrance", p.vibrance)
            program.setFloatUniform("uShadows", p.shadows)
            program.setFloatUniform("uHighlights", p.highlights)
            program.drawQuad()
        } catch (e: GlUtil.GlException) {
            throw VideoFrameProcessingException(e, presentationTimeUs)
        }
    }

    override fun release() {
        super.release()
        try { program.delete() } catch (e: GlUtil.GlException) { throw VideoFrameProcessingException(e) }
    }
}

// ---------------------------------------------------------------- Detail upscaler / resampler

/**
 * Resizes so the SHORT side becomes [targetShortSide] (orientation independent), keeping the
 * aspect ratio, even dimensions, and never exceeding [maxLongSide].
 */
class DetailScaleEffect(private val targetShortSide: Int, private val maxLongSide: Int) : GlEffect {
    override fun toGlShaderProgram(context: Context, useHdr: Boolean): GlShaderProgram =
        DetailScaleProgram(targetShortSide, maxLongSide, useHdr)

    companion object {
        fun outputSize(inW: Int, inH: Int, targetShortSide: Int, maxLongSide: Int): Size {
            val short = minOf(inW, inH).toFloat()
            val long = maxOf(inW, inH).toFloat()
            var scale = targetShortSide / short
            if (long * scale > maxLongSide) scale = maxLongSide / long
            fun even(v: Float) = ((v / 2f).roundToInt() * 2).coerceAtLeast(2)
            return Size(even(inW * scale), even(inH * scale))
        }
    }
}

private class DetailScaleProgram(
    private val targetShortSide: Int,
    private val maxLongSide: Int,
    useHdr: Boolean,
) : BaseGlShaderProgram(useHdr, 2) {

    private val program = try { newProgram(Shaders.SCALE) } catch (e: Exception) {
        throw VideoFrameProcessingException(e)
    }
    private var inW = 1
    private var inH = 1
    private var out = Size(1, 1)

    override fun configure(inputWidth: Int, inputHeight: Int): Size {
        inW = inputWidth; inH = inputHeight
        out = DetailScaleEffect.outputSize(inputWidth, inputHeight, targetShortSide, maxLongSide)
        return out
    }

    override fun drawFrame(inputTexId: Int, presentationTimeUs: Long) {
        try {
            program.use()
            program.setSamplerTexIdUniform("uTexSampler", inputTexId, 0)
            program.setFloatsUniform("uInSize", floatArrayOf(inW.toFloat(), inH.toFloat()))
            program.setFloatsUniform("uOutTexel", floatArrayOf(1f / out.width, 1f / out.height))
            program.setFloatUniform("uDownscale", if (out.width < inW) 1f else 0f)
            program.drawQuad()
        } catch (e: GlUtil.GlException) {
            throw VideoFrameProcessingException(e, presentationTimeUs)
        }
    }

    override fun release() {
        super.release()
        try { program.delete() } catch (e: GlUtil.GlException) { throw VideoFrameProcessingException(e) }
    }
}

// ---------------------------------------------------------------- Contrast adaptive sharpening

class CasSharpenEffect(private val sharpness: Float) : GlEffect {
    override fun toGlShaderProgram(context: Context, useHdr: Boolean): GlShaderProgram =
        CasProgram(sharpness, useHdr)

    override fun isNoOp(inputWidth: Int, inputHeight: Int) = sharpness <= 0.001f
}

private class CasProgram(private val sharpness: Float, useHdr: Boolean) : BaseGlShaderProgram(useHdr, 2) {
    private val program = try { newProgram(Shaders.CAS) } catch (e: Exception) {
        throw VideoFrameProcessingException(e)
    }
    private var w = 1
    private var h = 1

    override fun configure(inputWidth: Int, inputHeight: Int): Size {
        w = inputWidth; h = inputHeight
        return Size(inputWidth, inputHeight)
    }

    override fun drawFrame(inputTexId: Int, presentationTimeUs: Long) {
        try {
            program.use()
            program.setSamplerTexIdUniform("uTexSampler", inputTexId, 0)
            program.setFloatsUniform("uTexel", floatArrayOf(1f / w, 1f / h))
            program.setFloatUniform("uSharpness", sharpness)
            program.drawQuad()
        } catch (e: GlUtil.GlException) {
            throw VideoFrameProcessingException(e, presentationTimeUs)
        }
    }

    override fun release() {
        super.release()
        try { program.delete() } catch (e: GlUtil.GlException) { throw VideoFrameProcessingException(e) }
    }
}
