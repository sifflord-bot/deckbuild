package dev.deckbuild.core.engine

import dev.deckbuild.core.model.Aspect
import dev.deckbuild.core.model.CardDef
import dev.deckbuild.core.model.CardType
import dev.deckbuild.core.model.ConsumableSlot
import dev.deckbuild.core.model.ControllerScope
import dev.deckbuild.core.model.Cost
import dev.deckbuild.core.model.Effect
import dev.deckbuild.core.model.Filter
import dev.deckbuild.core.model.Keyword
import dev.deckbuild.core.model.Side

/** Eine konkrete Karte in einer Zone. Die [instanceId] bleibt ueber Zonen hinweg stabil. */
data class CardInstance(
    val instanceId: Int,
    val def: CardDef,
) {
    /** Verdeckt gelegte Quellen behalten ihre Karte, erzeugen aber nur farblose Essenz. */
    var faceDownSource: Boolean = false
}

/** Ein Permanent auf dem Schlachtfeld. */
class Permanent(
    val card: CardInstance,
    val controller: Side,
) {
    val instanceId: Int get() = card.instanceId
    val def: CardDef get() = card.def

    var tapped: Boolean = false
    var summoningSick: Boolean = true
    var damage: Int = 0

    /** Bleibende Marken. */
    var counterPower: Int = 0
    var counterToughness: Int = 0

    /** Verstaerkung bis zum Ende des Zuges. */
    var tempPower: Int = 0
    var tempToughness: Int = 0

    val grantedKeywords: MutableSet<Keyword> = mutableSetOf()
    val tempKeywords: MutableSet<Keyword> = mutableSetOf()

    var hasShield: Boolean = false
    var attacking: Boolean = false

    /** instanceId des Angreifers, den dieses Permanent blockt. */
    var blocking: Int? = null

    /** Wurde in diesem Kampf bereits von [blockedBy] geblockt. */
    val blockedBy: MutableList<Int> = mutableListOf()

    val isFaceDownSource: Boolean get() = card.faceDownSource

    /** Aspekte, die dieses Permanent als Quelle erzeugen kann. */
    val producedAspects: Set<Aspect>
        get() = when {
            card.faceDownSource -> setOf(Aspect.NEUTRAL)
            def.type == CardType.QUELLE -> def.produces
            else -> emptySet()
        }

    fun clearTurnState() {
        tempPower = 0
        tempToughness = 0
        tempKeywords.clear()
        damage = 0
    }

    fun clearCombatState() {
        attacking = false
        blocking = null
        blockedBy.clear()
    }
}

/** Zustand eines Spielers inklusive aller Zonen. */
class PlayerState(
    val side: Side,
    var life: Int,
    val library: MutableList<CardInstance> = mutableListOf(),
    val hand: MutableList<CardInstance> = mutableListOf(),
    val graveyard: MutableList<CardInstance> = mutableListOf(),
    val exile: MutableList<CardInstance> = mutableListOf(),
) {
    /** Bereits erzeugte, noch nicht ausgegebene Essenz im aktuellen Zug. */
    val essencePool: MutableMap<Aspect, Int> = mutableMapOf()

    /** Quellen-Einsatz in diesem Zug bereits verbraucht? */
    var sourcePlayedThisTurn: Boolean = false

    /** Verbrauchsgegenstaende des Laufs. */
    val pouch: MutableList<ConsumableSlot> = mutableListOf()

    var maxLife: Int = life

    /** Zaehlt Karten, die dieser Spieler in diesem Zug gewirkt hat. */
    var spellsCastThisTurn: Int = 0

    /** Wie oft aus einer leeren Bibliothek gezogen wurde - kostet steigend Leben. */
    var fatigue: Int = 0

    val missingLife: Int get() = (maxLife - life).coerceAtLeast(0)

    fun poolTotal(): Int = essencePool.values.sum()

    fun clearPool() = essencePool.clear()

    fun addEssence(aspect: Aspect, amount: Int) {
        if (amount <= 0) return
        essencePool[aspect] = (essencePool[aspect] ?: 0) + amount
    }
}

enum class Phase(val label: String) {
    ENTTAPPEN("Enttappen"),
    AUFZIEHEN("Aufziehen"),
    ZIEHEN("Ziehen"),
    HAUPT_1("Erste Hauptphase"),
    ANGRIFF("Angreifer deklarieren"),
    BLOCK("Blocker deklarieren"),
    KAMPFSCHADEN("Kampfschaden"),
    HAUPT_2("Zweite Hauptphase"),
    ENDE("Endphase"),
}

/** Verweis auf ein Ziel eines Effekts. */
sealed interface TargetRef {
    data class PermanentTarget(val instanceId: Int) : TargetRef
    data class PlayerTarget(val side: Side) : TargetRef
    data class StackTarget(val stackId: Int) : TargetRef
    data object None : TargetRef
}

enum class StackKind { ZAUBER, FAEHIGKEIT, AUSLOESER, GEGENSTAND }

/** Ein Eintrag auf dem Stapel. Wird in umgekehrter Reihenfolge aufgeloest. */
class StackItem(
    val stackId: Int,
    val kind: StackKind,
    val controller: Side,
    val sourceName: String,
    /** Die zugehoerige Karte, sofern es sich um einen Zauber handelt. */
    val card: CardInstance?,
    /** Quelle einer Faehigkeit auf dem Schlachtfeld. */
    val sourcePermanent: Permanent?,
    val effect: dev.deckbuild.core.model.Effect?,
    val targets: List<TargetRef>,
    /** Beim Wirken gewaehlter Wert von X. */
    val xValue: Int = 0,
    /** Gewaehlter Modus einer modalen Karte. */
    val modeIndex: Int = 0,
) {
    var countered: Boolean = false
}

/** Regelparameter, damit sich Varianten ohne Engine-Aenderung testen lassen. */
data class Ruleset(
    val startingLife: Int = 30,
    val startingHandSize: Int = 5,
    val handLimit: Int = 7,
    val maxSourcesOnField: Int = 12,
    /** Verdeckt gelegte Handkarten als farblose Quelle erlauben (verhindert Manaflut/-mangel). */
    val allowFaceDownSources: Boolean = true,
    /** Anzahl erlaubter Mulligans zu Beginn eines Kampfes. */
    val mulligans: Int = 1,
)

class GameState(
    val ruleset: Ruleset,
    val player: PlayerState,
    val opponent: PlayerState,
) {
    val battlefield: MutableList<Permanent> = mutableListOf()
    val stack: ArrayDeque<StackItem> = ArrayDeque()

    var activeSide: Side = Side.SPIELER
    var phase: Phase = Phase.HAUPT_1
    var turnNumber: Int = 1
    var winner: Side? = null

    private var nextInstanceId: Int = 1
    private var nextStackId: Int = 1

    fun allocateInstanceId(): Int = nextInstanceId++

    fun allocateStackId(): Int = nextStackId++

    fun stateOf(side: Side): PlayerState = if (side == Side.SPIELER) player else opponent

    fun permanentsOf(side: Side): List<Permanent> = battlefield.filter { it.controller == side }

    fun findPermanent(instanceId: Int): Permanent? = battlefield.firstOrNull { it.instanceId == instanceId }

    val isOver: Boolean get() = winner != null

    fun creatures(side: Side): List<Permanent> =
        battlefield.filter { it.controller == side && it.def.type == CardType.KREATUR }

    fun sources(side: Side): List<Permanent> =
        battlefield.filter { it.controller == side && (it.def.type == CardType.QUELLE || it.isFaceDownSource) }

    /**
     * Welche Aspekte [permanent] gerade erzeugen kann - entweder als Quelle oder
     * ueber eine kostenlose "Tappen: Erzeuge Essenz"-Faehigkeit. So zaehlen
     * Manakreaturen und Relikte beim Bezahlen automatisch mit, ohne dass sie
     * einzeln aktiviert werden muessen.
     */
    fun manaAspectsOf(permanent: Permanent): Set<Aspect> {
        if (permanent.tapped) return emptySet()
        val produced = permanent.producedAspects
        if (produced.isNotEmpty()) return produced
        val hasManaAbility = permanent.def.activated.any { candidate ->
            val effect = candidate.effect
            candidate.tapSelf && candidate.cost.isFree && effect is Effect.Essenz && !effect.dauerhaft
        }
        if (!hasManaAbility) return emptySet()
        if (permanent.def.type == CardType.KREATUR && permanent.summoningSick) return emptySet()
        return setOf(Aspect.NEUTRAL)
    }

    /** Alle Permanenten einer Seite, die derzeit Essenz erzeugen koennen. */
    fun essenceProducers(side: Side): List<Permanent> =
        battlefield.filter { it.controller == side && manaAspectsOf(it).isNotEmpty() }

    // ---------------------------------------------------------------- Statics

    /**
     * Statische Boni anderer Permanenten auf [target].
     *
     * Filter werden bewusst gegen die Basiswerte plus Marken geprueft, nicht
     * gegen bereits verstaerkte Werte - sonst koennten sich zwei Anfuehrer
     * gegenseitig endlos hochschaukeln.
     */
    private fun staticBonus(target: Permanent): Pair<Int, Int> {
        var power = 0
        var toughness = 0
        for (source in battlefield) {
            if (source === target) continue
            for (static in source.def.statics) {
                if (matches(target, static.filter, source)) {
                    power += static.power
                    toughness += static.toughness
                }
            }
        }
        return power to toughness
    }

    private fun staticKeywords(target: Permanent): Set<Keyword> {
        val result = mutableSetOf<Keyword>()
        for (source in battlefield) {
            if (source === target) continue
            for (static in source.def.statics) {
                if (matches(target, static.filter, source)) result += static.keywords
            }
        }
        return result
    }

    fun power(permanent: Permanent): Int {
        val base = permanent.def.power + permanent.counterPower + permanent.tempPower
        return (base + staticBonus(permanent).first).coerceAtLeast(0)
    }

    fun toughness(permanent: Permanent): Int {
        val base = permanent.def.toughness + permanent.counterToughness + permanent.tempToughness
        return base + staticBonus(permanent).second
    }

    fun keywords(permanent: Permanent): Set<Keyword> =
        permanent.def.keywords + permanent.grantedKeywords + permanent.tempKeywords + staticKeywords(permanent)

    fun has(permanent: Permanent, keyword: Keyword): Boolean = keyword in keywords(permanent)

    /** Verbleibende Widerstandskraft bis zur Zerstoerung. */
    fun remainingToughness(permanent: Permanent): Int = toughness(permanent) - permanent.damage

    // ---------------------------------------------------------------- Filter

    /**
     * Prueft, ob [permanent] den [filter] erfuellt. [source] ist das Permanent,
     * aus dessen Sicht "eigene"/"gegnerische" ausgewertet wird.
     */
    fun matches(permanent: Permanent, filter: Filter, source: Permanent?): Boolean {
        val viewpoint = source?.controller ?: activeSide
        if (filter.excludeSelf && source != null && permanent === source) return false
        if (filter.types.isNotEmpty() && permanent.def.type !in filter.types) return false
        if (filter.aspects.isNotEmpty() && permanent.def.aspect !in filter.aspects) return false
        // Stammeszugehoerigkeit: ein Treffer genuegt, damit Kreaturen mehreren
        // Staemmen angehoeren und von mehreren Anfuehrern profitieren koennen.
        if (filter.subtypes.isNotEmpty() && filter.subtypes.none { it in permanent.def.subtypes }) return false

        val effectiveKeywords = permanent.def.keywords + permanent.grantedKeywords + permanent.tempKeywords
        if (filter.keywords.isNotEmpty() && !effectiveKeywords.containsAll(filter.keywords)) return false
        if (filter.excludedKeywords.any { it in effectiveKeywords }) return false

        when (filter.controller) {
            ControllerScope.EIGENE -> if (permanent.controller != viewpoint) return false
            ControllerScope.GEGNERISCHE -> if (permanent.controller == viewpoint) return false
            ControllerScope.BELIEBIGE -> Unit
        }

        val basePower = permanent.def.power + permanent.counterPower + permanent.tempPower
        filter.minPower?.let { if (basePower < it) return false }
        filter.maxPower?.let { if (basePower > it) return false }
        filter.minCounters?.let { if (permanent.counterPower < it) return false }
        filter.tapped?.let { if (permanent.tapped != it) return false }
        filter.attacking?.let { if (permanent.attacking != it) return false }
        return true
    }

    fun select(filter: Filter, source: Permanent?): List<Permanent> =
        battlefield.filter { matches(it, filter, source) }

    // ------------------------------------------------------------- Ressourcen

    /**
     * Gesamte verfuegbare Essenz einer Seite: bereits im Pool plus alles, was
     * ungetappte Quellen noch erzeugen koennen.
     */
    fun availableEssence(side: Side): Int =
        stateOf(side).poolTotal() + essenceProducers(side).size

    /** Kann [cost] mit Pool und ungetappten Quellen bezahlt werden? */
    fun canPay(side: Side, cost: Cost): Boolean =
        EssenceSolver.solve(this, side, if (cost.hasX) cost.withX(0) else cost) != null

    /**
     * Groesstes X, das [side] fuer [cost] noch bezahlen kann. Liefert -1, wenn
     * schon die Grundkosten nicht aufgehen.
     */
    fun maxAffordableX(side: Side, cost: Cost): Int {
        if (!cost.hasX) return 0
        val ceiling = availableEssence(side)
        var best = -1
        for (x in 0..ceiling) {
            if (EssenceSolver.solve(this, side, cost.withX(x)) != null) best = x else break
        }
        return best
    }
}
