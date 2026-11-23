# ADR 0001: Modulschnitt

**Status:** Angenommen, 2026-11-23

## Kontext

v1.0 braucht strikte Trennung von Geometrie, Domäne und Android, aber keine Module ohne Funktion.

## Optionen

Einzelmodul; vorgeschlagener Schnitt der Mission; angepasster Schnitt.

## Entscheidung

Angepasster Schnitt laut `docs/ARCHITECTURE.md`. Neu sind `:core:domain`, `:core:document`, `:core:drawing`, `:core:render` und `:benchmark`. Entfallen sind `:core:ui`, `:core:measure`, `:feature:measure` und `:feature:viewer3d`. Die Messung ist Teil von `:feature:editor`.

## Konsequenzen

Alle fachlichen Module bis `:core:drawing` laufen als reine JVM-Module und sind schnell testbar. Ein Architekturtest erzwingt die Abhängigkeitsrichtung.
