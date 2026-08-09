package dev.deckbuild.core

import dev.deckbuild.core.content.CardLibrary
import dev.deckbuild.core.engine.Awaiting
import dev.deckbuild.core.engine.GameAction
import dev.deckbuild.core.engine.TargetRef
import dev.deckbuild.core.meta.InMemorySaveStore
import dev.deckbuild.core.meta.SaveService
import dev.deckbuild.core.model.Aspect
import dev.deckbuild.core.model.CardDef
import dev.deckbuild.core.model.CardType
import dev.deckbuild.core.model.Cost
import dev.deckbuild.core.model.Effect
import dev.deckbuild.core.model.Filter
import dev.deckbuild.core.model.Mode
import dev.deckbuild.core.model.Selector
import dev.deckbuild.core.model.Side
import dev.deckbuild.core.model.Value
import dev.deckbuild.core.run.NodeType
import dev.deckbuild.core.session.CastStage
import dev.deckbuild.core.session.GameSession
import dev.deckbuild.core.session.Screen
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** X-Kosten, modale Karten, Staerkemarken und der Opfer-Ausloeser. */
class AdvancedMechanicsTest {

    // -------------------------------------------------------------- X-Kosten

    @Test
    fun `X wird bezahlt und steht dem Effekt zur Verfuegung`() {
        val battle = testBattle()
        battle.giveSources(Side.SPIELER, Aspect.GLUT, 5)
        val flut = battle.giveCard(CardLibrary.find("glut_flammenflut")!!, Side.SPIELER)

        val result = battle.perform(
            GameAction.KarteSpielen(flut.instanceId, listOf(TargetRef.PlayerTarget(Side.GEGNER)), x = 4),
        )

        assertTrue(result.ok, result.message)
        assertEquals(26, battle.state.opponent.life)
        assertEquals(5, battle.state.sources(Side.SPIELER).count { it.tapped }, "1 Grundkosten plus X=4")
    }

    @Test
    fun `zu grosses X wird abgelehnt`() {
        val battle = testBattle()
        battle.giveSources(Side.SPIELER, Aspect.GLUT, 3)
        val flut = battle.giveCard(CardLibrary.find("glut_flammenflut")!!, Side.SPIELER)

        val result = battle.perform(
            GameAction.KarteSpielen(flut.instanceId, listOf(TargetRef.PlayerTarget(Side.GEGNER)), x = 9),
        )

        assertFalse(result.ok)
        assertEquals(30, battle.state.opponent.life)
    }

    @Test
    fun `maxAffordableX beruecksichtigt die Grundkosten`() {
        val battle = testBattle()
        battle.giveSources(Side.SPIELER, Aspect.GLUT, 4)
        val kosten = CardLibrary.find("glut_flammenflut")!!.cost

        // Vier Quellen, davon eine fuer die Grundkosten.
        assertEquals(3, battle.state.maxAffordableX(Side.SPIELER, kosten))
    }

    @Test
    fun `X gleich null ist erlaubt`() {
        val battle = testBattle()
        battle.giveSources(Side.SPIELER, Aspect.LICHT, 2)
        val aufmarsch = battle.giveCard(CardLibrary.find("licht_aufmarsch")!!, Side.SPIELER)

        val result = battle.perform(GameAction.KarteSpielen(aufmarsch.instanceId, x = 0))

        assertTrue(result.ok, result.message)
        assertEquals(0, battle.state.creatures(Side.SPIELER).size)
    }

    @Test
    fun `X-Kosten erzeugen die passende Anzahl Spielsteine`() {
        val battle = testBattle()
        battle.giveSources(Side.SPIELER, Aspect.LICHT, 6)
        val aufmarsch = battle.giveCard(CardLibrary.find("licht_aufmarsch")!!, Side.SPIELER)

        battle.perform(GameAction.KarteSpielen(aufmarsch.instanceId, x = 4))

        assertEquals(4, battle.state.creatures(Side.SPIELER).size)
    }

    // ------------------------------------------------------------ Modale Karten

    @Test
    fun `modale Karte fuehrt nur den gewaehlten Modus aus`() {
        val battle = testBattle()
        battle.giveSources(Side.SPIELER, Aspect.GLUT, 2)
        val opfer = battle.place(testCreature("Wache", 3, 3), Side.GEGNER)
        val zwiespalt = battle.giveCard(CardLibrary.find("glut_zwiespalt")!!, Side.SPIELER)

        battle.perform(
            GameAction.KarteSpielen(
                zwiespalt.instanceId,
                listOf(TargetRef.PermanentTarget(opfer.instanceId)),
                modeIndex = 0,
            ),
        )

        assertNull(battle.state.findPermanent(opfer.instanceId))
        assertEquals(30, battle.state.opponent.life, "Der zweite Modus darf nicht mitwirken")
    }

    @Test
    fun `zweiter Modus nutzt seine eigenen Zielvorgaben`() {
        val battle = testBattle()
        battle.giveSources(Side.SPIELER, Aspect.GLUT, 2)
        val zwiespalt = battle.giveCard(CardLibrary.find("glut_zwiespalt")!!, Side.SPIELER)
        val handVorher = battle.state.player.hand.size

        val result = battle.perform(GameAction.KarteSpielen(zwiespalt.instanceId, modeIndex = 1))

        assertTrue(result.ok, result.message)
        assertEquals(28, battle.state.opponent.life)
        assertEquals(handVorher - 1 + 1, battle.state.player.hand.size)
    }

    @Test
    fun `unbekannter Modus wird abgelehnt`() {
        val battle = testBattle()
        battle.giveSources(Side.SPIELER, Aspect.GLUT, 2)
        val zwiespalt = battle.giveCard(CardLibrary.find("glut_zwiespalt")!!, Side.SPIELER)

        val result = battle.perform(GameAction.KarteSpielen(zwiespalt.instanceId, modeIndex = 7))

        assertFalse(result.ok)
    }

    @Test
    fun `modale Karte bleibt spielbar wenn nur ein Modus Ziele hat`() {
        val battle = testBattle()
        battle.giveSources(Side.SPIELER, Aspect.NEUTRAL, 2)
        val karte = battle.giveCard(CardLibrary.find("neutral_wegkreuzung")!!, Side.SPIELER)

        // Ohne gegnerische Kreaturen ist nur der Kartenziehen-Modus moeglich.
        assertTrue(battle.canCast(Side.SPIELER, karte))
        assertEquals(listOf(0), battle.castableModes(Side.SPIELER, karte.def))
    }

    // ------------------------------------------------------------------ Marken

    @Test
    fun `Marken erhoehen Staerke und Widerstandskraft dauerhaft`() {
        val battle = testBattle()
        battle.giveSources(Side.SPIELER, Aspect.HAIN, 4)
        val ziel = battle.place(testCreature("Setzling", 1, 1), Side.SPIELER)
        val urwuchs = battle.giveCard(CardLibrary.find("hain_urwuchs")!!, Side.SPIELER)

        battle.perform(
            GameAction.KarteSpielen(
                urwuchs.instanceId,
                listOf(TargetRef.PermanentTarget(ziel.instanceId)),
                x = 3,
            ),
        )

        assertEquals(4, battle.state.power(ziel))
        assertEquals(4, battle.state.toughness(ziel))

        // Dauerhaft heisst: ueber das Zugende hinaus.
        battle.perform(GameAction.ZugBeenden)
        assertEquals(4, battle.state.power(ziel))
    }

    @Test
    fun `negative Marken koennen eine Kreatur toeten`() {
        val battle = testBattle()
        battle.giveSources(Side.SPIELER, Aspect.ASCHE, 4)
        val ziel = battle.place(testCreature("Laeufer", 3, 3), Side.GEGNER)
        val zoll = battle.giveCard(CardLibrary.find("asche_seelenzoll")!!, Side.SPIELER)

        battle.perform(
            GameAction.KarteSpielen(
                zoll.instanceId,
                listOf(TargetRef.PermanentTarget(ziel.instanceId)),
                x = 3,
            ),
        )

        assertNull(battle.state.findPermanent(ziel.instanceId))
    }

    @Test
    fun `Marken-Filter trifft nur Kreaturen mit Marken`() {
        val battle = testBattle()
        battle.giveSources(Side.SPIELER, Aspect.HAIN, 6)
        val ohneMarken = battle.place(testCreature("Schlichter", 2, 2), Side.SPIELER)

        // Der Rankenhueter muss gewirkt werden - seine Marken kommen aus dem
        // Betritt-Effekt, den ein direktes Setzen aufs Feld nicht ausloest.
        val hueterKarte = battle.giveCard(CardLibrary.find("hain_rankenhueter")!!, Side.SPIELER)
        battle.perform(GameAction.KarteSpielen(hueterKarte.instanceId))
        val mitMarken = battle.state.creatures(Side.SPIELER).first { it.def.id == "hain_rankenhueter" }
        assertEquals(3, battle.state.power(mitMarken), "Der Rankenhueter legt sich zwei Marken auf")

        val schub = battle.giveCard(CardLibrary.find("hain_wachstumsschub")!!, Side.SPIELER)
        battle.perform(GameAction.KarteSpielen(schub.instanceId))

        assertEquals(4, battle.state.power(mitMarken))
        assertEquals(2, battle.state.power(ohneMarken))
    }

    @Test
    fun `Markenzaehler summiert nur positive Marken`() {
        val battle = testBattle()
        val stark = battle.place(testCreature("Stark", 1, 1), Side.SPIELER)
        val schwach = battle.place(testCreature("Schwach", 4, 4), Side.SPIELER)
        stark.counterPower = 3
        schwach.counterPower = -2

        val zaehler = CardDef(
            id = "test_zaehler",
            name = "Zaehler",
            type = CardType.SPONTAN,
            aspect = Aspect.NEUTRAL,
            cost = Cost.FREE,
            onResolve = Effect.Schaden(Selector.Feind, Value.Marken(Filter.EIGENE_KREATUREN)),
        )
        val karte = battle.giveCard(zaehler, Side.SPIELER)
        battle.perform(GameAction.KarteSpielen(karte.instanceId))

        assertEquals(27, battle.state.opponent.life, "Nur die drei positiven Marken zaehlen")
    }

    // ----------------------------------------------------------- Opfer-Ausloeser

    @Test
    fun `Blutvogt reagiert auf den Tod eigener Kreaturen`() {
        val battle = testBattle(playerLife = 20)
        battle.giveSources(Side.SPIELER, Aspect.ASCHE, 3)
        battle.place(CardLibrary.find("asche_blutvogt")!!, Side.SPIELER)
        val diener = battle.place(testCreature("Diener", 1, 1), Side.SPIELER)

        val ernte = battle.giveCard(CardLibrary.find("asche_gebeinernte")!!, Side.SPIELER)
        battle.perform(
            GameAction.KarteSpielen(ernte.instanceId, listOf(TargetRef.PermanentTarget(diener.instanceId))),
        )

        assertEquals(29, battle.state.opponent.life)
        assertEquals(21, battle.state.player.life)
    }

    @Test
    fun `Opfer-Ausloeser feuert nicht fuer gegnerische Tode`() {
        val battle = testBattle()
        battle.giveSources(Side.SPIELER, Aspect.GLUT, 2)
        battle.place(CardLibrary.find("asche_blutvogt")!!, Side.SPIELER)
        val fremd = battle.place(testCreature("Fremder", 1, 1), Side.GEGNER)

        val stoss = battle.giveCard(CardLibrary.find("glut_sengender_stoss")!!, Side.SPIELER)
        battle.perform(
            GameAction.KarteSpielen(stoss.instanceId, listOf(TargetRef.PermanentTarget(fremd.instanceId))),
        )

        assertNull(battle.state.findPermanent(fremd.instanceId))
        assertEquals(30, battle.state.opponent.life, "Der Filter beschraenkt auf eigene Kreaturen")
    }

    @Test
    fun `Seelensammler waechst mit jedem eigenen Verlust`() {
        val battle = testBattle()
        battle.giveSources(Side.SPIELER, Aspect.ASCHE, 6)
        val sammler = battle.place(CardLibrary.find("asche_seelensammler")!!, Side.SPIELER)
        val ersterDiener = battle.place(testCreature("Diener", 1, 1), Side.SPIELER)
        val zweiterDiener = battle.place(testCreature("Knecht", 1, 1), Side.SPIELER)

        val ernteA = battle.giveCard(CardLibrary.find("asche_gebeinernte")!!, Side.SPIELER)
        battle.perform(
            GameAction.KarteSpielen(ernteA.instanceId, listOf(TargetRef.PermanentTarget(ersterDiener.instanceId))),
        )
        val ernteB = battle.giveCard(CardLibrary.find("asche_gebeinernte")!!, Side.SPIELER)
        battle.perform(
            GameAction.KarteSpielen(ernteB.instanceId, listOf(TargetRef.PermanentTarget(zweiterDiener.instanceId))),
        )

        assertEquals(4, battle.state.power(sammler))
    }

    @Test
    fun `verkettete Todesausloeser terminieren`() {
        // Zwei Kreaturen, die sich beim Tod gegenseitig Schaden zufuegen, duerfen
        // das Spiel nicht einfrieren.
        val kettenreaktion = CardDef(
            id = "test_kette",
            name = "Kettenwesen",
            type = CardType.KREATUR,
            aspect = Aspect.NEUTRAL,
            power = 1,
            toughness = 1,
            triggers = listOf(
                dev.deckbuild.core.model.Trigger(
                    event = dev.deckbuild.core.model.TriggerEvent.ANDERE_KREATUR_STIRBT,
                    effect = Effect.Schaden(Selector.Alle(Filter.KREATUREN), Value.of(1)),
                    text = "reisst andere mit.",
                ),
            ),
        )
        val battle = testBattle()
        repeat(6) { battle.place(kettenreaktion, Side.SPIELER) }
        repeat(6) { battle.place(kettenreaktion, Side.GEGNER) }

        battle.giveSources(Side.SPIELER, Aspect.GLUT, 2)
        val ziel = battle.state.creatures(Side.GEGNER).first()
        val stoss = battle.giveCard(CardLibrary.find("glut_sengender_stoss")!!, Side.SPIELER)

        // Der Aufruf muss zurueckkehren; ohne Deckel liefe er endlos.
        battle.perform(
            GameAction.KarteSpielen(stoss.instanceId, listOf(TargetRef.PermanentTarget(ziel.instanceId))),
        )

        assertTrue(battle.state.battlefield.size < 12, "Die Kettenreaktion hat gewirkt")
    }

    // ---------------------------------------------------------------- Sitzung

    @Test
    fun `Sitzung fragt erst den Modus und dann das Ziel ab`() {
        val session = GameSession(SaveService(InMemorySaveStore()), seedSource = { 99L })
        session.startRun("pfad_glut")
        session.chooseNode(session.run!!.nodes.indexOfFirst { it.type == NodeType.KAMPF })
        val battle = session.battle!!

        battle.giveSources(Side.SPIELER, Aspect.GLUT, 3)
        val opfer = battle.place(testCreature("Wache", 3, 3), Side.GEGNER)
        val zwiespalt = battle.giveCard(CardLibrary.find("glut_zwiespalt")!!, Side.SPIELER)

        session.tapHandCard(zwiespalt)
        assertEquals(CastStage.MODUS, session.targeting?.stage)
        assertEquals(2, session.targeting?.modes?.size)

        session.chooseMode(0)
        assertEquals(CastStage.ZIEL, session.targeting?.stage)
        assertNotNull(session.targeting?.currentSpec)

        session.tapPermanent(opfer)
        assertNull(session.targeting)
        assertNull(battle.state.findPermanent(opfer.instanceId))
    }

    @Test
    fun `Sitzung fragt X ab bevor gewirkt wird`() {
        val session = GameSession(SaveService(InMemorySaveStore()), seedSource = { 98L })
        session.startRun("pfad_glut")
        session.chooseNode(session.run!!.nodes.indexOfFirst { it.type == NodeType.KAMPF })
        val battle = session.battle!!

        battle.giveSources(Side.SPIELER, Aspect.GLUT, 5)
        val flammenflut = battle.giveCard(CardLibrary.find("glut_flammenflut")!!, Side.SPIELER)
        val lebenVorher = battle.state.opponent.life

        session.tapHandCard(flammenflut)
        assertEquals(CastStage.X_WERT, session.targeting?.stage)
        assertEquals(4, session.targeting?.maxX)

        session.chooseX(2)
        assertEquals(CastStage.ZIEL, session.targeting?.stage)

        session.tapPlayer(Side.GEGNER)
        assertNull(session.targeting)
        assertEquals(lebenVorher - 2, battle.state.opponent.life)
    }

    @Test
    fun `Sitzung deckelt X auf den bezahlbaren Wert`() {
        val session = GameSession(SaveService(InMemorySaveStore()), seedSource = { 97L })
        session.startRun("pfad_glut")
        session.chooseNode(session.run!!.nodes.indexOfFirst { it.type == NodeType.KAMPF })
        val battle = session.battle!!

        battle.giveSources(Side.SPIELER, Aspect.GLUT, 3)
        val flammenflut = battle.giveCard(CardLibrary.find("glut_flammenflut")!!, Side.SPIELER)
        val lebenVorher = battle.state.opponent.life

        session.tapHandCard(flammenflut)
        session.chooseX(99)
        session.tapPlayer(Side.GEGNER)

        // Zwei Quellen bleiben nach den Grundkosten, also X = 2.
        assertEquals(lebenVorher - 2, battle.state.opponent.life)
    }

    @Test
    fun `Abbrechen verwirft auch eine halbe Moduswahl`() {
        val session = GameSession(SaveService(InMemorySaveStore()), seedSource = { 96L })
        session.startRun("pfad_glut")
        session.chooseNode(session.run!!.nodes.indexOfFirst { it.type == NodeType.KAMPF })
        val battle = session.battle!!

        battle.giveSources(Side.SPIELER, Aspect.GLUT, 3)
        val zwiespalt = battle.giveCard(CardLibrary.find("glut_zwiespalt")!!, Side.SPIELER)
        val handVorher = battle.state.player.hand.size

        session.tapHandCard(zwiespalt)
        session.chooseMode(0)
        session.cancelTargeting()

        assertNull(session.targeting)
        assertEquals(handVorher, battle.state.player.hand.size, "Die Karte bleibt auf der Hand")
        assertEquals(Screen.Kampf, session.screen)
    }

    @Test
    fun `Karten ohne Wahl werden weiterhin sofort gewirkt`() {
        val session = GameSession(SaveService(InMemorySaveStore()), seedSource = { 95L })
        session.startRun("pfad_flut")
        session.chooseNode(session.run!!.nodes.indexOfFirst { it.type == NodeType.KAMPF })
        val battle = session.battle!!
        if (battle.awaiting != Awaiting.SPIELER_AKTION) return

        battle.giveSources(Side.SPIELER, Aspect.FLUT, 3)
        val erkenntnis = battle.giveCard(CardLibrary.find("flut_erkenntnis")!!, Side.SPIELER)

        session.tapHandCard(erkenntnis)

        assertNull(session.targeting, "Ohne Modus, X oder Ziel darf nichts stehenbleiben")
    }

    // --------------------------------------------------------------- Bestand

    @Test
    fun `jede modale Karte hat mindestens zwei Modi und Beschriftungen`() {
        val fehlerhaft = CardLibrary.all.filter { it.isModal }.mapNotNull { card ->
            when {
                card.modes.size < 2 -> "${card.name}: nur ${card.modes.size} Modus"
                card.modes.any { it.label.isBlank() } -> "${card.name}: Modus ohne Beschriftung"
                else -> null
            }
        }
        assertTrue(fehlerhaft.isEmpty(), "Fehlerhafte modale Karten: $fehlerhaft")
    }

    @Test
    fun `Karten mit X-Kosten nutzen X auch im Effekt`() {
        fun nutztX(effect: Effect?): Boolean = when (effect) {
            null -> false
            is Effect.Kette -> effect.effects.any { nutztX(it) }
            is Effect.Wenn -> nutztX(effect.dann) || nutztX(effect.sonst)
            is Effect.Schaden -> enthaeltX(effect.amount)
            is Effect.Heilung -> enthaeltX(effect.amount)
            is Effect.Ziehen -> enthaeltX(effect.amount)
            is Effect.Abwerfen -> enthaeltX(effect.amount)
            is Effect.Marken -> enthaeltX(effect.amount)
            is Effect.Erschaffen -> enthaeltX(effect.count)
            else -> false
        }

        val ohneWirkung = CardLibrary.all
            .filter { it.cost.hasX }
            .filterNot { card ->
                nutztX(card.onResolve) || card.modes.any { nutztX(it.effect) }
            }
            .map { it.name }

        assertTrue(ohneWirkung.isEmpty(), "X-Karten ohne X im Effekt: $ohneWirkung")
    }

    private fun enthaeltX(value: Value): Boolean = when (value) {
        Value.X -> true
        is Value.Negiert -> enthaeltX(value.inner)
        is Value.Summe -> value.values.any { enthaeltX(it) }
        else -> false
    }
}
