package dev.deckbuild.core.model

/**
 * Verbrauchsgegenstaende liegen nicht im Deck, sondern im Beutel des Laufs.
 * Sie kosten keine Essenz, sind jederzeit einsetzbar (auch im Blockschritt) und
 * besitzen Ladungen, die sich an Rastplaetzen wieder auffuellen.
 */
data class ConsumableDef(
    val id: String,
    val name: String,
    val maxCharges: Int = 1,
    val targets: List<TargetSpec> = emptyList(),
    val effect: Effect,
    val text: String,
    val rarity: Rarity = Rarity.HAEUFIG,
    val aspect: Aspect = Aspect.NEUTRAL,
)

/** Eine Instanz im Beutel inklusive verbleibender Ladungen. */
data class ConsumableSlot(
    val def: ConsumableDef,
    val charges: Int,
) {
    val isEmpty: Boolean get() = charges <= 0
}
