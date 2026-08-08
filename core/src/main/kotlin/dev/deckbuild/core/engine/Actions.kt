package dev.deckbuild.core.engine

/** Alles, was eine Seite aktiv tun kann. */
sealed interface GameAction {
    /** Eine Quellenkarte aus der Hand ausspielen (einmal pro Zug). */
    data class QuelleSpielen(val instanceId: Int) : GameAction

    /** Eine beliebige Handkarte verdeckt als farblose Quelle legen (einmal pro Zug). */
    data class VerdecktLegen(val instanceId: Int) : GameAction

    data class KarteSpielen(val instanceId: Int, val targets: List<TargetRef> = emptyList()) : GameAction

    data class FaehigkeitAktivieren(
        val permanentId: Int,
        val abilityIndex: Int,
        val targets: List<TargetRef> = emptyList(),
    ) : GameAction

    data class GegenstandNutzen(val slotIndex: Int, val targets: List<TargetRef> = emptyList()) : GameAction

    /** Von der ersten Hauptphase in den Kampf wechseln. */
    data object ZumKampf : GameAction

    data class AngreiferDeklarieren(val attackerIds: List<Int>) : GameAction

    /** Zuordnung Blocker-Id -> Angreifer-Id. */
    data class BlockerDeklarieren(val assignment: Map<Int, Int>) : GameAction

    data object ZugBeenden : GameAction
}

data class ActionResult(
    val ok: Boolean,
    val message: String = "",
) {
    companion object {
        val OK = ActionResult(true)

        fun fail(reason: String) = ActionResult(false, reason)
    }
}

/** Worauf die Engine gerade wartet. */
enum class Awaiting {
    /** Der Spieler ist am Zug und kann handeln. */
    SPIELER_AKTION,

    /** Der Gegner greift an, der Spieler muss Blocker zuweisen. */
    SPIELER_BLOCK,

    /** Der Kampf ist entschieden. */
    ENDE,
}
