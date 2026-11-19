@file:OptIn(UnstableApi::class)

package com.mohdrozaimi.statuspro4k.ui.components

import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.Effect
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.mohdrozaimi.statuspro4k.ui.theme.Pro
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun TopBar(title: String, onBack: (() -> Unit)? = null, trailing: @Composable RowScope.() -> Unit = {}) {
    Row(
        Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = Pro.White) }
        } else Spacer(Modifier.width(12.dp))
        Text(title.uppercase(), style = Pro.label.copy(color = Pro.White, fontSize = 12.sp, letterSpacing = 2.4.sp))
        Spacer(Modifier.weight(1f))
        trailing()
    }
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(text.uppercase(), style = Pro.label, modifier = modifier.padding(top = 18.dp, bottom = 8.dp))
}

@Composable
fun Panel(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(4.dp))
            .background(Pro.Panel)
            .border(BorderStroke(1.dp, Pro.Line), RoundedCornerShape(4.dp))
            .padding(14.dp),
    ) { content() }
}

/** Camera-viewfinder corner brackets drawn around content. */
@Composable
fun Viewfinder(modifier: Modifier = Modifier, color: Color = Pro.White, content: @Composable () -> Unit) {
    Box(modifier) {
        content()
        Canvas(Modifier.matchParentSize()) {
            val l = 18.dp.toPx(); val s = 1.5.dp.toPx(); val w = size.width; val h = size.height
            fun line(a: Offset, b: Offset) = drawLine(color, a, b, s)
            line(Offset(0f, 0f), Offset(l, 0f)); line(Offset(0f, 0f), Offset(0f, l))
            line(Offset(w, 0f), Offset(w - l, 0f)); line(Offset(w, 0f), Offset(w, l))
            line(Offset(0f, h), Offset(l, h)); line(Offset(0f, h), Offset(0f, h - l))
            line(Offset(w, h), Offset(w - l, h)); line(Offset(w, h), Offset(w, h - l))
        }
    }
}

@Composable
fun KeyValue(key: String, value: String, valueColor: Color = Pro.White) {
    Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(key.uppercase(), style = Pro.label, modifier = Modifier.weight(1f))
        Text(value, style = Pro.mono.copy(color = valueColor), textAlign = TextAlign.End)
    }
}

@Composable
fun <T> Segmented(options: List<T>, selected: T, label: (T) -> String, enabled: (T) -> Boolean = { true }, onSelect: (T) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(4.dp)).border(1.dp, Pro.Line, RoundedCornerShape(4.dp)),
    ) {
        options.forEachIndexed { i, o ->
            val sel = o == selected
            val en = enabled(o)
            Box(
                Modifier.weight(1f).height(40.dp)
                    .background(if (sel) Pro.Blue else Color.Transparent)
                    .clickable(enabled = en) { onSelect(o) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label(o),
                    style = Pro.mono.copy(
                        fontSize = 12.sp,
                        color = when { sel -> Color.White; en -> Pro.Soft; else -> Pro.Line },
                        fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal,
                    ),
                )
            }
            if (i < options.size - 1) Box(Modifier.width(1.dp).height(40.dp).background(Pro.Line))
        }
    }
}

@Composable
fun ProSlider(label: String, value: Float, onChange: (Float) -> Unit, onDone: () -> Unit = {}) {
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label.uppercase(), style = Pro.label, modifier = Modifier.weight(1f))
            Text("${(value * 100).toInt()}", style = Pro.mono.copy(color = Pro.Blue))
        }
        Slider(
            value = value, onValueChange = onChange, onValueChangeFinished = onDone,
            colors = SliderDefaults.colors(
                thumbColor = Pro.White, activeTrackColor = Pro.Blue, inactiveTrackColor = Pro.Line,
                activeTickColor = Color.Transparent, inactiveTickColor = Color.Transparent,
            ),
        )
    }
}

@Composable
fun ProSwitch(label: String, sub: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(label, style = Pro.body.copy(color = Pro.White))
            if (sub != null) Text(sub, style = Pro.body.copy(fontSize = 11.sp, color = Pro.Dim))
        }
        Switch(
            checked = checked, onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedTrackColor = Pro.Blue, checkedThumbColor = Color.White,
                uncheckedTrackColor = Pro.Panel2, uncheckedThumbColor = Pro.Dim, uncheckedBorderColor = Pro.Line,
            ),
        )
    }
}

@Composable
fun PrimaryButton(text: String, modifier: Modifier = Modifier, icon: ImageVector? = null, enabled: Boolean = true, onClick: () -> Unit) {
    Row(
        modifier.fillMaxWidth().height(54.dp).clip(RoundedCornerShape(4.dp))
            .background(if (enabled) Pro.Blue else Pro.Panel2)
            .clickable(enabled = enabled, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        if (icon != null) { Icon(icon, null, tint = Color.White, modifier = Modifier.size(20.dp)); Spacer(Modifier.width(10.dp)) }
        Text(text.uppercase(), style = Pro.label.copy(color = if (enabled) Color.White else Pro.Dim, fontSize = 12.sp, letterSpacing = 2.sp))
    }
}

@Composable
fun GhostButton(text: String, modifier: Modifier = Modifier, icon: ImageVector? = null, accent: Color = Pro.White, enabled: Boolean = true, sub: String? = null, onClick: () -> Unit) {
    Row(
        modifier.fillMaxWidth().height(if (sub != null) 60.dp else 52.dp).clip(RoundedCornerShape(4.dp))
            .border(1.dp, if (enabled) Pro.Line else Pro.Panel2, RoundedCornerShape(4.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) { Icon(icon, null, tint = if (enabled) accent else Pro.Line, modifier = Modifier.size(20.dp)); Spacer(Modifier.width(14.dp)) }
        Column(Modifier.weight(1f)) {
            Text(text.uppercase(), style = Pro.label.copy(color = if (enabled) Pro.White else Pro.Dim, fontSize = 12.sp, letterSpacing = 1.6.sp))
            if (sub != null) Text(sub, style = Pro.body.copy(fontSize = 11.sp, color = Pro.Dim))
        }
    }
}

@Composable
fun Chip(text: String, color: Color = Pro.Blue) {
    Box(Modifier.clip(RoundedCornerShape(3.dp)).background(color.copy(alpha = 0.15f)).padding(horizontal = 7.dp, vertical = 3.dp)) {
        Text(text, style = Pro.mono.copy(fontSize = 10.sp, color = color, fontWeight = FontWeight.Bold))
    }
}

/** Loads a video frame (thumbnail / comparison frame) off the main thread. */
@Composable
fun rememberFrame(uri: Uri?, timeUs: Long = 1_000_000L, maxSide: Int = 720): Bitmap? {
    val ctx = LocalContext.current
    var bmp by remember(uri, timeUs) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(uri, timeUs) {
        if (uri == null) return@LaunchedEffect
        bmp = withContext(Dispatchers.IO) { grabFrame(ctx, uri, timeUs, maxSide) }
    }
    return bmp
}

fun grabFrame(ctx: android.content.Context, uri: Uri, timeUs: Long, maxSide: Int): Bitmap? = runCatching {
    val r = MediaMetadataRetriever()
    try {
        if (uri.scheme == "file") r.setDataSource(uri.path) else r.setDataSource(ctx, uri)
        val w = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: maxSide
        val h = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: maxSide
        val rot = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
        val (dw, dh) = if (rot % 180 == 0) w to h else h to w
        val scale = minOf(1f, maxSide.toFloat() / maxOf(dw, dh))
        r.getScaledFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST, (dw * scale).toInt(), (dh * scale).toInt())
    } finally { r.release() }
}.getOrNull()

@Composable
fun FrameImage(bmp: Bitmap?, modifier: Modifier = Modifier, scale: ContentScale = ContentScale.Crop) {
    if (bmp != null) Image(bmp.asImageBitmap(), null, modifier, contentScale = scale)
    else Box(modifier.background(Pro.Panel2))
}

/** Media3 ExoPlayer view. [effects] are applied live on the GPU (same shaders as export). */
@Composable
fun VideoPlayer(
    uri: Uri,
    modifier: Modifier = Modifier,
    effects: List<Effect>? = null,
    effectsKey: Any? = null,
    muted: Boolean = false,
    showControls: Boolean = true,
    onPlayer: (ExoPlayer) -> Unit = {},
) {
    val ctx = LocalContext.current
    val player = remember(uri, effectsKey) {
        ExoPlayer.Builder(ctx).build().apply {
            if (!effects.isNullOrEmpty()) setVideoEffects(effects)
            setMediaItem(MediaItem.fromUri(uri))
            repeatMode = Player.REPEAT_MODE_ALL
            volume = if (muted) 0f else 1f
            playWhenReady = true
            prepare()
        }
    }
    LaunchedEffect(player) { onPlayer(player) }
    DisposableEffect(player) { onDispose { player.release() } }
    AndroidView(
        factory = { c -> PlayerView(c).apply { useController = showControls; setShutterBackgroundColor(android.graphics.Color.BLACK) } },
        update = { it.player = player },
        modifier = modifier.background(Color.Black),
    )
}

@Composable
fun VSpace(h: Dp) = Spacer(Modifier.height(h))
