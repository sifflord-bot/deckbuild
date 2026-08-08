package dev.deckbuild.app.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import dev.deckbuild.core.model.Aspect

/** Farbwerte der Oberflaeche. Bewusst dunkel - Karten sollen leuchten, nicht die App. */
object Palette {
    val Background = Color(0xFF0B0A10)
    val Surface = Color(0xFF17141F)
    val SurfaceHigh = Color(0xFF221D2E)
    val Outline = Color(0xFF3A3348)
    val TextPrimary = Color(0xFFEDE7F2)
    val TextMuted = Color(0xFF9A93A8)
    val Gold = Color(0xFFE8B155)
    val Danger = Color(0xFFD1495B)
    val Health = Color(0xFF7BC96F)
    val Essence = Color(0xFF6FA8DC)
}

/** Jeder Aspekt bekommt ein eigenes Farbpaar fuer Rahmen und Kartenflaeche. */
data class AspectColors(val primary: Color, val glow: Color, val ink: Color)

fun colorsFor(aspect: Aspect): AspectColors = when (aspect) {
    Aspect.GLUT -> AspectColors(Color(0xFFC0392B), Color(0xFFF0813F), Color(0xFF2A1210))
    Aspect.FLUT -> AspectColors(Color(0xFF2F6FA8), Color(0xFF4FB6E0), Color(0xFF0E1F2E))
    Aspect.ASCHE -> AspectColors(Color(0xFF6D4C7D), Color(0xFFB08BC9), Color(0xFF1A1220))
    Aspect.HAIN -> AspectColors(Color(0xFF3E7A44), Color(0xFF7BC96F), Color(0xFF11200F))
    Aspect.LICHT -> AspectColors(Color(0xFFC8A24A), Color(0xFFF2E2A8), Color(0xFF2A2312))
    Aspect.NEUTRAL -> AspectColors(Color(0xFF5A5A66), Color(0xFF9AA0AA), Color(0xFF16161C))
}

private val DeckbuildColors = darkColorScheme(
    primary = Palette.Gold,
    onPrimary = Color(0xFF201704),
    secondary = Palette.Essence,
    background = Palette.Background,
    onBackground = Palette.TextPrimary,
    surface = Palette.Surface,
    onSurface = Palette.TextPrimary,
    surfaceVariant = Palette.SurfaceHigh,
    onSurfaceVariant = Palette.TextMuted,
    outline = Palette.Outline,
    error = Palette.Danger,
)

private val DeckbuildTypography = Typography(
    titleLarge = TextStyle(fontSize = 26.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp),
    titleMedium = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.SemiBold),
    titleSmall = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.SemiBold),
    bodyMedium = TextStyle(fontSize = 14.sp),
    bodySmall = TextStyle(fontSize = 12.sp),
    labelSmall = TextStyle(fontSize = 10.sp, fontWeight = FontWeight.Medium),
)

@Composable
fun DeckbuildTheme(
    @Suppress("UNUSED_PARAMETER") darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    // Das Spiel hat bewusst nur ein Erscheinungsbild: Ein helles Kartenspiel
    // waere im Dunkeln unlesbar und umgekehrt.
    MaterialTheme(
        colorScheme = DeckbuildColors,
        typography = DeckbuildTypography,
        content = content,
    )
}
