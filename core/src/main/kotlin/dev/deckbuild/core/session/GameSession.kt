package dev.deckbuild.core.session

import dev.deckbuild.core.content.CardLibrary
import dev.deckbuild.core.engine.Awaiting
import dev.deckbuild.core.engine.Battle
import dev.deckbuild.core.engine.CardInstance
import dev.deckbuild.core.engine.GameAction
import dev.deckbuild.core.engine.Permanent
import dev.deckbuild.core.engine.Phase
import dev.deckbuild.core.engine.TargetRef
import dev.deckbuild.core.meta.MetaProgress
import dev.deckbuild.core.meta.MetaService
import dev.deckbuild.core.meta.SaveService
import dev.deckbuild.core.model.CardType
import dev.deckbuild.core.model.Keyword
import dev.deckbuild.core.model.Side
import dev.deckbuild.core.model.TargetSpec
import dev.deckbuild.core.run.EncounterFactory
import dev.deckbuild.core.run.NodeType
import dev.deckbuild.core.run.RunManager
import dev.deckbuild.core.run.RunState
import dev.deckbuild.core.run.ShopKind
import dev.deckbuild.core.run.ShopOffer

/** Welche Ansicht gerade sichtbar sein soll. */
sealed interface Screen {
    data object Home : Screen
    data object PfadWahl : Screen
    data object Karte : Screen
    data object Kampf : Screen
    data object Belohnung : Screen
    data object Rastplatz : Screen
    data object Haendler : Screen
    data class Begegnung(val eventId: String) : Screen
    data object Fundstelle : Screen
    data object Deck : Screen
    data object Kompendium : Screen
    data object LaufVorbei : Screen
}

/** Woher die laufende Zielauswahl stammt. */
sealed interface TargetingSource {
    data class Karte(val instanceId: Int) : TargetingSource
    data class Gegenstand(val slot: Int) : TargetingSource
    data class Faehigkeit(val permanentId: Int, val index: Int) : TargetingSource
}

data class TargetingState(
    val source: TargetingSource,
    val specs: List<TargetSpec>,
    val chosen: List<TargetRef> = emptyList(),
) {
    val currentSpec: TargetSpec? get() = specs.getOrNull(chosen.size)
    val complete: Boolean get() = chosen.size >= specs.size
}

/**
 * Die gesamte Ablauflogik der App: Bildschirmwechsel, Laufverwaltung,
 * Kampfsteuerung und Zielauswahl.
 *
 * Bewusst ohne Oberflaechen-Abhaengigkeit. Dadurch laesst sich der komplette
 * Spielfluss - vom Startbildschirm bis zum Laufende - in gewoehnlichen
 * Unit-Tests durchspielen, waehrend die Android-Schicht nur noch Zeichnen und
 * Ereignisweitergabe uebernimmt.
 *
 * [onChange] wird nach jeder Zustandsaenderung gerufen; die App nutzt das, um
 * eine Neuzeichnung auszuloesen.
 */
class GameSession(
    private val save: SaveService,
    private val seedSource: () -> Long = { System.currentTimeMillis() },
    private val onChange: () -> Unit = {},
) {
    var meta: MetaProgress = save.loadMeta()
        private set

    var run: RunState? = save.loadRun()
        private set

    var battle: Battle? = null
        private set

    var screen: Screen = Screen.Home
        private set

    var targeting: TargetingState? = null
        private set

    var message: String? = null
        private set

    /** Ergebnistext von Begegnung oder Fundstelle. */
    var flavorResult: String? = null
        private set

    var newUnlocks: List<String> = emptyList()
        private set

    var selectedAttackers: Set<Int> = emptySet()
        private set

    var blockAssignment: Map<Int, Int> = emptyMap()
        private set

    var selectedBlocker: Int? = null
        private set

    /** Die naechste angetippte Handkarte wird verdeckt als Quelle gelegt. */
    var faceDownMode: Boolean = false
        private set

    val hasRun: Boolean get() = run?.over == false

    private fun changed() = onChange()

    // ------------------------------------------------------------- Navigation

    fun go(target: Screen) {
        message = null
        screen = target
        changed()
    }

    fun dismissMessage() {
        message = null
        changed()
    }

    fun toggleFaceDownMode() {
        faceDownMode = !faceDownMode
        changed()
    }

    // ------------------------------------------------------------------ Lauf

    fun startRun(pathId: String) {
        run = RunManager.startRun(pathId, seedSource())
        meta = MetaService.startRun(meta)
        newUnlocks = emptyList()
        persist()
        go(Screen.Karte)
    }

    fun abandonRun() {
        run?.let { finishRun(it) }
        run = null
        battle = null
        save.clearRun()
        go(Screen.Home)
    }

    fun chooseNode(index: Int) {
        val current = run ?: return
        val updated = RunManager.chooseNode(current, index)
        run = updated
        persist()

        when (updated.activeNode?.type) {
            NodeType.KAMPF, NodeType.ELITE, NodeType.BOSS -> beginBattle()
            NodeType.RAST -> go(Screen.Rastplatz)
            NodeType.HAENDLER -> go(Screen.Haendler)
            NodeType.SCHATZ -> {
                val (next, text) = RunManager.openTreasure(updated)
                run = next
                flavorResult = text
                persist()
                go(Screen.Fundstelle)
            }
            NodeType.EREIGNIS -> {
                flavorResult = null
                go(Screen.Begegnung(updated.activeNode?.eventId.orEmpty()))
            }
            null -> Unit
        }
    }

    fun continueAfterNode() {
        val current = run ?: return
        run = RunManager.advanceStage(current)
        flavorResult = null
        persist()
        go(Screen.Karte)
    }

    // ----------------------------------------------------------------- Kampf

    private fun beginBattle() {
        val current = run ?: return
        val enemy = RunManager.buildEncounter(current) ?: return
        battle = EncounterFactory.create(current, enemy)
        resetCombatSelection()
        go(Screen.Kampf)
    }

    private fun resetCombatSelection() {
        selectedAttackers = emptySet()
        blockAssignment = emptyMap()
        selectedBlocker = null
        targeting = null
        faceDownMode = false
    }

    private fun perform(action: GameAction) {
        val current = battle ?: return
        val result = current.perform(action)
        if (!result.ok && result.message.isNotBlank()) message = result.message
        if (current.state.isOver) endBattle() else changed()
    }

    private fun endBattle() {
        val current = battle ?: return
        val currentRun = run ?: return
        val won = current.state.winner == Side.SPIELER
        battle = null

        if (won) {
            val synced = RunManager.syncPouch(currentRun, current.state.player.pouch)
            run = RunManager.winEncounter(synced, current.state.player.life, meta)
            persist()
            go(Screen.Belohnung)
        } else {
            val over = RunManager.loseRun(currentRun)
            run = over
            finishRun(over)
            go(Screen.LaufVorbei)
        }
    }

    private fun finishRun(state: RunState) {
        val (updated, unlocked) = MetaService.applyRun(meta, RunManager.summarize(state))
        meta = updated
        newUnlocks = unlocked
        save.saveMeta(updated)
        save.clearRun()
    }

    // -------------------------------------------------------- Kampfsteuerung

    fun tapHandCard(card: CardInstance) {
        val current = battle ?: return
        if (targeting != null) return

        if (faceDownMode) {
            faceDownMode = false
            perform(GameAction.VerdecktLegen(card.instanceId))
            return
        }
        if (card.def.type == CardType.QUELLE) {
            perform(GameAction.QuelleSpielen(card.instanceId))
            return
        }
        if (!current.canCast(Side.SPIELER, card)) {
            message = "${card.def.name} ist gerade nicht spielbar."
            changed()
            return
        }
        if (card.def.targets.isEmpty()) {
            perform(GameAction.KarteSpielen(card.instanceId))
        } else {
            targeting = TargetingState(TargetingSource.Karte(card.instanceId), card.def.targets)
            changed()
        }
    }

    fun useConsumable(slot: Int) {
        val current = battle ?: return
        val entry = current.state.player.pouch.getOrNull(slot) ?: return
        if (entry.isEmpty) {
            message = "${entry.def.name} hat keine Ladungen mehr."
            changed()
            return
        }
        if (entry.def.targets.isEmpty()) {
            perform(GameAction.GegenstandNutzen(slot))
        } else {
            targeting = TargetingState(TargetingSource.Gegenstand(slot), entry.def.targets)
            changed()
        }
    }

    fun activateAbility(permanent: Permanent, index: Int) {
        val ability = permanent.def.activated.getOrNull(index) ?: return
        if (ability.targets.isEmpty()) {
            perform(GameAction.FaehigkeitAktivieren(permanent.instanceId, index))
        } else {
            targeting = TargetingState(TargetingSource.Faehigkeit(permanent.instanceId, index), ability.targets)
            changed()
        }
    }

    fun cancelTargeting() {
        targeting = null
        changed()
    }

    fun isLegalTargetNow(ref: TargetRef): Boolean {
        val current = battle ?: return false
        val spec = targeting?.currentSpec ?: return false
        return current.isLegalTarget(spec, ref, Side.SPIELER)
    }

    private fun addTarget(ref: TargetRef) {
        val state = targeting ?: return
        if (!isLegalTargetNow(ref)) return
        val updated = state.copy(chosen = state.chosen + ref)
        if (!updated.complete) {
            targeting = updated
            changed()
            return
        }
        targeting = null
        when (val source = updated.source) {
            is TargetingSource.Karte -> perform(GameAction.KarteSpielen(source.instanceId, updated.chosen))
            is TargetingSource.Gegenstand -> perform(GameAction.GegenstandNutzen(source.slot, updated.chosen))
            is TargetingSource.Faehigkeit ->
                perform(GameAction.FaehigkeitAktivieren(source.permanentId, source.index, updated.chosen))
        }
    }

    fun tapPlayer(side: Side) {
        if (targeting != null) addTarget(TargetRef.PlayerTarget(side))
    }

    fun tapPermanent(permanent: Permanent) {
        val current = battle ?: return
        if (targeting != null) {
            addTarget(TargetRef.PermanentTarget(permanent.instanceId))
            return
        }
        when (current.awaiting) {
            Awaiting.SPIELER_BLOCK -> handleBlockTap(permanent)
            Awaiting.SPIELER_AKTION ->
                if (current.state.phase == Phase.ANGRIFF) {
                    handleAttackTap(permanent)
                } else if (permanent.controller == Side.SPIELER && permanent.def.activated.isNotEmpty()) {
                    activateAbility(permanent, 0)
                }
            Awaiting.ENDE -> Unit
        }
    }

    private fun handleAttackTap(permanent: Permanent) {
        val current = battle ?: return
        if (permanent.controller != Side.SPIELER) return
        if (permanent.def.type != CardType.KREATUR) return
        if (permanent.tapped || permanent.summoningSick) return
        if (current.state.has(permanent, Keyword.WAECHTER)) return

        selectedAttackers = if (permanent.instanceId in selectedAttackers) {
            selectedAttackers - permanent.instanceId
        } else {
            selectedAttackers + permanent.instanceId
        }
        changed()
    }

    private fun handleBlockTap(permanent: Permanent) {
        if (permanent.controller == Side.SPIELER) {
            if (permanent.def.type != CardType.KREATUR || permanent.tapped) return
            if (permanent.instanceId in blockAssignment) {
                // Erneutes Antippen loest eine bestehende Zuweisung wieder auf.
                blockAssignment = blockAssignment - permanent.instanceId
                selectedBlocker = null
            } else {
                selectedBlocker = if (selectedBlocker == permanent.instanceId) null else permanent.instanceId
            }
            changed()
            return
        }

        val blocker = selectedBlocker ?: return
        if (!permanent.attacking) return
        blockAssignment = blockAssignment + (blocker to permanent.instanceId)
        selectedBlocker = null
        changed()
    }

    fun toCombat() {
        selectedAttackers = emptySet()
        perform(GameAction.ZumKampf)
    }

    fun confirmAttack() {
        val attackers = selectedAttackers.toList()
        selectedAttackers = emptySet()
        perform(GameAction.AngreiferDeklarieren(attackers))
    }

    fun confirmBlocks() {
        val assignment = blockAssignment
        blockAssignment = emptyMap()
        selectedBlocker = null
        perform(GameAction.BlockerDeklarieren(assignment))
    }

    fun endTurn() {
        resetCombatSelection()
        perform(GameAction.ZugBeenden)
    }

    fun mulligan() {
        battle?.mulligan()
        changed()
    }

    // ------------------------------------------------------------ Belohnungen

    fun takeReward(cardId: String?) {
        val current = run ?: return
        val rewards = current.pendingRewards
        run = RunManager.takeReward(current, cardId)

        // Bosskarten sofort freischalten statt erst am Laufende - der Erfolg
        // soll unmittelbar sichtbar sein.
        val added = rewards?.unlockedCards.orEmpty().filterNot { meta.isCardUnlocked(it) }
        if (added.isNotEmpty()) {
            meta = meta.copy(unlockedCards = meta.unlockedCards + added)
            newUnlocks = added
            save.saveMeta(meta)
        }
        persist()
        continueAfterNode()
    }

    // -------------------------------------------------------------- Rastplatz

    fun restHeal() {
        val current = run ?: return
        run = RunManager.restRefill(RunManager.restHeal(current))
        persist()
        continueAfterNode()
    }

    fun restRemove(cardId: String) {
        val current = run ?: return
        run = RunManager.restRefill(RunManager.removeCard(current, cardId))
        persist()
        continueAfterNode()
    }

    // --------------------------------------------------------------- Haendler

    fun buy(offer: ShopOffer, cardIdToRemove: String? = null) {
        val current = run ?: return
        if (current.gold < offer.price) {
            message = "Nicht genug Gold."
            changed()
            return
        }
        if (offer.kind == ShopKind.ENTFERNEN && cardIdToRemove == null) return
        run = RunManager.buy(current, offer, cardIdToRemove)
        persist()
        changed()
    }

    // ------------------------------------------------------------- Begegnung

    fun resolveEvent(eventId: String, choiceIndex: Int) {
        val current = run ?: return
        val (next, text) = RunManager.resolveEvent(current, eventId, choiceIndex)
        run = next
        flavorResult = text
        persist()
        changed()
    }

    // ------------------------------------------------------------------ Hilfen

    fun cardName(id: String): String = CardLibrary.find(id)?.name ?: id

    fun persist() {
        val current = run
        if (current == null || current.over) save.clearRun() else save.saveRun(current)
        save.saveMeta(meta)
    }
}
