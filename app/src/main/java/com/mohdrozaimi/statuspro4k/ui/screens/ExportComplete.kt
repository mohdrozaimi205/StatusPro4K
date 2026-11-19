package com.mohdrozaimi.statuspro4k.ui.screens

import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Compare
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mohdrozaimi.statuspro4k.engine.ExportConfig
import com.mohdrozaimi.statuspro4k.engine.ExportMode
import com.mohdrozaimi.statuspro4k.engine.JobState
import com.mohdrozaimi.statuspro4k.engine.ProcessingManager
import com.mohdrozaimi.statuspro4k.engine.formatBitrate
import com.mohdrozaimi.statuspro4k.engine.formatDuration
import com.mohdrozaimi.statuspro4k.engine.formatSize
import com.mohdrozaimi.statuspro4k.share.ShareHelper
import com.mohdrozaimi.statuspro4k.ui.AppViewModel
import com.mohdrozaimi.statuspro4k.ui.components.Chip
import com.mohdrozaimi.statuspro4k.ui.components.FrameImage
import com.mohdrozaimi.statuspro4k.ui.components.GhostButton
import com.mohdrozaimi.statuspro4k.ui.components.KeyValue
import com.mohdrozaimi.statuspro4k.ui.components.Panel
import com.mohdrozaimi.statuspro4k.ui.components.PrimaryButton
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

// =================================================================================== EXPORT

@Composable
fun ExportScreen(vm: AppViewModel, onBack: () -> Unit, onStart: () -> Unit) {
    val src = vm.source ?: return
    val cfg = vm.config
    val plan = vm.plan() ?: return
    val bitrateOptions = listOf(0, 8_000_000, 16_000_000, 40_000_000, 80_000_000)

    Column(Modifier.fillMaxSize().background(Pro.Black).statusBarsPadding().navigationBarsPadding()) {
        TopBar("Export", onBack)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 18.dp)) {
            SectionLabel("Export mode")
            ExportMode.entries.forEachIndexed { i, m ->
                val sel = cfg.mode == m
                Row(
                    Modifier.fillMaxWidth().padding(bottom = 8.dp).clip(RoundedCornerShape(4.dp))
                        .background(if (sel) Pro.BlueGlow else Pro.Panel)
                        .border(1.dp, if (sel) Pro.Blue else Pro.Line, RoundedCornerShape(4.dp))
                        .clickable { vm.config = cfg.copy(mode = m, bitrate = 0) }.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(if (i == 0) "A" else "B", style = Pro.monoBig.copy(color = if (sel) Pro.Blue else Pro.Dim))
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(m.title, style = Pro.body.copy(color = Pro.White, fontWeight = FontWeight.SemiBold, fontSize = 14.sp))
                        Text(m.subtitle, style = Pro.body.copy(fontSize = 11.sp, color = Pro.Dim))
                    }
                    if (sel) Icon(Icons.Filled.Done, null, tint = Pro.Blue)
                }
            }

            SectionLabel("Output")
            Panel {
                Column {
                    KeyValue("Export resolution", "${plan.outWidth}×${plan.outHeight} (${plan.resolutionLabel})", Pro.Blue)
                    KeyValue("Export FPS", "${plan.fps.roundToInt()} FPS · CFR", Pro.Blue)
                    KeyValue("Bitrate", formatBitrate(plan.videoBitrate) + if (cfg.bitrate == 0) " · auto" else "")
                    KeyValue("Codec", "H.264 High · AAC")
                    KeyValue("Keyframe interval", "${plan.keyframeIntervalSec.roundToInt()} s")
                    KeyValue("Container", "MP4 · fast-start")
                    KeyValue("Estimated file size", formatSize(plan.estimatedBytes))
                }
            }

            SectionLabel("Bitrate")
            Segmented(bitrateOptions, cfg.bitrate, { if (it == 0) "AUTO" else "${it / 1_000_000}M" },
                enabled = { it <= vm.capability.maxBitrate }) { vm.config = cfg.copy(bitrate = it) }

            if (cfg.mode == ExportMode.WHATSAPP_OPTIMIZED) {
                VSpace(8.dp)
                ProSwitch("Trim to 90 s", "WhatsApp Status plays up to 90 seconds per video",
                    cfg.trimToStatusLimit) { vm.config = cfg.copy(trimToStatusLimit = it) }
            }

            if (plan.notes.isNotEmpty()) {
                SectionLabel("Engine notes")
                plan.notes.forEach { Text("› $it", style = Pro.body.copy(fontSize = 12.sp), modifier = Modifier.padding(bottom = 4.dp)) }
            }
            SectionLabel("About WhatsApp")
            Text(
                "WhatsApp re-compresses every Status on its own servers, so the viewer will not receive the full 4K 60 FPS file. " +
                    "This export gives WhatsApp the cleanest, sharpest, artifact-free source so the most quality survives its compression. " +
                    "Use Maximum Quality to keep a 4K master in your gallery.",
                style = Pro.body.copy(fontSize = 12.sp, color = Pro.Dim),
            )
            VSpace(20.dp)
        }
        Box(Modifier.padding(18.dp)) {
            PrimaryButton("Start processing", icon = Icons.Filled.PlayArrow) {
                ProcessingManager.start(vm.getApplication<android.app.Application>(), src, cfg)
                onStart()
            }
        }
    }
}

// =============================================================================== PROCESSING

@Composable
fun ProcessingScreen(vm: AppViewModel, onDone: () -> Unit, onBack: () -> Unit) {
    val state by ProcessingManager.state.collectAsState()
    LaunchedEffect(state) {
        val s = state
        if (s is JobState.Done) { vm.openResult(s.result); onDone() }
    }
    Column(
        Modifier.fillMaxSize().background(Pro.Black).statusBarsPadding().navigationBarsPadding().padding(18.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        TopBar("Processing")
        when (val s = state) {
            is JobState.Running -> {
                VSpace(30.dp)
                ProgressRing(s.percent / 100f)
                VSpace(24.dp)
                Text(s.stage, style = Pro.body.copy(color = Pro.White))
                Text(s.planLabel, style = Pro.mono.copy(color = Pro.Blue, fontSize = 12.sp), modifier = Modifier.padding(top = 6.dp))
                Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Chip("PASS ${s.attempt}")
                    Chip("TEMP ${s.thermal.uppercase()}", if (s.thermal == "Normal" || s.thermal == "Warm") Pro.Soft else Pro.Amber)
                    Chip("GPU + MEDIACODEC", Pro.Green)
                }
                VSpace(20.dp)
                Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())) {
                    s.log.forEach { Text("› $it", style = Pro.mono.copy(fontSize = 11.sp, color = Pro.Dim)) }
                    Text("You can leave the app — processing continues in the background.", style = Pro.body.copy(fontSize = 11.sp, color = Pro.Dim), modifier = Modifier.padding(top = 10.dp))
                }
                GhostButton("Cancel", accent = Pro.Red) { ProcessingManager.cancel() }
            }
            is JobState.Failed -> {
                VSpace(40.dp)
                Text(if (s.message == "Cancelled") "PROCESSING CANCELLED" else "PROCESSING FAILED", style = Pro.label.copy(color = Pro.Red, fontSize = 13.sp))
                Text(s.message, style = Pro.body, modifier = Modifier.padding(top = 10.dp))
                Column(Modifier.weight(1f).fillMaxWidth().padding(top = 16.dp).verticalScroll(rememberScrollState())) {
                    s.log.forEach { Text("› $it", style = Pro.mono.copy(fontSize = 11.sp, color = Pro.Dim)) }
                }
                PrimaryButton("Back") { ProcessingManager.reset(); onBack() }
            }
            else -> { VSpace(60.dp); Text("Preparing…", style = Pro.body) }
        }
    }
}

@Composable
private fun ProgressRing(p: Float) {
    Box(Modifier.size(200.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = 3.dp.toPx()
            drawArc(Pro.Line, 0f, 360f, false, style = Stroke(stroke))
            drawArc(Pro.Blue, -90f, 360f * p, false, style = Stroke(stroke, cap = StrokeCap.Round))
            // tick marks like a lens ring
            for (i in 0 until 60) {
                val a = Math.toRadians(i * 6.0 - 90)
                val r1 = size.minDimension / 2 - 12.dp.toPx()
                val r2 = r1 - if (i % 5 == 0) 8.dp.toPx() else 4.dp.toPx()
                val c = center
                drawLine(if (i / 60f <= p) Pro.Blue else Pro.Line,
                    Offset(c.x + (r1 * kotlin.math.cos(a)).toFloat(), c.y + (r1 * kotlin.math.sin(a)).toFloat()),
                    Offset(c.x + (r2 * kotlin.math.cos(a)).toFloat(), c.y + (r2 * kotlin.math.sin(a)).toFloat()), 1.dp.toPx())
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("${(p * 100).roundToInt()}", style = Pro.monoBig.copy(fontSize = 44.sp))
            Text("PERCENT", style = Pro.label)
        }
    }
}

// ================================================================================= COMPLETE

@Composable
fun CompleteScreen(vm: AppViewModel, onHome: () -> Unit, onPreview: () -> Unit, onCompare: () -> Unit) {
    val ctx = LocalContext.current
    val c = vm.completed ?: return
    val out = c.output
    val hasWa = remember { ShareHelper.isInstalled(ctx, ShareHelper.WHATSAPP) }
    val hasWaB = remember { ShareHelper.isInstalled(ctx, ShareHelper.WHATSAPP_BUSINESS) }
    var saved by remember { mutableStateOf(false) }
    val thumb = rememberFrame(Uri.fromFile(c.file), 1_000_000L, 720)

    Column(Modifier.fillMaxSize().background(Pro.Black).statusBarsPadding().navigationBarsPadding()) {
        TopBar(if (c.original != null) "Complete" else "Recent video", onHome)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 18.dp)) {
            if (c.original != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(28.dp).clip(RoundedCornerShape(14.dp)).background(Pro.Green), contentAlignment = Alignment.Center) {
                        Icon(Icons.Filled.Done, null, tint = Color.Black, modifier = Modifier.size(18.dp))
                    }
                    Spacer(Modifier.width(10.dp))
                    Text("PROCESSING COMPLETED", style = Pro.label.copy(color = Pro.White, fontSize = 13.sp))
                }
                c.elapsedMs?.let { Text("Finished in ${formatDuration(it)}", style = Pro.body.copy(fontSize = 11.sp), modifier = Modifier.padding(top = 4.dp)) }
                VSpace(14.dp)
            }
            Viewfinder(Modifier.fillMaxWidth().aspectRatio(if (out.isPortrait) 1f else 16f / 9f), color = Pro.Blue) {
                FrameImage(thumb, Modifier.fillMaxSize().padding(4.dp).clickable { onPreview() }, ContentScale.Crop)
            }

            SectionLabel("Video quality information")
            Panel {
                Column {
                    if (c.original != null) {
                        Text("ORIGINAL", style = Pro.label)
                        Text(c.original.summary(), style = Pro.mono.copy(fontSize = 15.sp), modifier = Modifier.padding(top = 2.dp, bottom = 10.dp))
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("ENHANCED · MEASURED", style = Pro.label.copy(color = Pro.Blue))
                        Spacer(Modifier.width(8.dp))
                        if (c.resolutionVerified == true && c.fpsVerified == true) Chip("VERIFIED", Pro.Green)
                        else if (c.resolutionVerified != null) Chip("CHECK NOTES", Pro.Amber)
                    }
                    Text(out.summary(), style = Pro.mono.copy(fontSize = 15.sp, color = Pro.Blue), modifier = Modifier.padding(top = 2.dp, bottom = 10.dp))
                    KeyValue("Resolution", "${out.displayWidth}×${out.displayHeight}")
                    KeyValue("Frame rate", "${"%.2f".format(out.fps)} FPS" + if (out.fpsIsVariable) " · VFR" else " · CFR")
                    KeyValue("Video", "${out.videoCodec} · ${formatBitrate(out.videoBitrate)}")
                    KeyValue("Audio", out.audioCodec?.let { "$it · ${out.audioSampleRate / 1000} kHz" } ?: "No audio")
                    KeyValue("Duration", formatDuration(out.durationMs))
                    KeyValue("File size", formatSize(c.file.length()))
                }
            }

            SectionLabel("Share")
            GhostButton("Share to WhatsApp Status", icon = Icons.Filled.Chat, accent = Pro.Green, enabled = hasWa,
                sub = if (hasWa) "Opens WhatsApp — choose “My status”" else "WhatsApp not installed") {
                ShareHelper.shareToWhatsApp(ctx, c.file, ShareHelper.WHATSAPP)
            }
            VSpace(8.dp)
            GhostButton("Share to WhatsApp Business Status", icon = Icons.Filled.Storefront, accent = Pro.Green, enabled = hasWaB,
                sub = if (hasWaB) "Opens WhatsApp Business — choose “My status”" else "WhatsApp Business not installed") {
                ShareHelper.shareToWhatsApp(ctx, c.file, ShareHelper.WHATSAPP_BUSINESS)
            }
            VSpace(8.dp)
            GhostButton(if (saved) "Saved to Gallery" else "Save to Gallery", icon = if (saved) Icons.Filled.Done else Icons.Filled.Save,
                accent = Pro.Blue, sub = "Movies / StatusPro4K") {
                val uri = ShareHelper.saveToGallery(ctx, c.file)
                saved = uri != null
                Toast.makeText(ctx, if (saved) "Saved to Movies/StatusPro4K" else "Save failed", Toast.LENGTH_SHORT).show()
            }
            VSpace(8.dp)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GhostButton("Preview", Modifier.weight(1f), icon = Icons.Filled.PlayArrow, accent = Pro.Blue) { onPreview() }
                GhostButton("Compare", Modifier.weight(1f), icon = Icons.Filled.Compare, accent = Pro.Blue, enabled = c.original != null) { onCompare() }
            }
            VSpace(8.dp)
            GhostButton("Share elsewhere", icon = Icons.Filled.Share) { ShareHelper.shareAny(ctx, c.file) }

            if (c.log.isNotEmpty()) {
                SectionLabel("Engine log")
                c.log.forEach { Text("› $it", style = Pro.mono.copy(fontSize = 11.sp, color = Pro.Dim)) }
            }
            VSpace(24.dp)
        }
    }
}

// ================================================================================== PREVIEW

@Composable
fun PreviewScreen(vm: AppViewModel, onBack: () -> Unit) {
    val c = vm.completed ?: return
    Column(Modifier.fillMaxSize().background(Color.Black).statusBarsPadding().navigationBarsPadding()) {
        TopBar("Preview video", onBack) { Chip("${c.output.resolutionLabel} · ${c.output.fps.roundToInt()} FPS"); Spacer(Modifier.width(12.dp)) }
        VideoPlayer(Uri.fromFile(c.file), Modifier.fillMaxSize())
    }
}

// ================================================================================== COMPARE

@Composable
fun CompareScreen(vm: AppViewModel, onBack: () -> Unit) {
    val c = vm.completed ?: return
    val orig = c.original ?: return
    var split by remember { mutableFloatStateOf(0.5f) }
    var pos by remember { mutableFloatStateOf(0.3f) }
    var committedPos by remember { mutableFloatStateOf(0.3f) }
    var zoom by remember { mutableStateOf(false) }
    var playMode by remember { mutableStateOf(false) }
    val durUs = minOf(orig.durationMs, c.output.durationMs) * 1000
    val t = (durUs * committedPos).toLong()
    val maxSide = 1600
    val a = rememberFrame(orig.uri, t, maxSide)
    val b = rememberFrame(Uri.fromFile(c.file), t, maxSide)

    Column(Modifier.fillMaxSize().background(Pro.Black).statusBarsPadding().navigationBarsPadding()) {
        TopBar("Original vs Enhanced", onBack)
        Box(Modifier.padding(horizontal = 18.dp)) {
            Segmented(listOf(false, true), playMode, { if (it) "PLAY SIDE BY SIDE" else "SPLIT FRAME" }) { playMode = it }
        }
        VSpace(12.dp)
        if (!playMode) {
            BoxWithConstraints(
                Modifier.fillMaxWidth().weight(1f).padding(horizontal = 18.dp).clipToBounds()
                    .pointerInput(Unit) { detectDragGestures { ch, _ -> split = (ch.position.x / size.width).coerceIn(0f, 1f) } }
                    .pointerInput(Unit) { detectTapGestures(onDoubleTap = { zoom = !zoom }) },
            ) {
                val wPx = with(LocalDensity.current) { maxWidth.toPx() }
                val zoomMod = Modifier.fillMaxSize().graphicsLayer { val s = if (zoom) 2.5f else 1f; scaleX = s; scaleY = s }
                FrameImage(b, zoomMod, ContentScale.Fit)
                FrameImage(a, zoomMod.drawWithContent {
                    clipRect(right = size.width * split) { this@drawWithContent.drawContent() }
                }, ContentScale.Fit)
                Box(Modifier.fillMaxHeight().width(2.dp).offset(x = with(LocalDensity.current) { (wPx * split).toDp() } - 1.dp).background(Pro.White))
                Text("ORIGINAL", style = Pro.mono.copy(fontSize = 10.sp), modifier = Modifier.align(Alignment.TopStart).padding(8.dp).background(Color(0x99000000)).padding(4.dp))
                Text("ENHANCED", style = Pro.mono.copy(fontSize = 10.sp, color = Pro.Blue), modifier = Modifier.align(Alignment.TopEnd).padding(8.dp).background(Color(0x99000000)).padding(4.dp))
            }
            Column(Modifier.padding(18.dp)) {
                Text("Drag on the image to move the divider · double-tap to zoom ${if (zoom) "out" else "250%"}", style = Pro.body.copy(fontSize = 11.sp, color = Pro.Dim))
                Slider(value = pos, onValueChange = { pos = it }, onValueChangeFinished = { committedPos = pos },
                    colors = SliderDefaults.colors(thumbColor = Pro.White, activeTrackColor = Pro.Blue, inactiveTrackColor = Pro.Line))
                Row {
                    Text(orig.summary(), style = Pro.mono.copy(fontSize = 11.sp), modifier = Modifier.weight(1f))
                    Text(c.output.summary(), style = Pro.mono.copy(fontSize = 11.sp, color = Pro.Blue))
                }
            }
        } else {
            Column(Modifier.weight(1f).padding(horizontal = 18.dp)) {
                Text("ORIGINAL · ${orig.summary()}", style = Pro.mono.copy(fontSize = 11.sp))
                VideoPlayer(orig.uri, Modifier.fillMaxWidth().weight(1f).padding(vertical = 6.dp), muted = true, showControls = false)
                Text("ENHANCED · ${c.output.summary()}", style = Pro.mono.copy(fontSize = 11.sp, color = Pro.Blue))
                VideoPlayer(Uri.fromFile(c.file), Modifier.fillMaxWidth().weight(1f).padding(vertical = 6.dp), muted = false, showControls = false)
            }
        }
    }
}
