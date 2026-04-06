package com.lumus.vkapp.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Color(0xFF0B5D5C),
    onPrimary = Color.White,
    secondary = Color(0xFFFF8A3D),
    onSecondary = Color(0xFF2A1200),
    background = Color(0xFFF4F8F2),
    surface = Color(0xFFFDFCF8),
    surfaceVariant = Color(0xFFE2ECE8),
    onSurfaceVariant = Color(0xFF415551),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF79C9C3),
    secondary = Color(0xFFFFB57A),
    background = Color(0xFF121816),
    surface = Color(0xFF17211F),
)

@Composable
fun LumusTheme(
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = LightColors,
        content = content,
    )
}
