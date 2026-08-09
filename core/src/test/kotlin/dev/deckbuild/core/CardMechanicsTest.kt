package dev.deckbuild.core

import dev.deckbuild.core.content.CardLibrary
import dev.deckbuild.core.engine.GameAction
import dev.deckbuild.core.engine.TargetRef
import dev.deckbuild.core.model.Aspect
import dev.deckbuild.core.model.CardType
import dev.deckbuild.core.model.Side
import dev.deckbuild.core.run.RunManager
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Mechaniken, die erst mit den neuen Karten benutzt werden: Suchen, Opfern,
 * Relikte mit Zug-Ausloesern und Massenentfernung nach Staerke.
 */
class CardMechanicsTest {

    @Test
    fun `Naturruf holt eine Kreatur aus der Bibliothek`() {
        val battle = testBattle(
            playerDeck = List(6) { CardLibrary.QUELLEN.getValue(Aspect.HAIN) } +
                CardLibrary.find("hain_urhornvieh")!! +
                List(6) { CardLibrary.QUELLEN.getValue(Aspect.HAIN) },
        )
        battle.giveSources(Side.SPIELER, Aspect.HAIN, 2)
        val ruf = battle.giveCard(CardLibrary.find("hain_naturruf")!!, Side.SPIELER)
        val bibliothekVorher = battle.state.player.library.size

        val result = battle.perform(GameAction.KarteSpielen(ruf.instanceId))

        assertTrue(result.ok, result.message)
        assertNotNull(
            battle.state.player.hand.firstOrNull { it.def.type == CardType.KREATUR },
            "Es wurde keine Kreatur gefunden",
        )
        assertEquals(bibliothekVorher - 1, battle.state.player.library.size)
    }

    @Test
    fun `Gebeinernte opfert eine eigene Kreatur und zieht nach`() {
        val battle = testBattle()
        battle.giveSources(Side.SPIELER, Aspect.ASCHE, 3)
        val opfer = battle.place(testCreature("Diener", 1, 1), Side.SPIELER)
        val ernte = battle.giveCard(CardLibrary.find("asche_gebeinernte")!!, Side.SPIELER)
        val handVorher = battle.state.player.hand.size

        battle.perform(
            GameAction.KarteSpielen(ernte.instanceId, listOf(TargetRef.PermanentTarget(opfer.instanceId))),
        )

        assertNull(battle.state.findPermanent(opfer.instanceId))
        // Eine Karte gespielt, zwei gezogen.
        assertEquals(handVorher - 1 + 2, battle.state.player.hand.size)
    }

    @Test
    fun `Flammenherz trifft zu Beginn des eigenen Zuges`() {
        val battle = testBattle()
        battle.place(CardLibrary.find("glut_flammenherz")!!, Side.SPIELER)
        assertEquals(30, battle.state.opponent.life)

        battle.perform(GameAction.ZugBeenden)

        assertEquals(29, battle.state.opponent.life)
    }

    @Test
    fun `Hainquell-Essenz steht in der Hauptphase noch bereit`() {
        // Der Essenzvorrat wird beim Enttappen geleert, der Ausloeser feuert
        // danach - sonst waere die Karte wirkungslos.
        val battle = testBattle()
        battle.place(CardLibrary.find("hain_hainquell")!!, Side.SPIELER)

        battle.perform(GameAction.ZugBeenden)

        assertEquals(1, battle.state.player.poolTotal())
    }

    @Test
    fun `Heiligtum heilt am Ende des eigenen Zuges`() {
        val battle = testBattle(playerLife = 20)
        battle.place(CardLibrary.find("licht_heiligtum")!!, Side.SPIELER)

        battle.perform(GameAction.ZugBeenden)

        assertEquals(22, battle.state.player.life)
    }

    @Test
    fun `Letzte Glut schlaegt haerter zu wenn der Gegner angeschlagen ist`() {
        val gesund = testBattle()
        gesund.giveSources(Side.SPIELER, Aspect.GLUT, 2)
        val ersteKarte = gesund.giveCard(CardLibrary.find("glut_letzte_glut")!!, Side.SPIELER)
        gesund.perform(
            GameAction.KarteSpielen(ersteKarte.instanceId, listOf(TargetRef.PlayerTarget(Side.GEGNER))),
        )
        assertEquals(28, gesund.state.opponent.life)

        val angeschlagen = testBattle(opponentLife = 30)
        angeschlagen.state.opponent.life = 12
        angeschlagen.giveSources(Side.SPIELER, Aspect.GLUT, 2)
        val zweiteKarte = angeschlagen.giveCard(CardLibrary.find("glut_letzte_glut")!!, Side.SPIELER)
        angeschlagen.perform(
            GameAction.KarteSpielen(zweiteKarte.instanceId, listOf(TargetRef.PlayerTarget(Side.GEGNER))),
        )
        assertEquals(7, angeschlagen.state.opponent.life)
    }

    @Test
    fun `Gerichtsspruch trifft nur Kreaturen ab Staerke vier`() {
        val battle = testBattle()
        battle.giveSources(Side.SPIELER, Aspect.LICHT, 4)
        val klein = battle.place(testCreature("Knappe", 3, 3), Side.GEGNER)
        val gross = battle.place(testCreature("Hueter", 5, 5), Side.GEGNER)
        val eigenerRiese = battle.place(testCreature("Riese", 6, 6), Side.SPIELER)

        val urteil = battle.giveCard(CardLibrary.find("licht_gerichtsspruch")!!, Side.SPIELER)
        battle.perform(GameAction.KarteSpielen(urteil.instanceId))

        assertNotNull(battle.state.findPermanent(klein.instanceId))
        assertNull(battle.state.findPermanent(gross.instanceId))
        assertNull(battle.state.findPermanent(eigenerRiese.instanceId), "Das Urteil kennt keine Seiten")
    }

    @Test
    fun `Verfluchte Krone staerkt Untote und fordert Leben`() {
        val battle = testBattle(playerLife = 25)
        val untoter = battle.place(CardLibrary.find("asche_grabkriecher")!!, Side.SPIELER)
        val lebender = battle.place(testCreature("Soeldner", 2, 2), Side.SPIELER)
        battle.place(CardLibrary.find("asche_verfluchte_krone")!!, Side.SPIELER)

        assertEquals(3, battle.state.power(untoter))
        assertEquals(2, battle.state.power(lebender))

        battle.perform(GameAction.ZugBeenden)

        assertEquals(24, battle.state.player.life, "Die Krone kostet zu Zugbeginn 1 Leben")
    }

    @Test
    fun `Leviathan tappt beim Erscheinen das gegnerische Feld`() {
        val battle = testBattle()
        battle.giveSources(Side.SPIELER, Aspect.FLUT, 6)
        val a = battle.place(testCreature("Wache", 2, 2), Side.GEGNER)
        val b = battle.place(testCreature("Laeufer", 3, 1), Side.GEGNER)

        val leviathan = battle.giveCard(CardLibrary.find("flut_leviathan")!!, Side.SPIELER)
        battle.perform(GameAction.KarteSpielen(leviathan.instanceId))

        assertTrue(a.tapped && b.tapped)
    }

    @Test
    fun `Gezeitenbruch raeumt nur die gegnerische Seite`() {
        val battle = testBattle()
        battle.giveSources(Side.SPIELER, Aspect.FLUT, 5)
        val fremd = battle.place(CardLibrary.find("neutral_wachkonstrukt")!!, Side.GEGNER)
        val eigen = battle.place(CardLibrary.find("neutral_wachkonstrukt")!!, Side.SPIELER)

        val bruch = battle.giveCard(CardLibrary.find("flut_gezeitenbruch")!!, Side.SPIELER)
        val gegnerhandVorher = battle.state.opponent.hand.size
        battle.perform(GameAction.KarteSpielen(bruch.instanceId))

        assertNull(battle.state.findPermanent(fremd.instanceId))
        assertNotNull(battle.state.findPermanent(eigen.instanceId))
        assertEquals(gegnerhandVorher + 1, battle.state.opponent.hand.size)
    }

    // ------------------------------------------------------------------ Heilung

    @Test
    fun `Rastplatz senkt niemals die Lebenspunkte`() {
        // Kaempfe koennen ueber das Maximum hinaus heilen; ein Rastplatz darf
        // diesen Ueberschuss nicht wieder abschneiden.
        val run = RunManager.startRun("pfad_licht", seed = 4).let { it.copy(life = it.maxLife + 12) }
        val danach = RunManager.restHeal(run)
        assertEquals(run.life, danach.life)
    }

    @Test
    fun `Rastplatz heilt bis hoechstens zum Maximum`() {
        val run = RunManager.startRun("pfad_licht", seed = 5).let { it.copy(life = 5) }
        val danach = RunManager.restHeal(run)
        assertTrue(danach.life > 5)
        assertTrue(danach.life <= run.maxLife)
    }

    @Test
    fun `Ereignisheilung senkt die Lebenspunkte nicht`() {
        val run = RunManager.startRun("pfad_glut", seed = 6).let { it.copy(life = it.maxLife + 7) }
        val (danach, _) = RunManager.resolveEvent(run, "ev_schrein", 0)
        assertEquals(run.life, danach.life)
    }
}
