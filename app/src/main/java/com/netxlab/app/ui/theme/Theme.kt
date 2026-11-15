package com.netxlab.app.ui.theme

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.netxlab.app.data.AppTheme

private val Dark = darkColorScheme(
    primary = Color(0xFF4FC3F7), onPrimary = Color(0xFF00334A), secondary = Color(0xFF5B8DEF), tertiary = Color(0xFF7CE0C3),
    background = Color(0xFF0E1116), onBackground = Color(0xFFE6EDF3),
    surface = Color(0xFF161B22), onSurface = Color(0xFFE6EDF3),
    surfaceVariant = Color(0xFF1F2630), onSurfaceVariant = Color(0xFF9DA7B3),
    outline = Color(0xFF3A4452), error = Color(0xFFFF6B6B)
)
private val Grey = darkColorScheme(
    primary = Color(0xFF8CC4FF), onPrimary = Color(0xFF0B2A4A), secondary = Color(0xFFA7B4FF), tertiary = Color(0xFF9EE6C8),
    background = Color(0xFF3A3D42), onBackground = Color(0xFFF0F2F5),
    surface = Color(0xFF45494F), onSurface = Color(0xFFF0F2F5),
    surfaceVariant = Color(0xFF51565D), onSurfaceVariant = Color(0xFFCBD1D8),
    outline = Color(0xFF6C727A), error = Color(0xFFFF8A80)
)
private val White = lightColorScheme(
    primary = Color(0xFF1565C0), onPrimary = Color.White, secondary = Color(0xFF3949AB), tertiary = Color(0xFF00796B),
    background = Color(0xFFFFFFFF), onBackground = Color(0xFF111827),
    surface = Color(0xFFF3F4F6), onSurface = Color(0xFF111827),
    surfaceVariant = Color(0xFFE5E7EB), onSurfaceVariant = Color(0xFF4B5563),
    outline = Color(0xFFC4CAD3), error = Color(0xFFC62828)
)

val Mono = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 12.sp, lineHeight = 16.sp)

private val Typo = Typography(
    headlineSmall = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.SemiBold),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp)
)

@Composable
fun NetxTheme(theme: AppTheme, content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = when (theme) { AppTheme.DARK -> Dark; AppTheme.GREY -> Grey; AppTheme.WHITE -> White },
        typography = Typo, content = content
    )
}
