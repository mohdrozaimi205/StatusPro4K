package com.mohdrozaimi.statuspro4k.ui.screens

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mohdrozaimi.statuspro4k.engine.DeviceCapabilityDetector
import com.mohdrozaimi.statuspro4k.engine.EffectChain
import com.mohdrozaimi.statuspro4k.engine.ExportMode
import com.mohdrozaimi.statuspro4k.engine.FPS_OPTIONS
import com.mohdrozaimi.statuspro4k.engine.TargetResolution
import com.mohdrozaimi.statuspro4k.engine.formatBitrate
import com.mohdrozaimi.statuspro4k.engine.formatDuration
import com.mohdrozaimi.statuspro4k.engine.formatSize
import com.mohdrozaimi.statuspro4k.ui.AppViewModel
import com.mohdrozaimi.statuspro4k.ui.components.Chip
import com.mohdrozaimi.statuspro4k.ui.components.FrameImage
import com.mohdrozaimi.statuspro4k.ui.components.KeyValue
import com.mohdrozaimi.statuspro4k.ui.components.Panel
import com.mohdrozaimi.statuspro4k.ui.components.PrimaryButton
import com.mohdrozaimi.statuspro4k.ui.components.ProSlider
import com.mohdrozaimi.statuspro4k.ui.components.ProSwitch
import com.mohdrozaimi.statuspro4k.ui.components.SectionLabel
import com.mohdrozaimi.statuspro4k.ui.components.Segmented
import com.mohdrozaimi.statuspro4k.ui.components.TopBar
import com.mohdrozaimi.statuspro4k.ui.components.VSpace
import com.mohdrozaimi.statuspro4k.ui.components.VideoPlayer
import com.mohdrozaimi.statuspro4k.ui.components.Viewfinder
import com.mohdrozaimi.statuspro4k.ui.components.rememberFrame
import com.mohdrozaimi.statuspro4k.ui.theme.Pro
import kotlin.math.roundToInt

// ===================================================================================== HOME

@Composable
fun HomeScreen(vm: AppViewModel, onPick: (ExportMode) -> Unit, onOpenRecent: () -> Unit) {
    val ctx = LocalContext.current
    LaunchedEffect(Unit) { vm.refreshRecents() }
    val cap = vm.capability
    val thermal = remember { DeviceCapabilityDetector.thermalLabel(DeviceCapabilityDetector.thermalStatus(ctx)) }

    Column(
        Modifier.fillMaxSize().background(Pro.Black).statusBarsPadding().navigationBarsPadding()
            .verticalScroll(rememberScrollState()).padding(horizontal = 18.dp),
    ) {
        Row(Modifier.fillMaxWidth().padding(top = 18.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("STATUSPRO", style = Pro.title.copy(letterSpacing = 4.sp, fontSize = 18.sp))
            Spacer(Modifier.width(8.dp))
            Box(Modifier.clip(RoundedCornerShape(3.dp)).background(Pro.Blue).padding(horizontal = 6.dp, vertical = 2.dp)) {
                Text("4K", style = Pro.mono.copy(fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color.White))
            }
            Spacer(Modifier.weight(1f))
            Text("REC ●", style = Pro.mono.copy(fontSize = 11.sp, color = Pro.Red))
        }
        Text("Prepare studio-grade video for WhatsApp Status", style = Pro.body, modifier = Modifier.padding(top = 4.dp))

        // Device strip
        Row(Modifier.fillMaxWidth().padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Chip(cap.tierLabel.uppercase())
            Chip(if (cap.isHardwareEncoder) "HW ENCODER" else "SW ENCODER", if (cap.isHardwareEncoder) Pro.Green else Pro.Amber)
            Chip("TEMP ${thermal.uppercase()}", if (thermal == "Normal" || thermal == "Warm") Pro.Soft else Pro.Amber)
        }

        // Select video — viewfinder
        VSpace(18.dp)
        Viewfinder(Modifier.fillMaxWidth().aspectRatio(1.25f)) {
            Box(
                Modifier.fillMaxSize().padding(6.dp).clip(RoundedCornerShape(2.dp))
                    .background(Brush.verticalGradient(listOf(Pro.Panel2, Pro.Black)))
                    .clickable { onPick(ExportMode.WHATSAPP_OPTIMIZED) },
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.size(64.dp).clip(RoundedCornerShape(32.dp)).border(1.5.dp, Pro.Blue, RoundedCornerShape(32.dp)), contentAlignment = Alignment.Center) {
                        Icon(Icons.Filled.Add, null, tint = Pro.Blue, modifier = Modifier.size(30.dp))
                    }
                    VSpace(14.dp)
                    Text("SELECT VIDEO", style = Pro.label.copy(color = Pro.White, fontSize = 13.sp, letterSpacing = 3.sp))
                    Text("720p · 1080p · 2K · 4K  |  24 – 60 FPS", style = Pro.mono.copy(fontSize = 11.sp, color = Pro.Dim), modifier = Modifier.padding(top = 6.dp))
                }
                Text("ISO AUTO   WB AUTO   ${cap.maxShortSide}p MAX", style = Pro.mono.copy(fontSize = 9.sp, color = Pro.Dim),
                    modifier = Modifier.align(Alignment.BottomStart).padding(12.dp))
            }
        }

        // Two modes
        SectionLabel("Modes")
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ModeTile(Modifier.weight(1f), "Video Enhancement", "Upscale · 60 FPS · sharpen · colour", Icons.Filled.AutoAwesome) {
                onPick(ExportMode.MAXIMUM_QUALITY)
            }
            ModeTile(Modifier.weight(1f), "WhatsApp Status Export", "Status-tuned H.264 export", Icons.Filled.Bolt) {
                onPick(ExportMode.WHATSAPP_OPTIMIZED)
            }
        }

        // Recent
        SectionLabel("Recent Videos")
        if (vm.recents.isEmpty()) {
            Text("Enhanced videos will appear here.", style = Pro.body.copy(color = Pro.Dim))
        } else {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                items(vm.recents, key = { it.file.absolutePath }) { item ->
                    val bmp = rememberFrame(Uri.fromFile(item.file), 500_000L, 360)
                    Column(Modifier.width(108.dp).clickable { vm.openRecent(item, onOpenRecent) }) {
                        FrameImage(bmp, Modifier.width(108.dp).height(150.dp).clip(RoundedCornerShape(3.dp)))
                        Text(item.sourceName, style = Pro.body.copy(fontSize = 10.sp), maxLines = 1, modifier = Modifier.padding(top = 4.dp))
                        Text(formatSize(item.file.length()), style = Pro.mono.copy(fontSize = 10.sp, color = Pro.Dim))
                    }
                }
            }
        }

        if (vm.analyzing) {
            Row(Modifier.padding(top = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(color = Pro.Blue, strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(10.dp))
                Text("Analysing video…", style = Pro.body)
            }
        }
        vm.error?.let { Text(it, style = Pro.body.copy(color = Pro.Red), modifier = Modifier.padding(top = 16.dp)) }
        VSpace(30.dp)
    }
}

@Composable
private fun ModeTile(modifier: Modifier, title: String, sub: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    Column(
        modifier.clip(RoundedCornerShape(4.dp)).background(Pro.Panel).border(1.dp, Pro.Line, RoundedCornerShape(4.dp))
            .clickable(onClick = onClick).padding(14.dp),
    ) {
        Icon(icon, null, tint = Pro.Blue, modifier = Modifier.size(22.dp))
        VSpace(18.dp)
        Text(title, style = Pro.body.copy(color = Pro.White, fontWeight = FontWeight.SemiBold, fontSize = 14.sp))
        Text(sub, style = Pro.body.copy(fontSize = 11.sp, color = Pro.Dim), modifier = Modifier.padding(top = 3.dp))
    }
}

// =================================================================================== EDITOR

@Composable
fun EditorScreen(vm: AppViewModel, onBack: () -> Unit, onNext: () -> Unit) {
    val src = vm.source ?: return
    val cfg = vm.config
    val cap = vm.capability
    var showEnhanced by remember { mutableStateOf(true) }
    // Live preview re-applies effects when a slider is released (not on every drag tick).
    var appliedKey by remember { mutableIntStateOf(0) }
    val plan = vm.plan()

    Column(Modifier.fillMaxSize().background(Pro.Black).statusBarsPadding().navigationBarsPadding()) {
        TopBar("Video Editor", onBack) { Chip(src.resolutionLabel + " · " + src.fps.roundToInt() + "fps") ; Spacer(Modifier.width(12.dp)) }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 18.dp)) {

            // Preview
            Viewfinder(Modifier.fillMaxWidth().aspectRatio(if (src.isPortrait) 0.8f else 16f / 9f), color = if (showEnhanced) Pro.Blue else Pro.White) {
                Box(Modifier.fillMaxSize().padding(4.dp)) {
                    val effects = if (showEnhanced) EffectChain.build(cfg.enhance, null, src.shortSide) else null
                    VideoPlayer(src.uri, Modifier.fillMaxSize(), effects = effects, effectsKey = showEnhanced to appliedKey, muted = true)
                    Text(if (showEnhanced) "ENHANCED PREVIEW" else "ORIGINAL PREVIEW", style = Pro.mono.copy(fontSize = 10.sp, color = if (showEnhanced) Pro.Blue else Pro.White),
                        modifier = Modifier.align(Alignment.TopStart).padding(10.dp).background(Color(0x99000000)).padding(horizontal = 6.dp, vertical = 2.dp))
                }
            }
            VSpace(10.dp)
            Segmented(listOf(false, true), showEnhanced, { if (it) "ENHANCED" else "ORIGINAL" }) { showEnhanced = it }
            Text("Live preview shows sharpening, noise reduction and colour. Upscaling & 60 FPS are applied during export.",
                style = Pro.body.copy(fontSize = 11.sp, color = Pro.Dim), modifier = Modifier.padding(top = 6.dp))

            // Analyzer
            SectionLabel("Video Quality Analyzer")
            Panel {
                Column {
                    KeyValue("Resolution", "${src.displayWidth}×${src.displayHeight} (${src.resolutionLabel})")
                    KeyValue("Frame rate", "${"%.2f".format(src.fps)} FPS" + if (src.fpsIsVariable) " · VFR" else " · CFR")
                    KeyValue("Bitrate", formatBitrate(src.videoBitrate))
                    KeyValue("Video codec", src.videoCodec + if (src.isHdr) " · HDR" else "")
                    KeyValue("Audio codec", src.audioCodec?.let { "$it · ${src.audioSampleRate / 1000} kHz · ${src.audioChannels}ch" } ?: "No audio")
                    KeyValue("Duration", formatDuration(src.durationMs))
                    KeyValue("File size", if (src.fileSizeBytes > 0) formatSize(src.fileSizeBytes) else "—")
                    KeyValue("Orientation", src.orientationLabel + if (src.rotation != 0) " (${src.rotation}°)" else "")
                    if (plan != null) KeyValue("Estimated output", plan.label, Pro.Blue)
                }
            }

            // Resolution
            SectionLabel("Resolution")
            val resOptions = listOf(TargetResolution.P720, TargetResolution.P1080, TargetResolution.K2, TargetResolution.K4)
            Segmented(resOptions, if (cfg.mode == ExportMode.WHATSAPP_OPTIMIZED) TargetResolution.P1080 else cfg.resolution, { it.label },
                enabled = { cfg.mode == ExportMode.MAXIMUM_QUALITY && it.shortSide <= cap.maxShortSide }) {
                vm.config = cfg.copy(resolution = it)
            }
            if (cfg.mode == ExportMode.WHATSAPP_OPTIMIZED) Text("WhatsApp Optimized uses 1080p. Switch to Maximum Quality on the Export page for 2K / 4K.",
                style = Pro.body.copy(fontSize = 11.sp, color = Pro.Dim), modifier = Modifier.padding(top = 6.dp))

            // FPS
            SectionLabel("Frame Rate")
            Segmented(FPS_OPTIONS, cfg.fps, { if (it == 0) "SRC" else "$it" }) { vm.config = cfg.copy(fps = it) }

            // Enhancement
            SectionLabel("Enhancement")
            Panel {
                Column {
                    ProSlider("Sharpness", cfg.enhance.sharpness, { vm.config = cfg.copy(enhance = cfg.enhance.copy(sharpness = it)) }) { appliedKey++ }
                    ProSlider("Noise Reduction", cfg.enhance.noiseReduction, { vm.config = cfg.copy(enhance = cfg.enhance.copy(noiseReduction = it)) }) { appliedKey++ }
                    ProSlider("Colour Enhancement", cfg.enhance.colour, { vm.config = cfg.copy(enhance = cfg.enhance.copy(colour = it)) }) { appliedKey++ }
                    ProSwitch("Motion interpolation", "Create real in-between frames when raising FPS",
                        cfg.enhance.motionInterpolation) { vm.config = cfg.copy(enhance = cfg.enhance.copy(motionInterpolation = it)) }
                }
            }
            VSpace(20.dp)
        }
        Box(Modifier.padding(18.dp)) {
            PrimaryButton("Next · Export settings", icon = Icons.Filled.ArrowForward, onClick = onNext)
        }
    }
}
