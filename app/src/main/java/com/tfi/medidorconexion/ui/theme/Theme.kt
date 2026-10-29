package com.tfi.medidorconexion.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF78A9FF),
    onPrimary = Color(0xFF002A6B),
    primaryContainer = Color(0xFF0B2A5B),
    onPrimaryContainer = Color(0xFFD6E4FF),
    secondary = Color(0xFF4DD6D6),
    secondaryContainer = Color(0xFF0A3D3D),
    onSecondaryContainer = Color(0xFFBFF3F3),
    tertiary = Color(0xFFFFA05C),
    background = Color(0xFF0A1020),
    onBackground = Color(0xFFE6EBF5),
    surface = Color(0xFF111A2E),
    onSurface = Color(0xFFE6EBF5),
    surfaceVariant = Color(0xFF1D2940),
    onSurfaceVariant = Color(0xFFB4C0D6),
    outlineVariant = Color(0xFF2A3956),
    surfaceContainer = Color(0xFF111A2E),
    surfaceContainerHighest = Color(0xFF18233A)
)

private val LightColorScheme = lightColorScheme(
    primary = Marino,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE6EBF5),
    onPrimaryContainer = Marino,
    secondary = AzulMarca,
    secondaryContainer = Color(0xFFE3F1EF),
    onSecondaryContainer = Color(0xFF14524B),
    tertiary = NaranjaMarca,
    background = Color.White,
    onBackground = Color(0xFF111827),
    surface = Color.White,
    onSurface = Color(0xFF111827),
    surfaceVariant = Color(0xFFEDF0F6),
    onSurfaceVariant = GrisTexto,
    outlineVariant = Color(0xFFDDE2EC),
    surfaceContainer = Color.White,
    surfaceContainerHighest = Color(0xFFF5F7FB)
)

@Composable
fun MedidorConexionTheme(
    // La app se ve siempre con fondo blanco, aunque el celular esté en modo oscuro
    darkTheme: Boolean = false,
    // Se usa la paleta propia de la app; el color dinámico (Android 12+) la reemplazaría por el del fondo de pantalla
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}