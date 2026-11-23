# ADR 0011: Keine Messquellen-Abstraktion in v1.0

**Status:** Angenommen, 2026-11-23

## Kontext

v1.0 kennt nur manuelle Eingabe. Bluetooth, Sensoren und AR sind ausgeschlossen.

## Optionen

`:core:measure` mit einer Implementierung; keine Abstraktion.

## Entscheidung

Kein `:core:measure`. Die Eingabe erzeugt direkt Domänenbefehle. Die Grenze für künftige Quellen ist der Befehl `AddWall`.

## Konsequenzen

Weniger Code ohne Nutzen. Das JSON-Format erlaubt spätere Felder zur Messquelle in einer neuen Version. Der Einbau in v1.1 kostet ein neues Modul, aber keinen Umbau des Solvers.
