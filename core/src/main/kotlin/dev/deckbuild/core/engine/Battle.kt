package dev.deckbuild.core.engine

import dev.deckbuild.core.model.ActivatedAbility
import dev.deckbuild.core.model.Aspect
import dev.deckbuild.core.model.CardDef
import dev.deckbuild.core.model.CardType
import dev.deckbuild.core.model.Condition
import dev.deckbuild.core.model.ConsumableSlot
import dev.deckbuild.core.model.ControllerScope
import dev.deckbuild.core.model.Cost
import dev.deckbuild.core.model.Effect
import dev.deckbuild.core.model.Filter
import dev.deckbuild.core.model.Keyword
import dev.deckbuild.core.model.Selector
import dev.deckbuild.core.model.Side
import dev.deckbuild.core.model.TargetKind
import dev.deckbuild.core.model.TargetSpec
import dev.deckbuild.core.model.TriggerEvent
import dev.deckbuild.core.model.Value
import dev.deckbuild.core.util.Rng

/** Nachschlagen von Kartendefinitionen (z. B. fuer Spielsteine) ohne harte Abhaengigkeit zur Bibliothek. */
fun interface CardResolver {
    fun resolve(id: String): CardDef
}

/** Entscheidungen der Gegnerseite. Implementierung liegt im ai-Paket. */
interface OpponentBrain {
    /** Naechste Aktion in der Hauptphase, oder null zum Beenden der Phase. */
    fun nextMainPhaseAction(battle: Battle, side: Side): GameAction?

    fun chooseAttackers(battle: Battle, side: Side): List<Int>

    /** Zuordnung Blocker-Id -> Angreifer-Id. */
    fun chooseBlockers(battle: Battle, side: Side, attackers: List<Permanent>): Map<Int, Int>

    /** Antwort auf einen gegnerischen Zauber auf dem Stapel, oder null. */
    fun respondToSpell(battle: Battle, side: Side, item: StackItem): GameAction?
}

data class LogEntry(val text: String, val side: Side? = null, val important: Boolean = false)

/**
 * Ein einzelner Kampf gegen einen Gegner. Die Klasse haelt den kompletten
 * Regelablauf: Zugphasen, Stapel, Effektaufloesung und Kampf.
 *
 * Bewusste Vereinfachungen gegenueber dem grossen Vorbild, damit das Spiel auf
 * einem Telefon mit einer Hand bedienbar bleibt:
 *  - Quellen werden beim Bezahlen automatisch optimal getappt.
 *  - Ausgeloeste Faehigkeiten loesen sofort auf statt ueber den Stapel; nur
 *    Zauber durchlaufen den Stapel, damit Neutralisieren funktioniert.
 *  - Prioritaet wird nicht frei durchgereicht: Der Gegner erhaelt genau ein
 *    Antwortfenster pro gewirktem Spielerzauber, der Spieler darf im
 *    Blockschritt und in seinen eigenen Phasen Spontanzauber wirken.
 */
class Battle(
    ruleset: Ruleset,
    playerDeck: List<CardDef>,
    opponentDeck: List<CardDef>,
    playerLife: Int,
    opponentLife: Int,
    playerPouch: List<ConsumableSlot> = emptyList(),
    private val rng: Rng,
    private val brain: OpponentBrain,
    private val resolver: CardResolver,
    val opponentName: String = "Gegner",
    /** Essenzkristalle, mit denen der Gegner startet - Schwierigkeitsregler fuer hohe Stufen. */
    private val opponentStartingSources: Int = 0,
) {
    val state: GameState = GameState(
        ruleset = ruleset,
        player = PlayerState(Side.SPIELER, playerLife),
        opponent = PlayerState(Side.GEGNER, opponentLife),
    )

    val log: MutableList<LogEntry> = mutableListOf()

    var awaiting: Awaiting = Awaiting.SPIELER_AKTION
        private set

    /** Angreifer, auf die der Spieler gerade Blocker verteilen muss. */
    var pendingAttackers: List<Permanent> = emptyList()
        private set

    private var startingPlayerFirstTurn = true

    init {
        state.player.maxLife = playerLife
        state.opponent.maxLife = opponentLife
        state.player.pouch.addAll(playerPouch)
        fillLibrary(state.player, playerDeck)
        fillLibrary(state.opponent, opponentDeck)
    }

    // ------------------------------------------------------------ Vorbereitung

    private fun fillLibrary(playerState: PlayerState, deck: List<CardDef>) {
        deck.forEach { playerState.library += CardInstance(state.allocateInstanceId(), it) }
        rng.shuffle(playerState.library)
    }

    /** Startet den Kampf: Eroeffnungshaende ziehen und den ersten Zug beginnen. */
    fun start() {
        if (opponentStartingSources > 0) {
            val crystal = resolver.resolve(TOKEN_KRISTALL)
            repeat(opponentStartingSources) {
                state.battlefield += Permanent(
                    CardInstance(state.allocateInstanceId(), crystal),
                    Side.GEGNER,
                ).also { it.summoningSick = false }
            }
        }
        repeat(state.ruleset.startingHandSize) {
            drawCard(Side.SPIELER, silent = true)
            drawCard(Side.GEGNER, silent = true)
        }
        say("Kampf gegen $opponentName beginnt.", important = true)
        beginTurn(Side.SPIELER)
        awaiting = Awaiting.SPIELER_AKTION
    }

    /**
     * Wirft die Starthand zurueck und zieht eine um eine Karte kleinere neue.
     * Nur direkt nach [start] sinnvoll.
     */
    fun mulligan(): Boolean {
        val playerState = state.player
        if (state.turnNumber != 1 || playerState.hand.size <= 1) return false
        playerState.library.addAll(playerState.hand)
        playerState.hand.clear()
        rng.shuffle(playerState.library)
        repeat((state.ruleset.startingHandSize - 1).coerceAtLeast(1)) { drawCard(Side.SPIELER, silent = true) }
        say("Neue Hand gezogen.")
        return true
    }

    // -------------------------------------------------------------- Zugablauf

    private fun beginTurn(side: Side) {
        if (state.isOver) return
        state.activeSide = side
        val playerState = state.stateOf(side)

        state.phase = Phase.ENTTAPPEN
        for (permanent in state.permanentsOf(side)) {
            permanent.tapped = false
            permanent.summoningSick = false
        }
        playerState.sourcePlayedThisTurn = false
        playerState.spellsCastThisTurn = 0
        playerState.clearPool()

        state.phase = Phase.AUFZIEHEN
        fireTriggers(TriggerEvent.ZUG_BEGINN, side)
        if (state.isOver) return

        state.phase = Phase.ZIEHEN
        val skipDraw = startingPlayerFirstTurn && side == Side.SPIELER && state.turnNumber == 1
        if (skipDraw) startingPlayerFirstTurn = false else drawCard(side)
        if (state.isOver) return

        state.phase = Phase.HAUPT_1
        say("Zug ${state.turnNumber} - ${if (side == Side.SPIELER) "du" else opponentName}.", side)
    }

    private fun endTurn(side: Side) {
        if (state.isOver) return
        state.phase = Phase.ENDE
        fireTriggers(TriggerEvent.ZUG_ENDE, side)

        for (permanent in state.battlefield) {
            permanent.clearTurnState()
            permanent.clearCombatState()
        }
        state.stateOf(side).clearPool()
        enforceHandLimit(side)
        checkState()
        if (side == Side.GEGNER) state.turnNumber++
    }

    private fun enforceHandLimit(side: Side) {
        val playerState = state.stateOf(side)
        val limit = state.ruleset.handLimit
        while (playerState.hand.size > limit) {
            // Zuerst die teuerste nicht bezahlbare Karte abwerfen, sonst die teuerste.
            val candidate = playerState.hand
                .sortedWith(compareByDescending<CardInstance> { !state.canPay(side, it.def.cost) }
                    .thenByDescending { it.def.manaValue })
                .first()
            playerState.hand.remove(candidate)
            playerState.graveyard += candidate
            say("${nameOf(side)} wirft ${candidate.def.name} ab (Handlimit).", side)
        }
    }

    // ------------------------------------------------------------ Spieleraktion

    fun perform(action: GameAction): ActionResult {
        if (state.isOver) return ActionResult.fail("Der Kampf ist bereits entschieden.")

        return when (action) {
            is GameAction.BlockerDeklarieren -> {
                if (awaiting != Awaiting.SPIELER_BLOCK) {
                    ActionResult.fail("Gerade sind keine Blocker zu verteilen.")
                } else {
                    submitBlockers(action.assignment)
                }
            }

            is GameAction.GegenstandNutzen -> useConsumable(Side.SPIELER, action.slotIndex, action.targets)

            is GameAction.KarteSpielen -> {
                val card = state.player.hand.firstOrNull { it.instanceId == action.instanceId }
                    ?: return ActionResult.fail("Karte nicht auf der Hand.")
                if (awaiting == Awaiting.SPIELER_BLOCK && card.def.type != CardType.SPONTAN) {
                    ActionResult.fail("Im Blockschritt sind nur Spontanzauber moeglich.")
                } else {
                    castCard(Side.SPIELER, action.instanceId, action.targets)
                }
            }

            else -> {
                if (awaiting != Awaiting.SPIELER_AKTION) {
                    return ActionResult.fail("Du bist gerade nicht am Zug.")
                }
                when (action) {
                    is GameAction.QuelleSpielen -> playSource(Side.SPIELER, action.instanceId, faceDown = false)
                    is GameAction.VerdecktLegen -> playSource(Side.SPIELER, action.instanceId, faceDown = true)
                    is GameAction.FaehigkeitAktivieren ->
                        activateAbility(Side.SPIELER, action.permanentId, action.abilityIndex, action.targets)
                    GameAction.ZumKampf -> goToCombat()
                    is GameAction.AngreiferDeklarieren -> playerAttacks(action.attackerIds)
                    GameAction.ZugBeenden -> {
                        endTurn(Side.SPIELER)
                        runOpponentTurn()
                        ActionResult.OK
                    }
                    else -> ActionResult.fail("Aktion hier nicht moeglich.")
                }
            }
        }
    }

    private fun goToCombat(): ActionResult {
        if (state.phase != Phase.HAUPT_1) return ActionResult.fail("Kampf ist nur aus der ersten Hauptphase erreichbar.")
        state.phase = Phase.ANGRIFF
        return ActionResult.OK
    }

    private fun playerAttacks(ids: List<Int>): ActionResult {
        if (state.phase != Phase.ANGRIFF) return ActionResult.fail("Nicht im Angriffsschritt.")
        val result = declareAttackers(Side.SPIELER, ids)
        if (!result.ok) return result

        if (state.permanentsOf(Side.SPIELER).none { it.attacking }) {
            state.phase = Phase.HAUPT_2
            return ActionResult.OK
        }

        state.phase = Phase.BLOCK
        val attackers = state.permanentsOf(Side.SPIELER).filter { it.attacking }
        val assignment = brain.chooseBlockers(this, Side.GEGNER, attackers)
        applyBlockers(Side.GEGNER, assignment)
        resolveCombat()
        if (!state.isOver) state.phase = Phase.HAUPT_2
        return ActionResult.OK
    }

    private fun submitBlockers(assignment: Map<Int, Int>): ActionResult {
        applyBlockers(Side.SPIELER, assignment)
        resolveCombat()
        pendingAttackers = emptyList()
        if (state.isOver) {
            awaiting = Awaiting.ENDE
            return ActionResult.OK
        }
        resumeOpponentTurnAfterCombat()
        return ActionResult.OK
    }

    // ------------------------------------------------------------- Gegnerzug

    private fun runOpponentTurn() {
        if (state.isOver) {
            awaiting = Awaiting.ENDE
            return
        }
        beginTurn(Side.GEGNER)
        if (state.isOver) {
            awaiting = Awaiting.ENDE
            return
        }
        runBrainMainPhase()
        if (state.isOver) {
            awaiting = Awaiting.ENDE
            return
        }

        state.phase = Phase.ANGRIFF
        val attackerIds = brain.chooseAttackers(this, Side.GEGNER)
        declareAttackers(Side.GEGNER, attackerIds)
        val attackers = state.permanentsOf(Side.GEGNER).filter { it.attacking }

        if (attackers.isEmpty()) {
            state.phase = Phase.HAUPT_2
            resumeOpponentTurnAfterCombat()
            return
        }

        state.phase = Phase.BLOCK
        pendingAttackers = attackers
        awaiting = Awaiting.SPIELER_BLOCK
    }

    private fun resumeOpponentTurnAfterCombat() {
        if (state.isOver) {
            awaiting = Awaiting.ENDE
            return
        }
        state.phase = Phase.HAUPT_2
        runBrainMainPhase()
        endTurn(Side.GEGNER)
        if (state.isOver) {
            awaiting = Awaiting.ENDE
            return
        }
        beginTurn(Side.SPIELER)
        awaiting = if (state.isOver) Awaiting.ENDE else Awaiting.SPIELER_AKTION
    }

    private fun runBrainMainPhase() {
        var guard = 0
        while (!state.isOver && guard++ < 40) {
            val action = brain.nextMainPhaseAction(this, Side.GEGNER) ?: return
            val result = applyBrainAction(action)
            if (!result.ok) return
        }
    }

    private fun applyBrainAction(action: GameAction): ActionResult = when (action) {
        is GameAction.QuelleSpielen -> playSource(Side.GEGNER, action.instanceId, faceDown = false)
        is GameAction.VerdecktLegen -> playSource(Side.GEGNER, action.instanceId, faceDown = true)
        is GameAction.KarteSpielen -> castCard(Side.GEGNER, action.instanceId, action.targets)
        is GameAction.FaehigkeitAktivieren ->
            activateAbility(Side.GEGNER, action.permanentId, action.abilityIndex, action.targets)
        else -> ActionResult.fail("Ungueltige KI-Aktion.")
    }

    // ------------------------------------------------------------- Ausspielen

    private fun playSource(side: Side, instanceId: Int, faceDown: Boolean): ActionResult {
        val playerState = state.stateOf(side)
        if (playerState.sourcePlayedThisTurn) return ActionResult.fail("In diesem Zug wurde bereits eine Quelle gelegt.")
        if (state.phase != Phase.HAUPT_1 && state.phase != Phase.HAUPT_2) {
            return ActionResult.fail("Quellen nur in einer Hauptphase.")
        }
        if (state.stack.isNotEmpty()) return ActionResult.fail("Der Stapel ist nicht leer.")
        if (state.sources(side).size >= state.ruleset.maxSourcesOnField) {
            return ActionResult.fail("Maximale Anzahl an Quellen erreicht.")
        }
        val card = playerState.hand.firstOrNull { it.instanceId == instanceId }
            ?: return ActionResult.fail("Karte nicht auf der Hand.")

        if (faceDown) {
            if (!state.ruleset.allowFaceDownSources) return ActionResult.fail("Verdecktes Legen ist deaktiviert.")
            card.faceDownSource = true
        } else if (card.def.type != CardType.QUELLE) {
            return ActionResult.fail("${card.def.name} ist keine Quelle.")
        }

        playerState.hand.remove(card)
        playerState.sourcePlayedThisTurn = true
        putOntoBattlefield(card, side, fromSpell = false)
        say(
            if (faceDown) "${nameOf(side)} legt eine Karte verdeckt als Quelle."
            else "${nameOf(side)} spielt ${card.def.name}.",
            side,
        )
        return ActionResult.OK
    }

    private fun castCard(side: Side, instanceId: Int, targets: List<TargetRef>): ActionResult {
        val playerState = state.stateOf(side)
        val card = playerState.hand.firstOrNull { it.instanceId == instanceId }
            ?: return ActionResult.fail("Karte nicht auf der Hand.")
        val def = card.def

        if (def.type == CardType.QUELLE) return ActionResult.fail("Quellen werden gelegt, nicht gewirkt.")

        val slowSpell = def.type != CardType.SPONTAN
        if (slowSpell) {
            if (side != state.activeSide) return ActionResult.fail("Nur im eigenen Zug spielbar.")
            if (state.phase != Phase.HAUPT_1 && state.phase != Phase.HAUPT_2) {
                return ActionResult.fail("${def.name} ist nur in einer Hauptphase spielbar.")
            }
            if (state.stack.isNotEmpty()) return ActionResult.fail("Der Stapel ist nicht leer.")
        }

        val validation = validateTargets(def.targets, targets, side)
        if (!validation.ok) return validation

        val payment = EssenceSolver.solve(state, side, def.cost)
            ?: return ActionResult.fail("Nicht genug Essenz fuer ${def.name}.")

        EssenceSolver.apply(state, side, payment)
        playerState.hand.remove(card)
        playerState.spellsCastThisTurn++

        val item = StackItem(
            stackId = state.allocateStackId(),
            kind = StackKind.ZAUBER,
            controller = side,
            sourceName = def.name,
            card = card,
            sourcePermanent = null,
            effect = def.onResolve,
            targets = targets,
        )
        state.stack.addLast(item)
        say("${nameOf(side)} wirkt ${def.name}.", side)
        fireTriggers(TriggerEvent.ZAUBER_GEWIRKT, side)

        offerResponse(side)
        resolveStack()
        return ActionResult.OK
    }

    /** Ein Antwortfenster fuer die jeweils andere Seite. Nur die KI nutzt es aktiv. */
    private fun offerResponse(caster: Side) {
        if (caster != Side.SPIELER) return
        val top = state.stack.lastOrNull() ?: return
        val response = brain.respondToSpell(this, Side.GEGNER, top) ?: return
        if (response is GameAction.KarteSpielen) {
            castResponse(Side.GEGNER, response.instanceId, response.targets)
        }
    }

    /** Wie [castCard], aber ohne erneutes Antwortfenster (verhindert Endlosketten). */
    private fun castResponse(side: Side, instanceId: Int, targets: List<TargetRef>): ActionResult {
        val playerState = state.stateOf(side)
        val card = playerState.hand.firstOrNull { it.instanceId == instanceId } ?: return ActionResult.fail("Karte fehlt.")
        if (card.def.type != CardType.SPONTAN) return ActionResult.fail("Nur Spontanzauber als Antwort.")
        val payment = EssenceSolver.solve(state, side, card.def.cost) ?: return ActionResult.fail("Zu teuer.")
        EssenceSolver.apply(state, side, payment)
        playerState.hand.remove(card)
        playerState.spellsCastThisTurn++
        state.stack.addLast(
            StackItem(
                stackId = state.allocateStackId(),
                kind = StackKind.ZAUBER,
                controller = side,
                sourceName = card.def.name,
                card = card,
                sourcePermanent = null,
                effect = card.def.onResolve,
                targets = targets,
            ),
        )
        say("${nameOf(side)} antwortet mit ${card.def.name}.", side)
        return ActionResult.OK
    }

    private fun resolveStack() {
        while (state.stack.isNotEmpty() && !state.isOver) {
            val item = state.stack.removeLast()
            val card = item.card
            if (item.countered) {
                if (card != null) state.stateOf(item.controller).graveyard += card
                say("${item.sourceName} wurde neutralisiert.", item.controller, important = true)
                continue
            }

            if (card != null && card.def.type.isPermanent) {
                putOntoBattlefield(card, item.controller, fromSpell = true)
            } else {
                item.effect?.let {
                    resolveEffect(it, EffectContext(item.controller, null, item.sourceName, item.targets))
                }
                if (card != null) {
                    val destination = state.stateOf(item.controller)
                    if (Keyword.VERBRAUCH in card.def.keywords) {
                        destination.exile += card
                        say("${card.def.name} wird verbraucht.", item.controller)
                    } else {
                        destination.graveyard += card
                    }
                }
            }
            checkState()
        }
    }

    // ---------------------------------------------------------- Schlachtfeld

    private fun putOntoBattlefield(card: CardInstance, side: Side, fromSpell: Boolean) {
        val permanent = Permanent(card, side)
        permanent.summoningSick = card.def.type == CardType.KREATUR &&
            Keyword.FLINK !in card.def.keywords
        if (card.def.type != CardType.KREATUR) permanent.summoningSick = false
        if (Keyword.SCHILD in card.def.keywords) permanent.hasShield = true
        state.battlefield += permanent

        if (fromSpell || card.def.type != CardType.QUELLE) {
            card.def.onResolve?.let { effect ->
                val targets = if (card.def.targets.isEmpty()) emptyList() else autoTargets(card.def.targets, side, permanent)
                resolveEffect(effect, EffectContext(side, permanent, card.def.name, targets))
            }
            fireEnterTriggers(permanent)
        }
        checkState()
    }

    private fun fireEnterTriggers(entering: Permanent) {
        for (trigger in entering.def.triggers.filter { it.event == TriggerEvent.BETRITT_SCHLACHTFELD }) {
            runTrigger(entering, trigger)
        }
        if (entering.def.type != CardType.KREATUR) return
        for (permanent in state.battlefield.toList()) {
            if (permanent === entering) continue
            for (trigger in permanent.def.triggers.filter { it.event == TriggerEvent.ANDERE_KREATUR_BETRITT }) {
                val filter = trigger.filter
                if (filter != null && !state.matches(entering, filter, permanent)) continue
                runTrigger(permanent, trigger)
            }
        }
    }

    private fun fireTriggers(event: TriggerEvent, side: Side) {
        for (permanent in state.battlefield.toList()) {
            if (permanent.controller != side) continue
            if (permanent !in state.battlefield) continue
            for (trigger in permanent.def.triggers.filter { it.event == event }) {
                runTrigger(permanent, trigger)
            }
        }
    }

    private fun runTrigger(source: Permanent, trigger: dev.deckbuild.core.model.Trigger) {
        val targets = if (trigger.targets.isEmpty()) emptyList() else autoTargets(trigger.targets, source.controller, source)
        if (trigger.targets.isNotEmpty() && targets.size < trigger.targets.count { !it.optional }) return
        if (trigger.text.isNotBlank()) say("${source.def.name}: ${trigger.text}", source.controller)
        resolveEffect(trigger.effect, EffectContext(source.controller, source, source.def.name, targets))
        checkState()
    }

    private fun activateAbility(
        side: Side,
        permanentId: Int,
        abilityIndex: Int,
        targets: List<TargetRef>,
    ): ActionResult {
        val permanent = state.findPermanent(permanentId) ?: return ActionResult.fail("Permanent nicht gefunden.")
        if (permanent.controller != side) return ActionResult.fail("Fremdes Permanent.")
        val ability = permanent.def.activated.getOrNull(abilityIndex)
            ?: return ActionResult.fail("Faehigkeit nicht vorhanden.")

        if (ability.sorcerySpeed &&
            (side != state.activeSide || (state.phase != Phase.HAUPT_1 && state.phase != Phase.HAUPT_2))
        ) {
            return ActionResult.fail("Nur in der eigenen Hauptphase aktivierbar.")
        }
        if (ability.tapSelf) {
            if (permanent.tapped) return ActionResult.fail("Bereits getappt.")
            if (permanent.summoningSick && permanent.def.type == CardType.KREATUR) {
                return ActionResult.fail("Kreatur ist noch beschwoerungskrank.")
            }
        }
        val validation = validateTargets(ability.targets, targets, side)
        if (!validation.ok) return validation

        val payment = EssenceSolver.solve(state, side, ability.cost)
            ?: return ActionResult.fail("Nicht genug Essenz.")
        EssenceSolver.apply(state, side, payment)
        if (ability.tapSelf) permanent.tapped = true
        if (ability.sacrificeSelf) moveToGraveyard(permanent, "geopfert")

        say("${nameOf(side)} nutzt ${permanent.def.name}: ${ability.text}", side)
        resolveEffect(ability.effect, EffectContext(side, permanent, permanent.def.name, targets))
        checkState()
        return ActionResult.OK
    }

    private fun useConsumable(side: Side, slotIndex: Int, targets: List<TargetRef>): ActionResult {
        val playerState = state.stateOf(side)
        val slot = playerState.pouch.getOrNull(slotIndex) ?: return ActionResult.fail("Kein Gegenstand in diesem Fach.")
        if (slot.isEmpty) return ActionResult.fail("${slot.def.name} hat keine Ladungen mehr.")
        val validation = validateTargets(slot.def.targets, targets, side)
        if (!validation.ok) return validation

        playerState.pouch[slotIndex] = slot.copy(charges = slot.charges - 1)
        say("${nameOf(side)} verbraucht ${slot.def.name}.", side, important = true)
        resolveEffect(slot.def.effect, EffectContext(side, null, slot.def.name, targets))
        checkState()
        return ActionResult.OK
    }

    // ------------------------------------------------------------------ Kampf

    private fun declareAttackers(side: Side, ids: List<Int>): ActionResult {
        for (id in ids) {
            val permanent = state.findPermanent(id) ?: continue
            if (permanent.controller != side) return ActionResult.fail("Fremde Kreatur kann nicht angreifen.")
            if (permanent.def.type != CardType.KREATUR) return ActionResult.fail("Nur Kreaturen greifen an.")
            if (permanent.tapped) return ActionResult.fail("${permanent.def.name} ist getappt.")
            if (permanent.summoningSick) return ActionResult.fail("${permanent.def.name} ist beschwoerungskrank.")
            if (state.has(permanent, Keyword.WAECHTER)) return ActionResult.fail("${permanent.def.name} kann nicht angreifen.")
        }
        for (id in ids) {
            val permanent = state.findPermanent(id) ?: continue
            permanent.attacking = true
            if (!state.has(permanent, Keyword.WACHT)) permanent.tapped = true
        }
        val attackers = state.permanentsOf(side).filter { it.attacking }
        if (attackers.isNotEmpty()) {
            say("${nameOf(side)} greift mit ${attackers.size} Kreatur(en) an.", side)
            for (attacker in attackers) {
                for (trigger in attacker.def.triggers.filter { it.event == TriggerEvent.GREIFT_AN }) {
                    runTrigger(attacker, trigger)
                }
            }
        }
        return ActionResult.OK
    }

    /** Prueft und setzt die Blockzuordnung. Ungueltige Eintraege werden verworfen. */
    private fun applyBlockers(defender: Side, assignment: Map<Int, Int>) {
        for ((blockerId, attackerId) in assignment) {
            val blocker = state.findPermanent(blockerId) ?: continue
            val attacker = state.findPermanent(attackerId) ?: continue
            if (blocker.controller != defender) continue
            if (blocker.def.type != CardType.KREATUR) continue
            if (blocker.tapped || blocker.attacking) continue
            if (!attacker.attacking || attacker.controller == defender) continue
            if (state.has(attacker, Keyword.UNBLOCKBAR)) continue
            if (state.has(attacker, Keyword.FLUG) &&
                !state.has(blocker, Keyword.FLUG) &&
                !state.has(blocker, Keyword.REICHWEITE)
            ) {
                continue
            }
            blocker.blocking = attackerId
            attacker.blockedBy += blockerId
        }
    }

    private fun resolveCombat() {
        state.phase = Phase.KAMPFSCHADEN
        dealCombatDamage(firstStrikeStep = true)
        checkState()
        if (!state.isOver) {
            dealCombatDamage(firstStrikeStep = false)
            checkState()
        }
        for (permanent in state.battlefield) permanent.clearCombatState()
    }

    private fun dealCombatDamage(firstStrikeStep: Boolean) {
        val attackers = state.battlefield.filter { it.attacking }
        if (attackers.isEmpty()) return
        val hasFirstStrikers = state.battlefield.any {
            (it.attacking || it.blocking != null) && state.has(it, Keyword.VORSTOSS)
        }
        if (firstStrikeStep && !hasFirstStrikers) return

        fun participates(permanent: Permanent): Boolean {
            val first = state.has(permanent, Keyword.VORSTOSS)
            return if (firstStrikeStep) first else !first
        }

        val lethal = mutableSetOf<Int>()

        for (attacker in attackers) {
            if (attacker !in state.battlefield) continue
            val blockers = attacker.blockedBy.mapNotNull { state.findPermanent(it) }
                .filter { it.blocking == attacker.instanceId }

            if (participates(attacker)) {
                val power = state.power(attacker)
                if (blockers.isEmpty()) {
                    if (attacker.blockedBy.isEmpty()) {
                        damagePlayer(attacker.controller.other, power, attacker)
                    }
                } else {
                    var remaining = power
                    val deathtouch = state.has(attacker, Keyword.GIFT)
                    for (blocker in blockers) {
                        if (remaining <= 0) break
                        val needed = if (deathtouch) 1 else state.remainingToughness(blocker).coerceAtLeast(1)
                        val assigned = minOf(remaining, needed)
                        damagePermanent(blocker, assigned, attacker, deathtouch, lethal)
                        remaining -= assigned
                    }
                    if (remaining > 0 && state.has(attacker, Keyword.TRAMPELN)) {
                        damagePlayer(attacker.controller.other, remaining, attacker)
                    }
                }
            }

            for (blocker in blockers) {
                if (!participates(blocker)) continue
                if (blocker.instanceId in lethal && firstStrikeStep) continue
                damagePermanent(
                    attacker,
                    state.power(blocker),
                    blocker,
                    state.has(blocker, Keyword.GIFT),
                    lethal,
                )
            }
        }

        for (id in lethal) {
            state.findPermanent(id)?.let { it.damage = maxOf(it.damage, state.toughness(it)) }
        }
    }

    // -------------------------------------------------------------- Schaden

    private fun damagePlayer(side: Side, amount: Int, source: Permanent?) {
        if (amount <= 0) return
        val playerState = state.stateOf(side)
        playerState.life -= amount
        say("${nameOf(side)} erleidet $amount Schaden (${playerState.life} Leben).", side)
        source?.let { attacker ->
            if (state.has(attacker, Keyword.ZEHRUNG)) {
                val controllerState = state.stateOf(attacker.controller)
                controllerState.life += amount
                say("${nameOf(attacker.controller)} zehrt $amount Leben.", attacker.controller)
            }
            for (trigger in attacker.def.triggers.filter { it.event == TriggerEvent.FUEGT_KAMPFSCHADEN_ZU }) {
                runTrigger(attacker, trigger)
            }
        }
    }

    private fun damagePermanent(
        target: Permanent,
        amount: Int,
        source: Permanent?,
        deathtouch: Boolean,
        lethal: MutableSet<Int>,
    ) {
        if (amount <= 0) return
        if (target.hasShield) {
            target.hasShield = false
            say("${target.def.name} faengt den Schaden mit dem Schild ab.")
            return
        }
        target.damage += amount
        if (deathtouch) lethal += target.instanceId
        source?.let {
            if (state.has(it, Keyword.ZEHRUNG)) {
                state.stateOf(it.controller).life += amount
            }
        }
    }

    private fun moveToGraveyard(permanent: Permanent, reason: String) {
        if (permanent !in state.battlefield) return
        state.battlefield.remove(permanent)
        val owner = state.stateOf(permanent.controller)
        permanent.card.faceDownSource = false
        if (!permanent.def.isToken) owner.graveyard += permanent.card
        say("${permanent.def.name} wurde $reason.", permanent.controller)
        for (trigger in permanent.def.triggers.filter { it.event == TriggerEvent.STIRBT }) {
            runTrigger(permanent, trigger)
        }
    }

    /** Zustandsbasierte Aktionen: toedlicher Schaden und Lebenspunkte. */
    private fun checkState() {
        var changed = true
        var guard = 0
        while (changed && guard++ < 20) {
            changed = false
            for (permanent in state.battlefield.toList()) {
                if (permanent.def.type != CardType.KREATUR) continue
                val toughness = state.toughness(permanent)
                if (toughness <= 0 || permanent.damage >= toughness) {
                    moveToGraveyard(permanent, "zerstoert")
                    changed = true
                }
            }
        }
        if (state.winner == null) {
            if (state.player.life <= 0 && state.opponent.life <= 0) {
                state.winner = Side.GEGNER
            } else if (state.player.life <= 0) {
                state.winner = Side.GEGNER
            } else if (state.opponent.life <= 0) {
                state.winner = Side.SPIELER
            }
        }
        if (state.isOver) {
            awaiting = Awaiting.ENDE
            say(
                if (state.winner == Side.SPIELER) "Sieg!" else "Niederlage.",
                state.winner,
                important = true,
            )
        }
    }

    // ------------------------------------------------------------ Karten ziehen

    fun drawCard(side: Side, silent: Boolean = false) {
        val playerState = state.stateOf(side)
        if (playerState.library.isEmpty()) {
            if (playerState.graveyard.isEmpty()) {
                playerState.fatigue++
                damagePlayer(side, playerState.fatigue, null)
                return
            }
            // Erschoepfung statt sofortiger Niederlage: Friedhof zurueckmischen kostet Leben.
            playerState.fatigue++
            playerState.library.addAll(playerState.graveyard)
            playerState.graveyard.clear()
            rng.shuffle(playerState.library)
            damagePlayer(side, playerState.fatigue, null)
            say("${nameOf(side)} mischt erschoepft den Friedhof zurueck.", side)
            if (playerState.library.isEmpty()) return
        }
        val card = playerState.library.removeAt(playerState.library.lastIndex)
        playerState.hand += card
        if (!silent) say("${nameOf(side)} zieht eine Karte.", side)
    }

    // ------------------------------------------------------------- Zielpruefung

    private fun validateTargets(specs: List<TargetSpec>, targets: List<TargetRef>, side: Side): ActionResult {
        val required = specs.count { !it.optional }
        if (targets.size < required) return ActionResult.fail("Es fehlen Ziele.")
        for ((index, spec) in specs.withIndex()) {
            val target = targets.getOrNull(index) ?: continue
            if (target is TargetRef.None) {
                if (!spec.optional) return ActionResult.fail("Ziel erforderlich.")
                continue
            }
            if (!isLegalTarget(spec, target, side)) return ActionResult.fail("Ungueltiges Ziel.")
        }
        return ActionResult.OK
    }

    fun isLegalTarget(spec: TargetSpec, target: TargetRef, side: Side): Boolean = when (target) {
        is TargetRef.PlayerTarget -> spec.kind == TargetKind.SPIELER || spec.kind == TargetKind.BELIEBIG
        is TargetRef.StackTarget ->
            spec.kind == TargetKind.ZAUBER_AUF_STAPEL && state.stack.any { it.stackId == target.stackId }
        is TargetRef.PermanentTarget -> {
            val permanent = state.findPermanent(target.instanceId)
            when {
                permanent == null -> false
                spec.kind == TargetKind.KREATUR && permanent.def.type != CardType.KREATUR -> false
                spec.kind == TargetKind.SPIELER -> false
                spec.kind == TargetKind.ZAUBER_AUF_STAPEL -> false
                else -> matchesFromViewpoint(permanent, spec.filter, side)
            }
        }
        TargetRef.None -> spec.optional
    }

    /** Filterpruefung aus Sicht einer Seite statt eines Quell-Permanenten. */
    private fun matchesFromViewpoint(
        permanent: Permanent,
        filter: Filter,
        side: Side,
    ): Boolean {
        val scopeOk = when (filter.controller) {
            ControllerScope.EIGENE -> permanent.controller == side
            ControllerScope.GEGNERISCHE -> permanent.controller != side
            ControllerScope.BELIEBIGE -> true
        }
        if (!scopeOk) return false
        return state.matches(permanent, filter.copy(controller = ControllerScope.BELIEBIGE), null)
    }

    /** Legale Ziele fuer eine Zielvorgabe - vom UI fuer die Zielauswahl genutzt. */
    fun legalTargets(spec: TargetSpec, side: Side): List<TargetRef> {
        val result = mutableListOf<TargetRef>()
        when (spec.kind) {
            TargetKind.SPIELER -> {
                result += TargetRef.PlayerTarget(Side.SPIELER)
                result += TargetRef.PlayerTarget(Side.GEGNER)
            }
            TargetKind.ZAUBER_AUF_STAPEL -> state.stack.forEach { result += TargetRef.StackTarget(it.stackId) }
            TargetKind.BELIEBIG -> {
                result += TargetRef.PlayerTarget(Side.SPIELER)
                result += TargetRef.PlayerTarget(Side.GEGNER)
                state.battlefield.filter { matchesFromViewpoint(it, spec.filter, side) }
                    .forEach { result += TargetRef.PermanentTarget(it.instanceId) }
            }
            TargetKind.KREATUR, TargetKind.PERMANENT -> {
                state.battlefield
                    .filter { spec.kind != TargetKind.KREATUR || it.def.type == CardType.KREATUR }
                    .filter { matchesFromViewpoint(it, spec.filter, side) }
                    .forEach { result += TargetRef.PermanentTarget(it.instanceId) }
            }
        }
        return result.filter { isLegalTarget(spec, it, side) }
    }

    /** Automatische Zielwahl fuer Ausloeser und KI. */
    fun autoTargets(specs: List<TargetSpec>, side: Side, source: Permanent?): List<TargetRef> =
        specs.map { spec ->
            val options = legalTargets(spec, side)
            if (options.isEmpty()) {
                TargetRef.None
            } else {
                options.maxByOrNull { scoreTarget(spec, it, side, source) } ?: TargetRef.None
            }
        }

    private fun scoreTarget(spec: TargetSpec, target: TargetRef, side: Side, source: Permanent?): Int {
        val hostile = spec.filter.controller == ControllerScope.GEGNERISCHE ||
            spec.filter.controller == ControllerScope.BELIEBIGE
        return when (target) {
            is TargetRef.PermanentTarget -> {
                val permanent = state.findPermanent(target.instanceId) ?: return Int.MIN_VALUE
                if (permanent === source) return Int.MIN_VALUE + 1
                val value = state.power(permanent) * 2 + state.toughness(permanent) + permanent.def.manaValue
                if (permanent.controller == side) {
                    if (hostile) -value else value
                } else {
                    if (hostile) value else -value
                }
            }
            is TargetRef.PlayerTarget -> if (target.side == side) -50 else 20
            is TargetRef.StackTarget -> 10
            TargetRef.None -> Int.MIN_VALUE
        }
    }

    // ---------------------------------------------------------- Effektauflösung

    private class EffectContext(
        val controller: Side,
        val source: Permanent?,
        val sourceName: String,
        val targets: List<TargetRef>,
    )

    private fun resolveEffect(effect: Effect, ctx: EffectContext) {
        if (state.isOver) return
        when (effect) {
            is Effect.Kette -> effect.effects.forEach { resolveEffect(it, ctx) }

            is Effect.Wenn -> {
                if (evaluate(effect.condition, ctx)) {
                    resolveEffect(effect.dann, ctx)
                } else {
                    effect.sonst?.let { resolveEffect(it, ctx) }
                }
            }

            is Effect.Schaden -> {
                val amount = evaluate(effect.amount, ctx)
                val (permanents, players) = resolveSelector(effect.target, ctx)
                permanents.forEach { damagePermanent(it, amount, ctx.source, false, mutableSetOf()) }
                players.forEach { damagePlayer(it, amount, null) }
                if (permanents.isNotEmpty() || players.isNotEmpty()) {
                    say("${ctx.sourceName} fuegt $amount Schaden zu.", ctx.controller)
                }
            }

            is Effect.Heilung -> {
                val amount = evaluate(effect.amount, ctx)
                val (permanents, players) = resolveSelector(effect.target, ctx)
                players.forEach {
                    val playerState = state.stateOf(it)
                    playerState.life += amount
                    say("${nameOf(it)} heilt $amount Leben (${playerState.life}).", it)
                }
                permanents.forEach { it.damage = (it.damage - amount).coerceAtLeast(0) }
            }

            is Effect.Ziehen -> {
                val amount = evaluate(effect.amount, ctx)
                resolveSelector(effect.target, ctx).second.forEach { side ->
                    repeat(amount) { drawCard(side, silent = true) }
                    say("${nameOf(side)} zieht $amount Karte(n).", side)
                }
            }

            is Effect.Abwerfen -> {
                val amount = evaluate(effect.amount, ctx)
                resolveSelector(effect.target, ctx).second.forEach { side ->
                    val playerState = state.stateOf(side)
                    repeat(amount) {
                        if (playerState.hand.isEmpty()) return@repeat
                        val card = rng.pick(playerState.hand)
                        playerState.hand.remove(card)
                        playerState.graveyard += card
                    }
                    say("${nameOf(side)} wirft $amount Karte(n) ab.", side)
                }
            }

            is Effect.Zerstoeren ->
                resolveSelector(effect.target, ctx).first.forEach { moveToGraveyard(it, "zerstoert") }

            is Effect.Verbannen -> resolveSelector(effect.target, ctx).first.forEach { permanent ->
                state.battlefield.remove(permanent)
                if (!permanent.def.isToken) state.stateOf(permanent.controller).exile += permanent.card
                say("${permanent.def.name} wird verbannt.", permanent.controller)
            }

            is Effect.Zurueckgeben -> resolveSelector(effect.target, ctx).first.forEach { permanent ->
                state.battlefield.remove(permanent)
                permanent.card.faceDownSource = false
                if (!permanent.def.isToken) state.stateOf(permanent.controller).hand += permanent.card
                say("${permanent.def.name} kehrt auf die Hand zurueck.", permanent.controller)
            }

            is Effect.Opfern ->
                resolveSelector(effect.target, ctx).first.forEach { moveToGraveyard(it, "geopfert") }

            is Effect.Antappen -> resolveSelector(effect.target, ctx).first.forEach {
                it.tapped = !effect.untap
            }

            is Effect.Staerken -> {
                val (permanents, _) = resolveSelector(effect.target, ctx)
                for (permanent in permanents) {
                    if (effect.dauerhaft) {
                        permanent.counterPower += effect.power
                        permanent.counterToughness += effect.toughness
                        permanent.grantedKeywords += effect.keywords
                    } else {
                        permanent.tempPower += effect.power
                        permanent.tempToughness += effect.toughness
                        permanent.tempKeywords += effect.keywords
                    }
                    if (Keyword.SCHILD in effect.keywords) permanent.hasShield = true
                }
                if (permanents.isNotEmpty()) {
                    say("${ctx.sourceName} staerkt ${permanents.size} Kreatur(en).", ctx.controller)
                }
            }

            is Effect.Erschaffen -> {
                val count = evaluate(effect.count, ctx)
                val side = resolveSelector(effect.controller, ctx).second.firstOrNull() ?: ctx.controller
                val def = resolver.resolve(effect.tokenId)
                repeat(count) {
                    val instance = CardInstance(state.allocateInstanceId(), def)
                    putOntoBattlefield(instance, side, fromSpell = false)
                }
                say("${nameOf(side)} erschafft ${count}x ${def.name}.", side)
            }

            is Effect.Essenz -> {
                val side = resolveSelector(effect.controller, ctx).second.firstOrNull() ?: ctx.controller
                if (effect.dauerhaft) {
                    val def = resolver.resolve(TOKEN_KRISTALL)
                    repeat(effect.amount) {
                        putOntoBattlefield(CardInstance(state.allocateInstanceId(), def), side, fromSpell = false)
                    }
                    say("${nameOf(side)} gewinnt ${effect.amount} dauerhafte Essenzquelle(n).", side)
                } else {
                    state.stateOf(side).addEssence(Aspect.NEUTRAL, effect.amount)
                    say("${nameOf(side)} erhaelt ${effect.amount} Essenz.", side)
                }
            }

            is Effect.Neutralisieren -> {
                val ref = resolveTargetRef(effect.target, ctx)
                if (ref is TargetRef.StackTarget) {
                    state.stack.firstOrNull { it.stackId == ref.stackId }?.countered = true
                } else {
                    state.stack.lastOrNull { it.controller != ctx.controller }?.countered = true
                }
            }

            is Effect.Wiederbeleben -> {
                val side = resolveSelector(effect.controller, ctx).second.firstOrNull() ?: ctx.controller
                val playerState = state.stateOf(side)
                val candidate = playerState.graveyard
                    .filter { it.def.type == CardType.KREATUR && it.def.manaValue <= effect.maxCost }
                    .maxByOrNull { it.def.manaValue }
                if (candidate != null) {
                    playerState.graveyard.remove(candidate)
                    putOntoBattlefield(candidate, side, fromSpell = false)
                    say("${nameOf(side)} belebt ${candidate.def.name} wieder.", side, important = true)
                }
            }

            is Effect.Suchen -> {
                val side = resolveSelector(effect.controller, ctx).second.firstOrNull() ?: ctx.controller
                val playerState = state.stateOf(side)
                val index = playerState.library.indexOfLast { it.def.type in effect.types }
                if (index >= 0) {
                    val card = playerState.library.removeAt(index)
                    playerState.hand += card
                    rng.shuffle(playerState.library)
                    say("${nameOf(side)} sucht ${card.def.name} heraus.", side)
                }
            }
        }
        checkState()
    }

    private fun resolveTargetRef(selector: Selector, ctx: EffectContext): TargetRef = when (selector) {
        is Selector.Chosen -> ctx.targets.getOrNull(selector.index) ?: TargetRef.None
        Selector.Du -> TargetRef.PlayerTarget(ctx.controller)
        Selector.Feind -> TargetRef.PlayerTarget(ctx.controller.other)
        Selector.Selbst -> ctx.source?.let { TargetRef.PermanentTarget(it.instanceId) } ?: TargetRef.None
        else -> TargetRef.None
    }

    private fun resolveSelector(selector: Selector, ctx: EffectContext): Pair<List<Permanent>, List<Side>> =
        when (selector) {
            Selector.Selbst -> listOfNotNull(ctx.source).filter { it in state.battlefield } to emptyList()

            Selector.Du -> emptyList<Permanent>() to listOf(ctx.controller)

            Selector.Feind -> emptyList<Permanent>() to listOf(ctx.controller.other)

            is Selector.Chosen -> when (val ref = ctx.targets.getOrNull(selector.index)) {
                is TargetRef.PermanentTarget ->
                    listOfNotNull(state.findPermanent(ref.instanceId)) to emptyList()
                is TargetRef.PlayerTarget -> emptyList<Permanent>() to listOf(ref.side)
                else -> emptyList<Permanent>() to emptyList()
            }

            is Selector.Alle -> selectWithViewpoint(selector.filter, ctx) to emptyList()

            is Selector.Zufaellig -> {
                val pool = selectWithViewpoint(selector.filter, ctx)
                rng.sample(pool, selector.count) to emptyList()
            }
        }

    /**
     * Wertet einen Filter aus Sicht des Effekt-Beherrschers aus - auch dann,
     * wenn kein Quell-Permanent existiert (etwa bei Verbrauchsgegenstaenden).
     */
    private fun selectWithViewpoint(filter: Filter, ctx: EffectContext): List<Permanent> =
        state.battlefield.filter { permanent ->
            val scopeOk = when (filter.controller) {
                ControllerScope.EIGENE -> permanent.controller == ctx.controller
                ControllerScope.GEGNERISCHE -> permanent.controller != ctx.controller
                ControllerScope.BELIEBIGE -> true
            }
            if (!scopeOk) {
                false
            } else if (filter.excludeSelf && permanent === ctx.source) {
                false
            } else {
                state.matches(permanent, filter.copy(controller = ControllerScope.BELIEBIGE, excludeSelf = false), null)
            }
        }

    private fun evaluate(value: Value, ctx: EffectContext): Int = when (value) {
        is Value.Fixed -> value.amount
        is Value.Count -> selectWithViewpoint(value.filter, ctx).size
        Value.Friedhof -> state.stateOf(ctx.controller).graveyard.size
        Value.FehlendeLeben -> state.stateOf(ctx.controller).missingLife
    }

    private fun evaluate(condition: Condition, ctx: EffectContext): Boolean = when (condition) {
        is Condition.Mindestens -> evaluate(condition.value, ctx) >= condition.threshold
        is Condition.Hoechstens -> evaluate(condition.value, ctx) <= condition.threshold
        is Condition.Nicht -> !evaluate(condition.inner, ctx)
        Condition.GegnerUnterHalbenLeben -> {
            val enemy = state.stateOf(ctx.controller.other)
            enemy.life * 2 <= enemy.maxLife
        }
    }

    // ------------------------------------------------------------------ Hilfen

    private fun nameOf(side: Side): String = if (side == Side.SPIELER) "Du" else opponentName

    private fun say(text: String, side: Side? = null, important: Boolean = false) {
        log += LogEntry(text, side, important)
        if (log.size > 200) log.removeAt(0)
    }

    /** Kann [side] diese Karte gerade legal spielen? Wird von UI und KI genutzt. */
    fun canCast(side: Side, card: CardInstance): Boolean {
        val def = card.def
        if (def.type == CardType.QUELLE) {
            return !state.stateOf(side).sourcePlayedThisTurn &&
                (state.phase == Phase.HAUPT_1 || state.phase == Phase.HAUPT_2) &&
                side == state.activeSide
        }
        if (def.type != CardType.SPONTAN) {
            if (side != state.activeSide) return false
            if (state.phase != Phase.HAUPT_1 && state.phase != Phase.HAUPT_2) return false
        }
        if (def.targets.any { !it.optional } && legalTargets(def.targets.first(), side).isEmpty()) return false
        return state.canPay(side, def.cost)
    }

    fun canPlayFaceDownSource(side: Side): Boolean =
        state.ruleset.allowFaceDownSources &&
            !state.stateOf(side).sourcePlayedThisTurn &&
            side == state.activeSide &&
            (state.phase == Phase.HAUPT_1 || state.phase == Phase.HAUPT_2) &&
            state.sources(side).size < state.ruleset.maxSourcesOnField

    /** Kosten einer Karte inklusive der Frage, ob sie bezahlbar ist. */
    fun affordability(side: Side, cost: Cost): Boolean = state.canPay(side, cost)

    companion object {
        const val TOKEN_KRISTALL = "tok_kristall"
    }
}
