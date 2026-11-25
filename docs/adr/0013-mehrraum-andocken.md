# ADR 0013: Mehrraum und Andocken

**Status:** Angenommen, 2026-11-23

## Kontext

Räume sollen exakt aneinander liegen, aber auch frei korrigierbar bleiben.

## Optionen

Gemeinsamer Wandgraph; nur freie Transformationen; Transformation plus optionaler Dock-Link.

## Entscheidung

Jeder Raum hat eine starre Platzierung und optional einen Dock-Link zu einer Wirtswand. Die Trennwandstärke ist die Stärke der Wirtswand. Freies Verschieben oder Drehen löst den Link.

## Konsequenzen

Raumformen bleiben unabhängig lösbar. Es gibt keinen globalen Solver. Zyklen werden beim Andocken abgelehnt.

## Nachtrag 2026-11-25: Bezugspunkt und Umlaufsinn (CR-W2-03)

**Anlass:** Die erste Umsetzung legte den Startpunkt `V_j` der eigenen Wand bei `hostOffset` ab und richtete die Wand immer antiparallel aus. Bei `hostOffset = 0` lag der Gast damit neben dem Wandanfang statt an ihm, ein Andocken an eine Tür mit Offset 0 überdeckte die Tür nicht. Bei entgegengesetztem Umlaufsinn von Wirt und Gast lag der Gast im Wirt.

**Optionen zum Bezugspunkt:** `V_j` bei `hostOffset` (bisher); das `V_i` nächste Ende der eigenen Wand bei `hostOffset`.

**Optionen zum Umlaufsinn:** immer antiparallel (Überlappung möglich); Richtung aus dem Umlaufsinn ableiten; Gast spiegeln; Andocken mit eigener `DomainRule` ablehnen.

**Entscheidung:**

- Der Gast belegt entlang der Wirtswand das Intervall `[a, a + L_j]` mit `a = hostOffset`, bei einer Öffnung `a = opening.offset + hostOffset`. Nutzer docken einen Raum „ab“ einer Stelle an, `hostOffset = 0` bedeutet bündig mit dem Wandanfang oder dem Öffnungsbeginn. Die Definition hängt nicht vom Umlaufsinn ab, und die Umrechnung beim Löschen einer Öffnung bleibt `hostOffset + opening.offset`.
- Die Richtung der eigenen Wand ist `e_j = −s_W · s_G · e_i`, sodass das Innere des Gastes immer auf der Außenseite der Wirtswand liegt. Nutzer messen Räume in beliebiger Richtung, eine Ablehnung wäre unverständlich. Spiegeln scheidet aus, weil die Platzierung starr ist und ein Spiegelbild die gemessene Form verfälscht. Es entsteht keine neue `DomainRule`, kein Vertrag ändert sich.

**Konsequenzen:** Gespeicherte `hostOffset`-Werte behalten ihre Zahl, bedeuten aber eine andere Lage. Da vor CR-W2-03 keine Version veröffentlicht wurde, ist keine Migration nötig. Überschneidungen abseits der Andockwand (nicht konvexe Formen, Überstand) werden weiterhin nicht geprüft. DOMAIN §10 ist maßgeblich.
