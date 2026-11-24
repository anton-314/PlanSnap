# Fortschritt

**Aktuelle Phase:** 4 – Umsetzung, Welle 1
**Letzte Aktualisierung:** 2026-11-24

## Phasen

| Phase | Status | Nachweis |
|---|---|---|
| 0 – Interview | Fertig | `docs/REQUIREMENTS.md`, vom Auftraggeber bestätigt |
| 1 – Umgebung | Fertig | `docs/ENVIRONMENT.md`, Emulator-Boot ausgeführt |
| 2 – Spezifikation | Fertig | Vom Auftraggeber freigegeben am 2026-11-23 |
| 3 – Fundament | Fertig | Gate 2026-11-24 auf `ebe15b1`: `./gradlew build koverVerify checkArchitecture verifyRoborazziDebug` grün, 381 JVM-Tests, Pitest Geometrie 82 %, 2 instrumentierte Tests, Review `APPROVE`. Vertragsfreeze, Tag `phase-3-foundation` |
| 4 – Umsetzung | Offen | |
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

## Wiedereinstieg (Stand 2026-11-24, Nutzungslimit)

- **Phase 3:** fertig, Tag `phase-3-foundation` gepusht, CI grün.
- **Welle 1 auf `main` (lokal, nicht gepusht):** T-COM-001, T-DES-001, T-DOC-001 gemergt. Build-Vorbereitung für `:core:data` (Room, DataStore, KSP) und `kotest-property` gemergt.
- **Unterbrochen, je in eigenem Worktree unter `.claude/worktrees/`:**
  - T-GEO-001 auf Branch `worktree-agent-a8663ab4c0ee1175e`: WIP-Commit `8c9ffc8`. Letzter DoD-Lauf grün (Pitest 78 %), aber ohne zwei neue Distanztests in `DefaultPolygonAnalyzerTest`. Entschieden: `:core:geometry` erhält `testImplementation(project(":testing:fixtures"))`, damit die Tests `ReferenceRooms` direkt nutzen statt gespiegelter Werte in `AnalyticShapes.kt`. Die Build-Zeile trägt der Orchestrator ein und prüft, dass keine Zyklusprobleme entstehen.
  - T-DAT-001 auf Branch `worktree-agent-aa680b4dec6a9aed5`: nur ein leerer WIP-Commit `b798b02`, keine Dateien. Ein zu komplexer Befehl zum Anlegen der Dateien wurde vom Harness abgelehnt. Die Aufgabe neu von `main` aus starten, Dateien einzeln anlegen. Entschieden: Die Tabelle `editor_drafts` gehört laut ARCHITECTURE §7 und ADR 0006 schon in Schema 1, damit T-DAT-002 keine Migration braucht.
  - T-DAT-003 auf Branch `worktree-agent-aeeeee5291a30bc39`: WIP-Commit `f9ff5a3`, nur `DataStoreSettingsRepository.kt`, nicht kompiliert, Tests fehlen. Offen: Obergrenze für `absoluteMm` (vorläufig `MAX_WALL_MM`) bestätigen.
  - Jeder Branch hat entweder einen `wip(...)`-Commit oder ungespeicherte Änderungen im Worktree. Vor dem Fortsetzen mit `git -C <worktree> status` und `git log main..<branch>` prüfen.

## Nächste Schritte

1. Die drei unterbrochenen Aufgaben vom jeweiligen Branch aus fertigstellen (neuer Agent je Aufgabe mit dem Briefing aus docs/TASKS.md, Start auf dem WIP-Stand), prüfen und übernehmen.
2. Batch 1.2 vervollständigen: T-GEO-002, T-GEO-003 (nach T-GEO-001).
3. Batches 1.3 und 1.4, danach Wellen-Gate 1 mit Review, Tag `wave-1` und Push.
