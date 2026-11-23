# ADR 0008: Rendering

**Status:** Angenommen, 2026-11-23

## Kontext

Editor, PDF, PNG und SVG müssen denselben Plan zeigen und der Editor muss bei großen Projekten flüssig bleiben.

## Optionen

Compose-Primitiven je Element; getrennte Zeichner je Format; gemeinsame Szene.

## Entscheidung

`:core:drawing` erzeugt aus dem Modell eine reine Szene in Weltkoordinaten. `:core:render` zeichnet sie auf `android.graphics.Canvas`, SVG wird direkt aus ihr geschrieben. Viewport, Hit-Testing und Culling sind reine Kotlin-Logik.

## Konsequenzen

Gleiche Darstellung in allen Ausgaben. Szenen werden je Raum zwischengespeichert. Die Zeichnung ist per JVM-Golden-Test prüfbar.
