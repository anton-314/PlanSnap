# ADR 0016: Nebenläufigkeit der Auswertung

**Status:** Angenommen, 2026-11-23, präzisiert durch T-ARC-006

## Kontext

`CommandHistory.execute` löst nach jedem Befehl die Platzierungen auf und wertet dafür Räume mit dem Solver aus. Synchron auf dem Main-Dispatcher würde das bei großen Räumen die UI blockieren. Reine Auslagerung ohne Serialisierung würde Historienzugriffe überlappen lassen. `:core:geometry` soll dabei frei von Coroutines bleiben.

## Optionen

- **A:** Synchrone Verträge, der Aufrufer lagert aus. Serialisierung und Abbruch wären in jedem Aufrufer neu zu lösen.
- **B:** `suspend`-Verträge in `:core:domain` und ein JVM-Abbruchsignal in `:core:geometry`.
- **C:** Coroutines direkt in `:core:geometry`. Das koppelt den reinen Rechenkern an eine Laufzeit.

## Entscheidung

Option B.

- `CommandHistory.execute`, `reconcile`, `undo`, `redo`, `DockPlacementResolver.resolve` und `RoomEvaluator.evaluate` sind `suspend` und main-safe. Die Auswertung läuft auf dem injizierten Default-Dispatcher.
- Alle verändernden Zugriffe auf die Historie sind über einen fairen Mutex in Aufrufreihenfolge serialisiert. `undo` und `redo` gehören dazu, weil sie sonst einen laufenden `execute` überholen könnten. `state` bleibt ein atomarer Schnappschuss und ist von jedem Thread lesbar.
- **Ablauf einer Operation:** Mutex nehmen, die Berechnung unter dem Mutex per `withContext` auf dem Default-Dispatcher ausführen, danach unter demselben Mutex committen. Der Mutex wird zwischen Berechnung und Commit nicht freigegeben.
- `CancellationSignal.checkpoint()` in `:core:geometry` wirft bei Abbruch die JDK-Klasse `CancellationException`. Solver, Ausgleich und Rangbestimmung rufen es mindestens vor jedem Startwert, je Iteration und je Eliminationsschritt auf. `currentCancellationSignal()` in `:core:domain` bindet es an den laufenden Job.
- Abbruch liefert kein Teilergebnis. Bis zum Commit bleiben Projekt und Historie unverändert, der Commit selbst ist ein einzelnes atomares Update.
- **Lebensdauer:** Bestätigte Befehle und das Speichern laufen in einem Application-Scope und werden durch Verlassen des Editors nicht abgebrochen. Abbrechbar sind nur Vorschauen und Auswertungen ohne Commit.
- Befehle werden nicht zusammengefasst oder verworfen. „Ein neuerer Lauf ersetzt den älteren“ gilt nur für Vorschauen.

## Konsequenzen

Die UI bleibt flüssig, und Undo kann keinen halb ausgeführten Befehl sehen. Aufrufer von `undo` und `redo` müssen in einer Coroutine laufen. Das Signal ist ein Pflichtparameter der Rechenverträge, damit es nicht versehentlich entfällt.
