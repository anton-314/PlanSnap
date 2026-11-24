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

## Ergänzung 2026-11-24 (CR-W2-01)

Aus T-DRW-002 (Viewport und Hit-Testing) festgelegt:

- **Trefferpriorität:** Öffnung vor Ecke vor Wand vor Diagonale vor Rauminnerem (DOMAIN §11). Diagonalen fehlten in der Regel. Hinter dem Rauminneren wären sie nie wählbar, vor Ecke oder Wand würden sie deren Enden verdecken.
- **Gleichstand:** Die relative Grenze `HIT_TIE_RELATIVE = 1e-9` liegt zentral in `GeometryTolerances` (DOMAIN §1), nicht lokal in `:core:drawing`. Die Konstante ist additiv und bricht den Vertragsfreeze nicht (WORKFLOWS §5).
- **Zoomgrenzen und Trefferradius:** `ZoomLimits.DEFAULT` = `1e-4` bis `100` px/mm und 24 dp Trefferradius gelten wie umgesetzt, begründet in UX §4. `fit` passt ohne Rand ein; den Rand von 24 dp legt der Aufrufer über eine verkleinerte Ansicht an.

Kein Verhalten der Umsetzung ändert sich.
