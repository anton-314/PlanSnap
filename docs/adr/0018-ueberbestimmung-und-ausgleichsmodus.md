# ADR 0018: Überbestimmung und Ausgleichsmodus

**Status:** Angenommen, 2026-11-23, ergänzt durch T-ARC-006, T-ARC-007, T-ARC-008, ADR 0019 und CR-W1-01

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
| Unerkanntes Band ohne Rauschen, nur Wandfehler | bis 1,10 T (L) bis 1,57 T (Kreuz), nicht reproduzierbar, korrigiert in Ergänzung CR-W1-01 |
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

## Ergänzung T-ARC-008

**Kontext.** Der offene Punkt aus T-ARC-007: Ein bestimmter Raum (`missing = 0`) kann mehrere diskrete Lösungen haben, die alle Eingaben und Drehrichtungen erfüllen. Die Auswahl nach DOMAIN §5.3 zeigt dann eine davon als `CONFIRMED`, obwohl der gemessene Raum die andere sein kann.

**Optionen.**

- **A:** Nichts ändern, nur dokumentieren. Bestätigt falsche Formen ohne Hinweis.
- **B:** Mehrdeutigkeit nur bei freier Schlussecke annehmen. Übersieht Fälle mit gemessener Schlussecke und markiert eindeutige Räume pauschal.
- **C:** Die übrigen gültigen Solver-Ergebnisse mit der angezeigten Form vergleichen. Weicht eine um mehr als `T` ab, wird der Raum ein Vorschlag mit Grund und trennendem Zusatzmaß.

**Entscheidung.** Option C (DOMAIN §5.6).

- `SolverOutcome.Solved.alternatives` liefert die übrigen gültigen Ergebnisse in Auswahlreihenfolge, nur mit eingehaltenen Drehrichtungen und allen harten Bedingungen auf den Rohlängen bis `EXACT_CLOSURE_MM`.
- `shapeDistanceMm` ist der größte Eckenabstand nach Verschiebung auf den Eckenschwerpunkt und optimaler Drehung, geschlossen berechnet und unabhängig vom lokalen Ursprung. Spiegelungen werden nicht ausgerichtet.
- `RoomEvaluation.Proposed` trägt `ProposalReason`. `UNDERDETERMINED` gilt genau bei `missing > 0`, `AMBIGUOUS_SHAPE` genau bei `missing = 0` und dann mit `ShapeAmbiguity`. `Proposed.recommendation` nennt das Zusatzmaß: zuerst die freie Schlussecke, wenn sie beide Formen um mehr als `T` als Bogen unterscheidet, sonst die nicht gemessene Diagonale mit dem größten Längenunterschied, Gleichstand nach kleinstem `(a, b)`.
- Die Prüfung läuft in A4 und B7 (DOMAIN §5.5). Im Ausgleichspfad gilt sie auch für Räume, die sonst `NEEDS_ADJUSTMENT` würden. Der Vorschlag trägt dann die Pflicht-Korrekturangaben wie in B7, aber kein `AdjustmentProposal`. `AcceptAdjustment` verlangt die aktuelle Auswertung `NeedsAdjustment` mit gleichem Vorschlag und lehnt sonst mit `ADJUSTMENT_NOT_AVAILABLE` ab. Geprüft wird in der Vorbedingung unter dem Mutex der Historie mit der Toleranz des laufenden `execute` (ADR 0019). Ein solcher Raum wird durch Bestätigung also nie `ADJUSTED`.
- Der Messfehlerpfad (`e ≤ T`) hat keinen Solver-Lauf und keine Alternativen. Er bleibt unverändert.

**Suchgrenze.** Die Prüfung sieht nur, was die festen Startwerte aus DOMAIN §5.3 finden. Sie zählt keine Lösungen vollständig auf. `CONFIRMED` heißt daher „keine zweite gültige Form weiter als `T` gefunden“, nicht „eindeutig“. Die Suche wird bewusst nicht weiter ausgebaut. Die verbleibende Quote wird am Produktionscode über den portierten Prüfstand verfolgt (T-GEO-005).

**Prüfstand mit Regel** (Prototyp in `docs/research/evaluation-prototype/`, Seed 20261124, 12 000 Fälle, Vergleich mit der Wahrheit über den Formabstand):

| Klasse | Status | Abweichungen |
|---|---|---|
| K1a, 2 400 | 2 017 `CONFIRMED`, 383 `NEEDS_ADJUSTMENT` | keine |
| K1b, 2 400 | 1 510 `CONFIRMED`, 184 `NEEDS_ADJUSTMENT`, 689 `PROPOSED`, 17 `CONFLICT` | 17 falsche Konflikte, 2 bestätigte Formen weiter als `T` von der Wahrheit |
| K2, 2 400 | 21 `CONFIRMED`, 16 `NEEDS_ADJUSTMENT`, 1 491 `PROPOSED`, 872 `CONFLICT` | keine |
| K3, 2 400 | 2 393 `PROPOSED`, 1 `NEEDS_ADJUSTMENT`, 6 `CONFLICT` | keine falsche Bestätigung |
| K4, 2 400 | 2 007 `CONFIRMED`, 393 `NEEDS_ADJUSTMENT` | keine |

In K1b wurden 42 Räume `PROPOSED(AMBIGUOUS_SHAPE)`: 15 in A4, 22 in B7 statt B8 und 5 in B7 statt B9. Ohne Regel wären 20 Formen bestätigt, die weiter als `T` von der Wahrheit liegen. Davon erkennt die Regel 18, 2 bleiben an der Suchgrenze. Dafür werden 19 richtig bestätigte Räume (0,8 %) und 1 richtiger Ausgleichsfall zum Vorschlag, weil auch bei ihnen eine zweite gültige Form existiert. Die Zahl 22 aus T-ARC-007 beruhte auf dem Eckenabstand im lokalen System, nicht auf dem ausgerichteten Formabstand. In K2 wurde 1 Raum mehrdeutig, in K1a, K3 und K4 keiner.

**Konsequenzen.** Die Sollwerte für T-GEO-005 stehen in `docs/research/evaluation-prototype/README.md`. `OverdeterminedCases` enthält drei Referenzfälle: angezeigte Form falsch mit freier Schlussecke, angezeigte Form richtig mit Diagonalempfehlung und den Ausgleichspfad. Der Prototyp liefert für alle 30 Katalogfälle Status und Grund wie erwartet.

## Ergänzung CR-W1-01

**Kontext.** T-GEO-006 hat den Ausgleich portiert und die Restgrenzen am Produktionscode nachgerechnet. Offen waren die Konvergenznorm, der Gleichstand bei `worstObservation` und zwei Tabellenzeilen, die der Produktionscode nicht traf. Das Skript der Tabelle „Statistische Restgrenzen“ liegt nicht im Repository.

**Konvergenznorm.** `‖C‖_∞` aus DOMAIN §6 wird in mm gemessen: `max(|(c_x, c_y)|, |c_τ| · U / (2π), max_k |c_k|)`. Die Grenze wird vor jedem Schritt geprüft, auch an `x_1`.

- Die Schließung zählt als euklidische Lücke. Auch `g` (DOMAIN §4) und die strukturelle Gültigkeit (§6) messen die Lücke als Vektor. Komponentenweise blieben bis `√2 · SOLVER_RESIDUAL_MM`, im Katalog von T-GEO-006 eine Lücke von 0,0128 mm über dem Kriterium von 0,01 mm.
- Die Winkelsummenzeile zählt als Bogen. In Radiant gegen 0,01 verglichen, endet der Ausgleich an `x_1`, wenn die Lücke schon passt und nur die Winkelsumme abweicht. Beispiel, am Produktionscode nachgestellt: Rechteck 4000, 3000, 4000, 3000, alle Ecken gemessen, `V_1` bis `V_3` mit 90°, `V_0` mit 89,7°. `e = a = 11,67 mm ≤ T`, also Messfehlerpfad mit Start bei den Beobachtungen. Die Lücke ist 0, `|c_τ| = 0,0052 rad ≤ 0,01`. Der Ausgleich endet ohne Schritt mit `S = 0` und einer Winkelsumme von 360,3°. Als Bogen ist `|c_τ| = 11,67 mm`, und der Fehler wird verteilt.
- Verworfen: komponentenweise Norm und Winkelsumme in Radiant. Der Prototyp (`flow.py`, `adjust`) nutzt beides und weicht hier vom Vertrag ab.

**Gleichstand bei `worstObservation`.** Normierte Residuen, die sich um höchstens `SOLVER_RESIDUAL_MM / SIGMA_LENGTH_MM` unterscheiden, gelten als gleich, danach gilt die Reihenfolge von `largestCorrection`. Die Iteration legt Längen nur bis `SOLVER_RESIDUAL_MM` fest, `SIGMA_LENGTH_MM` ist das kleinste σ in mm. Feiner aufgelöst hinge die Wahl von Rundungsresten ab.

**Abgleich der Restgrenzen.** Die Zeilen werden so definiert und am Produktionscode in `ClosureAdjusterSimulationTest` gehalten:

- Räume sind die wahren Formen der NH3-Fälle aus `OverdeterminedCases`: Rechteck 4000 × 3000, L mit `U = 20 m`, U mit `U = 25 m`, Kreuz aus 12 × 2000 mm. Alle Ecken sind als 90° gemessen, `r = 3`, `T` ist der Standard.
- Rauschen ist normalverteilt mit σ nach DOMAIN §6 und wird auf ganze mm und Milligrad gerundet.
- Jede Stichprobe durchläuft die Verzweigung aus DOMAIN §4. Bei `e ≤ T` gilt der Messfehlerpfad mit einfacher Bestätigung und ohne χ²-Test. Nur bei `e > T` folgt Zweig B. Raten beziehen sich auf alle Stichproben.
- „Große Korrektur mit einfacher Bestätigung“ ist B9 mit `S > T` und `q² ≤ χ²(3; 0,95)`.
- „Unerkanntes Band“ ist ohne Rauschen der größte Fehler einer Wand in Vielfachen von `T`, auf mm gerundet, mit beiden Vorzeichen, der noch nicht zu `CONFLICT` führt. Angegeben ist das Maximum über alle Wände.

| Größe | ADR | Prototyp `flow.evaluate`, 30 000 je Raum | Produktion, 40 000 je Raum |
|---|---|---|---|
| Große Korrektur mit einfacher Bestätigung | 11,88 / 23,79 / 27,12 / 39,92 % | 12,03 / 24,05 / 26,66 / 40,46 % | 12,52 / 23,81 / 26,71 / 40,17 % |
| Ausdrückliche Bestätigung | 3,62 / 3,83 / 3,78 / 3,98 % | 3,47 / 3,76 / 3,73 / 3,89 % | 3,57 / 3,56 / 3,59 / 3,80 % |
| Falsche Konflikte | 0,00 bis 0,93 % (13 Szenarien) | 0,97 / 1,01 / 1,00 / 1,07 % | 0,93 / 0,97 / 0,94 / 1,00 % |
| Unerkanntes Band | 1,10 T (L) bis 1,57 T (Kreuz) | 1,689 / 1,728 / 1,494 / 1,635 T | 1,689 / 1,728 / 1,494 / 1,635 T |

Reihenfolge jeweils Rechteck, L, U, Kreuz.

- **Große Korrektur:** Die ADR-Werte gelten. Die Abweichung aus T-GEO-006 (19,3 / 33,8 / 46,2 / 63,4 %) stammte aus einem Test, der jede Stichprobe durch Zweig B schickte, auch bei `e ≤ T`. Der Prototyp liefert mit dieser Definition dieselben Werte (18,27 / 33,95 / 46,20 / 63,71 %). Der Messfehlerpfad nimmt 54 bis 72 % der Stichproben auf.
- **Unerkanntes Band:** Die ADR-Zeile ist nicht reproduzierbar. Produktion, Prototyp und die lineare Näherung `σ_L · √(χ²(3; 0,99) / r_i) / T` an der wahren Form stimmen überein. Die ADR-Werte passen weder zum Maximum noch zum Minimum über die Wände (Minimum 1,27 / 1,14 / 1,15 / 1,64 T). Es gilt: **größter unerkannter Wandfehler 1,49 T (U) bis 1,73 T (L)**. Die Aussage zur Erkennung von 3 T bleibt unberührt.
- Die übrigen Zeilen der Tabelle (13 Szenarien, 77 Wandfälle) sind ohne Skript nicht nachrechenbar und bleiben stehen. Die nachgerechnete Teilmenge widerspricht ihnen nicht.

**Konsequenzen.** `DefaultClosureAdjuster` misst die Winkelsummenzeile noch in Radiant und braucht eine Korrektur samt Regressionstest mit dem Rechteck oben. Bis dahin weicht der Produktionscode in diesem Punkt vom Vertrag ab. Der README des Prototyps soll die Norm unter „Bekannte Abweichungen vom Vertrag“ aufführen.
