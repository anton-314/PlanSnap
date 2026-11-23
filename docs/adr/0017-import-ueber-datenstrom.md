# ADR 0017: Import über Datenstrom

**Status:** Angenommen, 2026-11-23

## Kontext

`ProjectRepository.importProject(project)` setzte ein bereits decodiertes Projekt voraus. `:feature:projects` hängt aber nicht von `:core:document` ab und könnte das Dokument gar nicht decodieren. Außerdem hing die Prüfung nicht vertrauenswürdiger Dateien davon ab, dass jeder Aufrufer sie auslöst.

## Optionen

- **A:** Features erhalten eine Abhängigkeit auf `:core:document`. Das verletzt den Modulschnitt.
- **B:** Ein zusätzlicher Use Case koordiniert Codec und Repository. Er bräuchte dieselbe Abhängigkeit oder eine weitere Abstraktion.
- **C:** `importProject(input: InputStream)`. `:core:data` übernimmt Decodieren, Validieren und Speichern.

## Entscheidung

Option C. Die Reihenfolge ist `DocumentCodec.decodeProject`, `ProjectValidator.validate`, Speichern. Der erste Fehler wird unverändert zurückgegeben und lässt die gespeicherten Daten unberührt. Der Aufrufer besitzt den Stream und schließt ihn.

## Konsequenzen

Es gibt genau einen Importpfad, der die Prüfung nicht umgehen kann. Das Feature kennt nur den Stream aus dem Dateidialog. Der Fake in `:testing:fixtures` erhält dafür einen injizierten Decoder.
