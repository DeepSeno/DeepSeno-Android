package com.enmooy.deepseno.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val DeepSenoDarkColorScheme = darkColorScheme(
    primary = AccentGreen,
    onPrimary = TextPrimary,
    secondary = AccentBlue,
    background = BgPrimary,
    surface = BgSecondary,
    surfaceVariant = BgTertiary,
    onBackground = TextPrimary,
    onSurface = TextPrimary,
    onSurfaceVariant = TextSecondary,
    error = AccentRed,
)

@Composable
fun DeepSenoTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DeepSenoDarkColorScheme,
        typography = DeepSenoTypography,
        content = content,
    )
}
