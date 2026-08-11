package dev.deckbuild.core.content

import dev.deckbuild.core.ai.BrainSkill
import dev.deckbuild.core.model.Aspect
import dev.deckbuild.core.model.CardDef
import dev.deckbuild.core.model.Rarity
import dev.deckbuild.core.util.Rng

enum class EncounterKind(val label: String) {
    NORMAL("Gefecht"),
    ELITE("Elite"),
    BOSS("Boss"),
}

/**
 * Vorlage eines Gegners. Das konkrete Deck wird pro Begegnung aus den Aspekten
 * und der Stufe erzeugt, damit dieselbe Vorlage endlos weiterskalieren kann.
 */
data class EnemyDef(
    val id: String,
    val name: String,
    val flavor: String,
    val aspects: List<Aspect>,
    val kind: EncounterKind,
    /** Ab welcher Laufstufe dieser Gegner auftauchen darf. */
    val minTier: Int,
    val baseLife: Int,
    val skill: BrainSkill,
    /** Karten, die das Deck immer enthaelt und die den Gegner charakterisieren. */
    val signature: List<String> = emptyList(),
    /** Karten, die ein Sieg dauerhaft im Kompendium freischaltet. */
    val unlocks: List<String> = emptyList(),
)

/** Fertig aufgebaute Begegnung, wie sie an [dev.deckbuild.core.engine.Battle] uebergeben wird. */
data class EnemyBuild(
    val def: EnemyDef,
    val name: String,
    val life: Int,
    val deck: List<CardDef>,
    val skill: BrainSkill,
    /** Vorab bereitstehende Essenzkristalle - der Schwierigkeitsregler fuer spaete Stufen. */
    val startingSources: Int,
    val tier: Int,
)

object Enemies {

    val roster: List<EnemyDef> = listOf(
        // ---------------------------------------------------------- Normal
        EnemyDef(
            id = "streuner",
            name = "Streuner der Aschewege",
            flavor = "Er hat nichts zu verlieren und weiss es.",
            aspects = listOf(Aspect.ASCHE),
            kind = EncounterKind.NORMAL,
            minTier = 0,
            baseLife = 22,
            skill = BrainSkill.EINFACH,
            signature = listOf("asche_grabkriecher", "asche_todeskuss"),
        ),
        EnemyDef(
            id = "glutkultist",
            name = "Glutkultist",
            flavor = "Jede Flamme ist ein Gebet.",
            aspects = listOf(Aspect.GLUT),
            kind = EncounterKind.NORMAL,
            minTier = 0,
            baseLife = 20,
            skill = BrainSkill.EINFACH,
            signature = listOf("glut_funkenlaeufer", "glut_sengender_stoss"),
        ),
        EnemyDef(
            id = "hainstreicher",
            name = "Hainstreicher",
            flavor = "Das Dickicht schliesst sich hinter ihm.",
            aspects = listOf(Aspect.HAIN),
            kind = EncounterKind.NORMAL,
            minTier = 1,
            baseLife = 26,
            skill = BrainSkill.NORMAL,
            signature = listOf("hain_dickichtwolf", "hain_baumhueter"),
        ),
        EnemyDef(
            id = "tiefenfischer",
            name = "Tiefenfischer",
            flavor = "Er angelt nicht nach Fischen.",
            aspects = listOf(Aspect.FLUT),
            kind = EncounterKind.NORMAL,
            minTier = 1,
            baseLife = 24,
            skill = BrainSkill.NORMAL,
            signature = listOf("flut_nebelkundschafter", "flut_gezeitenruf"),
        ),
        EnemyDef(
            id = "novize",
            name = "Novize der Morgenwacht",
            flavor = "Noch glaubt er an Regeln.",
            aspects = listOf(Aspect.LICHT),
            kind = EncounterKind.NORMAL,
            minTier = 1,
            baseLife = 26,
            skill = BrainSkill.NORMAL,
            signature = listOf("licht_schildwache", "licht_aufgebot"),
        ),
        EnemyDef(
            id = "soeldnerbande",
            name = "Soeldnerbande",
            flavor = "Bezahlt wird nach Gewicht.",
            aspects = listOf(Aspect.GLUT, Aspect.NEUTRAL),
            kind = EncounterKind.NORMAL,
            minTier = 2,
            baseLife = 28,
            skill = BrainSkill.NORMAL,
            signature = listOf("neutral_wachkonstrukt", "glut_aschekrieger"),
        ),
        EnemyDef(
            id = "moorhexe",
            name = "Moorhexe",
            flavor = "Sie sammelt, was andere zuruecklassen.",
            aspects = listOf(Aspect.ASCHE, Aspect.HAIN),
            kind = EncounterKind.NORMAL,
            minTier = 3,
            baseLife = 30,
            skill = BrainSkill.SCHLAU,
            signature = listOf("asche_knochensammler", "hain_rankengriff"),
        ),

        // ------------------------------------------------------------ Elite
        EnemyDef(
            id = "klingenmeister",
            name = "Klingenmeister Sarn",
            flavor = "Drei Schnitte. Mehr braucht er nie.",
            aspects = listOf(Aspect.GLUT, Aspect.NEUTRAL),
            kind = EncounterKind.ELITE,
            minTier = 1,
            baseLife = 34,
            skill = BrainSkill.SCHLAU,
            signature = listOf("glut_klingensturm", "glut_aschekrieger", "glut_lohenwurm"),
        ),
        EnemyDef(
            id = "nebelseherin",
            name = "Nebelseherin Ylva",
            flavor = "Sie kennt deinen naechsten Zug bereits.",
            aspects = listOf(Aspect.FLUT),
            kind = EncounterKind.ELITE,
            minTier = 2,
            baseLife = 32,
            skill = BrainSkill.SCHLAU,
            signature = listOf("flut_bannwelle", "flut_bannwelle", "flut_spiegelrochen"),
        ),
        EnemyDef(
            id = "knochenvogt",
            name = "Knochenvogt",
            flavor = "Sein Heer wird mit jedem Gefallenen groesser.",
            aspects = listOf(Aspect.ASCHE),
            kind = EncounterKind.ELITE,
            minTier = 2,
            baseLife = 36,
            skill = BrainSkill.SCHLAU,
            signature = listOf("asche_knochensammler", "asche_wiedergaenger", "asche_gierschlund"),
        ),
        EnemyDef(
            id = "hueter_der_lichtung",
            name = "Hueter der Lichtung",
            flavor = "Alles hier waechst nach seinem Willen.",
            aspects = listOf(Aspect.HAIN, Aspect.LICHT),
            kind = EncounterKind.ELITE,
            minTier = 3,
            baseLife = 38,
            skill = BrainSkill.SCHLAU,
            signature = listOf("hain_wildwuchs", "hain_urhornvieh", "licht_hochmeister"),
        ),

        // ------------------------------------------------------------- Boss
        EnemyDef(
            id = "aschefuerst",
            name = "Der Aschefuerst",
            flavor = "Was er beruehrt, wird Teil von ihm.",
            aspects = listOf(Aspect.ASCHE),
            kind = EncounterKind.BOSS,
            minTier = 0,
            baseLife = 38,
            skill = BrainSkill.SCHLAU,
            signature = listOf("asche_fuerst", "asche_verderbnis", "asche_gierschlund", "asche_wiedergaenger"),
            unlocks = listOf("asche_fuerst"),
        ),
        EnemyDef(
            id = "sturmherrin",
            name = "Sturmherrin Nael",
            flavor = "Die Flut kommt nicht. Sie war immer schon da.",
            aspects = listOf(Aspect.FLUT),
            kind = EncounterKind.BOSS,
            minTier = 1,
            baseLife = 36,
            skill = BrainSkill.SCHLAU,
            signature = listOf("flut_sturmherrin", "flut_bannwelle", "flut_zeitriss", "flut_spiegelrochen"),
            unlocks = listOf("flut_sturmherrin"),
        ),
        EnemyDef(
            id = "lohengeist",
            name = "Lohengeist Kaerul",
            flavor = "Er zaehlt deine Zuege rueckwaerts.",
            aspects = listOf(Aspect.GLUT),
            kind = EncounterKind.BOSS,
            minTier = 2,
            baseLife = 44,
            skill = BrainSkill.SCHLAU,
            signature = listOf("glut_glutmeisterin", "glut_lohenwurm", "glut_feuerregen", "glut_klingensturm"),
            unlocks = listOf("glut_glutmeisterin"),
        ),
        EnemyDef(
            id = "weltenbaumwaechter",
            name = "Waechter des Weltenbaums",
            flavor = "Wurzeln haben mehr Geduld als Schwerter.",
            aspects = listOf(Aspect.HAIN),
            kind = EncounterKind.BOSS,
            minTier = 3,
            baseLife = 54,
            skill = BrainSkill.SCHLAU,
            signature = listOf("hain_hueterin", "hain_urhornvieh", "hain_wildwuchs", "hain_lebensfluss"),
            unlocks = listOf("hain_hueterin"),
        ),
        EnemyDef(
            id = "morgenmeister",
            name = "Morgenmeister Aurel",
            flavor = "Er heilt schneller, als du schlagen kannst.",
            aspects = listOf(Aspect.LICHT),
            kind = EncounterKind.BOSS,
            minTier = 4,
            baseLife = 52,
            skill = BrainSkill.SCHLAU,
            signature = listOf("licht_lichtwaechterin", "licht_hochmeister", "licht_bannspruch", "licht_aufgebot"),
            unlocks = listOf("licht_lichtwaechterin"),
        ),
        EnemyDef(
            id = "scherbenkoenig",
            name = "Der Scherbenkoenig",
            flavor = "Am Ende bleibt nur die Scherbe.",
            aspects = listOf(Aspect.NEUTRAL, Aspect.ASCHE, Aspect.GLUT),
            kind = EncounterKind.BOSS,
            minTier = 6,
            baseLife = 60,
            skill = BrainSkill.SCHLAU,
            signature = listOf("neutral_scherbenschlag", "asche_gierschlund", "glut_lohenwurm", "neutral_pluendertrupp"),
            unlocks = listOf("neutral_scherbenschlag"),
        ),
    )

    fun byKind(kind: EncounterKind, tier: Int): List<EnemyDef> =
        roster.filter { it.kind == kind && it.minTier <= tier }
            .ifEmpty { roster.filter { it.kind == kind } }

    fun find(id: String): EnemyDef? = roster.firstOrNull { it.id == id }

    // ------------------------------------------------------------- Deckaufbau

    private const val SOURCE_COUNT = 8

    /**
     * Baut das Deck einer Begegnung. Die Stufe erhoeht Lebenspunkte, die
     * Obergrenze der Kartenkosten und die Wahrscheinlichkeit seltener Karten -
     * so bleibt dieselbe Vorlage auch in Stufe 40 noch bedrohlich.
     */
    fun build(def: EnemyDef, tier: Int, rng: Rng): EnemyBuild {
        val deck = mutableListOf<CardDef>()

        val sourcesPerAspect = SOURCE_COUNT / def.aspects.size
        for (aspect in def.aspects) {
            val source = CardLibrary.QUELLEN[aspect] ?: CardLibrary.QUELLEN.getValue(Aspect.NEUTRAL)
            repeat(sourcesPerAspect) { deck += source }
        }
        while (deck.size < SOURCE_COUNT) {
            deck += CardLibrary.QUELLEN.getValue(def.aspects.first())
        }

        def.signature.mapNotNull { CardLibrary.find(it) }.forEach { deck += it }

        val maxCost = 3 + tier
        val pool = CardLibrary.spells.filter { card ->
            (card.aspect in def.aspects || card.aspect == Aspect.NEUTRAL) && card.manaValue <= maxCost
        }.ifEmpty { CardLibrary.spells.filter { it.manaValue <= maxCost } }

        val targetSize = SOURCE_COUNT + 12 + minOf(tier, 6)
        var guard = 0
        while (deck.size < targetSize && pool.isNotEmpty() && guard++ < 200) {
            val pick = rng.weighted(pool) { rarityWeight(it.rarity, tier) } ?: break
            // Hoechstens drei Kopien derselben Karte, damit Decks nicht entarten.
            if (deck.count { it.id == pick.id } >= 3) continue
            deck += pick
        }

        val life = def.baseLife + tier * 5 + when (def.kind) {
            EncounterKind.NORMAL -> 0
            EncounterKind.ELITE -> 2
            EncounterKind.BOSS -> 4
        }

        val startingSources = when {
            tier >= 8 -> 3
            tier >= 5 -> 2
            tier >= 3 -> 1
            else -> 0
        } + if (def.kind == EncounterKind.BOSS && tier >= 2) 1 else 0

        val skill = when {
            tier >= 4 && def.skill == BrainSkill.EINFACH -> BrainSkill.NORMAL
            // Der erste Boss trifft ein Deck, das kaum ueber den Start hinaus
            // gewachsen ist - volle SCHLAU-Staerke waere hier unfair frueh.
            tier <= 1 && def.kind == EncounterKind.BOSS && def.skill == BrainSkill.SCHLAU -> BrainSkill.NORMAL
            else -> def.skill
        }
        val name = if (tier >= 5) "${def.name} (Stufe $tier)" else def.name

        return EnemyBuild(
            def = def,
            name = name,
            life = life,
            deck = deck,
            skill = skill,
            startingSources = startingSources,
            tier = tier,
        )
    }

    private fun rarityWeight(rarity: Rarity, tier: Int): Int = when (rarity) {
        Rarity.HAEUFIG -> 60
        Rarity.SELTEN -> 20 + tier * 4
        Rarity.LEGENDAER -> (tier - 1).coerceAtLeast(0) * 4
    }
}
