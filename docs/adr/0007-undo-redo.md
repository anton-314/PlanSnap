# ADR 0007: Undo/Redo

**Status:** Angenommen, 2026-11-23, Nebenläufigkeit ergänzt durch ADR 0016

## Kontext

Jede Aktion muss über die ganze Sitzung deterministisch umkehrbar sein.

## Optionen

Inverse Befehle; Ereignisprotokoll; Befehle mit unveränderlichen Schnappschüssen.

## Entscheidung

`ProjectCommand` ist eine reine Funktion auf dem unveränderlichen Projekt. Die `CommandHistory` speichert Vorher- und Nachher-Referenzen. Die Tiefe ist innerhalb der Sitzung unbegrenzt, die Historie wird nicht persistiert.

## Konsequenzen

Keine fehleranfälligen Inversen. Strukturelles Teilen hält den Speicherbedarf klein. Die laufende Zifferneingabe gehört nicht in die Historie.
