package com.viktor.anyconnect.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val AnyConnectColors = darkColorScheme(
    primary = RoyalCyan,
    onPrimary = Ink,
    primaryContainer = RoyalCyanDeep,
    onPrimaryContainer = OnInk,
    secondary = RoyalCyanBright,
    onSecondary = Ink,
    background = Ink,
    onBackground = OnInk,
    surface = Surface1,
    onSurface = OnInk,
    surfaceVariant = Surface2,
    onSurfaceVariant = Muted,
    error = Danger,
    onError = Ink,
)

@Composable
fun AnyConnectTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = AnyConnectColors,
        typography = Typography(),
        content = content
    )
}
