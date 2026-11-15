package com.vivescriptsolutions.roomready.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val WarmHotelLightColorScheme =
  lightColorScheme(
    primary = BotanicalSage,
    onPrimary = Color.White,
    primaryContainer = BotanicalSageContainer,
    onPrimaryContainer = OnBotanicalSageContainer,
    secondary = WarmTeak,
    onSecondary = Color.White,
    secondaryContainer = WarmTeakContainer,
    onSecondaryContainer = OnWarmTeakContainer,
    tertiary = StatusInspectedBg,
    onTertiary = Color.White,
    tertiaryContainer = StatusInspectedTint,
    onTertiaryContainer = StatusInspectedTintInk,
    background = WarmAlabaster,
    onBackground = EspressoInk,
    surface = WarmIvory,
    onSurface = EspressoInk,
    surfaceVariant = WarmSandstone,
    onSurfaceVariant = WarmSlateInk,
    surfaceContainerLowest = WarmIvory,
    surfaceContainerLow = WarmAlabaster,
    surfaceContainer = WarmSandstone,
    surfaceContainerHigh = WarmOatmeal,
    surfaceContainerHighest = WarmOatmeal,
    outline = WarmBorder,
    outlineVariant = WarmBorder,
    error = StatusDirtyBg,
    onError = Color.White,
    errorContainer = StatusDirtyTint,
    onErrorContainer = StatusDirtyTintInk,
  )

private val RoomReadyShapes =
  Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
  )

@Composable
fun RoomReadyTheme(content: @Composable () -> Unit) {
  // Forced light theme for calm, legible daytime resort housekeeping
  MaterialTheme(
    colorScheme = WarmHotelLightColorScheme,
    typography = RoomReadyTypography,
    shapes = RoomReadyShapes,
    content = content,
  )
}
