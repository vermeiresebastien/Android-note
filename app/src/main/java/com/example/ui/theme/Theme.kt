package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val PocketTypography = Typography(
  displayLarge = TextStyle(
    fontFamily = FontFamily.SansSerif,
    fontWeight = FontWeight.Bold,
    fontSize = 32.sp,
    letterSpacing = (-0.5).sp,
    color = TextPrimary
  ),
  titleLarge = TextStyle(
    fontFamily = FontFamily.SansSerif,
    fontWeight = FontWeight.SemiBold,
    fontSize = 20.sp,
    letterSpacing = 0.sp,
    color = TextPrimary
  ),
  titleMedium = TextStyle(
    fontFamily = FontFamily.SansSerif,
    fontWeight = FontWeight.Medium,
    fontSize = 16.sp,
    letterSpacing = 0.15.sp,
    color = TextPrimary
  ),
  bodyMedium = TextStyle(
    fontFamily = FontFamily.SansSerif,
    fontWeight = FontWeight.Normal,
    fontSize = 14.sp,
    letterSpacing = 0.25.sp,
    color = TextSecondary
  ),
  labelSmall = TextStyle(
    fontFamily = FontFamily.Monospace,
    fontWeight = FontWeight.Medium,
    fontSize = 11.sp,
    letterSpacing = 0.5.sp,
    color = TextMuted
  )
)

@Composable
fun PocketSessionTheme(content: @Composable () -> Unit) {
  MaterialTheme(
    colorScheme = PocketColorScheme,
    typography = PocketTypography,
    content = content
  )
}
