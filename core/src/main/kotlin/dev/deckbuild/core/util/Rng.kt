package dev.deckbuild.core.util

import kotlin.random.Random

/**
 * Deterministischer Zufall. Ein Lauf wird von genau einem Seed bestimmt, damit
 * sich Spielstaende reproduzierbar wiederherstellen und Tests stabil schreiben
 * lassen.
 */
class Rng(seed: Long) {
    var seed: Long = seed
        private set

    private var random = Random(seed)

    fun nextInt(bound: Int): Int = if (bound <= 0) 0 else random.nextInt(bound)

    fun nextInt(from: Int, until: Int): Int = if (until <= from) from else random.nextInt(from, until)

    fun nextDouble(): Double = random.nextDouble()

    fun nextBoolean(): Boolean = random.nextBoolean()

    fun <T> pick(items: List<T>): T = items[nextInt(items.size)]

    fun <T> pickOrNull(items: List<T>): T? = if (items.isEmpty()) null else items[nextInt(items.size)]

    fun <T> shuffled(items: List<T>): List<T> = items.shuffled(random)

    fun <T> shuffle(items: MutableList<T>) {
        val result = items.shuffled(random)
        items.clear()
        items.addAll(result)
    }

    /** Zieht [count] Elemente ohne Zuruecklegen. */
    fun <T> sample(items: List<T>, count: Int): List<T> = shuffled(items).take(count)

    /** Gewichtete Auswahl; Gewichte <= 0 werden ignoriert. */
    fun <T> weighted(items: List<T>, weight: (T) -> Int): T? {
        val usable = items.filter { weight(it) > 0 }
        if (usable.isEmpty()) return null
        val total = usable.sumOf { weight(it) }
        var roll = nextInt(total)
        for (item in usable) {
            roll -= weight(item)
            if (roll < 0) return item
        }
        return usable.last()
    }

    /** Abgespaltener Generator, damit Teilsysteme sich nicht gegenseitig stoeren. */
    fun fork(): Rng = Rng(random.nextLong())

    fun reseed(newSeed: Long) {
        seed = newSeed
        random = Random(newSeed)
    }
}
