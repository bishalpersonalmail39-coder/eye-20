package com.optirest.eyecare.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val ObsidianBg = Color(0xFF090D12)
val ElevatedSurface = Color(0xFF111720)
val HairlineBorder = Color(0x14FFFFFF)
val TealPrimary = Color(0xFF14B8A6)
val TealBright = Color(0xFF2DD4BF)
val AmberComfort = Color(0xFFF59E0B)
val RoseAlert = Color(0xFFF43F5E)
val TextPrimary = Color(0xFFF1F5F9)
val TextSecondary = Color(0xFF94A3B8)

private val OptiRestDarkScheme = darkColorScheme(
    primary = TealPrimary,
    onPrimary = Color.White,
    secondary = AmberComfort,
    onSecondary = Color.Black,
    background = ObsidianBg,
    onBackground = TextPrimary,
    surface = ElevatedSurface,
    onSurface = TextPrimary,
    surfaceVariant = Color(0xFF18212E),
    onSurfaceVariant = TextSecondary,
    outline = HairlineBorder
)

@Composable
fun OptiRestTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = OptiRestDarkScheme,
        content = content
    )
}
