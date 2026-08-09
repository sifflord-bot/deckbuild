# Aschepfad

Ein Android-Deckbuilding-Roguelike im Stil klassischer Sammelkartenspiele: Essenz
als Ressource, Kreaturen mit Stärke und Widerstandskraft, Spontanzauber auf einem
Stapel, Angreifer und Blocker. Statt gegen Mitspieler tritt man in einem endlosen
Zug gegen immer stärkere Gegner an und baut sein Deck während des Laufs weiter aus.

Alle Karten, Aspekte und Schlüsselwörter sind eigenständig. Übernommen ist nur das
Genre-Grundgerüst; Marken, Kartennamen und Illustrationen anderer Spiele kommen
nicht vor.

## Spielprinzip

**Ressourcen.** Quellen erzeugen Essenz in fünf Aspekten: Glut, Flut, Asche, Hain,
Licht. Pro Zug darf eine Quelle gelegt werden. Alternativ lässt sich eine
beliebige Handkarte verdeckt als farblose Quelle ablegen — das verhindert
Ressourcenpech, ohne den farbigen Deckbau zu entwerten, weil farbige Kosten
weiterhin echte Quellen brauchen. Beim Ausspielen werden Quellen automatisch
optimal getappt.

**Zug.** Enttappen → Aufziehen → Ziehen → erste Hauptphase → Kampf → zweite
Hauptphase → Endphase. Angreifer werden deklariert, der Verteidiger weist Blocker
zu, dann fällt Kampfschaden — mit einem eigenen Schritt für Vorstoß.

**Schlüsselwörter.** Flink, Wacht, Flug, Reichweite, Trampeln, Vorstoß, Gift,
Zehrung, Wächter, Verschleiert, Schild und Verbrauch.

**Stämme.** Kreaturen tragen Typen — Krieger, Magier, Geist, Bestie, Untoter,
Elementar, Konstrukt, Drache. Anführer stärken ihren Stamm, Zahlungen zählen
ihn („2 Schaden, plus 1 je eigenem Elementar"). Daneben gibt es eine
Zauber-Achse: Karten, die auslösen, sobald man einen Zauber wirkt.

Damit ein wachsender Kartenpool die Belohnungen nicht beliebiger macht, sind
Karten mit dem Stamm markiert, für den sie gebaut sind. Sobald ein Deck drei
Kreaturen eines Stammes enthält, werden passende Karten dreifach gewichtet
angeboten — ein Deck verdichtet sich also über den Lauf hinweg, statt zufällig
zu wachsen.

**Verbrauch gibt es zweifach.** Karten mit dem Schlüsselwort *Verbrauch* werden
nach dem Ausspielen verbannt statt abgelegt. Daneben trägt man bis zu drei
Verbrauchsgegenstände im Beutel: Sie kosten keine Essenz, wirken auch mitten im
Blockschritt und laden sich an Rastplätzen wieder auf.

## Lauf und Fortschritt

Ein Lauf ist eine endlose Folge von Stufen. Jede Stufe bietet zwei bis drei
Stationen zur Wahl — Gefecht, Elitegegner, Rastplatz, Händler, Fundstelle oder
eine Textbegegnung. Jede fünfte Stufe ist ein Boss. Nach jedem Sieg wählt man eine
von drei Karten oder überspringt sie gegen Gold.

Gegner skalieren mit der Stufe: mehr Leben, teurere Karten im Deck, höhere
Wahrscheinlichkeit für seltene Karten und ab Stufe 10 zusätzliche Startquellen.
Dieselbe Gegnervorlage bleibt dadurch auch in Stufe 40 bedrohlich.

Endet ein Lauf, bleibt der Fortschritt: Im Lauf eingesammelte Karten wandern
dauerhaft in den Pool und tauchen in künftigen Läufen als Belohnung auf. Besiegte
Bosse geben ihre Signaturkarte frei, und zwei der fünf Startpfade schalten sich
über Meilensteine frei.

## Projektaufbau

```
core/   Reines Kotlin-Modul: Spielregeln, Karten, Gegner-KI, Laufsteuerung
app/    Android-Modul: Jetpack Compose, Speicherdateien, Activity
```

Die gesamte Spiellogik liegt in `core` und ist ohne Android-SDK baubar und
testbar — inklusive der App-Ablaufsteuerung in `core/session/GameSession.kt`. Das
Compose-Modul zeichnet nur und leitet Eingaben weiter; `GameController` ist eine
dünne beobachtbare Hülle um die Sitzung.

Wichtige Stellen:

| Datei | Inhalt |
| --- | --- |
| `core/engine/Battle.kt` | Zugablauf, Stapel, Kampf, Effektauflösung |
| `core/engine/EssenceSolver.kt` | Bezahlen von Kosten als bipartites Matching |
| `core/content/CardLibrary.kt` | Alle Kartendefinitionen |
| `core/content/Enemies.kt` | Gegnervorlagen und Stufenskalierung |
| `core/ai/HeuristicBrain.kt` | Gegner-KI in drei Spielstärken |
| `core/run/RunManager.kt` | Stationen, Belohnungen, Händler, Begegnungen |
| `core/session/GameSession.kt` | Bildschirmablauf und Kampfsteuerung |
| `app/ui/BattleScreen.kt` | Kampfoberfläche |

## Bauen

**Nur die Spiellogik** (kein Android SDK nötig):

```bash
./gradlew :core:test
```

`settings.gradle.kts` bindet `:app` nur ein, wenn ein Android SDK gefunden wird
(`ANDROID_HOME` oder `local.properties`). Auf Servern ohne SDK bleibt `:core`
dadurch baubar.

**Die App:** Projekt in Android Studio öffnen und `:app` ausführen, oder

```bash
./gradlew :app:assembleDebug
```

Mindest-SDK ist 26, Ziel-SDK 35.

## Tests

Rund 88 Tests decken den Regelkern und den Spielablauf ab: Kostenberechnung,
Kampf mit allen Schlüsselwörtern, Zauber und Stapel, Stammes-Synergien,
Laufsteuerung, Speicherstand und der komplette Bildschirmablauf einer Sitzung.

Einige davon sind Wächtertests über die Kartenliste selbst: Jede Kreatur braucht
einen Typ, jeder Stamm mindestens drei Karten, und wer einen Stamm mechanisch
anspricht, muss ihn auch als Archetyp führen. Beim Nachliefern neuer Karten
schlagen sie an, bevor ein Stamm still verkümmert.

Dazu simuliert `SimulationTest` vollständige Partien: Die Heuristik-KI übernimmt
beide Seiten und spielt jede Kombination aus Startpfad und Gegner über mehrere
Stufen aus. Das findet Regelfehler und Hänger, die Einzeltests nicht sehen, und
liefert zugleich Gewinnraten fürs Balancing.

## Stand und nächste Schritte

Spielbar und vollständig in der Schleife Kampf → Belohnung → nächste Stufe.
Sinnvolle Erweiterungen:

- Animationen für den Gegnerzug; derzeit läuft er in einem Zug durch und ist nur
  über das Kampfprotokoll nachvollziehbar
- Freie Prioritätsfenster statt eines Antwortfensters pro Spielerzauber
- Deckbau zwischen den Läufen und mehr Karten pro Aspekt
- Kartenveredelung an Rastplätzen als zusätzliche Fortschrittsachse
- Modale Karten, X-Kosten, Marken-Zahlungen und ein Opfer-Auslöser („wenn eine
  andere Kreatur stirbt") — die vier Mechaniken, die das Effekt-Vokabular am
  stärksten verbreitern
