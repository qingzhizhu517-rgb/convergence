package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val CleanLightColorScheme = lightColorScheme(
    primary = ConvergencePrimary,
    onPrimary = ConvergenceOnPrimary,
    primaryContainer = ConvergencePrimaryContainer,
    onPrimaryContainer = ConvergenceOnPrimaryContainer,
    secondary = ConvergenceSecondary,
    onSecondary = ConvergenceOnSecondary,
    secondaryContainer = ConvergenceSecondaryContainer,
    onSecondaryContainer = ConvergenceOnSecondaryContainer,
    background = ConvergenceBackground,
    onBackground = ConvergenceOnBackground,
    surface = ConvergenceSurface,
    onSurface = ConvergenceOnSurface,
    surfaceVariant = ConvergenceSurfaceVariant,
    onSurfaceVariant = ConvergenceOnSurfaceVariant,
    outline = ConvergenceOutline,
    outlineVariant = ConvergenceOutlineVariant
)

// A beautiful dark theme fallback that mirrors the indigo feel nicely in low-light
private val DeepDarkColorScheme = darkColorScheme(
    primary = ConvergencePrimaryContainer,
    onPrimary = ConvergenceOnPrimary,
    primaryContainer = ConvergencePrimary,
    onPrimaryContainer = ConvergenceOnPrimaryContainer,
    secondary = ConvergenceSecondary,
    onSecondary = ConvergenceOnSecondary,
    background = ConvergenceOnBackground,
    onBackground = ConvergenceBackground,
    surface = ConvergenceOnBackground,
    onSurface = ConvergenceBackground,
    surfaceVariant = ConvergenceOnSurfaceVariant,
    onSurfaceVariant = ConvergenceSurfaceVariant
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = false, // Keep light mode by default for paper feel
    dynamicColor: Boolean = false, // Disable dynamic colors to enforce branding
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) DeepDarkColorScheme else CleanLightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
