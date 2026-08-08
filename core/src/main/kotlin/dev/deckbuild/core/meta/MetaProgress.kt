package dev.deckbuild.core.meta

import dev.deckbuild.core.content.CardLibrary
import dev.deckbuild.core.content.StarterDecks
import kotlinx.serialization.Serializable

/**
 * Dauerhafter Fortschritt ueber alle Laeufe hinweg. Ein Lauf endet, der
 * Kartenpool bleibt - freigeschaltete Karten tauchen in kuenftigen Laeufen als
 * Belohnung auf.
 */
@Serializable
data class MetaProgress(
    val unlockedCards: Set<String> = emptySet(),
    val unlockedPaths: Set<String> = emptySet(),
    val defeatedBosses: Set<String> = emptySet(),
    val bestStage: Int = 0,
    val bestVictories: Int = 0,
    val runsStarted: Int = 0,
    val runsFinished: Int = 0,
    val totalVictories: Int = 0,
) {
    fun isCardUnlocked(id: String): Boolean = id in unlockedCards

    fun isPathUnlocked(id: String): Boolean = id in unlockedPaths

    companion object {
        /** Startzustand mit den ohne Freischaltung verfuegbaren Inhalten. */
        fun initial(): MetaProgress = MetaProgress(
            unlockedCards = CardLibrary.defaultUnlocked.map { it.id }.toSet(),
            unlockedPaths = StarterDecks.all.filter { it.startsUnlocked }.map { it.id }.toSet(),
        )
    }
}

/** Ergebnis eines abgeschlossenen Laufs, das in den Meta-Fortschritt einfliesst. */
data class RunSummary(
    val stageReached: Int,
    val victories: Int,
    val defeatedBosses: List<String>,
    val collectedCards: List<String>,
    val won: Boolean,
)

object MetaService {

    /** Verrechnet einen beendeten Lauf und liefert die neuen Freischaltungen mit. */
    fun applyRun(meta: MetaProgress, summary: RunSummary): Pair<MetaProgress, List<String>> {
        val newlyUnlocked = mutableListOf<String>()

        val cards = meta.unlockedCards.toMutableSet()
        // Im Lauf aufgesammelte Karten bleiben dauerhaft im Pool.
        for (id in summary.collectedCards) {
            if (cards.add(id)) newlyUnlocked += id
        }
        // Besiegte Bosse geben ihre Signaturkarte frei.
        for (bossId in summary.defeatedBosses) {
            val boss = dev.deckbuild.core.content.Enemies.find(bossId) ?: continue
            for (id in boss.unlocks) {
                if (cards.add(id)) newlyUnlocked += id
            }
        }

        val paths = meta.unlockedPaths.toMutableSet()
        val bosses = meta.defeatedBosses + summary.defeatedBosses
        if ("aschefuerst" in bosses) paths += "pfad_asche"
        if (maxOf(meta.bestStage, summary.stageReached) >= 12) paths += "pfad_licht"

        val updated = meta.copy(
            unlockedCards = cards,
            unlockedPaths = paths,
            defeatedBosses = bosses,
            bestStage = maxOf(meta.bestStage, summary.stageReached),
            bestVictories = maxOf(meta.bestVictories, summary.victories),
            runsFinished = meta.runsFinished + 1,
            totalVictories = meta.totalVictories + summary.victories,
        )
        return updated to newlyUnlocked
    }

    fun startRun(meta: MetaProgress): MetaProgress = meta.copy(runsStarted = meta.runsStarted + 1)

    /** Text fuer das Kompendium: wie eine noch gesperrte Karte freigeschaltet wird. */
    fun unlockHint(cardId: String): String {
        val boss = dev.deckbuild.core.content.Enemies.roster.firstOrNull { cardId in it.unlocks }
        return if (boss != null) {
            "Besiege ${boss.name}."
        } else {
            "In einem Lauf als Belohnung einsammeln."
        }
    }
}
