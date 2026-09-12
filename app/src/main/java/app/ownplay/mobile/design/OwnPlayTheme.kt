package app.ownplay.mobile.design

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private val OwnPlayDarkColorScheme = darkColorScheme(
    primary = OwnPlayColors.Accent,
    onPrimary = OwnPlayColors.TextPrimary,
    secondary = OwnPlayColors.Accent,
    onSecondary = OwnPlayColors.TextPrimary,
    background = OwnPlayColors.Background,
    onBackground = OwnPlayColors.TextPrimary,
    surface = OwnPlayColors.Surface,
    onSurface = OwnPlayColors.TextPrimary,
    surfaceVariant = OwnPlayColors.SurfaceElevated,
    onSurfaceVariant = OwnPlayColors.TextSecondary,
    outline = OwnPlayColors.Divider,
    error = OwnPlayColors.Error,
)

private val OwnPlayTypography = Typography(
    displaySmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 32.sp,
        lineHeight = 38.sp,
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 26.sp,
        lineHeight = 32.sp,
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 22.sp,
        lineHeight = 28.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 17.sp,
        lineHeight = 22.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 22.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 13.sp,
        lineHeight = 17.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 15.sp,
    ),
)

@Composable
fun OwnPlayTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = OwnPlayDarkColorScheme,
        typography = OwnPlayTypography,
        shapes = OwnPlayMaterialShapes,
        content = content,
    )
}
