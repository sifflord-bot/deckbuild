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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.deckbuild.app.GameController
import dev.deckbuild.core.session.Screen
import dev.deckbuild.core.content.CardLibrary
import dev.deckbuild.core.content.StarterDecks
import dev.deckbuild.core.meta.MetaService
import dev.deckbuild.core.model.CardDef

@Composable
fun HomeScreen(controller: GameController) {
    val meta = controller.meta
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(listOf(Palette.Background, Palette.Surface, Palette.Background)),
            )
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(40.dp))
        Text("ASCHEPFAD", style = MaterialTheme.typography.titleLarge, color = Palette.Gold)
        Text(
            "Ein endloser Zug durch die Aschelande",
            style = MaterialTheme.typography.bodySmall,
            color = Palette.TextMuted,
        )

        Spacer(Modifier.height(28.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatChip("Beste Stufe", meta.bestStage.toString(), Palette.Gold)
            StatChip("Siege", meta.totalVictories.toString(), Palette.Health)
            StatChip("Karten", "${meta.unlockedCards.size}/${CardLibrary.spells.size}", Palette.Essence)
        }

        Spacer(Modifier.height(28.dp))

        val runState = controller.run
        if (runState != null && !runState.over) {
            ChoiceCard(
                title = "Lauf fortsetzen",
                subtitle = "Stufe ${runState.stage} · ${runState.life}/${runState.maxLife} Leben · " +
                    "${runState.deckSize} Karten",
                leading = "▶",
                accent = Palette.Health,
            ) { controller.go(Screen.Karte) }
            Spacer(Modifier.height(10.dp))
        }

        ChoiceCard(
            title = "Neuer Lauf",
            subtitle = "Waehle einen Pfad und beginne von vorn.",
            leading = "✦",
        ) { controller.go(Screen.PfadWahl) }

        Spacer(Modifier.height(10.dp))

        ChoiceCard(
            title = "Kompendium",
            subtitle = "Alle Karten, freigeschaltet und noch verborgen.",
            leading = "◈",
            accent = Palette.Essence,
        ) { controller.go(Screen.Kompendium) }

        Spacer(Modifier.height(24.dp))
        Text(
            "Aspekte: Glut · Flut · Asche · Hain · Licht",
            style = MaterialTheme.typography.bodySmall,
            color = Palette.TextMuted,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
fun PathScreen(controller: GameController) {
    val meta = controller.meta
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        BackRow("Pfad waehlen") { controller.go(Screen.Home) }

        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(StarterDecks.all) { deck ->
                val unlocked = meta.isPathUnlocked(deck.id)
                val colors = colorsFor(deck.aspect)
                ChoiceCard(
                    title = deck.name,
                    subtitle = if (unlocked) {
                        "${deck.flavor}\n${deck.startingLife} Leben · ${deck.cards.size + deck.sources} Karten"
                    } else {
                        "Gesperrt: ${deck.unlockHint}"
                    },
                    leading = deck.aspect.short,
                    accent = colors.glow,
                    enabled = unlocked,
                ) { controller.startRun(deck.id) }
            }
        }
    }
}

@Composable
fun CompendiumScreen(controller: GameController) {
    val meta = controller.meta
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        BackRow("Kompendium") { controller.go(Screen.Home) }
        Text(
            "${meta.unlockedCards.size} von ${CardLibrary.spells.size} Karten freigeschaltet",
            style = MaterialTheme.typography.bodySmall,
            color = Palette.TextMuted,
        )
        Spacer(Modifier.height(10.dp))

        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 112.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.weight(1f),
        ) {
            items(CardLibrary.spells) { card ->
                val unlocked = meta.isCardUnlocked(card.id)
                if (unlocked) {
                    CardFace(card, playable = true)
                } else {
                    LockedCard(card)
                }
            }
        }
    }
}

@Composable
private fun LockedCard(card: CardDef) {
    Column(
        modifier = Modifier
            .width(108.dp)
            .height(108.dp * 1.42f)
            .background(Palette.Surface)
            .padding(8.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("?", style = MaterialTheme.typography.titleLarge, color = Palette.Outline)
        Spacer(Modifier.height(6.dp))
        Text(
            MetaService.unlockHint(card.id),
            style = MaterialTheme.typography.labelSmall,
            color = Palette.TextMuted,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
fun DeckScreen(controller: GameController, onBack: () -> Unit) {
    val runState = controller.run ?: return
    val grouped = runState.deck
        .mapNotNull { CardLibrary.find(it) }
        .groupBy { it.id }
        .values
        .sortedWith(compareBy({ it.first().type.ordinal }, { it.first().manaValue }))

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        BackRow("Dein Deck (${runState.deckSize})", onBack)
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 112.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.weight(1f),
        ) {
            items(grouped) { copies ->
                val card = copies.first()
                CardFace(
                    def = card,
                    footer = if (copies.size > 1) "${copies.size}x · ${card.rulesText}" else null,
                )
            }
        }
    }
}

@Composable
fun RunOverScreen(controller: GameController) {
    val runState = controller.run
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("Der Pfad endet hier", style = MaterialTheme.typography.titleLarge, color = Palette.Danger)
        Spacer(Modifier.height(12.dp))
        Text(
            "Stufe ${runState?.stage ?: 0} erreicht · ${runState?.victories ?: 0} Siege",
            style = MaterialTheme.typography.titleSmall,
            color = Palette.TextPrimary,
        )

        val unlocks = controller.newUnlocks
        if (unlocks.isNotEmpty()) {
            Spacer(Modifier.height(20.dp))
            SectionTitle("Neu im Kompendium")
            LazyColumn(
                modifier = Modifier.height(160.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                items(unlocks) { id ->
                    Text(
                        "◈ ${controller.cardName(id)}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Palette.Gold,
                    )
                }
            }
        }

        Spacer(Modifier.height(28.dp))
        ChoiceCard("Zurueck ins Lager", "Neuen Lauf beginnen oder das Kompendium ansehen.", leading = "⌂") {
            controller.go(Screen.Home)
        }
    }
}

/** Kopfzeile mit Zurueck-Schaltflaeche - in allen Menues identisch. */
@Composable
fun BackRow(title: String, onBack: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TextButton(onClick = onBack) {
            Text("‹ Zurueck", color = Palette.TextMuted)
        }
        Spacer(Modifier.width(4.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, color = Palette.Gold)
    }
    Box(Modifier.fillMaxWidth().height(1.dp).background(Palette.Outline))
    Spacer(Modifier.height(10.dp))
}
