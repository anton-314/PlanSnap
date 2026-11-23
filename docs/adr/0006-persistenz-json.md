# ADR 0006: Persistenz und JSON-Versionierung

**Status:** Angenommen, 2026-11-23

## Kontext

Auto-Save, Backup und Export sollen dasselbe Datenformat nutzen und getrennt migrierbar bleiben.

## Optionen

Vollständig relationale Tabellen; eine Projektdatei; Room mit JSON-Dokumenten je Raum.

## Entscheidung

Room speichert Projektmetadaten und je Raum ein JSON-Dokument im Format von `:core:document`, plus eine Entwurfstabelle. Die Datei hat `formatVersion` und schrittweise Migrationen. Neuere Hauptversionen werden abgelehnt, unbekannte Felder derselben Hauptversion ignoriert.

## Konsequenzen

Nur geänderte Räume werden geschrieben. Es gibt ein einziges Schema für Datenbankinhalt und Backup. Abfragen innerhalb der Raumdaten sind nicht per SQL möglich und in v1.0 nicht nötig.
