# ADR 0015: Andocken und reine Befehle

**Status:** Angenommen, 2026-11-23, Nebenläufigkeit ergänzt durch ADR 0016

## Kontext

`ProjectCommand.apply(project)` ist rein (ADR 0007). Die Platzierung angedockter Räume hängt aber vom ausgewerteten Umriss des Wirtsraums ab, und dieser hängt von der Toleranz aus den Einstellungen ab, die nicht Teil von `Project` ist.

## Optionen

- **A:** `apply` bleibt unverändert. `CommandHistory` löst danach die Platzierungen über einen injizierten `DockPlacementResolver` auf.
- **B:** Befehle tragen vorberechnete Platzierungen aller abhängigen Räume. Das bläht Befehle auf und kann veraltete Werte enthalten.
- **C:** `apply(project, context)`. Das vermischt Modelländerung und Auswertung in jedem Befehl.

## Entscheidung

Option A.

- `CommandHistory.execute(command, tolerance)` führt `apply` und danach `DockPlacementResolver.resolve(project, tolerance)` aus. Beides zusammen ist genau ein Undo-Schritt. Schlägt einer der beiden Schritte fehl, bleiben Projekt und Historie unverändert.
- Undo und Redo stellen die gespeicherten Stände exakt wieder her und rechnen nichts neu.
- Maßgeblich ist der angezeigte Umriss: die Sollform bei `CONFIRMED`, das Overlay bei `ADJUSTED`, die Vorschau bei `NEEDS_ADJUSTMENT`, der Vorschlag bei `PROPOSED`. Ist der Wirtsraum oder der eigene Raum `OPEN` oder `CONFLICT`, bleibt die letzte Platzierung des Raums einschließlich Dock-Link erhalten, weil kein Umriss zum Ausrichten existiert.
- Ändert sich die Toleranz, ruft der Editor `CommandHistory.reconcile(tolerance)` auf. Ändern sich dadurch Platzierungen, entsteht genau ein Undo-Schritt, sonst keiner.

## Konsequenzen

Befehle bleiben rein und einzeln testbar. Die Historie hängt von einem zusätzlichen Interface ab. Die Auflösung läuft nach jedem Befehl und muss für 30 Räume schnell genug sein, was der Performance-Test abdeckt.
