# Prüfstand der Raumauswertung

Forschungs- und Referenzcode zu DOMAIN §4 bis §7 und ADR 0018. Er ist **kein Produktionscode**, gehört nicht zum Gradle-Build und wird von keinem Modul geladen. Er dient zwei Zwecken:

1. `flow.py` bildet den Entscheidungsablauf `evaluate(room, tolerance)` einschließlich Solver, Ausgleich, Rang und Mehrdeutigkeit (§5.6) unabhängig vom Kotlin-Code nach. Damit wurden die Referenzfälle in `testing/fixtures` (`OverdeterminedCases`) berechnet und geprüft.
2. `stress.py` prüft den Ablauf mit Zufallsräumen bekannter Wahrheit. T-GEO-005 portiert diesen Prüfstand als seeded Test an den Produktionscode und muss die Sollwerte unten erreichen.

## Voraussetzungen

- Python 3 mit **numpy** (geprüft mit numpy 2.3.5). numpy ist hier erlaubt, weil der Code nur der Forschung und als Referenz dient. Der Produktionscode verwendet eigene Dense-Matrizen (DOMAIN §5.2).
- Ein Rechner mit mehreren Kernen. Der Prüfstand nutzt 6 Prozesse.

## Aufruf

Aus dem Wurzelverzeichnis des Repositorys:

```bash
python3 -I -B docs/research/evaluation-prototype/stress.py
```

- Optionales erstes Argument: Fälle je Klasse, Standard 2400 (zusammen 12 000 Fälle).
- `--no-ambiguity` schaltet die Regel aus DOMAIN §5.6 ab und dient nur dem Vergleich.
- Seed ist fest `SEED = 20261124`. Jeder Fall hat einen eigenen, aus Seed und Fallnummer abgeleiteten Zufallsgenerator. Das Ergebnis hängt daher nicht von der Prozesszahl oder der Reihenfolge ab.
- Laufzeit auf dem Entwicklungsrechner rund 25 s.
- Ergebnis-Logs werden nicht eingecheckt.

## Klassen

Wahre Formen sind zufällige konvexe, konkave oder rechtwinklige Polygone mit 3 bis 12 Ecken, mindestens 150 mm Kantenlänge, einfach mit 30 mm Mindestabstand und Drehungen unter 170°. Sie werden zufällig gedreht, gespiegelt und mit zufälliger Startecke angelegt. Längen werden auf ganze mm, Winkel auf ganze Milligrad gerundet. `T = max(50 mm, 1 % U)`.

| Klasse | Eingabe | Erwartung |
|---|---|---|
| K1a, ehrlich | Deklariert nur an echten 90°-Ecken (80 %), sonst gemessen. Schlussecke zu 65 % frei. 0 bis 4 Diagonalen, zu 30 % bis zu 2 Wiederholungsmessungen einer Wand. | Kein `CONFLICT`, kein nicht einfacher Umriss. Eine bestätigte Form liegt im Formabstand (§5.6) höchstens `T` von der Wahrheit entfernt. |
| K1b, Formabweichung | Wie K1a, aber 60 % aller Ecken als 90° deklariert, unabhängig vom wahren Winkel. | Wie K1a. Ein `CONFLICT` zählt nur dann nicht als falsch, wenn die gerundeten Werte eine notwendige Bedingung nachweislich verletzen. |
| K2, falsche Richtungen | 60 %: K1b-Räume mit 1 bis 2 umgedrehten Drehrichtungen. 40 %: Züge mit 4 bis 10 Wänden aus 1000 bis 4000 mm und zufälligen Richtungen. | Kein nicht einfacher Umriss in `CONFIRMED`, `NEEDS_ADJUSTMENT` oder `PROPOSED`. Kein `CONFIRMED` mit verletzter Richtung. |
| K3, unterbestimmt | Mindestens zwei Ecken weichen mehr als 3° von 90° ab. Freie Schlussecke, keine Diagonalen, bis zu `n − 4` gemessene Ecken, die übrigen deklariert. | Kein `CONFIRMED`, kein nicht einfacher Umriss. |
| K4, Grenzwerte | Dreiecke, Kleinstdreiecke mit 10 bis 40 mm, kollineare und fast kollineare Ecken, spitze und dünne Formen, 0 bis 2 Diagonalen. Jeder fünfte Fall ist ein Rechteck 4000 × 3000 mm mit Lücke 0, 1, 2, 49, 50, 51, 139, 140 oder 141 mm. | Wie K1a. Beim Lückenfall bis 1 mm `CONFIRMED`, bis `T` `NEEDS_ADJUSTMENT`. |

Jede Ausnahme im Ablauf zählt als Befund (`crash:…`), nie als übersprungener Fall. Zeilen mit `info-` sind Kennzahlen, keine Abweichungen.

## Ergebnis mit Regel (Seed 20261124, 12 000 Fälle)

| Klasse | Fälle | Status | Abweichungen und Kennzahlen |
|---|---|---|---|
| K1a | 2 400 | 2 017 `CONFIRMED`, 383 `NEEDS_ADJUSTMENT` | keine |
| K1b | 2 400 | 1 510 `CONFIRMED`, 184 `NEEDS_ADJUSTMENT`, 689 `PROPOSED`, 17 `CONFLICT` | 17 falsche `CONFLICT` (0,71 %), 2 bestätigte Formen weiter als `T` von der Wahrheit, 42 × `AMBIGUOUS_SHAPE` |
| K2 | 2 400 | 21 `CONFIRMED`, 16 `NEEDS_ADJUSTMENT`, 1 491 `PROPOSED`, 872 `CONFLICT` | keine, 1 × `AMBIGUOUS_SHAPE` |
| K3 | 2 400 | 2 393 `PROPOSED`, 1 `NEEDS_ADJUSTMENT`, 6 `CONFLICT` | keine falsche Bestätigung, 6 `CONFLICT` (0,25 %) |
| K4 | 2 400 | 1 920 `CONFIRMED` in sechs Formklassen, Lückenfall 87 `CONFIRMED` und 393 `NEEDS_ADJUSTMENT` | keine |

Die 42 mehrdeutigen Räume in K1b nach Pfad und Lage der angezeigten Form:

| Pfad | Ohne Regel | Angezeigte Form weicht ab | Angezeigte Form stimmt |
|---|---|---|---|
| A4 | `CONFIRMED` | 7 | 8 |
| B7 | `CONFIRMED` (B8) | 11 | 11 |
| B7 | `NEEDS_ADJUSTMENT` (B9) | 4 | 1 |

Ohne Regel wären also 20 abweichende Formen `CONFIRMED`. Die Regel erkennt 18 davon. 2 bleiben bestätigt, weil die festen Startwerte die zweite Lösung nicht finden (Suchgrenze, ADR 0018). 19 richtig bestätigte Räume (0,8 % von K1b) werden zusätzlich zum Vorschlag. Das ist gewollt, weil eine zweite gültige Form existiert. Im Ausgleichspfad werden 5 Räume statt `NEEDS_ADJUSTMENT` zum Vorschlag, 4 davon mit abweichender angezeigter Form.

## Sollwerte für T-GEO-005

Der portierte Test läuft mit demselben Seed, denselben Klassen und `SOLVER_GRADIENT = 1e-9`.

| Klasse | Sollwert |
|---|---|
| alle | 0 Ausnahmen |
| K1a | 0 Abweichungen |
| K1b | falsche `CONFLICT` höchstens 1 % (Prototyp 17 von 2 400). Bestätigte Formen weiter als `T` von der Wahrheit: Richtwert 2. Jede Mehrzahl ist im Bericht mit Beispiel zu begründen. |
| K2 | 0 nicht einfache Umrisse, 0 `CONFIRMED` mit verletzter Richtung |
| K3 | 0 `CONFIRMED`, 0 nicht einfache Umrisse, `CONFLICT` höchstens 1 % (Prototyp 6 von 2 400) |
| K4 | 0 Abweichungen, Lückenfälle nach DOMAIN §4 |

Statuszahlen dürfen wegen des strengeren Gradienten leicht abweichen. Abweichungen gegenüber der Tabelle oben gehören in den Bericht. Zusätzlich müssen alle 30 Fälle aus `OverdeterminedCases` am Produktionscode bestehen. Der Prototyp liefert für alle 30 den erwarteten Status und Grund.

## Bekannte Abweichungen vom Vertrag

| Punkt | Prototyp | Vertrag |
|---|---|---|
| Abbruch des projizierten Gradienten im Solver | `1e-7` | `SOLVER_GRADIENT = 1e-9` (DOMAIN §1, §5.2) |
| Lineare Algebra | numpy, `lstsq` als Rückfall bei singulärem KKT-System | eigene Dense-Matrizen, Ausdünnen über die Rangbestimmung (§5.2) |
| Abbruch | nicht vorgesehen | `CancellationSignal` bis in Solver, Ausgleich und Rang |

Maßgeblich ist immer DOMAIN. Widerspricht der Prototyp DOMAIN, ist der Prototyp falsch.
