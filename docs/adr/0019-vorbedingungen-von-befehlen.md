# ADR 0019: Vorbedingungen von Befehlen

**Status:** Angenommen, 2026-11-24 (T-ARC-009), ergänzt ADR 0007, ADR 0015 und ADR 0016

## Kontext

`AcceptAdjustment` ist nur zulässig, wenn die aktuelle Auswertung des Raums `NeedsAdjustment` mit gleichem Vorschlag ist (DOMAIN §5.5). Mit den bisherigen Signaturen war das nicht prüfbar:

- `ProjectCommand.apply(project)` ist rein, nicht `suspend` und kennt keine Toleranz. `RoomEvaluator.evaluate(room, tolerance)` ist `suspend`.
- Prüft der Aufrufer selbst, kann sich zwischen seiner Auswertung und `execute` ein `reconcile` mit neuer Toleranz einreihen. Die Prüfung wäre außerdem freiwillig.
- Der Fingerabdruck allein reicht nicht. Er bindet das Overlay an die Rohgeometrie, nicht an die Toleranz.

**Zahlenfall** (Prüfstand `docs/research/evaluation-prototype/flow.py`): Rechteck 4150, 3000, 4000, 3000, alle Ecken als 90° gemessen, `U = 14 150`.

| Toleranz | `T` | Pfad | Ergebnis |
|---|---|---|---|
| `T_abs = 250` | 250 | Messfehler, `e = 150 ≤ T` | `NEEDS_ADJUSTMENT`, einfache Bestätigung |
| Standard | 141,5 | Zweig B, B9 | `NEEDS_ADJUSTMENT`, ausdrückliche Bestätigung, `S = 223,94`, `q² = 8,062` |

Overlay, `S`, `q²` und größte Korrektur (55,3 mm an `V_0`) sind in beiden Fällen gleich, ebenso der Fingerabdruck. Ein unter `T_abs = 250` angezeigter einfacher Vorschlag wäre nach dem Wechsel zur Standardtoleranz ohne ausdrückliche Bestätigung angenommen worden und dauerhaft `ADJUSTED` geblieben (DOMAIN §4 Schritt 2). Ebenso könnte ein Raum, der unter strengerem `T` `PROPOSED(AMBIGUOUS_SHAPE)` ist, über einen alten Vorschlag `ADJUSTED` werden.

## Optionen

- **A:** `ProjectCommand` erhält eine optionale `suspend`-Vorbedingung, die `CommandHistory.execute` unter dem Mutex prüft. `apply` bleibt rein.
- **B:** `apply(project, context)` mit Auswertung. Das vermischt Modelländerung und Auswertung in jedem Befehl, wie in ADR 0015 verworfen.
- **C:** Prüfung im Aufrufer. Sie wäre umgehbar und nicht atomar zum Commit.
- **D:** Die Toleranz im Overlay speichern. Das ändert das Dokumentformat und widerspricht DOMAIN §6, wonach ein Overlay toleranzunabhängig gültig bleibt.

## Entscheidung

Option A.

- `ProjectCommand` bleibt ein `fun interface`. Neu ist `suspend fun verify(project: Project, context: CommandContext): DomainResult<Unit>` mit dem Standard `Success(Unit)`.
- `CommandContext(tolerance, evaluator)` liefert die Toleranz des laufenden `execute` und `evaluate(room)`, das den `RoomEvaluator` der Historie mit genau dieser Toleranz aufruft. Eine Vorbedingung kann deshalb nicht mit einer anderen Toleranz auswerten.
- **Ablauf von `execute(command, tolerance)`:** Mutex nehmen, in einem `withContext` auf dem Default-Dispatcher `verify` auf dem zuletzt festgeschriebenen Projekt, dann `apply`, dann `DockPlacementResolver.resolve`, danach unter demselben Mutex committen. Der erste Fehlschlag wird unverändert zurückgegeben. Abbruch an jeder Stelle vor dem Commit lässt Projekt und Historie unverändert (ADR 0016).
- `apply` bleibt rein und deterministisch. Was sich ohne Auswertung prüfen lässt, prüft weiter `apply`, etwa Fingerabdruck und Bestätigungsstufe bei `AcceptAdjustment`.
- **`AcceptAdjustment`:** `verify` verlangt `NeedsAdjustment` mit strukturell gleichem `AdjustmentProposal`, sonst `ADJUSTMENT_NOT_AVAILABLE`. Strukturelle Gleichheit ist hier zulässig, weil beide Werte aus derselben deterministischen Auswertung stammen (DOMAIN §11).
- **Toleranz des Aufrufs:** Der Aufrufer übergibt die zum Aufrufzeitpunkt gültige Toleranz aus den Einstellungen. Operationen gelten in Aufrufreihenfolge. Ein `execute`, das vor `reconcile(neu)` aufgerufen wurde, läuft vollständig mit der alten Toleranz und ist gültig.
- **Undo und Redo** stellen gespeicherte Stände exakt wieder her und rufen `verify` nicht auf, wie in ADR 0015. Sie sind Wiederherstellungen, keine neuen Entscheidungen.
- **Toleranzwechsel nach gültigem `AcceptAdjustment`:** Das Overlay bleibt nach DOMAIN §4 Schritt 2 gültig und der Raum `ADJUSTED`, auch wenn die neue Toleranz einen anderen Vorschlag oder `PROPOSED(AMBIGUOUS_SHAPE)` ergäbe. `reconcile` ändert nur Platzierungen, nie Overlays, und prüft keine Vorbedingungen. Das gilt ebenso für ein per Redo wiederhergestelltes Overlay.
- `ProjectCommandFactory` behält ihre Signatur. Die erzeugten Befehle implementieren `verify`, wo DOMAIN §11 eine Vorbedingung nennt. Produktiver Code führt nur Befehle aus der Fabrik aus, ein Wrapper muss `verify` weiterreichen.

## Konsequenzen

Ein veralteter Vorschlag wird vertraglich abgewiesen, und der Aufrufer kann die Prüfung nicht überspringen, weil nur `execute` committet und `verify` immer vorher aufruft. `AcceptAdjustment` kostet eine zusätzliche Auswertung des einen Raums unter dem Mutex. Andere Befehle behalten den Standard und kosten nichts. Die Historie hängt zusätzlich vom `RoomEvaluator` ab. Die Prüfung von `execute` mit Vorbedingung, Abbruch in `verify` und dem Zahlenfall gehört zu den Tests der Historie (T-DOM-002).
