package dev.deckbuild.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import dev.deckbuild.core.model.CardDef
import dev.deckbuild.core.model.CardType
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * Prozedurale "Illustration" statt Bilddatei. Jede Karte bekommt ein
 * deterministisches Muster, abgeleitet aus ihrer id als Seed - dieselbe Karte
 * sieht also immer gleich aus, ohne dass irgendwo eine Bilddatei liegt oder
 * eine Lizenzfrage entsteht.
 *
 * Der Kartentyp waehlt die Grundform (Kreatur = Strahlen aus der Mitte,
 * Spontan = schroffe Blitze, Ritual = konzentrische Ringe, Relikt = Polygon,
 * Quelle = ruhiger Kern), die Aspektfarben aus [AspectColors] tragen die
 * Palette. So bleibt jede Karte einzigartig, aber der Aspekt bleibt auf den
 * ersten Blick erkennbar.
 */
@Composable
fun CardArt(def: CardDef, colors: AspectColors, modifier: Modifier = Modifier) {
    val seed = def.id.hashCode()
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val cx = w / 2f
        val cy = h / 2f
        val rng = Random(seed)

        // Weicher Farbverlauf als Untergrund, damit die Linien nicht auf
        // reiner Flaeche schweben.
        drawRect(
            brush = Brush.radialGradient(
                colors = listOf(colors.glow.copy(alpha = 0.35f), Color.Transparent),
                center = Offset(cx, cy),
                radius = maxOf(w, h) * 0.65f,
            ),
        )

        when (def.type) {
            CardType.KREATUR -> drawRays(rng, cx, cy, w, h, colors)
            CardType.SPONTAN -> drawBolts(rng, cx, cy, w, h, colors)
            CardType.RITUAL -> drawRings(rng, cx, cy, w, h, colors)
            CardType.RELIKT -> drawPolygon(rng, cx, cy, w, h, colors)
            CardType.QUELLE -> drawCore(rng, cx, cy, w, h, colors)
        }
    }
}

/** Kreaturen: Strahlen unterschiedlicher Laenge aus der Mitte - wirkt lebendig. */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawRays(
    rng: Random,
    cx: Float,
    cy: Float,
    w: Float,
    h: Float,
    colors: AspectColors,
) {
    val count = rng.nextInt(7, 13)
    val baseRadius = minOf(w, h) * 0.15f
    repeat(count) { i ->
        val angle = (i.toFloat() / count) * 2f * Math.PI.toFloat() + rng.nextFloat() * 0.3f
        val length = baseRadius + rng.nextFloat() * minOf(w, h) * 0.32f
        val startR = baseRadius * 0.5f
        val start = Offset(cx + cos(angle) * startR, cy + sin(angle) * startR)
        val end = Offset(cx + cos(angle) * length, cy + sin(angle) * length)
        drawLine(
            color = colors.glow.copy(alpha = 0.55f + rng.nextFloat() * 0.25f),
            start = start,
            end = end,
            strokeWidth = 2.2f + rng.nextFloat() * 2f,
            cap = StrokeCap.Round,
        )
    }
    drawCircle(color = colors.glow.copy(alpha = 0.8f), radius = baseRadius * 0.55f, center = Offset(cx, cy))
    drawCircle(
        color = colors.primary.copy(alpha = 0.9f),
        radius = baseRadius * 0.55f,
        center = Offset(cx, cy),
        style = Stroke(width = 1.5f),
    )
}

/** Spontanzauber: schroffe Blitzlinien - wirkt schnell und scharf. */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawBolts(
    rng: Random,
    cx: Float,
    cy: Float,
    w: Float,
    h: Float,
    colors: AspectColors,
) {
    val boltCount = rng.nextInt(3, 6)
    repeat(boltCount) {
        val angle = rng.nextFloat() * 2f * Math.PI.toFloat()
        var x = cx + cos(angle) * minOf(w, h) * 0.08f
        var y = cy + sin(angle) * minOf(w, h) * 0.08f
        val path = androidx.compose.ui.graphics.Path().apply { moveTo(x, y) }
        val segments = rng.nextInt(3, 5)
        repeat(segments) {
            val step = minOf(w, h) * 0.16f
            x += (rng.nextFloat() - 0.5f) * step * 2f
            y += (rng.nextFloat() - 0.5f) * step * 2f
            path.lineTo(x, y)
        }
        drawPath(
            path = path,
            color = colors.glow.copy(alpha = 0.6f + rng.nextFloat() * 0.3f),
            style = Stroke(width = 2.5f, cap = StrokeCap.Round),
        )
    }
}

/** Rituale: konzentrische, leicht versetzte Ringe - wirkt zeremoniell. */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawRings(
    rng: Random,
    cx: Float,
    cy: Float,
    w: Float,
    h: Float,
    colors: AspectColors,
) {
    val ringCount = rng.nextInt(3, 6)
    val maxRadius = minOf(w, h) * 0.42f
    for (i in 1..ringCount) {
        val radius = maxRadius * i / ringCount
        val offset = Offset(
            cx + (rng.nextFloat() - 0.5f) * 4f,
            cy + (rng.nextFloat() - 0.5f) * 4f,
        )
        drawCircle(
            color = colors.glow.copy(alpha = 0.75f - i * 0.1f),
            radius = radius,
            center = offset,
            style = Stroke(
                width = 1.8f,
                pathEffect = if (i % 2 == 0) PathEffect.dashPathEffect(floatArrayOf(6f, 5f)) else null,
            ),
        )
    }
}

/** Relikte: ein festes Polygon - wirkt wie ein geschliffener Gegenstand. */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawPolygon(
    rng: Random,
    cx: Float,
    cy: Float,
    w: Float,
    h: Float,
    colors: AspectColors,
) {
    val sides = rng.nextInt(5, 8)
    val radius = minOf(w, h) * 0.34f
    val rotation = rng.nextFloat() * 2f * Math.PI.toFloat()
    val path = androidx.compose.ui.graphics.Path()
    for (i in 0 until sides) {
        val angle = rotation + (i.toFloat() / sides) * 2f * Math.PI.toFloat()
        val jitter = radius * (0.85f + rng.nextFloat() * 0.3f)
        val point = Offset(cx + cos(angle) * jitter, cy + sin(angle) * jitter)
        if (i == 0) path.moveTo(point.x, point.y) else path.lineTo(point.x, point.y)
    }
    path.close()
    drawPath(path = path, color = colors.primary.copy(alpha = 0.45f))
    drawPath(path = path, color = colors.glow.copy(alpha = 0.9f), style = Stroke(width = 2f))
}

/** Quellen: ruhiger, pulsierender Kern - unaufgeregt, sie sind Ressourcen. */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawCore(
    rng: Random,
    cx: Float,
    cy: Float,
    w: Float,
    h: Float,
    colors: AspectColors,
) {
    val rings = rng.nextInt(2, 4)
    val maxRadius = minOf(w, h) * 0.3f
    for (i in rings downTo 1) {
        drawCircle(
            color = colors.glow.copy(alpha = 0.25f + (rings - i) * 0.15f),
            radius = maxRadius * i / rings,
            center = Offset(cx, cy),
        )
    }
    drawCircle(color = colors.primary.copy(alpha = 0.95f), radius = maxRadius / rings, center = Offset(cx, cy))
}
