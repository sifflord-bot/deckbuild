package dev.deckbuild.core

import dev.deckbuild.core.content.CardLibrary
import dev.deckbuild.core.content.StarterDecks
import dev.deckbuild.core.meta.InMemorySaveStore
import dev.deckbuild.core.meta.MetaProgress
import dev.deckbuild.core.meta.MetaService
import dev.deckbuild.core.meta.RunSummary
import dev.deckbuild.core.meta.SaveService
import dev.deckbuild.core.run.NodeType
import dev.deckbuild.core.run.RunManager
import dev.deckbuild.core.run.ShopKind
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class RunTest {

    @Test
    fun `Lauf startet mit vollstaendigem Deck und Stationen`() {
        val run = RunManager.startRun("pfad_glut", seed = 7)
        val path = StarterDecks.require("pfad_glut")

        assertEquals(path.cards.size + path.sources, run.deck.size)
        assertTrue(run.nodes.isNotEmpty())
        assertEquals(path.startingLife, run.life)
        assertTrue(RunManager.playerDeck(run).all { CardLibrary.find(it.id) != null })
    }

    @Test
    fun `jede fuenfte Stufe ist ein Boss`() {
        var run = RunManager.startRun("pfad_flut", seed = 11)
        repeat(4) { run = RunManager.advanceStage(run) }

        assertEquals(5, run.stage)
        assertTrue(run.isBossStage)
        assertEquals(1, run.nodes.size)
        assertEquals(NodeType.BOSS, run.nodes.first().type)
    }

    @Test
    fun `derselbe Seed erzeugt denselben Lauf`() {
        val first = RunManager.startRun("pfad_hain", seed = 4242)
        val second = RunManager.startRun("pfad_hain", seed = 4242)
        assertEquals(first.nodes, second.nodes)

        val other = RunManager.startRun("pfad_hain", seed = 4243)
        assertFalse(first.nodes == other.nodes && first.seed == other.seed)
    }

    @Test
    fun `Sieg erzeugt drei Kartenangebote`() {
        val meta = MetaProgress.initial()
        var run = RunManager.startRun("pfad_glut", seed = 21)
        run = RunManager.chooseNode(run, run.nodes.indexOfFirst { it.enemyId != null })
        run = RunManager.winEncounter(run, remainingLife = 25, meta = meta)

        val rewards = run.pendingRewards
        assertNotNull(rewards)
        assertEquals(3, rewards!!.cardChoices.size)
        assertTrue(rewards.cardChoices.all { meta.isCardUnlocked(it) })
        assertEquals(3, rewards.cardChoices.distinct().size)
    }

    @Test
    fun `Belohnung annehmen legt die Karte ins Deck`() {
        val meta = MetaProgress.initial()
        var run = RunManager.startRun("pfad_glut", seed = 22)
        run = RunManager.chooseNode(run, run.nodes.indexOfFirst { it.enemyId != null })
        run = RunManager.winEncounter(run, remainingLife = 20, meta = meta)

        val before = run.deck.size
        val chosen = run.pendingRewards!!.cardChoices.first()
        run = RunManager.takeReward(run, chosen)

        assertEquals(before + 1, run.deck.size)
        assertTrue(chosen in run.deck)
        assertNull(run.pendingRewards)
    }

    @Test
    fun `Belohnung ueberspringen bringt Gold statt Karte`() {
        val meta = MetaProgress.initial()
        var run = RunManager.startRun("pfad_glut", seed = 23)
        run = RunManager.chooseNode(run, run.nodes.indexOfFirst { it.enemyId != null })
        run = RunManager.winEncounter(run, remainingLife = 20, meta = meta)

        val before = run.deck.size
        val goldBefore = run.gold
        run = RunManager.takeReward(run, null)

        assertEquals(before, run.deck.size)
        assertTrue(run.gold > goldBefore)
    }

    @Test
    fun `Rastplatz heilt und fuellt Ladungen auf`() {
        var run = RunManager.startRun("pfad_hain", seed = 33).copy(life = 10)
        run = run.copy(pouch = run.pouch.map { it.copy(charges = 0) })

        run = RunManager.restHeal(run)
        run = RunManager.restRefill(run)

        assertTrue(run.life > 10)
        assertTrue(run.life <= run.maxLife)
        assertTrue(run.pouch.all { it.charges > 0 })
    }

    @Test
    fun `Haendler bucht Gold ab und liefert die Ware`() {
        var run = RunManager.startRun("pfad_flut", seed = 44).copy(gold = 500)
        val shopIndex = run.nodes.indexOfFirst { it.type == NodeType.HAENDLER }
        val withShop = if (shopIndex >= 0) {
            RunManager.chooseNode(run, shopIndex)
        } else {
            // Nicht jede Stufe bietet einen Haendler an - dann per Stufenwechsel suchen.
            var candidate = run
            var guard = 0
            while (candidate.nodes.none { it.type == NodeType.HAENDLER } && guard++ < 40) {
                candidate = RunManager.advanceStage(candidate)
            }
            RunManager.chooseNode(candidate, candidate.nodes.indexOfFirst { it.type == NodeType.HAENDLER })
        }
        run = withShop
        assertTrue(run.shop.isNotEmpty(), "Haendler ohne Angebote")

        val cardOffer = run.shop.first { it.kind == ShopKind.KARTE }
        val goldBefore = run.gold
        val deckBefore = run.deck.size
        run = RunManager.buy(run, cardOffer)

        assertEquals(goldBefore - cardOffer.price, run.gold)
        assertEquals(deckBefore + 1, run.deck.size)
        assertFalse(cardOffer in run.shop)
    }

    @Test
    fun `Kauf ohne Gold aendert nichts`() {
        var run = RunManager.startRun("pfad_flut", seed = 45).copy(gold = 0)
        var guard = 0
        while (run.nodes.none { it.type == NodeType.HAENDLER } && guard++ < 40) {
            run = RunManager.advanceStage(run)
        }
        run = RunManager.chooseNode(run, run.nodes.indexOfFirst { it.type == NodeType.HAENDLER })
        val offer = run.shop.first()
        val after = RunManager.buy(run, offer)

        assertEquals(run.deck.size, after.deck.size)
        assertEquals(0, after.gold)
    }

    @Test
    fun `Beutel fasst hoechstens drei Gegenstaende`() {
        var run = RunManager.startRun("pfad_glut", seed = 55)
        repeat(8) {
            val (next, _) = RunManager.openTreasure(run.copy(stage = run.stage + it))
            run = next
        }
        assertTrue(run.pouch.size <= RunManager.MAX_POUCH_SLOTS)
    }

    @Test
    fun `Bosssieg schaltet die Signaturkarte dauerhaft frei`() {
        val meta = MetaProgress.initial()
        assertFalse(meta.isCardUnlocked("asche_fuerst"))

        val summary = RunSummary(
            stageReached = 5,
            victories = 5,
            defeatedBosses = listOf("aschefuerst"),
            collectedCards = emptyList(),
            won = false,
        )
        val (updated, unlocked) = MetaService.applyRun(meta, summary)

        assertTrue(updated.isCardUnlocked("asche_fuerst"))
        assertTrue("asche_fuerst" in unlocked)
        assertTrue(updated.isPathUnlocked("pfad_asche"))
    }

    @Test
    fun `im Lauf gesammelte Karten bleiben im Pool`() {
        val meta = MetaProgress.initial()
        val summary = RunSummary(
            stageReached = 9,
            victories = 8,
            defeatedBosses = emptyList(),
            collectedCards = listOf("licht_hochmeister"),
            won = false,
        )
        val (updated, _) = MetaService.applyRun(meta, summary)

        assertTrue(updated.isCardUnlocked("licht_hochmeister"))
        assertEquals(9, updated.bestStage)
        assertEquals(8, updated.totalVictories)
    }

    @Test
    fun `Speicherstand ueberlebt einen Rundlauf`() {
        val service = SaveService(InMemorySaveStore())
        val run = RunManager.startRun("pfad_glut", seed = 66).copy(gold = 120, life = 17)
        val meta = MetaProgress.initial().copy(bestStage = 14)

        service.saveRun(run)
        service.saveMeta(meta)

        assertEquals(run, service.loadRun())
        assertEquals(14, service.loadMeta().bestStage)

        service.clearRun()
        assertNull(service.loadRun())
    }

    @Test
    fun `beschaedigter Speicherstand faellt auf den Startzustand zurueck`() {
        val store = InMemorySaveStore()
        store.writeMeta("{ kaputt")
        store.writeRun("auch kaputt")
        val service = SaveService(store)

        assertEquals(MetaProgress.initial().unlockedPaths, service.loadMeta().unlockedPaths)
        assertNull(service.loadRun())
    }

    @Test
    fun `Ereignisse liefern Zustand und Text`() {
        val run = RunManager.startRun("pfad_hain", seed = 77).copy(life = 10, gold = 0)
        val (healed, text) = RunManager.resolveEvent(run, "ev_schrein", 0)

        assertTrue(healed.life > run.life)
        assertTrue(text.isNotBlank())

        val (rich, _) = RunManager.resolveEvent(run, "ev_schrein", 1)
        assertEquals(45, rich.gold)
    }
}
