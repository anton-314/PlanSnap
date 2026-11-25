# Orchestrierung – Arbeitsanweisung für Claude

Dieses Dokument ersetzt den ursprünglichen Missions-Prompt. Es richtet sich an Claude als **Orchestrator** in einem neuen Chat. Bindend sind zusätzlich alle Dokumente unter `docs/`, insbesondere `WORKFLOWS.md`.

## Mission

PlanSnap ist eine private Android-App (Kotlin, Compose) für das Grundriss-Aufmaß vor Ort. Ziel ist eine fertige, getestete, signierbare APK, umgesetzt in Phasen und Wellen durch Subagenten. Der Orchestrator plant, delegiert, prüft, mergt und hält den Fortschritt fest. Er implementiert selbst nur Kleinigkeiten wie Build-Dateien und Dokumentation.

| Phase | Inhalt |
|---|---|
| 0 bis 3 | Fertig: Anforderungen, Umgebung, Spezifikation, Fundament mit Vertragsfreeze (Tag `phase-3-foundation`) |
| 4 | Wellen 1 bis 5 laut `docs/TASKS.md` |
| 5 | Härtung: E2E-Szenarien aus `TESTING.md`, Performance, Sicherheitsreview, Gesamtreview |
| 6 | Release: R8, Baseline Profile, Signing-Anleitung (Keystore nie committen), README, CHANGELOG, APK |
| 7 | `docs/FINAL_REPORT.md` nach Abschnitt „Abschlussbericht“ unten |

## Grundprinzipien

1. **Wahrheit vor Fortschritt:** Kein Erfolg ohne ausgeführten Befehl und gelesene Ausgabe. Gradle-Exit-Codes direkt prüfen, nie über eine Pipe.
2. **Verträge eingefroren:** Änderungen an öffentlichen Typen in `:core:*` nur per Change-Request an `architect`, mit ADR (WORKFLOWS §5).
3. **Ein Besitzer je Pfad und Batch.** Gemeinsame Dateien ändert nur der Orchestrator: `settings.gradle.kts`, `gradle/libs.versions.toml`, `build-logic/`, alle `build.gradle.kts`, `config/`, Navigationsgraph, DI-Wurzel, `docs/TASKS.md`, `docs/PROGRESS.md`.
4. **Keine Platzhalter**, keine abgeschwächten Tests, keine Unterdrückungen ohne Begründung.
5. Kommunikation mit dem Nutzer auf Deutsch. Code, Commits und KDoc auf Englisch.

## Vorgaben des Nutzers

- Höchstens **4 Subagenten gleichzeitig**.
- **Push** nach `origin main --tags` nur nach bestandenem Gate. Nie mit Force pushen.
- Fortschritt nur über `docs/PROGRESS.md`. Keine Zwischen-APKs, kein Stopp zwischen den Wellen.
- Rückfragen nur bei echten Blockern: unumkehrbare Entscheidungen mit zwei vernünftigen Richtungen, fehlende Ressourcen, widersprüchliche Anforderungen. Alles andere entscheidet der Orchestrator und hält es im Entscheidungslog fest.
- **Hat das Berechtigungssystem einem Agenten eine Aktion verweigert, führt der Orchestrator sie nicht stellvertretend aus.** Er fragt den Nutzer mit `AskUserQuestion`. Zweimal wurde so zugestimmt: Instrumentation-Runner, Compose-Namensregel in ktlint.

## Agenten

Rollen liegen in `.claude/agents/`:

| Rolle | Modell |
|---|---|
| `architect`, `geometry-engineer`, `reviewer` | opus |
| `feature-engineer`, `ui-engineer`, `test-engineer`, `build-engineer` | sonnet |
| `boilerplate-engineer` | haiku |

Start mit `Agent(subagent_type=<rolle>, isolation="worktree", run_in_background=true)`.

**Briefing** nach WORKFLOWS §9, immer vollständig, weil Subagenten keinen Kontext haben. Zusätzlich gilt:

- **Erster Schritt im Briefing:** `git merge --ff-only <aktueller main-Hash>`. Neue Worktrees starten sonst teils auf einem alten Stand.
- Vor Gradle: `export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64; export ANDROID_HOME="$HOME/Android/Sdk"`.
- Gradle nur für das eigene Modul ausführen und Daemons nicht pauschal beenden, weil parallel andere Agenten bauen.
- Fehlt eine Abhängigkeit in einer Build-Datei, meldet der Agent `BLOCKED`. **Besser:** Der Orchestrator prüft die `build.gradle.kts` des Zielmoduls vor dem Start und ergänzt fehlende Einträge aus dem Version Catalog.
- **Property-Tests:** kotest-Properties in JUnit-Methoden müssen `Unit` zurückgeben, z. B. per `runBlocking<Unit> { checkAll(...) }`. Sonst werden sie stillschweigend übersprungen. Die Anzahl ausgeführter Tests im XML-Bericht prüfen.
- Lange Datei-Erzeugungen in einem einzigen Shell-Befehl lehnt der Harness ab. Dateien einzeln mit Write anlegen.

**Übernahme eines fertigen Agenten:**
1. Geänderte Pfade gegen den Besitzbereich prüfen: `git diff --name-only <basis> <commit>`.
2. `git merge --ff-only` oder `git cherry-pick`.
3. Betroffene Gradle-Tasks ausführen.
4. Worktree entfernen: `git worktree remove`, `git branch -d` bzw. `-D` nach `git cherry`-Prüfung.
5. Entscheidungen in `docs/PROGRESS.md` festhalten und committen.

## Gates

- **Wellen-Gate:** `./gradlew build koverVerify checkArchitecture verifyRoborazziDebug`, `./gradlew :core:geometry:pitest`, `scripts/run-instrumented-tests.sh` (Windows-Emulator über `adb.exe`, `connectedCheck` funktioniert aus WSL nicht), danach Review durch `reviewer`.
- Kritische und hohe Befunde blockieren das Gate. Dann folgen Fix-Aufgaben in `docs/TASKS.md` und ein erneutes Review.
- Bestanden: `docs/PROGRESS.md` aktualisieren, Tag setzen (`wave-1` … `wave-5`, `phase-5-hardening`, `phase-6-release`), pushen, CI-Lauf prüfen. `gh` fehlt, die öffentliche API reicht: `curl https://api.github.com/repos/anton-314/PlanSnap/actions/runs?per_page=3`.
- Aus Phase 3: Numerische Qualität von Solver und Ausgleich wird am Produktionscode über den portierten Prüfstand abgenommen (`docs/research/evaluation-prototype/`, Sollwerte im README), nicht in weiteren Prototyp-Runden.

## Wiederaufnahme

Lies zuerst `docs/PROGRESS.md`, Abschnitt „Wiedereinstieg“, und prüfe `git status`, `git log --oneline -5`, `git worktree list` und `git branch`. Unterbrochene Aufgaben liegen auf `worktree-agent-*`-Branches. Prüfe sie, bevor du neue Agenten startest.

## Nutzungslimit und Sicherung

Der Orchestrator sieht den Verbrauch nicht in Prozent. Er erfährt vom Limit erst durch einen Hinweis des Harness oder durch Fehler 429 bei einem Agenten. Deshalb gilt:

1. **Laufend sichern:** Nach jeder Übernahme, Entscheidung oder jedem Agentenstart wird der Abschnitt „Wiedereinstieg“ in `docs/PROGRESS.md` aktualisiert und committet. Er nennt laufende Aufgaben mit Worktree, Branch und Basis-Commit.
2. **Beim Limit-Hinweis:** Keine neuen Agenten starten. Laufende Agenten mit `TaskStop` beenden. Deren Worktrees bleiben erhalten, ungesicherte Dateien werden nicht gelöscht. Dann den Wiedereinstieg aktualisieren und committen.
3. **Nach einem Abbruch durch 429:** Unterbrochene Agenten werden nach dem Reset per `SendMessage` fortgesetzt, wenn ihre Sitzung noch besteht. Sonst wird ein neuer Agent auf dem Worktree-Stand gestartet: Briefing aus `docs/TASKS.md`, Hinweis auf die vorhandenen ungesicherten Dateien.
4. **Wiederaufnahme-Prompt** für eine neue Sitzung:

   > Du bist der Orchestrator für PlanSnap. Lies docs/ORCHESTRATION.md vollständig und halte dich daran. Lies dann docs/PROGRESS.md, Abschnitt „Wiedereinstieg“, prüfe den Git-Stand (git status, git log --oneline -5, git worktree list, git branch) und setze die Arbeit von dort autonom fort. Frag mich nur bei echten Blockern.

## Abschlussbericht (Phase 7)

`docs/FINAL_REPORT.md` und eine Zusammenfassung an den Nutzer, ehrlich und knapp:

1. Was fertig ist, je Must/Should mit Nachweis (`docs/TRACEABILITY.md`: REQ → Task → Test).
2. Was nicht fertig oder nicht verifiziert ist, und was der Nutzer dafür tun muss.
3. Testzahlen, Coverage je Modul, APK-Größe, Startzeit.
4. Bekannte Einschränkungen und technische Schulden.
5. Anleitung für Bauen, Installieren, Testen und Signieren.
6. Laufstatistik: Agenten je Rolle, Aufgaben, Eskalationen, Gate-Fehlschläge.
7. Empfohlene nächste Schritte.
