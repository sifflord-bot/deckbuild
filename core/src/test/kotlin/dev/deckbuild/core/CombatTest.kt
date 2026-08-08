package dev.deckbuild.core

import dev.deckbuild.core.engine.GameAction
import dev.deckbuild.core.model.Keyword
import dev.deckbuild.core.model.Side
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CombatTest {

    @Test
    fun `ungeblockte Kreatur trifft den Gegner`() {
        val battle = testBattle()
        val attacker = battle.place(testCreature("Schlaeger", 4, 4), Side.SPIELER)

        battle.perform(GameAction.ZumKampf)
        battle.perform(GameAction.AngreiferDeklarieren(listOf(attacker.instanceId)))

        assertEquals(26, battle.state.opponent.life)
    }

    @Test
    fun `beschwoerungskranke Kreatur kann nicht angreifen`() {
        val battle = testBattle()
        val attacker = battle.place(testCreature("Frischling", 2, 2), Side.SPIELER)
        attacker.summoningSick = true

        battle.perform(GameAction.ZumKampf)
        val result = battle.perform(GameAction.AngreiferDeklarieren(listOf(attacker.instanceId)))

        assertFalse(result.ok)
        assertEquals(30, battle.state.opponent.life)
    }

    @Test
    fun `Waechter kann nicht angreifen`() {
        val battle = testBattle()
        val wall = battle.place(testCreature("Mauer", 0, 6, setOf(Keyword.WAECHTER)), Side.SPIELER)

        battle.perform(GameAction.ZumKampf)
        val result = battle.perform(GameAction.AngreiferDeklarieren(listOf(wall.instanceId)))

        assertFalse(result.ok)
    }

    @Test
    fun `Wacht verhindert das Tappen beim Angriff`() {
        val battle = testBattle()
        val vigilant = battle.place(testCreature("Wachposten", 3, 3, setOf(Keyword.WACHT)), Side.SPIELER)

        battle.perform(GameAction.ZumKampf)
        battle.perform(GameAction.AngreiferDeklarieren(listOf(vigilant.instanceId)))

        assertFalse(vigilant.tapped)
    }

    @Test
    fun `beidseitig toedlicher Kampf raeumt beide Kreaturen ab`() {
        val brain = ScriptedBrain()
        val battle = testBattle(brain)
        val attacker = battle.place(testCreature("Angreifer", 3, 3), Side.SPIELER)
        val blocker = battle.place(testCreature("Blocker", 3, 3), Side.GEGNER)
        brain.blocks = mapOf(blocker.instanceId to attacker.instanceId)

        battle.perform(GameAction.ZumKampf)
        battle.perform(GameAction.AngreiferDeklarieren(listOf(attacker.instanceId)))

        assertTrue(battle.state.findPermanent(attacker.instanceId) == null)
        assertTrue(battle.state.findPermanent(blocker.instanceId) == null)
        assertEquals(30, battle.state.opponent.life)
    }

    @Test
    fun `Vorstoss toetet vor dem Gegenschlag`() {
        val brain = ScriptedBrain()
        val battle = testBattle(brain)
        val attacker = battle.place(testCreature("Lanzer", 3, 2, setOf(Keyword.VORSTOSS)), Side.SPIELER)
        val blocker = battle.place(testCreature("Blocker", 3, 3), Side.GEGNER)
        brain.blocks = mapOf(blocker.instanceId to attacker.instanceId)

        battle.perform(GameAction.ZumKampf)
        battle.perform(GameAction.AngreiferDeklarieren(listOf(attacker.instanceId)))

        // 3 Schaden toeten den 3/3 vor dessen Gegenschlag - der Lanzer ueberlebt.
        assertTrue(battle.state.findPermanent(blocker.instanceId) == null)
        assertTrue(battle.state.findPermanent(attacker.instanceId) != null)
    }

    @Test
    fun `Gift toetet unabhaengig von der Widerstandskraft`() {
        val brain = ScriptedBrain()
        val battle = testBattle(brain)
        val attacker = battle.place(testCreature("Natter", 1, 1, setOf(Keyword.GIFT)), Side.SPIELER)
        val blocker = battle.place(testCreature("Koloss", 8, 8), Side.GEGNER)
        brain.blocks = mapOf(blocker.instanceId to attacker.instanceId)

        battle.perform(GameAction.ZumKampf)
        battle.perform(GameAction.AngreiferDeklarieren(listOf(attacker.instanceId)))

        assertTrue(battle.state.findPermanent(blocker.instanceId) == null)
    }

    @Test
    fun `Trampeln schiebt Ueberschussschaden durch`() {
        val brain = ScriptedBrain()
        val battle = testBattle(brain)
        val attacker = battle.place(testCreature("Riese", 6, 6, setOf(Keyword.TRAMPELN)), Side.SPIELER)
        val blocker = battle.place(testCreature("Wicht", 1, 2), Side.GEGNER)
        brain.blocks = mapOf(blocker.instanceId to attacker.instanceId)

        battle.perform(GameAction.ZumKampf)
        battle.perform(GameAction.AngreiferDeklarieren(listOf(attacker.instanceId)))

        // 2 Schaden toeten den Blocker, 4 gehen durch.
        assertEquals(26, battle.state.opponent.life)
    }

    @Test
    fun `Flieger sind nur von Flug oder Reichweite blockbar`() {
        val brain = ScriptedBrain()
        val battle = testBattle(brain)
        val flyer = battle.place(testCreature("Falke", 3, 3, setOf(Keyword.FLUG)), Side.SPIELER)
        val ground = battle.place(testCreature("Wache", 4, 4), Side.GEGNER)
        brain.blocks = mapOf(ground.instanceId to flyer.instanceId)

        battle.perform(GameAction.ZumKampf)
        battle.perform(GameAction.AngreiferDeklarieren(listOf(flyer.instanceId)))

        // Der Block ist ungueltig, der Schaden geht durch, beide leben.
        assertEquals(27, battle.state.opponent.life)
        assertTrue(battle.state.findPermanent(flyer.instanceId) != null)
        assertTrue(battle.state.findPermanent(ground.instanceId) != null)
    }

    @Test
    fun `Reichweite darf Flieger blocken`() {
        val brain = ScriptedBrain()
        val battle = testBattle(brain)
        val flyer = battle.place(testCreature("Falke", 3, 3, setOf(Keyword.FLUG)), Side.SPIELER)
        val archer = battle.place(testCreature("Schuetze", 3, 3, setOf(Keyword.REICHWEITE)), Side.GEGNER)
        brain.blocks = mapOf(archer.instanceId to flyer.instanceId)

        battle.perform(GameAction.ZumKampf)
        battle.perform(GameAction.AngreiferDeklarieren(listOf(flyer.instanceId)))

        assertEquals(30, battle.state.opponent.life)
        assertTrue(battle.state.findPermanent(flyer.instanceId) == null)
    }

    @Test
    fun `Zehrung heilt den Beherrscher`() {
        val battle = testBattle(playerLife = 20)
        val leech = battle.place(testCreature("Zehrer", 3, 3, setOf(Keyword.ZEHRUNG)), Side.SPIELER)

        battle.perform(GameAction.ZumKampf)
        battle.perform(GameAction.AngreiferDeklarieren(listOf(leech.instanceId)))

        assertEquals(23, battle.state.player.life)
        assertEquals(27, battle.state.opponent.life)
    }

    @Test
    fun `Schild faengt den ersten Schaden ab`() {
        val brain = ScriptedBrain()
        val battle = testBattle(brain)
        val attacker = battle.place(testCreature("Klinge", 5, 5), Side.SPIELER)
        val shielded = battle.place(testCreature("Behueteter", 1, 1, setOf(Keyword.SCHILD)), Side.GEGNER)
        brain.blocks = mapOf(shielded.instanceId to attacker.instanceId)

        battle.perform(GameAction.ZumKampf)
        battle.perform(GameAction.AngreiferDeklarieren(listOf(attacker.instanceId)))

        assertTrue(battle.state.findPermanent(shielded.instanceId) != null)
        assertFalse(shielded.hasShield)
    }

    @Test
    fun `mehrere Blocker teilen sich den Schaden des Angreifers`() {
        val brain = ScriptedBrain()
        val battle = testBattle(brain)
        val attacker = battle.place(testCreature("Berserker", 5, 6), Side.SPIELER)
        val first = battle.place(testCreature("Erster", 1, 2), Side.GEGNER)
        val second = battle.place(testCreature("Zweiter", 1, 2), Side.GEGNER)
        brain.blocks = mapOf(
            first.instanceId to attacker.instanceId,
            second.instanceId to attacker.instanceId,
        )

        battle.perform(GameAction.ZumKampf)
        battle.perform(GameAction.AngreiferDeklarieren(listOf(attacker.instanceId)))

        assertTrue(battle.state.findPermanent(first.instanceId) == null)
        assertTrue(battle.state.findPermanent(second.instanceId) == null)
        assertTrue(battle.state.findPermanent(attacker.instanceId) != null)
        assertEquals(30, battle.state.opponent.life)
    }

    @Test
    fun `toedlicher Schaden beendet den Kampf`() {
        val battle = testBattle(opponentLife = 3)
        val attacker = battle.place(testCreature("Vollstrecker", 4, 4), Side.SPIELER)

        battle.perform(GameAction.ZumKampf)
        battle.perform(GameAction.AngreiferDeklarieren(listOf(attacker.instanceId)))

        assertTrue(battle.state.isOver)
        assertEquals(Side.SPIELER, battle.state.winner)
    }
}
