# ADR 0021: Wirksame Wandlänge für Öffnungen

**Status:** Angenommen, 2026-11-25 (CR-W2-06), präzisiert DOMAIN §9 und ergänzt ADR 0018 und ADR 0019

## Kontext

DOMAIN §9 verlangt `offset + width ≤ L_wirksam + EXACT_CLOSURE_MM`, ließ `L_wirksam` aber offen. Das Gate-Review von Welle 2 fand zwei Lesarten im Code:

- `DefaultOpeningRules` (hinter `AddOpening` und `EditOpening`) nahm die Overlay-Länge, sobald der Fingerabdruck passt, sonst die Rohlänge, auch bei einem strukturell ungültigen Overlay.
- `DefaultRoomEvaluator` (Quelle von `RoomEvaluation.openingStatuses`, die Szene und Export-Vorprüfung lesen) nahm die Länge der angezeigten Form, bei `NEEDS_ADJUSTMENT` also die Vorschau, bei `PROPOSED` aus B7 die ausgeglichene Länge, und verwarf strukturell ungültige Overlays.

Zwei Probefälle belegen den Widerspruch:

| Fall | Evaluator | Regeln |
|---|---|---|
| `GAP_30_MM` (Rohlänge `w0` = 4030, Vorschau kürzer), Tür 3200 + 831 | `OUT_OF_BOUNDS` | `VALID` |
| Rechteck 4000 × 3000 mit aktuellem, nicht schließendem Overlay (`L0 = 3000`), Öffnung 3000 + 1000 | `VALID` | `OUT_OF_BOUNDS` |

Der Befehl nahm also Öffnungen an, die die Szene sofort rot zeigte, und lehnte grün gezeigte ab.

## Optionen

- **A: Angezeigte Form überall.** Regeln und Evaluator messen an `shape.lengthsMm`. Die Vorschau hängt von der Toleranz und vom Solver ab, `OpeningRules` ist aber rein und kennt keine Toleranz (DOMAIN §11). Der Befehl müsste die Prüfung in `verify` verlegen (ADR 0019), der Vertrag `OpeningRules` müsste toleranzabhängig werden. Eine Öffnung würde zudem beim bloßen Wechsel der Toleranz oder durch eine neue Vorschau rot oder grün, obwohl der Nutzer nichts bestätigt hat.
- **B: Bestätigte Länge.** `L_wirksam` ist die Overlay-Länge genau dann, wenn §4 Schritt 2 das Overlay annimmt (Raum geschlossen, Fingerabdruck passt, strukturell gültig), sonst die Rohlänge. Vorschau und Solverform zählen nie.
- **C: Immer die Rohlänge.** Einfach, aber nach `AcceptAdjustment` zeigte die Szene die bestätigte Wand mit Öffnungen, die an einer anderen Länge gemessen sind. Ein bestätigtes Overlay ist nach DOMAIN §4 die gültige Geometrie.

## Entscheidung

Option B. Für jeden Status gilt:

| Status | `L_wirksam` |
|---|---|
| `OPEN` | Rohlänge |
| `CONFIRMED` (auch mit veraltetem oder strukturell ungültigem Overlay) | Rohlänge, gleich der Länge der Sollform |
| `NEEDS_ADJUSTMENT` | Rohlänge, nicht die Vorschau |
| `ADJUSTED` | Länge des angenommenen Overlays |
| `PROPOSED` (A4 und B7) | Rohlänge, nicht die Solver- oder Ausgleichslänge |
| `CONFLICT` | Rohlänge |

- Es gibt genau eine Implementierung in `:core:domain` (`EffectiveWallLength`, intern). `DefaultOpeningRules` und `DefaultRoomEvaluator` nutzen sie beide. Die Overlay-Länge kommt nur über ein angenommenes Overlay (`AcceptedOverlay`) hinein, das allein die Prüfung aus §4 Schritt 2 erzeugt. Eine Vorschau lässt sich deshalb nicht übergeben.
- `L_wirksam` hängt weder von der Toleranz noch von `solverVersion` ab. Damit gilt für jeden Raum, dessen Auswertung gelingt: `OpeningRules.statusOf(room, o) == evaluate(room, T).openingStatuses[o.id]` für jedes `T`.
- **Zeitpunkt des Wechsels:** Der Status einer Öffnung ändert sich nur, wenn sich die Rohwerte ändern oder ein Overlay angenommen oder verworfen wird (`AcceptAdjustment`, `RevertAdjustment`, Undo, Redo, geänderte Rohgeometrie). Eine Vorschau oder ein Toleranzwechsel ändert ihn nie. Kürzt das bestätigte Overlay eine Wand, wird eine bis dahin gültige Öffnung nach `AcceptAdjustment` `OUT_OF_BOUNDS`. Sie bleibt nach DOMAIN §9 gespeichert und wird gemeldet, `AcceptAdjustment` wird dadurch nicht abgelehnt.
- **Zeichnung:** Die Szene legt eine Öffnung wie bisher bei `offset` ab `V_i` auf die angezeigte Wand und färbt sie nur nach `openingStatuses`. In einer Vorschau kann eine gültige Öffnung deshalb um höchstens die Korrektur der Wand plus `EXACT_CLOSURE_MM` über das angezeigte Wandende reichen. Das ist zulässig, weil die Form dort als „nicht bestätigt“ markiert ist.
- **Verbraucher:** Szene und Export-Vorprüfung lesen den Status nur aus `RoomEvaluation.openingStatuses`, Befehle nur aus `OpeningRules`. Beide liefern nach dieser ADR dasselbe.

## Konsequenzen

Befehl, Szene und Export-Vorprüfung sind für jede Öffnung einig, belegt durch einen Property-Test über alle Katalogfälle einschließlich angenommener, veralteter und nicht schließender Overlays. `OpeningRules` bleibt rein und ohne Toleranz, die öffentlichen Verträge ändern sich nur in der KDoc. `statusOf` und `check` prüfen bei einem gespeicherten Overlay zusätzlich dessen strukturelle Gültigkeit, also Zug und Einfachheit in O(n²) mit `n ≤ 128`. Der Test „Ausgleich kürzt die Wand, Öffnung `OUT_OF_BOUNDS`“ aus T-DOM-001 wird zu „erst das angenommene Overlay macht sie `OUT_OF_BOUNDS`“, die Regeltests mit aktuellem Overlay nutzen ein strukturell gültiges Overlay. Ein Hinweis im Bestätigungsdialog, welche Öffnungen durch das Annehmen ungültig würden, ist eine mögliche Erweiterung für den Editor.
