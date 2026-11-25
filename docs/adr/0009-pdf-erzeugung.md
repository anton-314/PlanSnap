# ADR 0009: PDF-Erzeugung

**Status:** Angenommen, 2026-11-23

## Kontext

Der Plan muss maßstäblich und vektorbasiert sein und beliebige Raumnamen darstellen können.

## Optionen

PDFBox-Android; OpenPDF; Android `PdfDocument`; eigener Writer.

## Entscheidung

Android `PdfDocument` mit dem gemeinsamen Canvas-Renderer. Die Maßstabsberechnung liegt rein in `PlotLayout`.

## Konsequenzen

Keine zusätzliche Abhängigkeit und volle Schriftunterstützung. Maße werden per JVM-Golden-Test der Szene und per instrumentiertem Test mit `PdfRenderer` an einem Kalibrierbalken geprüft.

## Ergänzung 2026-11-25 (CR-W2-04)

Aus T-EXP-001 (`PlotLayout`) festgelegt:

- **Lage auf der Seite:** Bestätigt. Die Mitte von `PlotPlan.worldBounds` liegt in der Mitte des Zeichenbereichs (Seite minus 10 mm Rand, über dem 25-mm-Titelblock). Weltachse y nach oben wird Seitenachse y nach unten. Zentrieren ist bei jedem Maßstab eindeutig und ohne Sonderfall. Maßstabsbalken, Maßstabsangabe und Projektname liegen im Titelblock, nicht im Zeichenbereich, und stoßen deshalb nie an den Plan.
- **`PageRectMm`, `PlotPlan.drawingAreaMm`, `PlotPlan.titleBlockMm`:** Als additive Vertragserweiterung von `:core:drawing` bestätigt (WORKFLOWS §5). Ganze Millimeter genügen, weil alle Seitenmaße aus ganzzahligen Konstanten folgen. Die Renderer (T-RND-001, PDF/PNG/SVG) beziehen Zeichenbereich und Titelblock nur hieraus und rechnen keine Ränder selbst.
- **Keine Überschreibung der Ausrichtung:** `PlotSelection` bleibt bei `scale` und `paper`. REQ-EXP-002 verlangt nur Maßstab und Papier. Passt nur eine Ausrichtung, wird sie ohnehin gewählt. Eine Überschreibung könnte also nur eine passende Ausrichtung durch die andere ersetzen, und dafür gibt es keinen Bedarf. Der Exportdialog (T-FEX-001) zeigt die gewählte Ausrichtung nur an. Ein späterer Bedarf wäre als Parameter mit Vorgabe `null` additiv nachrüstbar.
- **Exakter Vergleich:** Bestätigt. Die Passprüfung vergleicht mit `≤` ohne Toleranz. Eine Toleranz würde die Schwelle nur um ihren Betrag verschieben. Rauschen an der neuen Schwelle hätte dieselbe Wirkung, die Unstetigkeit verschwindet also nicht. Rauschen von `1e-7` mm fällt in die Größenordnung von Umrechnungsfehlern. Dass ein Plan dadurch auf das nächste Papier rückt, ist korrekt im Sinne der Regel und unschädlich. Die Szenengrenzen enthalten Maßketten und Beschriftungen, deshalb trifft eine reale Wohnung die Schwelle nur zufällig. Ein Test hält das Verhalten fest.

Kein Verhalten der Umsetzung ändert sich.
