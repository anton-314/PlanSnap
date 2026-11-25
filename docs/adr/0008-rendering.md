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

## Ergänzung 2026-11-25 (CR-W2-04)

Aus T-DRW-001 (SceneBuilder) und T-APP-001 festgelegt:

- **Rauminneres:** `SceneStyle` erhält additiv `ROOM_BODY` und `ROOM_BODY_UNCONFIRMED` (am Ende der Aufzählung). Der Körper eines Raums mit Form ist eine `Fill` in einem dieser Stile: leichte Innentönung, nie Wandfarbe, Innenkontur durchgezogen bzw. gestrichelt. `WALL_LINE` und `WALL_UNCONFIRMED` gelten nur noch für Wände der Stärke null. Die frühere Regel „eine `Fill` in `WALL_LINE` mit `RoomBody`-Ziel wird nur umrandet“ entfällt, weil Renderer und SVG-Schreiber sonst Stil und Ziel gemeinsam auswerten müssten. Außer Tests wertet noch kein Modul `SceneStyle` aus (`:core:render` ist leer), die Ergänzung bricht also kein erschöpfendes `when`.
- **Konfliktmarke:** `LabelText` erhält additiv `ShapeConflict`. Ein `CONFLICT`-Raum trägt sie als Raumbeschriftung in `SceneStyle.CONFLICT` nach Name und Typ, mit `RoomBody`-Ziel. Damit ist der Konflikt nicht nur über die Farbe der Wände erkennbar (UX §6). Der Grund steht im Editor, nicht im Plan. Im Export erscheint die Marke nie, weil `CONFLICT`-Räume dort fehlen.
- **Türanschlag:** Links und rechts folgen DIN 107, gesehen von der Aufschlagseite (DOMAIN §9). Die bisherige Lesart „Lage entlang der Wandrichtung“ hing von der Messrichtung des Raums ab: dieselbe Tür hieß im Uhrzeigersinn gemessen anders als gegen ihn. Die DIN-Lesart entspricht dem, was der Nutzer vor Ort sagt („DIN links, nach innen“). Für einen gegen den Uhrzeigersinn gemessenen Raum tauschen dadurch `HINGE_LEFT_INWARD` und `HINGE_RIGHT_INWARD` ihr Band, für einen im Uhrzeigersinn gemessenen `HINGE_LEFT_OUTWARD` und `HINGE_RIGHT_OUTWARD`. Gespeicherte Dateien brauchen keine Migration, es gibt noch keine veröffentlichte Version.
- **Feste Weltgrößen:** Die Größen in `SceneMetrics` (Maßzahl 150 mm, Raumbeschriftung 200 mm, Winkel 120 mm, Maßlinie 300 mm außen) bleiben in der Szene. Eine Szene für alle Ausgaben bleibt deterministisch und per Golden-Test prüfbar. Schrifthöhen sind Nennhöhen. Der Editor-Renderer zeichnet Text in konstanter Bildschirmgröße und im Verhältnis der Nennhöhen (Maßzahl 12 sp als Bezug für 150 mm) und blendet eine Beschriftung aus, deren Bezugselement auf dem Bildschirm kürzer als der Text ist. Der Export-Renderer vergrößert alle Beschriftungen mit `f = max(1, 1,8 mm · M / 150 mm)` (DOMAIN §12). Abstände wie die Maßlinie sind Geometrie und werden nie umskaliert. Der Renderer setzt das um, nicht `:core:drawing`.

Verhaltensänderung der Umsetzung: Stil des Rauminneren, Konfliktmarke und Türanschlag. Die Weltgrößen der Szene ändern sich nicht.

## Ergänzung 2026-11-25 (CR-W2-05)

Problem: Die Zeilen der Raumbeschriftung (Name, Typ, Fläche, Marke, Notizen) lagen auf festen Weltabständen von 300 mm. Der Export vergrößert Text mit `f` (bei 1:200 `f = 2,4`), der Editor zeichnet ihn in konstanter Bildschirmgröße. Die Abstände wuchsen nicht mit, die Zeilen überlappten (1:200, Editor beim Herauszoomen).

Entscheidung: **Beschriftungsblock über einen gemeinsamen Blockanker.**

- **Vertrag (additiv):** `SceneElement.Label` erhält als letzten Parameter `blockAnchor: Vec2? = null`. Alle Zeilen eines Blocks tragen denselben `blockAnchor`. `anchor` bleibt die Nennlage der Zeile für Text in genau `heightMm`. Einzeilige Beschriftungen (Maßzahlen, Diagonalen, Winkel) haben `null` und bleiben unverändert auf `anchor`. Die Raumbeschriftung aus `RoomLabels.stacked` ist ein Block mit dem Innenpunkt bzw. Kettenmittel als Blockanker.
- **Layout im Renderer:** Mit `k = gezeichnete Textgröße / (heightMm · Leinwandeinheiten je mm)` liegt eine Blockzeile bei `blockAnchor + k · (anchor − blockAnchor)`. Im Export ist `k = f`, im Editor hält `k` den Zeilenabstand bei jedem Zoom auf dem 1,5-Fachen der Textgröße (Verhältnis `ROOM_LABEL_LINE_MM / ROOM_LABEL_HEIGHT_MM`). Damit überlappen Zeilen nie, und bei 1:50 (`f = 1`) bleibt das Bild bitgleich.
- **Ausblendregel im Editor:** Zusätzlich zur Breitenregel wird eine Blockzeile ausgeblendet, wenn `2 · Abstand zum Blockanker + Textgröße` auf dem Bildschirm die kleinere Raumseite übersteigt. Die mittleren Zeilen bleiben am längsten sichtbar, der Block läuft nicht über die Wände in Nachbarräume.
- **Szene, Grenzen und Treffertest:** Die Szene bleibt rein, deterministisch und golden-test-fähig, `SceneBounds` und `DefaultHitTester` nutzen weiter die Nennlage `anchor` (Scheibe mit `heightMm` als Durchmesser, CR-W2-01). Die gezeichneten Zeilen liegen im Rauminneren, das über die `RoomBody`-Fläche ohnehin getroffen wird.

Das ist Variante (b) des Change-Requests, mit dem Blockanker als Blockidentität. Verworfen: (a) ein neues Element `LabelBlock` mit geordneten Zeilen, das der Renderer selbst setzt. Es bricht jedes erschöpfende `when` über `SceneElement`, braucht eigene Regeln für Grenzen und Treffertest und legt die Zeilenhöhe in jeden Renderer (auch den späteren SVG-Schreiber), ohne mehr auszudrücken als der Blockanker. Ebenso verworfen: Blöcke im Renderer aus Ziel und Reihenfolge zu erraten, weil das an jeder neuen Beschriftungsart bricht.

Offen: Im Export kann ein sehr kleiner Raum bei 1:200 einen Block haben, der über die Wände hinausreicht. Das gilt für den vergrößerten Text schon heute und bleibt ohne Ausblendung, weil der Export nichts ausblendet (DOMAIN §12).
