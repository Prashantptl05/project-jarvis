package com.example.ui.theme

import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

private val JarvisColorScheme = darkColorScheme(
    primary = HolographicCyan,
    onPrimary = VoidObsidian,
    primaryContainer = CyanMuted,
    onPrimaryContainer = HolographicCyan,
    secondary = ArcReactorBlue,
    onSecondary = HudTextPrimary,
    secondaryContainer = ObsidianCard,
    onSecondaryContainer = HudTextPrimary,
    tertiary = TacticalAmber,
    onTertiary = VoidObsidian,
    tertiaryContainer = AmberDarkContainer,
    onTertiaryContainer = TacticalAmber,
    error = AlertCrimson,
    onError = HudTextPrimary,
    errorContainer = CrimsonDarkContainer,
    onErrorContainer = AlertCrimson,
    background = VoidObsidian,
    onBackground = HudTextPrimary,
    surface = ObsidianElevated,
    onSurface = HudTextPrimary,
    surfaceVariant = ObsidianCard,
    onSurfaceVariant = HudTextSecondary,
    outline = CyanSubtleStroke,
    outlineVariant = CyanMuted
)

val JarvisShapes = Shapes(
    small = CutCornerShape(topStart = 8.dp, bottomEnd = 8.dp),
    medium = CutCornerShape(topStart = 12.dp, bottomEnd = 12.dp),
    large = CutCornerShape(topStart = 16.dp, bottomEnd = 16.dp)
)

@Composable
fun JarvisTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = JarvisColorScheme,
        typography = JarvisTypography,
        shapes = JarvisShapes,
        content = content
    )
}
