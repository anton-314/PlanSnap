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
| `NEAR_COLLINEAR_SIN` | 1e-6 | Grenze für fast kollineare Ecken (§8) |
| `MITER_LIMIT_FACTOR` | 4,0 | Gehrungsgrenze als Vielfaches der Wandstärke (§8) |

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

**Sollwert der Schlussecke:** Ist sie `Unspecified`, sei `r = 2π·s − Σ_{i≥1} τ̂_i`, normiert auf `(−π, π]`. `τ̂_0` ist der Wert aus `{−90°, 0°, +90°}` mit dem kleinsten zyklischen Abstand zu `r`. Bei Gleichstand gewinnt der Kandidat mit dem größten `s · τ̂_0`. Beispiele: `r = −45°`, `s = +1` ergibt 0°, `r = 180°` ergibt `s · 90°`. Die Rechnung erfolgt ganzzahlig in Milligrad, damit Gleichstände exakt erkannt werden.

## 4. Raumstatus

```kotlin
enum class ShapeStatus { OPEN, CONFIRMED, NEEDS_ADJUSTMENT, ADJUSTED, PROPOSED, CONFLICT }
```

| Status | Bedeutung | Im Export |
|---|---|---|
| `OPEN` | Weniger als 3 Wände oder nicht abgeschlossen | Nicht exportierbar, Warnung |
| `CONFIRMED` | Form eindeutig, exakt oder mit Korrekturen bis `EXACT_CLOSURE_MM` geschlossen | Normal |
| `NEEDS_ADJUSTMENT` | Korrektur der Messwerte wartet auf Bestätigung | „Form nicht bestätigt“ |
| `ADJUSTED` | Ausgleich bestätigt und zu den Rohwerten passend | Normal |
| `PROPOSED` | Form aus Vorschlag, Zusatzmaße fehlen | „Form nicht bestätigt“ |
| `CONFLICT` | Maße sind unmöglich oder widersprechen sich signifikant | Nicht exportierbar, Warnung |

### Auswertung `evaluate(room, tolerance)`

Die Schritte laufen in dieser Reihenfolge. Der erste zutreffende Schritt legt das Ergebnis fest, jeder Pfad endet in genau einem Status.

1. **Validieren.** Wertebereiche prüfen, ein Verstoß ergibt `InvalidValue`. Weniger als 3 Wände oder nicht abgeschlossen ergibt `OPEN`.
2. **Gespeichertes Overlay.** Hat der Raum ein Overlay mit passendem Fingerabdruck, das strukturell gültig ist (§6), folgt sofort `ADJUSTED` mit der Overlay-Form. Es wird nichts neu berechnet, `solverVersion` und Toleranz spielen keine Rolle. So macht ein App-Update aus `ADJUSTED` nie `CONFLICT`. Ein anderes Overlay wird ignoriert.
3. **Notwendige Bedingungen.** Ist `L_max ≥ Σ L_i − L_max − MIN_FEATURE_MM`, folgt `CONFLICT(WALL_TOO_LONG)`. Für jede Diagonale zwischen **nicht benachbarten** Ecken `a` und `b` teilt sich der Umriss in zwei Ketten. Für jede Kette mit Länge `C` und längstem Glied `M` muss `D ≤ C` und `D ≥ 2M − C` gelten, sonst `CONFLICT(DIAGONAL_IMPOSSIBLE)` mit der betroffenen Diagonale. Eine Diagonale zwischen benachbarten Ecken ist eine Wiederholungsmessung der Wand und wird über §5.5 und §6 geprüft.
4. **Sollform prüfen.** Mit allen Sollwinkeln `τ̂` den Zug ablaufen. Berechne:
   - `g = |Σ L_i (cos φ̂_i, sin φ̂_i)|`
   - `r_k = |d_k(τ̂) − D_k|` für alle Diagonalen
   - `a = |wrap(τ̂_0,fest − τ_0,abgeleitet)| · U / (2π)` mit `τ_0,abgeleitet = 2π·s − Σ_{i≥1} τ̂_i`, falls die Schlussecke festgelegt ist (`Measured` oder `DeclaredRight`), sonst 0
   - `e = max(g, max r_k, a)`
5. **Grenzwert:** `T = max(T_abs, T_rel · U)` mit `U = Σ L_i`.
6. **Verzweigung:**
   - `e ≤ EXACT_CLOSURE_MM` ergibt `CONFIRMED` mit der Sollform.
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
- Ziel (REQ-MEAS-007): `f(u) = Σ_{i ∈ W} (τ_i − τ̂_i)²`, wobei `W` alle nicht gemessenen Ecken einschließlich der Schlussecke enthält.

Gemessene Ecken werden als Variablen eliminiert. Abstandsbedingungen werden durch `U` geteilt, damit alle Zeilen dieselbe Größenordnung haben.

### 5.2 Iteration (Gleichungs-SQP)

Für jeden Startwert, höchstens `SOLVER_MAX_ITERATIONS` Schritte:

1. `g = ∇f`, `J = ∂c/∂u` analytisch, `H = ∇²f + μI` mit `μ = 1e-8`.
2. KKT-System lösen: `[H Jᵀ; J 0][δ; λ] = [−g; −c]` mit LU-Zerlegung und Spaltenpivotisierung. Bei Singularität `J` über die Rangbestimmung (§5.4) ausdünnen.
3. Schrittweite `t ∈ {1, ½, ¼ …}` mit mindestens `2⁻²⁰`, sodass die Merit-Funktion `m(u) = f(u) + ρ‖c(u)‖₁` sinkt. `ρ = max(1, 2‖λ‖_∞)`, und `ρ` wächst nur.
4. Abbruch, wenn `‖c‖_∞ · U ≤ SOLVER_RESIDUAL_MM` und `‖g + Jᵀλ‖_∞ ≤ SOLVER_GRADIENT`.

Die Implementierung verwendet nur eigene Dense-Matrizen, keine Zufallszahlen und keine Abhängigkeit von Iterationsreihenfolgen unsortierter Sammlungen.

### 5.3 Startwerte und Auswahl

Feste Reihenfolge:

1. **S1:** Sollwerte `τ̂`.
2. **S2:** Mittel aus `τ̂` und `s · 2π/n`.
3. **S3:** Drehungen des **Sehnenpolygons** zu den Längen. Radius `R` per Bisektion aus `Σ 2·asin(L_i/(2R)) = 2π`. Liegt der Mittelpunkt außerhalb, wird der Bogen der längsten Seite durch `2π − 2·asin(L_max/(2R))` ersetzt.

Gültig ist ein Ergebnis, wenn es konvergiert, der Umriss einfach ist (§7) und kein Kantenabstand unter `MIN_FEATURE_MM` liegt. Von den gültigen Ergebnissen gewinnt:

1. dasjenige, das alle deklarierten und gemessenen Drehrichtungen einhält,
2. dann die kleinsten Kosten `f` (Gleichstand bei Differenz unter `COST_TIE`),
3. dann das lexikografisch kleinste `u`, auf 1e-6 rad gerundet.

Ohne gültiges Ergebnis folgt `CONFLICT(NO_SIMPLE_SHAPE)`.

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

- `missing = 0`: Die Form ist bis auf diskrete Spiegelungen eindeutig. Die Drehrichtungen wählen die Lösung.
- `missing > 0`: Status `PROPOSED`.

**Empfehlung des Zusatzmaßes:** Unter allen Diagonalen zwischen nicht benachbarten Ecken, die den Rang erhöhen, wird die längste empfohlen. Gleichstand wird durch den kleinsten Index `(a, b)` entschieden. Für eine Ecke wird nur dann ein Winkel empfohlen, wenn keine Diagonale den Rang erhöht.

### 5.5 Entscheidungsablauf bei Formabweichung

Für `e > T` gilt dieser Ablauf (ADR 0018). `v`, `m`, `I` stammen aus `analyzeConstraints`.

**Zweig A, nicht überbestimmt (`m = ρ`):**

1. Solver auf allen Bedingungen. `NoSimpleShape` ergibt `CONFLICT(NO_SIMPLE_SHAPE)`.
2. `analyze` an der Lösung. `missing > 0` ergibt `PROPOSED`.
3. Bei `missing = 0`: Verletzt die Lösung eine deklarierte oder gemessene Drehrichtung, folgt `CONFLICT(TURN_DIRECTION_VIOLATED)`, sonst `CONFIRMED` mit `r = 0`.

**Zweig B, überbestimmt (`m > ρ`):**

1. **Startform.** Bei `v = 0` sind die Beobachtungen der Start. Sonst läuft der Solver auf `I`. `NoSimpleShape` ergibt `CONFLICT(NO_SIMPLE_SHAPE)`.
2. **Ausgleich** aus §6 im Modus `SHAPE_DEVIATION`, `x_0` sind die Beobachtungen, die Startform ist nur der erste Linearisierungspunkt. `NotConverged` ergibt `CONFLICT(ADJUSTMENT_NOT_CONVERGED)` ohne Overlay.
3. **Einfachheit.** Ist der ausgeglichene Umriss nicht einfacher oder hat er einen Kantenabstand unter `MIN_FEATURE_MM`, folgt `CONFLICT(NO_SIMPLE_SHAPE)`.
4. **Rang.** `analyze` an der ausgeglichenen Form ergibt `ρ_L`, `missing = v − ρ_L` und die Redundanz `r = m − ρ_L`.
5. **Konflikttest.** Bei `r ≥ 1`, `S > T` **und** `q² > χ²(r; 0,99)` folgt `CONFLICT(MEASUREMENTS_INCONSISTENT)` unter Angabe von `worstObservation`.
6. **Drehrichtungen.** Bei `missing = 0` und verletzter deklarierter oder gemessener Drehrichtung folgt `CONFLICT(TURN_DIRECTION_VIOLATED)`.
7. `missing > 0` ergibt `PROPOSED` mit der ausgeglichenen Form. Eine verletzte Drehrichtung ist hier zulässig, weil die Form nur ein Vorschlag ist.
8. `maxCorrectionMm ≤ EXACT_CLOSURE_MM` ergibt `CONFIRMED`. Eine stimmige Zusatzmessung verschlechtert den Status also nicht.
9. Sonst folgt `NEEDS_ADJUSTMENT` mit Vorschau. Die **ausdrückliche Bestätigung** ist genau bei `r ≥ 1`, `S > T` und `χ²(r; 0,95) < q² ≤ χ²(r; 0,99)` nötig, sonst genügt die einfache. `S`, die größte Einzelkorrektur und die betroffene Messung stehen immer im Ergebnis.

Der Fall „alle Ecken gemessen, `e > T`“ hat `v = 0`, `ρ = 0` und `r = m`, läuft also immer durch Zweig B.

**Kenngrößen:**

- `S` ist die Summennorm der Korrekturen: `S = Σ |ΔL_i| + Σ |ΔD_k| + Σ |wrap(Δτ_i)| · U / (2π)` über alle Beobachtungen.
- `q² = Σ (v_i / σ_i)²` über dieselben Beobachtungen, mit `σ_i` aus §6.
- `χ²(r; p)` liefert `ChiSquareLimits.quantile`: feste Tabelle für `r = 1 … 30`, darüber Wilson-Hilferty `r · (1 − 2/(9r) + z_p · √(2/(9r)))³`.

**Bestätigung:** `AcceptAdjustment` nennt die tatsächlich gegebene Bestätigung. Verlangt der Vorschlag `EXPLICIT` und liegt nur `SIMPLE` vor, wird der Befehl mit `EXPLICIT_CONFIRMATION_REQUIRED` abgelehnt.

**Kontrollierbarkeit (`analyzeControl`).** Sie läuft bei jedem bestimmten Raum (`missing = 0`) mit Ausgleich und **ändert nie den Status**:

- `r_i` ist die Redundanzzahl der Beobachtung, `r_i = (Q Aᵀ (A Q Aᵀ)⁻¹ A Q)_ii / σ_i²` an der Lösung.
- `λ_i(3T) = (CONTROL_ERROR_FACTOR · T)² · r_i / σ_i²`. Für Winkel ist `σ_i` das mm-Äquivalent `σ · U / (2π)`.
- Eine Messung ist **nicht kontrollierbar**, wenn `λ_i(3T) < λ₀(r)`. `λ₀(r)` ist die Nichtzentralität, bei der der Konflikttest die Trennschärfe `CONTROL_POWER` erreicht: feste, aufgerundete Tabelle für `r = 1 … 30`, darüber `λ₀ = c − r + 2z² + √(4z⁴ + z²(4c − 2r))` mit `c = χ²(r; 0,99)` und `z = z_0,95`.
- **Empfohlenes Zusatzmaß je Messung:** Geprüft werden die noch nicht gemessenen Diagonalen zwischen nicht benachbarten Ecken, die längste zuerst, Gleichstand nach kleinstem `(a, b)`, danach die nicht gemessenen Ecken nach Index. Empfohlen wird das erste Maß, mit dem `λ_i(3T) ≥ λ₀(r + 1)` gilt. Gibt es keines, bleibt die Empfehlung leer.
- Bei `r = 0` hat keine Messung eine Gegenprobe. `MeasurementControl` meldet dann `r = 0` und keine Einzelmessungen.

## 6. Schließungsausgleich

Methode: gewichtete kleinste Quadrate (ADR 0005). Beobachtungen und Standardabweichungen:

| Beobachtung | σ |
|---|---|
| Wandlänge | 5 mm |
| Diagonale | 5 mm |
| Deklarierter rechter Winkel | 0,5° |
| Gemessener Winkel | 1,0° |

Variablen `x = (L_0…L_{n-1}, τ_0…τ_{n-1}, D_k)`. `x_0` sind immer die Beobachtungen. Eine nicht festgelegte Schlussecke hat keine Beobachtung, ihr Wert in `x_0` ist `τ_0,abgeleitet`. Der erste Linearisierungspunkt `x_1` ist `x_0` mit den Drehungen aus dem Parameter `startTurnsRad` (§5.5). Bedingungen `C(x) = 0`: Schließung, `Σ τ_i = 2π·s` und Diagonalen. Eine Diagonale zwischen benachbarten Ecken hat die lineare Bedingung `L_i − D_k = 0` mit der Wand `w_i` zwischen beiden Ecken.

Iteration (Gauss-Helmert) ab `k = 1`: `x_{k+1} = x_0 − Q Aᵀ (A Q Aᵀ)⁻¹ (C(x_k) + A (x_0 − x_k))` mit `A = ∂C/∂x` an `x_k` und `Q = diag(σ²)`, Winkel in Radiant. Eine Variable ohne Beobachtung erhält ein festes großes `σ = 10 rad`. Höchstens 50 Schritte, Abbruch bei `‖C‖_∞ ≤ SOLVER_RESIDUAL_MM`. Wird die Grenze nicht erreicht, ist das Ergebnis `NotConverged`. Das führt in jedem Pfad zu `CONFLICT(ADJUSTMENT_NOT_CONVERGED)`, es entsteht kein Overlay.

**Modus (ADR 0018):** `MEASUREMENT_ERROR` gilt für `e ≤ T`, deklarierte rechte Winkel sind dort Beobachtungen. `SHAPE_DEVIATION` gilt für Zweig B aus §5.5. Deklarierte rechte Winkel sind dort nur Form-Angaben (§4) und haben keine Beobachtung.

**Größte Korrektur:** `maxCorrectionMm = max(|ΔL_i|, |ΔD_k|, |wrap(Δτ_i)| · U / (2π))` über alle Beobachtungen, mit `U = Σ L_i` der Rohlängen. Ecken ohne Beobachtung, also die freie Schlussecke und im Modus `SHAPE_DEVIATION` deklarierte rechte Winkel, zählen weder hier noch in `S`, `q²`, den Redundanzzahlen oder als `worstObservation`. `largestCorrection` nennt die Beobachtung mit der größten mm-äquivalenten Korrektur, `worstObservation` die mit dem größten normierten Residuum.

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

**Strukturell gültig** ist ein Overlay, wenn es je Wand eine positive endliche Länge und eine endliche Drehung hat, sein Umriss bis `EXACT_CLOSURE_MM` schließt und einfach ist. Diese Prüfung läuft in §4 Schritt 2 vor jeder Neuberechnung.

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

Die Stärke `t_i` wirkt nur nach außen (ADR 0003). Äußere Normale von `w_i`: `n_i = s · (sin φ_i, −cos φ_i)`.

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
- Öffnungen derselben Wand dürfen sich nicht überlappen.
- `sillHeight` nur für Fenster, `swing` nur für Türen.
- `DoorSwing` hat die Werte `HINGE_LEFT_INWARD`, `HINGE_LEFT_OUTWARD`, `HINGE_RIGHT_INWARD`, `HINGE_RIGHT_OUTWARD`. Links und rechts gelten in Wandrichtung `V_i → V_{i+1}`, `INWARD` öffnet in den Raum hinein.
- Wird eine Wand so geändert, dass eine Öffnung ungültig ist, bleibt die Öffnung gespeichert. Sie erhält den abgeleiteten Status `OUT_OF_BOUNDS`, wird rot markiert und vor dem Export gemeldet. Es wird nichts still gelöscht oder verschoben.

## 10. Mehrere Räume

```kotlin
data class RoomPlacement(val originX: Double, val originY: Double, val rotationRad: Double, val dock: DockLink?)
data class DockLink(
    val hostRoomId: RoomId, val hostWallId: WallId, val ownWallId: WallId,
    val hostOffset: Length, val hostOpeningId: OpeningId? = null,
)
```

**Andocken:** Die eigene Wand wird antiparallel zur Wirtswand ausgerichtet. Ihr Startpunkt wird so gelegt, dass der Abstand zwischen den Innenkonturen genau der Stärke der Wirtswand entspricht. `hostOffset` verschiebt den Raum entlang der Wand.

**Andocken an eine Tür oder Öffnung:** Ist `hostOpeningId` gesetzt, muss die Öffnung auf `hostWallId` liegen, und `hostOffset` zählt ab dem Beginn der Öffnung statt ab `V_i`. Der Raum folgt damit einer verschobenen Öffnung. Wird die Öffnung gelöscht, bleibt die Weltposition erhalten: `hostOpeningId` wird `null` und `hostOffset` wird auf den entsprechenden Abstand ab `V_i` umgerechnet.

- Ändert sich der Wirtsraum, berechnet `DockPlacementResolver` die Platzierung abhängiger Räume in topologischer Reihenfolge neu (ADR 0015). Maßgeblich ist der angezeigte Umriss. Ist der Wirtsraum oder der eigene Raum `OPEN` oder `CONFLICT`, bleibt die letzte Platzierung des Raums einschließlich Dock-Link erhalten.
- Andockungen dürfen keinen Zyklus bilden. Ein solcher Befehl wird abgelehnt.
- Freies Verschieben oder Drehen entfernt den Dock-Link. Raummaße bleiben unverändert.
- Wird der Wirtsraum gelöscht, behalten abhängige Räume ihre Weltposition und verlieren den Link.

**Einrasten:** Reine Funktion in `:core:domain` auf Weltkoordinaten. Kandidaten sind Ecken, Wandlinien und das Raster. Die Standarddistanz beträgt 16 dp, vom Aufrufer in mm umgerechnet. Ecken haben Vorrang vor Wänden, Wände vor dem Raster.

## 11. Befehle und Historie

`ProjectCommand.apply(project): DomainResult<Project>` ist rein und deterministisch. Mindestens: `AddWall`, `EditWall`, `RemoveLastWall`, `SetCorner`, `AddDiagonal`, `RemoveDiagonal`, `CloseRoom`, `AcceptAdjustment`, `RevertAdjustment`, `SetThickness`, `Add/Edit/RemoveOpening`, `AddRoom`, `RemoveRoom`, `MoveRoom`, `DockRoom`, `UpdateRoomDetails`, `SetProjectHeight`.

Ein fehlgeschlagener Befehl ändert weder Projekt noch Historie. Ein neuer Befehl nach Undo verwirft den Redo-Stapel. Jeder `execute(command, tolerance)` erzeugt genau einen Undo-Schritt, einschließlich der Neuberechnung der Platzierungen (ADR 0015). IDs werden beim Erzeugen des Befehls vergeben, damit Redo identische Objekte herstellt.

Befehle werden als Daten (`ProjectEdit`) beschrieben. `ProjectCommandFactory.create(edit)` erzeugt daraus den ausführbaren `ProjectCommand`. Der Aufruf lautet `history.execute(factory.create(edit), tolerance)`.

Weitere Festlegungen:

- **`RemoveLastWall`:** Auf einem geschlossenen Raum öffnet der Befehl den Raum und verwirft das Overlay, das per Undo zurückkommt. Hängen an der entfernten Wand oder Ecke Öffnungen, Diagonalen oder Dock-Links, wird er mit einer eigenen `DomainRule` abgelehnt, damit nichts still gelöscht wird.
- **`RoomEvaluator.evaluate`** liefert `DomainResult<RoomEvaluation>`. Verstöße gegen Wertebereiche ergeben `InvalidValue`.
- **Nebenläufigkeit (ADR 0016):** `CommandHistory.execute`, `reconcile`, `undo`, `redo`, `DockPlacementResolver.resolve` und `RoomEvaluator.evaluate` sind `suspend`. Eine Operation nimmt den Mutex der Historie, rechnet unter ihm per `withContext` auf dem Default-Dispatcher und schreibt danach unter demselben Mutex. Bestätigte Befehle und Speichern laufen im Application-Scope und werden durch Verlassen des Editors nicht abgebrochen. Abbrechbar sind nur Vorschauen und Auswertungen ohne Commit. Ein `CancellationSignal` reicht bis in Solver, Ausgleich und Rangbestimmung. Abbruch vor dem Commit lässt Projekt und Historie unverändert.
- **`EditOpening`:** Soll eine Öffnung mit angedocktem Raum die Wand wechseln, wird der Befehl mit `OPENING_HAS_DOCKED_ROOMS` abgelehnt. Der Dock-Link wird nicht still gelöst.
- **Import (ADR 0017):** `importProject(input: InputStream)` liest ein nicht vertrauenswürdiges Dokument. `:core:data` decodiert es, prüft es mit `ProjectValidator` und speichert es erst danach. Der Aufrufer besitzt den Stream und schließt ihn. `DocumentCodec` übersetzt `IllegalArgumentException` in `DomainError.Document(INVALID_VALUES)`. `ProjectValidator` in `:core:domain` prüft Wandgrenzen, Dock-Ziele einschließlich `DockLink.ownWallId`, Zyklen und überlappende Öffnungen je Wand. Berührende Öffnungen überlappen nicht.
- **`updatedAtEpochMs`** wird nicht von Befehlen geändert, sondern von `ProjectSaver` beim Speichern über `EpochClock` gesetzt.
- **Raumreihenfolge beim Speichern:** Die gespeicherte Reihenfolge folgt `project.rooms`, auch für Räume außerhalb von `changedRooms`, deren Inhalt unverändert bleibt. Wird `[A, B, C]` ohne `B` gespeichert und `B` per Undo zurückgeholt, ergibt das Speichern von `{B}` wieder `[A, B, C]`.
- **`reconcile`** verwirft den Redo-Stapel nur, wenn ein neuer Undo-Schritt entsteht.
- **`importProject`** behält die IDs. Bei einer Kollision entsteht eine Kopie wie beim Duplizieren. `decodeProject` meldet `IOException` als `Storage(READ_FAILED)`.
- **Trefferpriorität (`HitTester`):** Öffnung vor Ecke vor Wand vor Rauminnerem. Innerhalb einer Klasse gewinnt der kleinste Abstand, bei Gleichstand beim Rauminneren die kleinere Fläche, danach die kleinste ID.

## 12. Maßstab im Export

`PlotLayout` prüft die Maßstäbe `1:50`, `1:100`, `1:200` in dieser Reihenfolge, je Maßstab A4, dann A3, und je Papier beide Ausrichtungen. Der nutzbare Bereich ist die Seite minus 10 mm Rand an allen Seiten und 25 mm Titelblock in der Höhe. Ein Plan passt, wenn `Breite ≤ nutzbare Breite · M` und `Höhe ≤ nutzbare Höhe · M`. Gewählt wird die erste passende Kombination, also der größte Maßstab. Passen auf einem Papier beide Ausrichtungen, gewinnt die zum Plan passende: Hochformat, wenn die Höhe die Breite um mehr als `EXACT_CLOSURE_MM` übersteigt, sonst Querformat. Ein quadratischer Plan liegt also quer. Passt der Plan auch bei 1:200 auf A3 nicht, wird der Export mit einer Fehlermeldung abgelehnt.

| Papier | Hochformat nutzbar (mm) | Querformat nutzbar (mm) |
|---|---|---|
| A4 | 190 × 252 | 277 × 165 |
| A3 | 277 × 375 | 400 × 252 |

**Referenzfälle** (Breite × Höhe):

- 13,5 × 13,0 m: Bei 1:50 fasst A4 hoch 9,5 × 12,6 m und A4 quer 13,85 × 8,25 m, beide zu klein. A3 hoch fasst 13,85 × 18,75 m und passt, A3 quer mit 20,0 × 12,6 m nicht. Ergebnis: **1:50, A3 hoch**.
- 9,25 × 8,5 m: Bei 1:50 passt A4 hoch (9,5 × 12,6 m), A4 quer nicht (Höhe 8,5 > 8,25 m). Ergebnis: **1:50, A4 hoch**.

Umrechnung: `pt = mm_welt / M · 72 / 25,4`. Jeder Plan enthält einen Maßstabsbalken mit Nennlänge. Der Titelblock liegt am unteren Rand des Bereichs innerhalb der 10-mm-Ränder, über die volle Breite und 25 mm hoch. PNG wird mit 200 dpi auf der gewählten Seite gerendert.

Räume mit `OPEN` oder `CONFLICT` werden in PDF, PNG und SVG weggelassen und in der Vorprüfung gemeldet. Bleibt kein Raum übrig, wird der Export mit `NOTHING_TO_EXPORT` abgelehnt. JSON enthält immer alle Räume.
