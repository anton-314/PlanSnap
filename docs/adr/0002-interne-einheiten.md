# ADR 0002: Interne Einheiten

**Status:** Angenommen, 2026-11-23

## Kontext

Rohmaße müssen verlustfrei bleiben, berechnete Koordinaten sind jedoch irrational.

## Optionen

Alles `Double` in Metern; alles `Long` in Millimetern; getrennte Typen.

## Entscheidung

Rohlängen sind `Length(mm: Long)`, Rohwinkel `Angle(milliDegrees: Int)`. Berechnete Koordinaten sind `Double` in Millimetern. Umrechnung findet nur bei Eingabe, Anzeige und Export statt.

## Konsequenzen

1 mm ist die kleinste erfassbare Länge, 0,001° der kleinste Winkel. Rohwerte haben keine Rundungsfehler.
