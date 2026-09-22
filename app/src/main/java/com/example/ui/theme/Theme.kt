package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val FocusCaptureColorScheme = darkColorScheme(
    primary = ElectricCyan,
    onPrimary = Color(0xFF002025),
    primaryContainer = Color(0xFF004F57),
    onPrimaryContainer = Color(0xFF70F7FF),

    secondary = IndigoGlow,
    onSecondary = Color(0xFF1E1B4B),
    secondaryContainer = Color(0xFF312E81),
    onSecondaryContainer = Color(0xFFC7D2FE),

    tertiary = CrimsonRecord,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFF4C0519),
    onTertiaryContainer = Color(0xFFFFD9E2),

    background = ObsidianBg,
    onBackground = TextPrimary,

    surface = DarkSurface,
    onSurface = TextPrimary,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = TextSecondary,

    outline = BorderSubtle,
    outlineVariant = DarkSurfaceHighlight
)

@Composable
fun FocusCaptureTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = FocusCaptureColorScheme,
        typography = Typography,
        content = content
    )
}

// Alias to maintain compatibility with test suites
@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = true,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    FocusCaptureTheme(content = content)
}

