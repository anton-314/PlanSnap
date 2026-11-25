# ADR 0020: Application-Scope als Typ in `:core:common`

**Status:** Angenommen, 2026-11-25 (CR-W3-01), ergänzt ADR 0016

## Kontext

Bestätigte Befehle und das Speichern laufen im Application-Scope und überleben das ViewModel (ARCHITECTURE §5, ADR 0016). T-EDT-001 braucht diesen Scope im `EditorViewModel`. Bisher war er nur über den Qualifier `@ApplicationScope` auf `CoroutineScope` erreichbar, deklariert in `:app` (`app.plansnap.di.RuntimeModule`). `:feature:editor` hängt nicht von `:app` ab und darf es nach ARCHITECTURE §2 nicht. `:core:common` ist ein reines JVM-Modul mit `kotlinx-coroutines-core` als einziger Abhängigkeit, `javax.inject` steht nicht im Version Catalog.

## Optionen

- **A:** Den Qualifier nach `:core:common` verschieben. Das braucht `javax.inject` als neue Abhängigkeit des reinen Kerns und einen Katalogeintrag. Ein ungekennzeichneter `CoroutineScope` bliebe leicht verwechselbar.
- **B:** Ein eigener Typ `ApplicationScope` in `app.plansnap.core.common.runtime`, der `CoroutineScope` per Delegation implementiert. `:app` stellt genau eine Instanz bereit. Keine neue Abhängigkeit, der Typ selbst unterscheidet den Scope.
- **C:** Ein Qualifier je Feature-Modul oder ein Hilt-Modul in jedem Feature. Das verteilt eine prozessweite Entscheidung auf mehrere Module.

## Entscheidung

Option B.

- `class ApplicationScope(delegate: CoroutineScope) : CoroutineScope by delegate` in `app.plansnap.core.common.runtime`. Der Kontext des Delegaten wird unverändert durchgereicht.
- `ApplicationScope.create(dispatchers)` baut die Produktionsinstanz: `SupervisorJob` und `dispatchers.default`. Ein fehlschlagendes Kind bricht seine Geschwister nicht ab.
- `:app` stellt sie in `RuntimeModule` als einzige `@Singleton`-Bindung bereit. Der Qualifier `@ApplicationScope` in `:app` entfällt, `DataModule` bezieht den DataStore-Scope aus derselben Instanz.
- Aufrufer starten Arbeit im Scope, brechen ihn oder seinen Job aber nie ab.
- Tests übergeben einen eigenen Scope, etwa `ApplicationScope(testScope.backgroundScope)`.

Die Änderung an `:core:common` ist additiv. Bestehende öffentliche Typen bleiben unverändert, Modulgraph und Regeln von `checkArchitecture` ändern sich nicht. Feature-Module sehen den Typ über `:core:domain` → `:core:model` → `:core:common` (jeweils `api`).

## Konsequenzen

Jedes Modul mit Zugriff auf `:core:common` kann den Application-Scope per Konstruktorinjektion erhalten, ohne `:app` oder `javax.inject` zu kennen. Weil der Typ `CoroutineScope` ist, kann ein Aufrufer ihn technisch abbrechen. Das schließt nur die Regel oben aus, kein Compiler. Der Graph-Test in `:app` prüft, dass die Bindung per Konstruktorinjektion auflösbar und dieselbe Instanz ist und dass ein fehlschlagendes Kind den Scope nicht beendet.
