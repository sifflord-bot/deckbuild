package dev.deckbuild.core.content

import dev.deckbuild.core.engine.Battle
import dev.deckbuild.core.engine.CardResolver
import dev.deckbuild.core.model.ActivatedAbility
import dev.deckbuild.core.model.Aspect
import dev.deckbuild.core.model.CardDef
import dev.deckbuild.core.model.CardType
import dev.deckbuild.core.model.Condition
import dev.deckbuild.core.model.ControllerScope
import dev.deckbuild.core.model.Cost
import dev.deckbuild.core.model.Effect
import dev.deckbuild.core.model.Filter
import dev.deckbuild.core.model.Keyword
import dev.deckbuild.core.model.Rarity
import dev.deckbuild.core.model.Selector
import dev.deckbuild.core.model.StaticAbility
import dev.deckbuild.core.model.Subtype
import dev.deckbuild.core.model.TargetKind
import dev.deckbuild.core.model.TargetSpec
import dev.deckbuild.core.model.Trigger
import dev.deckbuild.core.model.TriggerEvent
import dev.deckbuild.core.model.Value

/**
 * Alle Kartendefinitionen des Spiels.
 *
 * Namen, Aspekte und Schluesselwoerter sind eigenstaendig; uebernommen ist nur
 * das Genre-Grundgeruest (Ressourcen, Kreaturenkampf, Sofortzauber).
 */
object CardLibrary : CardResolver {

    // ------------------------------------------------------------- Zielvorgaben

    private val zielKreatur = TargetSpec(TargetKind.KREATUR, prompt = "Kreatur waehlen")
    private val zielGegnerKreatur = TargetSpec(
        TargetKind.KREATUR,
        Filter(types = setOf(CardType.KREATUR), controller = ControllerScope.GEGNERISCHE),
        prompt = "Gegnerische Kreatur waehlen",
    )
    private val zielEigeneKreatur = TargetSpec(
        TargetKind.KREATUR,
        Filter(types = setOf(CardType.KREATUR), controller = ControllerScope.EIGENE),
        prompt = "Eigene Kreatur waehlen",
    )
    private val zielBeliebig = TargetSpec(TargetKind.BELIEBIG, prompt = "Ziel waehlen")
    private val zielZauber = TargetSpec(TargetKind.ZAUBER_AUF_STAPEL, prompt = "Zauber waehlen")

    // ------------------------------------------------------------------ Quellen

    private fun quelle(id: String, name: String, aspect: Aspect) = CardDef(
        id = id,
        name = name,
        type = CardType.QUELLE,
        aspect = aspect,
        produces = setOf(aspect),
        rulesText = "Tappen: Erzeugt 1 Essenz (${aspect.label}).",
    )

    val QUELLEN: Map<Aspect, CardDef> = mapOf(
        Aspect.GLUT to quelle("q_glut", "Glutader", Aspect.GLUT),
        Aspect.FLUT to quelle("q_flut", "Flutspiegel", Aspect.FLUT),
        Aspect.ASCHE to quelle("q_asche", "Aschegrube", Aspect.ASCHE),
        Aspect.HAIN to quelle("q_hain", "Hainwurzel", Aspect.HAIN),
        Aspect.LICHT to quelle("q_licht", "Lichtquell", Aspect.LICHT),
        Aspect.NEUTRAL to quelle("q_neutral", "Oedland", Aspect.NEUTRAL),
    )

    // ----------------------------------------------------------- Spielsteine

    private val tokens: List<CardDef> = listOf(
        CardDef(
            id = Battle.TOKEN_KRISTALL,
            name = "Essenzkristall",
            type = CardType.QUELLE,
            aspect = Aspect.NEUTRAL,
            produces = setOf(Aspect.NEUTRAL),
            isToken = true,
            rulesText = "Spielstein. Tappen: Erzeugt 1 farblose Essenz.",
        ),
        CardDef(
            id = "tok_waechter",
            name = "Schildgeist",
            type = CardType.KREATUR,
            aspect = Aspect.LICHT,
            power = 1,
            toughness = 1,
            subtypes = setOf(Subtype.GEIST),
            isToken = true,
            rulesText = "Spielstein.",
        ),
        CardDef(
            id = "tok_skelett",
            name = "Knochendiener",
            type = CardType.KREATUR,
            aspect = Aspect.ASCHE,
            power = 1,
            toughness = 1,
            subtypes = setOf(Subtype.UNTOTER),
            isToken = true,
            rulesText = "Spielstein.",
        ),
        CardDef(
            id = "tok_wolf",
            name = "Dickichtwolf",
            type = CardType.KREATUR,
            aspect = Aspect.HAIN,
            power = 2,
            toughness = 2,
            subtypes = setOf(Subtype.BESTIE),
            isToken = true,
            rulesText = "Spielstein.",
        ),
        CardDef(
            id = "tok_funke",
            name = "Irrfunke",
            type = CardType.KREATUR,
            aspect = Aspect.GLUT,
            power = 1,
            toughness = 1,
            subtypes = setOf(Subtype.ELEMENTAR),
            keywords = setOf(Keyword.FLINK),
            isToken = true,
            rulesText = "Spielstein.",
        ),
    )

    // ---------------------------------------------------------------- Glut

    private val glut: List<CardDef> = listOf(
        CardDef(
            id = "glut_funkenlaeufer",
            name = "Funkenlaeufer",
            type = CardType.KREATUR,
            aspect = Aspect.GLUT,
            cost = Cost.colored(Aspect.GLUT, 1),
            power = 2,
            toughness = 1,
            subtypes = setOf(Subtype.ELEMENTAR),
            keywords = setOf(Keyword.FLINK),
            rulesText = "Flink.",
            flavor = "Er brennt schneller, als er denkt.",
        ),
        CardDef(
            id = "glut_aschekrieger",
            name = "Aschekrieger",
            type = CardType.KREATUR,
            aspect = Aspect.GLUT,
            cost = Cost.colored(Aspect.GLUT, 1, generic = 1),
            power = 3,
            toughness = 1,
            subtypes = setOf(Subtype.KRIEGER),
        ),
        CardDef(
            id = "glut_sengender_stoss",
            name = "Sengender Stoss",
            type = CardType.SPONTAN,
            aspect = Aspect.GLUT,
            cost = Cost.colored(Aspect.GLUT, 1),
            targets = listOf(zielBeliebig),
            onResolve = Effect.Schaden(Selector.Chosen(0), Value.of(3)),
            rulesText = "Fuegt einem beliebigen Ziel 3 Schaden zu.",
        ),
        CardDef(
            id = "glut_klingensturm",
            name = "Klingensturm",
            type = CardType.SPONTAN,
            aspect = Aspect.GLUT,
            cost = Cost.colored(Aspect.GLUT, 1, generic = 1),
            onResolve = Effect.Staerken(
                Selector.Alle(Filter.EIGENE_KREATUREN),
                power = 2,
                keywords = setOf(Keyword.FLINK),
            ),
            rulesText = "Eigene Kreaturen erhalten +2/+0 und Flink bis zum Zugende.",
            rarity = Rarity.SELTEN,
        ),
        CardDef(
            id = "glut_feuerregen",
            name = "Feuerregen",
            type = CardType.RITUAL,
            aspect = Aspect.GLUT,
            cost = Cost.colored(Aspect.GLUT, 1, generic = 2),
            onResolve = Effect.Schaden(Selector.Alle(Filter.GEGNERISCHE_KREATUREN), Value.of(2)),
            rulesText = "Fuegt allen gegnerischen Kreaturen 2 Schaden zu.",
            rarity = Rarity.SELTEN,
        ),
        CardDef(
            id = "glut_lohenwurm",
            name = "Lohenwurm",
            type = CardType.KREATUR,
            aspect = Aspect.GLUT,
            cost = Cost.colored(Aspect.GLUT, 2, generic = 3),
            power = 5,
            toughness = 4,
            subtypes = setOf(Subtype.DRACHE),
            keywords = setOf(Keyword.TRAMPELN),
            onResolve = Effect.Schaden(Selector.Feind, Value.of(2)),
            rulesText = "Trampeln. Betritt das Schlachtfeld: 2 Schaden am Gegner.",
            rarity = Rarity.SELTEN,
        ),
        CardDef(
            id = "glut_brandopfer",
            name = "Brandopfer",
            type = CardType.RITUAL,
            aspect = Aspect.GLUT,
            cost = Cost.colored(Aspect.GLUT, 1),
            keywords = setOf(Keyword.VERBRAUCH),
            targets = listOf(zielGegnerKreatur),
            onResolve = Effect.Schaden(Selector.Chosen(0), Value.of(4)),
            rulesText = "Verbrauch. Fuegt einer gegnerischen Kreatur 4 Schaden zu.",
        ),
        CardDef(
            id = "glut_glutmeisterin",
            name = "Glutmeisterin",
            type = CardType.KREATUR,
            aspect = Aspect.GLUT,
            archetypes = setOf(Subtype.ELEMENTAR),
            cost = Cost.colored(Aspect.GLUT, 2, generic = 2),
            power = 3,
            toughness = 3,
            subtypes = setOf(Subtype.KRIEGER),
            triggers = listOf(
                Trigger(
                    event = TriggerEvent.GREIFT_AN,
                    effect = Effect.Erschaffen(Selector.Du, "tok_funke", Value.of(1)),
                    text = "erschafft einen Irrfunken.",
                ),
            ),
            rulesText = "Immer wenn sie angreift, erschaffe einen 1/1 Irrfunken mit Flink.",
            rarity = Rarity.LEGENDAER,
            startsUnlocked = false,
        ),
        CardDef(
            id = "glut_kriegstrommler",
            name = "Kriegstrommler",
            type = CardType.KREATUR,
            aspect = Aspect.GLUT,
            archetypes = setOf(Subtype.KRIEGER),
            cost = Cost.colored(Aspect.GLUT, 1, generic = 2),
            power = 2,
            toughness = 2,
            subtypes = setOf(Subtype.KRIEGER),
            statics = listOf(
                StaticAbility(
                    filter = Filter.eigenerStamm(Subtype.KRIEGER),
                    power = 1,
                    text = "Andere eigene Krieger erhalten +1/+0.",
                ),
            ),
            rulesText = "Andere eigene Krieger erhalten +1/+0.",
            rarity = Rarity.SELTEN,
        ),
        CardDef(
            id = "glut_funkenweber",
            name = "Funkenweber",
            type = CardType.KREATUR,
            aspect = Aspect.GLUT,
            cost = Cost.colored(Aspect.GLUT, 1, generic = 2),
            power = 2,
            toughness = 2,
            subtypes = setOf(Subtype.MAGIER),
            triggers = listOf(
                Trigger(
                    event = TriggerEvent.ZAUBER_GEWIRKT,
                    effect = Effect.Schaden(Selector.Feind, Value.of(1)),
                    text = "sengt den Gegner.",
                ),
            ),
            rulesText = "Immer wenn du einen Zauber wirkst, erleidet der Gegner 1 Schaden.",
            rarity = Rarity.SELTEN,
        ),
        CardDef(
            id = "glut_brandmal",
            name = "Brandmal",
            type = CardType.SPONTAN,
            aspect = Aspect.GLUT,
            archetypes = setOf(Subtype.ELEMENTAR),
            cost = Cost.colored(Aspect.GLUT, 1, generic = 1),
            targets = listOf(zielBeliebig),
            onResolve = Effect.Schaden(
                Selector.Chosen(0),
                Value.basis(2, Filter.eigenerStamm(Subtype.ELEMENTAR)),
            ),
            rulesText = "Fuegt einem beliebigen Ziel 2 Schaden zu, plus 1 je eigenem Elementar.",
        ),
        CardDef(
            id = "glut_drachenhort",
            name = "Drachenhort",
            type = CardType.RELIKT,
            aspect = Aspect.GLUT,
            archetypes = setOf(Subtype.DRACHE),
            cost = Cost.colored(Aspect.GLUT, 1, generic = 1),
            statics = listOf(
                StaticAbility(
                    filter = Filter.eigenerStamm(Subtype.DRACHE),
                    power = 1,
                    toughness = 1,
                    text = "Eigene Drachen erhalten +1/+1.",
                ),
            ),
            rulesText = "Eigene Drachen erhalten +1/+1.",
            flavor = "Gold waermt nicht. Es sammelt sich trotzdem.",
            rarity = Rarity.SELTEN,
        ),
    )

    // ---------------------------------------------------------------- Flut

    private val flut: List<CardDef> = listOf(
        CardDef(
            id = "flut_nebelkundschafter",
            name = "Nebelkundschafter",
            type = CardType.KREATUR,
            aspect = Aspect.FLUT,
            cost = Cost.colored(Aspect.FLUT, 1, generic = 2),
            power = 2,
            toughness = 2,
            subtypes = setOf(Subtype.GEIST),
            keywords = setOf(Keyword.FLUG),
            onResolve = Effect.Ziehen(Selector.Du, Value.of(1)),
            rulesText = "Flug. Betritt das Schlachtfeld: Ziehe eine Karte.",
        ),
        CardDef(
            id = "flut_gezeitenruf",
            name = "Gezeitenruf",
            type = CardType.SPONTAN,
            aspect = Aspect.FLUT,
            cost = Cost.colored(Aspect.FLUT, 1, generic = 1),
            targets = listOf(zielKreatur),
            onResolve = Effect.kette(
                Effect.Zurueckgeben(Selector.Chosen(0)),
                Effect.Ziehen(Selector.Du, Value.of(1)),
            ),
            rulesText = "Bringe eine Kreatur auf die Hand zurueck. Ziehe eine Karte.",
        ),
        CardDef(
            id = "flut_bannwelle",
            name = "Bannwelle",
            type = CardType.SPONTAN,
            aspect = Aspect.FLUT,
            cost = Cost.colored(Aspect.FLUT, 1, generic = 1),
            targets = listOf(zielZauber),
            onResolve = Effect.Neutralisieren(Selector.Chosen(0)),
            rulesText = "Neutralisiere einen Zauber auf dem Stapel.",
            rarity = Rarity.SELTEN,
        ),
        CardDef(
            id = "flut_tiefenwaechter",
            name = "Tiefenwaechter",
            type = CardType.KREATUR,
            aspect = Aspect.FLUT,
            cost = Cost.colored(Aspect.FLUT, 1, generic = 2),
            power = 1,
            toughness = 5,
            subtypes = setOf(Subtype.BESTIE),
            keywords = setOf(Keyword.REICHWEITE),
            rulesText = "Reichweite.",
        ),
        CardDef(
            id = "flut_erkenntnis",
            name = "Erkenntnis",
            type = CardType.RITUAL,
            aspect = Aspect.FLUT,
            cost = Cost.colored(Aspect.FLUT, 1, generic = 1),
            onResolve = Effect.Ziehen(Selector.Du, Value.of(2)),
            rulesText = "Ziehe zwei Karten.",
        ),
        CardDef(
            id = "flut_spiegelrochen",
            name = "Spiegelrochen",
            type = CardType.KREATUR,
            aspect = Aspect.FLUT,
            cost = Cost.colored(Aspect.FLUT, 2, generic = 2),
            power = 2,
            toughness = 3,
            subtypes = setOf(Subtype.BESTIE),
            keywords = setOf(Keyword.FLUG),
            triggers = listOf(
                Trigger(
                    event = TriggerEvent.FUEGT_KAMPFSCHADEN_ZU,
                    effect = Effect.Ziehen(Selector.Du, Value.of(1)),
                    text = "laesst dich eine Karte ziehen.",
                ),
            ),
            rulesText = "Flug. Immer wenn er einem Spieler Kampfschaden zufuegt, ziehe eine Karte.",
            rarity = Rarity.SELTEN,
        ),
        CardDef(
            id = "flut_zeitriss",
            name = "Zeitriss",
            type = CardType.SPONTAN,
            aspect = Aspect.FLUT,
            cost = Cost.colored(Aspect.FLUT, 1, generic = 2),
            keywords = setOf(Keyword.VERBRAUCH),
            onResolve = Effect.Antappen(Selector.Alle(Filter.GEGNERISCHE_KREATUREN)),
            rulesText = "Verbrauch. Tappe alle gegnerischen Kreaturen.",
        ),
        CardDef(
            id = "flut_sturmherrin",
            name = "Sturmherrin der Tiefe",
            type = CardType.KREATUR,
            aspect = Aspect.FLUT,
            cost = Cost.colored(Aspect.FLUT, 2, generic = 4),
            power = 4,
            toughness = 5,
            subtypes = setOf(Subtype.MAGIER),
            keywords = setOf(Keyword.FLUG),
            onResolve = Effect.Zurueckgeben(Selector.Chosen(0)),
            targets = listOf(TargetSpec(TargetKind.KREATUR, Filter(types = setOf(CardType.KREATUR), controller = ControllerScope.GEGNERISCHE), optional = true)),
            rulesText = "Flug. Betritt das Schlachtfeld: Bringe eine gegnerische Kreatur auf die Hand zurueck.",
            rarity = Rarity.LEGENDAER,
            startsUnlocked = false,
        ),
        CardDef(
            id = "flut_zirkelmeisterin",
            name = "Zirkelmeisterin",
            type = CardType.KREATUR,
            aspect = Aspect.FLUT,
            archetypes = setOf(Subtype.MAGIER),
            cost = Cost.colored(Aspect.FLUT, 2, generic = 2),
            power = 2,
            toughness = 3,
            subtypes = setOf(Subtype.MAGIER),
            statics = listOf(
                StaticAbility(
                    filter = Filter.eigenerStamm(Subtype.MAGIER),
                    power = 1,
                    toughness = 1,
                    text = "Andere eigene Magier erhalten +1/+1.",
                ),
            ),
            rulesText = "Andere eigene Magier erhalten +1/+1.",
            rarity = Rarity.SELTEN,
        ),
        CardDef(
            id = "flut_arkanes_echo",
            name = "Arkanes Echo",
            type = CardType.KREATUR,
            aspect = Aspect.FLUT,
            cost = Cost.colored(Aspect.FLUT, 1, generic = 1),
            power = 1,
            toughness = 3,
            subtypes = setOf(Subtype.MAGIER),
            triggers = listOf(
                Trigger(
                    event = TriggerEvent.ZAUBER_GEWIRKT,
                    effect = Effect.Staerken(Selector.Selbst, power = 1),
                    text = "verstaerkt sich.",
                ),
            ),
            rulesText = "Immer wenn du einen Zauber wirkst, erhaelt es +1/+0 bis zum Zugende.",
        ),
        CardDef(
            id = "flut_gedankenflut",
            name = "Gedankenflut",
            type = CardType.RITUAL,
            aspect = Aspect.FLUT,
            cost = Cost.colored(Aspect.FLUT, 1, generic = 2),
            onResolve = Effect.Wenn(
                // Der Zauber zaehlt sich selbst mit: Bedingung 3 bedeutet, dass
                // vorher bereits zwei andere Zauber gewirkt wurden.
                condition = Condition.Mindestens(Value.ZauberDiesenZug, 3),
                dann = Effect.Ziehen(Selector.Du, Value.of(3)),
                sonst = Effect.Ziehen(Selector.Du, Value.of(1)),
            ),
            rulesText = "Ziehe eine Karte. Ist dies dein dritter Zauber in diesem Zug, ziehe stattdessen drei.",
            rarity = Rarity.SELTEN,
        ),
        CardDef(
            id = "flut_nebeldrache",
            name = "Nebeldrache",
            type = CardType.KREATUR,
            aspect = Aspect.FLUT,
            cost = Cost.colored(Aspect.FLUT, 1, generic = 3),
            power = 3,
            toughness = 3,
            subtypes = setOf(Subtype.DRACHE),
            keywords = setOf(Keyword.FLUG),
            rulesText = "Flug.",
            flavor = "Man hoert ihn erst, wenn der Nebel sich schliesst.",
        ),
    )

    // --------------------------------------------------------------- Asche

    private val asche: List<CardDef> = listOf(
        CardDef(
            id = "asche_grabkriecher",
            name = "Grabkriecher",
            type = CardType.KREATUR,
            aspect = Aspect.ASCHE,
            cost = Cost.colored(Aspect.ASCHE, 1, generic = 1),
            power = 2,
            toughness = 2,
            subtypes = setOf(Subtype.UNTOTER),
        ),
        CardDef(
            id = "asche_seelenzehrer",
            name = "Seelenzehrer",
            type = CardType.KREATUR,
            aspect = Aspect.ASCHE,
            cost = Cost.colored(Aspect.ASCHE, 2, generic = 2),
            power = 3,
            toughness = 3,
            subtypes = setOf(Subtype.GEIST),
            keywords = setOf(Keyword.ZEHRUNG),
            rulesText = "Zehrung.",
        ),
        CardDef(
            id = "asche_todeskuss",
            name = "Todeskuss",
            type = CardType.SPONTAN,
            aspect = Aspect.ASCHE,
            cost = Cost.colored(Aspect.ASCHE, 1),
            targets = listOf(zielGegnerKreatur),
            onResolve = Effect.Staerken(Selector.Chosen(0), power = -3, toughness = -3),
            rulesText = "Eine gegnerische Kreatur erhaelt -3/-3 bis zum Zugende.",
        ),
        CardDef(
            id = "asche_knochensammler",
            name = "Knochensammler",
            type = CardType.KREATUR,
            aspect = Aspect.ASCHE,
            archetypes = setOf(Subtype.UNTOTER),
            cost = Cost.colored(Aspect.ASCHE, 1, generic = 2),
            power = 2,
            toughness = 2,
            subtypes = setOf(Subtype.UNTOTER),
            onResolve = Effect.Abwerfen(Selector.Feind, Value.of(1)),
            triggers = listOf(
                Trigger(
                    event = TriggerEvent.STIRBT,
                    effect = Effect.Erschaffen(Selector.Du, "tok_skelett", Value.of(2)),
                    text = "hinterlaesst zwei Knochendiener.",
                ),
            ),
            rulesText = "Betritt das Schlachtfeld: Der Gegner wirft eine Karte ab. Stirbt: Erschaffe zwei 1/1 Knochendiener.",
            rarity = Rarity.SELTEN,
        ),
        CardDef(
            id = "asche_wiedergaenger",
            name = "Ruf der Wiedergaenger",
            type = CardType.RITUAL,
            aspect = Aspect.ASCHE,
            cost = Cost.colored(Aspect.ASCHE, 1, generic = 3),
            onResolve = Effect.Wiederbeleben(Selector.Du, maxCost = 6),
            rulesText = "Bringe die staerkste Kreatur mit Kosten bis 6 aus deinem Friedhof ins Spiel.",
            rarity = Rarity.SELTEN,
        ),
        CardDef(
            id = "asche_blutpakt",
            name = "Blutpakt",
            type = CardType.RITUAL,
            aspect = Aspect.ASCHE,
            cost = Cost.colored(Aspect.ASCHE, 1),
            onResolve = Effect.kette(
                Effect.Ziehen(Selector.Du, Value.of(2)),
                Effect.Schaden(Selector.Du, Value.of(3)),
            ),
            rulesText = "Ziehe zwei Karten. Du erleidest 3 Schaden.",
        ),
        CardDef(
            id = "asche_verderbnis",
            name = "Verderbnis",
            type = CardType.RITUAL,
            aspect = Aspect.ASCHE,
            cost = Cost.colored(Aspect.ASCHE, 2, generic = 2),
            targets = listOf(zielGegnerKreatur),
            onResolve = Effect.kette(
                Effect.Zerstoeren(Selector.Chosen(0)),
                Effect.Schaden(Selector.Feind, Value.of(2)),
            ),
            rulesText = "Zerstoere eine gegnerische Kreatur. Der Gegner erleidet 2 Schaden.",
            rarity = Rarity.SELTEN,
        ),
        CardDef(
            id = "asche_gierschlund",
            name = "Gierschlund",
            type = CardType.KREATUR,
            aspect = Aspect.ASCHE,
            cost = Cost.colored(Aspect.ASCHE, 1, generic = 4),
            power = 4,
            toughness = 4,
            subtypes = setOf(Subtype.BESTIE),
            keywords = setOf(Keyword.GIFT),
            rulesText = "Gift.",
        ),
        CardDef(
            id = "asche_fuerst",
            name = "Fuerst der Aschelande",
            type = CardType.KREATUR,
            aspect = Aspect.ASCHE,
            cost = Cost.colored(Aspect.ASCHE, 2, generic = 3),
            power = 3,
            toughness = 4,
            subtypes = setOf(Subtype.UNTOTER),
            keywords = setOf(Keyword.FLUG, Keyword.ZEHRUNG),
            triggers = listOf(
                Trigger(
                    event = TriggerEvent.ZUG_BEGINN,
                    effect = Effect.kette(
                        Effect.Schaden(Selector.Feind, Value.of(1)),
                        Effect.Heilung(Selector.Du, Value.of(1)),
                    ),
                    text = "entzieht dem Gegner 1 Leben.",
                ),
            ),
            rulesText = "Flug, Zehrung. Zu Beginn deines Zuges: Der Gegner verliert 1 Leben, du heilst 1.",
            rarity = Rarity.LEGENDAER,
            startsUnlocked = false,
        ),
        CardDef(
            id = "asche_gebeinvogt",
            name = "Gebeinvogt",
            type = CardType.KREATUR,
            aspect = Aspect.ASCHE,
            archetypes = setOf(Subtype.UNTOTER),
            cost = Cost.colored(Aspect.ASCHE, 2, generic = 1),
            power = 2,
            toughness = 2,
            subtypes = setOf(Subtype.UNTOTER),
            statics = listOf(
                StaticAbility(
                    filter = Filter.eigenerStamm(Subtype.UNTOTER),
                    power = 1,
                    toughness = 1,
                    text = "Andere eigene Untote erhalten +1/+1.",
                ),
            ),
            rulesText = "Andere eigene Untote erhalten +1/+1.",
            rarity = Rarity.SELTEN,
        ),
        CardDef(
            id = "asche_massengrab",
            name = "Massengrab",
            type = CardType.RITUAL,
            aspect = Aspect.ASCHE,
            archetypes = setOf(Subtype.UNTOTER),
            cost = Cost.colored(Aspect.ASCHE, 1, generic = 3),
            onResolve = Effect.Erschaffen(
                Selector.Du,
                "tok_skelett",
                Value.basis(1, Filter.eigenerStamm(Subtype.UNTOTER)),
            ),
            rulesText = "Erschaffe einen 1/1 Knochendiener, plus einen je eigenem Untoten.",
            rarity = Rarity.SELTEN,
        ),
        CardDef(
            id = "asche_knochendrache",
            name = "Knochendrache",
            type = CardType.KREATUR,
            aspect = Aspect.ASCHE,
            cost = Cost.colored(Aspect.ASCHE, 1, generic = 4),
            power = 4,
            toughness = 4,
            // Gehoert beiden Staemmen an - damit haben Untoten-Decks eine Spitze
            // und Drachen-Decks einen Anschluss an den Friedhof.
            subtypes = setOf(Subtype.DRACHE, Subtype.UNTOTER),
            keywords = setOf(Keyword.FLUG),
            rulesText = "Flug.",
            rarity = Rarity.SELTEN,
        ),
    )

    // ---------------------------------------------------------------- Hain

    private val hain: List<CardDef> = listOf(
        CardDef(
            id = "hain_wurzelaeltester",
            name = "Wurzelaeltester",
            type = CardType.KREATUR,
            aspect = Aspect.HAIN,
            cost = Cost.colored(Aspect.HAIN, 1, generic = 1),
            power = 1,
            toughness = 3,
            subtypes = setOf(Subtype.ELEMENTAR),
            activated = listOf(
                ActivatedAbility(
                    tapSelf = true,
                    effect = Effect.Essenz(Selector.Du, 1),
                    text = "Tappen: Erzeuge 1 Essenz.",
                ),
            ),
            rulesText = "Tappen: Erzeuge 1 farblose Essenz.",
        ),
        CardDef(
            id = "hain_dickichtwolf",
            name = "Rudelfuehrer",
            type = CardType.KREATUR,
            aspect = Aspect.HAIN,
            cost = Cost.colored(Aspect.HAIN, 1, generic = 1),
            power = 3,
            toughness = 2,
            subtypes = setOf(Subtype.BESTIE),
        ),
        CardDef(
            id = "hain_lebensfluss",
            name = "Lebensfluss",
            type = CardType.RITUAL,
            aspect = Aspect.HAIN,
            cost = Cost.colored(Aspect.HAIN, 1, generic = 1),
            onResolve = Effect.Essenz(Selector.Du, 1, dauerhaft = true),
            rulesText = "Erschaffe einen Essenzkristall - eine dauerhafte zusaetzliche Quelle.",
        ),
        CardDef(
            id = "hain_baumhueter",
            name = "Baumhueter",
            type = CardType.KREATUR,
            aspect = Aspect.HAIN,
            cost = Cost.colored(Aspect.HAIN, 1, generic = 2),
            power = 2,
            toughness = 4,
            subtypes = setOf(Subtype.ELEMENTAR),
            keywords = setOf(Keyword.REICHWEITE),
            rulesText = "Reichweite.",
        ),
        CardDef(
            id = "hain_rankengriff",
            name = "Rankengriff",
            type = CardType.SPONTAN,
            aspect = Aspect.HAIN,
            cost = Cost.colored(Aspect.HAIN, 1),
            targets = listOf(zielEigeneKreatur),
            onResolve = Effect.Staerken(Selector.Chosen(0), power = 3, toughness = 3),
            rulesText = "Eine eigene Kreatur erhaelt +3/+3 bis zum Zugende.",
        ),
        CardDef(
            id = "hain_wildwuchs",
            name = "Wildwuchs",
            type = CardType.RITUAL,
            aspect = Aspect.HAIN,
            cost = Cost.colored(Aspect.HAIN, 1, generic = 3),
            onResolve = Effect.Staerken(
                Selector.Alle(Filter.EIGENE_KREATUREN),
                power = 1,
                toughness = 1,
                dauerhaft = true,
            ),
            rulesText = "Alle eigenen Kreaturen erhalten dauerhaft +1/+1.",
            rarity = Rarity.SELTEN,
        ),
        CardDef(
            id = "hain_urhornvieh",
            name = "Urhornvieh",
            type = CardType.KREATUR,
            aspect = Aspect.HAIN,
            cost = Cost.colored(Aspect.HAIN, 2, generic = 4),
            power = 7,
            toughness = 7,
            subtypes = setOf(Subtype.BESTIE),
            keywords = setOf(Keyword.TRAMPELN),
            rulesText = "Trampeln.",
            rarity = Rarity.SELTEN,
        ),
        CardDef(
            id = "hain_hueterin",
            name = "Hueterin des Hains",
            type = CardType.KREATUR,
            aspect = Aspect.HAIN,
            cost = Cost.colored(Aspect.HAIN, 2, generic = 2),
            power = 3,
            toughness = 3,
            subtypes = setOf(Subtype.KRIEGER),
            triggers = listOf(
                Trigger(
                    event = TriggerEvent.ANDERE_KREATUR_BETRITT,
                    filter = Filter(types = setOf(CardType.KREATUR), controller = ControllerScope.EIGENE),
                    effect = Effect.Staerken(Selector.Selbst, power = 1, toughness = 1, dauerhaft = true),
                    text = "waechst um +1/+1.",
                ),
            ),
            rulesText = "Immer wenn eine andere eigene Kreatur ins Spiel kommt, waechst die Hueterin dauerhaft um +1/+1.",
            rarity = Rarity.LEGENDAER,
            startsUnlocked = false,
        ),
        CardDef(
            id = "hain_rudelaeltester",
            name = "Rudelaeltester",
            type = CardType.KREATUR,
            aspect = Aspect.HAIN,
            archetypes = setOf(Subtype.BESTIE),
            cost = Cost.colored(Aspect.HAIN, 2, generic = 2),
            power = 3,
            toughness = 3,
            subtypes = setOf(Subtype.BESTIE),
            statics = listOf(
                StaticAbility(
                    filter = Filter.eigenerStamm(Subtype.BESTIE),
                    power = 1,
                    toughness = 1,
                    text = "Andere eigene Bestien erhalten +1/+1.",
                ),
            ),
            rulesText = "Andere eigene Bestien erhalten +1/+1.",
            rarity = Rarity.SELTEN,
        ),
        CardDef(
            id = "hain_meutenruf",
            name = "Meutenruf",
            type = CardType.RITUAL,
            aspect = Aspect.HAIN,
            archetypes = setOf(Subtype.BESTIE),
            cost = Cost.colored(Aspect.HAIN, 1, generic = 2),
            onResolve = Effect.Erschaffen(Selector.Du, "tok_wolf", Value.of(2)),
            rulesText = "Erschaffe zwei 2/2 Dickichtwoelfe.",
        ),
        CardDef(
            id = "hain_urinstinkt",
            name = "Urinstinkt",
            type = CardType.SPONTAN,
            aspect = Aspect.HAIN,
            archetypes = setOf(Subtype.BESTIE),
            cost = Cost.colored(Aspect.HAIN, 1, generic = 1),
            onResolve = Effect.Staerken(
                Selector.Alle(Filter.eigenerStamm(Subtype.BESTIE)),
                power = 2,
                toughness = 2,
            ),
            rulesText = "Eigene Bestien erhalten +2/+2 bis zum Zugende.",
        ),
    )

    // --------------------------------------------------------------- Licht

    private val licht: List<CardDef> = listOf(
        CardDef(
            id = "licht_schildwache",
            name = "Schildwache",
            type = CardType.KREATUR,
            aspect = Aspect.LICHT,
            cost = Cost.colored(Aspect.LICHT, 1, generic = 1),
            power = 2,
            toughness = 3,
            subtypes = setOf(Subtype.KRIEGER),
            keywords = setOf(Keyword.WACHT),
            rulesText = "Wacht.",
        ),
        CardDef(
            id = "licht_glaubensbote",
            name = "Glaubensbote",
            type = CardType.KREATUR,
            aspect = Aspect.LICHT,
            cost = Cost.colored(Aspect.LICHT, 1, generic = 1),
            power = 2,
            toughness = 2,
            subtypes = setOf(Subtype.GEIST),
            keywords = setOf(Keyword.FLUG),
            rulesText = "Flug.",
        ),
        CardDef(
            id = "licht_heilige_flamme",
            name = "Heilige Flamme",
            type = CardType.SPONTAN,
            aspect = Aspect.LICHT,
            cost = Cost.colored(Aspect.LICHT, 1, generic = 1),
            onResolve = Effect.Heilung(Selector.Du, Value.of(6)),
            rulesText = "Heile 6 Leben.",
        ),
        CardDef(
            id = "licht_aufgebot",
            name = "Aufgebot",
            type = CardType.RITUAL,
            aspect = Aspect.LICHT,
            archetypes = setOf(Subtype.GEIST),
            cost = Cost.colored(Aspect.LICHT, 1, generic = 2),
            onResolve = Effect.Erschaffen(Selector.Du, "tok_waechter", Value.of(3)),
            rulesText = "Erschaffe drei 1/1 Schildgeister.",
        ),
        CardDef(
            id = "licht_bannspruch",
            name = "Bannspruch",
            type = CardType.SPONTAN,
            aspect = Aspect.LICHT,
            cost = Cost.colored(Aspect.LICHT, 1, generic = 2),
            targets = listOf(zielGegnerKreatur),
            onResolve = Effect.Verbannen(Selector.Chosen(0)),
            rulesText = "Verbanne eine gegnerische Kreatur.",
            rarity = Rarity.SELTEN,
        ),
        CardDef(
            id = "licht_schutzsiegel",
            name = "Schutzsiegel",
            type = CardType.SPONTAN,
            aspect = Aspect.LICHT,
            cost = Cost.colored(Aspect.LICHT, 1),
            keywords = setOf(Keyword.VERBRAUCH),
            targets = listOf(zielEigeneKreatur),
            onResolve = Effect.Staerken(
                Selector.Chosen(0),
                toughness = 2,
                keywords = setOf(Keyword.SCHILD),
            ),
            rulesText = "Verbrauch. Eine eigene Kreatur erhaelt +0/+2 und Schild.",
        ),
        CardDef(
            id = "licht_hochmeister",
            name = "Hochmeister der Morgenwacht",
            type = CardType.KREATUR,
            aspect = Aspect.LICHT,
            cost = Cost.colored(Aspect.LICHT, 2, generic = 3),
            power = 3,
            toughness = 4,
            subtypes = setOf(Subtype.KRIEGER),
            statics = listOf(
                StaticAbility(
                    filter = Filter(types = setOf(CardType.KREATUR), controller = ControllerScope.EIGENE),
                    power = 1,
                    toughness = 1,
                    text = "Andere eigene Kreaturen erhalten +1/+1.",
                ),
            ),
            rulesText = "Andere eigene Kreaturen erhalten +1/+1.",
            rarity = Rarity.SELTEN,
        ),
        CardDef(
            id = "licht_lichtwaechterin",
            name = "Lichtwaechterin",
            type = CardType.KREATUR,
            aspect = Aspect.LICHT,
            cost = Cost.colored(Aspect.LICHT, 2, generic = 2),
            power = 2,
            toughness = 5,
            subtypes = setOf(Subtype.GEIST),
            keywords = setOf(Keyword.WACHT, Keyword.FLUG),
            triggers = listOf(
                Trigger(
                    event = TriggerEvent.ZUG_ENDE,
                    effect = Effect.Wenn(
                        condition = Condition.GegnerUnterHalbenLeben,
                        dann = Effect.Heilung(Selector.Du, Value.of(3)),
                        sonst = Effect.Heilung(Selector.Du, Value.of(1)),
                    ),
                    text = "spendet Leben.",
                ),
            ),
            rulesText = "Wacht, Flug. Am Ende deines Zuges heilst du 1 Leben (3, wenn du unter der Haelfte bist).",
            rarity = Rarity.LEGENDAER,
            startsUnlocked = false,
        ),
        CardDef(
            id = "licht_bannerherrin",
            name = "Bannerherrin",
            type = CardType.KREATUR,
            aspect = Aspect.LICHT,
            archetypes = setOf(Subtype.KRIEGER),
            cost = Cost.colored(Aspect.LICHT, 2, generic = 1),
            power = 2,
            toughness = 3,
            subtypes = setOf(Subtype.KRIEGER),
            statics = listOf(
                StaticAbility(
                    filter = Filter.eigenerStamm(Subtype.KRIEGER),
                    power = 1,
                    toughness = 1,
                    text = "Andere eigene Krieger erhalten +1/+1.",
                ),
            ),
            rulesText = "Andere eigene Krieger erhalten +1/+1.",
            rarity = Rarity.SELTEN,
        ),
        CardDef(
            id = "licht_geisterrufer",
            name = "Geisterrufer",
            type = CardType.KREATUR,
            aspect = Aspect.LICHT,
            archetypes = setOf(Subtype.GEIST),
            cost = Cost.colored(Aspect.LICHT, 1, generic = 2),
            power = 2,
            toughness = 2,
            subtypes = setOf(Subtype.GEIST),
            onResolve = Effect.Erschaffen(Selector.Du, "tok_waechter", Value.of(1)),
            rulesText = "Betritt das Schlachtfeld: Erschaffe einen 1/1 Schildgeist.",
        ),
    )

    // ------------------------------------------------------------- Neutral

    private val neutral: List<CardDef> = listOf(
        CardDef(
            id = "neutral_wachkonstrukt",
            name = "Wachkonstrukt",
            type = CardType.KREATUR,
            aspect = Aspect.NEUTRAL,
            cost = Cost(generic = 3),
            power = 3,
            toughness = 3,
            subtypes = setOf(Subtype.KONSTRUKT),
        ),
        CardDef(
            id = "neutral_kristallsplitter",
            name = "Kristallsplitter",
            type = CardType.RELIKT,
            aspect = Aspect.NEUTRAL,
            cost = Cost(generic = 2),
            activated = listOf(
                ActivatedAbility(
                    tapSelf = true,
                    effect = Effect.Essenz(Selector.Du, 1),
                    text = "Tappen: Erzeuge 1 Essenz.",
                ),
            ),
            rulesText = "Tappen: Erzeuge 1 farblose Essenz.",
        ),
        CardDef(
            id = "neutral_pluendertrupp",
            name = "Pluendertrupp",
            type = CardType.KREATUR,
            aspect = Aspect.NEUTRAL,
            cost = Cost(generic = 4),
            power = 4,
            toughness = 3,
            subtypes = setOf(Subtype.KRIEGER),
            onResolve = Effect.Ziehen(Selector.Du, Value.of(1)),
            rulesText = "Betritt das Schlachtfeld: Ziehe eine Karte.",
        ),
        CardDef(
            id = "neutral_scherbenschlag",
            name = "Scherbenschlag",
            type = CardType.RITUAL,
            aspect = Aspect.NEUTRAL,
            cost = Cost(generic = 5),
            keywords = setOf(Keyword.VERBRAUCH),
            onResolve = Effect.Zerstoeren(Selector.Alle(Filter.KREATUREN)),
            rulesText = "Verbrauch. Zerstoere alle Kreaturen.",
            rarity = Rarity.LEGENDAER,
            startsUnlocked = false,
        ),
        CardDef(
            id = "neutral_schildwall",
            name = "Schildwall",
            type = CardType.KREATUR,
            aspect = Aspect.NEUTRAL,
            cost = Cost(generic = 2),
            power = 0,
            toughness = 5,
            subtypes = setOf(Subtype.KONSTRUKT),
            keywords = setOf(Keyword.WAECHTER),
            rulesText = "Waechter.",
            flavor = "Es hat nie einen Schritt getan.",
        ),
        CardDef(
            id = "neutral_bergungskonstrukt",
            name = "Bergungskonstrukt",
            type = CardType.KREATUR,
            aspect = Aspect.NEUTRAL,
            cost = Cost(generic = 4),
            power = 2,
            toughness = 3,
            subtypes = setOf(Subtype.KONSTRUKT),
            onResolve = Effect.Wiederbeleben(Selector.Du, maxCost = 2),
            rulesText = "Betritt das Schlachtfeld: Bringe eine Kreatur mit Kosten bis 2 aus deinem Friedhof ins Spiel.",
        ),
    )

    // ----------------------------------------------------------------- Zugriff

    /** Alle spielbaren Karten ohne Quellen und Spielsteine. */
    val spells: List<CardDef> = glut + flut + asche + hain + licht + neutral

    val all: List<CardDef> = spells + QUELLEN.values + tokens

    private val byId: Map<String, CardDef> = all.associateBy { it.id }

    override fun resolve(id: String): CardDef =
        byId[id] ?: error("Unbekannte Karte: $id")

    fun find(id: String): CardDef? = byId[id]

    fun byAspect(aspect: Aspect): List<CardDef> = spells.filter { it.aspect == aspect }

    /** Karten, die zu Spielbeginn ohne Freischaltung verfuegbar sind. */
    val defaultUnlocked: List<CardDef> = spells.filter { it.startsUnlocked }

    /** Karten, die erst ueber das Kompendium freigeschaltet werden. */
    val lockedCards: List<CardDef> = spells.filter { !it.startsUnlocked }
}
