# ADR 0013: Mehrraum und Andocken

**Status:** Angenommen, 2026-11-23

## Kontext

Räume sollen exakt aneinander liegen, aber auch frei korrigierbar bleiben.

## Optionen

Gemeinsamer Wandgraph; nur freie Transformationen; Transformation plus optionaler Dock-Link.

## Entscheidung

Jeder Raum hat eine starre Platzierung und optional einen Dock-Link zu einer Wirtswand. Die Trennwandstärke ist die Stärke der Wirtswand. Freies Verschieben oder Drehen löst den Link.

## Konsequenzen

Raumformen bleiben unabhängig lösbar. Es gibt keinen globalen Solver. Zyklen werden beim Andocken abgelehnt.
