package com.mohdrozaimi.statuspro4k.ui

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mohdrozaimi.statuspro4k.data.RecentItem
import com.mohdrozaimi.statuspro4k.data.RecentStore
import com.mohdrozaimi.statuspro4k.engine.DeviceCapability
import com.mohdrozaimi.statuspro4k.engine.DeviceCapabilityDetector
import com.mohdrozaimi.statuspro4k.engine.ExportConfig
import com.mohdrozaimi.statuspro4k.engine.ExportMode
import com.mohdrozaimi.statuspro4k.engine.ExportPlan
import com.mohdrozaimi.statuspro4k.engine.ExportPlanner
import com.mohdrozaimi.statuspro4k.engine.ProcessingResult
import com.mohdrozaimi.statuspro4k.engine.TargetResolution
import com.mohdrozaimi.statuspro4k.engine.VideoAnalyzer
import com.mohdrozaimi.statuspro4k.engine.VideoInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** What the "Complete" page shows: a fresh result, or a recent video opened from Home. */
data class CompletedView(
    val original: VideoInfo?,
    val output: VideoInfo,
    val file: File,
    val plan: ExportPlan?,
    val resolutionVerified: Boolean?,
    val fpsVerified: Boolean?,
    val elapsedMs: Long?,
    val log: List<String>,
)

class AppViewModel(app: Application) : AndroidViewModel(app) {

    val capability: DeviceCapability by lazy { DeviceCapabilityDetector.detect(app) }

    var source by mutableStateOf<VideoInfo?>(null)
        private set
    var config by mutableStateOf(ExportConfig())
    var analyzing by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
    var recents by mutableStateOf<List<RecentItem>>(emptyList())
        private set
    var completed by mutableStateOf<CompletedView?>(null)

    fun refreshRecents() {
        viewModelScope.launch {
            recents = withContext(Dispatchers.IO) { RecentStore.load(getApplication()) }
        }
    }

    fun plan(): ExportPlan? = source?.let { ExportPlanner.plan(it, config, capability) }

    fun load(uri: Uri, mode: ExportMode, onReady: () -> Unit) {
        analyzing = true
        error = null
        viewModelScope.launch {
            val info = withContext(Dispatchers.IO) {
                runCatching { VideoAnalyzer.analyze(getApplication(), uri) }
            }
            analyzing = false
            info.onSuccess { v ->
                if (v.width <= 0 || v.height <= 0) {
                    error = "This file has no readable video track."
                    return@onSuccess
                }
                source = v
                config = defaultConfig(v, mode)
                onReady()
            }.onFailure { error = "Can't read this video: ${it.message}" }
        }
    }

    private fun defaultConfig(v: VideoInfo, mode: ExportMode): ExportConfig {
        val cap = capability
        val res = when {
            mode == ExportMode.WHATSAPP_OPTIMIZED -> TargetResolution.P1080
            cap.supports4k30 -> TargetResolution.K4
            cap.supports1440p60 -> TargetResolution.K2
            else -> TargetResolution.P1080
        }
        val fps = if (cap.supports1080p60) 60 else 30
        return ExportConfig(mode = mode, resolution = res, fps = fps)
    }

    fun openResult(r: ProcessingResult) {
        completed = CompletedView(r.original, r.output, r.outputFile, r.plan, r.resolutionVerified, r.fpsVerified, r.elapsedMs, r.log)
    }

    fun openRecent(item: RecentItem, onReady: () -> Unit) {
        viewModelScope.launch {
            val out = withContext(Dispatchers.IO) { runCatching { VideoAnalyzer.analyze(getApplication(), Uri.fromFile(item.file)) }.getOrNull() }
            if (out != null) {
                completed = CompletedView(null, out, item.file, null, null, null, null, emptyList())
                onReady()
            }
        }
    }

    fun deleteRecent(item: RecentItem) {
        RecentStore.remove(getApplication(), item)
        refreshRecents()
    }
}
