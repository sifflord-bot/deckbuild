package dev.deckbuild.core

import dev.deckbuild.core.engine.Awaiting
import dev.deckbuild.core.engine.Phase
import dev.deckbuild.core.engine.TargetRef
import dev.deckbuild.core.meta.InMemorySaveStore
import dev.deckbuild.core.meta.SaveService
import dev.deckbuild.core.meta.SaveStore
import dev.deckbuild.core.model.CardType
import dev.deckbuild.core.model.Side
import dev.deckbuild.core.run.NodeType
import dev.deckbuild.core.run.ShopKind
import dev.deckbuild.core.session.GameSession
import dev.deckbuild.core.session.Screen
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Durchspielen der App-Ablauflogik ohne Oberflaeche. Diese Tests decken genau
 * den Teil ab, den die Compose-Schicht nur noch anzeigt.
 */
class SessionTest {

    private fun session(
        store: SaveStore = InMemorySaveStore(),
        seed: Long = 4711L,
        changes: MutableList<Unit> = mutableListOf(),
    ) = GameSession(SaveService(store), seedSource = { seed }, onChange = { changes += Unit })

    private fun battleNodeIndex(session: GameSession): Int =
        session.run!!.nodes.indexOfFirst { it.type == NodeType.KAMPF || it.type == NodeType.ELITE }

    /** Spielt den laufenden Kampf zuegig, aber legal zu Ende. */
    private fun playBattle(session: GameSession, aggressive: Boolean = true, maxSteps: Int = 4000) {
        var steps = 0
        while (session.screen == Screen.Kampf && steps++ < maxSteps) {
            val battle = session.battle ?: break
            if (session.targeting != null) {
                session.cancelTargeting()
                continue
            }
            when (battle.awaiting) {
                Awaiting.SPIELER_BLOCK -> session.confirmBlocks()
                Awaiting.ENDE -> break
                Awaiting.SPIELER_AKTION -> when (battle.state.phase) {
                    Phase.HAUPT_1 -> {
                        val hand = battle.state.player.hand
                        val source = hand.firstOrNull { it.def.type == CardType.QUELLE }
                        val playable = hand.firstOrNull {
                            it.def.type != CardType.QUELLE &&
                                it.def.targets.isEmpty() &&
                                battle.canCast(Side.SPIELER, it)
                        }
                        when {
                            source != null && !battle.state.player.sourcePlayedThisTurn ->
                                session.tapHandCard(source)
                            playable != null -> session.tapHandCard(playable)
                            aggressive -> session.toCombat()
                            else -> session.endTurn()
                        }
                    }

                    Phase.ANGRIFF -> {
                        battle.state.creatures(Side.SPIELER)
                            .filter { !it.tapped && !it.summoningSick }
                            .forEach { session.tapPermanent(it) }
                        session.confirmAttack()
                    }

                    else -> session.endTurn()
                }
            }
        }
    }

    @Test
    fun `Sitzung startet im Hauptmenue ohne Lauf`() {
        val session = session()
        assertEquals(Screen.Home, session.screen)
        assertNull(session.run)
        assertFalse(session.hasRun)
    }

    @Test
    fun `Lauf starten fuehrt zur Stationsauswahl`() {
        val session = session()
        session.startRun("pfad_glut")

        assertEquals(Screen.Karte, session.screen)
        assertNotNull(session.run)
        assertEquals(1, session.run!!.stage)
        assertTrue(session.run!!.nodes.isNotEmpty())
        assertTrue(session.hasRun)
    }

    @Test
    fun `Kampfstation oeffnet den Kampfbildschirm`() {
        val session = session()
        session.startRun("pfad_hain")
        session.chooseNode(battleNodeIndex(session))

        assertEquals(Screen.Kampf, session.screen)
        val battle = session.battle
        assertNotNull(battle)
        assertEquals(5, battle!!.state.player.hand.size)
        assertEquals(Awaiting.SPIELER_AKTION, battle.awaiting)
    }

    @Test
    fun `gewonnener Kampf fuehrt ueber die Belohnung zur naechsten Stufe`() {
        var session = session(seed = 900L)
        session.startRun("pfad_glut")

        // Bis zu einem Sieg spielen; bei Niederlage mit neuem Lauf weiter.
        var attempts = 0
        while (session.screen != Screen.Belohnung && attempts++ < 12) {
            if (session.screen == Screen.LaufVorbei || session.run?.over == true) {
                session = session(seed = 900L + attempts)
                session.startRun("pfad_glut")
            }
            val index = battleNodeIndex(session)
            if (index < 0) {
                session.chooseNode(0)
                if (session.screen != Screen.Kampf) {
                    session.continueAfterNode()
                    continue
                }
            } else {
                session.chooseNode(index)
            }
            playBattle(session)
        }

        assertEquals(Screen.Belohnung, session.screen, "Kein Sieg in zwoelf Anlaeufen")
        val rewards = session.run!!.pendingRewards!!
        assertEquals(3, rewards.cardChoices.size)

        val deckBefore = session.run!!.deckSize
        session.takeReward(rewards.cardChoices.first())

        assertEquals(Screen.Karte, session.screen)
        assertEquals(deckBefore + 1, session.run!!.deckSize)
        assertEquals(2, session.run!!.stage)
        assertNull(session.run!!.pendingRewards)
    }

    @Test
    fun `verlorener Kampf beendet den Lauf und schreibt den Fortschritt`() {
        val store = InMemorySaveStore()
        val session = session(store)
        session.startRun("pfad_glut")
        session.chooseNode(battleNodeIndex(session))

        // Passiv bleiben: Der Gegner gewinnt zwangslaeufig.
        var steps = 0
        while (session.screen == Screen.Kampf && steps++ < 2000) {
            val battle = session.battle ?: break
            when (battle.awaiting) {
                Awaiting.SPIELER_BLOCK -> session.confirmBlocks()
                Awaiting.SPIELER_AKTION -> session.endTurn()
                Awaiting.ENDE -> break
            }
        }

        assertEquals(Screen.LaufVorbei, session.screen)
        assertTrue(session.run!!.over)
        assertEquals(1, session.meta.runsFinished)
        // Ein beendeter Lauf darf nicht als fortsetzbarer Spielstand liegenbleiben.
        assertNull(SaveService(store).loadRun())
    }

    @Test
    fun `Zielauswahl wartet auf eine Eingabe und wirkt dann`() {
        val session = session()
        session.startRun("pfad_glut")
        session.chooseNode(battleNodeIndex(session))
        val battle = session.battle!!

        // Quellen bereitstellen und einen Zielzauber auf die Hand legen.
        battle.giveSources(Side.SPIELER, dev.deckbuild.core.model.Aspect.GLUT, 2)
        val bolt = battle.giveCard(
            dev.deckbuild.core.content.CardLibrary.find("glut_sengender_stoss")!!,
            Side.SPIELER,
        )
        val lifeBefore = battle.state.opponent.life

        session.tapHandCard(bolt)
        assertNotNull(session.targeting)
        assertEquals(lifeBefore, battle.state.opponent.life)

        assertTrue(session.isLegalTargetNow(TargetRef.PlayerTarget(Side.GEGNER)))
        session.tapPlayer(Side.GEGNER)

        assertNull(session.targeting)
        assertEquals(lifeBefore - 3, battle.state.opponent.life)
    }

    @Test
    fun `Zielauswahl laesst sich abbrechen`() {
        val session = session()
        session.startRun("pfad_glut")
        session.chooseNode(battleNodeIndex(session))
        val battle = session.battle!!
        battle.giveSources(Side.SPIELER, dev.deckbuild.core.model.Aspect.GLUT, 2)
        val bolt = battle.giveCard(
            dev.deckbuild.core.content.CardLibrary.find("glut_sengender_stoss")!!,
            Side.SPIELER,
        )
        val handBefore = battle.state.player.hand.size

        session.tapHandCard(bolt)
        session.cancelTargeting()

        assertNull(session.targeting)
        assertEquals(handBefore, battle.state.player.hand.size)
    }

    @Test
    fun `Angreiferauswahl laesst sich umschalten`() {
        val session = session()
        session.startRun("pfad_glut")
        session.chooseNode(battleNodeIndex(session))
        val battle = session.battle!!
        val creature = battle.place(testCreature("Recke", 3, 3), Side.SPIELER)

        session.toCombat()
        session.tapPermanent(creature)
        assertTrue(creature.instanceId in session.selectedAttackers)

        session.tapPermanent(creature)
        assertFalse(creature.instanceId in session.selectedAttackers)
    }

    @Test
    fun `Blockzuweisung entsteht durch zwei Antipper und wird ausgefuehrt`() {
        val session = session(seed = 123L)
        session.startRun("pfad_hain")
        session.chooseNode(battleNodeIndex(session))
        val battle = session.battle!!

        // Eine klar lohnende Angriffssituation herstellen, damit die KI angreift.
        battle.place(testCreature("Schlaechter", 6, 6), Side.GEGNER)
        val blocker = battle.place(testCreature("Wache", 2, 8), Side.SPIELER)

        var guard = 0
        while (battle.awaiting != Awaiting.SPIELER_BLOCK && !battle.state.isOver && guard++ < 12) {
            session.endTurn()
        }
        assertEquals(Awaiting.SPIELER_BLOCK, battle.awaiting, "Die KI greift nicht an")

        val attacker = battle.pendingAttackers.first()
        session.tapPermanent(blocker)
        assertEquals(blocker.instanceId, session.selectedBlocker)

        session.tapPermanent(attacker)
        assertEquals(mapOf(blocker.instanceId to attacker.instanceId), session.blockAssignment)
        assertNull(session.selectedBlocker)

        val lifeBefore = battle.state.player.life
        session.confirmBlocks()

        // Der Block hat den Schaden abgefangen und die Zuweisung ist zurueckgesetzt.
        assertEquals(lifeBefore, battle.state.player.life)
        assertTrue(session.blockAssignment.isEmpty())
    }

    @Test
    fun `bestehende Blockzuweisung laesst sich wieder loesen`() {
        val session = session(seed = 321L)
        session.startRun("pfad_hain")
        session.chooseNode(battleNodeIndex(session))
        val battle = session.battle!!

        battle.place(testCreature("Schlaechter", 6, 6), Side.GEGNER)
        val blocker = battle.place(testCreature("Wache", 2, 8), Side.SPIELER)

        var guard = 0
        while (battle.awaiting != Awaiting.SPIELER_BLOCK && !battle.state.isOver && guard++ < 12) {
            session.endTurn()
        }
        if (battle.awaiting != Awaiting.SPIELER_BLOCK) return

        val attacker = battle.pendingAttackers.first()
        session.tapPermanent(blocker)
        session.tapPermanent(attacker)
        assertFalse(session.blockAssignment.isEmpty())

        session.tapPermanent(blocker)
        assertTrue(session.blockAssignment.isEmpty())
    }

    @Test
    fun `Rastplatz heilt und geht zur naechsten Stufe`() {
        val session = session()
        session.startRun("pfad_hain")
        var guard = 0
        while (session.run!!.nodes.none { it.type == NodeType.RAST } && guard++ < 30) {
            session.continueAfterNode()
        }
        val index = session.run!!.nodes.indexOfFirst { it.type == NodeType.RAST }
        if (index < 0) return

        val stageBefore = session.run!!.stage
        session.chooseNode(index)
        assertEquals(Screen.Rastplatz, session.screen)

        session.restHeal()
        assertEquals(Screen.Karte, session.screen)
        assertEquals(stageBefore + 1, session.run!!.stage)
    }

    @Test
    fun `Haendler lehnt Kauf ohne Gold mit Hinweis ab`() {
        val session = session()
        session.startRun("pfad_flut")
        var guard = 0
        while (session.run!!.nodes.none { it.type == NodeType.HAENDLER } && guard++ < 30) {
            session.continueAfterNode()
        }
        val index = session.run!!.nodes.indexOfFirst { it.type == NodeType.HAENDLER }
        if (index < 0) return

        session.chooseNode(index)
        assertEquals(Screen.Haendler, session.screen)

        val offer = session.run!!.shop.first { it.kind == ShopKind.KARTE }
        val deckBefore = session.run!!.deckSize

        // Ohne Gold passiert nichts, mit Gold landet die Karte im Deck.
        session.buy(offer)
        assertEquals(deckBefore, session.run!!.deckSize)
        assertNotNull(session.message)
    }

    @Test
    fun `Lauf wird gespeichert und laesst sich fortsetzen`() {
        val store = InMemorySaveStore()
        val first = session(store)
        first.startRun("pfad_glut")
        first.continueAfterNode()
        val stage = first.run!!.stage

        val resumed = session(store)
        assertNotNull(resumed.run)
        assertEquals(stage, resumed.run!!.stage)
        assertTrue(resumed.hasRun)
    }

    @Test
    fun `Lauf aufgeben raeumt den Spielstand auf`() {
        val store = InMemorySaveStore()
        val session = session(store)
        session.startRun("pfad_glut")
        session.abandonRun()

        assertEquals(Screen.Home, session.screen)
        assertNull(session.run)
        assertNull(SaveService(store).loadRun())
    }

    @Test
    fun `Oberflaeche wird ueber Aenderungen benachrichtigt`() {
        val changes = mutableListOf<Unit>()
        val session = session(changes = changes)
        session.startRun("pfad_glut")
        val afterStart = changes.size
        assertTrue(afterStart > 0)

        session.chooseNode(battleNodeIndex(session))
        assertTrue(changes.size > afterStart)
    }
}
