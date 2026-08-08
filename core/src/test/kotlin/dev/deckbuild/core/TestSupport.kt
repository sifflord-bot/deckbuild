package dev.deckbuild.core

import dev.deckbuild.core.content.CardLibrary
import dev.deckbuild.core.engine.Battle
import dev.deckbuild.core.engine.CardInstance
import dev.deckbuild.core.engine.GameAction
import dev.deckbuild.core.engine.OpponentBrain
import dev.deckbuild.core.engine.Permanent
import dev.deckbuild.core.engine.Ruleset
import dev.deckbuild.core.engine.StackItem
import dev.deckbuild.core.model.Aspect
import dev.deckbuild.core.model.CardDef
import dev.deckbuild.core.model.CardType
import dev.deckbuild.core.model.Keyword
import dev.deckbuild.core.model.Side
import dev.deckbuild.core.util.Rng

/** Gegner-Attrappe mit fest vorgegebenen Entscheidungen. */
class ScriptedBrain(
    var blocks: Map<Int, Int> = emptyMap(),
    var attackers: List<Int> = emptyList(),
    var mainPhase: (Battle) -> GameAction? = { null },
    var response: (Battle, StackItem) -> GameAction? = { _, _ -> null },
) : OpponentBrain {
    override fun nextMainPhaseAction(battle: Battle, side: Side): GameAction? = mainPhase(battle)

    override fun chooseAttackers(battle: Battle, side: Side): List<Int> = attackers

    override fun chooseBlockers(battle: Battle, side: Side, attackers: List<Permanent>): Map<Int, Int> = blocks

    override fun respondToSpell(battle: Battle, side: Side, item: StackItem): GameAction? = response(battle, item)
}

/** Testkreatur mit frei waehlbaren Werten - unabhaengig von der echten Kartenliste. */
fun testCreature(
    name: String,
    power: Int,
    toughness: Int,
    keywords: Set<Keyword> = emptySet(),
    aspect: Aspect = Aspect.NEUTRAL,
): CardDef = CardDef(
    id = "test_${name.lowercase()}",
    name = name,
    type = CardType.KREATUR,
    aspect = aspect,
    power = power,
    toughness = toughness,
    keywords = keywords,
)

/** Kampf mit leerem Schlachtfeld und reinen Quellendecks - stoerungsfreie Testbasis. */
fun testBattle(
    brain: OpponentBrain = ScriptedBrain(),
    playerLife: Int = 30,
    opponentLife: Int = 30,
    playerDeck: List<CardDef> = List(20) { CardLibrary.QUELLEN.getValue(Aspect.NEUTRAL) },
    opponentDeck: List<CardDef> = List(20) { CardLibrary.QUELLEN.getValue(Aspect.NEUTRAL) },
    seed: Long = 42,
): Battle = Battle(
    ruleset = Ruleset(startingHandSize = 3),
    playerDeck = playerDeck,
    opponentDeck = opponentDeck,
    playerLife = playerLife,
    opponentLife = opponentLife,
    rng = Rng(seed),
    brain = brain,
    resolver = CardLibrary,
).also { it.start() }

/** Setzt ein einsatzbereites Permanent aufs Schlachtfeld. */
fun Battle.place(def: CardDef, side: Side, tapped: Boolean = false): Permanent {
    val permanent = Permanent(CardInstance(state.allocateInstanceId(), def), side)
    permanent.summoningSick = false
    permanent.tapped = tapped
    if (Keyword.SCHILD in def.keywords) permanent.hasShield = true
    state.battlefield += permanent
    return permanent
}

/** Legt eine Karte direkt auf die Hand, ohne sie zu ziehen. */
fun Battle.giveCard(def: CardDef, side: Side): CardInstance {
    val instance = CardInstance(state.allocateInstanceId(), def)
    state.stateOf(side).hand += instance
    return instance
}

/** Stellt genug ungetappte Quellen bereit, um beliebige Kosten zu bezahlen. */
fun Battle.giveSources(side: Side, aspect: Aspect, count: Int) {
    repeat(count) { place(CardLibrary.QUELLEN.getValue(aspect), side) }
}
