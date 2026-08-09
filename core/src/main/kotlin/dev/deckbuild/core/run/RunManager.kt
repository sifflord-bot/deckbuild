package dev.deckbuild.core.run

import dev.deckbuild.core.content.CardLibrary
import dev.deckbuild.core.content.ConsumableLibrary
import dev.deckbuild.core.content.Enemies
import dev.deckbuild.core.content.EnemyBuild
import dev.deckbuild.core.content.EncounterKind
import dev.deckbuild.core.content.StarterDecks
import dev.deckbuild.core.meta.MetaProgress
import dev.deckbuild.core.meta.RunSummary
import dev.deckbuild.core.model.Aspect
import dev.deckbuild.core.model.CardDef
import dev.deckbuild.core.model.CardType
import dev.deckbuild.core.model.ConsumableSlot
import dev.deckbuild.core.model.Rarity
import dev.deckbuild.core.model.Subtype
import dev.deckbuild.core.util.Rng

/**
 * Steuert den Ablauf eines Durchgangs: Stationen erzeugen, Belohnungen
 * verteilen, Haendler bestuecken.
 *
 * Alle Funktionen sind rein - sie nehmen einen [RunState] und liefern einen
 * neuen. So laesst sich der Lauf jederzeit speichern, und die Oberflaeche kann
 * ohne eigene Spiellogik darauf aufsetzen.
 */
object RunManager {

    const val MAX_POUCH_SLOTS = 3
    private const val CARD_CHOICES = 3
    private const val SKIP_REWARD_GOLD = 30

    // ------------------------------------------------------------ Lauf starten

    const val SPLASH_SOURCE_COUNT = 4
    private const val EXTRA_SOURCE_COUNT = 3

    /**
     * Obergrenze farbiger Aspekte je Lauf.
     *
     * Bei drei Farben reicht die Quellenzahl nicht mehr, um verlaesslich die
     * richtige Farbe zu ziehen; das Deck wuerde sich selbst blockieren.
     */
    const val MAX_ASPECTS = 2

    /** Aspekte, die dieser Lauf noch dazulernen kann. */
    fun learnableAspects(run: RunState): List<Aspect> =
        if (coloredAspects(run).size >= MAX_ASPECTS) {
            emptyList()
        } else {
            Aspect.entries.filter { it.isColored && it !in coloredAspects(run) }
        }

    fun startRun(pathId: String, seed: Long): RunState {
        val path = StarterDecks.require(pathId)
        val base = RunState(
            seed = seed,
            pathId = pathId,
            stage = 1,
            life = path.startingLife,
            maxLife = path.startingLife,
            deck = StarterDecks.deckList(path),
            aspects = setOf(path.aspect),
            pouch = path.startingConsumables.map { PouchEntry(it, ConsumableLibrary.require(it).maxCharges) },
        )
        return base.copy(nodes = rollNodes(base))
    }

    /**
     * Farbige Aspekte, deren Karten der Lauf tatsaechlich wirken kann.
     *
     * Aeltere Spielstaende kennen das Feld noch nicht; dann gilt der Aspekt des
     * gewaehlten Pfades.
     */
    fun coloredAspects(run: RunState): Set<Aspect> =
        run.aspects.ifEmpty { setOfNotNull(StarterDecks.find(run.pathId)?.aspect) }

    /** Der Grundaspekt des Pfades - er bleibt gegenueber einem Zweitaspekt bevorzugt. */
    private fun primaryAspect(run: RunState): Aspect =
        StarterDecks.find(run.pathId)?.aspect ?: coloredAspects(run).firstOrNull() ?: Aspect.NEUTRAL

    /** Kann diese Karte im Lauf ueberhaupt gewirkt werden? */
    fun isPlayable(run: RunState, card: CardDef): Boolean =
        card.aspect == Aspect.NEUTRAL || card.aspect in coloredAspects(run)

    /** Fuegt einen Aspekt hinzu und legt passende Quellen ins Deck. */
    fun addAspect(run: RunState, aspect: Aspect, sources: Int = SPLASH_SOURCE_COUNT): RunState {
        if (aspect == Aspect.NEUTRAL || aspect in coloredAspects(run)) return run
        if (coloredAspects(run).size >= MAX_ASPECTS) return run
        val sourceId = CardLibrary.QUELLEN.getValue(aspect).id
        return run.copy(
            aspects = coloredAspects(run) + aspect,
            deck = run.deck + List(sources) { sourceId },
        )
    }

    private fun rngFor(run: RunState, salt: Int = 0): Rng =
        Rng(run.seed * 1_000_003L + run.stage * 7919L + salt)

    // --------------------------------------------------------------- Stationen

    /**
     * Erzeugt die Auswahl der aktuellen Stufe. Jede fuenfte Stufe ist ein Boss
     * ohne Alternative; sonst gibt es zwei bis drei Wege.
     */
    fun rollNodes(run: RunState): List<NodeOption> {
        val rng = rngFor(run, salt = 1)
        if (run.isBossStage) {
            val boss = pickEnemy(EncounterKind.BOSS, run, rng)
            return listOf(
                NodeOption(
                    type = NodeType.BOSS,
                    label = boss.name,
                    detail = boss.flavor,
                    enemyId = boss.id,
                    goldReward = 90 + run.tier * 15,
                ),
            )
        }

        val options = mutableListOf<NodeOption>()
        val fight = pickEnemy(EncounterKind.NORMAL, run, rng)
        options += NodeOption(
            type = NodeType.KAMPF,
            label = fight.name,
            detail = fight.flavor,
            enemyId = fight.id,
            goldReward = 25 + run.tier * 6,
        )

        // Zweite Option: Elite (mehr Risiko, mehr Ertrag) oder eine Ruhestation.
        val eliteChance = 0.30 + run.tier * 0.05
        if (rng.nextDouble() < eliteChance) {
            val elite = pickEnemy(EncounterKind.ELITE, run, rng)
            options += NodeOption(
                type = NodeType.ELITE,
                label = elite.name,
                detail = elite.flavor,
                enemyId = elite.id,
                goldReward = 60 + run.tier * 12,
            )
        } else {
            val second = pickEnemy(EncounterKind.NORMAL, run, Rng(rng.nextInt(1, 99999).toLong()))
            options += NodeOption(
                type = NodeType.KAMPF,
                label = second.name,
                detail = second.flavor,
                enemyId = second.id,
                goldReward = 25 + run.tier * 6,
            )
        }

        // Dritte Option: Ruhe, Handel, Fund oder Begegnung.
        options += when (rng.nextInt(4)) {
            0 -> NodeOption(NodeType.RAST, "Rastplatz", "Heilen oder das Deck verschlanken.")
            1 -> NodeOption(NodeType.HAENDLER, "Haendler", "Karten, Gegenstaende und Dienste gegen Gold.")
            2 -> NodeOption(NodeType.SCHATZ, "Fundstelle", "Gold und ein Gegenstand.", goldReward = 40 + run.tier * 8)
            else -> {
                val event = rng.pick(Events.all)
                NodeOption(NodeType.EREIGNIS, event.title, "Eine Entscheidung ohne Kampf.", eventId = event.id)
            }
        }
        return options
    }

    private fun pickEnemy(kind: EncounterKind, run: RunState, rng: Rng) =
        rng.pick(Enemies.byKind(kind, run.tier))

    fun chooseNode(run: RunState, index: Int): RunState {
        val node = run.nodes.getOrNull(index) ?: return run
        val withNode = run.copy(activeNode = node)
        return when (node.type) {
            NodeType.HAENDLER -> withNode.copy(shop = rollShop(withNode))
            else -> withNode
        }
    }

    /** Baut den Gegner der aktuell gewaehlten Station. */
    fun buildEncounter(run: RunState): EnemyBuild? {
        val enemyId = run.activeNode?.enemyId ?: return null
        val def = Enemies.find(enemyId) ?: return null
        return Enemies.build(def, run.tier, rngFor(run, salt = 2))
    }

    /** Das Deck des Spielers als Kartendefinitionen. */
    fun playerDeck(run: RunState): List<CardDef> = run.deck.mapNotNull { CardLibrary.find(it) }

    fun pouchSlots(run: RunState): List<ConsumableSlot> =
        run.pouch.mapNotNull { entry ->
            ConsumableLibrary.find(entry.id)?.let { ConsumableSlot(it, entry.charges) }
        }

    // -------------------------------------------------------------- Ergebnisse

    /** Nach einem Sieg: Belohnungen wuerfeln und in den Lauf schreiben. */
    fun winEncounter(run: RunState, remainingLife: Int, meta: MetaProgress): RunState {
        val node = run.activeNode ?: return run
        val rng = rngFor(run, salt = 3)
        val isBoss = node.type == NodeType.BOSS
        val isElite = node.type == NodeType.ELITE

        val choices = rollCardChoices(run, meta, rng, boss = isBoss)
        val consumable = when {
            isBoss -> rng.pick(ConsumableLibrary.all).id
            isElite -> rng.pick(ConsumableLibrary.all).id
            rng.nextDouble() < 0.25 -> rng.pick(ConsumableLibrary.all).id
            else -> null
        }

        val unlocked = if (isBoss) Enemies.find(node.enemyId ?: "")?.unlocks.orEmpty() else emptyList()

        val rewards = RewardBundle(
            cardChoices = choices,
            gold = node.goldReward,
            consumableId = consumable,
            maxLifeBonus = if (isBoss) 6 else 0,
            unlockedCards = unlocked,
        )

        return run.copy(
            life = remainingLife.coerceAtLeast(1),
            victories = run.victories + 1,
            defeatedEnemies = run.defeatedEnemies + listOfNotNull(node.enemyId),
            pendingRewards = rewards,
        )
    }

    fun loseRun(run: RunState): RunState = run.copy(over = true, pendingRewards = null, activeNode = null)

    /**
     * Kartenauswahl nach einem Sieg. Bevorzugt den Aspekt des Pfades, laesst
     * aber Beimischungen zu - genau das macht Deckbau im Lauf interessant.
     */
    private fun rollCardChoices(
        run: RunState,
        meta: MetaProgress,
        rng: Rng,
        boss: Boolean,
    ): List<String> {
        val pathAspect = primaryAspect(run)
        // Nur Karten anbieten, fuer die im Deck auch Quellen liegen koennen.
        val pool = CardLibrary.spells.filter {
            meta.isCardUnlocked(it.id) && it.type != CardType.QUELLE && isPlayable(run, it)
        }
        if (pool.isEmpty()) return emptyList()

        val synergy = synergyTribes(run)
        val picks = mutableListOf<String>()
        var guard = 0
        while (picks.size < CARD_CHOICES && guard++ < 200) {
            val card = rng.weighted(pool) { weightFor(it, pathAspect, run.tier, boss, synergy) } ?: break
            if (card.id in picks) continue
            picks += card.id
        }
        return picks
    }

    /**
     * Staemme, die im Deck bereits Substanz haben.
     *
     * Ohne diese Kopplung wuerde ein wachsender Kartenpool die Belohnungen
     * beliebiger machen statt reichhaltiger: Drei zufaellige Karten aus
     * zweihundert treffen fast nie das, woran man gerade baut.
     */
    fun synergyTribes(run: RunState, threshold: Int = 3): Set<Subtype> =
        run.deck.mapNotNull { CardLibrary.find(it) }
            .flatMap { it.subtypes }
            .groupingBy { it }
            .eachCount()
            .filterValues { it >= threshold }
            .keys

    private fun weightFor(
        card: CardDef,
        pathAspect: Aspect,
        tier: Int,
        boss: Boolean,
        synergy: Set<Subtype> = emptySet(),
    ): Int {
        // Der Pool ist bereits auf spielbare Aspekte gefiltert; hier geht es nur
        // noch darum, den Grundaspekt gegenueber einem Zweitaspekt zu bevorzugen.
        val aspectFactor = when (card.aspect) {
            pathAspect -> 6
            Aspect.NEUTRAL -> 3
            else -> 4
        }
        val rarityFactor = when (card.rarity) {
            Rarity.HAEUFIG -> if (boss) 2 else 6
            Rarity.SELTEN -> 3 + tier / 2
            Rarity.LEGENDAER -> if (boss) 6 else 1 + tier / 3
        }
        // Sehr teure Karten erst anbieten, wenn der Lauf sie auch bezahlen kann.
        val costFactor = if (card.manaValue > 3 + tier) 0 else 1
        // Passt die Karte zu einem Stamm im Deck, wird sie deutlich haeufiger
        // angeboten - so verdichtet sich ein Deck ueber den Lauf hinweg.
        val synergyFactor = if (card.relatedSubtypes.any { it in synergy }) 3 else 1
        return aspectFactor * rarityFactor * costFactor * synergyFactor
    }

    /** Belohnung annehmen; [cardId] = null bedeutet ueberspringen gegen Gold. */
    fun takeReward(run: RunState, cardId: String?): RunState {
        val rewards = run.pendingRewards ?: return run
        var deck = run.deck
        var gold = run.gold + rewards.gold
        if (cardId != null && cardId in rewards.cardChoices) {
            deck = deck + cardId
        } else {
            gold += SKIP_REWARD_GOLD
        }
        val pouch = rewards.consumableId?.let { addConsumable(run.pouch, it) } ?: run.pouch
        val maxLife = run.maxLife + rewards.maxLifeBonus
        return run.copy(
            deck = deck,
            gold = gold,
            pouch = pouch,
            maxLife = maxLife,
            life = (run.life + rewards.maxLifeBonus).coerceAtMost(maxLife),
            pendingRewards = null,
        )
    }

    /** Naechste Stufe betreten und neue Stationen wuerfeln. */
    fun advanceStage(run: RunState): RunState {
        val next = run.copy(
            stage = run.stage + 1,
            activeNode = null,
            shop = emptyList(),
            pendingRewards = null,
        )
        return next.copy(nodes = rollNodes(next))
    }

    // ------------------------------------------------------------- Rastplatz

    /**
     * Heilung bis zum Maximum - aber niemals nach unten.
     *
     * Kaempfe koennen ueber das Maximum hinaus heilen (Zehrung, Heilrelikte),
     * und dieser Ueberschuss soll erhalten bleiben. Ein hartes Kappen auf
     * [RunState.maxLife] wuerde einen Rastplatz sonst zur Strafe machen.
     */
    private fun healedLife(run: RunState, amount: Int): Int =
        (run.life + amount).coerceAtMost(maxOf(run.maxLife, run.life))

    fun restHeal(run: RunState): RunState =
        run.copy(life = healedLife(run, (run.maxLife * 0.35).toInt()))

    /** Ladungen aller Verbrauchsgegenstaende auffuellen. */
    fun restRefill(run: RunState): RunState =
        run.copy(
            pouch = run.pouch.map { entry ->
                val def = ConsumableLibrary.find(entry.id)
                if (def == null) entry else entry.copy(charges = def.maxCharges)
            },
        )

    fun removeCard(run: RunState, cardId: String): RunState {
        val index = run.deck.indexOf(cardId)
        if (index < 0) return run
        return run.copy(deck = run.deck.toMutableList().also { it.removeAt(index) })
    }

    // -------------------------------------------------------------- Haendler

    private fun rollShop(run: RunState): List<ShopOffer> {
        val rng = rngFor(run, salt = 4)
        val pathAspect = primaryAspect(run)
        val offers = mutableListOf<ShopOffer>()

        val synergy = synergyTribes(run)
        val cardPool = CardLibrary.spells.filter { it.type != CardType.QUELLE && isPlayable(run, it) }
        repeat(3) {
            val card = rng.weighted(cardPool) {
                weightFor(it, pathAspect, run.tier, boss = false, synergy = synergy)
            } ?: return@repeat
            if (offers.any { it.id == card.id }) return@repeat
            val price = 35 + card.manaValue * 12 + when (card.rarity) {
                Rarity.HAEUFIG -> 0
                Rarity.SELTEN -> 25
                Rarity.LEGENDAER -> 55
            }
            offers += ShopOffer(ShopKind.KARTE, card.id, price, card.name, card.rulesText.ifBlank { "Kreatur" })
        }

        repeat(2) {
            val item = rng.pick(ConsumableLibrary.all)
            if (offers.any { it.id == item.id }) return@repeat
            val price = 40 + when (item.rarity) {
                Rarity.HAEUFIG -> 0
                Rarity.SELTEN -> 25
                Rarity.LEGENDAER -> 50
            }
            offers += ShopOffer(ShopKind.GEGENSTAND, item.id, price, item.name, item.text)
        }

        // Quellen zu kaufen war bisher unmoeglich - damit liess sich ein
        // Zweitaspekt nie erschliessen und die Manabasis nie nachbessern.
        val quellenAspekt = rng.pick(coloredAspects(run).toList().ifEmpty { listOf(pathAspect) })
        offers += ShopOffer(
            ShopKind.QUELLE,
            quellenAspekt.name,
            30,
            "$EXTRA_SOURCE_COUNT Quellen (${quellenAspekt.label})",
            "Legt $EXTRA_SOURCE_COUNT ${CardLibrary.QUELLEN.getValue(quellenAspekt).name} in dein Deck.",
        )

        val lernbar = learnableAspects(run)
        if (lernbar.isNotEmpty()) {
            rng.pickOrNull(lernbar)?.let { neuerAspekt ->
                offers += ShopOffer(
                    ShopKind.ASPEKT,
                    neuerAspekt.name,
                    110,
                    "Zweiter Aspekt: ${neuerAspekt.label}",
                    "Oeffnet ${neuerAspekt.label} fuer kuenftige Belohnungen und legt " +
                        "$SPLASH_SOURCE_COUNT passende Quellen in dein Deck.",
                )
            }
        }

        offers += ShopOffer(
            ShopKind.ENTFERNEN,
            "entfernen",
            60,
            "Karte entfernen",
            "Streiche eine Karte dauerhaft aus deinem Deck.",
        )
        offers += ShopOffer(
            ShopKind.HEILUNG,
            "heilung",
            45,
            "Verband",
            "Heile 12 Leben.",
        )
        return offers
    }

    /** Kauf abwickeln. [cardIdToRemove] wird nur bei [ShopKind.ENTFERNEN] benoetigt. */
    fun buy(run: RunState, offer: ShopOffer, cardIdToRemove: String? = null): RunState {
        if (run.gold < offer.price) return run
        val paid = run.copy(gold = run.gold - offer.price, shop = run.shop - offer)
        return when (offer.kind) {
            ShopKind.KARTE -> paid.copy(deck = paid.deck + offer.id)
            ShopKind.GEGENSTAND -> paid.copy(pouch = addConsumable(paid.pouch, offer.id))
            ShopKind.HEILUNG -> paid.copy(life = healedLife(paid, 12))
            ShopKind.ENTFERNEN -> {
                if (cardIdToRemove == null) run else removeCard(paid, cardIdToRemove)
            }
            ShopKind.QUELLE -> {
                val aspect = aspectFromId(offer.id) ?: return run
                val sourceId = CardLibrary.QUELLEN.getValue(aspect).id
                paid.copy(deck = paid.deck + List(EXTRA_SOURCE_COUNT) { sourceId })
            }
            ShopKind.ASPEKT -> {
                val aspect = aspectFromId(offer.id) ?: return run
                addAspect(paid, aspect)
            }
        }
    }

    private fun aspectFromId(id: String): Aspect? =
        Aspect.entries.firstOrNull { it.name == id }?.takeIf { it.isColored }

    // ------------------------------------------------------------- Ereignisse

    /** Loest eine Textbegegnung auf und liefert Zustand plus Ergebnistext. */
    fun resolveEvent(run: RunState, eventId: String, choiceIndex: Int): Pair<RunState, String> {
        val rng = rngFor(run, salt = 5)
        return when (eventId) {
            "ev_schrein" -> if (choiceIndex == 0) {
                run.copy(life = healedLife(run, 10)) to "Du rastest am Schrein und heilst 10 Leben."
            } else {
                run.copy(gold = run.gold + 45) to "Du nimmst die Gaben an dich: 45 Gold."
            }

            "ev_pakt" -> if (choiceIndex == 0) {
                val rare = CardLibrary.spells.filter { it.rarity != Rarity.HAEUFIG }
                val card = rng.pickOrNull(rare)
                val newLife = (run.life - 8).coerceAtLeast(1)
                if (card == null) {
                    run.copy(life = newLife) to "Die Stimme verstummt. Du verlierst 8 Leben."
                } else {
                    run.copy(life = newLife, deck = run.deck + card.id) to
                        "Du zahlst 8 Leben und erhaeltst ${card.name}."
                }
            } else {
                run to "Du gehst weiter. Die Stimme laesst dich ziehen."
            }

            "ev_lehre" -> if (choiceIndex == 0) {
                val neuer = rng.pickOrNull(learnableAspects(run))
                if (neuer == null) {
                    // Wer schon zwei Aspekte fuehrt, hat hier nichts mehr zu lernen.
                    run.copy(gold = run.gold + 40) to
                        "Du beherrschst bereits mehrere Aspekte. Er gibt dir stattdessen 40 Gold."
                } else {
                    val gelernt = addAspect(run.copy(life = (run.life - 6).coerceAtLeast(1)), neuer)
                    gelernt to
                        "Du lernst den Aspekt ${neuer.label}. " +
                        "$SPLASH_SOURCE_COUNT Quellen wandern in dein Deck."
                }
            } else {
                run.copy(gold = run.gold + 40) to "Du bleibst bei deinem Weg: 40 Gold."
            }

            "ev_kampfplatz" -> if (choiceIndex == 0) {
                val item = rng.pick(ConsumableLibrary.all)
                run.copy(pouch = addConsumable(run.pouch, item.id)) to "Du findest: ${item.name}."
            } else {
                run.copy(
                    life = healedLife(run, 6),
                    gold = run.gold + 20,
                ) to "Du ehrst die Toten: 6 Leben und 20 Gold."
            }

            "ev_wanderer" -> if (choiceIndex == 0) {
                val weakest = run.deck
                    .mapNotNull { CardLibrary.find(it) }
                    .filter { it.type != CardType.QUELLE }
                    .minByOrNull { it.power + it.toughness + it.manaValue }
                if (weakest == null) {
                    run to "Der Wanderer findet nichts zu bemaengeln."
                } else {
                    removeCard(run, weakest.id) to "${weakest.name} verlaesst dein Deck."
                }
            } else {
                run.copy(gold = run.gold + 25) to "Du lehnst ab. Er gibt dir 25 Gold fuer deine Zeit."
            }

            else -> run to "Nichts passiert."
        }
    }

    /** Fundstelle: Gold plus ein Gegenstand. */
    fun openTreasure(run: RunState): Pair<RunState, String> {
        val rng = rngFor(run, salt = 6)
        val node = run.activeNode
        val item = rng.pick(ConsumableLibrary.all)
        val gold = node?.goldReward ?: 40
        return run.copy(
            gold = run.gold + gold,
            pouch = addConsumable(run.pouch, item.id),
        ) to "$gold Gold und ${item.name}."
    }

    // ------------------------------------------------------------------ Hilfen

    /**
     * Legt einen Gegenstand in den Beutel. Ist er voll, wird der Gegenstand mit
     * den wenigsten Ladungen ersetzt.
     */
    private fun addConsumable(pouch: List<PouchEntry>, id: String): List<PouchEntry> {
        val def = ConsumableLibrary.find(id) ?: return pouch
        val existing = pouch.indexOfFirst { it.id == id }
        if (existing >= 0) {
            val entry = pouch[existing]
            return pouch.toMutableList().also {
                it[existing] = entry.copy(charges = (entry.charges + 1).coerceAtMost(def.maxCharges))
            }
        }
        if (pouch.size < MAX_POUCH_SLOTS) return pouch + PouchEntry(id, def.maxCharges)
        val weakest = pouch.withIndex().minByOrNull { it.value.charges } ?: return pouch
        return pouch.toMutableList().also { it[weakest.index] = PouchEntry(id, def.maxCharges) }
    }

    /** Verbrauchte Ladungen aus einem Kampf in den Lauf uebernehmen. */
    fun syncPouch(run: RunState, slots: List<ConsumableSlot>): RunState =
        run.copy(pouch = slots.map { PouchEntry(it.def.id, it.charges) })

    fun summarize(run: RunState): RunSummary {
        val bossIds = run.defeatedEnemies.filter { id ->
            Enemies.find(id)?.kind == EncounterKind.BOSS
        }
        val starter = StarterDecks.find(run.pathId)?.let { StarterDecks.deckList(it) }.orEmpty()
        val collected = run.deck.toMutableList()
        starter.forEach { collected.remove(it) }
        return RunSummary(
            stageReached = run.stage,
            victories = run.victories,
            defeatedBosses = bossIds.distinct(),
            collectedCards = collected.distinct(),
            won = false,
        )
    }
}
