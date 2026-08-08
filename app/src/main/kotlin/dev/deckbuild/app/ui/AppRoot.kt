package dev.deckbuild.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import dev.deckbuild.app.GameController
import dev.deckbuild.core.session.Screen

/**
 * Einstiegspunkt der Oberflaeche. Statt einer Navigationsbibliothek genuegt
 * hier ein Zustandsfeld - das Spiel hat wenige, klar getrennte Ansichten und
 * kein Bedarf an tiefen Rueckwaertsstapeln.
 */
@Composable
fun AppRoot(controller: GameController) {
    DeckbuildTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxSize().background(Palette.Background)) {
                when (val screen = controller.screen) {
                    Screen.Home -> HomeScreen(controller)
                    Screen.PfadWahl -> PathScreen(controller)
                    Screen.Karte -> MapScreen(controller)
                    Screen.Kampf -> BattleScreen(controller)
                    Screen.Belohnung -> RewardScreen(controller)
                    Screen.Rastplatz -> RestScreen(controller)
                    Screen.Haendler -> ShopScreen(controller)
                    Screen.Fundstelle -> TreasureScreen(controller)
                    is Screen.Begegnung -> EventScreen(controller, screen.eventId)
                    Screen.Deck -> DeckScreen(controller) { controller.go(Screen.Karte) }
                    Screen.Kompendium -> CompendiumScreen(controller)
                    Screen.LaufVorbei -> RunOverScreen(controller)
                }
            }
        }

        // Die Zurueck-Geste darf einen laufenden Kampf nicht abbrechen.
        BackHandler(enabled = controller.screen != Screen.Home) {
            when (controller.screen) {
                Screen.Kampf -> Unit
                Screen.Belohnung -> Unit
                Screen.Deck -> controller.go(Screen.Karte)
                Screen.Karte -> controller.go(Screen.Home)
                else -> controller.go(if (controller.hasRun) Screen.Karte else Screen.Home)
            }
        }
    }
}
