# ADR 0018: Überbestimmung und Ausgleichsmodus

**Status:** Angenommen, 2026-11-23, ergänzt durch T-ARC-006

## Kontext

DOMAIN §5.5 verlangte „mehr unabhängige Bedingungen als Variablen“. Der Rang kann die Variablenzahl aber nie übersteigen, und er war nur an einer Lösung definiert, die es im überbestimmten Fall nicht geben muss. Zudem würde ein Ausgleich mit deklarierten 90°-Ecken als Beobachtungen eine in sich stimmige Form verwerfen: Ein Trapez mit vier Längen und zwei passenden Diagonalen müsste seine Winkel weit von 90° wegkorrigieren und endete fälschlich in `CONFLICT`. Das widerspricht DOMAIN §4, wonach deklarierte Ecken bei einer Formabweichung nur weiche Sollwerte sind.

## Optionen

- **A:** Zählen, `m > v`. Erkennt doppelte oder starre Diagonalen nicht.
- **B:** Rang nur an `τ̂`. Eine zufällige Singularität von `τ̂` würde zu falschen Konflikten führen.
- **C:** Rang an `τ̂` und bei Defekt zusätzlich an einem festen Prüfpunkt, dazu ein Ausgleichsmodus für deklarierte Ecken.

## Entscheidung

Option C.

- `analyzeConstraints` weist `v`, `m`, die unabhängige Menge `I`, `ρ` und `missing` aus. Überbestimmt ist eine Eingabe bei `m > ρ` (DOMAIN §5.4). Dieser Rang dient nur dem Routing, maßgeblich ist der Rang an der Lösung.
- `ClosureAdjuster.adjust` erhält `AdjustmentMode`. `MEASUREMENT_ERROR` behandelt deklarierte rechte Winkel als Beobachtungen, `SHAPE_DEVIATION` (§5.5) nicht.
- `maxCorrectionMm` und die Summennorm `S` umfassen Längen, Diagonalen und beobachtete Winkel als Bogenmaß `|Δτ| · U / (2π)`. Ecken ohne Beobachtung zählen nicht.
- `NotConverged` führt zu `CONFLICT(ADJUSTMENT_NOT_CONVERGED)` ohne Overlay.

## Konsequenzen

Widersprüchliche Messungen enden deterministisch in `CONFLICT`. T-GEO-005 und T-GEO-006 müssen alle Fälle aus `OverdeterminedCases` in `testing/fixtures` prüfen.

## Ergänzung T-ARC-006

**Unbestimmte Formen.** `m > ρ` zeigt nur eine abhängige Bedingung. Das Fünfeck mit doppelter Diagonale hat `v = 4, m = 4, ρ = 3` und bleibt mit `missing = 1` ein Vorschlag.

**Startform.** Der Ausgleich ab `τ̂` lieferte in 399 stimmigen Zufallsräumen 16 falsche Formen, 13 selbstüberschneidende und einen falschen Konflikt. Mit Solver-Start auf der unabhängigen Menge und getrenntem Linearisierungspunkt waren im Prototyp alle 399 korrekt.

**Konfliktregel.** `CONFLICT(MEASUREMENTS_INCONSISTENT)` folgt genau bei `S > T` und `q² > χ²(r; 0,99)`. Die reine Summennorm scheidet aus: Bei allen gemessenen Ecken und σ nach DOMAIN §6 meldete sie in 17 bis 45 % der stimmigen Räume einen Konflikt, weil `T = 1 % U` nur 3,6° Winkelsumme entspricht.

**Ausdrückliche Bestätigung.** Sie ist genau bei `S > T` und `χ²(r; 0,95) < q² ≤ χ²(r; 0,99)` nötig. `AcceptAdjustment` trägt die gegebene Bestätigung, ohne sie folgt `EXPLICIT_CONFIRMATION_REQUIRED`.

**Rang am Umriss.** Eine Diagonale zwischen benachbarten Ecken wiederholt eine Wand. Sie ist an offenen Prüfpunkten scheinbar unabhängig, auf dem geschlossenen Umriss aber abhängig. Sie zählt deshalb in `m`, nie in einen Rang, und hat im Ausgleich die lineare Bedingung `L_i − D_k = 0`.

**Drehrichtungen.** Ein bestimmter Raum, dessen einzige gültige Lösung eine Drehrichtung verletzt, endet in `CONFLICT(TURN_DIRECTION_VIOLATED)`. Bei `missing > 0` bleibt die Form ein Vorschlag.

**Statistische Restgrenzen** (Prototyp, σ nach DOMAIN §6):

| Größe | Ergebnis |
|---|---|
| Falsch-Konflikt-Rate, 13 Szenarien | 0,00 bis 0,93 % |
| Wandfehler 3 T erkannt | 72 von 77 Wandfällen mindestens 95 % |
| Nicht erkennbar | Messungen ohne Gegenprobe (`r_i = 0`) mit 0 bis 1 %, `r_i = 0,00088` mit rund 75 % |
| Unerkanntes Band ohne Rauschen, nur Wandfehler | bis 1,10 T (L) bis 1,57 T (Kreuz) |
| Ausdrückliche Bestätigung bei stimmigen Räumen, alle Ecken gemessen | Rechteck 3,62 %, L 3,83 %, U 3,78 %, Kreuz 3,98 % |
| Große Korrektur mit einfacher Bestätigung, gleiche Räume | Rechteck 11,88 %, L 23,79 %, U 27,12 %, Kreuz 39,92 % |

Messungen mit `λ(3T) < λ₀(r)` kann keine Regel zuverlässig prüfen. Die Auswertung weist sie deshalb samt empfohlenem Zusatzmaß aus, ohne den Status zu ändern. `CONFIRMED` bleibt möglich, weil sonst jeder minimal bestimmte Raum diesen Status verlöre. Oberhalb der Tabelle ist `λ₀` eine Näherung mit 95,4 bis 96,2 % Trennschärfe (geprüft für `r` bis 400), also konservativ.

## Ergänzung T-ARC-007

**Einfachheit im exakten Zweig.** Auch `e ≤ EXACT_CLOSURE_MM` prüft DOMAIN §7. Der Umriss 2000, 2000, 1000, 3000, 1000, 1000 mit den Richtungen L L L R R schließt mit Lücke 0, kreuzt sich aber selbst und endet in `CONFLICT(NO_SIMPLE_SHAPE)`.

**`NoSimpleShape` ist kein Endstatus.** Ob eine Bedingung erst am geschlossenen Umriss abhängig wird, ist ohne Lösung nicht entscheidbar. Jedes `NoSimpleShape` aus A1 oder B1 läuft deshalb nach B2 mit Start bei den Sollwinkeln, und erst B3 entscheidet den Konflikt.

**Drehrichtung.** Eine Verletzung gilt erst bei `d · wrap(τ) < −2°`. Bei `α = 180°` hat die Ecke keine Richtung.

**Korrekturen im Vorschlag.** `Proposed` trägt die Rohlängen und bei abweichenden Längen verpflichtend `CorrectionSummary`. `measurementControl` ist bei `NeedsAdjustment` Pflichtfeld.

**Korrekturen aus der systematischen Prüfung.** Der Prototyp fand vier weitere Ablauffehler:

| Fehler | Korrektur |
|---|---|
| Der Ausgleich zog Variablen ohne Beobachtung zum Sollwert zurück und divergierte | Bezug `x̃_k` in DOMAIN §6 |
| Die Startform ignorierte abhängige Bedingungen und wählte den falschen Lösungsast | Startverfeinerung und Auswahl nach Residuum (§5.3) |
| Der Solver verließ einen bereits einfachen Umriss | Schrittannahme in §5.2, Richtungsstart S4 |
| `s` aus Sollwerten kann vom Umlaufsinn der Form abweichen | `s_F` in DOMAIN §3 und §8 |

**Prüfstand** (Prototyp, Seed 20261124, 12 000 Fälle). Ehrliche Eingaben (K1a, deklariert nur an echten 90°-Ecken): 2 400 Fälle ohne Abweichung. Bei deklarierten Ecken, die in Wahrheit nicht 90° haben (K1b, 2 400 Fälle), bleiben 17 falsche Konflikte und 22 bestätigte Formen, die alle Eingaben erfüllen, aber von der wahren Form abweichen. In K3 (2 400 Fälle) enden 6 stimmige Räume in `CONFLICT`. In einer Stichprobe aus je 1 000 Fällen von K1b und K3 konvergierte der Ausgleich bei allen 12 verbliebenen Konflikten aus der wahren Form. Das spricht für Grenzen der lokalen Suche. Der Prototyp bricht den Solver bei einem Gradienten von 1e-7 ab, nicht bei `SOLVER_GRADIENT = 1e-9`.

**Offen.** Der Rang an der Lösung erkennt keine zweite diskrete Lösung. Die Aussage „Drehrichtungen wählen die Lösung“ (DOMAIN §5.4) gilt daher nicht für jede Eingabe, vor allem nicht bei freier Schlussecke (19 der 22 Fälle). Die Behandlung braucht eine eigene Entscheidung.
