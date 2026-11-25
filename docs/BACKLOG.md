# PlanSnap – Backlog (nach v1.0)

## Aus dem Interview zurückgestellt

- Bluetooth-Laser-Entfernungsmesser
- Sensor- und AR-Messung
- 3D-Ansicht
- DXF- und IFC-Export
- CSV-Flächenliste
- Mehrere Geschosse und Treppen
- Stützen, Kamine, Schächte
- Dachschrägen und Wohnflächenberechnung nach WoFlV
- Möbel und Symbole
- Cloud-Sync, Konten, Kollaboration
- Imperiale Einheiten
- Play-Store-Release und Monetarisierung

## Technisch

- Wechsel auf compileSdk 37 und aktuelle AndroidX-Versionen (ADR 0012)
- Wechsel auf detekt 2, sobald eine stabile Version erscheint
- Optimierte Layouts für Querformat, Tablets und Faltbare
- `OverdeterminedCases`: Für h2a-109471 und h2a-109472 die nicht kontrollierbaren Wände w1 und w3 mit Empfehlung (1,3) bzw. (0,2) aufnehmen (aus T-GEO-005).
- `ProjectRepository.observeProjects()`: Speicherfehler erreichen den Collector als Exception, obwohl die Schnittstelle `DomainError.Storage` verspricht. Der Flow-Vertrag hat keinen Fehlerkanal. `architect` soll entscheiden, ob die KDoc den Aufrufer zum Fangen verpflichtet oder der Vertrag einen Fehlerzustand erhält (Gate-Review Welle 1, niedrig).
- KDoc von `DockLink.hostOffset` in `core/model/…/Room.kt` auf DOMAIN §10 verweisen (Bezugspunkt nach CR-W2-03).
- Überschneidungen angedockter Räume abseits der Andockwand werden nicht geprüft (nicht konvexe oder überstehende Räume). Bei Bedarf als eigene Anforderung aufnehmen.
- `DefaultProjectValidator` dupliziert das Überlappungsprädikat aus `DefaultOpeningRules`, weil `OpeningRules.check` Grenzen vor Überlappung meldet. Ein gemeinsames internes Prädikat würde das beheben, ohne den Vertrag zu ändern. Außerdem `wallLengthError` und `thicknessError` aus `command/` in ein neutrales Paket verschieben, damit `validation/` nicht von `command/` abhängt.
- Drehrichtung an fast spitzen Ecken (α nahe 0°): Der Wortlaut von DOMAIN §3 meldet durch den Sprung über ±π schon bei winzigen Abweichungen eine Verletzung, in Auswerter und Solver gleich. `architect` soll prüfen, ob das gemeint ist (aus T-GEO-009).
- KDoc von `DoorSwing` in `core/model/…/Opening.kt` auf DIN 107 und DOMAIN §9 verweisen (Lesart nach CR-W2-04).
- `:core:domain`: Vertrag `fun interface CommandHistoryFactory` statt des Funktionstyps `(Project) -> CommandHistory` im DI-Graph (aus T-APP-001).
- `:core:data`: Room-Runtime als `api`, damit `:app` ohne `DatabaseHolder` auskommt (aus T-APP-001).
- `SceneRenderer`: Die Längenreferenz zum Ausblenden von Maßtexten hängt am Stil (`DIMENSION`, `DIAGONAL`). Bei einer Konfliktdiagonale (Stil `CONFLICT`) wird ihr Maß gegen die falsche Linie geprüft. Die Referenz sollte am Ziel (`SceneTarget.Wall/Diagonal`) hängen (Gate-Review Welle 2, niedrig, `SceneRenderer.kt:185`).
- `SceneRenderer.kt:237`: Das ktlint-Artefakt `if (block ==\n null\n)` lesbar formatieren (Gate-Review Welle 2, niedrig).
- `DefaultSnapper`: Listen und `Pair` je Aufruf vermeiden, weil das Einrasten beim Ziehen in jedem Frame läuft. Mit T-EDT umsetzen (Gate-Review Welle 2, niedrig).
