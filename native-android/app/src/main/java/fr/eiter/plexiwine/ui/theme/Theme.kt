package fr.eiter.plexiwine.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/** Exact iOS Theme.swift palette — always dark (Weeno). */
object WineColors {
    val bg = Color(0xFF0F1419)
    val fieldBg = Color(0xFF0F1419)
    val card = Color(0xFF1A222C)
    val text = Color(0xFFF1F5F9)
    val muted = Color(0xFF94A3B8)
    val accent = Color(0xFFF59E0B)
    val accent2 = Color(0xFFD97706)
    val border = Color(0xFF2D3A4A)
    val star = Color(0xFFFBBF24)
    val starOff = Color(0xFF475569)
    val ok = Color(0xFF34D399)
    val error = Color(0xFFF87171)
    val btnPrimaryText = Color(0xFF1A1208)
    val photoBg = Color(0xFF0A0A0C)
}

private val DarkColors = darkColorScheme(
    primary = WineColors.accent,
    onPrimary = WineColors.btnPrimaryText,
    secondary = WineColors.accent2,
    background = WineColors.bg,
    surface = WineColors.card,
    onBackground = WineColors.text,
    onSurface = WineColors.text,
    outline = WineColors.border,
    error = WineColors.error,
    onError = WineColors.text
)

private val WeenoTypography = Typography(
    headlineLarge = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.Bold, color = WineColors.text),
    headlineSmall = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.Bold, color = WineColors.text),
    titleMedium = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = WineColors.text),
    titleSmall = TextStyle(fontSize = 13.6.sp, fontWeight = FontWeight.SemiBold, color = WineColors.text),
    bodyLarge = TextStyle(fontSize = 14.sp, color = WineColors.text),
    bodyMedium = TextStyle(fontSize = 13.sp, color = WineColors.text),
    bodySmall = TextStyle(fontSize = 12.sp, color = WineColors.muted),
    labelLarge = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.SemiBold),
    labelSmall = TextStyle(fontSize = 11.5.sp, color = WineColors.muted)
)

@Composable
fun PlexiWineTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColors,
        typography = WeenoTypography,
        content = content
    )
}
