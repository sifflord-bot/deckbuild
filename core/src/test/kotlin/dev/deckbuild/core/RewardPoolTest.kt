package dev.deckbuild.core

import dev.deckbuild.core.content.CardLibrary
import dev.deckbuild.core.content.StarterDecks
import dev.deckbuild.core.meta.InMemorySaveStore
import dev.deckbuild.core.meta.MetaProgress
import dev.deckbuild.core.meta.SaveService
import dev.deckbuild.core.model.Aspect
import dev.deckbuild.core.run.NodeType
import dev.deckbuild.core.run.RunManager
import dev.deckbuild.core.run.RunState
import dev.deckbuild.core.run.ShopKind
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Belohnungen und Haendlerangebote muessen zu den Quellen im Deck passen -
 * eine Karte, die der Lauf nie wirken kann, ist keine Wahl.
 */
class RewardPoolTest {

    private val meta = MetaProgress.initial()

    private fun angebote(run: RunState, stufen: Int = 120): List<String> {
        val basis = run.copy(activeNode = run.nodes.first())
        return (1..stufen).flatMap { stage ->
            RunManager.winEncounter(basis.copy(stage = stage), remainingLife = 20, meta = meta)
                .pendingRewards!!.cardChoices
        }
    }

    @Test
    fun `ein Lauf startet mit dem Aspekt seines Pfades`() {
        for (pfad in StarterDecks.all) {
            val run = RunManager.startRun(pfad.id, seed = 1)
            assertEquals(setOf(pfad.aspect), RunManager.coloredAspects(run), pfad.name)
        }
    }

    @Test
    fun `Belohnungen bieten nur spielbare Aspekte an`() {
        for (pfad in StarterDecks.all) {
            val run = RunManager.startRun(pfad.id, seed = 4711)
            val fremd = angebote(run)
                .mapNotNull { CardLibrary.find(it) }
                .filterNot { RunManager.isPlayable(run, it) }
                .map { "${it.name} (${it.aspect.label})" }
                .distinct()

            assertTrue(fremd.isEmpty(), "${pfad.name} bekam unspielbare Angebote: $fremd")
        }
    }

    @Test
    fun `ein zweiter Aspekt oeffnet seine Karten fuer Belohnungen`() {
        val basis = RunManager.startRun("pfad_glut", seed = 77)
        assertTrue(
            angebote(basis).mapNotNull { CardLibrary.find(it) }.none { it.aspect == Aspect.FLUT },
            "Ohne Zweitaspekt darf keine Flut-Karte erscheinen",
        )

        val erweitert = RunManager.addAspect(basis, Aspect.FLUT)
        val flutAngebote = angebote(erweitert).mapNotNull { CardLibrary.find(it) }.count { it.aspect == Aspect.FLUT }

        assertTrue(flutAngebote > 0, "Nach dem Oeffnen muessen Flut-Karten auftauchen")
    }

    @Test
    fun `der Grundaspekt bleibt haeufiger als der Zweitaspekt`() {
        val run = RunManager.addAspect(RunManager.startRun("pfad_glut", seed = 78), Aspect.FLUT)
        val karten = angebote(run, stufen = 200).mapNotNull { CardLibrary.find(it) }
        val glut = karten.count { it.aspect == Aspect.GLUT }
        val flut = karten.count { it.aspect == Aspect.FLUT }

        assertTrue(glut > flut, "Glut $glut, Flut $flut - der Pfad soll das Deck praegen")
    }

    @Test
    fun `ein zweiter Aspekt bringt eigene Quellen ins Deck`() {
        val basis = RunManager.startRun("pfad_hain", seed = 12)
        val quelleId = CardLibrary.QUELLEN.getValue(Aspect.ASCHE).id
        assertEquals(0, basis.deck.count { it == quelleId })

        val erweitert = RunManager.addAspect(basis, Aspect.ASCHE)

        assertEquals(RunManager.SPLASH_SOURCE_COUNT, erweitert.deck.count { it == quelleId })
        assertTrue(Aspect.ASCHE in RunManager.coloredAspects(erweitert))
    }

    @Test
    fun `derselbe Aspekt wird nicht doppelt geoeffnet`() {
        val run = RunManager.startRun("pfad_hain", seed = 13)
        val nochmal = RunManager.addAspect(run, Aspect.HAIN)
        assertEquals(run.deck.size, nochmal.deck.size)
        assertEquals(1, RunManager.coloredAspects(nochmal).size)
    }

    // ------------------------------------------------------------- Haendler

    private fun haendlerLauf(pfad: String, seed: Long): RunState {
        var run = RunManager.startRun(pfad, seed)
        var guard = 0
        while (run.nodes.none { it.type == NodeType.HAENDLER } && guard++ < 40) {
            run = RunManager.advanceStage(run)
        }
        val index = run.nodes.indexOfFirst { it.type == NodeType.HAENDLER }
        return RunManager.chooseNode(run, index).copy(gold = 500)
    }

    @Test
    fun `der Haendler verkauft Quellen`() {
        val run = haendlerLauf("pfad_glut", 21)
        val angebot = run.shop.firstOrNull { it.kind == ShopKind.QUELLE }
        assertNotNull(angebot, "Kein Quellenangebot im Sortiment")

        val deckVorher = run.deck.size
        val danach = RunManager.buy(run, angebot!!)

        assertTrue(danach.deck.size > deckVorher)
        assertTrue(
            danach.deck.takeLast(3).all { CardLibrary.find(it)?.isSource == true },
            "Gekauft wurden keine Quellen",
        )
    }

    @Test
    fun `der Haendler oeffnet einen zweiten Aspekt`() {
        val run = haendlerLauf("pfad_glut", 22)
        val angebot = run.shop.firstOrNull { it.kind == ShopKind.ASPEKT }
        assertNotNull(angebot, "Kein Aspektangebot im Sortiment")

        val danach = RunManager.buy(run, angebot!!)

        assertEquals(2, RunManager.coloredAspects(danach).size)
        assertEquals(
            RunManager.SPLASH_SOURCE_COUNT,
            danach.deck.size - run.deck.size,
            "Der Kauf muss Quellen mitbringen",
        )
    }

    @Test
    fun `ein zweiter Aspekt wird nicht erneut angeboten`() {
        val run = RunManager.addAspect(haendlerLauf("pfad_glut", 23), Aspect.LICHT)
        var guard = 0
        var weiter = run
        while (weiter.nodes.none { it.type == NodeType.HAENDLER } && guard++ < 40) {
            weiter = RunManager.advanceStage(weiter)
        }
        val index = weiter.nodes.indexOfFirst { it.type == NodeType.HAENDLER }
        if (index < 0) return
        val mitLaden = RunManager.chooseNode(weiter, index)

        assertTrue(mitLaden.shop.none { it.kind == ShopKind.ASPEKT })
    }

    @Test
    fun `Haendlerkarten sind ebenfalls auf spielbare Aspekte beschraenkt`() {
        val run = haendlerLauf("pfad_licht", 24)
        val unspielbar = run.shop
            .filter { it.kind == ShopKind.KARTE }
            .mapNotNull { CardLibrary.find(it.id) }
            .filterNot { RunManager.isPlayable(run, it) }

        assertTrue(unspielbar.isEmpty(), "Unspielbare Ware: ${unspielbar.map { it.name }}")
    }

    // ------------------------------------------------------------ Begegnung

    @Test
    fun `die fremde Lehre oeffnet einen Aspekt und kostet Leben`() {
        val run = RunManager.startRun("pfad_glut", seed = 31)
        val (danach, text) = RunManager.resolveEvent(run, "ev_lehre", 0)

        assertEquals(2, RunManager.coloredAspects(danach).size)
        assertEquals(run.life - 6, danach.life)
        assertTrue(text.isNotBlank())
    }

    @Test
    fun `die fremde Lehre gibt Gold wenn nichts mehr zu lernen ist`() {
        val run = RunManager.addAspect(RunManager.startRun("pfad_glut", seed = 32), Aspect.FLUT)
        val (danach, _) = RunManager.resolveEvent(run, "ev_lehre", 0)

        assertEquals(2, RunManager.coloredAspects(danach).size)
        assertEquals(run.life, danach.life, "Ohne Gegenleistung darf es kein Leben kosten")
        assertTrue(danach.gold > run.gold)
    }

    // -------------------------------------------------------- Altspielstand

    @Test
    fun `alte Spielstaende ohne Aspektfeld laufen weiter`() {
        val service = SaveService(InMemorySaveStore())
        val alt = RunManager.startRun("pfad_asche", seed = 41).copy(aspects = emptySet())
        service.saveRun(alt)

        val geladen = service.loadRun()!!
        assertEquals(setOf(Aspect.ASCHE), RunManager.coloredAspects(geladen))
        assertTrue(
            angebote(geladen).mapNotNull { CardLibrary.find(it) }.all { RunManager.isPlayable(geladen, it) },
        )
    }

    // ------------------------------------------------------------- Messung

    @Test
    fun `mindestens zwei Drittel der Angebote treffen den Grundaspekt`() {
        for (pfad in StarterDecks.all) {
            val run = RunManager.startRun(pfad.id, seed = 909)
            val karten = angebote(run, stufen = 200).mapNotNull { CardLibrary.find(it) }
            val imAspekt = karten.count { it.aspect == pfad.aspect }
            val anteil = 100 * imAspekt / karten.size

            assertTrue(
                anteil >= 66,
                "${pfad.name}: nur $anteil% im Grundaspekt",
            )
            assertFalse(karten.any { !RunManager.isPlayable(run, it) })
        }
    }
}
