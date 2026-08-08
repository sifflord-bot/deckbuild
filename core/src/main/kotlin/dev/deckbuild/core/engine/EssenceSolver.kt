package dev.deckbuild.core.engine

import dev.deckbuild.core.model.Aspect
import dev.deckbuild.core.model.Cost
import dev.deckbuild.core.model.Side

/**
 * Ergebnis einer Kostenberechnung: welche Essenz aus dem Pool ausgegeben und
 * welche Quellen dafuer getappt werden muessen.
 */
data class Payment(
    val poolSpend: Map<Aspect, Int>,
    val tapSourceIds: List<Int>,
)

/**
 * Loest das Bezahlproblem "welche Quellen tappe ich fuer diese Kosten".
 *
 * Farbige Anforderungen bilden zusammen mit den verfuegbaren Erzeugern ein
 * bipartites Matching-Problem. Existiert ein perfektes Matching, verbraucht es
 * immer genau so viele Erzeuger wie farbige Symbole vorhanden sind - der Rest
 * steht danach unabhaengig von der konkreten Zuordnung fuer die farblosen
 * Kosten bereit. Deshalb genuegt: perfektes Matching finden, dann pruefen ob
 * genug Erzeuger uebrig bleiben.
 */
object EssenceSolver {

    private class Producer(
        val aspects: Set<Aspect>,
        /** Gesetzt, wenn dafuer ein Permanent getappt werden muss. */
        val permanentId: Int?,
        /** Gesetzt, wenn dafuer Essenz aus dem Pool verbraucht wird. */
        val poolAspect: Aspect?,
    ) {
        /** Weniger flexible Erzeuger zuerst fuer farbige Kosten verplanen. */
        val flexibility: Int get() = aspects.size
    }

    fun solve(state: GameState, side: Side, cost: Cost): Payment? {
        if (cost.isFree) return Payment(emptyMap(), emptyList())

        val playerState = state.stateOf(side)
        val producers = mutableListOf<Producer>()

        // Pool-Essenz zuerst: sie ist bereits erzeugt und verfaellt am Zugende.
        for ((aspect, amount) in playerState.essencePool) {
            repeat(amount) { producers += Producer(setOf(aspect), null, aspect) }
        }
        for (source in state.essenceProducers(side)) {
            val aspects = state.manaAspectsOf(source)
            if (aspects.isEmpty()) continue
            producers += Producer(aspects, source.instanceId, null)
        }

        val pips = buildList {
            for ((aspect, count) in cost.colored) repeat(count) { add(aspect) }
        }
        if (producers.size < pips.size + cost.generic) return null

        val ordered = producers.sortedWith(
            compareBy({ if (it.poolAspect != null) 0 else 1 }, { it.flexibility }),
        )
        val assignment = matchPips(pips, ordered) ?: return null

        val used = assignment.toMutableSet()
        val leftover = ordered.indices.filter { it !in used }
        if (leftover.size < cost.generic) return null

        // Farblose Kosten aus den am wenigsten wertvollen Erzeugern decken:
        // erst Pool, dann farblose bzw. verdeckte Quellen, dann unflexible.
        val genericPicks = leftover.sortedWith(
            compareBy(
                { if (ordered[it].poolAspect != null) 0 else 1 },
                { if (ordered[it].aspects == setOf(Aspect.NEUTRAL)) 0 else 1 },
                { ordered[it].flexibility },
            ),
        ).take(cost.generic)
        used += genericPicks

        val poolSpend = mutableMapOf<Aspect, Int>()
        val tapIds = mutableListOf<Int>()
        for (index in used) {
            val producer = ordered[index]
            if (producer.poolAspect != null) {
                poolSpend[producer.poolAspect] = (poolSpend[producer.poolAspect] ?: 0) + 1
            } else if (producer.permanentId != null) {
                tapIds += producer.permanentId
            }
        }
        return Payment(poolSpend, tapIds)
    }

    /**
     * Bipartites Matching per Augmentierungspfad. Liefert die Indizes der
     * Erzeuger, die die farbigen Symbole abdecken, oder null.
     */
    private fun matchPips(pips: List<Aspect>, producers: List<Producer>): List<Int>? {
        if (pips.isEmpty()) return emptyList()
        val producerForPip = IntArray(pips.size) { -1 }
        val pipForProducer = IntArray(producers.size) { -1 }

        for (pip in pips.indices) {
            val visited = BooleanArray(producers.size)
            if (!augment(pip, pips, producers, visited, producerForPip, pipForProducer)) return null
        }
        return producerForPip.toList()
    }

    private fun augment(
        pip: Int,
        pips: List<Aspect>,
        producers: List<Producer>,
        visited: BooleanArray,
        producerForPip: IntArray,
        pipForProducer: IntArray,
    ): Boolean {
        for (index in producers.indices) {
            if (visited[index]) continue
            if (pips[pip] !in producers[index].aspects) continue
            visited[index] = true
            val occupant = pipForProducer[index]
            if (occupant == -1 || augment(occupant, pips, producers, visited, producerForPip, pipForProducer)) {
                pipForProducer[index] = pip
                producerForPip[pip] = index
                return true
            }
        }
        return false
    }

    /** Wendet eine Zahlung an: Pool abbuchen und Quellen tappen. */
    fun apply(state: GameState, side: Side, payment: Payment) {
        val playerState = state.stateOf(side)
        for ((aspect, amount) in payment.poolSpend) {
            val remaining = (playerState.essencePool[aspect] ?: 0) - amount
            if (remaining > 0) playerState.essencePool[aspect] = remaining else playerState.essencePool.remove(aspect)
        }
        for (id in payment.tapSourceIds) {
            state.findPermanent(id)?.tapped = true
        }
    }
}
