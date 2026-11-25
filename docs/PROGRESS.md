# Fortschritt

**Aktuelle Phase:** 4 – Umsetzung, Welle 2
**Letzte Aktualisierung:** 2026-11-24

## Phasen

| Phase | Status | Nachweis |
|---|---|---|
| 0 – Interview | Fertig | `docs/REQUIREMENTS.md`, vom Auftraggeber bestätigt |
| 1 – Umgebung | Fertig | `docs/ENVIRONMENT.md`, Emulator-Boot ausgeführt |
| 2 – Spezifikation | Fertig | Vom Auftraggeber freigegeben am 2026-11-23 |
| 3 – Fundament | Fertig | Gate 2026-11-24 auf `ebe15b1`: `./gradlew build koverVerify checkArchitecture verifyRoborazziDebug` grün, 381 JVM-Tests, Pitest Geometrie 82 %, 2 instrumentierte Tests, Review `APPROVE`. Vertragsfreeze, Tag `phase-3-foundation` |
| 4 – Umsetzung | Welle 1 fertig | Gate 2026-11-24 auf `1a5ec6e`: `./gradlew build koverVerify checkArchitecture verifyRoborazziDebug` grün, 1 058 JVM-Testergebnisse ohne Fehler. Pitest Geometrie 86 % (1 655 von 1 925), 2 instrumentierte Tests, Review `APPROVE`. Tag `wave-1` |
| 5 – Härtung | Offen | |
| 6 – Release | Offen | |
| 7 – Abschlussbericht | Offen | |

## Offene Blocker

Keine.

## Entscheidungslog

- 2026-11-23: Windows-Emulator wird aus WSL über `adb.exe` genutzt, weil KVM in WSL ohne `sudo` nicht zugänglich ist.
- 2026-11-23: Linux-SDK unter `~/Android/Sdk` installiert, Lizenzannahme aus dem Windows-SDK übernommen.
- 2026-11-23: DOMAIN vor dem Vertragsfreeze präzisiert: `DoorSwing`-Werte, `Unspecified` nur an `V_0`, Gleichstandsregel der Schlussecke, Byte-Kodierung des Fingerabdrucks, Rangdefinition, Gültigkeit von Overlays bei neuer `solverVersion`, Andocken an Öffnungen über `hostOpeningId`, zentrale Toleranzen.
- 2026-11-23: T-ARC-002 läuft abweichend vom Batch-Plan nach T-ARC-001, weil es dessen Typen benötigt.
- 2026-11-23: Instrumentation-Runner nach ausdrücklicher Freigabe des Auftraggebers ergänzt. Emulator-Skript real geprüft: grüner Lauf Exit 0 (2 Tests), absichtlich fehlschlagender Test Exit 1.
- 2026-11-23: Vertragsdetails aus T-ARC-001 bestätigt: `ImmutableList` in `ClosureAdjustment`, `RoomTypeKey` als String-Schlüssel, `solverVersion > 0`, `ClosureTolerance.relative` in `[0, 1]`.
- 2026-11-23: ADR 0015: Platzierungen angedockter Räume löst `CommandHistory` über `DockPlacementResolver` auf, Befehle bleiben rein. Dazu Regeln für `RemoveLastWall`, Import, Export und `updatedAtEpochMs` in DOMAIN.
- 2026-11-23: `ProjectEdit` und `ProjectCommandFactory` abgenommen, damit Verträge ohne Stubs auskommen. Trefferpriorität, Titelblock und PNG mit 200 dpi in DOMAIN festgelegt.
- 2026-11-23: Vertragskorrekturen aus dem Review abgenommen (ADR 0016 bis 0018), darunter `suspend`-Historie, Import über `InputStream` und `AdjustmentMode`: Deklarierte 90°-Ecken zählen bei Überbestimmung nicht als Beobachtung.
- 2026-11-23: Konfliktregel für überbestimmte Räume: `S > T` und `q² > χ²(r; 0,99)`. Die reine Summenregel hätte in der Simulation 17 bis 45 % korrekter Räume als Widerspruch gemeldet, die kombinierte Regel rund 1 %. Eine ausdrückliche Bestätigung ist nur im Band zwischen dem 95-%- und dem 99-%-Quantil nötig, weil eine Warnung allein an `S > T` in der Simulation 16 bis 44 % korrekter Räume getroffen hätte. Messungen ohne Gegenprobe sind mathematisch nicht prüfbar und werden mit empfohlenem Zusatzmaß ausgewiesen.
- 2026-11-24: Diskret mehrdeutige Räume werden `PROPOSED`. Die numerische Qualität des Ablaufs wird ab jetzt am Produktionscode über den portierten Prüfstand abgenommen (T-GEO-005, T-GEO-006), nicht in weiteren Prototyp-Runden. Bekannte Grenze im Prototyp (ausgerichteter Formabstand, Abschlusslauf T-ARC-008): 2 von 20 abweichenden Formen in einer Klasse von 2.400 Fällen bleiben unerkannt, dafür werden 19 korrekt bestätigte Räume (0,8 %) zum Vorschlag.
- 2026-11-24: Mehrdeutigkeitsregel gilt auch im Ausgleichspfad (B7). Im Messfehlerpfad (`e ≤ T`) läuft kein Solver, dort entfällt die Prüfung (DOMAIN §5.6 Punkt 7). `AcceptAdjustment` ist nur zulässig, wenn die aktuelle Auswertung `NeedsAdjustment` mit gleichem Vorschlag ist, sonst `ADJUSTMENT_NOT_AVAILABLE`.
- 2026-11-24: ADR 0019: Befehle haben eine optionale `suspend`-Vorbedingung `verify`, die `CommandHistory.execute` unter dem Mutex mit der aktuellen Toleranz vor `apply` prüft. `AcceptAdjustment` lehnt damit veraltete Vorschläge ab. `ClosureTolerance` verlangt `absoluteMm ≥ 1`.
- 2026-11-24: Phase-3-Gate nach vier Review-Runden bestanden. Verträge in `:core:*` sind eingefroren, Änderungen nur per Change-Request (WORKFLOWS §5).
- 2026-11-24: Erster GitHub-Actions-Lauf auf `f6a61e9` grün: Wrapper-Validierung, Build mit Tests, Lint und Coverage sowie instrumentierte Tests (API 36, x86_64).
- 2026-11-24: `:core:document` nutzt `kotlinx-serialization-json` (aus dem Version Catalog) statt eines eigenen Parsers und erhält `:testing:fixtures` als Testabhängigkeit.
- 2026-11-24: `kotest-property` ist über `plansnap.jvm.library` in allen JVM-Modulen als Testabhängigkeit verfügbar. Längen werden nicht gerundet: Eingaben feiner als 1 mm werden abgelehnt. Längenanzeige mit festen Nachkommastellen je Einheit (mm 0, cm 1, m 3), Winkelanzeige ohne überflüssige Nullen.
- 2026-11-24: ktlint erlaubt PascalCase nur für `@Composable`-Funktionen, nach ausdrücklicher Freigabe des Auftraggebers (der Auto-Modus hatte die Änderung beim Agenten abgelehnt).
- 2026-11-24: `:core:data` erhält Room (Runtime, KTX, Compiler über KSP, Schema-Export nach `core/data/schemas`), DataStore Preferences sowie Testabhängigkeiten. `androidx-room-testing` mit der Room-Version aus dem Catalog ergänzt, für den Migrationstest-Harness. Hilt folgt mit der DI-Wurzel (T-APP-001).
- 2026-11-24: `:core:geometry` hat `:testing:fixtures` als Testabhängigkeit erhalten (`9c0ef23`). Es gibt keinen Zyklus, weil die Fixtures nur die Hauptausgabe nutzen. Geprüft mit Test, Pitest, Kover und `checkArchitecture`. Der leere T-DAT-001-Branch wurde verworfen, T-DAT-001 wurde neu gestartet.
- 2026-11-24: Gespeichertes `absoluteMm` ist nur in `MIN_TOLERANCE_ABS_MM..MAX_WALL_MM` (1 bis 100 000) gültig. Werte außerhalb fallen je Feld auf den Standard zurück. Eine Toleranz über der größten Wandlänge ist sinnlos.
- 2026-11-24: T-DAT-003 gemergt (`f5ccf27`, 13 Tests). Werte mit falschem gespeicherten Typ fallen je Feld auf den Standard zurück. `update` mit ungültigem `absoluteMm` liefert `InvalidValue(TOLERANCE, TOO_LARGE)` und schreibt nichts.
- 2026-11-24: T-DAT-001 gemergt (`99fba85`, 16 Tests). Schema 1 mit `projects`, `rooms`, `editor_drafts`, Fremdschlüssel mit CASCADE. `@Upsert` statt REPLACE, weil REPLACE per Cascade die Entwürfe löschen würde. Kein UNIQUE auf `(projectId, position)`, damit Umsortieren nicht kollidiert. Die DAOs speichern das Raumdokument als JSON-String, Kodierung und Formatversion übernimmt T-DAT-002 über `DocumentCodec`.
- 2026-11-24: T-GEO-001 gemergt (`f687756`). Die Referenzfälle laufen direkt über `ReferenceRooms.ALL`, `AnalyticShapes` ist entfallen. Abstandsgrenze in `isSimple` inklusiv getestet. Pitest Geometrie 78 % (482 von 615).
- 2026-11-24: Schema 1 wird vor der Auslieferung korrigiert: `editor_drafts` erhält `projectId` als Primärschlüssel und ein nullbares `roomId`, weil der eingefrorene Vertrag einen Entwurf je Projekt mit optionalem Raum vorsieht. Die Korrektur gehört zu T-DAT-002, das den Pfad `db/` nach dem Merge von T-DAT-001 übernimmt. Eine Migration ist nicht nötig, weil Schema 1 noch nicht ausgeliefert ist.
- 2026-11-24: T-DAT-002 gemergt (`cc75c72`, 79 Tests in `:core:data`). Schema 1 korrigiert: `editor_drafts` mit Primärschlüssel `projectId`, nullbarem `roomId` und Spalte `inputText`. Der Saver serialisiert über einen fairen Mutex, jedes `save` läuft in genau einer Transaktion. Duplizieren vergibt neue IDs für alle Elemente, der Name bleibt gleich. Import behält die IDs und speichert bei Kollision eine Kopie.
- 2026-11-24: T-GEO-002 (`3b8d65e`) und T-GEO-003 (`d4a19d1`) gemergt. Geometrie: 242 Tests, Pitest 83 % (592 von 717). Rechtwinklige Sollformen sind ganzzahlig exakt. Ein Werttest verbietet nicht-strikte Mathematik im Paket `traverse`.
- 2026-11-24: Planungslücke geschlossen: Keine Aufgabe implementierte `ProjectValidator`, den `RoomProjectRepository` für den Import braucht. Neue Aufgabe T-DOM-007 in Batch 2.3, nach T-DOM-003 und T-DOM-004 und vor der Verdrahtung in T-APP-001.
- 2026-11-24: T-GEO-006 gemergt (`222f2d6`). Geometrie: 293 Tests, Pitest 85 % (873 von 1 023). Alle 30 Katalogfälle laufen am Produktionscode. Die Schließung zählt in `‖C‖_∞` als euklidische Lücke, sonst blieb eine Lücke von 0,0128 mm über dem Kriterium von 0,01 mm. Nachgerechnet am Produktionscode: Falsche Konflikte (0,87 bis 1,04 %) und ausdrückliche Bestätigungen (3,8 bis 4,1 %) entsprechen ADR 0018 und der χ²-Theorie. Zwei Werte weichen ab: große Korrekturen 19 bis 63 % (ADR 12 bis 40 %) und unerkanntes Band bis 1,72 T (ADR bis 1,57 T). Das Skript der ADR fehlt im Repository, den Abgleich übernimmt `architect` (CR-W1-01).
- 2026-11-24: Folgepunkte aus T-GEO-006: `ShapeDistance.kt` nutzt `kotlin.math.atan2` entgegen DOMAIN §11, das wird Teil von T-GEO-005. Der Katalogtest bezieht die Startdrehungen für Zweig B1 vorläufig aus Prototyp-Läufen (`CatalogReferenceRuns.kt`). Die Umstellung auf den Produktions-Solver wird Teil von T-GEO-007.
- 2026-11-24: CR-W1-01 gemergt (`e677a33`). Die Konvergenznorm des Ausgleichs ist `max(|(c_x, c_y)|, |c_τ| · U/(2π), max_k |c_k|)` in mm und wird vor jedem Schritt geprüft. Bei `worstObservation` gelten normierte Residuen innerhalb `SOLVER_RESIDUAL_MM / SIGMA_LENGTH_MM` als gleich. Dann gewinnt die erste Beobachtung in der Reihenfolge Wände, Ecken, Diagonalen. Simulation: Die Zeile „große Korrektur“ war im Test falsch definiert, weil der Test jede Stichprobe durch Zweig B schickte. Neu gemessen am Produktionscode: 12,5 / 23,8 / 26,7 / 40,2 %, passend zu ADR 0018. Die ADR-Zeile „unerkanntes Band“ ist nicht reproduzierbar. Produktion, Prototyp und lineare Näherung ergeben übereinstimmend 1,69 / 1,73 / 1,49 / 1,64 T. Die ADR ist per Ergänzung korrigiert.
- 2026-11-24: Defekt gefunden: `AdjustmentProblem.residual()` vergleicht die Winkelsummenzeile in Radiant mit der mm-Grenze. Ein Rechteck mit 0,3° Winkelfehler endet ohne Schritt mit einer Winkelsumme von 360,3°. Fix-Aufgabe T-GEO-008. Der Prototyp hat denselben Fehler, im Prototyp-README als Abweichung vermerkt.
- 2026-11-24: T-GEO-004 gemergt (`8a91899`). Geometrie: 356 Tests, Pitest 85 %. Trapez 70,53°/109,47° mit 11,314 m², mit Diagonale 4 m 10,472 m². Abweichungen vom Prototyp: Gradient `1e-9`, Ausdünnen über die Rangbestimmung statt `lstsq`, freie Drehungen werden für die Auswahlregel auf (−π, π] gewrappt. Bekannte Grenze: Die SQP stagniert teils kurz vor `SOLVER_GRADIENT` (Maratos-Effekt wie im Prototyp). Die Form ist dann zulässig, aber nur auf etwa 1e-6 rad optimal.
- 2026-11-24: T-GEO-005 startet parallel zu T-GEO-008 und übernimmt den Fix vor den finalen Prüfstandsmessungen. T-GEO-007 folgt nach beiden und stellt den Katalogtest auf Startdrehungen des Produktions-Solvers um.
- 2026-11-24: T-GEO-008 gemergt (`674494a`). Vorher rot (4 Tests), danach grün. Geometrie: 362 Tests, Pitest 85 %. Katalog-, Property- und Simulationserwartungen sind unverändert. `DefaultClosureAdjuster.VERSION` bleibt 1, weil noch nichts ausgeliefert ist und es deshalb keine Overlays aus dem fehlerhaften Stand gibt. Versionssprünge gelten erst ab dem ersten Release.
- 2026-11-24: T-GEO-007 gemergt (`cf80edc`). Geometrie: 435 Tests, Kover 99,9 % Zeilen und 97,6 % Branches, Pitest 86 %. Alle 13 Referenzräume laufen durch die gemergte Pipeline. Der Katalogtest bezieht die B1-Startdrehungen jetzt vom `DefaultShapeSolver`, alle 30 Fälle bleiben mit unveränderten Erwartungen grün.
- 2026-11-24: T-GEO-005 gemergt (`8561dbd`). Geometrie: 628 Tests, Pitest laut Agent 86 % (Pitest-Laufzeit rund 11 Minuten wegen des Prüfstands). Prüfstand am Produktionscode mit voller README-Stichprobe (12 000 Fälle, Seed 20261124): alle Sollwerte erfüllt. K1a, K3 und K4 sind identisch mit dem Prototyp. K1b hat 11 falsche CONFLICT (0,46 %, Prototyp 17), K2 hat 0 nicht einfache Umrisse und 0 bestätigte Formen mit verletzter Richtung. Die Fälle sind identisch mit `stress.py`, weil `PythonRandom` MT19937 nachbildet. `ShapeDistance` nutzt jetzt `StrictMath.atan2`.
- 2026-11-24: Offener Katalogpunkt (nicht blockierend, in BACKLOG): Die Trapezfälle h2a-109471 und h2a-109472 melden die Wände w1 und w3 als nicht kontrollierbar, das ist plausibel bei `r_i < 0,0025`. Der Katalog nennt dort keine Einträge. `architect` soll den Katalog ergänzen, sobald `:testing:fixtures` wieder bearbeitet wird.
- 2026-11-24: Gate-Review Welle 1: `APPROVE`, keine kritischen oder hohen Befunde. Mittel: Die Standardhöhe wurde als `Double` gespeichert und verliert Werte über 2^53. Das wird vor der Auslieferung von Schema 1 in T-DAT-004 behoben. Niedrig: Corruption-Handler der DataStore geht in T-APP-001, die Fehlerabbildung von `observeProjects` in den Backlog. T-APP-001 hängt jetzt auch an T-DOM-007.
- 2026-11-24: T-DAT-004 gemergt (`1a5ec6e`): Die Standardhöhe wird als INTEGER gespeichert, Schema bleibt Version 1. Regressionstests vorher rot, danach grün.
- 2026-11-24: Wellen-Gate 1 bestanden. Pitest und instrumentierte Tests liefen auf `9507601`. Danach kamen nur Dokumentation und T-DAT-004 in `:core:data` hinzu. Darauf lief der volle Build erneut grün.
- 2026-11-24: CI-Lauf für `wave-1` (`eced75c`) grün. Es gibt noch keine testbare APK, weil `:app` keine Activity und die Feature-Module keinen Code haben. Die erste bedienbare App folgt mit T-APP-001 (Welle 2) und den Screens aus Welle 3.
- 2026-11-24: `:core:domain` und `:core:drawing` erhalten `:testing:fixtures` als Testabhängigkeit. Geprüft mit Test und `checkArchitecture`.
- 2026-11-24: T-DOM-005 gemergt (`ba53ee6`, 10 Tests). Fläche und Umfang kommen ungerundet aus der angezeigten Form, gerundet wird erst am Anzeigerand (UI, Export). Räume ohne Form (`OPEN`, `CONFLICT`) fehlen in den Summen. T-DOM-006 wird vorgezogen, weil seine Abhängigkeit T-GEO-001 erfüllt ist.
- 2026-11-24: T-DOM-006 gemergt (`d610cea`-Inhalt, 19 Tests). Die erste qualifizierende Art gewinnt in der Reihenfolge Ecke, Wand, Raster, innerhalb einer Art der nächste Kandidat. Die Snap-Distanz gilt inklusiv in Weltkoordinaten, die Umrechnung von 16 dp macht der Aufrufer. Abgeschaltet liefert das Einrasten die Eingabe bitgleich zurück.
- 2026-11-24: T-DOM-002 gemergt (`e4d0b75`). `:core:domain` hat 121 Tests. Die Historie nutzt einen fairen Mutex: Vorbedingung, `apply` und Auflösung laufen in `withContext`, danach folgen `ensureActive` und der Commit. Ein Property-Test mit 300 × 40 Schritten zeigt, dass Undo und Redo Identitäten sind. Festlegungen ohne ausdrückliche Spezifikation: `EditWall` lässt das Overlay stehen, der Fingerabdruck macht es ungültig. Nur `RemoveLastWall` an einem geschlossenen Raum verwirft es. `DockRoom` prüft Ziele und Zyklen im Modell, die Platzierung rechnet T-DOM-004. Offen: Den ADR-0019-Zahlenfall gegen den echten Evaluator prüfen, sobald T-DOM-001 gemergt ist.
- 2026-11-24: T-DRW-002 gemergt (`:core:drawing` 97 Tests, Zeilenabdeckung 100 %). Vorläufige Festlegungen, an `architect` als CR-W2-01: Zoomgrenzen 1e-4 bis 100 px/mm, Trefferradius 24 dp, Trefferpriorität Öffnung > Ecke > Wand > Diagonale > Rauminneres, relative Gleichstandstoleranz 1e-9 (soll nach `GeometryTolerances`). Hinweis für T-DRW-001: Ecken sind nur treffbar, wenn die Szene für jede Ecke ein Element mit Ziel `SceneTarget.Corner` erzeugt.
- 2026-11-24: T-DOM-003 gemergt (`75a9e95`). `:core:domain` hat 153 Tests. Gültig nach DOMAIN §9 ist `offset + width ≤ L_wirksam + EXACT_CLOSURE_MM`. `L_wirksam` ist die ausgeglichene Länge, solange das Overlay zum Fingerabdruck passt, sonst die Rohlänge. Die Verdrahtung von Öffnungsregeln und Andock-Resolver ist in T-APP-001 nachgetragen.
- 2026-11-24: CR-W2-01 gemergt (`4a4120e`). Bestätigt und festgeschrieben: Zoom 1e-4 bis 100 px/mm, begründet mit dem größten gültigen Raum und der kürzesten Wand (UX §4). Trefferpriorität Öffnung > Ecke > Wand > Diagonale > Rauminneres (DOMAIN §11). `GeometryTolerances.HIT_TIE_RELATIVE = 1e-9`, additiv (DOMAIN §1). Trefferradius 24 dp. Doppeltippen mit 24 dp Rand ist in T-EDT-003 aufgenommen.
- 2026-11-25: T-DOM-001 gemergt (`2074112`). `:core:domain` hat 328 Tests. Alle 30 Katalogfälle liefern über den Produktions-Evaluator Status, Grund und Bestätigungsstufe. Für den Zahlenfall aus ADR 0019 ergibt die Auswertung SIMPLE bei `T_abs = 250` und EXPLICIT bei Standardtoleranz, wie es der Fake in T-DOM-002 angenommen hat. Ein Integrationstest mit echter Historie und echtem Evaluator wird Teil von T-DOM-007. A3 prüft die tatsächlich entstehende Drehung mit abgeleitetem `τ_0`. Folgepunkte: T-GEO-009 (Verdacht auf übersehene Richtungsverletzung an `V_0` im Solver) und CR-W2-02 (`ValueField` ohne Wandanzahl, der Evaluator liefert `RuleViolation(TOO_MANY_WALLS)`).

## Wiedereinstieg (Stand 2026-11-25)

- **Welle 1:** fertig, Tag `wave-1`, CI grün.
- **Welle 2 gemergt:** T-DOM-001, T-DOM-002, T-DOM-003, T-DOM-005, T-DOM-006, T-DRW-002, CR-W2-01.
- **Welle 2 laufend** (je eigener Worktree unter `.claude/worktrees/agent-*`, Branch `worktree-agent-*`):
  - T-DOM-004 (Platzierung und Andocken). Basis `a60b7d5`, ungesicherte Dateien unter `core/domain/…/placement/`. Einmal durch 429 unterbrochen, per `SendMessage` fortgesetzt.
  - T-DRW-001 (SceneBuilder), Basis `5837c4b`.
  - T-GEO-009 (Fix: Drehrichtung an der Schlussecke), Basis `5837c4b`.
  - CR-W2-02 (Fehlercode der Wandanzahl, `architect`), Basis `5837c4b`.
- Vor dem Fortsetzen je Worktree `git -C <worktree> status` und `git log main..<branch>` prüfen. Fertige Commits übernehmen. Sonst einen neuen Agenten auf dem Worktree-Stand starten.

## Nächste Schritte

1. Die laufenden Aufgaben prüfen und übernehmen.
2. Danach T-DOM-007 (nach T-DOM-004, mit Integrationstest des ADR-0019-Zahlenfalls über echte Historie und echten Evaluator), T-RND-001 und T-EXP-001 (nach T-DRW-001), zuletzt T-APP-001.
3. Wellen-Gate 2 mit Review, Tag `wave-2`, Push, CI prüfen.
