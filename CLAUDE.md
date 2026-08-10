# Aschepfad — Projektkontext

Android-Deckbuilding-Roguelike im Stil klassischer Sammelkartenspiele. Endloser
Zug gegen skalierende Gegner, Deckbau während des Laufs, dauerhafter Fortschritt
zwischen den Läufen.

Alle Karten, Aspekte und Schlüsselwörter sind eigenständig erfunden. Übernommen
ist nur das Genre-Grundgerüst. **Keine Marken-, Karten- oder Regelnamen anderer
Spiele einbauen.**

## Bauen und Testen

```bash
./gradlew :core:test          # Spiellogik, braucht kein Android SDK
./gradlew :app:assembleDebug  # App, braucht SDK Platform 35
```

`settings.gradle.kts` bindet `:app` nur ein, wenn ein Android SDK gefunden wird
(`ANDROID_HOME` oder `local.properties`). Ohne SDK bleibt `:core` baubar — genau
so ist das gesamte Projekt entstanden.

Toolchain: AGP 8.7.3, Kotlin 2.1.21, Gradle 8.14.3 (Wrapper liegt bei),
compileSdk/targetSdk 35, minSdk 26, JDK 17.

## Modulaufbau

```
core/   Reines Kotlin: Regeln, Karten, Gegner-KI, Laufsteuerung, Sitzungslogik
app/    Android: Jetpack Compose, Speicherdateien, Activity
```

**Die gesamte Ablauflogik liegt bewusst in `core`**, einschließlich der
App-Steuerung in `core/session/GameSession.kt`. Das Compose-Modul zeichnet nur
und leitet Eingaben weiter; `GameController` ist eine dünne beobachtbare Hülle
mit einem Revisionszähler. Wer neue Spiellogik schreibt, schreibt sie in `core`
und testet sie dort — nicht im App-Modul.

| Datei | Inhalt |
| --- | --- |
| `core/model/Cards.kt` | Kartenmodell, Effekt-Hierarchie, Filter, Werte |
| `core/engine/Battle.kt` | Zugablauf, Stapel, Kampf, Effektauflösung |
| `core/engine/EssenceSolver.kt` | Kostenbezahlung als bipartites Matching |
| `core/content/CardLibrary.kt` | Alle Kartendefinitionen (119 Spielkarten) |
| `core/content/Enemies.kt` | Gegnervorlagen und Stufenskalierung |
| `core/ai/HeuristicBrain.kt` | Gegner-KI in drei Spielstärken |
| `core/run/RunManager.kt` | Stationen, Belohnungen, Händler, Begegnungen |
| `core/session/GameSession.kt` | Bildschirmablauf und Kampfsteuerung |
| `core/sim/AutoPlayer.kt` | Spielt Partien ohne Oberfläche zu Ende |

## Konventionen

- **Fachsprache ist Deutsch.** Klassen, Felder, Enum-Werte und Kommentare nutzen
  deutsche Begriffe (`Kreatur`, `Quelle`, `Essenz`, `Marken`). Nicht auf
  Englisch umstellen.
- **Bezeichner und Kartentexte in Kotlin sind ASCII-transliteriert**
  (`Funkenlaeufer`, `Waechter`, `zerstoert`). Umlaute stehen nur in Markdown und
  in einzelnen UI-Symbolen. Neue Karten bitte genauso schreiben.
- **Testnamen sind deutsche Sätze in Backticks** und beschreiben das erwartete
  Verhalten, nicht die Methode.
- Kommentare erklären *warum*, nicht *was*. Bestehende Dichte beibehalten.

## Regelbesonderheiten, die nicht offensichtlich sind

- **Quellen werden automatisch getappt.** Farbige Kosten löst
  `EssenceSolver` als bipartites Matching — greedy liefert bei Kosten wie `GG1`
  falsche Ergebnisse.
- **Jede Handkarte kann verdeckt als farblose Quelle gelegt werden.** Das
  verhindert Ressourcenpech, ohne farbigen Deckbau zu entwerten.
- **Ausgelöste Fähigkeiten lösen sofort auf**, nur Zauber durchlaufen den
  Stapel — damit Neutralisieren funktioniert.
- **Todesketten sind auf `Battle.MAX_TRIGGER_DEPTH` gedeckelt.** Ohne das ließe
  sich mit zwei Karten eine Endlosschleife bauen.
- **Ein Lauf hat höchstens zwei farbige Aspekte** (`RunManager.MAX_ASPECTS`).
  Belohnungen bieten nur Karten an, die der Lauf auch wirken kann.
- **Der Gegnerzug läuft in einem Rutsch durch.** Nachvollziehbar ist er nur über
  das Kampfprotokoll — hier fehlen Animationen.

## Tests

139 Tests. Neben Einzelfällen zwei Sorten, die beim Erweitern wichtig sind:

- **Wächtertests über die Kartenliste** (`TribalTest`): jede Kreatur braucht
  einen Typ, jeder Stamm mindestens drei Karten, jeder mechanisch angesprochene
  Stamm muss als `archetypes` markiert sein. Beim Nachliefern neuer Karten
  schlagen sie an, bevor etwas still verkümmert.
- **`SimulationTest`**: Die KI übernimmt beide Seiten und spielt jede Kombination
  aus Startpfad und Gegner über mehrere Stufen aus. Findet Regelfehler und
  Hänger, die Einzeltests nicht sehen.

Nach inhaltlichen Änderungen immer die volle Suite laufen lassen, nicht nur die
betroffene Klasse — die Simulation ist der eigentliche Wächter.

## Stand

Vollständig spielbar in der Schleife Kampf → Belohnung → nächste Stufe.

**Wichtig für die erste lokale Sitzung:** Das `:app`-Modul wurde nie kompiliert.
Die gesamte Entwicklung lief in einer Umgebung ohne Zugriff auf Googles
Maven-Spiegel, deshalb liegt die Logik in `core` und ist dort getestet. Der
Compose-Code ist sorgfältig geprüft, aber ungebaut. Erwartbar sind fehlende
Importe oder Signaturabweichungen in Material3.

Erster sinnvoller Schritt lokal:

```bash
./gradlew :core:test          # muss grün sein
./gradlew :app:assembleDebug  # hier ist mit Fehlern zu rechnen
```

## Nächste Schritte

- `:app` zum Bauen bringen und auf einem Gerät starten
- Animationen für den Gegnerzug
- Kartenpool ausbauen; Ziel sind langfristig etwa 450 Karten. Vor 300 Karten
  sollte `CardLibrary.kt` in eine Datei je Aspekt aufgeteilt werden, Gegnerdecks
  brauchen kuratierte Archetyp-Pools statt des flachen Aspektfilters, und das
  Kompendium braucht Filter.
- Balancing pro Karte: `AutoPlayer` liefert die Partien, es fehlt die Auswertung
  je Karte statt je Pfad.
