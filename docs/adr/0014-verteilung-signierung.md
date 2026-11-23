# ADR 0014: Private Verteilung und Signierung

**Status:** Angenommen, 2026-11-23

## Kontext

Die App wird nur privat als APK installiert.

## Optionen

Play-Store-Vorbereitung; signierte private APK.

## Entscheidung

Release-APK mit R8. Die Signatur kommt aus einer lokalen `keystore.properties` außerhalb der Versionskontrolle. Keine Store-Texte, kein AAB und keine Data-Safety-Angaben. `allowBackup` ist `false`, Sicherung läuft über die Projektdatei.

## Konsequenzen

Der Nutzer muss den Schlüssel sicher aufbewahren, weil Updates ohne ihn nicht installierbar sind. Android-Cloud-Backup enthält keine Projekte.
