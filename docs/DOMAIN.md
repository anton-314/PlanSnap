# PlanSnap – Domäne und Geometrie

Bindend für `:core:common`, `:core:geometry`, `:core:model` und `:core:domain`.

## 1. Einheiten und Toleranzen

Rohlängen sind `Length(mm: Long)`, Rohwinkel `Angle(milliDegrees: Int)`, berechnete Koordinaten `Double` in mm (ADR 0002). Alle Konstanten liegen zentral in `GeometryTolerances`. Gleitkommazahlen werden nie mit `==` verglichen.

| Konstante | Wert | Bedeutung |
|---|---|---|
| `MIN_WALL_MM` | 10 | Kleinste Wandlänge |
| `MAX_WALL_MM` | 100 000 | Größte Wandlänge |
| `MAX_THICKNESS_MM` | 1 000 | Größte Wandstärke, 0 ist erlaubt |
| `DEFAULT_THICKNESS_MM` | 120 | Erstwert, danach zuletzt genutzte Stärke |
| `MAX_WALLS_PER_ROOM` | 128 | Obergrenze je Raum |
| `EXACT_CLOSURE_MM` | 1,0 | Bis hier gilt ein Umriss als exakt geschlossen |
| `SOLVER_RESIDUAL_MM` | 0,01 | Erlaubter Restfehler nach dem Lösen |
| `SOLVER_GRADIENT` | 1e-9 | Abbruch des projizierten Gradienten |
| `SOLVER_MAX_ITERATIONS` | 200 | Iterationen je Startwert |
| `RANK_TOLERANCE` | 1e-9 | Relative Pivot-Schwelle der Rangbestimmung |
| `RANK_PROBE_RAD` | 0,1 | Skala des festen Prüfpunkts der Rangbestimmung (§5.4) |
| `MIN_FEATURE_MM` | 1,0 | Mindestabstand nicht benachbarter Kanten |
| `COST_TIE` | 1e-9 | Kostengleichheit bei der Lösungsauswahl |
| `DEFAULT_TOLERANCE_ABS_MM` | 50 | Grenze Messfehler, absolut |
| `DEFAULT_TOLERANCE_REL` | 0,01 | Grenze Messfehler, relativ zum Umfang |
| `MIN_TOLERANCE_ABS_MM` | 1 | Kleinste zulässige Grenze Messfehler, absolut |
| `SOLVER_REGULARIZATION` | 1e-8 | Regularisierung `μ` der Hesse-Matrix (§5.2) |
| `SOLUTION_ROUNDING_RAD` | 1e-6 | Rundung für den lexikografischen Vergleich (§5.3) |
| `ADJUSTMENT_MAX_ITERATIONS` | 50 | Schritte des Ausgleichs (§6) |
| `SIGMA_LENGTH_MM` | 5,0 | Standardabweichung Wandlänge (§6) |
| `SIGMA_DIAGONAL_MM` | 5,0 | Standardabweichung Diagonale (§6) |
| `SIGMA_RIGHT_ANGLE_DEG` | 0,5 | Standardabweichung deklarierter rechter Winkel (§6) |
| `SIGMA_MEASURED_ANGLE_DEG` | 1,0 | Standardabweichung gemessener Winkel (§6) |
| `SIGMA_FREE_RAD` | 10,0 | Festes σ einer Variable ohne Beobachtung (§6) |
| `RANK_SOLUTION_RESIDUAL_FACTOR` | 10,0 | Faktor der Rangschwelle an einer Lösung (§5.4) |
| `CONFLICT_CONFIDENCE` | 0,99 | Niveau des χ²-Tests für `CONFLICT` (§5.5) |
| `EXPLICIT_CONFIRMATION_CONFIDENCE` | 0,95 | Niveau des χ²-Tests für die ausdrückliche Bestätigung (§5.5) |
| `CONTROL_POWER` | 0,95 | Trennschärfe einer kontrollierbaren Messung (§5.5) |
| `CONTROL_ERROR_FACTOR` | 3,0 | Fehler in Vielfachen von `T` für die Kontrollierbarkeit (§5.5) |
| `CHI_SQUARE_TABLE_MAX_REDUNDANCY` | 30 | Größte Redundanz der festen Tabellen (§5.5) |
| `NORMAL_QUANTILE_95` | 1,6448536269514722 | Normalquantil zu 0,95 (§5.5) |
| `NORMAL_QUANTILE_99` | 2,3263478740408408 | Normalquantil zu 0,99 (§5.5) |
| `TURN_DIRECTION_TOLERANCE_DEG` | 2,0 | Toleranz `ε` des Drehrichtungstests (§3) |
| `START_REFINEMENT_MAX_ITERATIONS` | 60 | Schritte der Startverfeinerung (§5.3) |
| `START_REFINEMENT_MAX_TRIALS` | 12 | Dämpfungsversuche je Schritt (§5.3) |
| `START_REFINEMENT_INITIAL_DAMPING` | 1e-3 | Startwert der Dämpfung (§5.3) |
| `START_REFINEMENT_MIN_DAMPING` | 1e-12 | Kleinste Dämpfung (§5.3) |
| `START_REFINEMENT_DAMPING_DECREASE` | 3,0 | Teiler nach einem angenommenen Schritt (§5.3) |
| `START_REFINEMENT_DAMPING_INCREASE` | 4,0 | Faktor nach einem abgelehnten Versuch (§5.3) |
| `START_REFINEMENT_GRADIENT` | 1e-13 | Abbruch der Startverfeinerung (§5.3) |
| `NEAR_COLLINEAR_SIN` | 1e-6 | Grenze für fast kollineare Ecken (§8) |
| `MITER_LIMIT_FACTOR` | 4,0 | Gehrungsgrenze als Vielfaches der Wandstärke (§8) |
| `HIT_TIE_RELATIVE` | 1e-9 | Relative Gleichstandsgrenze für Abstände und Flächen im Hit-Testing (§11) |

**Toleranz `ClosureTolerance(absoluteMm, relative)`:** `T_abs = absoluteMm` ist eine ganze Zahl mit `T_abs ≥ MIN_TOLERANCE_ABS_MM`, `T_rel = relative` liegt in `[0, 1]`. Ein Verstoß wirft `IllegalArgumentException` im Konstruktor. Damit gilt `T = max(T_abs, T_rel · U) ≥ 1 mm > 0` für jeden Umfang, auch bei `T_rel = 0`. Jeder Verbraucher von `T` darf sich darauf verlassen, etwa `ShapeAmbiguity.limitMm > 0` (§5.6) und die Kontrollierbarkeit mit `CONTROL_ERROR_FACTOR · T` (§5.5). Einstellungen mit kleinerem Wert sind ungültig und fallen auf den Standard zurück (`SettingsRepository`).

## 2. Koordinaten

- **Raum lokal:** Ecke `V0` liegt im Ursprung, Wand `w0` zeigt nach +x. y zeigt nach oben.
- **Welt:** `p_welt = R(rotation) · p_lokal + origin` mit der Platzierung des Raums.
- **Bildschirm:** nur über `Viewport` oder `PlotLayout`.

## 3. Raummodell

Ein Raum hat Wände `w_0 … w_{n-1}` und Ecken `V_0 … V_{n-1}`. Wand `w_i` läuft von `V_i` nach `V_{i+1 mod n}`. Ecke `V_i` liegt zwischen `w_{i-1}` und `w_i`. `V_0` ist die **Schlussecke**.

```kotlin
sealed interface CornerSpec {
    val direction: TurnDirection?
    data class DeclaredRight(override val direction: TurnDirection) : CornerSpec
    data class Measured(override val direction: TurnDirection, val angle: Angle) : CornerSpec
    data object Unspecified : CornerSpec { override val direction: TurnDirection? = null }
}
data class Diagonal(val id: DiagonalId, val fromCorner: Int, val toCorner: Int, val length: Length)
```

**Winkelkonvention:** Eingegeben werden Richtung (links oder rechts) und der Winkel `α` zwischen den beiden Wänden, `0° < α ≤ 180°`. Die Drehung ist `τ = d · (180° − α)` mit `d = +1` für links und `d = −1` für rechts. So ist die Eingabe auch vor dem Schließen eindeutig. `DeclaredRight` entspricht `α = 90°`. Nach dem Schließen zeigt die App den **Innenwinkel** `180° − s · τ` (Werte über 180° sind einspringende Ecken).

Heading: `φ_0 = 0`, `φ_i = φ_{i-1} + τ_i` für `i = 1 … n−1`. Die Schlussdrehung ist `τ_0 = 2π·s − Σ_{i≥1} τ_i`.

**Umlaufsinn:** `s = +1`, wenn `Σ_{i≥1} τ̂_i ≥ 0`, sonst `s = −1`. Dabei ist `τ̂_i` der Sollwert aus der Eingabe.

Die Schlussecke ist standardmäßig `Unspecified` und darf als `Measured` erfasst werden. `DeclaredRight` ist an ihr ebenfalls erlaubt. **Invariante von `Room`:** `Unspecified` ist nur an `V_0` zulässig. Jede Ecke `i ≥ 1` ist `DeclaredRight` oder `Measured`, sodass `τ̂_i` immer definiert ist.

**Drehrichtungstest:** Eine Ecke mit Richtung `d` verletzt diese Richtung genau bei `d · wrap(τ) < −ε` mit `ε = TURN_DIRECTION_TOLERANCE_DEG`. Eine gemessene Ecke mit `α = 180°` hat keine Richtung und verletzt nie. `Unspecified` hat ebenfalls keine Richtung. Der Test gilt überall gleich, auch bei der Lösungsauswahl (§5.3).

**Umlaufsinn der Form:** `s` aus den Sollwerten dient nur der Rechnung. Für eine geschlossene Form gilt `s_F`, das Vorzeichen ihrer Fläche. Innenwinkel (`180° − s_F · wrap(τ)`) und äußere Normalen (§8) verwenden `s_F`.

**Sollwert der Schlussecke:** Ist sie `Unspecified`, sei `r = 2π·s − Σ_{i≥1} τ̂_i`, normiert auf `(−π, π]`. `τ̂_0` ist der Wert aus `{−90°, 0°, +90°}` mit dem kleinsten zyklischen Abstand zu `r`. Bei Gleichstand gewinnt der Kandidat mit dem größten `s · τ̂_0`. Beispiele: `r = −45°`, `s = +1` ergibt 0°, `r = 180°` ergibt `s · 90°`. Die Rechnung erfolgt ganzzahlig in Milligrad, damit Gleichstände exakt erkannt werden.

## 4. Raumstatus

```kotlin
enum class ShapeStatus { OPEN, CONFIRMED, NEEDS_ADJUSTMENT, ADJUSTED, PROPOSED, CONFLICT }
```

| Status | Bedeutung | Im Export |
|---|---|---|
| `OPEN` | Weniger als 3 Wände oder nicht abgeschlossen | Nicht exportierbar, Warnung |
| `CONFIRMED` | Form bestimmt, exakt oder mit Korrekturen bis `EXACT_CLOSURE_MM` geschlossen, keine zweite gültige Form weiter als `T` gefunden (§5.6) | Normal |
| `NEEDS_ADJUSTMENT` | Korrektur der Messwerte wartet auf Bestätigung | „Form nicht bestätigt“ |
| `ADJUSTED` | Ausgleich bestätigt und zu den Rohwerten passend | Normal |
| `PROPOSED` | Form aus Vorschlag, ein Zusatzmaß ist nötig: Es fehlen Maße (`UNDERDETERMINED`) oder eine zweite gültige Form weicht um mehr als `T` ab (`AMBIGUOUS_SHAPE`, §5.6) | „Form nicht bestätigt“ |
| `CONFLICT` | Maße sind unmöglich oder widersprechen sich signifikant | Nicht exportierbar, Warnung |

### Auswertung `evaluate(room, tolerance)`

Die Schritte laufen in dieser Reihenfolge. Der erste zutreffende Schritt legt das Ergebnis fest, jeder Pfad endet in genau einem Status.

1. **Validieren.** Zuerst die Wandanzahl: mehr als `MAX_WALLS_PER_ROOM` Wände ergeben `RuleViolation(TOO_MANY_WALLS)`, wie bei `AddWall` (§11). Danach die Wertebereiche, ein Verstoß ergibt `InvalidValue`. Weniger als 3 Wände oder nicht abgeschlossen ergibt `OPEN`.
2. **Gespeichertes Overlay.** Hat der Raum ein Overlay mit passendem Fingerabdruck, das strukturell gültig ist (§6), folgt sofort `ADJUSTED` mit der Overlay-Form. Es wird nichts neu berechnet, `solverVersion` und Toleranz spielen keine Rolle. So macht ein App-Update aus `ADJUSTED` nie `CONFLICT`. Ein anderes Overlay wird ignoriert.
3. **Notwendige Bedingungen.** Ist `L_max ≥ Σ L_i − L_max − MIN_FEATURE_MM`, folgt `CONFLICT(WALL_TOO_LONG)`. Für jede Diagonale zwischen **nicht benachbarten** Ecken `a` und `b` teilt sich der Umriss in zwei Ketten. Für jede Kette mit Länge `C` und längstem Glied `M` muss `D ≤ C` und `D ≥ 2M − C` gelten, sonst `CONFLICT(DIAGONAL_IMPOSSIBLE)` mit der betroffenen Diagonale. Eine Diagonale zwischen benachbarten Ecken ist eine Wiederholungsmessung der Wand und wird über §5.5 und §6 geprüft.
4. **Sollform prüfen.** Mit allen Sollwinkeln `τ̂` den Zug ablaufen. Berechne:
   - `g = |Σ L_i (cos φ̂_i, sin φ̂_i)|`
   - `r_k = |d_k(τ̂) − D_k|` für alle Diagonalen
   - `a = |wrap(τ̂_0,fest − τ_0,abgeleitet)| · U / (2π)` mit `τ_0,abgeleitet = 2π·s − Σ_{i≥1} τ̂_i`, falls die Schlussecke festgelegt ist (`Measured` oder `DeclaredRight`), sonst 0
   - `e = max(g, max r_k, a)`
5. **Grenzwert:** `T = max(T_abs, T_rel · U)` mit `U = Σ L_i`. Nach §1 gilt immer `T > 0`.
6. **Verzweigung:**
   - `e ≤ EXACT_CLOSURE_MM`: Die Sollform wird nach §7 geprüft. Ist sie einfach, folgt `CONFIRMED` mit der Sollform, sonst `CONFLICT(NO_SIMPLE_SHAPE)`.
   - `e ≤ T` ist ein **Messfehler**. Der Ausgleich aus §6 läuft im Modus `MEASUREMENT_ERROR` mit Start bei den Beobachtungen. `NotConverged` ergibt `CONFLICT(ADJUSTMENT_NOT_CONVERGED)`, ein nicht einfacher Umriss `CONFLICT(NO_SIMPLE_SHAPE)`, sonst folgt `NEEDS_ADJUSTMENT` mit Vorschau und einfacher Bestätigung. Die Redundanz für die Kontrollierbarkeit (§5.5) ist hier `r = 2 + [Schlussecke festgelegt] + Anzahl der Diagonalen`.
   - `e > T` ist eine **Formabweichung** und läuft nach §5.5.

Deklarierte 90°-Ecken zählen als Form-Angabe, solange die Sollform innerhalb der Toleranz schließt. Bei einer Formabweichung werden sie zu weichen Sollwerten.

**Grenzfälle:** `e` genau gleich einem Grenzwert fällt in die strengere, also kleinere Klasse. 3 cm Lücke in einem Raum mit 14 m Umfang liegt unter `T = 140 mm` und ist ein Messfehler. Bei 3 × 3 × 3 × 5 m beträgt die Lücke 2 m und ist eine Formabweichung.

## 5. Solver für die Formabweichung

### 5.1 Problem

Variablen: `u = (τ_1 … τ_{n-1})`.

- Harte Bedingungen `c(u) = 0`:
  - Schließung: `c_x = Σ L_i cos φ_i`, `c_y = Σ L_i sin φ_i`
  - gemessene Ecke `i ≥ 1`: `τ_i − τ_i^m`
  - gemessene Schlussecke: `wrap(2π·s − Σ τ_i − τ_0^m)`
  - Diagonale `k`: `(d_k² − D_k²) / (2 D_k)`
- Ziel (REQ-MEAS-007): `f(u) = Σ_{i ∈ W} wrap(τ_i − τ̂_i)²`, wobei `W` alle nicht gemessenen Ecken einschließlich der Schlussecke enthält.

Gemessene Ecken werden als Variablen eliminiert. Abstandsbedingungen werden durch `U` geteilt, damit alle Zeilen dieselbe Größenordnung haben.

### 5.2 Iteration (Gleichungs-SQP)

Für jeden Startwert, höchstens `SOLVER_MAX_ITERATIONS` Schritte:

1. `g = ∇f`, `J = ∂c/∂u` analytisch, `H = ∇²f + μI` mit `μ = 1e-8`.
2. KKT-System lösen: `[H Jᵀ; J 0][δ; λ] = [−g; −c]` mit LU-Zerlegung und Spaltenpivotisierung. Bei Singularität `J` über die Rangbestimmung (§5.4) ausdünnen.
3. Schrittweite `t ∈ {1, ½, ¼ …}` mit mindestens `2⁻²⁰`, sodass die Merit-Funktion `m(u) = f(u) + ρ‖c(u)‖₁` sinkt. `ρ = max(1, 2‖λ‖_∞)`, und `ρ` wächst nur. Ist der aktuelle Punkt geschlossen (`‖c‖_∞ · U ≤ SOLVER_RESIDUAL_MM`) und einfach (§7), wird ein Schritt nur angenommen, wenn auch der neue Umriss einfach ist.
4. Abbruch, wenn `‖c‖_∞ · U ≤ SOLVER_RESIDUAL_MM` und `‖g + Jᵀλ‖_∞ ≤ SOLVER_GRADIENT`. Endet der Lauf ohne diesen Abbruch oder an einem nicht einfachen Punkt, gilt der letzte geschlossene und einfache Punkt, falls es einen gab.

Die Implementierung verwendet nur eigene Dense-Matrizen, keine Zufallszahlen und keine Abhängigkeit von Iterationsreihenfolgen unsortierter Sammlungen.

### 5.3 Startwerte und Auswahl

Feste Reihenfolge:

1. **S1:** Sollwerte `τ̂`.
2. **S2:** Mittel aus `τ̂` und `s · 2π/n`.
3. **S3:** Drehungen des **Sehnenpolygons** zu den Längen. Radius `R` per Bisektion aus `Σ 2·asin(L_i/(2R)) = 2π`. Liegt der Mittelpunkt außerhalb, wird der Bogen der längsten Seite durch `2π − 2·asin(L_max/(2R))` ersetzt.
4. **S4:** Richtungsstart `τ_i = d_i · β` für die Ecken aus `u_frei` mit `β = (2π·s − Σ_{i≥1, gemessen} τ̂_i − τ̂_0) / Σ_{i ∈ u_frei} d_i`. S4 entfällt, wenn der Nenner 0 ist oder nicht `0 < β < π` gilt.

Jeder Startwert läuft zweimal: direkt und nach der **Startverfeinerung**. Das ist ein Levenberg-Marquardt-Verfahren auf `u_frei` über alle Zeilen aus §5.4 in deren fester Reihenfolge. Je Schritt wird `(JᵀJ + δI) d = −Jᵀc` gelöst. Der Schritt wird angenommen, wenn `‖c‖₂` sinkt, dann gilt `δ = max(δ / 3, 1e-12)`. Ein abgelehnter oder nicht lösbarer Versuch setzt `δ = 4δ`. Start ist `δ = 1e-3`, höchstens 60 Schritte mit je 12 Versuchen, Abbruch bei `‖Jᵀc‖_∞ < 1e-13` oder ohne annehmbaren Versuch. Ist `I` kleiner als die Zeilenzahl, ist das verfeinerte Ergebnis selbst ein Kandidat, und auch das Solver-Ergebnis wird vor der Bewertung so verfeinert. Es dient dann nur als Linearisierungspunkt.

Gültig ist ein Ergebnis, wenn es konvergiert, der Umriss einfach ist (§7) und kein Kantenabstand unter `MIN_FEATURE_MM` liegt. Von den gültigen Ergebnissen gewinnt:

1. dasjenige, das alle deklarierten und gemessenen Drehrichtungen nach §3 einhält,
2. dann das kleinste Residuum der harten Bedingungen außerhalb von `I` in mm, auf ganze `EXACT_CLOSURE_MM` gerundet,
3. dann die kleinsten Kosten `f` (Gleichstand bei Differenz unter `COST_TIE`),
4. dann das lexikografisch kleinste `u`, auf 1e-6 rad gerundet.

Die übrigen gültigen Ergebnisse gibt der Solver in Auswahlreihenfolge als `alternatives` zurück, gefiltert nach §5.6. Ohne gültiges Ergebnis meldet der Solver `NoSimpleShape`. Das ist kein Endstatus, §5.5 entscheidet.

**Verbindlicher Referenzfall:** Längen 3000, 3000, 3000, 5000 mm mit drei Linksdrehungen ergeben Innenwinkel von `arccos(1/3)` ≈ 70,53° an beiden Ecken der 5-m-Wand, ≈ 109,47° an den beiden anderen und die Fläche `8√2` ≈ 11,314 m².

### 5.4 Bestimmtheit

Sei `u_frei` der Vektor der nach der Elimination gemessener Ecken verbleibenden Variablen, `v = |u_frei|`, und `J` die Jacobi-Matrix der verbleibenden harten Bedingungen (Schließung, gemessene Schlussecke, Diagonalen) nach `u_frei`. Ihre Anzahl ist `m = 2 + [Schlussecke gemessen] + Anzahl der Diagonalen`. Ränge werden per Gauß-Elimination mit vollständiger Pivotisierung bestimmt. Eine Diagonale zwischen benachbarten Ecken zählt in `m`, ihre Zeile aber nie in einen Rang, weil sie auf dem geschlossenen Umriss nur die Wand wiederholt.

**Vorabprüfung ohne Lösung (`analyzeConstraints`).** Sie läuft bei `e > T` vor dem Solver und dient **nur dem Routing**:

1. `ρ` ist der Rang von `J` an den Sollwinkeln `τ̂` mit der relativen Schwelle `RANK_TOLERANCE`.
2. Ist `ρ < m`, wird der Rang zusätzlich am festen Prüfpunkt bestimmt: `τ_i = τ̂_i + RANK_PROBE_RAD · i / n` für die Ecken aus `u_frei`, gemessene Ecken bleiben fest. Es zählt der größere Rang, bei Gleichstand `τ̂`. So gilt eine zufällige Singularität von `τ̂` nicht als Überbestimmung, strukturell abhängige Bedingungen bleiben aber erkannt.
3. Die **unabhängige Bedingungsmenge** `I` mit `|I| = ρ` entsteht an diesem Punkt durch Aufnahme in fester Reihenfolge, solange der Rang steigt: `c_x`, `c_y`, gemessene Schlussecke, dann die Diagonalen sortiert nach `(min(a,b), max(a,b), Länge, Eingabeindex)`.
4. Die Eingabe ist **überbestimmt**, wenn `m > ρ`. `ConstraintAnalysis` weist `v`, `m`, `I`, `ρ = |I|` und `missing = v − ρ` immer aus.

Beispiele: Viereck ohne Zusatzmaße `v = 3, m = 2, ρ = 2`, mit einer Diagonale `3, 3, 3`, jeweils nicht überbestimmt. Viereck mit zwei Diagonalen `3, 4, 3` und Raum mit lauter gemessenen Ecken `0, m ≥ 2, 0` sind überbestimmt. Fünfeck mit derselben Diagonale doppelt: `4, 4, 3`, also überbestimmt und trotzdem `missing = 1`.

**Bestimmtheit an der Lösung (`analyze`).** Nach dem Lösen oder Ausgleichen wird der Rang `ρ_L` an der geschlossenen Form erneut bestimmt, mit den wirksamen Längen und Drehungen und der Schwelle `max(RANK_TOLERANCE, RANK_SOLUTION_RESIDUAL_FACTOR · SOLVER_RESIDUAL_MM / U)`. **Nur dieser Rang ist maßgeblich.** Die Zahl fehlender Angaben ist `missing = v − ρ_L`. Für ein Viereck ohne Zusatzmaße gilt `missing = 3 − 2 = 1`.

- `missing = 0`: Die Form ist lokal bestimmt. Es kann trotzdem mehrere diskrete Lösungen geben, die alle Drehrichtungen einhalten. Diesen Fall behandelt §5.6.
- `missing > 0`: Status `PROPOSED` mit Grund `UNDERDETERMINED`.

**Empfehlung des Zusatzmaßes:** Unter allen Diagonalen zwischen nicht benachbarten Ecken, die den Rang erhöhen, wird die längste empfohlen. Gleichstand wird durch den kleinsten Index `(a, b)` entschieden. Für eine Ecke wird nur dann ein Winkel empfohlen, wenn keine Diagonale den Rang erhöht.

### 5.5 Entscheidungsablauf bei Formabweichung

Für `e > T` gilt dieser Ablauf (ADR 0018). `v`, `m`, `I` stammen aus `analyzeConstraints`.

Die Schritte laufen in dieser Reihenfolge, der erste zutreffende legt das Ergebnis fest.

**Routing:** `m = ρ` startet bei A1, `m > ρ` bei B1.

**Zweig A, nicht überbestimmt (`m = ρ`):**

- **A1 Solver** auf allen Bedingungen. `NoSimpleShape` führt zu **B2 mit Start bei den Sollwinkeln**.
- **A2 Rang.** `analyze` an der Lösung. `missing > 0` ergibt `PROPOSED(UNDERDETERMINED)` mit den Rohlängen, ohne Korrekturangaben.
- **A3 Drehrichtungen.** Bei `missing = 0` und verletzter Richtung (§3) folgt `CONFLICT(TURN_DIRECTION_VIOLATED)`.
- **A4 Mehrdeutigkeit.** §5.6 vergleicht die Lösung aus A1 mit den Alternativen aus A1. Eine Alternative weiter als `T` ergibt `PROPOSED(AMBIGUOUS_SHAPE)` mit den Rohlängen, ohne Korrekturangaben.
- **A5 Bestätigt.** Sonst folgt `CONFIRMED` mit `r = 0`.

**Zweig B, überbestimmt (`m > ρ`):**

- **B1 Startform.** Bei `v = 0` sind die Beobachtungen der Start. Sonst läuft der Solver auf `I`. `NoSimpleShape` führt zu **B2 mit Start bei den Sollwinkeln**.
- **B2 Ausgleich** aus §6 im Modus `SHAPE_DEVIATION`, `x_0` sind die Beobachtungen, die Startform ist nur der erste Linearisierungspunkt. Beim Start bei den Sollwinkeln ist die Drehung der freien Schlussecke `τ_0,abgeleitet`. `NotConverged` ergibt `CONFLICT(ADJUSTMENT_NOT_CONVERGED)` ohne Overlay.
- **B3 Einfachheit.** Ist der ausgeglichene Umriss nicht einfach oder hat er einen Kantenabstand unter `MIN_FEATURE_MM`, folgt `CONFLICT(NO_SIMPLE_SHAPE)`. Nur dieser Schritt entscheidet diesen Konflikt bei `e > T`.
- **B4 Rang.** `analyze` an der ausgeglichenen Form ergibt `ρ_L`, `missing = v − ρ_L` und die Redundanz `r = m − ρ_L`.
- **B5 Konflikttest.** Bei `r ≥ 1`, `S > T` **und** `q² > χ²(r; 0,99)` folgt `CONFLICT(MEASUREMENTS_INCONSISTENT)` unter Angabe von `worstObservation`.
- **B6 Drehrichtungen.** Bei `missing = 0` und verletzter Richtung (§3) folgt `CONFLICT(TURN_DIRECTION_VIOLATED)`.
- **B7 Vorschlag.** `missing > 0` ergibt `PROPOSED(UNDERDETERMINED)`. Eine verletzte Drehrichtung ist dort zulässig, weil die Form nur ein Vorschlag ist. Bei `missing = 0` vergleicht §5.6 die ausgeglichene Form mit den Alternativen aus B1. Eine Alternative weiter als `T` ergibt `PROPOSED(AMBIGUOUS_SHAPE)`, gleich ob sonst B8 oder B9 folgen würde. Beide Vorschläge zeigen die ausgeglichene Form und tragen **Pflicht-Korrekturangaben**: `S`, `q²` (nur bei `r ≥ 1` definiert), größte Einzelkorrektur und betroffene Messung.
- **B8 Bestätigt.** `maxCorrectionMm ≤ EXACT_CLOSURE_MM` ergibt `CONFIRMED`. Eine stimmige Zusatzmessung verschlechtert den Status also nicht.
- **B9 Ausgleich nötig.** Sonst folgt `NEEDS_ADJUSTMENT` mit Vorschau und `measurementControl`. Die **ausdrückliche Bestätigung** ist genau bei `r ≥ 1`, `S > T` und `χ²(r; 0,95) < q² ≤ χ²(r; 0,99)` nötig, sonst genügt die einfache. `S`, die größte Einzelkorrektur und die betroffene Messung stehen immer im Ergebnis.

Ob eine Bedingung erst am geschlossenen Umriss abhängig wird, lässt sich ohne Lösung nicht entscheiden. Deshalb führt **jedes** `NoSimpleShape` aus A1 oder B1 zu B2. Beispiele: Trapez 5000, 3000, 3000, 3000 mit `α = 109,471°` an `V_2` und `V_3` (B1 scheitert an der Rundung, B8 bestätigt) und Fünfeck 4000, 2864, 3106, 2802, 2915 mit `α_4 = 118,918°` und Diagonale (0,3) = 4924 (A1 scheitert, B7 schlägt vor).

Der Fall „alle Ecken gemessen, `e > T`“ hat `v = 0`, `ρ = 0` und `r = m`, läuft also immer durch Zweig B.

**Kenngrößen:**

- `S` ist die Summennorm der Korrekturen: `S = Σ |ΔL_i| + Σ |ΔD_k| + Σ |wrap(Δτ_i)| · U / (2π)` über alle Beobachtungen.
- `q² = Σ (v_i / σ_i)²` über dieselben Beobachtungen, mit `σ_i` aus §6.
- `χ²(r; p)` liefert `ChiSquareLimits.quantile`: feste Tabelle für `r = 1 … 30`, darüber Wilson-Hilferty `r · (1 − 2/(9r) + z_p · √(2/(9r)))³`.

**Bestätigung:** `AcceptAdjustment` ist nur zulässig, wenn die aktuelle Auswertung des Raums `NeedsAdjustment` mit gleichem `AdjustmentProposal` ist, sonst folgt `ADJUSTMENT_NOT_AVAILABLE`. „Aktuell“ heißt: ausgewertet in der Vorbedingung des Befehls unter dem Mutex der Historie, auf dem zuletzt festgeschriebenen Projekt und mit der Toleranz des laufenden `execute` (§11, ADR 0019). Das schließt einen veralteten Fingerabdruck, einen Vorschlag aus einer Auswertung mit anderer Toleranz und jeden Raum in `PROPOSED` ein, auch mit Korrekturangaben. Der Befehl nennt die tatsächlich gegebene Bestätigung. Verlangt der Vorschlag `EXPLICIT` und liegt nur `SIMPLE` vor, wird er mit `EXPLICIT_CONFIRMATION_REQUIRED` abgelehnt.

Beispiel (Prüfstand, ADR 0019): Rechteck 4150, 3000, 4000, 3000, alle Ecken als 90° gemessen, `U = 14 150`. Mit `T_abs = 250` ist `T = 250` und `e = 150 ≤ T`, es folgt `NEEDS_ADJUSTMENT` im Messfehlerpfad mit einfacher Bestätigung. Mit der Standardtoleranz ist `T = 141,5 < e`, Zweig B ergibt B9 mit `S = 223,94 > T`, `q² = 8,062` zwischen `χ²(3; 0,95)` und `χ²(3; 0,99)`, also ausdrückliche Bestätigung. Das Overlay ist in beiden Fällen gleich, die Vorschläge unterscheiden sich nur in `requiredConfirmation`. Ein unter `T_abs = 250` angezeigter einfacher Vorschlag wird nach dem Wechsel zur Standardtoleranz mit `ADJUSTMENT_NOT_AVAILABLE` abgelehnt.

**Kontrollierbarkeit (`analyzeControl`).** Sie läuft bei jedem bestimmten Raum (`missing = 0`) mit Ausgleich, der nicht `PROPOSED(AMBIGUOUS_SHAPE)` wird, und **ändert nie den Status**:

- `r_i` ist die Redundanzzahl der Beobachtung, `r_i = (Q Aᵀ (A Q Aᵀ)⁻¹ A Q)_ii / σ_i²` an der Lösung.
- `λ_i(3T) = (CONTROL_ERROR_FACTOR · T)² · r_i / σ_i²`. Für Winkel ist `σ_i` das mm-Äquivalent `σ · U / (2π)`.
- Eine Messung ist **nicht kontrollierbar**, wenn `λ_i(3T) < λ₀(r)`. `λ₀(r)` ist die Nichtzentralität, bei der der Konflikttest die Trennschärfe `CONTROL_POWER` erreicht: feste, aufgerundete Tabelle für `r = 1 … 30`, darüber `λ₀ = c − r + 2z² + √(4z⁴ + z²(4c − 2r))` mit `c = χ²(r; 0,99)` und `z = z_0,95`.
- **Empfohlenes Zusatzmaß je Messung:** Geprüft werden die noch nicht gemessenen Diagonalen zwischen nicht benachbarten Ecken, die längste zuerst, Gleichstand nach kleinstem `(a, b)`, danach die nicht gemessenen Ecken nach Index. Empfohlen wird das erste Maß, mit dem `λ_i(3T) ≥ λ₀(r + 1)` gilt. Gibt es keines, bleibt die Empfehlung leer.
- Bei `r = 0` hat keine Messung eine Gegenprobe. `MeasurementControl` meldet dann `r = 0` und keine Einzelmessungen.

### 5.6 Mehrdeutige Formen

Der Rang an der Lösung (§5.4) ist eine lokale Aussage. Auch ein bestimmter Raum (`missing = 0`) kann mehrere diskrete Lösungen haben, die alle Eingaben und Drehrichtungen erfüllen, vor allem bei freier Schlussecke. Die Auswahl nach §5.3 zeigt dann eine davon, nicht zwingend den gemessenen Raum. Dieser Abschnitt legt fest, wann das als Mehrdeutigkeit gilt (ADR 0018, Ergänzung T-ARC-008). Er gilt nur in A4 und B7.

1. **Alternativen.** `SolverOutcome.Solved.alternatives` aus A1 oder B1 enthält alle übrigen gültigen Ergebnisse nach §5.3, also aus allen Startwerten direkt und nach der Startverfeinerung, in Auswahlreihenfolge. Aufgenommen wird nur ein Ergebnis, das alle Drehrichtungen nach §3 einhält und mit den Rohlängen alle harten Bedingungen der Eingabe bis `EXACT_CLOSURE_MM` erfüllt: Schließung, gemessene Schlussecke als Bogen `|Δτ_0| · U / (2π)` und Diagonalen zwischen nicht benachbarten Ecken, auch außerhalb von `I`. Fast-Duplikate der gewählten Lösung sind zulässig. Ohne Solver-Ergebnis (`v = 0` oder `NoSimpleShape` in A1 oder B1) ist die Liste leer.
2. **Formabstand.** `shapeDistanceMm(F, Q)` vergleicht zwei Umrisse mit gleicher Eckenreihenfolge. Beide werden auf ihren Eckenschwerpunkt verschoben, `p_i` und `q_i` sind die verschobenen Ecken. `F` wird um `θ = atan2(Σ p_i × q_i, Σ p_i · q_i)` gedreht. Das ist die Drehung mit der kleinsten Summe `Σ |R(θ) p_i − q_i|²`. Der Abstand ist `max_i |R(θ) p_i − q_i|` in mm. Eine Spiegelung wird nicht ausgerichtet. Das Maß ist geschlossen berechnet, also deterministisch, und hängt weder vom lokalen Ursprung noch von Lage und Drehung eines der Umrisse ab.
3. **Prüfung.** `F` ist die angezeigte Form: in A4 die Lösung aus A1, in B7 die ausgeglichene Form mit den wirksamen Längen. Jede Alternative `Q` liegt auf den Rohlängen im lokalen System (§2). Mehrdeutig ist der Raum genau dann, wenn eine Alternative `shapeDistanceMm(F, Q) > T` hat. Ein Abstand genau gleich `T` zählt nicht (§4, Grenzfälle). Berichtet wird die Alternative mit dem größten Abstand, bei Gleichstand innerhalb `SOLVER_RESIDUAL_MM` die erste in Auswahlreihenfolge.
4. **Empfehlung.** Ist `V_0` `Unspecified` und gilt `|wrap(τ_0(F) − τ_0(Q))| · U / (2π) > T`, wird der Winkel an `V_0` empfohlen. Sonst wird unter den nicht gemessenen Diagonalen `(a, b)` mit `a < b` zwischen nicht benachbarten Ecken die mit dem größten Unterschied `||F_a F_b| − |Q_a Q_b||` empfohlen. Bei Gleichstand innerhalb `SOLVER_RESIDUAL_MM` gewinnt das kleinste `(a, b)` lexikografisch. Liegt der größte Unterschied höchstens bei `EXACT_CLOSURE_MM`, bleibt die Empfehlung leer. `U` ist die Summe der Rohlängen.
5. **Ergebnis.** `PROPOSED(AMBIGUOUS_SHAPE)` zeigt weiter `F`, mit `Determinacy` bei `missing = 0` und `ShapeAmbiguity` aus Alternative, Abstand, `T` und Empfehlung. `Proposed.recommendation` liefert die Empfehlung aus `ShapeAmbiguity`. Die Alternative wird nie automatisch gewählt. Invariante: `UNDERDETERMINED` genau bei `missing > 0`, `AMBIGUOUS_SHAPE` genau bei `missing = 0`.
6. **Ausgleichspfad.** In B7 gilt die Regel auch dann, wenn ohne sie B9 `NEEDS_ADJUSTMENT` folgen würde. Der Vorschlag trägt dieselben Pflicht-Korrekturangaben wie bei `UNDERDETERMINED` in B7, aber kein `AdjustmentProposal`. `AcceptAdjustment` wird mit `ADJUSTMENT_NOT_AVAILABLE` abgelehnt (§5.5, Bestätigung), sodass der Raum durch Bestätigung nie `ADJUSTED` wird. Erst eine geänderte Eingabe, etwa das empfohlene Zusatzmaß, ändert den Status. Ein schon gespeichertes Overlay mit passendem Fingerabdruck bleibt nach §4 Schritt 2 `ADJUSTED`.
7. **Messfehlerpfad.** Der Ausgleich bei `e ≤ T` (§4 Schritt 6) läuft ohne Solver, hat also keine Alternativen, und bleibt unverändert.
8. **Grenze.** Die Suche ist endlich: feste Startwerte nach §5.3, keine Aufzählung aller Lösungen. Eine leere Liste beweist keine Eindeutigkeit, `CONFIRMED` heißt nur, dass keine zweite gültige Form weiter als `T` gefunden wurde. Die gemessene Restquote steht in ADR 0018.

Die Prüfung läuft nach dem Konflikttest und der Drehrichtungsprüfung. Ein Konflikt geht also immer vor. `analyzeControl` entfällt bei einem Vorschlag, weil `Proposed` keine Kontrollierbarkeit trägt.

## 6. Schließungsausgleich

Methode: gewichtete kleinste Quadrate (ADR 0005). Beobachtungen und Standardabweichungen:

| Beobachtung | σ |
|---|---|
| Wandlänge | 5 mm |
| Diagonale | 5 mm |
| Deklarierter rechter Winkel | 0,5° |
| Gemessener Winkel | 1,0° |

Variablen `x = (L_0…L_{n-1}, τ_0…τ_{n-1}, D_k)`. `x_0` sind immer die Beobachtungen. Eine nicht festgelegte Schlussecke hat keine Beobachtung, ihr Wert in `x_0` ist `τ_0,abgeleitet`. Der erste Linearisierungspunkt `x_1` ist `x_0` mit den Drehungen aus dem Parameter `startTurnsRad` (§5.5). Bedingungen `C(x) = 0`: Schließung, `wrap(Σ τ_i − 2π·s) = 0` und Diagonalen. Eine Diagonale zwischen benachbarten Ecken hat die lineare Bedingung `L_i − D_k = 0` mit der Wand `w_i` zwischen beiden Ecken.

Iteration (Gauss-Helmert) ab `k = 1`: `x_{k+1} = x̃_k − Q Aᵀ (A Q Aᵀ)⁻¹ (C(x_k) + A (x̃_k − x_k))` mit `A = ∂C/∂x` an `x_k` und `Q = diag(σ²)`, Winkel in Radiant. `x̃_k` ist `x_0` für beobachtete Variablen und `x_k` für Variablen ohne Beobachtung, die so nicht zum Sollwert zurückgezogen werden. Eine Variable ohne Beobachtung erhält ein festes großes `σ = 10 rad`. Höchstens 50 Schritte, Abbruch bei `‖C‖_∞ ≤ SOLVER_RESIDUAL_MM`, geprüft vor jedem Schritt, also auch an `x_1`. Die Norm ist in mm (ADR 0018, Ergänzung CR-W1-01): `‖C‖_∞ = max(|(c_x, c_y)|, |c_τ| · U / (2π), max_k |c_k|)`. Die Schließung zählt als euklidische Lücke, nicht komponentenweise, damit die Lücke selbst höchstens `SOLVER_RESIDUAL_MM` beträgt. Die Winkelsummenzeile `c_τ = wrap(Σ τ_i − 2π·s)` zählt als Bogen mit `U` der Rohlängen, die Diagonalzeilen `c_k` direkt in mm. Wird die Grenze nicht erreicht, ist das Ergebnis `NotConverged`. Das führt in jedem Pfad zu `CONFLICT(ADJUSTMENT_NOT_CONVERGED)`, es entsteht kein Overlay.

**Modus (ADR 0018):** `MEASUREMENT_ERROR` gilt für `e ≤ T`, deklarierte rechte Winkel sind dort Beobachtungen. `SHAPE_DEVIATION` gilt für Zweig B aus §5.5. Deklarierte rechte Winkel sind dort nur Form-Angaben (§4) und haben keine Beobachtung.

**Größte Korrektur:** `maxCorrectionMm = max(|ΔL_i|, |ΔD_k|, |wrap(Δτ_i)| · U / (2π))` über alle Beobachtungen, mit `U = Σ L_i` der Rohlängen. Ecken ohne Beobachtung, also die freie Schlussecke und im Modus `SHAPE_DEVIATION` deklarierte rechte Winkel, zählen weder hier noch in `S`, `q²`, den Redundanzzahlen oder als `worstObservation`. `largestCorrection` nennt die Beobachtung mit der größten mm-äquivalenten Korrektur, bei Gleichstand innerhalb `SOLVER_RESIDUAL_MM` die erste in der Reihenfolge Wände, Ecken, Diagonalen und nach kleinstem Index. Liegen alle Korrekturen höchstens bei `SOLVER_RESIDUAL_MM`, ist sie `null`. `worstObservation` ist die Beobachtung mit dem größten normierten Residuum `|v_i| / σ_i`, Winkel mit `wrap` in Radiant. Normierte Residuen, die sich um höchstens `SOLVER_RESIDUAL_MM / SIGMA_LENGTH_MM` unterscheiden, gelten als gleich, weil die Iteration Längen nur bis `SOLVER_RESIDUAL_MM` festlegt. Bei Gleichstand gewinnt die erste in derselben Reihenfolge wie bei `largestCorrection`. Sind alle Residuen genau 0, ist sie `null`.

Das Ergebnis wird als Overlay gespeichert und nie in die Rohwerte geschrieben:

```kotlin
data class ClosureAdjustment(
    val inputFingerprint: Long,
    val solverVersion: Int,
    val adjustedLengthsMm: ImmutableList<Double>,
    val adjustedTurnsRad: ImmutableList<Double>,
)
```

`inputFingerprint` ist ein stabiler FNV-1a-64-Hash (Offset `0xcbf29ce484222325`, Primzahl `0x100000001b3`) über diese Bytefolge, alle Zahlen Big-Endian:

1. `n` als Int
2. je Wand in Indexreihenfolge `mm` als Long
3. je Ecke in Indexreihenfolge: Tag als Byte (0 = `Unspecified`, 1 = `DeclaredRight`, 2 = `Measured`), Richtung als Byte (0 = keine, 1 = links, 2 = rechts), bei `Measured` zusätzlich `milliDegrees` als Int
4. Anzahl der Diagonalen als Int
5. je Diagonale `min(from, to)` und `max(from, to)` als Int und `mm` als Long, sortiert nach diesen drei Werten

IDs und Wandstärken fließen nicht ein, weil sie die Form nicht bestimmen. Stimmt der Hash nicht mehr, ist das Overlay ungültig und der Raum wird nach §4 neu ausgewertet. Das Zurücknehmen entfernt nur das Overlay.

**Strukturell gültig** ist ein Overlay, wenn es je Wand eine positive endliche Länge und eine endliche Drehung hat, sein Umriss bis `EXACT_CLOSURE_MM` schließt und einfach ist. Diese Prüfung läuft in §4 Schritt 2 vor jeder Neuberechnung. Dieselbe Prüfung entscheidet, ob die wirksame Länge einer Öffnung aus dem Overlay kommt (§9, ADR 0021). Ein strukturell ungültiges Overlay mit passendem Fingerabdruck wird auch dort ignoriert.

`solverVersion` dient der Nachvollziehbarkeit. Ein strukturell gültiges Overlay mit passendem Fingerabdruck bleibt auch bei abweichender Version und geänderter Toleranz gültig, damit ein App-Update eine bestätigte Geometrie nicht still verändert.

## 7. Einfache Polygone und Metriken

- **Selbstüberschneidung:** Alle Paare nicht benachbarter Kanten werden mit robusten Orientierungstests und `MIN_FEATURE_MM` geprüft. O(n²) ist bei maximal 128 Wänden zulässig.
- **Fläche:** `A = ½ |Σ (x_i y_{i+1} − x_{i+1} y_i)|` auf der Innenkontur.
- **Umfang:** Summe der wirksamen Längen.
- **Volumen:** `A · h`, wobei `h` die Raumhöhe oder sonst die Standardhöhe des Projekts ist.
- **Anrechnung:** `A_anrechenbar = A · f / 100` mit ganzzahligem `f` von 0 bis 100, Standard 100.
- **Anzeige:** Fläche in m² mit 2 Nachkommastellen, kaufmännisch gerundet. Summen werden aus ungerundeten Werten berechnet und erst danach gerundet.
- **Nischen und Erker** sind normale Teile des Umrisses und brauchen keine Sonderlogik.

## 8. Wandstärke und Bänder

Die Stärke `t_i` wirkt nur nach außen (ADR 0003). Äußere Normale von `w_i`: `n_i = s_F · (sin φ_i, −cos φ_i)` mit dem Umlaufsinn `s_F` der Form (§3).

Für die Ecke zwischen zwei Wänden werden die äußeren Offsetgeraden geschnitten:

- **Fast kollinear** (`|sin τ| < 1e-6`): Keine Gehrung, die Bandenden stehen senkrecht.
- **Gehrung:** Liegt der Schnittpunkt höchstens `4 · max(t_a, t_b)` von der Ecke entfernt, wird er verwendet.
- **Fase:** Sonst endet jedes Band senkrecht, das Dreieck zwischen beiden Enden wird als Füllstück ergänzt.
- **Konkave Ecke:** Die Bänder enden senkrecht an der Innenecke und dürfen sich außen überlappen.

Ein Band mit `t_i = 0` wird als Linie gezeichnet. Bänder ändern nie Fläche oder Umfang.

## 9. Öffnungen

```kotlin
data class Opening(
    val id: OpeningId, val wallId: WallId, val type: OpeningType,
    val offset: Length, val width: Length,
    val height: Length?, val sillHeight: Length?, val swing: DoorSwing?,
)
```

- `offset` zählt von `V_i` entlang der Wand. Die Bindung läuft über die stabile `WallId`.
- Gültig, wenn `offset ≥ 0`, `width > 0` und `offset + width ≤ L_wirksam + EXACT_CLOSURE_MM`.
- **`L_wirksam` (ADR 0021, CR-W2-06)** ist die Länge `adjustedLengthsMm[i]` des Overlays, wenn §4 Schritt 2 es annimmt, der Raum also geschlossen ist, der Fingerabdruck passt und das Overlay strukturell gültig ist (§6). In jedem anderen Fall ist es die Rohlänge: ohne Overlay, bei veraltetem oder strukturell ungültigem Overlay, bei `OPEN`, `CONFIRMED` und `CONFLICT` und ausdrücklich auch bei `NEEDS_ADJUSTMENT` und `PROPOSED` (A4 und B7), obwohl diese eine Vorschau- oder Solverform zeigen. `L_wirksam` hängt weder von der Toleranz noch von `solverVersion` ab und ist nicht zu verwechseln mit den Längen der angezeigten Form in §5.6 und §7.
- `OpeningRules.statusOf` und `RoomEvaluation.openingStatuses` nutzen dieselbe Implementierung und liefern für jede Öffnung denselben Status. Befehle prüfen über `OpeningRules`, Szene und Export-Vorprüfung lesen nur `openingStatuses`. Der Status einer Öffnung ändert sich nur mit den Rohwerten oder dem angenommenen Overlay (`AcceptAdjustment`, `RevertAdjustment`, Undo, Redo), nie durch eine Vorschau oder einen Toleranzwechsel. Kürzt ein angenommenes Overlay eine Wand, wird eine Öffnung dort `OUT_OF_BOUNDS`. `AcceptAdjustment` wird dadurch nicht abgelehnt.
- Gezeichnet wird eine Öffnung bei `offset` ab `V_i` auf der angezeigten Wand, ohne Skalierung. In einer Vorschau kann eine gültige Öffnung daher um höchstens die Korrektur der Wand plus `EXACT_CLOSURE_MM` über das angezeigte Wandende reichen.
- Öffnungen derselben Wand dürfen sich nicht überlappen.
- `sillHeight` nur für Fenster, `swing` nur für Türen.
- `DoorSwing` hat die Werte `HINGE_LEFT_INWARD`, `HINGE_LEFT_OUTWARD`, `HINGE_RIGHT_INWARD`, `HINGE_RIGHT_OUTWARD`. `INWARD` öffnet in den Raum hinein, `OUTWARD` aus ihm heraus. Links und rechts folgen DIN 107 (CR-W2-04): Man steht auf der Seite, zu der die Tür aufschlägt, und blickt auf die Wand. Liegen die Bänder links, ist die Tür `HINGE_LEFT_*`, sonst `HINGE_RIGHT_*`. Bei `INWARD` steht der Betrachter also im Raum, bei `OUTWARD` außerhalb. Die Seite hängt damit nicht von der Messrichtung des Raums ab.
- Geometrie: Mit der Wandrichtung `d_i` und der Außennormale `n_i` (§8) ist die Aufschlagseite `a = −n_i` für `INWARD` und `a = n_i` für `OUTWARD`. Die linke Hand des Betrachters zeigt in Richtung `l = (a.y, −a.x)`. Ist `l · d_i > 0`, liegt das linke Band am Ende `offset + width`, sonst am Anfang `offset`. Das rechte Band liegt jeweils gegenüber. Beispiel: Wand von `(0, 0)` nach `(4000, 0)`, Raum darüber. `HINGE_LEFT_INWARD` hat das Band am Ende, `HINGE_LEFT_OUTWARD` am Anfang.
- Wird eine Wand so geändert, dass eine Öffnung ungültig ist, bleibt die Öffnung gespeichert. Sie erhält den abgeleiteten Status `OUT_OF_BOUNDS`, wird rot markiert und vor dem Export gemeldet. Es wird nichts still gelöscht oder verschoben.

## 10. Mehrere Räume

```kotlin
data class RoomPlacement(val originX: Double, val originY: Double, val rotationRad: Double, val dock: DockLink?)
data class DockLink(
    val hostRoomId: RoomId, val hostWallId: WallId, val ownWallId: WallId,
    val hostOffset: Length, val hostOpeningId: OpeningId? = null,
)
```

**Andocken (CR-W2-03):** Die Wirtswand `w_i` läuft von `V_i` nach `V_{i+1}` mit der Einheitsrichtung `e_i` in Weltkoordinaten, der äußeren Normale `n_i` (§8) und der Stärke `t_i`. Die eigene Wand `w_j` mit der angezeigten Länge `L_j` wird so gelegt:

- **Lage:** Beide Enden von `w_j` liegen auf der um `t_i` nach außen versetzten Wandlinie, also genau `t_i` in Richtung `n_i` von der Innenkontur des Wirts entfernt. Die Trennwandstärke ist die Stärke der Wirtswand (ADR 0013).
- **Richtung:** Das Innere des Gastes liegt auf der Außenseite der Wirtswand, seine nach innen zeigende Normale an `w_j` ist `n_i`. Daraus folgt die Richtung `e_j = −s_W · s_G · e_i` mit dem Umlaufsinn `s_W` des Wirts und `s_G` des Gastes (§3). Bei gleichem Umlaufsinn läuft `w_j` gegenläufig zur Wirtswand, bei verschiedenem gleichläufig. Der Raum wird nur gedreht und verschoben, nie gespiegelt. Die Platzierung ist starr (§2), ein Spiegeln würde die gemessene Form in ihr Spiegelbild verfälschen. Ein Andocken wird deshalb wegen des Umlaufsinns nie abgelehnt.
- **Bezugspunkt von `hostOffset`:** Entlang der Wirtswand, gezählt von `V_i` in Richtung `e_i`, belegt `w_j` das Intervall `[a, a + L_j]` mit `a = hostOffset`. Bezugspunkt ist also das Ende von `w_j`, das `V_i` am nächsten liegt: `V_{j+1}` bei gleichem, `V_j` bei verschiedenem Umlaufsinn. `hostOffset = 0` setzt den Gast bündig an den Anfang der Wirtswand. `hostOffset` darf negativ sein oder über `L_i` hinausführen, der Gast steht dann über das Wandende hinaus.
- Die Regel stellt nur sicher, dass der Gast an der Andockwand auf der Außenseite des Wirts liegt. Bei nicht konvexen Formen oder überstehenden Gästen kann es an anderer Stelle Überschneidungen geben. Sie werden nicht geprüft und nicht abgelehnt.

**Andocken an eine Tür oder Öffnung:** Ist `hostOpeningId` gesetzt, muss die Öffnung auf `hostWallId` liegen, und `hostOffset` zählt ab dem Beginn der Öffnung statt ab `V_i`: `a = opening.offset + hostOffset`. Bei `hostOffset = 0` beginnt `w_j` bündig mit der Öffnung und überdeckt sie vollständig, wenn `L_j ≥ width`. Der Raum folgt damit einer verschobenen Öffnung. Wird die Öffnung gelöscht, bleibt die Weltposition erhalten: `hostOpeningId` wird `null` und `hostOffset` wird zu `hostOffset + opening.offset`, dem Abstand ab `V_i`.

- Ändert sich der Wirtsraum, berechnet `DockPlacementResolver` die Platzierung abhängiger Räume in topologischer Reihenfolge neu (ADR 0015). Maßgeblich ist der angezeigte Umriss. Ist der Wirtsraum oder der eigene Raum `OPEN` oder `CONFLICT`, bleibt die letzte Platzierung des Raums einschließlich Dock-Link erhalten.
- Andockungen dürfen keinen Zyklus bilden. Ein solcher Befehl wird abgelehnt.
- Freies Verschieben oder Drehen entfernt den Dock-Link. Raummaße bleiben unverändert.
- Wird der Wirtsraum gelöscht, behalten abhängige Räume ihre Weltposition und verlieren den Link.

**Einrasten:** Reine Funktion in `:core:domain` auf Weltkoordinaten. Kandidaten sind Ecken, Wandlinien und das Raster. Die Standarddistanz beträgt 16 dp, vom Aufrufer in mm umgerechnet. Ecken haben Vorrang vor Wänden, Wände vor dem Raster.

## 11. Befehle und Historie

`ProjectCommand.apply(project): DomainResult<Project>` ist rein und deterministisch. Es kennt weder Toleranz noch Auswertung, Uhr oder Dispatcher. Mindestens: `AddWall`, `EditWall`, `RemoveLastWall`, `SetCorner`, `AddDiagonal`, `RemoveDiagonal`, `CloseRoom`, `AcceptAdjustment`, `RevertAdjustment`, `SetThickness`, `Add/Edit/RemoveOpening`, `AddRoom`, `RemoveRoom`, `MoveRoom`, `DockRoom`, `UpdateRoomDetails`, `SetProjectHeight`.

Ein fehlgeschlagener Befehl ändert weder Projekt noch Historie. Ein neuer Befehl nach Undo verwirft den Redo-Stapel. Jeder `execute(command, tolerance)` erzeugt genau einen Undo-Schritt, einschließlich der Neuberechnung der Platzierungen (ADR 0015). IDs werden beim Erzeugen des Befehls vergeben, damit Redo identische Objekte herstellt.

Befehle werden als Daten (`ProjectEdit`) beschrieben. `ProjectCommandFactory.create(edit)` erzeugt daraus den ausführbaren `ProjectCommand`. Der Aufruf lautet `history.execute(factory.create(edit), tolerance)`.

**Vorbedingung (ADR 0019):** `ProjectCommand.verify(project, context): DomainResult<Unit>` ist `suspend` und liefert standardmäßig `Success(Unit)`. `CommandContext` trägt die Toleranz des laufenden `execute` und `evaluate(room)`, das den `RoomEvaluator` der Historie mit genau dieser Toleranz aufruft. Eine Bedingung, die eine Auswertung oder die Toleranz braucht, gehört in `verify`, nie in `apply`.

- **Ablauf von `execute(command, tolerance)`:** Mutex nehmen, dann in einem `withContext` auf dem Default-Dispatcher `verify` auf dem zuletzt festgeschriebenen Projekt, `apply` und `DockPlacementResolver.resolve`, danach unter demselben Mutex committen. Der erste Fehlschlag wird unverändert zurückgegeben, Projekt und Historie bleiben unverändert. Abbruch in `verify` ändert nichts (ADR 0016). Zwischen `verify` und Commit kann keine andere Operation, auch kein `reconcile`, das Projekt ändern.
- **Toleranz:** Der Aufrufer übergibt die Toleranz, die zum Zeitpunkt des Aufrufs in den Einstellungen gilt, nie eine zusammen mit einer angezeigten Auswertung gemerkte. Operationen gelten in Aufrufreihenfolge. Ein `execute`, das vor einem `reconcile(neu)` aufgerufen wurde, läuft vollständig mit der alten Toleranz. Das ist eine Folge von Operationen, kein Wettlauf.
- **Undo und Redo** stellen gespeicherte Stände exakt wieder her und rufen `verify` nicht auf (ADR 0015). Ein per Redo wiederhergestelltes Overlay bleibt nach §4 Schritt 2 gültig, auch wenn sich die Toleranz inzwischen geändert hat.
- **Toleranzwechsel nach gültigem `AcceptAdjustment`:** Das Overlay hängt nur vom Fingerabdruck ab (§4 Schritt 2, §6). Der Raum bleibt `ADJUSTED`, auch wenn die neue Toleranz einen anderen Vorschlag oder `PROPOSED(AMBIGUOUS_SHAPE)` ergäbe. `reconcile` ändert nur Platzierungen, nie Overlays, und prüft keine Vorbedingungen. Erst `RevertAdjustment` oder eine geänderte Rohgeometrie führt zur Neuauswertung mit der dann gültigen Toleranz.
- **`AcceptAdjustment`:** `verify` wertet den Raum über `context.evaluate` aus und verlangt `NeedsAdjustment` mit gleichem `AdjustmentProposal` (§5.5), ein fehlender Raum ergibt `NotFound`, ein Auswertungsfehler wird durchgereicht. „Gleich“ ist die strukturelle Gleichheit des Datenobjekts. Das ist kein Vergleich berechneter Größen im Sinn von §1, sondern die Wiedererkennung desselben Ergebnisses derselben deterministischen Auswertung. `apply` prüft danach rein: fehlender Raum `NotFound`, Fingerabdruck des Overlays passt nicht zur Rohgeometrie `ADJUSTMENT_NOT_AVAILABLE`, unzureichende Bestätigung `EXPLICIT_CONFIRMATION_REQUIRED`.
- Produktiver Code führt nur Befehle aus `ProjectCommandFactory` aus. Ein Wrapper um einen Befehl muss `verify` weiterreichen.

Weitere Festlegungen:

- **`RemoveLastWall`:** Auf einem geschlossenen Raum öffnet der Befehl den Raum und verwirft das Overlay, das per Undo zurückkommt. Hängen an der entfernten Wand oder Ecke Öffnungen, Diagonalen oder Dock-Links, wird er mit einer eigenen `DomainRule` abgelehnt, damit nichts still gelöscht wird.
- **`RoomEvaluator.evaluate`** liefert `DomainResult<RoomEvaluation>`. Verstöße gegen Wertebereiche ergeben `InvalidValue`. Die Wandanzahl ist kein Wertebereich, sondern eine Strukturregel: mehr als `MAX_WALLS_PER_ROOM` Wände ergeben wie bei `AddWall` `RuleViolation(TOO_MANY_WALLS)`. `ValueField` erhält deshalb kein Feld für die Wandanzahl (CR-W2-02).
- **Nebenläufigkeit (ADR 0016, ADR 0019):** `CommandHistory.execute`, `reconcile`, `undo`, `redo`, `ProjectCommand.verify`, `DockPlacementResolver.resolve` und `RoomEvaluator.evaluate` sind `suspend`. Eine Operation nimmt den Mutex der Historie, rechnet unter ihm per `withContext` auf dem Default-Dispatcher und schreibt danach unter demselben Mutex. Bestätigte Befehle und Speichern laufen im Application-Scope und werden durch Verlassen des Editors nicht abgebrochen. Abbrechbar sind nur Vorschauen und Auswertungen ohne Commit. Ein `CancellationSignal` reicht bis in Solver, Ausgleich und Rangbestimmung. Abbruch vor dem Commit lässt Projekt und Historie unverändert.
- **`EditOpening`:** Soll eine Öffnung mit angedocktem Raum die Wand wechseln, wird der Befehl mit `OPENING_HAS_DOCKED_ROOMS` abgelehnt. Der Dock-Link wird nicht still gelöst.
- **Import (ADR 0017):** `importProject(input: InputStream)` liest ein nicht vertrauenswürdiges Dokument. `:core:data` decodiert es, prüft es mit `ProjectValidator` und speichert es erst danach. Der Aufrufer besitzt den Stream und schließt ihn. `DocumentCodec` übersetzt `IllegalArgumentException` in `DomainError.Document(INVALID_VALUES)`. `ProjectValidator` in `:core:domain` prüft Wandgrenzen, Dock-Ziele einschließlich `DockLink.ownWallId`, Zyklen und überlappende Öffnungen je Wand. Berührende Öffnungen überlappen nicht.
- **`updatedAtEpochMs`** wird nicht von Befehlen geändert, sondern von `ProjectSaver` beim Speichern über `EpochClock` gesetzt.
- **Raumreihenfolge beim Speichern:** Die gespeicherte Reihenfolge folgt `project.rooms`, auch für Räume außerhalb von `changedRooms`, deren Inhalt unverändert bleibt. Wird `[A, B, C]` ohne `B` gespeichert und `B` per Undo zurückgeholt, ergibt das Speichern von `{B}` wieder `[A, B, C]`.
- **`reconcile`** verwirft den Redo-Stapel nur, wenn ein neuer Undo-Schritt entsteht.
- **`importProject`** behält die IDs. Bei einer Kollision entsteht eine Kopie wie beim Duplizieren. `decodeProject` meldet `IOException` als `Storage(READ_FAILED)`.
- **Trefferpriorität (`HitTester`, CR-W2-01):** Kandidat ist jedes Szenenelement mit Ziel, dessen Abstand zum Punkt höchstens der Trefferradius ist (Radius einschließlich). Klassen: Öffnung vor Ecke vor Wand vor Diagonale vor Rauminnerem. Diagonalen liegen im Raum und enden in Ecken: Vor dem Rauminneren müssen sie stehen, sonst wären sie nie wählbar; hinter Ecke und Wand, damit an ihren Enden die Ecke und nahe einer Wand die Wand gewinnt. Innerhalb einer Klasse gewinnt der kleinste Abstand, bei Gleichstand beim Rauminneren die kleinere Fläche, danach die kleinste ID (Öffnungs-, Wand- oder Diagonalen-ID, dann Raum-ID, dann Eckindex, als Zeichenketten nach UTF-16-Codeeinheiten). Zwei Abstände oder Flächen `a`, `b` sind gleich, wenn `|a − b| ≤ HIT_TIE_RELATIVE · max(1, |a|, |b|)` (§1). Abstand ist 0 im Inneren einer Fläche, sonst der euklidische Abstand zum gezeichneten Umriss, Linienzug oder Bogen. Eine Beschriftung zählt als Kreisscheibe um ihren Anker mit ihrer Höhe als Durchmesser, weil die Textbreite von der Schrift abhängt. Der Trefferradius ist 24 dp auf dem Bildschirm, umgerechnet in Weltmillimeter (UX §4).

## 12. Maßstab im Export

`PlotLayout` prüft die Maßstäbe `1:50`, `1:100`, `1:200` in dieser Reihenfolge, je Maßstab A4, dann A3, und je Papier beide Ausrichtungen. Der nutzbare Bereich ist die Seite minus 10 mm Rand an allen Seiten und 25 mm Titelblock in der Höhe. Ein Plan passt, wenn `Breite ≤ nutzbare Breite · M` und `Höhe ≤ nutzbare Höhe · M`. Gewählt wird die erste passende Kombination, also der größte Maßstab. Passen auf einem Papier beide Ausrichtungen, gewinnt die zum Plan passende: Hochformat, wenn die Höhe die Breite um mehr als `EXACT_CLOSURE_MM` übersteigt, sonst Querformat. Ein quadratischer Plan liegt also quer. Die Ausrichtung wird immer automatisch gewählt, der Nutzer kann nur Maßstab und Papier festlegen (REQ-EXP-002, CR-W2-04). Die Prüfung ist exakt, ohne Toleranz: Schon `13 850,0000001 mm` Breite passt nicht mehr in `277 mm · 50`. Passt der Plan auch bei 1:200 auf A3 nicht, wird der Export mit einer Fehlermeldung abgelehnt.

| Papier | Hochformat nutzbar (mm) | Querformat nutzbar (mm) |
|---|---|---|
| A4 | 190 × 252 | 277 × 165 |
| A3 | 277 × 375 | 400 × 252 |

**Referenzfälle** (Breite × Höhe):

- 13,5 × 13,0 m: Bei 1:50 fasst A4 hoch 9,5 × 12,6 m und A4 quer 13,85 × 8,25 m, beide zu klein. A3 hoch fasst 13,85 × 18,75 m und passt, A3 quer mit 20,0 × 12,6 m nicht. Ergebnis: **1:50, A3 hoch**.
- 9,25 × 8,5 m: Bei 1:50 passt A4 hoch (9,5 × 12,6 m), A4 quer nicht (Höhe 8,5 > 8,25 m). Ergebnis: **1:50, A4 hoch**.

Umrechnung: `pt = mm_welt / M · 72 / 25,4`. Schrifthöhen der Szene sind Nennhöhen in Weltmillimetern (ADR 0008, Ergänzung CR-W2-04): Alle Beschriftungen werden mit demselben Faktor `f = max(1, 1,8 mm · M / 150 mm)` vergrößert, sodass Maßzahlen auf Papier mindestens 1,8 mm hoch sind (1:50 `f = 1`, 1:100 `f = 1,2`, 1:200 `f = 2,4`). Lage und Abstände der Maße ändern sich nicht. Jeder Plan enthält einen Maßstabsbalken mit Nennlänge. Der Titelblock liegt am unteren Rand des Bereichs innerhalb der 10-mm-Ränder, über die volle Breite und 25 mm hoch. Er enthält Maßstabsbalken und Maßstabsangabe. Der Plan wird im Zeichenbereich darüber zentriert: Die Mitte der Weltgrenzen liegt auf der Mitte des Zeichenbereichs (`PlotPlan.drawingAreaMm`, Titelblock `PlotPlan.titleBlockMm`). PNG wird mit 200 dpi auf der gewählten Seite gerendert.

**Titelblock** (ADR 0022, `TitleBlockSpec`): Maße in Papiermillimetern relativ zum Titelblock, Innenabstand 5 mm, Rahmen und Balkenumrisse 0,25 mm.

| Element | Lage und Größe |
|---|---|
| Projektname | links, Grundlinie 8 mm unter Oberkante, 5 mm Schrift, höchstens Blockbreite − 35 mm breit |
| Maßstab `1:M` | rechtsbündig, gleiche Grundlinie, 5 mm Schrift |
| Maßstabsbalken | links, Oberkante 12 mm unter Blockoberkante, 3 mm hoch, zwei gleiche Segmente (dunkel, hell) |
| Nennlänge | 2 m bei 1:50, 5 m bei 1:100, 10 m bei 1:200 |
| Balkenbeschriftung | `0` und Nennlänge in der Exporteinheit, mittig unter den Enden, 3 mm Schrift, Grundlinie 3 mm unter dem Balken |

Räume mit `OPEN` oder `CONFLICT` werden in PDF, PNG und SVG weggelassen und in der Vorprüfung gemeldet. Der Plan selbst trägt keinen Hinweis auf sie (ADR 0022). Die Vorprüfung meldet außerdem nicht bestätigte Räume und Öffnungen exportierbarer Räume, deren Status in der Auswertung `OUT_OF_BOUNDS` ist. Bleibt kein Raum übrig, wird der Export mit `NOTHING_TO_EXPORT` abgelehnt. JSON enthält immer alle Räume.
