package dev.deckbuild.core.run

/** Eine Auswahlmoeglichkeit einer Begegnung. */
data class EventChoice(val label: String, val detail: String)

data class EventDef(
    val id: String,
    val title: String,
    val text: String,
    val choices: List<EventChoice>,
)

/** Textbegegnungen zwischen den Kaempfen - kurze Entscheidungen mit Kosten. */
object Events {

    val all: List<EventDef> = listOf(
        EventDef(
            id = "ev_schrein",
            title = "Verlassener Schrein",
            text = "Zwischen umgestuerzten Saeulen glimmt noch ein Rest Essenz. " +
                "Jemand hat hier Opfergaben zurueckgelassen - und sie nie abgeholt.",
            choices = listOf(
                EventChoice("Am Schrein rasten", "Heile 10 Leben."),
                EventChoice("Die Gaben mitnehmen", "Erhalte 45 Gold."),
            ),
        ),
        EventDef(
            id = "ev_pakt",
            title = "Zwielichtiger Pakt",
            text = "Eine Stimme aus der Asche bietet dir Macht an. Der Preis ist, wie immer, " +
                "im Voraus zu zahlen.",
            choices = listOf(
                EventChoice("Den Pakt eingehen", "Verliere 8 Leben, erhalte eine seltene Karte."),
                EventChoice("Weitergehen", "Nichts passiert."),
            ),
        ),
        EventDef(
            id = "ev_kampfplatz",
            title = "Alter Kampfplatz",
            text = "Waffen, Ruestungen, Knochen. Wer hier gefallen ist, hat etwas hinterlassen.",
            choices = listOf(
                EventChoice("Durchsuchen", "Erhalte einen zufaelligen Verbrauchsgegenstand."),
                EventChoice("Die Toten ehren", "Heile 6 Leben und erhalte 20 Gold."),
            ),
        ),
        EventDef(
            id = "ev_wanderer",
            title = "Schweigsamer Wanderer",
            text = "Er sieht dein Deck an, nickt langsam und deutet auf eine Karte darin.",
            choices = listOf(
                EventChoice("Rat annehmen", "Entferne die schwaechste Karte aus deinem Deck."),
                EventChoice("Ablehnen", "Erhalte 25 Gold."),
            ),
        ),
    )

    fun find(id: String): EventDef? = all.firstOrNull { it.id == id }
}
