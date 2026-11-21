package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DPlickDarkColorScheme = darkColorScheme(
    primary = ElectricBlueBright,
    onPrimary = Slate950,
    primaryContainer = ElectricBlue,
    onPrimaryContainer = Color.White,
    secondary = EmeraldAccent,
    onSecondary = Slate950,
    secondaryContainer = EmeraldDeep,
    onSecondaryContainer = EmeraldAccent,
    tertiary = AmberBeam,
    onTertiary = Slate950,
    tertiaryContainer = AmberDeep,
    onTertiaryContainer = AmberBeam,
    background = Slate900,
    onBackground = Slate100,
    surface = Slate900,
    onSurface = Slate100,
    surfaceVariant = Slate800,
    onSurfaceVariant = Slate300,
    surfaceContainerLowest = Slate950,
    surfaceContainerLow = Slate900,
    surfaceContainer = Slate800,
    surfaceContainerHigh = Slate700,
    outline = Slate600,
    outlineVariant = Slate700,
    error = CoralError,
    onError = Color.White,
    errorContainer = CoralErrorContainer,
    onErrorContainer = CoralError
)

@Composable
fun DPlickScannerTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = DPlickDarkColorScheme,
        typography = DPlickTypography,
        content = content
    )
}
