# Entwicklungsumgebung

**Geprüft am:** 2026-11-23, durch tatsächlich ausgeführte Befehle

| Bereich | Ergebnis |
|---|---|
| System | WSL2 (Linux 6.6), 16 Threads, 23 GB RAM, 938 GB frei |
| JDK | OpenJDK 21.0.13 (`JAVA_HOME` nicht gesetzt, `java` im `PATH`) |
| Gradle / Kotlin | Nicht global installiert, Gradle kommt in Phase 3 per Wrapper |
| Android SDK (Linux) | `~/Android/Sdk`: Plattform 36, Build-Tools 36.0.0, Platform-Tools 37.0.3, Command-line Tools |
| Android SDK (Windows) | `C:\Users\anon\AppData\Local\Android\Sdk`, mit Emulator und AVD `Medium_Phone_API_36.1` |
| SDK-Lizenz | Vorhandene Annahme aus dem Windows-SDK übernommen |
| Emulator | Bootet headless in 30 s, API 36, x86_64, 1080 × 2400, Seitengröße 4 KB |
| Netzwerk | Google Maven, Maven Central, Gradle Plugin Portal und Gradle Services erreichbar |
| Git | 2.53.0, SSH-Zugriff auf `origin` funktioniert |
| `gh` | Nicht installiert, für den Ablauf nicht erforderlich |
| `unzip` | Nicht installiert, Python `zipfile` ersetzt es |
| Subagenten-Modelle | `opus`, `sonnet`, `haiku` |

## Konsequenzen für Build und Tests

- Gradle läuft in WSL mit dem Linux-SDK. Dafür braucht das Projekt eine lokale `local.properties` mit `sdk.dir` oder die Variable `ANDROID_HOME=$HOME/Android/Sdk`.
- `/dev/kvm` ist für den WSL-Benutzer nicht beschreibbar, und `sudo` braucht ein Passwort. Emulator-Tests laufen deshalb über den Windows-Emulator und `adb.exe`.
- Geprüft: Gradle-`connectedDebugAndroidTest` erreicht den Windows-Emulator aus WSL nicht (`No connected devices`). Instrumentierte Tests laufen lokal über `scripts/run-instrumented-tests.sh`, das Test-APKs in WSL baut und per `adb.exe` ausführt.
- Gradle Managed Devices sind lokal nicht nutzbar, solange KVM in WSL nicht freigegeben ist. In GitHub Actions bleiben sie möglich.
- Bei maximal 4 parallelen Agenten müssen Gradle-Heap und Worker begrenzt werden, damit 23 GB RAM reichen.
- Echtgerät-Tests (Kaltstart, Bedienung) kann nur der Auftraggeber auf dem eigenen Handy ausführen.
