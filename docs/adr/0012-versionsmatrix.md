# ADR 0012: Versionsmatrix und compileSdk 36

**Status:** Angenommen, 2026-11-23

## Kontext

Die neuesten AndroidX-Versionen (Compose BOM 2026.11.01 und andere) verlangen compileSdk 37.1 oder höher. Im SDK ist Plattform 36 installiert.

## Optionen

Auf compileSdk 37 wechseln; neueste mit API 36 kompatible Versionen fixieren.

## Entscheidung

compileSdk und targetSdk 36 mit der Versionstabelle in `docs/ARCHITECTURE.md` §9. Die Grenzen sind `minCompileSdk` aus den AAR-Metadaten.

## Konsequenzen

Die Kombination wurde am 2026-11-23 real gebaut. Dependabot darf AndroidX-Updates, die compileSdk 37 verlangen, nicht automatisch übernehmen. Ein Wechsel auf API 37 ist ein Backlog-Punkt.
