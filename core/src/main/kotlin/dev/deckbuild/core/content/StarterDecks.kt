package dev.deckbuild.core.content

import dev.deckbuild.core.model.Aspect

/**
 * Startdecks ("Pfade"). Jeder Pfad legt fest, womit ein Lauf beginnt und
 * welche Aspekte die Belohnungen bevorzugt anbieten.
 */
data class StarterDeck(
    val id: String,
    val name: String,
    val flavor: String,
    val aspect: Aspect,
    /** Kartenzaehlung; Quellen werden separat ergaenzt. */
    val cards: List<String>,
    val sources: Int = 8,
    val startingConsumables: List<String> = emptyList(),
    val startingLife: Int = 40,
    /** false = muss ueber die Meta-Progression freigeschaltet werden. */
    val startsUnlocked: Boolean = true,
    val unlockHint: String = "",
)

object StarterDecks {

    val all: List<StarterDeck> = listOf(
        StarterDeck(
            id = "pfad_glut",
            name = "Pfad der Glut",
            flavor = "Schnell, laut, kurzlebig. Beende Kaempfe, bevor sie beginnen.",
            aspect = Aspect.GLUT,
            cards = listOf(
                "glut_funkenlaeufer", "glut_funkenlaeufer", "glut_funkenlaeufer",
                "glut_aschekrieger", "glut_aschekrieger", "glut_aschekrieger",
                "glut_sengender_stoss", "glut_sengender_stoss",
                "glut_brandopfer", "glut_brandopfer",
                "neutral_pluendertrupp",
                "glut_feuerregen",
            ),
            startingConsumables = listOf("kon_flammenphiole"),
        ),
        StarterDeck(
            id = "pfad_hain",
            name = "Pfad des Hains",
            flavor = "Waechst langsam, endet gross. Ressourcen schlagen Tempo.",
            aspect = Aspect.HAIN,
            cards = listOf(
                "hain_wurzelaeltester", "hain_wurzelaeltester",
                "hain_dickichtwolf", "hain_dickichtwolf", "hain_dickichtwolf",
                "hain_baumhueter", "hain_baumhueter",
                "hain_rankengriff",
                "hain_lebensfluss", "hain_lebensfluss",
                "hain_urhornvieh",
                "neutral_pluendertrupp",
            ),
            startingConsumables = listOf("kon_heiltrank"),
        ),
        StarterDeck(
            id = "pfad_flut",
            name = "Pfad der Flut",
            flavor = "Karten statt Kreaturen. Wer mehr sieht, gewinnt spaeter.",
            aspect = Aspect.FLUT,
            cards = listOf(
                "flut_nebelkundschafter", "flut_nebelkundschafter", "flut_nebelkundschafter",
                "flut_tiefenwaechter", "flut_tiefenwaechter",
                "flut_wellenreiter", "flut_wellenreiter",
                "flut_erkenntnis", "flut_erkenntnis",
                "flut_spiegelrochen", "flut_spiegelrochen",
                "neutral_wachkonstrukt",
                "neutral_pluendertrupp",
            ),
            startingConsumables = listOf("kon_schriftrolle"),
        ),
        StarterDeck(
            id = "pfad_asche",
            name = "Pfad der Asche",
            flavor = "Der Friedhof ist eine zweite Hand.",
            aspect = Aspect.ASCHE,
            cards = listOf(
                "asche_grabkriecher", "asche_grabkriecher", "asche_grabkriecher",
                "asche_seelenzehrer", "asche_seelenzehrer",
                "asche_todeskuss", "asche_todeskuss",
                "asche_knochensammler",
                "asche_blutpakt",
                "asche_wiedergaenger",
                "asche_gierschlund",
                "neutral_wachkonstrukt",
            ),
            startingConsumables = listOf("kon_totenmuenze"),
            startsUnlocked = false,
            unlockHint = "Besiege den Aschefuersten.",
        ),
        StarterDeck(
            id = "pfad_licht",
            name = "Pfad des Lichts",
            flavor = "Ueberleben ist auch eine Siegbedingung.",
            aspect = Aspect.LICHT,
            cards = listOf(
                "licht_schildwache", "licht_schildwache", "licht_schildwache",
                "licht_glaubensbote", "licht_glaubensbote",
                "licht_aufgebot", "licht_aufgebot",
                "licht_heilige_flamme",
                "licht_schutzsiegel",
                "licht_bannspruch",
                "licht_hochmeister",
                "neutral_wachkonstrukt",
            ),
            startingConsumables = listOf("kon_heiltrank"),
            startingLife = 46,
            startsUnlocked = false,
            unlockHint = "Erreiche Stufe 12 in einem Lauf.",
        ),
    )

    fun find(id: String): StarterDeck? = all.firstOrNull { it.id == id }

    fun require(id: String): StarterDeck = find(id) ?: error("Unbekannter Pfad: $id")

    /** Kartenliste inklusive der passenden Quellen. */
    fun deckList(deck: StarterDeck): List<String> {
        val source = CardLibrary.QUELLEN.getValue(deck.aspect).id
        return deck.cards + List(deck.sources) { source }
    }
}
