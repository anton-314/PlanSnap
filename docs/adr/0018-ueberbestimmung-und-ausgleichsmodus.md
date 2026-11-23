# ADR 0018: Überbestimmung und Ausgleichsmodus

**Status:** Angenommen, 2026-11-23

## Kontext

DOMAIN §5.5 verlangte „mehr unabhängige Bedingungen als Variablen“. Der Rang kann die Variablenzahl aber nie übersteigen, und er war nur an einer Lösung definiert, die es im überbestimmten Fall nicht geben muss. Zudem würde ein Ausgleich mit deklarierten 90°-Ecken als Beobachtungen eine in sich stimmige Form verwerfen: Ein Trapez mit vier Längen und zwei passenden Diagonalen müsste seine Winkel weit von 90° wegkorrigieren und endete fälschlich in `CONFLICT`. Das widerspricht DOMAIN §4, wonach deklarierte Ecken bei einer Formabweichung nur weiche Sollwerte sind.

## Optionen

- **A:** Zählen, `m > v`. Erkennt doppelte oder starre Diagonalen nicht.
- **B:** Rang nur an `τ̂`. Eine zufällige Singularität von `τ̂` würde zu falschen Konflikten führen.
- **C:** Rang an `τ̂` und bei Defekt zusätzlich an einem festen Prüfpunkt, dazu ein Ausgleichsmodus für deklarierte Ecken.

## Entscheidung

Option C.

- `analyzeConstraints` weist `v`, `m` und `ρ` getrennt aus. Überbestimmt ist eine Eingabe bei `m > ρ` (DOMAIN §5.4).
- `ClosureAdjuster.adjust` erhält `AdjustmentMode`. `MEASUREMENT_ERROR` behandelt deklarierte rechte Winkel als Beobachtungen, `SHAPE_DEVIATION` (§5.5) nicht.
- `maxCorrectionMm` umfasst Längen, Diagonalen und beobachtete Winkel als Bogenmaß `|Δτ| · U / (2π)`. Ecken ohne Beobachtung zählen nicht.
- `NotConverged` führt zu `CONFLICT(ADJUSTMENT_NOT_CONVERGED)` ohne Overlay.

## Konsequenzen

Widersprüchliche Messungen enden deterministisch in `CONFLICT`, stimmige überbestimmte Formen nicht. Im Modus `SHAPE_DEVIATION` startet der Ausgleich weiter von den Sollwinkeln entfernt. T-GEO-006 muss die Konvergenz dafür mit dem Trapez samt zwei Diagonalen belegen.
