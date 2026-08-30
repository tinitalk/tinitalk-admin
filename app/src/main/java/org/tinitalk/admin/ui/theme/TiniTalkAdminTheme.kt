package org.tinitalk.admin.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val AdminColors = darkColorScheme(
    primary = Color(0xFFD4AF37),
    background = Color(0xFF08111F),
    surface = Color(0xFF08111F),
    onBackground = Color(0xFFF1F5F9),
    onSurface = Color(0xFFF1F5F9),
)

@Composable
fun TiniTalkAdminTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = AdminColors,
        content = content,
    )
}
