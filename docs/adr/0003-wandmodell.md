# ADR 0003: Wandmodell

**Status:** Angenommen, 2026-11-23

## Kontext

Vor Ort werden Innenmaße gemessen, die Fläche darf sich durch Wandstärken nicht ändern.

## Optionen

Mittellinie plus Stärke; Innenkontur plus Stärke; Außenkontur.

## Entscheidung

Innenkontur plus Stärke je Wand. Die Stärke wirkt nur nach außen. Gezeichnet wird je Wand ein Band mit Gehrung und Fasenbegrenzung.

## Konsequenzen

Fläche und Umfang sind von der Stärke unabhängig. Bänder dürfen sich an konkaven Ecken überlappen und werden deckend gezeichnet, daher ist keine robuste Polygon-Vereinigung nötig.
