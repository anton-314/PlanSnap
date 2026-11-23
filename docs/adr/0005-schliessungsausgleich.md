# ADR 0005: Schließungsausgleich

**Status:** Angenommen, 2026-11-23, ergänzt durch ADR 0018

## Kontext

Kleine Lücken müssen ohne Verfahrenswahl für Nutzer ausgeglichen werden, auch wenn Diagonalen vorliegen.

## Optionen

Bowditch; Transit-Regel; gewichtete kleinste Quadrate.

## Entscheidung

Gewichtete kleinste Quadrate über Längen, Winkel und Diagonalen mit den Standardabweichungen aus `docs/DOMAIN.md` §6.

## Konsequenzen

Diagonalen fließen ein, was Bowditch nicht leistet. Das Ergebnis wird als separates Overlay gespeichert, die Rohwerte bleiben unverändert.
