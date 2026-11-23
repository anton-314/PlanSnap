# PlanSnap – Verbindliche Workflows

## 1. Lebenszyklus einer Aufgabe

`Ready → In Arbeit → Selbstprüfung → Review → Merge-Bereit → Gemerged → Verifiziert`

**Definition of Ready:** Vertrag eingefroren, Abhängigkeiten gemergt, Akzeptanzkriterien testbar, Besitzbereich eindeutig.

**Definition of Done** (nachweisbar im Bericht):

1. Das Modul kompiliert: `./gradlew :<modul>:assemble`.
2. Alle Tests des Moduls sind grün: `./gradlew :<modul>:test` (JVM) oder `:<modul>:testDebugUnitTest` (Android).
3. Keine neuen Verstöße: `./gradlew :<modul>:spotlessCheck :<modul>:detekt`, bei Android-Modulen zusätzlich `:<modul>:lintDebug`.
4. Jedes Akzeptanzkriterium ist abgehakt und durch mindestens einen Test belegt.
5. Öffentliche API hat KDoc. Kein Dead Code, keine ungenutzten Imports, kein `TODO` oder `FIXME`.
6. Keine Änderungen außerhalb des Besitzbereichs: `git diff --name-only main...HEAD`.
7. Commit im Format `<type>(<scope>): <summary> [T-ID]`.

## 2. Umgebung

Vor jedem Gradle-Aufruf gilt:

```bash
export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64
export ANDROID_HOME="$HOME/Android/Sdk"
```

### Speichergrenzen (23 GB RAM, maximal 4 Agenten)

| Prozess | Grenze |
|---|---|
| Gradle-Daemon | `-Xmx3g`, `-XX:MaxMetaspaceSize=1g` |
| Kotlin-Daemon | `-Xmx2g` |
| Worker je Build | `org.gradle.workers.max=3` |
| Test-JVM | `maxHeapSize=1g`, `maxParallelForks=2` |

Agenten führen während der Arbeit nur Tasks des eigenen Moduls aus. Volle Builds laufen nur beim Orchestrator, wenn keine Agenten-Builds aktiv sind. Worktrees teilen `~/.gradle`, Daemons dürfen nicht pauschal beendet werden.

### Emulator (Windows, über `adb.exe`)

```bash
W=/mnt/c/Users/anon/AppData/Local/Android/Sdk
A="$W/platform-tools/adb.exe"
(cd /mnt/c/Users/anon && nohup "$W/emulator/emulator.exe" -avd Medium_Phone_API_36.1 \
  -no-window -no-audio -no-boot-anim -no-snapshot -read-only >/dev/null 2>&1 &)
timeout 240 "$A" wait-for-device shell \
  'while [ "$(getprop sys.boot_completed)" != "1" ]; do sleep 2; done'
```

Nur ein Agent nutzt den Emulator gleichzeitig, und zwar der vom Orchestrator benannte. APK-Pfade für `adb.exe` müssen mit `wslpath -w` umgewandelt werden. Instrumentierte Tests:

```bash
./gradlew :app:assembleDebug :app:assembleDebugAndroidTest
"$A" install -r -t "$(wslpath -w app/build/outputs/apk/debug/app-debug.apk)"
"$A" install -r -t "$(wslpath -w app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk)"
"$A" shell am instrument -w app.plansnap.test/androidx.test.runner.AndroidJUnitRunner
"$A" emu kill
```

Das Ergebnis gilt nur als bestanden, wenn die Ausgabe `OK (` enthält. In Phase 3 wird geprüft, ob `connectedCheck` aus WSL direkt funktioniert. Bis dahin ist dieser Ablauf verbindlich.

## 3. Taktung

| Zeitpunkt | Was läuft | Wer |
|---|---|---|
| Während der Arbeit | Modultests, Compile | Agent |
| Vor „fertig“ | Komplette DoD | Agent |
| Nach jedem Task-Merge | `./gradlew assembleDebug test testDebugUnitTest --continue` für betroffene Module | Orchestrator |
| Wellen-Gate | `./gradlew build koverVerify verifyRoborazziDebug`, Architekturtest, Review | Orchestrator + `reviewer` |
| Phasen-Gate | Zusätzlich instrumentierte Tests, Migrationstests, Smoke-Test mit Screenshots | `test-engineer` |
| Vor Release | Release-Build, R8-Smoke-Test, Baseline Profile, APK-Größe, Startzeit | `build-engineer` |

Ein rotes Gate wird nie übersprungen. Die Welle bleibt offen, und der Orchestrator verteilt Fix-Aufgaben. Nach jedem bestandenen Gate: `docs/PROGRESS.md` aktualisieren, taggen und `git push origin main --tags`. Nie mit Force pushen.

## 4. Agenten und Isolation

- Maximal **4 Subagenten gleichzeitig**. Größere Wellen werden in Batches geteilt.
- Jeder parallele Agent arbeitet in einem eigenen Worktree auf `task/<ID>-<slug>`.
- Ein Pfad hat pro Batch genau einen Besitzer.
- `settings.gradle.kts`, `gradle/libs.versions.toml`, `build-logic/`, Navigationsgraph und DI-Wurzel ändern nur Orchestrator oder `build-engineer`. Andere melden den Bedarf im Bericht.
- Merge und Konfliktlösung macht nur der Orchestrator: Rebase auf `main`, Squash je Aufgabe.

**Eskalation:** Nach zwei gescheiterten Versuchen geht die Aufgabe mit Fehlerprotokoll an die nächsthöhere Stufe (`haiku → sonnet → opus`). Scheitert auch diese, entscheidet der Orchestrator oder `architect`.

**Downgrade:** Rein mechanische Aufgaben gehen an `haiku`.

## 5. Vertragsfreeze

Nach Phase 3 sind die öffentlichen Schnittstellen in `:core:*` und die Modulgrenzen eingefroren. Eine Änderung braucht einen Change-Request an `architect`, eine ADR und die Verteilung durch den Orchestrator. Rein additive interne Helfer sind kein Vertragsbruch.

## 6. Bugfix

Reproduktion als fehlschlagender Test, dann Fix, dann grüner Test. Der Regressionstest bleibt dauerhaft.

## 7. Review

`reviewer` ist unabhängig vom Autor und prüft Korrektheit, Randfälle, Nebenläufigkeit, Speicher, Architektur, Testqualität, Sicherheit und Lesbarkeit. Ergebnis ist `APPROVE` oder `CHANGES_REQUESTED` mit Dateipfad und Zeile. Kritische und hohe Findings blockieren das Gate.

## 8. Kommunikation

Berichte sind kurz und enthalten Befehl plus Ergebnisauszug. Bei unklarer Spezifikation wird nicht geraten, sondern `BLOCKED` mit einer konkreten Frage gemeldet. Fehler außerhalb des eigenen Bereichs werden gemeldet, nicht selbst behoben. Neue Ideen gehören in `docs/BACKLOG.md`.

## 9. Briefing-Vorlage

```text
ROLLE & ZIEL:
AUFGABE-ID:
KONTEXT: Lies zuerst docs/WORKFLOWS.md, docs/ARCHITECTURE.md (Abschnitte …), docs/TESTING.md, docs/DOMAIN.md (falls relevant)
BESITZBEREICH (darf ändern):
NICHT ANFASSEN: alles andere
VERTRAG:
AKZEPTANZKRITERIEN:
MITZULIEFERNDE TESTS:
DEFINITION OF DONE: siehe WORKFLOWS.md §1, Befehle selbst ausführen
BERICHT: Status | geänderte Dateien | Befehle + Ergebnis | offene Punkte | Änderungswünsche
```

## 10. Git

`main` ist immer grün. Pro Aufgabe ein Branch, kleine atomare Commits. Tags: `phase-3-foundation`, `wave-1` bis `wave-5`, `phase-5-hardening`, `phase-6-release`. Keine destruktiven Git-Operationen ohne Notwendigkeit, keine Secrets oder Keystores im Repository.
