package com.mohdrozaimi.statuspro4k.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

object Pro {
    val Black = Color(0xFF000000)
    val Panel = Color(0xFF0B0C0E)
    val Panel2 = Color(0xFF15171B)
    val Line = Color(0xFF24272D)
    val Dim = Color(0xFF6E737C)
    val Soft = Color(0xFFA9AEB7)
    val White = Color(0xFFF4F5F7)
    val Blue = Color(0xFF2F7BFF)
    val BlueGlow = Color(0x332F7BFF)
    val Green = Color(0xFF35D07F)
    val Amber = Color(0xFFFFB547)
    val Red = Color(0xFFFF5A5F)

    val label = TextStyle(fontSize = 10.sp, letterSpacing = 1.8.sp, fontWeight = FontWeight.Medium, color = Dim)
    val mono = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 13.sp, color = White)
    val monoBig = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 22.sp, fontWeight = FontWeight.Medium, color = White)
    val title = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.SemiBold, color = White, letterSpacing = 0.2.sp)
    val body = TextStyle(fontSize = 13.sp, color = Soft)
}

@Composable
fun StatusProTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Pro.Blue,
            onPrimary = Color.White,
            background = Pro.Black,
            surface = Pro.Panel,
            onSurface = Pro.White,
            onBackground = Pro.White,
            surfaceVariant = Pro.Panel2,
            outline = Pro.Line,
        ),
        content = content,
    )
}
