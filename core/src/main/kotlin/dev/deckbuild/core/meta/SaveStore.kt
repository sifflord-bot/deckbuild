package dev.deckbuild.core.meta

import dev.deckbuild.core.run.RunState
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Persistenz-Anbindung. Das core-Modul kennt kein Android, deshalb liefert die
 * App eine Implementierung, die einfach zwei Textdateien schreibt.
 */
interface SaveStore {
    fun readMeta(): String?

    fun writeMeta(json: String)

    fun readRun(): String?

    /** null loescht den gespeicherten Lauf. */
    fun writeRun(json: String?)
}

/** In-Memory-Variante fuer Tests und Vorschauen. */
class InMemorySaveStore : SaveStore {
    private var meta: String? = null
    private var run: String? = null

    override fun readMeta(): String? = meta

    override fun writeMeta(json: String) {
        meta = json
    }

    override fun readRun(): String? = run

    override fun writeRun(json: String?) {
        run = json
    }
}

/**
 * Laedt und speichert Meta-Fortschritt und laufenden Durchgang.
 *
 * Beschaedigte oder veraltete Spielstaende fuehren nicht zum Absturz, sondern
 * zum Startzustand - bei einem Spiel, das lokal auf einem Telefon laeuft, ist
 * das die freundlichere Variante.
 */
class SaveService(private val store: SaveStore) {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun loadMeta(): MetaProgress {
        val raw = store.readMeta() ?: return MetaProgress.initial()
        return runCatching { json.decodeFromString<MetaProgress>(raw) }
            .getOrElse { MetaProgress.initial() }
            .let { restored ->
                // Neu hinzugekommene Standardinhalte nachtragen.
                val defaults = MetaProgress.initial()
                restored.copy(
                    unlockedCards = restored.unlockedCards + defaults.unlockedCards,
                    unlockedPaths = restored.unlockedPaths + defaults.unlockedPaths,
                )
            }
    }

    fun saveMeta(meta: MetaProgress) {
        store.writeMeta(json.encodeToString<MetaProgress>(meta))
    }

    fun loadRun(): RunState? {
        val raw = store.readRun() ?: return null
        return runCatching { json.decodeFromString<RunState>(raw) }.getOrNull()
    }

    fun saveRun(run: RunState?) {
        store.writeRun(run?.let { json.encodeToString<RunState>(it) })
    }

    fun clearRun() = store.writeRun(null)
}
