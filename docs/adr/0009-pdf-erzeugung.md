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
