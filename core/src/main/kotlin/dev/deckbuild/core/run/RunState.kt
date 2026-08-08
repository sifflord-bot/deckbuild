package dev.deckbuild.core.run

import kotlinx.serialization.Serializable

enum class NodeType(val label: String, val icon: String) {
    KAMPF("Gefecht", "⚔"),
    ELITE("Elitegegner", "☠"),
    BOSS("Boss", "♛"),
    RAST("Rastplatz", "⛺"),
    SCHATZ("Fundstelle", "✦"),
    HAENDLER("Haendler", "⚖"),
    EREIGNIS("Begegnung", "❓"),
}

/** Eine waehlbare Station der aktuellen Stufe. */
@Serializable
data class NodeOption(
    val type: NodeType,
    val label: String,
    val detail: String,
    val enemyId: String? = null,
    val eventId: String? = null,
    val goldReward: Int = 0,
)

@Serializable
data class PouchEntry(val id: String, val charges: Int)

/** Angebot eines Haendlers. */
@Serializable
data class ShopOffer(
    val kind: ShopKind,
    val id: String,
    val price: Int,
    val label: String,
    val detail: String,
)

@Serializable
enum class ShopKind { KARTE, GEGENSTAND, ENTFERNEN, HEILUNG }

/** Der komplette Zustand eines laufenden Durchgangs. */
@Serializable
data class RunState(
    val seed: Long,
    val pathId: String,
    val stage: Int = 1,
    val life: Int,
    val maxLife: Int,
    val deck: List<String> = emptyList(),
    val pouch: List<PouchEntry> = emptyList(),
    val gold: Int = 0,
    val defeatedEnemies: List<String> = emptyList(),
    val nodes: List<NodeOption> = emptyList(),
    /** Der gerade gewaehlte Knoten, solange er noch nicht abgeschlossen ist. */
    val activeNode: NodeOption? = null,
    val shop: List<ShopOffer> = emptyList(),
    val pendingRewards: RewardBundle? = null,
    val over: Boolean = false,
    /** Anzahl gewonnener Kaempfe - Grundlage der Endlos-Wertung. */
    val victories: Int = 0,
) {
    val tier: Int get() = (stage - 1) / 3

    val isBossStage: Boolean get() = stage % 5 == 0

    val deckSize: Int get() = deck.size
}

/** Was nach einem gewonnenen Kampf zur Auswahl steht. */
@Serializable
data class RewardBundle(
    val cardChoices: List<String> = emptyList(),
    val gold: Int = 0,
    val consumableId: String? = null,
    val maxLifeBonus: Int = 0,
    val unlockedCards: List<String> = emptyList(),
)
