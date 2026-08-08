package dev.deckbuild.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.deckbuild.app.GameController
import dev.deckbuild.core.session.Screen
import dev.deckbuild.core.content.CardLibrary
import dev.deckbuild.core.content.ConsumableLibrary
import dev.deckbuild.core.run.Events
import dev.deckbuild.core.run.NodeType
import dev.deckbuild.core.run.RunState
import dev.deckbuild.core.run.ShopKind

@Composable
fun MapScreen(controller: GameController) {
    val runState = controller.run ?: return

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        RunHeader(runState, controller)
        Spacer(Modifier.height(12.dp))

        Text(
            if (runState.isBossStage) "Stufe ${runState.stage} · Boss" else "Stufe ${runState.stage} · Waehle deinen Weg",
            style = MaterialTheme.typography.titleMedium,
            color = Palette.Gold,
        )
        Spacer(Modifier.height(10.dp))

        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.weight(1f)) {
            items(runState.nodes.size) { index ->
                val node = runState.nodes[index]
                ChoiceCard(
                    title = "${node.type.label}: ${node.label}",
                    subtitle = node.detail + if (node.goldReward > 0) "\nBelohnung: ${node.goldReward} Gold" else "",
                    leading = node.type.icon,
                    accent = accentFor(node.type),
                ) { controller.chooseNode(index) }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = { controller.go(Screen.Deck) }) {
                Text("Deck ansehen", color = Palette.Essence)
            }
            TextButton(onClick = { controller.abandonRun() }) {
                Text("Lauf aufgeben", color = Palette.Danger)
            }
        }
    }
}

private fun accentFor(type: NodeType) = when (type) {
    NodeType.BOSS -> Palette.Danger
    NodeType.ELITE -> Palette.Gold
    NodeType.RAST -> Palette.Health
    NodeType.HAENDLER -> Palette.Essence
    else -> Palette.TextMuted
}

@Composable
fun RunHeader(runState: RunState, controller: GameController) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            StatChip("Leben", "${runState.life}/${runState.maxLife}", Palette.Health)
            StatChip("Gold", runState.gold.toString(), Palette.Gold)
            StatChip("Deck", runState.deckSize.toString(), Palette.Essence)
        }
        Spacer(Modifier.height(6.dp))
        Meter(runState.life.toFloat() / runState.maxLife.coerceAtLeast(1), Palette.Health)

        if (runState.pouch.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(runState.pouch) { entry ->
                    val def = ConsumableLibrary.find(entry.id)
                    StatChip(def?.name ?: entry.id, "${entry.charges}", Palette.Gold)
                }
            }
        }
        controller.message?.let {
            Spacer(Modifier.height(6.dp))
            Text(it, style = MaterialTheme.typography.bodySmall, color = Palette.Danger)
        }
    }
}

@Composable
fun RewardScreen(controller: GameController) {
    val runState = controller.run ?: return
    val rewards = runState.pendingRewards ?: return

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("Sieg", style = MaterialTheme.typography.titleLarge, color = Palette.Gold)
        Text(
            "${rewards.gold} Gold erhalten" +
                (rewards.consumableId?.let { " · ${ConsumableLibrary.find(it)?.name}" } ?: "") +
                (if (rewards.maxLifeBonus > 0) " · +${rewards.maxLifeBonus} maximale Leben" else ""),
            style = MaterialTheme.typography.bodySmall,
            color = Palette.TextMuted,
        )

        Spacer(Modifier.height(16.dp))
        SectionTitle("Waehle eine Karte")

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.weight(1f),
        ) {
            items(rewards.cardChoices) { id ->
                val card = CardLibrary.find(id)
                if (card != null) {
                    CardFace(card, width = 132.dp) { controller.takeReward(id) }
                }
            }
        }

        Spacer(Modifier.height(10.dp))
        ChoiceCard(
            title = "Ueberspringen",
            subtitle = "Kein neue Karte, dafuer 30 Gold extra. Ein schlankes Deck zieht verlaesslicher.",
            leading = "→",
            accent = Palette.TextMuted,
        ) { controller.takeReward(null) }
    }
}

@Composable
fun RestScreen(controller: GameController) {
    val runState = controller.run ?: return
    var removing by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("Rastplatz", style = MaterialTheme.typography.titleLarge, color = Palette.Health)
        Text(
            "Ein Feuer, etwas Ruhe. Beides reicht nur fuer eine Sache.",
            style = MaterialTheme.typography.bodySmall,
            color = Palette.TextMuted,
        )
        Spacer(Modifier.height(16.dp))

        if (!removing) {
            ChoiceCard(
                title = "Ausruhen",
                subtitle = "Heile 35% deiner maximalen Leben (aktuell ${runState.life}/${runState.maxLife}).",
                leading = "♥",
                accent = Palette.Health,
            ) { controller.restHeal() }

            Spacer(Modifier.height(10.dp))

            ChoiceCard(
                title = "Deck verschlanken",
                subtitle = "Entferne eine Karte dauerhaft. Weniger Karten bedeuten verlaesslichere Zuege.",
                leading = "✂",
                accent = Palette.Essence,
            ) { removing = true }
        } else {
            SectionTitle("Welche Karte soll gehen?")
            val cards = runState.deck.mapNotNull { CardLibrary.find(it) }.distinctBy { it.id }
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(1f)) {
                items(cards) { card ->
                    val count = runState.deck.count { it == card.id }
                    ChoiceCard(
                        title = "${card.name} (${count}x)",
                        subtitle = card.rulesText.ifBlank { "${card.power}/${card.toughness}" },
                        leading = card.aspect.short,
                        accent = colorsFor(card.aspect).glow,
                    ) { controller.restRemove(card.id) }
                }
            }
            TextButton(onClick = { removing = false }) {
                Text("Doch lieber ausruhen", color = Palette.TextMuted)
            }
        }

        Spacer(Modifier.height(8.dp))
        Text(
            "Der Beutel wird an jedem Rastplatz wieder aufgefuellt.",
            style = MaterialTheme.typography.labelSmall,
            color = Palette.TextMuted,
        )
    }
}

@Composable
fun ShopScreen(controller: GameController) {
    val runState = controller.run ?: return
    var removalPick by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Haendler", style = MaterialTheme.typography.titleLarge, color = Palette.Essence)
            StatChip("Gold", runState.gold.toString(), Palette.Gold)
        }
        controller.message?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = Palette.Danger)
        }
        Spacer(Modifier.height(12.dp))

        if (removalPick) {
            SectionTitle("Karte zum Entfernen waehlen")
            val offer = runState.shop.firstOrNull { it.kind == ShopKind.ENTFERNEN }
            val cards = runState.deck.mapNotNull { CardLibrary.find(it) }.distinctBy { it.id }
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(1f)) {
                items(cards) { card ->
                    ChoiceCard(
                        title = card.name,
                        subtitle = card.rulesText.ifBlank { "${card.power}/${card.toughness}" },
                        leading = card.aspect.short,
                        accent = colorsFor(card.aspect).glow,
                    ) {
                        if (offer != null) controller.buy(offer, card.id)
                        removalPick = false
                    }
                }
            }
            TextButton(onClick = { removalPick = false }) { Text("Abbrechen", color = Palette.TextMuted) }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.weight(1f)) {
                items(runState.shop) { offer ->
                    val affordable = runState.gold >= offer.price
                    ChoiceCard(
                        title = "${offer.label} · ${offer.price} Gold",
                        subtitle = offer.detail,
                        leading = when (offer.kind) {
                            ShopKind.KARTE -> "✥"
                            ShopKind.GEGENSTAND -> "⚗"
                            ShopKind.ENTFERNEN -> "✂"
                            ShopKind.HEILUNG -> "♥"
                        },
                        accent = if (affordable) Palette.Gold else Palette.Outline,
                        enabled = affordable,
                    ) {
                        if (offer.kind == ShopKind.ENTFERNEN) removalPick = true else controller.buy(offer)
                    }
                }
            }
        }

        ChoiceCard("Weiterziehen", "Der Haendler bleibt zurueck.", leading = "→", accent = Palette.TextMuted) {
            controller.continueAfterNode()
        }
    }
}

@Composable
fun EventScreen(controller: GameController, eventId: String) {
    val event = Events.find(eventId) ?: return
    val result = controller.flavorResult

    Column(Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState())) {
        Text(event.title, style = MaterialTheme.typography.titleLarge, color = Palette.Gold)
        Spacer(Modifier.height(10.dp))
        Text(event.text, style = MaterialTheme.typography.bodyMedium, color = Palette.TextPrimary)
        Spacer(Modifier.height(20.dp))

        if (result == null) {
            event.choices.forEachIndexed { index, choice ->
                ChoiceCard(choice.label, choice.detail, leading = "${index + 1}") {
                    controller.resolveEvent(eventId, index)
                }
                Spacer(Modifier.height(10.dp))
            }
        } else {
            Box(
                Modifier
                    .fillMaxWidth()
                    .background(Palette.SurfaceHigh)
                    .padding(14.dp),
            ) {
                Text(result, style = MaterialTheme.typography.bodyMedium, color = Palette.Health)
            }
            Spacer(Modifier.height(16.dp))
            ChoiceCard("Weiterziehen", "", leading = "→") { controller.continueAfterNode() }
        }
    }
}

@Composable
fun TreasureScreen(controller: GameController) {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("Fundstelle", style = MaterialTheme.typography.titleLarge, color = Palette.Gold)
        Spacer(Modifier.height(12.dp))
        Text(
            controller.flavorResult ?: "Nichts von Wert.",
            style = MaterialTheme.typography.titleSmall,
            color = Palette.TextPrimary,
        )
        Spacer(Modifier.height(24.dp))
        ChoiceCard("Weiterziehen", "", leading = "→") { controller.continueAfterNode() }
    }
}
