package dev.deckbuild.core.content

import dev.deckbuild.core.model.Aspect
import dev.deckbuild.core.model.CardType
import dev.deckbuild.core.model.ConsumableDef
import dev.deckbuild.core.model.ControllerScope
import dev.deckbuild.core.model.Effect
import dev.deckbuild.core.model.Filter
import dev.deckbuild.core.model.Keyword
import dev.deckbuild.core.model.Rarity
import dev.deckbuild.core.model.Selector
import dev.deckbuild.core.model.TargetKind
import dev.deckbuild.core.model.TargetSpec
import dev.deckbuild.core.model.Value

/**
 * Verbrauchsgegenstaende des Beutels. Sie kosten keine Essenz und sind auch
 * dann einsetzbar, wenn der Gegner bereits angreift - der Notausgang des Laufs.
 */
object ConsumableLibrary {

    private val zielKreatur = TargetSpec(TargetKind.KREATUR, prompt = "Kreatur waehlen")
    private val zielEigeneKreatur = TargetSpec(
        TargetKind.KREATUR,
        Filter(types = setOf(CardType.KREATUR), controller = ControllerScope.EIGENE),
        prompt = "Eigene Kreatur waehlen",
    )
    private val zielBeliebig = TargetSpec(TargetKind.BELIEBIG, prompt = "Ziel waehlen")

    val all: List<ConsumableDef> = listOf(
        ConsumableDef(
            id = "kon_heiltrank",
            name = "Heiltrank",
            maxCharges = 2,
            effect = Effect.Heilung(Selector.Du, Value.of(8)),
            text = "Heile 8 Leben.",
            aspect = Aspect.LICHT,
        ),
        ConsumableDef(
            id = "kon_flammenphiole",
            name = "Flammenphiole",
            maxCharges = 2,
            targets = listOf(zielBeliebig),
            effect = Effect.Schaden(Selector.Chosen(0), Value.of(4)),
            text = "Fuegt einem beliebigen Ziel 4 Schaden zu.",
            aspect = Aspect.GLUT,
        ),
        ConsumableDef(
            id = "kon_essenzstein",
            name = "Essenzstein",
            maxCharges = 2,
            effect = Effect.Essenz(Selector.Du, 3),
            text = "Erhalte sofort 3 Essenz.",
            aspect = Aspect.NEUTRAL,
        ),
        ConsumableDef(
            id = "kon_wachstumselixier",
            name = "Wachstumselixier",
            maxCharges = 1,
            targets = listOf(zielEigeneKreatur),
            effect = Effect.Staerken(Selector.Chosen(0), power = 3, toughness = 3, dauerhaft = true),
            text = "Eine eigene Kreatur erhaelt dauerhaft +3/+3.",
            rarity = Rarity.SELTEN,
            aspect = Aspect.HAIN,
        ),
        ConsumableDef(
            id = "kon_nebelschleier",
            name = "Nebelschleier",
            maxCharges = 1,
            effect = Effect.Antappen(Selector.Alle(Filter.GEGNERISCHE_KREATUREN)),
            text = "Tappe alle gegnerischen Kreaturen.",
            rarity = Rarity.SELTEN,
            aspect = Aspect.FLUT,
        ),
        ConsumableDef(
            id = "kon_schriftrolle",
            name = "Schriftrolle der Weisheit",
            maxCharges = 2,
            effect = Effect.Ziehen(Selector.Du, Value.of(2)),
            text = "Ziehe zwei Karten.",
            aspect = Aspect.FLUT,
        ),
        ConsumableDef(
            id = "kon_totenmuenze",
            name = "Totenmuenze",
            maxCharges = 1,
            effect = Effect.Wiederbeleben(Selector.Du, maxCost = 8),
            text = "Bringe die staerkste Kreatur aus deinem Friedhof ins Spiel.",
            rarity = Rarity.SELTEN,
            aspect = Aspect.ASCHE,
        ),
        ConsumableDef(
            id = "kon_richtstrahl",
            name = "Richtstrahl",
            maxCharges = 1,
            targets = listOf(zielKreatur),
            effect = Effect.Verbannen(Selector.Chosen(0)),
            text = "Verbanne eine Kreatur.",
            rarity = Rarity.LEGENDAER,
            aspect = Aspect.LICHT,
        ),
        ConsumableDef(
            id = "kon_schlachtbanner",
            name = "Schlachtbanner",
            maxCharges = 1,
            effect = Effect.Staerken(
                Selector.Alle(Filter.EIGENE_KREATUREN),
                power = 2,
                toughness = 0,
                keywords = setOf(Keyword.FLINK),
            ),
            text = "Eigene Kreaturen erhalten +2/+0 und Flink bis zum Zugende.",
            rarity = Rarity.SELTEN,
            aspect = Aspect.GLUT,
        ),
    )

    private val byId = all.associateBy { it.id }

    fun find(id: String): ConsumableDef? = byId[id]

    fun require(id: String): ConsumableDef = byId[id] ?: error("Unbekannter Gegenstand: $id")
}
