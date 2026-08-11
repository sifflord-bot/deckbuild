package dev.deckbuild.app.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.deckbuild.core.model.Aspect
import dev.deckbuild.core.model.CardDef
import dev.deckbuild.core.model.CardType
import dev.deckbuild.core.model.Keyword

/**
 * Kartenansicht ohne Bildmaterial. Jede Karte bekommt ihre Identitaet aus
 * Aspektfarbe, Kartentyp und einer prozeduralen Flaeche - das haelt die App
 * klein und erspart Lizenzfragen bei Illustrationen.
 */
@Composable
fun CardFace(
    def: CardDef,
    modifier: Modifier = Modifier,
    width: androidx.compose.ui.unit.Dp = 108.dp,
    playable: Boolean = true,
    selected: Boolean = false,
    footer: String? = null,
    onClick: (() -> Unit)? = null,
) {
    val colors = colorsFor(def.aspect)
    val height = width * 1.42f
    val borderColor = when {
        selected -> Palette.Gold
        playable -> colors.glow
        else -> Palette.Outline
    }

    // Tap-Feedback: die Karte weicht kurz zurueck, sobald der Finger sie
    // beruehrt - fuehlt sich wie Papier an, nicht wie eine tote Flaeche.
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = if (pressed) 0.94f else 1f,
        animationSpec = tween(120, easing = FastOutSlowInEasing),
        label = "cardPress",
    )

    Column(
        modifier = modifier
            .graphicsLayer { scaleX = pressScale; scaleY = pressScale }
            .width(width)
            .height(height)
            .clip(RoundedCornerShape(10.dp))
            .background(
                Brush.verticalGradient(
                    listOf(
                        colors.primary.copy(alpha = 0.85f),
                        colors.ink,
                        Color.Black.copy(alpha = 0.92f),
                    ),
                ),
            )
            .border(if (selected) 2.5.dp else 1.5.dp, borderColor, RoundedCornerShape(10.dp))
            .alpha(if (playable) 1f else 0.55f)
            .then(
                if (onClick != null) {
                    Modifier.clickable(
                        interactionSource = interactionSource,
                        indication = null,
                        onClick = onClick,
                    )
                } else {
                    Modifier
                },
            )
            .padding(6.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top,
        ) {
            Text(
                text = def.name,
                style = MaterialTheme.typography.labelSmall,
                color = Palette.TextPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (!def.cost.isFree) {
                CostPill(def)
            }
        }

        Spacer(Modifier.height(3.dp))

        // Prozedurale "Illustration": ein deterministisches Muster je Karte,
        // siehe CardArt.kt - keine Bilddatei, keine Lizenzfrage.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(width * 0.38f)
                .clip(RoundedCornerShape(6.dp))
                .background(colors.ink),
            contentAlignment = Alignment.Center,
        ) {
            CardArt(def = def, colors = colors, modifier = Modifier.fillMaxWidth().height(width * 0.38f))
            Text(
                text = typeGlyph(def.type),
                color = Color.Black.copy(alpha = 0.35f),
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.align(Alignment.BottomEnd).padding(3.dp),
            )
        }

        Spacer(Modifier.height(3.dp))

        Text(
            text = def.typeLine + if (def.aspect != Aspect.NEUTRAL) " · ${def.aspect.label}" else "",
            style = MaterialTheme.typography.labelSmall,
            color = colors.glow,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )

        Text(
            text = footer ?: def.rulesText,
            style = MaterialTheme.typography.labelSmall,
            color = Palette.TextMuted,
            maxLines = 4,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )

        if (def.type == CardType.KREATUR) {
            Text(
                text = "${def.power}/${def.toughness}",
                style = MaterialTheme.typography.titleSmall,
                color = Palette.TextPrimary,
                textAlign = TextAlign.End,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun CostPill(def: CardDef) {
    Box(
        modifier = Modifier
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.6f))
            .border(1.dp, colorsFor(def.aspect).glow, CircleShape)
            .padding(horizontal = 5.dp, vertical = 1.dp),
    ) {
        Text(
            text = def.cost.render(),
            style = MaterialTheme.typography.labelSmall,
            color = Palette.TextPrimary,
        )
    }
}

private fun typeGlyph(type: CardType): String = when (type) {
    CardType.KREATUR -> "✥"
    CardType.SPONTAN -> "✦"
    CardType.RITUAL -> "❋"
    CardType.RELIKT -> "◈"
    CardType.QUELLE -> "◉"
}

/** Kurzzeichen fuer Schluesselwoerter auf kleinen Kartenansichten. */
fun keywordGlyph(keyword: Keyword): String = when (keyword) {
    Keyword.FLINK -> "»"
    Keyword.WACHT -> "◇"
    Keyword.FLUG -> "▲"
    Keyword.REICHWEITE -> "↑"
    Keyword.TRAMPELN -> "≫"
    Keyword.VORSTOSS -> "!"
    Keyword.GIFT -> "☠"
    Keyword.ZEHRUNG -> "♥"
    Keyword.WAECHTER -> "■"
    Keyword.UNBLOCKBAR -> "~"
    Keyword.SCHILD -> "◐"
    Keyword.VERBRAUCH -> "×"
}

/** Kleine Statuszeile, wie sie ueberall in der Oberflaeche auftaucht. */
@Composable
fun StatChip(
    label: String,
    value: String,
    color: Color = Palette.TextPrimary,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(Palette.SurfaceHigh)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = Palette.TextMuted)
        Spacer(Modifier.width(4.dp))
        Text(value, style = MaterialTheme.typography.titleSmall, color = color)
    }
}

/** Ein schlichter Balken - fuer Lebenspunkte, ohne Abhaengigkeit von Fortschrittsanzeigen. */
@Composable
fun Meter(
    fraction: Float,
    color: Color,
    modifier: Modifier = Modifier,
    height: androidx.compose.ui.unit.Dp = 6.dp,
) {
    // Tweent zum neuen Wert statt zu springen - Schaden und Heilung wirken
    // dadurch wie ein Ereignis, nicht wie ein stiller Zahlenwechsel.
    val animatedFraction by animateFloatAsState(
        targetValue = fraction.coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 420, easing = FastOutSlowInEasing),
        label = "meterFraction",
    )
    Box(
        modifier = modifier
            .height(height)
            .clip(RoundedCornerShape(3.dp))
            .background(Palette.SurfaceHigh),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(animatedFraction)
                .height(height)
                .clip(RoundedCornerShape(3.dp))
                .background(color),
        )
    }
}

/** Abschnittstitel mit dezenter Linie darunter. */
@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Text(text, style = MaterialTheme.typography.titleMedium, color = Palette.Gold)
        Spacer(Modifier.height(4.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(Palette.Outline),
        )
    }
}

/** Grosse Auswahlflaeche fuer Menue- und Stationslisten. */
@Composable
fun ChoiceCard(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    accent: Color = Palette.Gold,
    leading: String? = null,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Palette.Surface)
            .border(1.dp, if (enabled) accent.copy(alpha = 0.6f) else Palette.Outline, RoundedCornerShape(12.dp))
            .alpha(if (enabled) 1f else 0.5f)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(PaddingValues(horizontal = 14.dp, vertical = 12.dp)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leading != null) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(accent.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center,
            ) {
                Text(leading, style = MaterialTheme.typography.titleMedium, color = accent)
            }
            Spacer(Modifier.width(12.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = Palette.TextPrimary)
            if (subtitle.isNotBlank()) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = Palette.TextMuted,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
