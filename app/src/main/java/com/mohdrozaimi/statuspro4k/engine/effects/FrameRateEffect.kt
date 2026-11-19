@file:OptIn(UnstableApi::class)

package com.mohdrozaimi.statuspro4k.engine.effects

import android.content.Context
import androidx.media3.common.GlObjectsProvider
import androidx.media3.common.GlTextureInfo
import androidx.media3.common.VideoFrameProcessingException
import androidx.media3.common.util.GlProgram
import androidx.media3.common.util.GlUtil
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.GlEffect
import androidx.media3.effect.GlShaderProgram
import java.util.concurrent.Executor
import kotlin.math.roundToLong

/**
 * Converts the video to a true Constant Frame Rate of [targetFps].
 *  - Higher target (e.g. 30 -> 60, 24 -> 60, 25 -> 50): new in-between frames are synthesised with
 *    motion-compensated interpolation (see Shaders.INTERPOLATE) — real new frames, not duplicates.
 *  - Lower target (60 -> 30): frames are resampled onto the new time grid.
 *  - Same target on a variable-frame-rate phone video: re-timed to an exact CFR grid.
 */
class FrameRateEffect(private val targetFps: Float, private val motionCompensated: Boolean = true) : GlEffect {
    override fun toGlShaderProgram(context: Context, useHdr: Boolean): GlShaderProgram =
        FrameRateShaderProgram(targetFps, motionCompensated, useHdr)
}

private class FrameRateShaderProgram(
    targetFps: Float,
    private val motionCompensated: Boolean,
    private val useHdr: Boolean,
) : GlShaderProgram {

    private val intervalUs = 1_000_000.0 / targetFps
    private var inputListener: GlShaderProgram.InputListener = object : GlShaderProgram.InputListener {}
    private var outputListener: GlShaderProgram.OutputListener = object : GlShaderProgram.OutputListener {}
    private var errorListener: GlShaderProgram.ErrorListener = GlShaderProgram.ErrorListener { }
    private var errorExecutor: Executor = Executor { it.run() }

    private var interp: GlProgram? = null
    private var copy: GlProgram? = null

    private var width = 0
    private var height = 0
    private val outputs = ArrayList<GlTextureInfo>()
    private val free = ArrayDeque<GlTextureInfo>()
    private var prev: GlTextureInfo? = null

    private var hasPrev = false
    private var prevTs = 0L
    private var baseTs = 0L
    private var slotIndex = 0L

    private var pending: GlTextureInfo? = null
    private var pendingTs = 0L
    private var endPending = false

    override fun setInputListener(listener: GlShaderProgram.InputListener) {
        inputListener = listener
        listener.onReadyToAcceptInputFrame()
    }

    override fun setOutputListener(listener: GlShaderProgram.OutputListener) { outputListener = listener }

    override fun setErrorListener(executor: Executor, listener: GlShaderProgram.ErrorListener) {
        errorExecutor = executor
        errorListener = listener
    }

    override fun queueInputFrame(glObjectsProvider: GlObjectsProvider, inputTexture: GlTextureInfo, presentationTimeUs: Long) {
        try {
            ensureResources(glObjectsProvider, inputTexture.width, inputTexture.height)
            pending = inputTexture
            pendingTs = presentationTimeUs
            drain()
        } catch (e: Exception) {
            reportError(e, presentationTimeUs)
        }
    }

    override fun releaseOutputFrame(outputTexture: GlTextureInfo) {
        val t = outputs.firstOrNull { it.texId == outputTexture.texId } ?: return
        if (!free.contains(t)) free.addLast(t)
        try { drain() } catch (e: Exception) { reportError(e, pendingTs) }
    }

    override fun signalEndOfCurrentInputStream() {
        if (pending != null) {
            endPending = true
        } else {
            endStream()
        }
    }

    override fun flush() {
        pending = null
        endPending = false
        hasPrev = false
        free.clear()
        free.addAll(outputs)
        inputListener.onFlush()
        inputListener.onReadyToAcceptInputFrame()
    }

    override fun release() {
        try {
            interp?.delete(); copy?.delete()
            interp = null; copy = null
            deleteTextures()
        } catch (e: GlUtil.GlException) {
            throw VideoFrameProcessingException(e)
        }
    }

    // ------------------------------------------------------------------------------------------

    private fun endStream() {
        endPending = false
        hasPrev = false
        outputListener.onCurrentOutputStreamEnded()
    }

    private fun reportError(e: Exception, ts: Long) {
        errorExecutor.execute {
            errorListener.onError(
                if (e is VideoFrameProcessingException) e else VideoFrameProcessingException(e, ts),
            )
        }
    }

    private fun ensureResources(provider: GlObjectsProvider, w: Int, h: Int) {
        if (interp == null) {
            interp = newProgram(Shaders.INTERPOLATE)
            copy = newProgram(Shaders.COPY)
        }
        if (w == width && h == height && prev != null) return
        deleteTextures()
        width = w; height = h
        repeat(3) {
            val tex = GlUtil.createTexture(w, h, useHdr)
            val info = provider.createBuffersForTexture(tex, w, h)
            outputs.add(info)
            free.addLast(info)
        }
        val ptex = GlUtil.createTexture(w, h, useHdr)
        prev = provider.createBuffersForTexture(ptex, w, h)
        hasPrev = false
    }

    private fun deleteTextures() {
        for (t in outputs) { GlUtil.deleteFbo(t.fboId); GlUtil.deleteTexture(t.texId) }
        outputs.clear(); free.clear()
        prev?.let { GlUtil.deleteFbo(it.fboId); GlUtil.deleteTexture(it.texId) }
        prev = null
    }

    private fun slotTs(i: Long) = baseTs + (i * intervalUs).roundToLong()

    /** Emits as many output frames as possible for the pending input. */
    private fun drain() {
        val input = pending ?: return
        val prevTex = prev ?: return

        if (!hasPrev) {
            val out = free.removeFirstOrNull() ?: return
            render(copy!!, out) { setSamplerTexIdUniform("uTexSampler", input.texId, 0) }
            baseTs = pendingTs
            slotIndex = 1
            outputListener.onOutputFrameAvailable(out, pendingTs)
            storeAsPrevious(input)
            return
        }

        if (pendingTs <= prevTs) { // out-of-order / duplicate timestamp: drop
            finishInput(input)
            return
        }

        val span = (pendingTs - prevTs).toDouble()
        val tolerance = (intervalUs * 0.25).roundToLong()
        while (true) {
            val ts = slotTs(slotIndex)
            if (ts > pendingTs + tolerance) break
            val out = free.removeFirstOrNull() ?: return // wait for downstream to release a texture
            val alpha = ((ts - prevTs) / span).coerceIn(0.0, 1.0).toFloat()
            when {
                alpha >= 0.97f -> render(copy!!, out) { setSamplerTexIdUniform("uTexSampler", input.texId, 0) }
                alpha <= 0.03f -> render(copy!!, out) { setSamplerTexIdUniform("uTexSampler", prevTex.texId, 0) }
                !motionCompensated -> render(copy!!, out) {
                    setSamplerTexIdUniform("uTexSampler", if (alpha < 0.5f) prevTex.texId else input.texId, 0)
                }
                else -> render(interp!!, out) {
                    setSamplerTexIdUniform("uPrev", prevTex.texId, 0)
                    setSamplerTexIdUniform("uCur", input.texId, 1)
                    setFloatsUniform("uTexel", floatArrayOf(1f / width, 1f / height))
                    setFloatUniform("uAlpha", alpha)
                    setFloatUniform("uMotionScale", (minOf(width, height) / 1080f).coerceIn(0.5f, 2f))
                }
            }
            outputListener.onOutputFrameAvailable(out, ts)
            slotIndex++
        }
        storeAsPrevious(input)
    }

    private fun storeAsPrevious(input: GlTextureInfo) {
        val p = prev!!
        render(copy!!, p) { setSamplerTexIdUniform("uTexSampler", input.texId, 0) }
        prevTs = pendingTs
        hasPrev = true
        finishInput(input)
    }

    private fun finishInput(input: GlTextureInfo) {
        pending = null
        inputListener.onInputFrameProcessed(input)
        inputListener.onReadyToAcceptInputFrame()
        if (endPending) endStream()
    }

    private inline fun render(program: GlProgram, target: GlTextureInfo, setup: GlProgram.() -> Unit) {
        GlUtil.focusFramebufferUsingCurrentContext(target.fboId, target.width, target.height)
        program.use()
        program.setup()
        program.drawQuad()
    }
}
