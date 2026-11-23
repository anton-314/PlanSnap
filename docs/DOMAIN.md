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
| `MIN_FEATURE_MM` | 1,0 | Mindestabstand nicht benachbarter Kanten |
| `COST_TIE` | 1e-9 | Kostengleichheit bei der Lösungsauswahl |
| `DEFAULT_TOLERANCE_ABS_MM` | 50 | Grenze Messfehler, absolut |
| `DEFAULT_TOLERANCE_REL` | 0,01 | Grenze Messfehler, relativ zum Umfang |

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

Die Schlussecke ist standardmäßig `Unspecified` und darf als `Measured` erfasst werden. `DeclaredRight` ist an ihr ebenfalls erlaubt.

**Sollwert der Schlussecke:** Ist sie `Unspecified`, sei `r = 2π·s − Σ_{i≥1} τ̂_i`, normiert auf `(−π, π]`. `τ̂_0` ist der nächstliegende Wert aus `{−90°, 0°, +90°}`. Bei Gleichstand gewinnt der Wert mit dem größeren Betrag und dem Vorzeichen von `s`.

## 4. Raumstatus

```kotlin
enum class ShapeStatus { OPEN, CONFIRMED, NEEDS_ADJUSTMENT, ADJUSTED, PROPOSED, CONFLICT }
```

| Status | Bedeutung | Im Export |
|---|---|---|
| `OPEN` | Weniger als 3 Wände oder nicht abgeschlossen | Nicht exportierbar, Warnung |
| `CONFIRMED` | Form eindeutig, exakt geschlossen | Normal |
| `NEEDS_ADJUSTMENT` | Kleine Lücke, Ausgleich wartet auf Bestätigung | „Form nicht bestätigt“ |
| `ADJUSTED` | Ausgleich bestätigt und zu den Rohwerten passend | Normal |
| `PROPOSED` | Form aus Vorschlag, Zusatzmaße fehlen | „Form nicht bestätigt“ |
| `CONFLICT` | Maße sind geometrisch unmöglich | Nicht exportierbar, Warnung |

### Auswertung `evaluate(room, tolerance)`

1. **Validieren.** Wertebereiche prüfen. Weniger als 3 Wände oder nicht abgeschlossen ergibt `OPEN`.
2. **Notwendige Bedingungen.** Ist `L_max ≥ Σ L_i − L_max − MIN_FEATURE_MM`, folgt `CONFLICT(WALL_TOO_LONG)`. Für jede Diagonale zwischen `a` und `b` teilt sich der Umriss in zwei Ketten. Für jede Kette mit Länge `C` und längstem Glied `M` muss `D ≤ C` und `D ≥ 2M − C` gelten, sonst `CONFLICT(DIAGONAL_IMPOSSIBLE)` mit der betroffenen Diagonale.
3. **Sollform prüfen.** Mit allen Sollwinkeln `τ̂` den Zug ablaufen. Berechne:
   - `g = |Σ L_i (cos φ̂_i, sin φ̂_i)|`
   - `r_k = |d_k(τ̂) − D_k|` für alle Diagonalen
   - `a = |wrap(τ̂_0,gemessen − τ_0,abgeleitet)| · U / (2π)`, falls die Schlussecke gemessen ist, sonst 0
   - `e = max(g, max r_k, a)`
4. **Grenzwert:** `T = max(T_abs, T_rel · U)` mit `U = Σ L_i`.
5. **Verzweigung:**
   - `e ≤ EXACT_CLOSURE_MM` ergibt `CONFIRMED` mit der Sollform.
   - `e ≤ T` ist ein **Messfehler**. Stimmt ein gespeicherter Ausgleich (§6), folgt `ADJUSTED`, sonst `NEEDS_ADJUSTMENT` mit Vorschau.
   - `e > T` ist eine **Formabweichung**. Weiter mit §5.

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

Der Rang `ρ_J` von `J` der harten Bedingungen wird an der Lösung per Gauß-Elimination mit vollständiger Pivotisierung und relativer Schwelle `RANK_TOLERANCE` bestimmt. Die Zahl fehlender Angaben ist `missing = (n − 1) − ρ_J`.

- `missing = 0`: Die Form ist bis auf diskrete Spiegelungen eindeutig. Die Drehrichtungen wählen die Lösung. Status `CONFIRMED`.
- `missing > 0`: Status `PROPOSED`.

**Empfehlung des Zusatzmaßes:** Unter allen Diagonalen zwischen nicht benachbarten Ecken, die den Rang erhöhen, wird die längste empfohlen. Gleichstand wird durch den kleinsten Index `(a, b)` entschieden. Für eine Ecke wird nur dann ein Winkel empfohlen, wenn keine Diagonale den Rang erhöht.

### 5.5 Überbestimmte Messungen

Gibt es mehr unabhängige harte Bedingungen als Variablen, wird der Solver nicht direkt angewendet. Die Raumauswertung führt stattdessen den Ausgleich aus §6 aus. Ist die größte Korrektur kleiner oder gleich `T`, gilt der Fall als Messfehler. Sonst folgt `CONFLICT(MEASUREMENTS_INCONSISTENT)` unter Angabe der Messung mit dem größten normierten Residuum.

## 6. Schließungsausgleich

Methode: gewichtete kleinste Quadrate (ADR 0005). Beobachtungen und Standardabweichungen:

| Beobachtung | σ |
|---|---|
| Wandlänge | 5 mm |
| Diagonale | 5 mm |
| Deklarierter rechter Winkel | 0,5° |
| Gemessener Winkel | 1,0° |

Variablen `x = (L_0…L_{n-1}, τ_0…τ_{n-1}, D_k)`, Startwert sind die Beobachtungen. Eine nicht festgelegte Schlussecke hat keine Beobachtung. Bedingungen `C(x) = 0`: Schließung, `Σ τ_i = 2π·s` und Diagonalen.

Iteration (Gauss-Helmert): `x_{k+1} = x_0 − Q Aᵀ (A Q Aᵀ)⁻¹ (C(x_k) + A (x_0 − x_k))` mit `A = ∂C/∂x` an `x_k` und `Q = diag(σ²)`, Winkel in Radiant. Eine Variable ohne Beobachtung erhält ein festes großes `σ = 10 rad`. Höchstens 50 Schritte, Abbruch bei `‖C‖_∞ ≤ SOLVER_RESIDUAL_MM`.

Das Ergebnis wird als Overlay gespeichert und nie in die Rohwerte geschrieben:

```kotlin
data class ClosureAdjustment(
    val inputFingerprint: Long,
    val solverVersion: Int,
    val adjustedLengthsMm: List<Double>,
    val adjustedTurnsRad: List<Double>,
)
```

`inputFingerprint` ist ein stabiler FNV-1a-64-Hash über alle Rohlängen, Eckangaben und Diagonalen in Indexreihenfolge. Stimmt er nicht mehr, ist das Overlay ungültig und der Status wird zu `NEEDS_ADJUSTMENT` oder neu ausgewertet. Das Zurücknehmen entfernt nur das Overlay.

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
- Wird eine Wand so geändert, dass eine Öffnung ungültig ist, bleibt die Öffnung gespeichert. Sie erhält den abgeleiteten Status `OUT_OF_BOUNDS`, wird rot markiert und vor dem Export gemeldet. Es wird nichts still gelöscht oder verschoben.

## 10. Mehrere Räume

```kotlin
data class RoomPlacement(val originX: Double, val originY: Double, val rotationRad: Double, val dock: DockLink?)
data class DockLink(val hostRoomId: RoomId, val hostWallId: WallId, val ownWallId: WallId, val hostOffset: Length)
```

**Andocken:** Die eigene Wand wird antiparallel zur Wirtswand ausgerichtet. Ihr Startpunkt wird so gelegt, dass der Abstand zwischen den Innenkonturen genau der Stärke der Wirtswand entspricht. `hostOffset` verschiebt den Raum entlang der Wand.

- Ändert sich der Wirtsraum, wird die Platzierung abhängiger Räume in topologischer Reihenfolge neu berechnet.
- Andockungen dürfen keinen Zyklus bilden. Ein solcher Befehl wird abgelehnt.
- Freies Verschieben oder Drehen entfernt den Dock-Link. Raummaße bleiben unverändert.
- Wird der Wirtsraum gelöscht, behalten abhängige Räume ihre Weltposition und verlieren den Link.

**Einrasten:** Reine Funktion in `:core:domain` auf Weltkoordinaten. Kandidaten sind Ecken, Wandlinien und das Raster. Die Standarddistanz beträgt 16 dp, vom Aufrufer in mm umgerechnet. Ecken haben Vorrang vor Wänden, Wände vor dem Raster.

## 11. Befehle und Historie

`ProjectCommand.apply(project): DomainResult<Project>` ist rein und deterministisch. Mindestens: `AddWall`, `EditWall`, `RemoveLastWall`, `SetCorner`, `AddDiagonal`, `RemoveDiagonal`, `CloseRoom`, `AcceptAdjustment`, `RevertAdjustment`, `SetThickness`, `Add/Edit/RemoveOpening`, `AddRoom`, `RemoveRoom`, `MoveRoom`, `DockRoom`, `UpdateRoomDetails`, `SetProjectHeight`.

Ein fehlgeschlagener Befehl ändert weder Projekt noch Historie. Ein neuer Befehl nach Undo verwirft den Redo-Stapel. Jeder `execute` erzeugt genau einen Undo-Schritt. IDs werden beim Erzeugen des Befehls vergeben, damit Redo identische Objekte herstellt.

## 12. Maßstab im Export

`PlotLayout` prüft die Maßstäbe `1:50`, `1:100`, `1:200` in dieser Reihenfolge und je Maßstab A4, dann A3. Die Ausrichtung folgt dem Seitenverhältnis des Plans. Der nutzbare Bereich ist die Seite minus 10 mm Rand und 25 mm Titelblock. Gewählt wird die erste passende Kombination, also der größte Maßstab. Passt der Plan auch bei 1:200 auf A3 nicht, wird der Export mit einer Fehlermeldung abgelehnt.

Umrechnung: `pt = mm_welt / M · 72 / 25,4`. Jeder Plan enthält einen Maßstabsbalken mit Nennlänge.
