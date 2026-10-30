package com.osei.djmixer.ui.theme

import com.osei.djmixer.ui.theme.*

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = CyberPink,
    secondary = CyberCyan,
    tertiary = CyberPurple,
    background = CyberDark,
    surface = CyberSurface,
    onPrimary = Color.White,
    onSecondary = Color.Black,
    onTertiary = Color.White,
    onBackground = Slate100,
    onSurface = Slate100,
)

private val LightColorScheme = lightColorScheme(
    primary = CyberPink,
    secondary = CyberBlue,
    tertiary = CyberPurple,
    background = Color.White,
    surface = Color.White,
    onPrimary = Color.Black,
    onSecondary = Color.Black,
    onTertiary = Color.Black,
    onBackground = CyberDark,
    onSurface = CyberDark,
)

private val MonochromeColorScheme = darkColorScheme(
    primary = Color.White,
    secondary = Color(0xFFD1D5DB), // Light Gray
    tertiary = Color(0xFF4B5563),  // Gray 600
    background = Color.Black,       // Pure Black for High Contrast
    surface = Color(0xFF111827),    // Slate 900 / Dark Gray
    onPrimary = Color.Black,
    onSecondary = Color.White,
    onTertiary = Color.White,
    onBackground = Color.White,
    onSurface = Color.White,
)

private val SlateColorScheme = darkColorScheme(
    primary = Color(0xFF6366F1), // Indigo 500
    secondary = Color(0xFF818CF8), // Indigo 400
    tertiary = Color(0xFFC7D2FE), // Indigo 200
    background = CyberSurface, // Slate 900
    surface = Color(0xFF1E293B), // Slate 800
    onPrimary = Color.White,
    onSecondary = Color.White,
    onTertiary = Color.Black,
    onBackground = Color(0xFFF1F5F9), // Slate 100
    onSurface = Color(0xFFF1F5F9), // Slate 100
)

@Composable
fun OseiDjMixerTheme(
  themeMode: String = "dark",
  content: @Composable () -> Unit,
) {
  val colorScheme =
    when (themeMode) {
      "light" -> LightColorScheme
      "slate" -> SlateColorScheme
      "monochrome" -> MonochromeColorScheme
      else -> DarkColorScheme
    }

  MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
}
