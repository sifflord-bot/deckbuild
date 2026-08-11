package dev.deckbuild.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.deckbuild.app.GameController
import dev.deckbuild.core.engine.Awaiting
import dev.deckbuild.core.engine.Battle
import dev.deckbuild.core.engine.Permanent
import dev.deckbuild.core.engine.Phase
import dev.deckbuild.core.engine.TargetRef
import dev.deckbuild.core.model.CardType
import dev.deckbuild.core.model.Side
import dev.deckbuild.core.session.CastStage
import dev.deckbuild.core.session.TargetingState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun BattleScreen(controller: GameController) {
    // Der Kampf ist ein veraenderliches Objekt; dieser Lesezugriff bindet die
    // Ansicht an jede ausgefuehrte Aktion.
    @Suppress("UNUSED_VARIABLE")
    val version = controller.revision
    val battle = controller.battle ?: return

    Column(
        Modifier
            .fillMaxSize()
            .background(Palette.Background)
            .padding(horizontal = 10.dp, vertical = 6.dp),
    ) {
        PlayerBar(
            battle = battle,
            side = Side.GEGNER,
            controller = controller,
            title = battle.opponentName,
        )

        Spacer(Modifier.height(6.dp))
        BoardRow(battle, controller, Side.GEGNER)

        Spacer(Modifier.height(6.dp))
        MiddleStrip(battle, controller, Modifier.weight(1f))

        Spacer(Modifier.height(6.dp))
        BoardRow(battle, controller, Side.SPIELER)

        Spacer(Modifier.height(6.dp))
        PlayerBar(battle = battle, side = Side.SPIELER, controller = controller, title = "Du")

        Spacer(Modifier.height(6.dp))
        PouchRow(battle, controller)

        Spacer(Modifier.height(6.dp))
        HandRow(battle, controller)

        Spacer(Modifier.height(8.dp))
        ActionBar(battle, controller)
    }
}

// ------------------------------------------------------------------ Kopfleisten

@Composable
private fun PlayerBar(battle: Battle, side: Side, controller: GameController, title: String) {
    // Erzwingt die Neuzeichnung dieser Komposition bei jeder Aktion - ohne
    // diesen Lesezugriff kann Compose sie trotz geaenderter battle/controller-
    // Referenzen ueberspringen (Smart Recomposition), da beide Objekte ueber
    // mehrere Zuege hinweg dieselbe Identitaet behalten.
    @Suppress("UNUSED_VARIABLE")
    val version = controller.revision

    val state = battle.state
    val playerState = state.stateOf(side)
    val isTargetable = controller.isLegalTargetNow(TargetRef.PlayerTarget(side))

    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(Palette.Surface)
            .border(
                if (isTargetable) 2.dp else 0.dp,
                if (isTargetable) Palette.Gold else Color.Transparent,
                RoundedCornerShape(8.dp),
            )
            .clickable(enabled = isTargetable) { controller.tapPlayer(side) }
            .padding(horizontal = 10.dp, vertical = 6.dp),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                title,
                style = MaterialTheme.typography.titleSmall,
                color = if (state.activeSide == side) Palette.Gold else Palette.TextPrimary,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Box {
                Text(
                    "♥ ${playerState.life}",
                    style = MaterialTheme.typography.titleSmall,
                    color = Palette.Health,
                )
                // Fliegende Schadens-/Heilzahl statt stillem Zahlenwechsel -
                // steigt kurz auf, verblasst, und verschwindet wieder.
                FloatingDelta(value = playerState.life, modifier = Modifier.align(Alignment.TopCenter))
            }
            Spacer(Modifier.width(10.dp))
            Text(
                "◈ ${state.availableEssence(side)}",
                style = MaterialTheme.typography.titleSmall,
                color = Palette.Essence,
            )
            Spacer(Modifier.width(10.dp))
            Text(
                "✋ ${playerState.hand.size}  ▤ ${playerState.library.size}",
                style = MaterialTheme.typography.labelSmall,
                color = Palette.TextMuted,
            )
        }
        Spacer(Modifier.height(4.dp))
        Meter(
            fraction = playerState.life.toFloat() / playerState.maxLife.coerceAtLeast(1),
            color = if (side == Side.SPIELER) Palette.Health else Palette.Danger,
        )
    }
}

/**
 * Fliegende +/- Zahl bei Lebenspunkt-Aenderung. Reine Anzeige-Reaktion auf
 * einen Wertwechsel, ohne den Spielzustand zu beruehren - erkennt die
 * Aenderung selbst ueber den letzten gesehenen Wert.
 */
@Composable
private fun FloatingDelta(value: Int, modifier: Modifier = Modifier) {
    var previous by remember { mutableIntStateOf(value) }
    var pendingDelta by remember { mutableStateOf<Int?>(null) }
    var deltaKey by remember { mutableIntStateOf(0) }

    LaunchedEffect(value) {
        if (value != previous) {
            pendingDelta = value - previous
            deltaKey++
            previous = value
        }
    }

    val currentDelta = pendingDelta
    if (currentDelta != null && currentDelta != 0) {
        key(deltaKey) {
            val alpha = remember { Animatable(1f) }
            val offsetY = remember { Animatable(0f) }
            LaunchedEffect(deltaKey) {
                launch { alpha.animateTo(0f, tween(850)) }
                launch { offsetY.animateTo(-26f, tween(850, easing = FastOutSlowInEasing)) }
                delay(850)
                pendingDelta = null
            }
            Text(
                text = if (currentDelta > 0) "+$currentDelta" else "$currentDelta",
                color = if (currentDelta > 0) Palette.Health else Palette.Danger,
                style = MaterialTheme.typography.labelSmall,
                modifier = modifier.graphicsLayer {
                    translationY = offsetY.value
                    this.alpha = alpha.value
                },
            )
        }
    }
}

// --------------------------------------------------------------- Schlachtfeld

@Composable
private fun BoardRow(battle: Battle, controller: GameController, side: Side) {
    // Erzwingt die Neuzeichnung dieser Komposition bei jeder Aktion - ohne
    // diesen Lesezugriff kann Compose sie trotz geaenderter battle/controller-
    // Referenzen ueberspringen (Smart Recomposition), da beide Objekte ueber
    // mehrere Zuege hinweg dieselbe Identitaet behalten.
    @Suppress("UNUSED_VARIABLE")
    val version = controller.revision

    val state = battle.state
    val creatures = state.permanentsOf(side).filter { it.def.type != CardType.QUELLE && !it.isFaceDownSource }
    val sources = state.sources(side)

    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Quellen ${sources.count { !it.tapped }}/${sources.size}",
                style = MaterialTheme.typography.labelSmall,
                color = Palette.TextMuted,
            )
            Spacer(Modifier.width(8.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                items(sources) { source ->
                    val aspect = source.producedAspects.firstOrNull() ?: dev.deckbuild.core.model.Aspect.NEUTRAL
                    Box(
                        Modifier
                            .width(14.dp)
                            .height(14.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(
                                if (source.tapped) Palette.Outline else colorsFor(aspect).glow,
                            ),
                    )
                }
            }
        }

        Spacer(Modifier.height(4.dp))

        if (creatures.isEmpty()) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(88.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Palette.Surface.copy(alpha = 0.4f)),
                contentAlignment = Alignment.Center,
            ) {
                Text("leeres Feld", style = MaterialTheme.typography.labelSmall, color = Palette.TextMuted)
            }
        } else {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.height(88.dp),
            ) {
                items(creatures, key = { it.instanceId }) { permanent ->
                    // animateItem() laesst eine sterbende Kreatur aus der
                    // Reihe gleiten statt schlagartig zu verschwinden, und
                    // ruecken die uebrigen sanft nach.
                    PermanentChip(battle, controller, permanent, Modifier.animateItem())
                }
            }
        }
    }
}

@Composable
private fun PermanentChip(
    battle: Battle,
    controller: GameController,
    permanent: Permanent,
    modifier: Modifier = Modifier,
) {
    // Erzwingt die Neuzeichnung dieser Komposition bei jeder Aktion - ohne
    // diesen Lesezugriff kann Compose sie trotz geaenderter battle/controller-
    // Referenzen ueberspringen (Smart Recomposition), da beide Objekte ueber
    // mehrere Zuege hinweg dieselbe Identitaet behalten.
    @Suppress("UNUSED_VARIABLE")
    val version = controller.revision

    val state = battle.state
    val colors = colorsFor(permanent.def.aspect)
    val power = state.power(permanent)
    val toughness = state.toughness(permanent)
    val keywords = state.keywords(permanent)

    val isSelectedAttacker = permanent.instanceId in controller.selectedAttackers
    val isSelectedBlocker = controller.selectedBlocker == permanent.instanceId
    val blockedTargetId = controller.blockAssignment[permanent.instanceId]
    val isTargetable = controller.isLegalTargetNow(TargetRef.PermanentTarget(permanent.instanceId))

    val borderColor = when {
        isTargetable -> Palette.Gold
        isSelectedAttacker || isSelectedBlocker -> Palette.Danger
        permanent.attacking -> Palette.Danger
        blockedTargetId != null -> Palette.Essence
        else -> colors.glow.copy(alpha = 0.5f)
    }

    // Sanftes Antippen statt starrem Sprung: Angreifer/Blocker heben sich
    // leicht an, damit eine Auswahl auch ohne Farbwechsel sofort auffaellt.
    val liftScale by animateFloatAsState(
        targetValue = if (isSelectedAttacker || isSelectedBlocker || permanent.attacking) 1.06f else 1f,
        animationSpec = tween(180, easing = FastOutSlowInEasing),
        label = "permanentLift",
    )

    Column(
        modifier
            .graphicsLayer { scaleX = liftScale; scaleY = liftScale }
            .width(76.dp)
            .height(88.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(colors.ink)
            .border(if (isTargetable || isSelectedAttacker || isSelectedBlocker) 2.dp else 1.dp, borderColor, RoundedCornerShape(8.dp))
            .alpha(if (permanent.tapped) 0.5f else 1f)
            .clickable { controller.tapPermanent(permanent) }
            .padding(5.dp),
    ) {
        Text(
            permanent.def.name,
            style = MaterialTheme.typography.labelSmall,
            color = Palette.TextPrimary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )

        if (keywords.isNotEmpty()) {
            Text(
                keywords.joinToString("") { keywordGlyph(it) },
                style = MaterialTheme.typography.labelSmall,
                color = colors.glow,
                maxLines = 1,
            )
        }

        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val status = buildString {
                when {
                    permanent.attacking -> append("⚔")
                    blockedTargetId != null -> append("⛨")
                    permanent.summoningSick && permanent.def.type == CardType.KREATUR -> append("z")
                    permanent.tapped -> append("↻")
                }
                // Marken sichtbar machen: Sie sind dauerhaft und veraendern das
                // Kampfrechnen, anders als Verstaerkungen bis zum Zugende.
                if (permanent.counterPower != 0) {
                    if (isNotEmpty()) append(" ")
                    append(if (permanent.counterPower > 0) "+${permanent.counterPower}" else "${permanent.counterPower}")
                }
            }
            Text(status, style = MaterialTheme.typography.labelSmall, color = Palette.Gold)

            if (permanent.def.type == CardType.KREATUR) {
                Text(
                    "$power/${(toughness - permanent.damage).coerceAtLeast(0)}",
                    style = MaterialTheme.typography.titleSmall,
                    color = if (permanent.damage > 0) Palette.Danger else Palette.TextPrimary,
                )
            }
        }
    }
}

// ------------------------------------------------------------- Mittlerer Streifen

@Composable
private fun MiddleStrip(battle: Battle, controller: GameController, modifier: Modifier) {
    // Erzwingt die Neuzeichnung dieser Komposition bei jeder Aktion - ohne
    // diesen Lesezugriff kann Compose sie trotz geaenderter battle/controller-
    // Referenzen ueberspringen (Smart Recomposition), da beide Objekte ueber
    // mehrere Zuege hinweg dieselbe Identitaet behalten.
    @Suppress("UNUSED_VARIABLE")
    val version = controller.revision

    val targeting = controller.targeting

    Column(modifier.fillMaxWidth()) {
        // Sanftes Ein-/Ausblenden statt hartem Sprung: Die Zielaufforderung
        // taucht mitten im Bildschirm auf und soll nicht wie ein Fehler wirken.
        AnimatedVisibility(
            visible = targeting != null,
            enter = fadeIn(tween(180)) + expandVertically(tween(180)),
            exit = fadeOut(tween(140)) + shrinkVertically(tween(140)),
        ) {
            if (targeting != null) {
                Column {
                    CastPrompt(targeting, controller)
                    Spacer(Modifier.height(4.dp))
                }
            }
        }

        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                "Zug ${battle.state.turnNumber} · ${battle.state.phase.label}",
                style = MaterialTheme.typography.labelSmall,
                color = Palette.TextMuted,
            )
            controller.message?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.labelSmall,
                    color = Palette.Danger,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f).clickable { controller.dismissMessage() },
                    textAlign = TextAlign.End,
                )
            }
        }

        Spacer(Modifier.height(4.dp))

        // Kampfprotokoll: Da der Gegnerzug in einem Rutsch abgehandelt wird, ist
        // das Protokoll die einzige Stelle, an der er nachvollziehbar bleibt.
        Box(
            Modifier
                .fillMaxWidth()
                .weight(1f)
                .clip(RoundedCornerShape(6.dp))
                .background(Palette.Surface.copy(alpha = 0.6f))
                .padding(horizontal = 8.dp, vertical = 4.dp),
        ) {
            // Stabiler Schluessel = Position im Gesamtprotokoll (nur
            // anhaengend, nie umsortiert) statt Position in der begrenzten,
            // umgedrehten Ansicht - sonst waeren gleichlautende Zeilen nicht
            // unterscheidbar und animateItem() koennte Zeilen verwechseln.
            val fullLog = battle.log
            val entries = fullLog.takeLast(40).reversed()
                .mapIndexed { i, entry -> (fullLog.size - 1 - i) to entry }
            LazyColumn(reverseLayout = false) {
                items(entries, key = { it.first }) { (_, entry) ->
                    Text(
                        entry.text,
                        style = MaterialTheme.typography.labelSmall,
                        color = when {
                            entry.important -> Palette.Gold
                            entry.side == Side.SPIELER -> Palette.TextPrimary
                            else -> Palette.TextMuted
                        },
                        // Neue Zeilen ruecken sanft in Position statt zu
                        // springen - besonders spuerbar, wenn der Gegnerzug
                        // mehrere Eintraege auf einmal nachliefert.
                        modifier = Modifier.animateItem(),
                    )
                }
            }
        }
    }
}

/**
 * Aufforderung waehrend einer Wirkung in Vorbereitung: erst Modus, dann X,
 * dann Ziele. Welche Stufe gerade ansteht, entscheidet die Sitzung.
 */
@Composable
private fun CastPrompt(targeting: TargetingState, controller: GameController) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(Palette.Gold.copy(alpha = 0.18f))
            .border(1.dp, Palette.Gold, RoundedCornerShape(6.dp))
            .padding(8.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = when (targeting.stage) {
                    CastStage.MODUS -> "${targeting.cardName}: Waehle eins"
                    CastStage.X_WERT -> "${targeting.cardName}: Wie viel Essenz fuer X?"
                    else -> targeting.currentSpec?.prompt ?: "Ziel waehlen"
                },
                style = MaterialTheme.typography.bodySmall,
                color = Palette.Gold,
                modifier = Modifier.weight(1f),
            )
            Text(
                "Abbrechen",
                style = MaterialTheme.typography.labelSmall,
                color = Palette.Danger,
                modifier = Modifier.clickable { controller.cancelTargeting() },
            )
        }

        when (targeting.stage) {
            CastStage.MODUS -> {
                Spacer(Modifier.height(6.dp))
                targeting.modes.forEachIndexed { index, mode ->
                    Text(
                        text = "${index + 1}. ${mode.label}",
                        style = MaterialTheme.typography.labelSmall,
                        color = Palette.TextPrimary,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(Palette.SurfaceHigh)
                            .clickable { controller.chooseMode(index) }
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                    )
                }
            }

            CastStage.X_WERT -> {
                Spacer(Modifier.height(6.dp))
                XPicker(maxX = targeting.maxX) { controller.chooseX(it) }
            }

            else -> Unit
        }
    }
}

/** Schrittweise Auswahl von X - bewusst als Tastenreihe statt Schieberegler. */
@Composable
private fun XPicker(maxX: Int, onPick: (Int) -> Unit) {
    var value by remember(maxX) { mutableIntStateOf(maxX) }

    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        StepButton("−") { if (value > 0) value-- }
        Spacer(Modifier.width(10.dp))
        Text(
            "X = $value",
            style = MaterialTheme.typography.titleSmall,
            color = Palette.TextPrimary,
        )
        Spacer(Modifier.width(10.dp))
        StepButton("+") { if (value < maxX) value++ }
        Spacer(Modifier.width(14.dp))
        Text(
            "Bestaetigen",
            style = MaterialTheme.typography.labelSmall,
            color = Palette.Gold,
            modifier = Modifier
                .clip(RoundedCornerShape(4.dp))
                .background(Palette.Gold.copy(alpha = 0.25f))
                .clickable { onPick(value) }
                .padding(horizontal = 10.dp, vertical = 6.dp),
        )
    }
}

@Composable
private fun StepButton(label: String, onClick: () -> Unit) {
    Text(
        label,
        style = MaterialTheme.typography.titleSmall,
        color = Palette.TextPrimary,
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(Palette.SurfaceHigh)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 4.dp),
    )
}

// -------------------------------------------------------------------- Beutel

@Composable
private fun PouchRow(battle: Battle, controller: GameController) {
    // Erzwingt die Neuzeichnung dieser Komposition bei jeder Aktion - ohne
    // diesen Lesezugriff kann Compose sie trotz geaenderter battle/controller-
    // Referenzen ueberspringen (Smart Recomposition), da beide Objekte ueber
    // mehrere Zuege hinweg dieselbe Identitaet behalten.
    @Suppress("UNUSED_VARIABLE")
    val version = controller.revision

    // Schnappschuss statt Live-Referenz - selber Grund wie bei HandRow: der
    // Beutel ist eine im Spielkern mutierte MutableList, kein Compose-Snapshot.
    val pouch = battle.state.player.pouch.toList()
    if (pouch.isEmpty()) return

    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        itemsIndexed(pouch, key = { _, slot -> slot.def.id }) { index, slot ->
            val colors = colorsFor(slot.def.aspect)
            Row(
                Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(Palette.SurfaceHigh)
                    .border(1.dp, if (slot.isEmpty) Palette.Outline else colors.glow, RoundedCornerShape(6.dp))
                    .alpha(if (slot.isEmpty) 0.45f else 1f)
                    .clickable(enabled = !slot.isEmpty) { controller.useConsumable(index) }
                    .padding(horizontal = 8.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("⚗", style = MaterialTheme.typography.labelSmall, color = colors.glow)
                Spacer(Modifier.width(5.dp))
                Text(slot.def.name, style = MaterialTheme.typography.labelSmall, color = Palette.TextPrimary)
                Spacer(Modifier.width(5.dp))
                Text("${slot.charges}", style = MaterialTheme.typography.labelSmall, color = Palette.Gold)
            }
        }
    }
}

// ---------------------------------------------------------------------- Hand

@Composable
private fun HandRow(battle: Battle, controller: GameController) {
    // Erzwingt die Neuzeichnung dieser Komposition bei jeder Aktion - ohne
    // diesen Lesezugriff kann Compose sie trotz geaenderter battle/controller-
    // Referenzen ueberspringen (Smart Recomposition), da beide Objekte ueber
    // mehrere Zuege hinweg dieselbe Identitaet behalten.
    @Suppress("UNUSED_VARIABLE")
    val version = controller.revision

    // Schnappschuss statt Live-Referenz: battle.state.player.hand ist eine im
    // Spielkern mutierte MutableList (kein Compose-Snapshot). Ohne die Kopie
    // kann eine Karte, die waehrend der verzoegerten Lazy-Komposition gespielt
    // wird, die Liste unter den Fuessen der LazyRow verkuerzen und zu einem
    // IndexOutOfBoundsException fuehren.
    val hand = battle.state.player.hand.toList()

    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.heightIn(min = 128.dp),
    ) {
        items(hand, key = { it.instanceId }) { card ->
            val playable = controller.faceDownMode ||
                battle.canCast(Side.SPIELER, card)
            CardFace(
                def = card.def,
                // animateItem() laesst eine gespielte Karte aus der Hand
                // gleiten statt schlagartig zu verschwinden.
                modifier = Modifier.animateItem(),
                width = 90.dp,
                playable = playable,
                selected = controller.faceDownMode,
            ) { controller.tapHandCard(card) }
        }
    }
}

// ------------------------------------------------------------------ Aktionen

@Composable
private fun ActionBar(battle: Battle, controller: GameController) {
    // Erzwingt die Neuzeichnung dieser Komposition bei jeder Aktion - ohne
    // diesen Lesezugriff kann Compose sie trotz geaenderter battle/controller-
    // Referenzen ueberspringen (Smart Recomposition), da beide Objekte ueber
    // mehrere Zuege hinweg dieselbe Identitaet behalten.
    @Suppress("UNUSED_VARIABLE")
    val version = controller.revision

    val state = battle.state

    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        when {
            battle.awaiting == Awaiting.SPIELER_BLOCK -> {
                ActionButton(
                    text = if (controller.selectedBlocker != null) {
                        "Angreifer antippen"
                    } else {
                        "Blocker waehlen"
                    },
                    accent = Palette.Essence,
                    enabled = false,
                    modifier = Modifier.weight(1f),
                ) {}
                ActionButton(
                    text = "Blocken bestaetigen (${controller.blockAssignment.size})",
                    accent = Palette.Gold,
                    modifier = Modifier.weight(1f),
                ) { controller.confirmBlocks() }
            }

            state.phase == Phase.ANGRIFF -> {
                ActionButton("Kein Angriff", Palette.TextMuted, modifier = Modifier.weight(1f)) {
                    controller.confirmAttack()
                }
                ActionButton(
                    "Angriff (${controller.selectedAttackers.size})",
                    Palette.Danger,
                    modifier = Modifier.weight(1f),
                ) { controller.confirmAttack() }
            }

            state.phase == Phase.HAUPT_1 -> {
                ActionButton(
                    if (controller.faceDownMode) "Verdeckt: an" else "Verdeckt legen",
                    if (controller.faceDownMode) Palette.Gold else Palette.TextMuted,
                    modifier = Modifier.weight(1f),
                ) { controller.toggleFaceDownMode() }
                ActionButton("Zum Kampf", Palette.Danger, modifier = Modifier.weight(1f)) {
                    controller.toCombat()
                }
                ActionButton("Zug beenden", Palette.Essence, modifier = Modifier.weight(1f)) {
                    controller.endTurn()
                }
            }

            else -> {
                ActionButton(
                    if (controller.faceDownMode) "Verdeckt: an" else "Verdeckt legen",
                    if (controller.faceDownMode) Palette.Gold else Palette.TextMuted,
                    modifier = Modifier.weight(1f),
                ) { controller.toggleFaceDownMode() }
                ActionButton("Zug beenden", Palette.Essence, modifier = Modifier.weight(1f)) {
                    controller.endTurn()
                }
            }
        }
    }
}

@Composable
private fun ActionButton(
    text: String,
    accent: Color,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = accent.copy(alpha = 0.22f),
            contentColor = accent,
            disabledContainerColor = Palette.Surface,
            disabledContentColor = Palette.TextMuted,
        ),
    ) {
        Text(text, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
