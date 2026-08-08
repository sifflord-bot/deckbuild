package dev.deckbuild.core.ai

import dev.deckbuild.core.engine.Battle
import dev.deckbuild.core.engine.CardInstance
import dev.deckbuild.core.engine.GameAction
import dev.deckbuild.core.engine.OpponentBrain
import dev.deckbuild.core.engine.Permanent
import dev.deckbuild.core.engine.StackItem
import dev.deckbuild.core.engine.TargetRef
import dev.deckbuild.core.model.CardType
import dev.deckbuild.core.model.Effect
import dev.deckbuild.core.model.Keyword
import dev.deckbuild.core.model.Side
import dev.deckbuild.core.util.Rng

/**
 * Spielstaerke der Gegner-KI. Hoehere Stufen bewerten Tauschgeschaefte
 * genauer, halten Antwortzauber zurueck und blocken vorausschauend.
 */
enum class BrainSkill { EINFACH, NORMAL, SCHLAU }

/**
 * Heuristische Gegner-KI ohne Suchbaum: schnell genug fuer ein Telefon und
 * ausreichend gut, um Fehler des Spielers zu bestrafen.
 *
 * Bewertungsgrundlage ist eine Materialfunktion (Staerke doppelt gewichtet,
 * plus Widerstandskraft, Schluesselwoerter und Kosten). Alle Entscheidungen
 * vergleichen den Materialwert vor und nach dem erwarteten Tausch.
 */
class HeuristicBrain(
    private val skill: BrainSkill = BrainSkill.NORMAL,
    private val rng: Rng = Rng(0),
) : OpponentBrain {

    // ---------------------------------------------------------------- Bewertung

    private fun value(battle: Battle, permanent: Permanent): Int {
        val state = battle.state
        var score = state.power(permanent) * 2 + state.toughness(permanent) + permanent.def.manaValue
        val keywords = state.keywords(permanent)
        if (Keyword.FLUG in keywords) score += 3
        if (Keyword.GIFT in keywords) score += 4
        if (Keyword.ZEHRUNG in keywords) score += 2
        if (Keyword.TRAMPELN in keywords) score += 2
        if (Keyword.UNBLOCKBAR in keywords) score += 3
        if (Keyword.SCHILD in keywords || permanent.hasShield) score += 2
        if (permanent.def.statics.isNotEmpty()) score += 5
        if (permanent.def.triggers.isNotEmpty()) score += 2
        return score
    }

    private fun handValue(battle: Battle, card: CardInstance, side: Side): Int {
        val def = card.def
        val state = battle.state
        var score = def.manaValue * 2
        when (def.type) {
            CardType.KREATUR -> score += def.power * 2 + def.toughness + def.keywords.size * 2
            CardType.RITUAL, CardType.SPONTAN -> {
                score += 4
                // Entfernungszauber sind nur so viel wert wie ihr bestes Ziel.
                if (removesCreature(def.onResolve)) {
                    val best = state.creatures(side.other).maxOfOrNull { value(battle, it) } ?: 0
                    score += best - 4
                }
            }
            CardType.RELIKT -> score += 5
            CardType.QUELLE -> score = 0
        }
        return score
    }

    private fun removesCreature(effect: Effect?): Boolean = when (effect) {
        null -> false
        is Effect.Zerstoeren, is Effect.Verbannen, is Effect.Zurueckgeben -> true
        is Effect.Schaden -> true
        is Effect.Staerken -> effect.power < 0 || effect.toughness < 0
        is Effect.Kette -> effect.effects.any { removesCreature(it) }
        is Effect.Wenn -> removesCreature(effect.dann) || removesCreature(effect.sonst)
        else -> false
    }

    private fun isCounterspell(effect: Effect?): Boolean = when (effect) {
        null -> false
        is Effect.Neutralisieren -> true
        is Effect.Kette -> effect.effects.any { isCounterspell(it) }
        else -> false
    }

    // -------------------------------------------------------------- Hauptphase

    override fun nextMainPhaseAction(battle: Battle, side: Side): GameAction? {
        val state = battle.state
        val playerState = state.stateOf(side)

        // 1. Ressourcen ausbauen - immer die hoechste Prioritaet.
        if (!playerState.sourcePlayedThisTurn) {
            val sourceCard = pickSourceToPlay(battle, side)
            if (sourceCard != null) return GameAction.QuelleSpielen(sourceCard.instanceId)

            val faceDown = pickFaceDownCandidate(battle, side)
            if (faceDown != null) return GameAction.VerdecktLegen(faceDown.instanceId)
        }

        // 2. Beste bezahlbare Karte spielen.
        val candidates = playerState.hand
            .filter { it.def.type != CardType.QUELLE }
            .filter { shouldCastInMainPhase(battle, side, it) }
            .filter { battle.canCast(side, it) }

        val best = candidates.maxByOrNull { handValue(battle, it, side) } ?: return null
        val targets = battle.autoTargets(best.def.targets, side, null)
        if (best.def.targets.any { !it.optional } && targets.any { it is TargetRef.None }) return null
        return GameAction.KarteSpielen(best.instanceId, targets)
    }

    private fun pickSourceToPlay(battle: Battle, side: Side): CardInstance? {
        val hand = battle.state.stateOf(side).hand
        val sources = hand.filter { it.def.type == CardType.QUELLE }
        if (sources.isEmpty()) return null

        // Die Farbe waehlen, die auf der Hand am dringendsten fehlt.
        val needed = hand.filter { it.def.type != CardType.QUELLE }
            .flatMap { card -> card.def.cost.colored.entries.map { it.key } }
            .groupingBy { it }.eachCount()
        val owned = battle.state.sources(side)
            .flatMap { it.producedAspects }
            .groupingBy { it }.eachCount()

        return sources.maxByOrNull { source ->
            source.def.produces.sumOf { aspect -> (needed[aspect] ?: 0) * 3 - (owned[aspect] ?: 0) }
        }
    }

    /**
     * Eine Karte verdeckt als farblose Quelle legen. Nur sinnvoll, solange das
     * Ressourcenniveau niedrig ist und die Karte selbst wenig wert ist.
     */
    private fun pickFaceDownCandidate(battle: Battle, side: Side): CardInstance? {
        if (!battle.canPlayFaceDownSource(side)) return null
        val state = battle.state
        val sourceCount = state.essenceProducers(side).size + state.sources(side).count { it.tapped }
        val ceiling = if (skill == BrainSkill.EINFACH) 4 else 6
        if (sourceCount >= ceiling) return null

        val hand = state.stateOf(side).hand.filter { it.def.type != CardType.QUELLE }
        if (hand.size <= 2) return null
        // Die teuerste noch nicht bezahlbare Karte opfern, sonst die schwaechste.
        return hand.filter { !battle.affordability(side, it.def.cost) }
            .maxByOrNull { it.def.manaValue }
            ?: hand.minByOrNull { handValue(battle, it, side) }
    }

    private fun shouldCastInMainPhase(battle: Battle, side: Side, card: CardInstance): Boolean {
        val def = card.def
        if (def.type != CardType.SPONTAN) return true
        if (isCounterspell(def.onResolve)) return false
        if (skill == BrainSkill.EINFACH) return true

        // Sofortzauber nur ausspielen, wenn sie jetzt etwas bewirken.
        val enemyLife = battle.state.stateOf(side.other).life
        if (def.onResolve is Effect.Schaden && enemyLife <= 4) return true
        return removesCreature(def.onResolve) && battle.state.creatures(side.other).isNotEmpty()
    }

    // ------------------------------------------------------------------ Angriff

    override fun chooseAttackers(battle: Battle, side: Side): List<Int> {
        val state = battle.state
        val ready = state.creatures(side).filter {
            !it.tapped && !it.summoningSick && !state.has(it, Keyword.WAECHTER)
        }
        if (ready.isEmpty()) return emptyList()

        val defenders = state.creatures(side.other).filter { !it.tapped }
        val enemyLife = state.stateOf(side.other).life

        // Toedlicher Alpha-Schlag: Wenn ungeblockter Schaden reicht, alles schicken.
        val unblockableDamage = ready.filter { attacker ->
            state.has(attacker, Keyword.UNBLOCKBAR) ||
                defenders.none { canBlock(battle, it, attacker) }
        }.sumOf { state.power(it) }
        if (unblockableDamage >= enemyLife) return ready.map { it.instanceId }

        if (skill == BrainSkill.EINFACH || defenders.isEmpty()) {
            val totalPower = ready.sumOf { state.power(it) }
            if (defenders.isEmpty() || totalPower >= enemyLife) return ready.map { it.instanceId }
        }

        // Verteidigungsbedarf: bei niedrigem Leben Blocker zurueckhalten.
        val myLife = state.stateOf(side).life
        val incomingThreat = state.creatures(side.other).sumOf { state.power(it) }
        val keepBack = if (skill == BrainSkill.SCHLAU && incomingThreat >= myLife) 1 else 0

        val ranked = ready.sortedByDescending { attackTradeScore(battle, it, defenders) }
        val attackers = ranked.filter { attackTradeScore(battle, it, defenders) >= 0 }
        return attackers.dropLast(minOf(keepBack, (attackers.size - 1).coerceAtLeast(0)))
            .map { it.instanceId }
    }

    /**
     * Erwarteter Materialgewinn eines Angriffs: Der Verteidiger waehlt den fuer
     * ihn besten Block, die KI rechnet mit genau diesem schlechtesten Fall.
     */
    private fun attackTradeScore(battle: Battle, attacker: Permanent, defenders: List<Permanent>): Int {
        val state = battle.state
        val possible = defenders.filter { canBlock(battle, it, attacker) }
        if (possible.isEmpty()) return state.power(attacker) + 2

        var worst = Int.MAX_VALUE
        for (blocker in possible) {
            val attackerPower = state.power(attacker)
            val blockerPower = state.power(blocker)
            val attackerDies = blockerPower >= state.remainingToughness(attacker) ||
                (state.has(blocker, Keyword.GIFT) && blockerPower > 0)
            val blockerDies = attackerPower >= state.remainingToughness(blocker) ||
                (state.has(attacker, Keyword.GIFT) && attackerPower > 0)

            var score = 0
            if (blockerDies) score += value(battle, blocker)
            if (attackerDies) score -= value(battle, attacker)
            if (state.has(attacker, Keyword.TRAMPELN) && blockerDies) {
                score += (attackerPower - state.remainingToughness(blocker)).coerceAtLeast(0)
            }
            worst = minOf(worst, score)
        }
        return worst
    }

    private fun canBlock(battle: Battle, blocker: Permanent, attacker: Permanent): Boolean {
        val state = battle.state
        if (blocker.def.type != CardType.KREATUR) return false
        if (blocker.tapped || blocker.attacking) return false
        if (state.has(attacker, Keyword.UNBLOCKBAR)) return false
        if (state.has(attacker, Keyword.FLUG) &&
            !state.has(blocker, Keyword.FLUG) &&
            !state.has(blocker, Keyword.REICHWEITE)
        ) {
            return false
        }
        return true
    }

    // -------------------------------------------------------------------- Block

    override fun chooseBlockers(battle: Battle, side: Side, attackers: List<Permanent>): Map<Int, Int> {
        val state = battle.state
        val available = state.creatures(side).filter { !it.tapped && !it.attacking }.toMutableList()
        if (available.isEmpty()) return emptyMap()

        val assignment = mutableMapOf<Int, Int>()
        val incoming = attackers.sumOf { state.power(it) }
        val life = state.stateOf(side).life
        var unblockedDamage = incoming

        if (skill == BrainSkill.EINFACH) {
            for (attacker in attackers.sortedByDescending { state.power(it) }) {
                val blocker = available.firstOrNull { canBlock(battle, it, attacker) } ?: continue
                available.remove(blocker)
                assignment[blocker.instanceId] = attacker.instanceId
            }
            return assignment
        }

        for (attacker in attackers.sortedByDescending { state.power(it) }) {
            val options = available.filter { canBlock(battle, it, attacker) }
            if (options.isEmpty()) continue

            val attackerPower = state.power(attacker)
            val scored = options.map { blocker ->
                val blockerPower = state.power(blocker)
                val blockerDies = attackerPower >= state.remainingToughness(blocker) ||
                    (state.has(attacker, Keyword.GIFT) && attackerPower > 0)
                val attackerDies = blockerPower >= state.remainingToughness(attacker) ||
                    (state.has(blocker, Keyword.GIFT) && blockerPower > 0)
                var score = 0
                if (attackerDies) score += value(battle, attacker)
                if (blockerDies) score -= value(battle, blocker)
                // Verhinderter Schaden zaehlt mit, wenn es eng wird.
                if (unblockedDamage >= life) score += attackerPower * 3
                blocker to score
            }

            val lethalIncoming = unblockedDamage >= life
            val best = scored.maxByOrNull { it.second } ?: continue
            val worthIt = best.second > 0 || (lethalIncoming && attackerPower > 0)
            if (!worthIt) continue

            available.remove(best.first)
            assignment[best.first.instanceId] = attacker.instanceId
            unblockedDamage -= attackerPower
        }
        return assignment
    }

    // ----------------------------------------------------------------- Antwort

    override fun respondToSpell(battle: Battle, side: Side, item: StackItem): GameAction? {
        if (skill == BrainSkill.EINFACH) return null
        val hand = battle.state.stateOf(side).hand
        val counter = hand.firstOrNull {
            it.def.type == CardType.SPONTAN &&
                isCounterspell(it.def.onResolve) &&
                battle.state.canPay(side, it.def.cost)
        } ?: return null

        val threat = item.card?.def?.manaValue ?: 0
        val important = threat >= 3 ||
            item.card?.def?.type == CardType.KREATUR && threat >= 2 ||
            removesCreature(item.card?.def?.onResolve)
        if (!important && skill == BrainSkill.SCHLAU) return null

        return GameAction.KarteSpielen(counter.instanceId, listOf(TargetRef.StackTarget(item.stackId)))
    }

    @Suppress("unused")
    private fun jitter(): Int = if (skill == BrainSkill.EINFACH) rng.nextInt(3) else 0
}
