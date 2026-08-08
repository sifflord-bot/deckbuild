package dev.deckbuild.core.sim

import dev.deckbuild.core.ai.BrainSkill
import dev.deckbuild.core.ai.HeuristicBrain
import dev.deckbuild.core.engine.Awaiting
import dev.deckbuild.core.engine.Battle
import dev.deckbuild.core.engine.GameAction
import dev.deckbuild.core.engine.OpponentBrain
import dev.deckbuild.core.engine.Phase
import dev.deckbuild.core.model.Side
import dev.deckbuild.core.util.Rng

/**
 * Spielt einen Kampf ohne Oberflaeche zu Ende, indem die Heuristik-KI auch die
 * Spielerseite uebernimmt.
 *
 * Zweck ist doppelt: In Tests deckt sie Regelfehler und Haenger auf, und fuer
 * das Balancing lassen sich damit Gewinnraten ganzer Deck-Paarungen messen.
 */
object AutoPlayer {

    data class Result(
        val winner: Side?,
        val turns: Int,
        val playerLife: Int,
        val opponentLife: Int,
        val stalled: Boolean,
    )

    fun playOut(
        battle: Battle,
        playerBrain: OpponentBrain = HeuristicBrain(BrainSkill.NORMAL, Rng(1)),
        maxTurns: Int = 60,
    ): Result {
        var failures = 0
        var guard = 0
        val guardLimit = maxTurns * 40

        while (battle.awaiting != Awaiting.ENDE && !battle.state.isOver && guard++ < guardLimit) {
            if (battle.state.turnNumber > maxTurns) break

            val result = when (battle.awaiting) {
                Awaiting.SPIELER_BLOCK -> {
                    val assignment = playerBrain.chooseBlockers(battle, Side.SPIELER, battle.pendingAttackers)
                    battle.perform(GameAction.BlockerDeklarieren(assignment))
                }

                Awaiting.SPIELER_AKTION -> when (battle.state.phase) {
                    Phase.HAUPT_1 -> {
                        val action = playerBrain.nextMainPhaseAction(battle, Side.SPIELER)
                        battle.perform(action ?: GameAction.ZumKampf)
                    }

                    Phase.ANGRIFF -> {
                        val attackers = playerBrain.chooseAttackers(battle, Side.SPIELER)
                        battle.perform(GameAction.AngreiferDeklarieren(attackers))
                    }

                    else -> {
                        val action = playerBrain.nextMainPhaseAction(battle, Side.SPIELER)
                        battle.perform(action ?: GameAction.ZugBeenden)
                    }
                }

                Awaiting.ENDE -> break
            }

            if (!result.ok) {
                failures++
                // Nach wiederholt ungueltigen Aktionen den Zug erzwungen beenden,
                // damit ein Regelfehler nicht zur Endlosschleife wird.
                if (failures >= 3) {
                    failures = 0
                    val forced = battle.perform(GameAction.ZugBeenden)
                    if (!forced.ok) break
                }
            } else {
                failures = 0
            }
        }

        return Result(
            winner = battle.state.winner,
            turns = battle.state.turnNumber,
            playerLife = battle.state.player.life,
            opponentLife = battle.state.opponent.life,
            stalled = battle.state.winner == null,
        )
    }
}
