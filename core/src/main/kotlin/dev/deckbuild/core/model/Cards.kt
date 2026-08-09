package dev.deckbuild.core.model

/**
 * Die fuenf Aspekte des Spiels. Sie erfuellen dieselbe Rolle wie Farben in
 * klassischen Sammelkartenspielen: Sie beschraenken, welche Karten zusammen
 * spielbar sind, und geben jedem Deck eine mechanische Identitaet.
 */
enum class Aspect(val label: String, val short: String) {
    GLUT("Glut", "G"),
    FLUT("Flut", "F"),
    ASCHE("Asche", "A"),
    HAIN("Hain", "H"),
    LICHT("Licht", "L"),
    NEUTRAL("Neutral", "N"),
    ;

    val isColored: Boolean get() = this != NEUTRAL
}

enum class CardType(val label: String) {
    QUELLE("Quelle"),
    KREATUR("Kreatur"),
    RELIKT("Relikt"),
    RITUAL("Ritual"),
    SPONTAN("Spontan"),
    ;

    /** Rituale sind langsam (nur eigene Hauptphase), Spontanzauber schnell. */
    val isSpell: Boolean get() = this == RITUAL || this == SPONTAN
    val isPermanent: Boolean get() = this == QUELLE || this == KREATUR || this == RELIKT
}

/**
 * Kreaturentypen. Sie tragen keine eigene Regel, sondern sind der Anknuepfungs-
 * punkt fuer Stammes-Synergien: Anfuehrer staerken ihren Typ, Zahlungen zaehlen
 * ihn. Bewusst wenige Typen - ein Stamm mit drei Karten ist kein Archetyp.
 */
enum class Subtype(val label: String, val plural: String) {
    KRIEGER("Krieger", "Krieger"),
    MAGIER("Magier", "Magier"),
    GEIST("Geist", "Geister"),
    BESTIE("Bestie", "Bestien"),
    UNTOTER("Untoter", "Untote"),
    ELEMENTAR("Elementar", "Elementare"),
    KONSTRUKT("Konstrukt", "Konstrukte"),
    DRACHE("Drache", "Drachen"),
}

enum class Rarity(val label: String, val weight: Int) {
    HAEUFIG("Haeufig", 60),
    SELTEN("Selten", 30),
    LEGENDAER("Legendaer", 10),
}

/**
 * Schluesselwortfaehigkeiten. Bewusst eigene Begriffe - die Mechaniken sind
 * Genre-Standard, die Namensgebung gehoert diesem Projekt.
 */
enum class Keyword(val label: String, val reminder: String) {
    FLINK("Flink", "Kann im Zug des Erscheinens angreifen."),
    WACHT("Wacht", "Enttappt nicht durch Angreifen."),
    FLUG("Flug", "Nur von Kreaturen mit Flug oder Reichweite blockbar."),
    REICHWEITE("Reichweite", "Kann Kreaturen mit Flug blocken."),
    TRAMPELN("Trampeln", "Ueberschuessiger Kampfschaden trifft den Gegner."),
    VORSTOSS("Vorstoss", "Teilt Kampfschaden vor Kreaturen ohne Vorstoss aus."),
    GIFT("Gift", "Jeder Schaden dieser Kreatur zerstoert die getroffene Kreatur."),
    ZEHRUNG("Zehrung", "Schaden dieser Kreatur heilt ihren Besitzer um denselben Wert."),
    WAECHTER("Waechter", "Kann nicht angreifen."),
    UNBLOCKBAR("Verschleiert", "Kann nicht geblockt werden."),
    SCHILD("Schild", "Verhindert die naechste Schadensquelle einmalig."),
    VERBRAUCH("Verbrauch", "Wird nach dem Ausspielen verbannt statt abgelegt."),
}

/** Manakosten: farblose Menge plus konkrete Aspekt-Anforderungen. */
data class Cost(
    val generic: Int = 0,
    val colored: Map<Aspect, Int> = emptyMap(),
) {
    val total: Int get() = generic + colored.values.sum()

    val isFree: Boolean get() = total == 0

    fun render(): String = buildString {
        if (generic > 0 || colored.isEmpty()) append(generic)
        for ((aspect, count) in colored.entries.sortedBy { it.key.ordinal }) {
            repeat(count) { append(aspect.short) }
        }
    }

    companion object {
        val FREE = Cost()

        fun of(generic: Int = 0, vararg pips: Pair<Aspect, Int>): Cost =
            Cost(generic, pips.toMap())

        fun colored(aspect: Aspect, pips: Int, generic: Int = 0): Cost =
            Cost(generic, mapOf(aspect to pips))
    }
}

/** Auf welche Seite sich ein Filter bezieht. */
enum class Side {
    SPIELER,
    GEGNER,
    ;

    val other: Side get() = if (this == SPIELER) GEGNER else SPIELER
}

enum class ControllerScope { EIGENE, GEGNERISCHE, BELIEBIGE }

/**
 * Beschreibt eine Menge von Permanenten auf dem Schlachtfeld. Wird sowohl fuer
 * Zielbestimmung als auch fuer Massenwirkungen und statische Effekte genutzt.
 */
data class Filter(
    val types: Set<CardType> = emptySet(),
    val aspects: Set<Aspect> = emptySet(),
    /** Trifft, wenn die Kreatur mindestens einen dieser Typen hat. */
    val subtypes: Set<Subtype> = emptySet(),
    val keywords: Set<Keyword> = emptySet(),
    val excludedKeywords: Set<Keyword> = emptySet(),
    val controller: ControllerScope = ControllerScope.BELIEBIGE,
    val minPower: Int? = null,
    val maxPower: Int? = null,
    val tapped: Boolean? = null,
    val attacking: Boolean? = null,
    val excludeSelf: Boolean = false,
) {
    companion object {
        val KREATUREN = Filter(types = setOf(CardType.KREATUR))
        val EIGENE_KREATUREN = Filter(types = setOf(CardType.KREATUR), controller = ControllerScope.EIGENE)
        val GEGNERISCHE_KREATUREN = Filter(types = setOf(CardType.KREATUR), controller = ControllerScope.GEGNERISCHE)
        val ALLE_PERMANENTEN = Filter()

        /** Eigene Kreaturen eines Stammes - Grundlage aller Anfuehrer und Zahlungen. */
        fun eigenerStamm(subtype: Subtype, excludeSelf: Boolean = false) = Filter(
            types = setOf(CardType.KREATUR),
            subtypes = setOf(subtype),
            controller = ControllerScope.EIGENE,
            excludeSelf = excludeSelf,
        )
    }
}

/** Ein zur Laufzeit berechneter Zahlenwert (z. B. "Schaden gleich Kreaturenzahl"). */
sealed interface Value {
    data class Fixed(val amount: Int) : Value

    /** Anzahl der Permanenten, die [filter] erfuellen. */
    data class Count(val filter: Filter) : Value

    /** Groesse des eigenen Friedhofs. */
    data object Friedhof : Value

    /** Fehlende Lebenspunkte des Beherrschers (fuer Comeback-Effekte). */
    data object FehlendeLeben : Value

    /** Zauber, die der Beherrscher in diesem Zug bereits gewirkt hat. */
    data object ZauberDiesenZug : Value

    /**
     * Summe mehrerer Werte. Erst damit lassen sich Zahlungen der Form
     * "2 Schaden, plus 1 je eigenem Elementar" ueberhaupt ausdruecken.
     */
    data class Summe(val values: List<Value>) : Value

    companion object {
        fun of(amount: Int): Value = Fixed(amount)

        fun plus(vararg values: Value): Value = Summe(values.toList())

        /** Grundwert plus einen Zaehler - die haeufigste Form einer Stammes-Zahlung. */
        fun basis(amount: Int, proStueck: Filter): Value =
            Summe(listOf(Fixed(amount), Count(proStueck)))
    }
}

/** Bedingung fuer [Effect.Wenn]. */
sealed interface Condition {
    data class Mindestens(val value: Value, val threshold: Int) : Condition
    data class Hoechstens(val value: Value, val threshold: Int) : Condition
    data object GegnerUnterHalbenLeben : Condition
    data class Nicht(val inner: Condition) : Condition
}

/**
 * Wen ein Effekt betrifft. [Chosen] verweist auf das i-te beim Ausspielen
 * gewaehlte Ziel, alles andere wird beim Aufloesen automatisch bestimmt.
 */
sealed interface Selector {
    /** Die Quelle des Effekts selbst. */
    data object Selbst : Selector

    /** Das i-te deklarierte Ziel. */
    data class Chosen(val index: Int = 0) : Selector

    /** Der Beherrscher des Effekts. */
    data object Du : Selector

    /** Der Gegner des Beherrschers. */
    data object Feind : Selector

    /** Alle Permanenten, die [filter] erfuellen. */
    data class Alle(val filter: Filter) : Selector

    /** [count] zufaellige Permanente, die [filter] erfuellen. */
    data class Zufaellig(val filter: Filter, val count: Int = 1) : Selector
}

/** Baustein der Regel-Engine. Jede Karte beschreibt ihre Wirkung als Effektbaum. */
sealed interface Effect {
    data class Schaden(val target: Selector, val amount: Value) : Effect
    data class Heilung(val target: Selector, val amount: Value) : Effect
    data class Ziehen(val target: Selector, val amount: Value) : Effect
    data class Abwerfen(val target: Selector, val amount: Value) : Effect
    data class Zerstoeren(val target: Selector) : Effect
    data class Verbannen(val target: Selector) : Effect
    data class Zurueckgeben(val target: Selector) : Effect
    data class Opfern(val target: Selector) : Effect
    data class Antappen(val target: Selector, val untap: Boolean = false) : Effect

    /** Staerkung; [dauerhaft] = bleibende Marken, sonst bis zum Zugende. */
    data class Staerken(
        val target: Selector,
        val power: Int = 0,
        val toughness: Int = 0,
        val keywords: Set<Keyword> = emptySet(),
        val dauerhaft: Boolean = false,
    ) : Effect

    data class Erschaffen(val controller: Selector, val tokenId: String, val count: Value) : Effect

    /** Zusaetzliche Essenz; [dauerhaft] erhoeht die Quellenbasis (Ramp). */
    data class Essenz(val controller: Selector, val amount: Int, val dauerhaft: Boolean = false) : Effect

    /** Neutralisiert einen Zauber auf dem Stapel. */
    data class Neutralisieren(val target: Selector) : Effect

    /** Holt eine Kreatur aus dem eigenen Friedhof aufs Schlachtfeld. */
    data class Wiederbeleben(val controller: Selector, val maxCost: Int) : Effect

    /** Legt die oberste Karte der eigenen Bibliothek auf die Hand, wenn sie [filter] erfuellt. */
    data class Suchen(val controller: Selector, val types: Set<CardType>) : Effect

    data class Kette(val effects: List<Effect>) : Effect
    data class Wenn(val condition: Condition, val dann: Effect, val sonst: Effect? = null) : Effect

    companion object {
        fun kette(vararg effects: Effect): Effect = Kette(effects.toList())
    }
}

enum class TargetKind {
    KREATUR,
    PERMANENT,
    SPIELER,
    BELIEBIG,
    ZAUBER_AUF_STAPEL,
}

data class TargetSpec(
    val kind: TargetKind,
    val filter: Filter = Filter(),
    val optional: Boolean = false,
    val prompt: String = "Ziel waehlen",
)

enum class TriggerEvent {
    BETRITT_SCHLACHTFELD,
    STIRBT,
    GREIFT_AN,
    ANDERE_KREATUR_BETRITT,
    ZUG_BEGINN,
    ZUG_ENDE,
    FUEGT_KAMPFSCHADEN_ZU,
    ZAUBER_GEWIRKT,
}

data class Trigger(
    val event: TriggerEvent,
    val effect: Effect,
    /** Optionaler Filter auf das ausloesende Objekt. */
    val filter: Filter? = null,
    val targets: List<TargetSpec> = emptyList(),
    val text: String = "",
)

data class ActivatedAbility(
    val cost: Cost = Cost.FREE,
    val tapSelf: Boolean = false,
    val sacrificeSelf: Boolean = false,
    val targets: List<TargetSpec> = emptyList(),
    val effect: Effect,
    val text: String,
    /** true = nur in der eigenen Hauptphase aktivierbar. */
    val sorcerySpeed: Boolean = false,
)

/** Dauerhafter Effekt auf andere Permanente ("Anfuehrer"-Karten). */
data class StaticAbility(
    val filter: Filter,
    val power: Int = 0,
    val toughness: Int = 0,
    val keywords: Set<Keyword> = emptySet(),
    val text: String,
)

/**
 * Die unveraenderliche Definition einer Karte. Instanzen auf dem Schlachtfeld
 * werden von [dev.deckbuild.core.engine.Permanent] gehalten.
 */
data class CardDef(
    val id: String,
    val name: String,
    val type: CardType,
    val aspect: Aspect,
    val cost: Cost = Cost.FREE,
    val power: Int = 0,
    val toughness: Int = 0,
    /** Kreaturentypen; bei Nichtkreaturen leer. */
    val subtypes: Set<Subtype> = emptySet(),
    /**
     * Staemme, auf die diese Karte hinarbeitet, ohne ihnen selbst anzugehoeren -
     * etwa ein Zauber, der alle Bestien staerkt, oder ein Anfuehrer.
     *
     * Bewusst von Hand gesetzt statt aus dem Effektbaum abgeleitet: Die Angabe
     * steuert, welche Belohnungen ein Deck bekommt, und diese Entscheidung soll
     * beim Kartenentwurf getroffen werden, nicht von einer Heuristik.
     */
    val archetypes: Set<Subtype> = emptySet(),
    val keywords: Set<Keyword> = emptySet(),
    val targets: List<TargetSpec> = emptyList(),
    /** Wirkung eines Zaubers bzw. Betritt-das-Schlachtfeld-Effekt eines Permanenten. */
    val onResolve: Effect? = null,
    val triggers: List<Trigger> = emptyList(),
    val activated: List<ActivatedAbility> = emptyList(),
    val statics: List<StaticAbility> = emptyList(),
    /** Fuer Quellen: welche Aspekte diese Quelle erzeugen kann. */
    val produces: Set<Aspect> = emptySet(),
    val rarity: Rarity = Rarity.HAEUFIG,
    val rulesText: String = "",
    val flavor: String = "",
    val isToken: Boolean = false,
    /** false = muss im Kompendium erst freigeschaltet werden. */
    val startsUnlocked: Boolean = true,
) {
    val isCreature: Boolean get() = type == CardType.KREATUR
    val isSource: Boolean get() = type == CardType.QUELLE

    /** Alle Staemme, zu denen diese Karte einen Bezug hat. */
    val relatedSubtypes: Set<Subtype> get() = subtypes + archetypes

    /** Typzeile fuer die Kartenansicht, z. B. "Kreatur - Bestie". */
    val typeLine: String
        get() = if (subtypes.isEmpty()) {
            type.label
        } else {
            "${type.label} - ${subtypes.joinToString(" ") { it.label }}"
        }

    /** Grobe Deckbau-Kennzahl fuer KI-Bewertung und Belohnungsgewichtung. */
    val manaValue: Int get() = cost.total
}
