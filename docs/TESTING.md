# PlanSnap – Teststrategie

## 1. Pyramide

| Ebene | Anteil | Werkzeuge | Inhalt |
|---|---|---|---|
| Unit (JVM) | ca. 70 % | JUnit 6, Kotest Property, Turbine, Coroutines Test | Geometrie, Solver, Domäne, Codec, Szene, Formatierung, ViewModels |
| Integration | ca. 20 % | Robolectric, Room-Testhelfer, Golden-Dateien | Datenbank, Migration, Repositories, Export, Roundtrip |
| UI/E2E | ca. 10 % | Compose UI Test, Roborazzi, Instrumentierung, UIAutomator | Screens, Flows, Prozess-Tod, Performance |

Android-Unit-Tests nutzen JUnit 4 mit Robolectric, reine JVM-Module JUnit 6. Fakes aus `:testing:fixtures` haben Vorrang vor MockK.

## 2. Konventionen

- Testnamen beschreiben Verhalten: `` `given closed square when evaluated then status is confirmed` ``.
- Deterministisch: keine echte Uhr, Zufall nur mit festem Seed, Dispatcher injiziert, kein `Thread.sleep`.
- Property-Tests laufen mit festem Seed und mindestens 1 000 Iterationen im Geometriekern.
- Tests sind voneinander unabhängig. Golden-Dateien liegen unter `src/test/resources/golden` oder `:testing:fixtures`.
- Eine Golden-Datei wird nur mit Begründung im Commit aktualisiert.
- Deaktivierte Tests sind verboten, außer mit ADR und Backlog-Eintrag.

## 3. Pflichttests Geometriekern

**Analytische Referenzfälle** (Fläche, Umfang, Status): Rechteck, Quadrat, L, U, T, Raum mit Nische, Raum mit Erker, Schräge, konkave Ecken, sehr kleiner Raum (10-mm-Wände), sehr großer Raum (100-m-Wände), Trapez 3 × 3 × 3 × 5 m.

**Properties:**

1. Fläche und Umfang sind invariant unter Rotation und Translation.
2. Die Fläche ändert sich nicht durch Wandstärken.
3. Nach dem Ausgleich gilt Schließungsfehler ≤ `SOLVER_RESIDUAL_MM`.
4. Ausgleich und Rücknahme lassen Rohwerte bitgenau unverändert.
5. Der Solver hält alle harten Bedingungen innerhalb der Toleranz ein.
6. Gleiche Eingabe liefert bitgenau dieselbe Lösung.
7. `undo ∘ execute` und `redo ∘ undo` sind die Identität.
8. Serialisierung ist verlustfrei.
9. Viewport: `screenToWorld ∘ worldToScreen` ist die Identität.

**Rand- und Fuzz-Fälle:** Länge 0, negative Längen, Überlauf, NaN und Unendlich an der Grenze, Selbstüberschneidung, kollineare Ecken, unmögliche Diagonalen, Wand länger als alle anderen zusammen, Fehlerketten, 128 Wände, zufällige Polygone.

**Mutationstests:** Pitest auf `:core:geometry`, Ziel mindestens 70 % getötete Mutanten, ausgewertet am Ende von Welle 1 und in Phase 5.

## 4. Abdeckungsziele (Kover-Gate)

| Bereich | Zeilen | Branches |
|---|---|---|
| `:core:geometry` | ≥ 90 % | ≥ 85 % |
| `:core:common`, `:core:model`, `:core:domain`, `:core:document`, `:core:drawing`, `:core:data` | ≥ 80 % | – |
| ViewModels in `:feature:*` | ≥ 80 % | – |
| Composables | Keine Abdeckungsquote, aber Screen- und Flow-Abdeckung | – |

## 5. Integration

- **Room:** In-Memory-DAO-Tests. Ab Schema 2 hat jede Migration einen Test mit `MigrationTestHelper`.
- **Format:** Golden-Datei je `formatVersion`, Migration, Ablehnung neuerer Hauptversionen, unbekannte Felder, ungültige Werte, Größenlimit.
- **Export:** SVG und JSON als Golden-Dateien, Szene und `PlotLayout` als JVM-Golden. PDF: Seitengröße und Kalibrierbalken instrumentiert über `PdfRenderer`, Toleranz ±0,5 mm bei 300 dpi.

## 6. Screenshots

Roborazzi für Projektliste, Editor (leer, laufend, Vorschlag, Ausgleich, Konflikt), Export und Einstellungen. Varianten: Hell, Dunkel, DE, EN und Schriftgröße 200 %.

Screenshot-Baselines liegen je Modul unter `src/test/screenshots`, Diff-Bilder unter `build/outputs/roborazzi`. Tests rufen `captureRoboImage("name.png")` mit `changeThreshold = 0f` auf, sofern keine Toleranz vereinbart ist. Baselines gelten nur für Linux (lokal WSL, CI Ubuntu).

## 7. Pflicht-E2E-Szenarien (Gate Phase 5)

| Nr. | Szenario |
|---|---|
| E2E-1 | Neues Projekt, 4 Wände, Umriss schließt, Fläche korrekt, App neu starten, Projekt unverändert |
| E2E-2 | L-Raum mit Winkel und Messfehler, Ausgleich, Rohdaten bleiben erhalten |
| E2E-3 | Tür und Fenster setzen, Wand ändern, Öffnung bleibt konsistent oder wird gemeldet |
| E2E-4 | Mehrere Räume, PDF-Export im Maßstab, Maße stimmen |
| E2E-5 | Rotation und Prozess-Tod mitten in der Eingabe, kein Datenverlust |
| E2E-6 | Projekt mit 200 Wänden und 30 Räumen bleibt flüssig |
| E2E-7 | Undo/Redo über eine komplette Sitzung |
| E2E-8 | 3 × 3 × 3 × 5 m ergibt Vorschlag, Diagonale bestätigt die Form, Kennzeichnung im Export verschwindet |

## 8. Nichtfunktionale Messungen

| Ziel | Messung | Grenzwert |
|---|---|---|
| Editor | Macrobenchmark `FrameTimingMetric` mit 200 Wänden, 30 Räumen | P95 Frame ≤ 16,7 ms auf dem Referenzgerät |
| Kaltstart | `StartupTimingMetric` | Median < 2 s |
| Solver | JVM-Messung, nur als Orientierung | Raum mit 32 Wänden < 50 ms |
| Berechtigungen | Prüfung des zusammengeführten Manifests | Kein `INTERNET`, keine gefährlichen Berechtigungen |

Emulatorwerte sind nur ein Anhaltspunkt. Kaltstart und Flüssigkeit gelten erst nach Messung auf dem echten Handy als bestätigt. Bis dahin werden sie im Abschlussbericht als „nicht auf Echtgerät verifiziert“ markiert.

## 9. Umgebung

| Testart | Lokal WSL | Windows-Emulator | GitHub Actions |
|---|---|---|---|
| JVM, Robolectric, Screenshots | Ja | – | Ja |
| Instrumentiert | – | Ja, über `adb.exe` | Ja |
| Macrobenchmark | – | Nur Anhaltspunkt | Nicht als Gate |
| Echtgerät | – | – | Nur durch den Auftraggeber |
