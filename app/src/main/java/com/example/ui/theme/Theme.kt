package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

// Monochrome White Theme (Shadcn style)
private val MonochromeLightColorScheme = lightColorScheme(
    primary = Zinc950,
    onPrimary = PureWhite,
    primaryContainer = Zinc100,
    onPrimaryContainer = Zinc900,
    secondary = Zinc800,
    onSecondary = PureWhite,
    secondaryContainer = Zinc100,
    onSecondaryContainer = Zinc900,
    tertiary = Zinc700,
    onTertiary = PureWhite,
    background = PureWhite,
    onBackground = Zinc950,
    surface = PureWhite,
    onSurface = Zinc950,
    surfaceVariant = Zinc100,
    onSurfaceVariant = Zinc700,
    outline = BorderLight,
    outlineVariant = Zinc200
)

val ShadcnShapes = Shapes(
    extraSmall = RoundedCornerShape(2.dp),
    small = RoundedCornerShape(4.dp),
    medium = RoundedCornerShape(6.dp),
    large = RoundedCornerShape(8.dp),
    extraLarge = RoundedCornerShape(12.dp)
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = false,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = MonochromeLightColorScheme,
        typography = Typography,
        shapes = ShadcnShapes,
        content = content
    )
}
