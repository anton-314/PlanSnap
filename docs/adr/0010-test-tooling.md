# ADR 0010: Test-Tooling

**Status:** Angenommen, 2026-11-23

## Kontext

Die Teststrategie braucht schnelle JVM-Tests, Property-Tests, Screenshots und Abdeckungsmessung.

## Optionen

Paparazzi oder Roborazzi; JaCoCo oder Kover; manuelle oder automatische Mutationstests.

## Entscheidung

JUnit 6 und Kotest Property für JVM, JUnit 4 mit Robolectric für Android, Roborazzi für Screenshots, Turbine für Flows, Kover für Abdeckung, Pitest für `:core:geometry`. MockK nur, wenn kein Fake sinnvoll ist.

## Konsequenzen

Alle Werkzeuge liefen im Kompatibilitätstest. Screenshot-Referenzen werden auf Linux erzeugt und nur dort verglichen.
