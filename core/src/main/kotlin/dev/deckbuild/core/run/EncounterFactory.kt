package dev.deckbuild.core.run

import dev.deckbuild.core.ai.HeuristicBrain
import dev.deckbuild.core.content.CardLibrary
import dev.deckbuild.core.content.EnemyBuild
import dev.deckbuild.core.engine.Battle
import dev.deckbuild.core.engine.OpponentBrain
import dev.deckbuild.core.engine.Ruleset
import dev.deckbuild.core.util.Rng

/** Baut aus Laufzustand und Gegnervorlage einen spielfertigen Kampf. */
object EncounterFactory {

    fun create(
        run: RunState,
        enemy: EnemyBuild,
        seed: Long = run.seed * 31 + run.stage,
        ruleset: Ruleset = Ruleset(),
        brain: OpponentBrain = HeuristicBrain(enemy.skill, Rng(seed)),
    ): Battle = Battle(
        ruleset = ruleset,
        playerDeck = RunManager.playerDeck(run),
        opponentDeck = enemy.deck,
        playerLife = run.life,
        opponentLife = enemy.life,
        playerPouch = RunManager.pouchSlots(run),
        rng = Rng(seed),
        brain = brain,
        resolver = CardLibrary,
        opponentName = enemy.name,
        opponentStartingSources = enemy.startingSources,
    ).also { it.start() }
}
