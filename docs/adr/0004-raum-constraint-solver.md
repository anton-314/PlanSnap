# ADR 0004: Raum-Constraint-Modell und Solver

**Status:** Angenommen, 2026-11-23

## Kontext

n Längen lassen n − 3 Freiheitsgrade offen. Die App muss Form, Status und fehlende Angaben reproduzierbar bestimmen.

## Optionen

Nur Heuristiken; allgemeine Bibliothek; eigener Gleichungs-SQP-Solver über Eckdrehungen.

## Entscheidung

Eigener deterministischer SQP-Solver ohne Abhängigkeit, wie in `docs/DOMAIN.md` §5 beschrieben. Längen, gemessene Winkel und Diagonalen sind harte Bedingungen, deklarierte 90°-Ecken weiche Sollwerte. Feste Startwerte und eine feste Auswahlordnung sichern die Reproduzierbarkeit.

## Konsequenzen

Höchstes Fachrisiko des Projekts, daher `opus`, analytische Referenzfälle, Property-Tests und Mutationstests. Das Beispiel 3 × 3 × 3 × 5 m ist ein verbindlicher Abnahmefall.
