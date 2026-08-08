package dev.deckbuild.app

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import dev.deckbuild.core.engine.Battle
import dev.deckbuild.core.engine.CardInstance
import dev.deckbuild.core.engine.Permanent
import dev.deckbuild.core.engine.TargetRef
import dev.deckbuild.core.meta.MetaProgress
import dev.deckbuild.core.meta.SaveService
import dev.deckbuild.core.model.Side
import dev.deckbuild.core.run.RunState
import dev.deckbuild.core.run.ShopOffer
import dev.deckbuild.core.session.GameSession
import dev.deckbuild.core.session.Screen
import dev.deckbuild.core.session.TargetingState

/**
 * Compose-Huelle um [GameSession].
 *
 * Die Sitzung enthaelt die gesamte Spiellogik und ist im core-Modul getestet;
 * diese Klasse macht sie lediglich beobachtbar. Statt jedes Feld einzeln in
 * einen Zustand zu verpacken, zaehlt ein Revisionswert nach jeder Aenderung
 * hoch. Jede Ansicht liest ihn einmal und wird dadurch neu gezeichnet - bei der
 * Groesse dieser Bildschirme ist das der einfachere und weniger fehleranfaellige
 * Weg als eine Spiegelung des kompletten Zustands.
 */
class GameController(save: SaveService) {

    private var revisionState by mutableIntStateOf(0)

    private val session = GameSession(save = save, onChange = { revisionState++ })

    /** Muss von jeder Ansicht gelesen werden, damit sie auf Aenderungen reagiert. */
    val revision: Int get() = revisionState

    // ---------------------------------------------------------------- Zustand

    val meta: MetaProgress get() = revisionState.let { session.meta }
    val run: RunState? get() = revisionState.let { session.run }
    val battle: Battle? get() = revisionState.let { session.battle }
    val screen: Screen get() = revisionState.let { session.screen }
    val targeting: TargetingState? get() = revisionState.let { session.targeting }
    val message: String? get() = revisionState.let { session.message }
    val flavorResult: String? get() = revisionState.let { session.flavorResult }
    val newUnlocks: List<String> get() = revisionState.let { session.newUnlocks }
    val selectedAttackers: Set<Int> get() = revisionState.let { session.selectedAttackers }
    val blockAssignment: Map<Int, Int> get() = revisionState.let { session.blockAssignment }
    val selectedBlocker: Int? get() = revisionState.let { session.selectedBlocker }
    val faceDownMode: Boolean get() = revisionState.let { session.faceDownMode }
    val hasRun: Boolean get() = revisionState.let { session.hasRun }

    // --------------------------------------------------------------- Aktionen

    fun go(target: Screen) = session.go(target)

    fun dismissMessage() = session.dismissMessage()

    fun toggleFaceDownMode() = session.toggleFaceDownMode()

    fun startRun(pathId: String) = session.startRun(pathId)

    fun abandonRun() = session.abandonRun()

    fun chooseNode(index: Int) = session.chooseNode(index)

    fun continueAfterNode() = session.continueAfterNode()

    fun tapHandCard(card: CardInstance) = session.tapHandCard(card)

    fun useConsumable(slot: Int) = session.useConsumable(slot)

    fun activateAbility(permanent: Permanent, index: Int) = session.activateAbility(permanent, index)

    fun cancelTargeting() = session.cancelTargeting()

    fun isLegalTargetNow(ref: TargetRef): Boolean = revisionState.let { session.isLegalTargetNow(ref) }

    fun tapPlayer(side: Side) = session.tapPlayer(side)

    fun tapPermanent(permanent: Permanent) = session.tapPermanent(permanent)

    fun toCombat() = session.toCombat()

    fun confirmAttack() = session.confirmAttack()

    fun confirmBlocks() = session.confirmBlocks()

    fun endTurn() = session.endTurn()

    fun mulligan() = session.mulligan()

    fun takeReward(cardId: String?) = session.takeReward(cardId)

    fun restHeal() = session.restHeal()

    fun restRemove(cardId: String) = session.restRemove(cardId)

    fun buy(offer: ShopOffer, cardIdToRemove: String? = null) = session.buy(offer, cardIdToRemove)

    fun resolveEvent(eventId: String, choiceIndex: Int) = session.resolveEvent(eventId, choiceIndex)

    fun cardName(id: String): String = session.cardName(id)

    fun persist() = session.persist()
}
