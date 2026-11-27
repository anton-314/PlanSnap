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
- Editor: Der Bestätigungsdialog von `AcceptAdjustment` könnte vorab nennen, welche Öffnungen durch das Annehmen `OUT_OF_BOUNDS` würden (aus CR-W2-06).
- `DefaultOpeningRules` legt `ShapeBuilder` fest an, `DefaultRoomEvaluator` erhält ihn injiziert. Mit Fakes könnten beide auseinanderlaufen: optionalen Konstruktorparameter ergänzen (Nachreview Welle 2, niedrig).
- `OpeningStatusConsistencyTest`: Die Property-Basis um veraltete sowie schließende, aber nicht einfache Overlays erweitern (Nachreview Welle 2, Hinweis).
- `:core:designsystem`: `PlanSnapNumericKey` mit Parameter `contentPadding` (feste 24 dp schneiden bei 200 % Schrift ab), `NumericKeyTextStyle` öffentlich, `error` in `PlanSnapTheme.colors`. Danach den eigenen Ziffernblock aus T-EDT-002 auf die Designsystem-Taste umstellen.
- `EditorUiState.kt:176`: veraltetes `PersistentMap.put` durch `putting()` ersetzen.
- Einstellungen: Der Text der absoluten Toleranz sollte `drafts` synchron setzen, mit der Einheit aus `state.value`. Bisher läuft je Tastendruck eine eigene Coroutine nach `settings.first()` (Gate-Review Welle 3, N1, `SettingsViewModel.kt:63-70`).
- Export: `NO_SPACE` wird beim Schreiben nie gemeldet, `PageExportFlow` und `JsonExporter` machen aus jeder `IOException` `WRITE_FAILED` (Gate-Review Welle 3, N3).
- Editor und Export lesen beim Öffnen das Repository, während ein Job im ApplicationScope noch speichert. Das kann einen veralteten Stand ergeben, und der nächste Speichervorgang überschreibt dann die letzte Wand. Laden soll auf den Saver warten (Gate-Review Welle 3, N4). Mit T-EDT-008 oder in Phase 5 umsetzen.
- Szene: Maßbeschriftungen benachbarter Räume überlappen (`canvas-rooms.png`, Gate-Review Welle 3, N6).
- Export: Prozess-Tod während eines laufenden Exports wird nicht wiederhergestellt (aus T-FEX-002).
- Editor: Hat der Nutzer schon weitergetippt, als eine Wand abgelehnt wird, geht deren Wert verloren (`EditorSession.kt:162`). Den abgelehnten Wert in der Fehlermeldung nennen (Nachreview Welle 3, N1).
- Export: `onDocumentChosen` bedient eine zurückgestellte URI nicht, wenn das Laden schon mit `Failed` oder `Empty` geendet hat oder `fit == Checking` ist. Ohne Ergebnis nach der Wiederherstellung bleibt `awaitingDocument` gesetzt. Fix über `serveDeferredDocument` bzw. `previewJob?.join()` (Nachreview Welle 3, N2).
- Editor: Die Tasten `D` und `Diag` skalieren bei 200 % Schrift nicht mit und sind sichtbar kleiner (Nachreview Welle 3, Nit).
- Editor: Rückfrage beim Verlassen, solange ein Speicherfehler offen ist (aus T-EDT-013).
- Editor: `EditorUiState.draftError` zeigt keine UI an (aus T-EDT-013).
- Editor: Ziehgeste für Öffnungen samt Einrasten (REQ-UX-010), dafür fehlt eine Festlegung in UX §4 (aus T-EDT-005/006).
- Editor: Einrasten beim Drehen von Räumen als reine Domänenfunktion und Festlegung in UX §4 (aus T-EDT-006).
- Plan: Richtungspfeil oder Eckennummern der Wirtswand beim Andocken (aus T-EDT-006).
- `:core:domain`: Vorbedingung `OPENING_HAS_DOCKED_ROOMS` in `OpeningRules`, `opening/` bildet sie nach (aus T-EDT-005).
- Editor: Blatt- und Texthelfer aus `shape/` in ein neutrales Paket verschieben (aus T-EDT-005).
- `:core:render`: `LabelTextFormatter` rundet Flächen über Double-Division, `DetailsFormat.area` über `BigDecimal`. Vereinheitlichen per Change-Request (aus T-EXP-004).
- Vor einem App Bundle: `bundle.language.enableSplit = false` in `app/build.gradle.kts`, sonst fallen Exportsprachen weg (Gate-Review Welle 4, N3).
- Entwürfe von Öffnung, Raumdetails und Andocken überleben keinen Prozess-Tod. ARCHITECTURE §5 verlangt das nur für die laufende Eingabe (Gate-Review Welle 4, N4).
- Export: Wird ein zurückgestelltes Dokument wegen unpassender Seite abgelehnt, bewirkt „Erneut versuchen“ nichts (aus T-EXP-005).
- Export: Verlässt der Nutzer den Screen mit zurückgestellter URI, bleibt das leere Dokument liegen (aus T-EXP-005).
- ADR 0008 und ARCHITECTURE §2 verorten das Culling in `:core:drawing`, es liegt in `:feature:editor` (`PlanViewMath.visibleRooms`). `architect` soll die Dokumente oder den Ort angleichen (aus T-PRF-001).
- Projektnamen-Dialog bei 360 dp auf dem Emulator prüfen: Unter Robolectric wird ein `AlertDialog` mit `OutlinedTextField` bei 360 × 640 dp nicht idle, auch ohne App-Code (aus T-E2E-003).
- Querformat und Split-Screen: Mit Statuskarte bleiben vom Plan nur etwa 31 dp sichtbar (Schätzung aus T-UX-002). Ein eigenes Querformat-Layout wäre die Lösung.
- Raum verschieben und drehen per langem Drücken hat keine Screenreader-Alternative, nur Andocken (aus T-A11-001).
- Diagonalen-Chips heißen unter „Von“ und „Bis“ beide „Ecke n“, für Screenreader nicht unterscheidbar (aus T-A11-001).
- Längenanzeige in Liste und Wandblatt uneinheitlich („400.0 cm“ und „90 cm“) (aus T-A11-001).
- Barrierefreiheit mit TalkBack auf Gerät oder Emulator prüfen, optional Google-ATF-Prüfungen (`ui-test-junit4-accessibility`) (aus T-A11-001).
- `CachingSceneBuilderPerformanceTest`: Kommentar spricht von einem längeren Wandpaar, der Code ändert die Wandstärke (Gate-Review Welle 5, N3).
- `AccessibilityAudit` behandelt `TextOverflow.Visible` wie eine Ellipse, Regel oder Name präzisieren (Gate-Review Welle 5, N4).
