package dev.deckbuild.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import dev.deckbuild.app.ui.AppRoot
import dev.deckbuild.core.meta.SaveService
import dev.deckbuild.core.meta.SaveStore
import java.io.File

/**
 * Spielstand als zwei einfache Textdateien im privaten App-Verzeichnis.
 * Fuer ein reines Einzelspieler-Spiel ohne Konto ist das die passende Groesse -
 * eine Datenbank waere hier nur zusaetzliche Angriffsflaeche fuer Fehler.
 */
class FileSaveStore(baseDir: File) : SaveStore {
    private val metaFile = File(baseDir, "meta.json")
    private val runFile = File(baseDir, "run.json")

    override fun readMeta(): String? = metaFile.takeIf { it.exists() }?.readText()

    override fun writeMeta(json: String) {
        runCatching { metaFile.writeText(json) }
    }

    override fun readRun(): String? = runFile.takeIf { it.exists() }?.readText()

    override fun writeRun(json: String?) {
        runCatching {
            if (json == null) runFile.delete() else runFile.writeText(json)
        }
    }
}

class MainActivity : ComponentActivity() {

    private lateinit var controller: GameController

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        controller = GameController(SaveService(FileSaveStore(filesDir)))

        setContent {
            AppRoot(controller)
        }
    }

    override fun onPause() {
        super.onPause()
        // Der Spielstand wird bei jeder Entscheidung geschrieben; dies fangt den
        // Fall ab, dass die App mitten in einer Ansicht beendet wird.
        controller.persist()
    }
}
