package dev.deckbuild.core

import dev.deckbuild.core.content.Enemies
import dev.deckbuild.core.content.StarterDecks
import dev.deckbuild.core.run.EncounterFactory
import dev.deckbuild.core.run.RunManager
import dev.deckbuild.core.sim.AutoPlayer
import dev.deckbuild.core.util.Rng
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Vollstaendige Kaempfe ohne Oberflaeche. Diese Tests suchen keine einzelne
 * Regel, sondern Haenger: Zustaende, aus denen weder Spieler noch KI
 * herauskommen, und Kombinationen, die eine Ausnahme werfen.
 */
class SimulationTest {

    private fun runFor(pathId: String, stage: Int, seed: Long) =
        RunManager.startRun(pathId, seed).copy(stage = stage)

    @Test
    fun `jeder Pfad beendet Kaempfe gegen jeden Gegner`() {
        var battles = 0
        var stalls = 0

        for (path in StarterDecks.all) {
            for (stage in listOf(1, 7, 16)) {
                val run = runFor(path.id, stage, seed = 12345L)
                for (enemy in Enemies.roster.filter { it.minTier <= run.tier }) {
                    val build = Enemies.build(enemy, run.tier, Rng(run.seed + enemy.id.hashCode()))
                    val battle = EncounterFactory.create(run, build, seed = 777L + battles)
                    val result = AutoPlayer.playOut(battle, maxTurns = 45)
                    battles++
                    if (result.stalled) stalls++
                }
            }
        }

        assertTrue(battles > 100, "Zu wenige Partien simuliert: $battles")
        // Einzelne Partien duerfen am Zuglimit enden (echte Kontrollspiegel),
        // aber Haenger duerfen nicht die Regel sein.
        assertTrue(stalls * 10 <= battles, "Zu viele unentschiedene Partien: $stalls von $battles")
    }

    @Test
    fun `Startdecks gewinnen die ersten Gefechte ueberwiegend`() {
        val results = mutableListOf<Boolean>()
        for (path in StarterDecks.all) {
            for (seed in 1L..12L) {
                val run = runFor(path.id, stage = 1, seed = seed)
                val enemy = Enemies.byKind(dev.deckbuild.core.content.EncounterKind.NORMAL, 0).first()
                val build = Enemies.build(enemy, 0, Rng(seed))
                val battle = EncounterFactory.create(run, build, seed = seed * 31)
                val result = AutoPlayer.playOut(battle, maxTurns = 40)
                results += (result.winner == dev.deckbuild.core.model.Side.SPIELER)
            }
        }
        val wins = results.count { it }
        assertTrue(
            wins * 2 >= results.size,
            "Startdecks verlieren die erste Stufe zu oft: $wins von ${results.size}",
        )
    }

    @Test
    fun `hoehere Stufen sind messbar schwerer`() {
        fun winRate(stage: Int): Double {
            var wins = 0
            val rounds = 16
            repeat(rounds) { index ->
                val seed = 500L + index
                val run = runFor("pfad_glut", stage, seed)
                val enemy = Enemies.byKind(dev.deckbuild.core.content.EncounterKind.NORMAL, run.tier).first()
                val build = Enemies.build(enemy, run.tier, Rng(seed))
                val battle = EncounterFactory.create(run, build, seed = seed * 7)
                if (AutoPlayer.playOut(battle, maxTurns = 40).winner == dev.deckbuild.core.model.Side.SPIELER) {
                    wins++
                }
            }
            return wins.toDouble() / rounds
        }

        // Dasselbe Startdeck gegen skalierte Gegner: spaet darf es nicht leichter sein.
        assertTrue(winRate(19) <= winRate(1), "Die Skalierung greift nicht")
    }

    @Test
    fun `Kampf endet auch ohne jede Aktion`() {
        val run = runFor("pfad_hain", stage = 1, seed = 99L)
        val enemy = Enemies.roster.first()
        val build = Enemies.build(enemy, 0, Rng(1))
        val battle = EncounterFactory.create(run, build, seed = 5L)

        // Nur Zuege beenden: Der Gegner muss den Kampf allein entscheiden koennen.
        var guard = 0
        while (!battle.state.isOver && guard++ < 400) {
            when (battle.awaiting) {
                dev.deckbuild.core.engine.Awaiting.SPIELER_BLOCK ->
                    battle.perform(dev.deckbuild.core.engine.GameAction.BlockerDeklarieren(emptyMap()))
                dev.deckbuild.core.engine.Awaiting.SPIELER_AKTION ->
                    battle.perform(dev.deckbuild.core.engine.GameAction.ZugBeenden)
                dev.deckbuild.core.engine.Awaiting.ENDE -> break
            }
        }
        assertTrue(battle.state.isOver, "Passives Spiel fuehrt zu keinem Ergebnis")
        assertTrue(battle.state.winner == dev.deckbuild.core.model.Side.GEGNER)
    }
}
