# PlanSnap – UX

## 1. Screens und Navigation

```mermaid
graph LR
  P[Projektliste] --> E[Editor]
  P --> S[Einstellungen]
  P --> I[Import-Dateidialog]
  E --> X[Export]
  E --> O[Blatt Öffnung]
  E --> C[Blatt Ecke und Diagonale]
  E --> A[Blatt Ausgleich]
  E --> R[Blatt Raumdetails]
  E --> L[Blatt Räume und Flächen]
```

| Screen | Zweck | Leer | Laden | Fehler |
|---|---|---|---|---|
| Projektliste | Anlegen, Öffnen, Umbenennen, Duplizieren, Löschen, Import | Erklärung + „Neues Projekt“ | Skeleton | Meldung + erneut versuchen |
| Editor | Messen und Bearbeiten | „Erste Wand eingeben“ | Fortschritt | Projekt nicht ladbar, zurück zur Liste |
| Export | Format, Maßstab, Papier, Teilen oder Speichern | Hinweis „Noch kein Raum“ | Fortschritt | Meldung + erneut versuchen |
| Einstellungen | Einheit, Toleranz, Raster, Hinweise | – | – | Wert wird abgelehnt und erklärt |

## 2. Editor im Hochformat

```text
+--------------------------------+
| <  Wohnung Süd     ↶  ↷  ⋮     |
+--------------------------------+
|                                |
|      Live-Plan (ca. 50 %)      |
|      Pinch, Pan, Tippen        |
|                                |
| [Küche · 11,31 m² · ⚠ Vorschlag]|
+--------------------------------+
| Wand 4          345 cm   D 12  |
| Ecke: [ Links ] [ Rechts ] [∠] |
| [ 7 ] [ 8 ] [ 9 ]  [  ⌫  ]     |
| [ 4 ] [ 5 ] [ 6 ]  [  D  ]     |
| [ 1 ] [ 2 ] [ 3 ]  [ Diag ]    |
| [ , ] [ 0 ] [      OK      ]   |
+--------------------------------+
```

Alle Messaktionen liegen in der unteren Hälfte. Andere Geräteformen erhalten denselben Aufbau mit scrollbarem Eingabebereich. Ohne Optimierung für Querformat und Tablets bleiben sie bedienbar und verlieren keinen Zustand.

## 3. Messablauf

1. Neues Projekt legt automatisch den ersten Raum an.
2. Länge eintippen, `OK` bestätigt. Die Wand erscheint sofort im Plan.
3. Die Richtung der nächsten Wand ist die zuletzt gewählte (Start: links, 90°). Ein Tipp auf `Links` oder `Rechts` ändert sie.
4. `∠` öffnet die Winkeleingabe. `D` ändert die Wandstärke, vorbelegt mit dem letzten Wert.
5. `Schließen` erscheint ab 3 Wänden. Die App wertet den Raum aus:

| Ergebnis | Darstellung | Aktion |
|---|---|---|
| Exakt | Grüner Haken, Fläche | Weiter |
| Kleine Lücke | Lücke in cm und Vorschau | `Ausgleichen`, `Maße prüfen` |
| Formabweichung | Gestrichelter Umriss, „Form nicht bestätigt“ | Empfohlene Diagonale oder Winkel eintragen |
| Unmöglich | Rot markierte Maße, Erklärung | Maß ändern |

Ein Tipp auf eine Wand, Ecke oder Öffnung wählt sie aus und öffnet ihre Bearbeitung. Langes Drücken auf einen Raum startet das Verschieben. Änderungen laufen immer über Befehle und sind rückgängig machbar.

## 4. Gesten

| Geste | Wirkung |
|---|---|
| Zwei Finger | Zoom und Verschieben der Ansicht |
| Ein Finger ziehen | Verschieben der Ansicht |
| Tippen | Auswahl |
| Lang drücken + ziehen | Raum verschieben, Einrasten aktiv |
| Doppeltippen | Plan einpassen |

Gesten ändern ohne Long-Press nie das Modell.

## 5. Einmalige Hinweise

Höchstens ein Hinweis gleichzeitig, schließbar, in den Einstellungen zurücksetzbar: Ziffernblock, Richtung, Schließen, Diagonale bei einem Vorschlag, Ausgleich bei einer Lücke.

## 6. Design-Tokens

| Token | Hell | Dunkel |
|---|---|---|
| `primary` | `#1B5FAA` | `#A6C8FF` |
| `onPrimary` | `#FFFFFF` | `#003060` |
| `surface` | `#F8F9FC` | `#11141A` |
| `onSurface` | `#191C20` | `#E2E2E9` |
| `planWall` | `#1F2A37` | `#DCE3EC` |
| `planGrid` | `#DDE3EC` | `#2A313B` |
| `planDimension` | `#1B5FAA` | `#A6C8FF` |
| `warning` | `#8A5000` | `#FFB95C` |
| `error` | `#BA1A1A` | `#FFB4AB` |
| `success` | `#17693A` | `#7FDB9F` |

Kein Dynamic Color. Der Export nutzt immer die helle Planpalette. Abstände: 4, 8, 12, 16, 24 dp. Tasten des Ziffernblocks mindestens 56 dp, alle anderen Touch-Ziele mindestens 48 dp. Status wird nie nur über Farbe vermittelt, sondern immer zusätzlich über Symbol und Text.

## 7. Barrierefreiheit und Sprache

- Jede Taste und jedes Symbol hat ein TalkBack-Label.
- Der Plan bietet eine semantische Alternative: die Liste aus Räumen und Wänden mit Maßen und Status.
- Texte skalieren bis 200 %, der Eingabebereich scrollt dann.
- Alle Texte liegen in `strings.xml` (DE, EN), Pluralformen über `plurals`, Zahlen nach Gerätesprache. Kommaeingabe akzeptiert `,` und `.`.
- Kurze Haptik bei `OK`, deaktiviert sich mit der Systemeinstellung.
