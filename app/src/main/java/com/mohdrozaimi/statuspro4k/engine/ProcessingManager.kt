package com.mohdrozaimi.statuspro4k.engine

import android.content.Context
import android.net.Uri
import android.os.PowerManager
import androidx.core.content.ContextCompat
import com.mohdrozaimi.statuspro4k.data.RecentStore
import com.mohdrozaimi.statuspro4k.service.ProcessingService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.coroutines.resume
import kotlin.math.abs
import kotlin.math.roundToInt

data class ProcessingResult(
    val original: VideoInfo,
    val output: VideoInfo,
    val outputFile: File,
    val plan: ExportPlan,
    val elapsedMs: Long,
    val resolutionVerified: Boolean,
    val fpsVerified: Boolean,
    val log: List<String>,
)

sealed interface JobState {
    data object Idle : JobState
    data class Running(
        val percent: Int,
        val stage: String,
        val attempt: Int,
        val planLabel: String,
        val thermal: String,
        val log: List<String>,
    ) : JobState
    data class Done(val result: ProcessingResult) : JobState
    data class Failed(val message: String, val log: List<String>) : JobState
}

/**
 * Runs one export at a time with automatic recovery:
 *  - encoder / GPU errors -> retry with the next lower plan on the fallback ladder
 *  - device overheating (thermal SEVERE) -> cancel and restart at a lighter plan
 * and finally verifies the real resolution & frame rate of the produced file.
 */
object ProcessingManager {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val _state = MutableStateFlow<JobState>(JobState.Idle)
    val state: StateFlow<JobState> = _state

    private var job: Job? = null
    private var enhancer: VideoEnhancer? = null
    @Volatile private var userCancelled = false

    fun reset() { if (_state.value !is JobState.Running) _state.value = JobState.Idle }

    fun start(context: Context, src: VideoInfo, cfg: ExportConfig) {
        if (_state.value is JobState.Running) return
        val app = context.applicationContext
        userCancelled = false
        ContextCompat.startForegroundService(app, ProcessingService.intent(app))
        job = scope.launch {
            val log = mutableListOf<String>()
            val t0 = System.currentTimeMillis()
            try {
                val result = run(app, src, cfg, log, t0)
                _state.value = if (result != null) JobState.Done(result)
                else JobState.Failed(if (userCancelled) "Cancelled" else "Processing failed on all quality levels.", log)
            } catch (e: Exception) {
                _state.value = JobState.Failed(e.message ?: e.toString(), log)
            } finally {
                ProcessingService.stop(app)
            }
        }
    }

    fun cancel() {
        userCancelled = true
        enhancer?.cancel()
        job?.cancel()
        _state.value = JobState.Failed("Cancelled", emptyList())
    }

    private suspend fun run(ctx: Context, src: VideoInfo, cfg: ExportConfig, log: MutableList<String>, t0: Long): ProcessingResult? {
        val cap = DeviceCapabilityDetector.detect(ctx)
        val first = ExportPlanner.plan(src, cfg, cap)
        val attempts = listOf(first) + ExportPlanner.fallbacks(first).map { (s, f) ->
            ExportPlanner.plan(src, cfg, cap, forceShortSide = s, forceFps = f)
        }.distinctBy { it.shortSide to it.fps }

        // Thermal-aware start: if already hot, skip the heaviest plans.
        var startIdx = 0
        val thermalNow = DeviceCapabilityDetector.thermalStatus(ctx)
        if (thermalNow >= PowerManager.THERMAL_STATUS_MODERATE && attempts.size > 1 && first.shortSide >= 2160 && first.fps > 30) {
            startIdx = 1
            log += "Phone is ${DeviceCapabilityDetector.thermalLabel(thermalNow)} — starting with a lighter plan."
        }

        val exportsDir = File(ctx.filesDir, "exports").apply { mkdirs() }
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())

        for (i in startIdx until attempts.size) {
            if (userCancelled) return null
            val plan = attempts[i]
            log += "Attempt ${i + 1}: ${plan.label}"
            val tmp = File(ctx.cacheDir, "render_$stamp.mp4")
            val outcome = runOnce(ctx, src, cfg, plan, tmp, i + 1, log)
            when (outcome) {
                is Outcome.Ok -> {
                    publish(97, "Finalising MP4 (fast-start)", i + 1, plan, ctx, log)
                    val final = File(exportsDir, "StatusPro4K_$stamp.mp4")
                    withContext(Dispatchers.IO) {
                        val moved = runCatching { FastStart.process(tmp, final) }.getOrDefault(false)
                        if (!moved) { final.delete(); tmp.copyTo(final, overwrite = true) }
                        tmp.delete()
                    }
                    publish(99, "Verifying output", i + 1, plan, ctx, log)
                    val out = withContext(Dispatchers.IO) { VideoAnalyzer.analyze(ctx, Uri.fromFile(final)) }
                    val resOk = abs(out.shortSide - plan.shortSide) <= 16
                    val fpsOk = abs(out.fps - plan.fps) < 1.5f
                    log += "Verified output: ${out.width}×${out.height}, ${"%.2f".format(out.fps)} FPS, ${formatBitrate(out.videoBitrate)}"
                    if (!resOk) log += "Note: encoder delivered ${out.shortSide}p instead of ${plan.shortSide}p."
                    if (!fpsOk) log += "Note: measured ${"%.1f".format(out.fps)} FPS vs planned ${plan.fps.roundToInt()}."
                    RecentStore.add(ctx, final, src.displayName)
                    return ProcessingResult(src, out, final, plan, System.currentTimeMillis() - t0, resOk, fpsOk, log.toList())
                }
                is Outcome.Error -> {
                    tmp.delete()
                    log += "Failed: ${outcome.message}"
                    if (i + 1 < attempts.size) log += "Auto-recovery: retrying with a lighter plan…"
                }
                Outcome.Overheated -> {
                    tmp.delete()
                    log += "Thermal protection: phone too hot, restarting with a lighter plan…"
                }
                Outcome.Cancelled -> { tmp.delete(); return null }
            }
        }
        return null
    }

    private sealed interface Outcome {
        data object Ok : Outcome
        data class Error(val message: String) : Outcome
        data object Overheated : Outcome
        data object Cancelled : Outcome
    }

    private suspend fun runOnce(
        ctx: Context, src: VideoInfo, cfg: ExportConfig, plan: ExportPlan, tmp: File, attempt: Int, log: List<String>,
    ): Outcome = suspendCancellableCoroutine { cont ->
        val pm = ctx.getSystemService(Context.POWER_SERVICE) as PowerManager
        val e = VideoEnhancer(ctx)
        enhancer = e
        var finished = false
        lateinit var thermalListener: PowerManager.OnThermalStatusChangedListener
        fun finish(o: Outcome) {
            if (finished) return
            finished = true
            runCatching { pm.removeThermalStatusListener(thermalListener) }
            enhancer = null
            if (cont.isActive) cont.resume(o)
        }
        thermalListener = PowerManager.OnThermalStatusChangedListener { status ->
            if (status >= PowerManager.THERMAL_STATUS_SEVERE && !finished) {
                e.cancel()
                finish(Outcome.Overheated)
            }
        }
        pm.addThermalStatusListener(ContextCompat.getMainExecutor(ctx), thermalListener)
        publish(0, stageFor(plan, src), attempt, plan, ctx, log)
        e.start(src, cfg.enhance, plan, tmp, object : VideoEnhancer.Callback {
            override fun onProgress(percent: Int) {
                publish((percent * 0.96f).roundToInt(), stageFor(plan, src), attempt, plan, ctx, log)
            }
            override fun onCompleted(result: ExportResult) = finish(Outcome.Ok)
            override fun onError(e: ExportException) {
                finish(if (userCancelled) Outcome.Cancelled else Outcome.Error("${e.errorCodeName}: ${e.message}"))
            }
        })
        cont.invokeOnCancellation { e.cancel(); finish(Outcome.Cancelled) }
    }

    private fun stageFor(plan: ExportPlan, src: VideoInfo): String = buildString {
        append("Enhancing")
        if (plan.shortSide > src.shortSide) append(" · upscaling to ${plan.resolutionLabel}")
        if (plan.fps > src.fps + 0.5f) append(" · interpolating to ${plan.fps.roundToInt()} FPS")
    }

    private fun publish(p: Int, stage: String, attempt: Int, plan: ExportPlan, ctx: Context, log: List<String>) {
        val th = DeviceCapabilityDetector.thermalLabel(DeviceCapabilityDetector.thermalStatus(ctx))
        _state.value = JobState.Running(p.coerceIn(0, 100), stage, attempt, plan.label, th, log.toList())
        ProcessingService.update(ctx, p, stage)
    }
}
