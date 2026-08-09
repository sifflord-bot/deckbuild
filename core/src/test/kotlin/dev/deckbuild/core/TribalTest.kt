package dev.deckbuild.core

import dev.deckbuild.core.content.CardLibrary
import dev.deckbuild.core.engine.GameAction
import dev.deckbuild.core.engine.TargetRef
import dev.deckbuild.core.meta.MetaProgress
import dev.deckbuild.core.model.Aspect
import dev.deckbuild.core.model.CardType
import dev.deckbuild.core.model.Side
import dev.deckbuild.core.model.Subtype
import dev.deckbuild.core.run.RunManager
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** Kreaturentypen, Stammes-Anfuehrer und die Zauber-Achse. */
class TribalTest {

    // ------------------------------------------------------------- Anfuehrer

    @Test
    fun `Anfuehrer staerkt nur den eigenen Stamm`() {
        val battle = testBattle()
        val krieger = battle.place(CardLibrary.find("glut_aschekrieger")!!, Side.SPIELER)
        val elementar = battle.place(CardLibrary.find("glut_funkenlaeufer")!!, Side.SPIELER)

        assertEquals(3, battle.state.power(krieger))
        assertEquals(2, battle.state.power(elementar))

        battle.place(CardLibrary.find("glut_kriegstrommler")!!, Side.SPIELER)

        assertEquals(4, battle.state.power(krieger), "Krieger sollte +1/+0 erhalten")
        assertEquals(2, battle.state.power(elementar), "Elementar darf nicht profitieren")
    }

    @Test
    fun `Anfuehrer staerkt sich nicht selbst`() {
        val battle = testBattle()
        val trommler = battle.place(CardLibrary.find("glut_kriegstrommler")!!, Side.SPIELER)
        assertEquals(2, battle.state.power(trommler))
    }

    @Test
    fun `zwei Anfuehrer desselben Stammes staerken einander`() {
        val battle = testBattle()
        val ersteBannerherrin = battle.place(CardLibrary.find("licht_bannerherrin")!!, Side.SPIELER)
        assertEquals(2, battle.state.power(ersteBannerherrin))

        val zweite = battle.place(CardLibrary.find("licht_bannerherrin")!!, Side.SPIELER)

        assertEquals(3, battle.state.power(ersteBannerherrin))
        assertEquals(3, battle.state.power(zweite))
    }

    @Test
    fun `Anfuehrer wirkt nicht ueber die Seiten hinweg`() {
        val battle = testBattle()
        val fremderKrieger = battle.place(CardLibrary.find("glut_aschekrieger")!!, Side.GEGNER)
        battle.place(CardLibrary.find("glut_kriegstrommler")!!, Side.SPIELER)

        assertEquals(3, battle.state.power(fremderKrieger))
    }

    @Test
    fun `Anfuehrer verschwindet mit seinem Traeger`() {
        val battle = testBattle()
        battle.giveSources(Side.SPIELER, Aspect.GLUT, 1)
        val krieger = battle.place(CardLibrary.find("glut_aschekrieger")!!, Side.SPIELER)
        val trommler = battle.place(CardLibrary.find("glut_kriegstrommler")!!, Side.SPIELER)
        assertEquals(4, battle.state.power(krieger))

        val stoss = battle.giveCard(CardLibrary.find("glut_sengender_stoss")!!, Side.SPIELER)
        battle.perform(
            GameAction.KarteSpielen(stoss.instanceId, listOf(TargetRef.PermanentTarget(trommler.instanceId))),
        )

        assertEquals(3, battle.state.power(krieger), "Bonus muss mit dem Anfuehrer enden")
    }

    // ------------------------------------------------------ Stammes-Zahlungen

    @Test
    fun `Brandmal skaliert mit der Anzahl eigener Elementare`() {
        val battle = testBattle()
        battle.giveSources(Side.SPIELER, Aspect.GLUT, 4)
        battle.place(CardLibrary.find("glut_funkenlaeufer")!!, Side.SPIELER)
        battle.place(CardLibrary.find("glut_funkenlaeufer")!!, Side.SPIELER)

        val brandmal = battle.giveCard(CardLibrary.find("glut_brandmal")!!, Side.SPIELER)
        battle.perform(
            GameAction.KarteSpielen(brandmal.instanceId, listOf(TargetRef.PlayerTarget(Side.GEGNER))),
        )

        // Grundwert 2 plus zwei Elementare.
        assertEquals(26, battle.state.opponent.life)
    }

    @Test
    fun `Massengrab erschafft einen Knochendiener je Untotem`() {
        val battle = testBattle()
        battle.giveSources(Side.SPIELER, Aspect.ASCHE, 4)
        battle.place(CardLibrary.find("asche_grabkriecher")!!, Side.SPIELER)
        battle.place(CardLibrary.find("asche_grabkriecher")!!, Side.SPIELER)

        val grab = battle.giveCard(CardLibrary.find("asche_massengrab")!!, Side.SPIELER)
        battle.perform(GameAction.KarteSpielen(grab.instanceId))

        // Zwei Grabkriecher plus Grundwert 1 ergibt drei neue Spielsteine.
        assertEquals(3, battle.state.creatures(Side.SPIELER).count { it.def.id == "tok_skelett" })
    }

    @Test
    fun `Urinstinkt staerkt nur Bestien`() {
        val battle = testBattle()
        battle.giveSources(Side.SPIELER, Aspect.HAIN, 2)
        val bestie = battle.place(CardLibrary.find("hain_dickichtwolf")!!, Side.SPIELER)
        val elementar = battle.place(CardLibrary.find("hain_baumhueter")!!, Side.SPIELER)

        val instinkt = battle.giveCard(CardLibrary.find("hain_urinstinkt")!!, Side.SPIELER)
        battle.perform(GameAction.KarteSpielen(instinkt.instanceId))

        assertEquals(5, battle.state.power(bestie))
        assertEquals(2, battle.state.power(elementar))
    }

    // -------------------------------------------------------- Zauber-Archetyp

    @Test
    fun `Funkenweber trifft bei jedem gewirkten Zauber`() {
        val battle = testBattle()
        battle.giveSources(Side.SPIELER, Aspect.GLUT, 4)
        battle.place(CardLibrary.find("glut_funkenweber")!!, Side.SPIELER)

        val stoss = battle.giveCard(CardLibrary.find("glut_sengender_stoss")!!, Side.SPIELER)
        battle.perform(
            GameAction.KarteSpielen(stoss.instanceId, listOf(TargetRef.PlayerTarget(Side.GEGNER))),
        )

        // 3 Schaden vom Zauber, 1 vom Ausloeser.
        assertEquals(26, battle.state.opponent.life)
    }

    @Test
    fun `Arkanes Echo waechst pro Zauber und faellt am Zugende zurueck`() {
        val battle = testBattle()
        battle.giveSources(Side.SPIELER, Aspect.FLUT, 6)
        val echo = battle.place(CardLibrary.find("flut_arkanes_echo")!!, Side.SPIELER)
        assertEquals(1, battle.state.power(echo))

        val erkenntnis = battle.giveCard(CardLibrary.find("flut_erkenntnis")!!, Side.SPIELER)
        battle.perform(GameAction.KarteSpielen(erkenntnis.instanceId))
        assertEquals(2, battle.state.power(echo))

        val zweite = battle.giveCard(CardLibrary.find("flut_erkenntnis")!!, Side.SPIELER)
        battle.perform(GameAction.KarteSpielen(zweite.instanceId))
        assertEquals(3, battle.state.power(echo))

        battle.perform(GameAction.ZugBeenden)
        assertEquals(1, battle.state.power(echo), "Die Verstaerkung gilt nur bis zum Zugende")
    }

    @Test
    fun `Gedankenflut belohnt den dritten Zauber des Zuges`() {
        val battle = testBattle()
        battle.giveSources(Side.SPIELER, Aspect.FLUT, 12)

        // Erster Zauber allein: nur eine Karte.
        val soloFlut = battle.giveCard(CardLibrary.find("flut_gedankenflut")!!, Side.SPIELER)
        val vorher = battle.state.player.hand.size
        battle.perform(GameAction.KarteSpielen(soloFlut.instanceId))
        assertEquals(vorher - 1 + 1, battle.state.player.hand.size)

        // Nach zwei weiteren Zaubern greift die Bedingung.
        val a = battle.giveCard(CardLibrary.find("flut_erkenntnis")!!, Side.SPIELER)
        battle.perform(GameAction.KarteSpielen(a.instanceId))
        val b = battle.giveCard(CardLibrary.find("flut_erkenntnis")!!, Side.SPIELER)
        battle.perform(GameAction.KarteSpielen(b.instanceId))

        val grosseFlut = battle.giveCard(CardLibrary.find("flut_gedankenflut")!!, Side.SPIELER)
        val davor = battle.state.player.hand.size
        battle.perform(GameAction.KarteSpielen(grosseFlut.instanceId))
        assertEquals(davor - 1 + 3, battle.state.player.hand.size)
    }

    // ------------------------------------------------------------ Belohnungen

    @Test
    fun `Belohnungen bevorzugen Staemme aus dem eigenen Deck`() {
        val meta = MetaProgress.initial()
        val basis = RunManager.startRun("pfad_hain", seed = 8080)

        // Ein Deck, das klar auf Bestien setzt.
        val bestienDeck = basis.copy(
            deck = basis.deck + List(6) { "hain_dickichtwolf" },
        )
        assertTrue(Subtype.BESTIE in RunManager.synergyTribes(bestienDeck))

        fun bestienAnteil(run: dev.deckbuild.core.run.RunState): Int {
            var treffer = 0
            for (stage in 1..40) {
                val schritt = run.copy(stage = stage, activeNode = run.nodes.first())
                val gewonnen = RunManager.winEncounter(schritt, remainingLife = 20, meta = meta)
                treffer += gewonnen.pendingRewards!!.cardChoices
                    .mapNotNull { CardLibrary.find(it) }
                    .count { Subtype.BESTIE in it.relatedSubtypes }
            }
            return treffer
        }

        val mitStamm = bestienAnteil(bestienDeck)
        val ohneStamm = bestienAnteil(basis.copy(deck = basis.deck.filterNot { it == "hain_dickichtwolf" }))

        assertTrue(
            mitStamm > ohneStamm,
            "Bestien-Deck bekam $mitStamm Bestien-Angebote, Vergleichsdeck $ohneStamm",
        )
    }

    // ------------------------------------------------------------ Konsistenz

    @Test
    fun `jede Kreatur hat mindestens einen Typ`() {
        val ohneTyp = CardLibrary.all
            .filter { it.type == CardType.KREATUR && it.subtypes.isEmpty() }
            .map { it.name }
        assertTrue(ohneTyp.isEmpty(), "Kreaturen ohne Typ: $ohneTyp")
    }

    @Test
    fun `Nichtkreaturen tragen keine Kreaturentypen`() {
        val falsch = CardLibrary.all
            .filter { it.type != CardType.KREATUR && it.subtypes.isNotEmpty() }
            .map { it.name }
        assertTrue(falsch.isEmpty(), "Nichtkreaturen mit Kreaturentyp: $falsch")
    }

    /**
     * Wer einen Stamm mechanisch anspricht, muss ihn auch als Archetyp fuehren -
     * sonst zaehlt die Karte bei der Belohnungsgewichtung nicht mit und der
     * Stamm bleibt unsichtbar.
     */
    @Test
    fun `Stammes-Anfuehrer sind als Archetyp markiert`() {
        val fehlend = CardLibrary.all.mapNotNull { card ->
            val angesprochen = card.statics.flatMap { it.filter.subtypes }.toSet()
            val fehlt = angesprochen - card.relatedSubtypes
            if (fehlt.isEmpty()) null else "${card.name}: $fehlt"
        }
        assertTrue(fehlend.isEmpty(), "Nicht markierte Stammesbezuege: $fehlend")
    }

    @Test
    fun `jeder Stamm hat genug Karten um ein Deck zu tragen`() {
        val zaehlung = Subtype.entries.associateWith { subtype ->
            CardLibrary.spells.count { subtype in it.relatedSubtypes }
        }
        val zuDuenn = zaehlung.filterValues { it < 3 }.keys
        assertTrue(
            zuDuenn.isEmpty(),
            "Staemme mit weniger als drei Karten: $zuDuenn (Zaehlung: $zaehlung)",
        )
    }

    @Test
    fun `Anfuehrer staerken nur Kreaturen`() {
        val battle = testBattle()
        battle.place(CardLibrary.QUELLEN.getValue(Aspect.LICHT), Side.SPIELER)
        battle.place(CardLibrary.find("licht_bannerherrin")!!, Side.SPIELER)

        val quelle = battle.state.sources(Side.SPIELER).first()
        assertEquals(0, battle.state.power(quelle))
        assertFalse(battle.state.toughness(quelle) > 0)
    }
}
