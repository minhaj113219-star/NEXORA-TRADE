package com.nexora.trade.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

@Immutable
data class NexoraExtendedColors(
    val profit: Color,
    val profitContainer: Color,
    val onProfitContainer: Color,
    val loss: Color,
    val lossContainer: Color,
    val onLossContainer: Color,
    val warning: Color,
    val warningContainer: Color,
    val onWarningContainer: Color,
    val cardBorder: Color,
    val surfaceElevated: Color,
    val textMuted: Color
)

private val DarkExtendedColors = NexoraExtendedColors(
    profit = ProfitGreen,
    profitContainer = ProfitGreenContainerDark,
    onProfitContainer = Color(0xFF6EE7B7),
    loss = LossRed,
    lossContainer = LossRedContainerDark,
    onLossContainer = Color(0xFFFCA5A5),
    warning = WarningAmber,
    warningContainer = WarningAmberContainerDark,
    onWarningContainer = Color(0xFFFCD34D),
    cardBorder = DarkBorder,
    surfaceElevated = DarkSurfaceElevated,
    textMuted = DarkTextMuted
)

private val LightExtendedColors = NexoraExtendedColors(
    profit = ProfitGreenTextLight,
    profitContainer = ProfitGreenContainerLight,
    onProfitContainer = ProfitGreenTextLight,
    loss = LossRedTextLight,
    lossContainer = LossRedContainerLight,
    onLossContainer = LossRedTextLight,
    warning = WarningAmberTextLight,
    warningContainer = WarningAmberContainerLight,
    onWarningContainer = WarningAmberTextLight,
    cardBorder = LightBorder,
    surfaceElevated = LightSurfaceElevated,
    textMuted = LightTextMuted
)

val LocalNexoraColors = staticCompositionLocalOf { DarkExtendedColors }

private val DarkColorScheme = darkColorScheme(
    primary = BrandAccent,
    onPrimary = Color(0xFF041E2B),
    primaryContainer = Color(0xFF0E344D),
    onPrimaryContainer = Color(0xFFBAE6FD),
    secondary = ProfitGreen,
    onSecondary = Color(0xFF032318),
    secondaryContainer = ProfitGreenContainerDark,
    onSecondaryContainer = Color(0xFF6EE7B7),
    tertiary = WarningAmber,
    onTertiary = Color(0xFF2B1B02),
    background = DarkBackground,
    onBackground = DarkTextPrimary,
    surface = DarkSurface,
    onSurface = DarkTextPrimary,
    surfaceVariant = DarkSurfaceHighest,
    onSurfaceVariant = DarkTextSecondary,
    outline = DarkBorder,
    error = LossRed,
    onError = Color.White,
    errorContainer = LossRedContainerDark,
    onErrorContainer = Color(0xFFFCA5A5)
)

private val LightColorScheme = lightColorScheme(
    primary = BrandAccentDark,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE0F2FE),
    onPrimaryContainer = Color(0xFF075985),
    secondary = ProfitGreenTextLight,
    onSecondary = Color.White,
    secondaryContainer = ProfitGreenContainerLight,
    onSecondaryContainer = ProfitGreenTextLight,
    tertiary = WarningAmberTextLight,
    onTertiary = Color.White,
    background = LightBackground,
    onBackground = LightTextPrimary,
    surface = LightSurface,
    onSurface = LightTextPrimary,
    surfaceVariant = LightSurfaceHighest,
    onSurfaceVariant = LightTextSecondary,
    outline = LightBorder,
    error = LossRedTextLight,
    onError = Color.White,
    errorContainer = LossRedContainerLight,
    onErrorContainer = LossRedTextLight
)

object NexoraTheme {
    val colors: NexoraExtendedColors
        @Composable
        get() = LocalNexoraColors.current
}

@Composable
fun NexoraTradeTheme(
    darkTheme: Boolean = true,
    followSystemTheme: Boolean = false,
    content: @Composable () -> Unit
) {
    val useDark = if (followSystemTheme) isSystemInDarkTheme() else darkTheme
    val colorScheme = if (useDark) DarkColorScheme else LightColorScheme
    val extendedColors = if (useDark) DarkExtendedColors else LightExtendedColors

    CompositionLocalProvider(LocalNexoraColors provides extendedColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = NexoraTypography,
            shapes = NexoraShapes,
            content = content
        )
    }
}