# PlanSnap – Anforderungen v1.0

**Status:** Vom Auftraggeber bestätigt am 2026-11-23
**Stand:** 2026-11-23
**Priorität:** M = Must, S = Should, W = Won't (v1.0)

## 1. Produkt

PlanSnap ist eine Android-App, mit der Privatleute und Fachanwender (z. B. Makler, Handwerker) Wohnungen vor Ort Wand für Wand aufmessen und sofort einen maßstäblichen, bearbeitbaren und exportierbaren 2D-Grundriss erhalten. Der Schwerpunkt liegt auf Wohnungen, ausdrücklich auch im Altbau mit schiefen Wänden.

| Rahmen | Entscheidung |
|---|---|
| App-Name / Paket | PlanSnap / `app.plansnap` |
| Verteilung | Private, signierte APK, kein Play-Store-Release |
| Preis | Kostenlos, ohne Werbung |
| Geräte | Smartphone im Hochformat, Android 8.0+ (API 26) |
| Sprachen | Deutsch und Englisch, folgt der Gerätesprache |
| Daten | Nur lokal, ohne Konto, Server und Telemetrie |
| Testgeräte | Eigenes Handy und Android-Emulator |

## 2. Messen und Eingabe

| ID | Prio | Anforderung | Akzeptanzkriterium |
|---|---|---|---|
| REQ-MEAS-001 | M | Wände werden nacheinander manuell über einen großen Ziffernblock erfasst. | Ein Raum mit 4 Wänden ist ohne Systemtastatur vollständig eingebbar. |
| REQ-MEAS-002 | M | Standardeinheit ist cm, umschaltbar auf m und mm. | `345` in cm, `3,45` in m und `3450` in mm ergeben dieselbe Länge von 3450 mm. |
| REQ-MEAS-003 | M | Die Richtung der nächsten Wand ist standardmäßig 90° links oder rechts. | Vier gleiche Wände mit 90° ergeben ein geschlossenes Quadrat. |
| REQ-MEAS-004 | M | Für jede Ecke kann ein beliebiger Winkel eingetragen werden. | Ein eingegebener Winkel wird exakt übernommen und im Plan angezeigt. |
| REQ-MEAS-005 | M | Eine gemessene Diagonale zwischen zwei Ecken kann die Form festlegen. | Ein Viereck mit 4 Längen und 1 Diagonale wird eindeutig berechnet. Geometrisch unmögliche Maße werden abgelehnt und erklärt. |
| REQ-MEAS-006 | M | Passen die Längen nicht zu 90°-Ecken, schlägt die App automatisch eine Form mit passenden Winkeln vor. | 3 × 3 × 3 × 5 m ergibt ein symmetrisches Trapez mit ca. 70,53° / 109,47° und 11,31 m². |
| REQ-MEAS-007 | M | Der Vorschlag minimiert die quadratische Abweichung der nicht festgelegten Winkel von 90°, bei gleichen Längen und reproduzierbarem Ergebnis. | Gleiche Eingaben liefern immer dieselbe Form. Eingegebene Winkel und Diagonalen bleiben unverändert. |
| REQ-MEAS-008 | M | Eine vorgeschlagene Form ist als „nicht bestätigt“ markiert, bis genügend Zusatzmaße sie eindeutig festlegen. | Die Markierung verschwindet erst, wenn der Raum eindeutig bestimmt ist. Die App zeigt, welches Zusatzmaß noch fehlt. |
| REQ-MEAS-009 | M | Kleine Schließungsfehler werden angezeigt, mit Vorschau ausgeglichen und erst nach Bestätigung angewendet. | Nach dem Ausgleich ist der Umriss innerhalb der Toleranz geschlossen. |
| REQ-MEAS-010 | M | Rohmessungen bleiben immer erhalten. | Ausgleich und Formvorschlag lassen sich zurücknehmen, die Rohwerte sind danach identisch. |
| REQ-MEAS-011 | M | Lücken bis 5 cm oder 1 % des Umfangs (der größere Wert) gelten als Messfehler, größere als Formabweichung. Der Grenzwert ist einstellbar. | Eine Lücke von 3 cm löst den Ausgleich aus, die 2 m Lücke bei 3 × 3 × 3 × 5 m den Formvorschlag. |
| REQ-MEAS-012 | M | Die Wandstärke wird je Wand erfasst und mit dem zuletzt verwendeten Wert vorbelegt. | Die Stärke ist je Wand änderbar und verändert die Innenfläche nicht. |
| REQ-MEAS-013 | M | Gemessen werden Innenmaße. | Fläche und Umfang beziehen sich auf die Innenkontur. |

## 3. Modell und Bauelemente

| ID | Prio | Anforderung | Akzeptanzkriterium |
|---|---|---|---|
| REQ-MOD-001 | M | Türen, Fenster und Durchgänge sind an eine Wand gebunden, mit Abstand und Breite als Pflichtangaben. | Eine Öffnung kann nicht über das Wandende hinausragen. |
| REQ-MOD-002 | M | Höhe, Brüstung und Türanschlag sind optional. | Eine Öffnung ist ohne diese Angaben speicherbar. |
| REQ-MOD-003 | M | Öffnungen bleiben bei Wandänderungen konsistent. | Wird eine Wand so gekürzt, dass eine Öffnung nicht mehr passt, wird dies gemeldet und nichts still gelöscht. |
| REQ-MOD-004 | M | Nischen, Erker und Vorsprünge werden als Teil des Raumumrisses erfasst. | Referenzräume mit Nische und Erker liefern die analytisch korrekte Fläche. |
| REQ-MOD-005 | M | Eine Standardhöhe gilt für die Wohnung und ist je Raum überschreibbar. | Das Volumen nutzt die Raumhöhe, falls gesetzt, sonst die Standardhöhe. |
| REQ-MOD-006 | M | Ein Projekt enthält mehrere Räume einer Wohnung auf einem Geschoss. | Mindestens 30 Räume sind in einem Projekt möglich. |
| REQ-MOD-007 | M | Ein neuer Raum kann an einer Wand oder Tür andocken, unter Berücksichtigung der Trennwandstärke. | Der angedockte Raum liegt exakt im Abstand der Wandstärke. |
| REQ-MOD-008 | M | Räume können zusätzlich frei verschoben und gedreht werden. | Die Raummaße bleiben dabei unverändert. |
| REQ-MOD-009 | M | Fläche, Umfang und Volumen werden je Raum und als Summe ausgewiesen. | Rechteck, L-, U- und T-Form sowie Schräge stimmen mit den analytischen Werten überein. |
| REQ-MOD-010 | M | Je Raum ist ein Anrechnungsfaktor für die Fläche möglich. | Gemessene und angerechnete Fläche werden getrennt gezeigt, mit dem Hinweis, dass dies keine rechtsverbindliche Wohnflächenberechnung ist. |
| REQ-MOD-011 | S | Raumnamen und Raumtypen mit Vorlagen. | Name und Typ erscheinen im Plan und in der Flächenübersicht. |
| REQ-MOD-012 | S | Notizen je Raum. | Notizen werden gespeichert und sind optional im PDF sichtbar. |

## 4. Editor und Bedienung

| ID | Prio | Anforderung | Akzeptanzkriterium |
|---|---|---|---|
| REQ-UX-001 | M | Der Messbildschirm zeigt oben den Live-Plan und unten den Ziffernblock mit Richtungstasten. | Alle Messaktionen sind im unteren Bildschirmbereich erreichbar. |
| REQ-UX-002 | M | Der Plan wächst bei jeder Eingabe sofort mit. | Jede bestätigte Wand ist ohne Neuladen sichtbar. |
| REQ-UX-003 | M | Pinch-Zoom, Verschieben und Auswählen im Plan. | Gesten verändern nur die Ansicht, nicht das Modell. |
| REQ-UX-004 | M | Maßketten und Beschriftungen im Plan. | Jede Wand zeigt ihre Länge in der gewählten Einheit. |
| REQ-UX-005 | M | Undo/Redo über die gesamte Sitzung. | Beliebige Aktionsfolgen lassen sich vollständig zurück- und wiederherstellen. |
| REQ-UX-006 | M | Kurze einmalige Hinweise im Editor. | Hinweise erscheinen beim ersten Start, sind schließbar und in den Einstellungen zurücksetzbar. |
| REQ-UX-007 | M | Technisch klares Design mit Blaupausen-Akzent, Hell- und Dunkelmodus. | Kontrast erfüllt WCAG AA. |
| REQ-UX-008 | M | Barrierefreiheit: TalkBack-Labels, Touch-Ziele ab 48 dp, große Schrift. | Automatische Prüfungen der Kern-Screens bestehen. |
| REQ-UX-009 | M | Tablets, Querformat und Faltbare werden nicht optimiert, bleiben aber bedienbar. | Rotation und Größenwechsel verursachen keinen Absturz und keinen Datenverlust. |
| REQ-UX-010 | S | Raster und magnetisches Einrasten. | Verschobene Räume und Öffnungen rasten innerhalb der Toleranz ein. Einrasten ist abschaltbar. |

## 5. Projekte und Daten

| ID | Prio | Anforderung | Akzeptanzkriterium |
|---|---|---|---|
| REQ-DATA-001 | M | Projekte anlegen, öffnen, umbenennen und löschen. | Löschen erfordert eine Bestätigung. |
| REQ-DATA-002 | M | Automatisches Speichern. | Nach Prozess-Tod mitten in der Eingabe fehlt keine bestätigte Wand, und die laufende Eingabe wird wiederhergestellt. |
| REQ-DATA-003 | M | Vollständig offline nutzbar. | Die App deklariert keine Internet-Berechtigung. |
| REQ-DATA-004 | M | Sicherung und Wiederherstellung als versionierte JSON-Projektdatei über den Android-Dateidialog. | Export und Import ergeben ein identisches Projekt. Neuere, unbekannte Dateiversionen werden verständlich abgelehnt. |
| REQ-DATA-005 | M | Datenbankmigrationen sind getestet. | Jede Schemaversion hat einen Migrationstest. |
| REQ-DATA-006 | M | Keine Telemetrie. | Der Build enthält keine Analyse-, Crash- oder Werbe-SDKs. |
| REQ-DATA-007 | S | Projekt duplizieren. | Die Kopie ist vollständig und vom Original unabhängig. |

## 6. Export

| ID | Prio | Anforderung | Akzeptanzkriterium |
|---|---|---|---|
| REQ-EXP-001 | M | PDF im Maßstab mit Maßketten, Maßstabsangabe und Flächen. | Eine Strecke im PDF entspricht beim gewählten Maßstab der Modelllänge (Golden-Test). |
| REQ-EXP-002 | M | Automatische Wahl von 1:50, 1:100 oder 1:200 und A4 oder A3, vor dem Export änderbar. | Gewählt wird der größte Maßstab, bei dem der Plan auf die Seite passt. |
| REQ-EXP-003 | M | Export als PNG und SVG. | Beide Dateien zeigen den vollständigen Plan, SVG ist gültig. |
| REQ-EXP-004 | M | Export als JSON (siehe REQ-DATA-004). | Die Datei ist wieder importierbar. |
| REQ-EXP-005 | M | Teilen über das Android-Teilen-Menü oder Speichern als Datei. | Jede Exportart ist auf beiden Wegen möglich. |
| REQ-EXP-006 | M | Nicht bestätigte Räume werden vor dem Export gemeldet und in PDF, PNG und SVG als „Form nicht bestätigt“ gekennzeichnet. | Die Kennzeichnung ist in allen drei Formaten am Raum und an dessen Fläche vorhanden. |

## 7. Qualität

| ID | Prio | Anforderung | Akzeptanzkriterium |
|---|---|---|---|
| REQ-NFR-001 | M | Der Editor bleibt bei großen Projekten flüssig. | Ziel 60 fps bei 200 Wänden und 30 Räumen, gemessen im Performance-Test. |
| REQ-NFR-002 | M | Schneller Kaltstart. | Unter 2 s auf einem Mittelklasse-Gerät, gemessen auf deinem Handy. |
| REQ-NFR-003 | M | Interne Längen in einem einzigen Einheitensystem, Umrechnung nur an den Rändern. | Eingabe → Speicherung → Anzeige verliert keine Genauigkeit. |
| REQ-NFR-004 | M | Keine unnötigen Berechtigungen und keine Secrets im Repository. | Manifest- und Repository-Prüfung bestehen. |
| REQ-NFR-005 | M | Signierte Release-APK mit R8. | Die APK installiert und startet, der Kernablauf besteht den Smoke-Test. |
| REQ-NFR-006 | M | Testabdeckung: Geometrie ≥ 90 % Zeilen / ≥ 85 % Branches, Domain, Data und ViewModels ≥ 80 %. | Das Kover-Gate besteht. |

## 8. Nicht in v1.0 (Won't, Kandidaten für v1.1+)

Bluetooth-Laser, Sensor- und AR-Messung, 3D-Ansicht, DXF und IFC, CSV-Flächenliste, mehrere Geschosse, Treppen, Stützen, Kamine und Schächte, Dachschrägen, Möbel und Symbole, Cloud-Sync, Konten, Kollaboration, imperiale Einheiten, Play-Store-Release, Monetarisierung.

Höhen und Öffnungsdaten werden bereits so gespeichert, dass eine spätere 3D-Ansicht ohne Datenumbau möglich ist.

## 9. Prozess

| Punkt | Entscheidung |
|---|---|
| Git | Push nach `origin` nach jedem bestandenen Gate |
| CI | GitHub Actions, inklusive Emulator-Tests |
| Parallelität | Maximal 4 Subagenten gleichzeitig |
| Fortschritt | Über `docs/PROGRESS.md`, APK am Ende, kein Stopp zwischen den Wellen |
| Rückfragen | Nach der Freigabe in Phase 2 nur bei echten Blockern |

## 10. Von Claude getroffene Festlegungen

Diese Punkte wurden nicht einzeln abgefragt und gelten mit der Bestätigung dieses Dokuments:

1. Die Grenze zwischen Messfehler und Formabweichung liegt bei 5 cm oder 1 % des Umfangs (REQ-MEAS-011).
2. Der Formvorschlag nutzt die geringste quadratische Abweichung von 90° (REQ-MEAS-007). Am Beispiel 3 × 3 × 3 × 5 m wurde numerisch bestätigt, dass dies das Trapez mit 11,31 m² ergibt.
3. Ein Raum mit n Wänden braucht n − 3 unabhängige Form-Angaben (Winkel oder Diagonalen), um eindeutig zu sein.
4. Das Ausgleichsverfahren für kleine Fehler wird in Phase 2 per ADR festgelegt. Nutzer wählen in v1.0 kein Verfahren aus.
