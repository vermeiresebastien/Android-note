package com.example.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.graphics.Color

val CharcoalBackground = Color(0xFF101214)
val CharcoalSurface = Color(0xFF181A1E)
val CharcoalSurfaceVariant = Color(0xFF22252A)
val CharcoalBorder = Color(0xFF2E3238)

val NeonCyan = Color(0xFF00E5FF)
val NeonAmber = Color(0xFFFFB300)
val NeonCoral = Color(0xFFFF5252)
val NeonEmerald = Color(0xFF00E676)
val NeonPurple = Color(0xFFB388FF)
val TextPrimary = Color(0xFFEEEEEE)
val TextSecondary = Color(0xFF9E9E9E)
val TextMuted = Color(0xFF616161)

val PocketColorScheme = darkColorScheme(
  primary = NeonCyan,
  onPrimary = Color.Black,
  primaryContainer = Color(0xFF004D40),
  onPrimaryContainer = NeonCyan,
  secondary = NeonAmber,
  onSecondary = Color.Black,
  background = CharcoalBackground,
  onBackground = TextPrimary,
  surface = CharcoalSurface,
  onSurface = TextPrimary,
  surfaceVariant = CharcoalSurfaceVariant,
  onSurfaceVariant = TextSecondary,
  outline = CharcoalBorder
)
