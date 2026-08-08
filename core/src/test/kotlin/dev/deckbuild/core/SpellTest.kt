package dev.deckbuild.core

import dev.deckbuild.core.content.CardLibrary
import dev.deckbuild.core.engine.GameAction
import dev.deckbuild.core.engine.TargetRef
import dev.deckbuild.core.model.Aspect
import dev.deckbuild.core.model.CardType
import dev.deckbuild.core.model.Side
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SpellTest {

    @Test
    fun `direkter Schaden trifft den Gegner`() {
        val battle = testBattle()
        battle.giveSources(Side.SPIELER, Aspect.GLUT, 1)
        val card = battle.giveCard(CardLibrary.find("glut_sengender_stoss")!!, Side.SPIELER)

        val result = battle.perform(
            GameAction.KarteSpielen(card.instanceId, listOf(TargetRef.PlayerTarget(Side.GEGNER))),
        )

        assertTrue(result.ok, result.message)
        assertEquals(27, battle.state.opponent.life)
        assertTrue(battle.state.stack.isEmpty())
    }

    @Test
    fun `Zauber ohne bezahlbare Kosten wird abgelehnt`() {
        val battle = testBattle()
        val card = battle.giveCard(CardLibrary.find("glut_sengender_stoss")!!, Side.SPIELER)

        val result = battle.perform(
            GameAction.KarteSpielen(card.instanceId, listOf(TargetRef.PlayerTarget(Side.GEGNER))),
        )

        assertFalse(result.ok)
        assertEquals(30, battle.state.opponent.life)
    }

    @Test
    fun `Verbrauchskarten landen in der Verbannung statt im Friedhof`() {
        val battle = testBattle()
        battle.giveSources(Side.SPIELER, Aspect.GLUT, 1)
        val target = battle.place(testCreature("Opfer", 2, 3), Side.GEGNER)
        val card = battle.giveCard(CardLibrary.find("glut_brandopfer")!!, Side.SPIELER)

        battle.perform(
            GameAction.KarteSpielen(card.instanceId, listOf(TargetRef.PermanentTarget(target.instanceId))),
        )

        assertEquals(1, battle.state.player.exile.size)
        assertEquals(0, battle.state.player.graveyard.size)
        assertNull(battle.state.findPermanent(target.instanceId))
    }

    @Test
    fun `Betritt-das-Schlachtfeld-Effekt zieht eine Karte`() {
        val battle = testBattle()
        battle.giveSources(Side.SPIELER, Aspect.FLUT, 3)
        val card = battle.giveCard(CardLibrary.find("flut_nebelkundschafter")!!, Side.SPIELER)
        val handBefore = battle.state.player.hand.size

        battle.perform(GameAction.KarteSpielen(card.instanceId))

        // Eine Karte gespielt, eine gezogen: die Handgroesse bleibt gleich.
        assertEquals(handBefore, battle.state.player.hand.size)
        assertNotNull(battle.state.creatures(Side.SPIELER).firstOrNull { it.def.id == "flut_nebelkundschafter" })
    }

    @Test
    fun `Gegner neutralisiert einen Zauber`() {
        val brain = ScriptedBrain()
        val battle = testBattle(brain)
        battle.giveSources(Side.SPIELER, Aspect.GLUT, 6)
        battle.giveSources(Side.GEGNER, Aspect.FLUT, 2)
        val counter = battle.giveCard(CardLibrary.find("flut_bannwelle")!!, Side.GEGNER)
        val wurm = battle.giveCard(CardLibrary.find("glut_lohenwurm")!!, Side.SPIELER)

        brain.response = { _, item ->
            GameAction.KarteSpielen(counter.instanceId, listOf(TargetRef.StackTarget(item.stackId)))
        }

        battle.perform(GameAction.KarteSpielen(wurm.instanceId))

        assertTrue(battle.state.creatures(Side.SPIELER).isEmpty())
        assertEquals(1, battle.state.player.graveyard.count { it.def.id == "glut_lohenwurm" })
        assertEquals(30, battle.state.opponent.life)
    }

    @Test
    fun `Aufgebot erschafft drei Spielsteine`() {
        val battle = testBattle()
        battle.giveSources(Side.SPIELER, Aspect.LICHT, 3)
        val card = battle.giveCard(CardLibrary.find("licht_aufgebot")!!, Side.SPIELER)

        val result = battle.perform(GameAction.KarteSpielen(card.instanceId))

        assertTrue(result.ok, result.message)
        assertEquals(3, battle.state.creatures(Side.SPIELER).size)
        assertTrue(battle.state.creatures(Side.SPIELER).all { it.def.isToken })
    }

    @Test
    fun `zerstoerte Spielsteine hinterlassen keine Karte im Friedhof`() {
        val battle = testBattle()
        battle.giveSources(Side.SPIELER, Aspect.GLUT, 1)
        val token = battle.place(CardLibrary.find("tok_waechter")!!, Side.GEGNER)
        val bolt = battle.giveCard(CardLibrary.find("glut_sengender_stoss")!!, Side.SPIELER)

        battle.perform(
            GameAction.KarteSpielen(bolt.instanceId, listOf(TargetRef.PermanentTarget(token.instanceId))),
        )

        assertNull(battle.state.findPermanent(token.instanceId))
        assertTrue(battle.state.opponent.graveyard.isEmpty())
    }

    @Test
    fun `statische Faehigkeit staerkt andere eigene Kreaturen`() {
        val battle = testBattle()
        val soldier = battle.place(testCreature("Soldat", 2, 2), Side.SPIELER)
        assertEquals(2, battle.state.power(soldier))

        val master = battle.place(CardLibrary.find("licht_hochmeister")!!, Side.SPIELER)

        assertEquals(3, battle.state.power(soldier))
        assertEquals(3, battle.state.toughness(soldier))
        // Der Hochmeister staerkt sich nicht selbst.
        assertEquals(3, battle.state.power(master))
    }

    @Test
    fun `Ramp erzeugt eine dauerhafte zusaetzliche Quelle`() {
        val battle = testBattle()
        battle.giveSources(Side.SPIELER, Aspect.HAIN, 2)
        val card = battle.giveCard(CardLibrary.find("hain_lebensfluss")!!, Side.SPIELER)

        battle.perform(GameAction.KarteSpielen(card.instanceId))

        assertEquals(3, battle.state.sources(Side.SPIELER).size)
    }

    @Test
    fun `Manakreatur zaehlt beim Bezahlen automatisch mit`() {
        val battle = testBattle()
        battle.giveSources(Side.SPIELER, Aspect.HAIN, 1)
        battle.place(CardLibrary.find("hain_wurzelaeltester")!!, Side.SPIELER)

        // Hain-Quelle plus Manakreatur decken Kosten von 2.
        assertTrue(battle.state.canPay(Side.SPIELER, CardLibrary.find("hain_dickichtwolf")!!.cost))
    }

    @Test
    fun `verdeckt gelegte Karte wird zur farblosen Quelle`() {
        val battle = testBattle()
        val card = battle.giveCard(CardLibrary.find("glut_lohenwurm")!!, Side.SPIELER)

        val result = battle.perform(GameAction.VerdecktLegen(card.instanceId))

        assertTrue(result.ok, result.message)
        assertEquals(1, battle.state.sources(Side.SPIELER).size)
        assertTrue(battle.state.sources(Side.SPIELER).first().isFaceDownSource)
    }

    @Test
    fun `nur eine Quelle pro Zug`() {
        val battle = testBattle()
        val first = battle.giveCard(CardLibrary.QUELLEN.getValue(Aspect.GLUT), Side.SPIELER)
        val second = battle.giveCard(CardLibrary.QUELLEN.getValue(Aspect.GLUT), Side.SPIELER)

        assertTrue(battle.perform(GameAction.QuelleSpielen(first.instanceId)).ok)
        assertFalse(battle.perform(GameAction.QuelleSpielen(second.instanceId)).ok)
    }

    @Test
    fun `Rituale sind im Blockschritt gesperrt`() {
        val battle = testBattle()
        battle.giveSources(Side.SPIELER, Aspect.FLUT, 3)
        val ritual = battle.giveCard(CardLibrary.find("flut_erkenntnis")!!, Side.SPIELER)

        battle.perform(GameAction.ZumKampf)
        val result = battle.perform(GameAction.KarteSpielen(ritual.instanceId))

        assertFalse(result.ok)
    }

    @Test
    fun `Verbrauchsgegenstand heilt und verliert eine Ladung`() {
        val battle = testBattle(playerLife = 10)
        val heiltrank = dev.deckbuild.core.content.ConsumableLibrary.require("kon_heiltrank")
        battle.state.player.pouch += dev.deckbuild.core.model.ConsumableSlot(heiltrank, 2)

        val result = battle.perform(GameAction.GegenstandNutzen(0))

        assertTrue(result.ok, result.message)
        assertEquals(18, battle.state.player.life)
        assertEquals(1, battle.state.player.pouch[0].charges)
    }

    @Test
    fun `Kartenbibliothek ist widerspruchsfrei`() {
        val ids = CardLibrary.all.map { it.id }
        assertEquals(ids.size, ids.distinct().size, "Doppelte Karten-Ids gefunden")

        for (card in CardLibrary.all) {
            if (card.type == CardType.KREATUR) {
                assertTrue(card.toughness > 0, "${card.name} hat keine Widerstandskraft")
            }
            if (card.type == CardType.QUELLE) {
                assertTrue(card.produces.isNotEmpty(), "${card.name} erzeugt keine Essenz")
            }
        }
    }
}
