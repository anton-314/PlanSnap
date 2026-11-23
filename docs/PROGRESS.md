# Fortschritt

**Aktuelle Phase:** 3 – Fundament
**Letzte Aktualisierung:** 2026-11-23

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

Keine.

## Entscheidungslog

- 2026-11-23: Windows-Emulator wird aus WSL über `adb.exe` genutzt, weil KVM in WSL ohne `sudo` nicht zugänglich ist.
- 2026-11-23: Linux-SDK unter `~/Android/Sdk` installiert, Lizenzannahme aus dem Windows-SDK übernommen.
- 2026-11-23: DOMAIN vor dem Vertragsfreeze präzisiert: `DoorSwing`-Werte, `Unspecified` nur an `V_0`, Gleichstandsregel der Schlussecke, Byte-Kodierung des Fingerabdrucks, Rangdefinition, Gültigkeit von Overlays bei neuer `solverVersion`, Andocken an Öffnungen über `hostOpeningId`, zentrale Toleranzen.
- 2026-11-23: T-ARC-002 läuft abweichend vom Batch-Plan nach T-ARC-001, weil es dessen Typen benötigt.

## Nächste Schritte

1. Phase 3 gemäß `docs/TASKS.md` umsetzen.
2. Gate prüfen, Tag `phase-3-foundation` setzen und pushen.
