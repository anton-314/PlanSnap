# Fortschritt

**Aktuelle Phase:** 3 – Fundament
**Letzte Aktualisierung:** 2026-11-24

## Phasen

| Phase | Status | Nachweis |
|---|---|---|
| 0 – Interview | Fertig | `docs/REQUIREMENTS.md`, vom Auftraggeber bestätigt |
| 1 – Umgebung | Fertig | `docs/ENVIRONMENT.md`, Emulator-Boot ausgeführt |
| 2 – Spezifikation | Fertig | Vom Auftraggeber freigegeben am 2026-11-23 |
| 3 – Fundament | In Arbeit | |
| 4 – Umsetzung | Offen | |
| 5 – Härtung | Offen | |
| 6 – Release | Offen | |
| 7 – Abschlussbericht | Offen | |

## Offene Blocker

Phase-3-Gate rot: Review `CHANGES_REQUESTED` (7 hoch, 9 mittel, 3 niedrig, kein kritisch). T-ARC-004, T-BLD-005 und T-ARC-005 sind gemergt. Nachreview weiter `CHANGES_REQUESTED` (3 hoch, 6 mittel, 1 niedrig, alle neu). T-ARC-006 und T-BLD-006 sind gemergt. Drittes Review weiter `CHANGES_REQUESTED` (3 hoch, 2 mittel, 1 niedrig, alle neu, Signaturen weitgehend tragfähig). T-ARC-007 ist gemergt. Offen: T-ARC-008 und ein abschließendes Vertragsreview.

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
- 2026-11-24: Diskret mehrdeutige Räume werden `PROPOSED`. Die numerische Qualität des Ablaufs wird ab jetzt am Produktionscode über den portierten Prüfstand abgenommen (T-GEO-005, T-GEO-006), nicht in weiteren Prototyp-Runden. Bekannte Grenze im Prototyp: 5 von 22 abweichenden Formen in einer Klasse von 2.400 Fällen blieben unerkannt.

## Wiedereinstieg (Stand 2026-11-24, Lauf unterbrochen)

- **`main`:** T-BLD-001 bis -006 und T-ARC-001 bis -007 sind gemergt. Letzter voller Build grün auf `4ab0baa`. `main` ist 27 Commits vor `origin/main`, weil erst nach dem Phase-3-Gate gepusht wird.
- **Gate Phase 3:** nicht bestanden. Kein Tag, kein Push, CI noch nie auf GitHub gelaufen. Welle 1 ist nicht gestartet.
- **Unfertig:** T-ARC-008 auf Branch `wip/T-ARC-008` (`caadf81`), nicht gemergt, Kotlin-Stand ungeprüft. Der Code verweist auf DOMAIN §5.6, den es noch nicht gibt.
- **Prototypen:** Sicherung in `~/PlanSnap-handoff/scratchpad-2026-11-24`. Der Prüfstand `docs/research/evaluation-prototype/` auf dem WIP-Branch braucht `numpy`.

## Nächste Schritte

1. T-ARC-008 auf `wip/T-ARC-008` fertigstellen:
   - DOMAIN aktualisieren: §4-Tabelle, §5.4, A2 bis A5, B7/B8, neuer §5.6 (Formabstand, Alternativen, Empfehlung, Gleichstand).
   - ADR 0018 um die Ergänzung T-ARC-008 erweitern, mit Suchgrenze und Zahlen (17/22 im Original, 18/22 mit neuem Formabstand).
   - `docs/research/evaluation-prototype/README.md` schreiben (Zweck, Befehl, Seed 20261124, K1a bis K4, Sollwerte, Gradient 1e-7 gegenüber 1e-9, numpy).
   - `stress.py` mit Mehrdeutigkeitsregel erneut laufen lassen und die Tabelle übernehmen.
   - Volle DoD ausführen, WIP-Commit durch `feat(core): model ambiguous room shapes [T-ARC-008]` ersetzen.
2. Entschieden (2026-11-24): Die Mehrdeutigkeitsregel gilt auch für den Ausgleichspfad. Ein Raum mit zweiter gültiger Form wird dort ebenfalls `PROPOSED(AMBIGUOUS_SHAPE)` und kann nicht durch Bestätigung zu `ADJUSTED` werden.
3. T-ARC-008 mergen, voller Gate-Lauf: `./gradlew build koverVerify checkArchitecture verifyRoborazziDebug`, `:core:geometry:pitest`, `scripts/run-instrumented-tests.sh`.
4. Abschließendes Vertragsreview durch `reviewer`. Bei `APPROVE`: Tag `phase-3-foundation`, `git push origin main --tags`, ersten CI-Lauf prüfen.
5. Welle 1, Batch 1.1 starten.
