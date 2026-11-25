# ADR 0022: Gemeinsame Stil- und Titelblockquelle für alle Ausgaben

**Status:** Angenommen, 2026-11-25 (CR-W3-02), ergänzt ADR 0008 und ADR 0009

## Kontext

ADR 0008 verlangt, dass Editor, PDF, PNG und SVG denselben Plan zeigen. Nach T-RND-001 und T-EXP-002 gab es zwei Quellen für das Aussehen:

- `StyleLook` in `:core:render` war `internal`. Der SVG-Schreiber musste Farben, Linienstärken, Strichmuster und Kappen je `SceneStyle` in `SvgStyles` spiegeln. PDF und PNG (T-EXP-003) laufen zwar über `SceneRenderer`, zeichnen den Titelblock aber selbst und hätten eine dritte Kopie gebraucht.
- Titelblock und Maßstabsbalken standen nur in `SvgDocument`. ADR 0009 legte nur fest, dass sie im Titelblock liegen, nicht wie.
- `ExportPreflight` hatte keine Implementierung, T-FEX-001 braucht eine.

## Optionen

1. Spiegeln beibehalten und durch einen Vergleichstest absichern.
2. Eine öffentliche Stiltabelle in `:core:drawing`. Dafür müsste `RenderPalette` aus `:core:render` nach `:core:drawing` wandern, ein Vertragsbruch ohne Nutzen, weil nur Android-Ausgaben Farben auflösen.
3. Eine öffentliche, Android-freie Stiltabelle in `:core:render` und eine reine Titelblock-Spezifikation in `:core:drawing.plot`.

## Entscheidung

Option 3.

**Stiltabelle** (`app.plansnap.core.render.SceneStyleSheet`, additiv): Je `SceneStyle` ein `SceneStyleSpec` mit Füllung, Umriss ja/nein, Strichfarbe, Strichstärke in Linieneinheiten, Kappe (`LineCap`), Strichmuster (`DashPattern` in Linieneinheiten) und Textfarbe. Farben sind Rollen (`PlanInk`), keine ARGB-Werte. `RenderPalette.argb(ink)` löst sie auf, deshalb gilt eine Tabelle für die helle und die dunkle Palette. Die Datei importiert weder `android.*` noch `androidx.*`. Gemeinsame Konstanten:

| Konstante | Wert |
|---|---|
| `PAPER_LINE_UNIT_MM` | 0,18 mm Linieneinheit auf Papier (PDF, PNG, SVG) |
| `EDITOR_LINE_UNIT_DP` | 1 dp Linieneinheit im Editor |
| `MARKER_UNITS` | 3 Linieneinheiten Durchmesser des Eckpunkts, in Strichfarbe |
| `EXPORT_PALETTE` | `RenderPalette.Light` (UX §6) |
| `PAGE_BACKGROUND_ARGB` | Deckendes Weiß für Seite und helle Balkensegmente |
| `TITLE_BLOCK_INK` | `PlanInk.WALL` für Rahmen, Texte und dunkle Balkensegmente |

Die Werte sind die von T-RND-001, ein Test schreibt jede Zeile fest. `StyleLook` ist nur noch eine vorberechnete Canvas-Sicht auf die Tabelle (ohne Allokation je Element), `SvgStyles` eine SVG-Sicht mit aufgelösten Exportfarben. Eine neue `SceneStyle` scheitert am erschöpfenden `when` in `SceneStyleSheet` und braucht nur dort einen Eintrag.

Ort: `:core:render`, weil `RenderPalette` dort liegt und `:core:export` von `:core:render` abhängt. `:core:render` hängt weiterhin nicht von `:core:designsystem` ab.

**Titelblock** (`app.plansnap.core.drawing.plot.TitleBlockSpec`, additiv): rein, ohne Farben. `TitleBlockSpec.layout(plan)` bzw. `PlotPlan.titleBlockLayout` löst alle Lagen in Seitenmillimetern auf. Maße relativ zu `titleBlockMm`:

| Element | Festlegung |
|---|---|
| Rahmen | `titleBlockMm`, Strich 0,25 mm |
| Innenabstand | 5 mm |
| Projektname | links am Innenabstand, Grundlinie 8 mm unter Oberkante, Schriftgröße 5 mm, Breite höchstens Blockbreite − 2 · 5 mm − 25 mm |
| Maßstab `1:M` | rechtsbündig am Innenabstand, gleiche Grundlinie und Größe, gebietsschemaunabhängig |
| Maßstabsbalken | links am Innenabstand, Oberkante 12 mm unter Blockoberkante, 3 mm hoch |
| Nennlänge | 2 m bei 1:50, 5 m bei 1:100, 10 m bei 1:200, also 40, 50 und 50 mm auf Papier |
| Unterteilung | 2 gleiche Segmente, das erste in Titelblock-Tinte, das zweite in Seitenhintergrund, beide mit 0,25 mm umrandet |
| Beschriftung | `0` und die Nennlänge (als Maß in der Exporteinheit formatiert), mittig unter den Balkenenden, Schriftgröße 3 mm, Grundlinie 3 mm unter der Balkenunterkante |

Schriftgröße heißt Geviert (SVG `font-size`, Android `Paint.textSize`), Schrift serifenlos, y ist die Grundlinie. Ein Test belegt für alle zwölf Kombinationen aus Papier, Ausrichtung und Maßstab, dass Balken samt Endbeschriftung in den Block passen. Ein zu langer Projektname wird in PDF und PNG mit Auslassungszeichen auf die Höchstbreite gekürzt. SVG schreibt ihn ungekürzt, weil dort keine Schriftmetrik vorliegt.

**Ausgelassene Räume auf dem Plan:** Kein sichtbarer Hinweis. DOMAIN §12 verlangt die Meldung in der Vorprüfung, REQ-EXP-006 verlangt eine Kennzeichnung nur für nicht bestätigte Räume, die gezeichnet werden. Der Nutzer hat die Auslassung vor dem Export gesehen und bestätigt. Ein Hinweis auf dem Plan wäre ein zweiter Text im Titelblock ohne Anforderung und bräuchte lokalisierte Strings in jedem Exporter. SVG behält das maschinenlesbare Attribut `data-omitted-rooms`.

**Vorprüfung** (`app.plansnap.core.export.preflight.DefaultExportPreflight`, additiv neben dem unveränderten Vertrag): Ein Raum ist exportierbar genau bei `status.isExportable`, sonst ausgelassen. Nicht bestätigt heißt `status.isUnconfirmed`. Eine Öffnung wird gemeldet, wenn `RoomEvaluation.openingStatuses` sie als `OUT_OF_BOUNDS` führt und ihr Raum exportierbar ist. Öffnungen ausgelassener Räume werden nicht gezeichnet und deshalb nicht gemeldet. Die Vorprüfung rechnet keine Längen nach und nutzt keine `OpeningRules`, so gilt `L_wirksam` aus ADR 0021 ohne zweite Lesart. Die Mengen folgen der Reihenfolge der Räume im Projekt und der Öffnungen im Raum. Fehlt die Auswertung eines Raums, ist das wie bei `ExportRequest` ein `IllegalArgumentException`.

## Konsequenzen

- Editor, SVG, PDF und PNG lesen dasselbe Aussehen. Die Renderer-Referenzbilder und die SVG-Goldens sind bitgleich geblieben.
- T-EXP-003 zeichnet den Titelblock aus `titleBlockLayout` mit `SceneStyleSheet.TITLE_BLOCK_INK` und `PAGE_BACKGROUND_ARGB` und rechnet keine eigenen Lagen.
- T-FEX-001 nutzt `DefaultExportPreflight`. `:app` bindet sie an `ExportPreflight`.
- `SceneStyleSheet`, `SceneStyleSpec`, `PlanInk`, `LineCap`, `DashPattern`, `TitleBlockSpec` und ihre Layoutklassen sind ab jetzt eingefrorener Vertrag (WORKFLOWS §5).
