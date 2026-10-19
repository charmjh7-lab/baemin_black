package com.charmjh7.baeminblack.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val light = lightColorScheme(
    primary = Color(0xFF9D2639), onPrimary = Color.White, primaryContainer = Color(0xFFFFD9DF), onPrimaryContainer = Color(0xFF5D1020),
    secondary = Color(0xFF605B60), onSecondary = Color.White, secondaryContainer = Color(0xFFE9E0E3), onSecondaryContainer = Color(0xFF292429),
    tertiary = Color(0xFF765B20), onTertiary = Color.White, tertiaryContainer = Color(0xFFF8E1AB), onTertiaryContainer = Color(0xFF382B0D),
    background = Color(0xFFFAF8F5), onBackground = Color(0xFF252326), surface = Color(0xFFFAF8F5), onSurface = Color(0xFF252326),
    surfaceVariant = Color(0xFFEEE9E5), onSurfaceVariant = Color(0xFF655F62), outline = Color(0xFF81777B), outlineVariant = Color(0xFFD6CCCF),
    error = Color(0xFFBA1A1A), onError = Color.White, errorContainer = Color(0xFFFFDAD6), onErrorContainer = Color(0xFF410002),
    inverseSurface = Color(0xFF333034), inverseOnSurface = Color(0xFFF8F1F3), inversePrimary = Color(0xFFFFB1BF), surfaceTint = Color(0xFF9D2639)
)
private val dark = darkColorScheme(
    primary = Color(0xFFFFB1BF), onPrimary = Color(0xFF600F25), primaryContainer = Color(0xFF7D2033), onPrimaryContainer = Color(0xFFFFD9DF),
    secondary = Color(0xFFCEC4C9), onSecondary = Color(0xFF342F34), secondaryContainer = Color(0xFF4B454B), onSecondaryContainer = Color(0xFFEAE0E5),
    tertiary = Color(0xFFE3C37E), onTertiary = Color(0xFF3F2E05), tertiaryContainer = Color(0xFF594515), onTertiaryContainer = Color(0xFFF8E1AB),
    background = Color(0xFF19181A), onBackground = Color(0xFFECE5E7), surface = Color(0xFF19181A), onSurface = Color(0xFFECE5E7),
    surfaceVariant = Color(0xFF403A3E), onSurfaceVariant = Color(0xFFD1C4CA), outline = Color(0xFF9B8E95), outlineVariant = Color(0xFF50454B),
    error = Color(0xFFFFB4AB), onError = Color(0xFF690005), errorContainer = Color(0xFF93000A), onErrorContainer = Color(0xFFFFDAD6),
    inverseSurface = Color(0xFFECE5E7), inverseOnSurface = Color(0xFF333034), inversePrimary = Color(0xFF9D2639), surfaceTint = Color(0xFFFFB1BF)
)
@Composable fun BlackTheme(content: @Composable () -> Unit) { MaterialTheme(colorScheme = if (isSystemInDarkTheme()) dark else light, content = content) }
@Composable fun statusColor(status: com.charmjh7.baeminblack.data.ShopStatus): Color = when (status) {
    com.charmjh7.baeminblack.data.ShopStatus.BLOCKED -> MaterialTheme.colorScheme.primary
    com.charmjh7.baeminblack.data.ShopStatus.CAUTION -> MaterialTheme.colorScheme.tertiary
    else -> MaterialTheme.colorScheme.onSurfaceVariant
}
