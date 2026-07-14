# Brix TODO

Die Priorisierung basiert auf der Performance- und Stabilitätsprüfung aus Sicht
von app-shop. `brix-demo` und der Clustered Workspace Manager sind ausdrücklich
nicht Bestandteil dieser Aufgaben. Für app-shop ist der normale
`Jcr2WorkspaceManager` mit ModeShape maßgeblich.

## Hohe Priorität

### Publishing und Snapshot-Restore atomar oder wiederherstellbar machen

Beim Publishing und beim Snapshot-Restore wird der Ziel-Workspace zunächst
geleert und dieser Zustand gespeichert. Erst danach werden die Ersatzdaten
geklont beziehungsweise importiert. Ein Fehler oder Prozessabbruch kann deshalb
einen leeren oder nur teilweise aufgebauten produktiven Workspace hinterlassen.

Betroffene Stellen:

- `brix-core/src/main/java/org/brixcms/Brix.java`
- `brix-plugin-publish/src/main/java/org/brixcms/plugin/publishing/PublishingPlugin.java`
- `brix-plugin-snapshot/src/main/java/org/brixcms/plugin/snapshot/web/ManageSnapshotsPanel.java`

Ziel:

- Ersatzinhalt möglichst in einem separaten Workspace aufbauen und validieren,
  bevor atomar auf ihn umgeschaltet wird.
- Falls ein atomarer Wechsel mit einem Backend nicht möglich ist, vor dem
  Austausch eine Rückfallkopie anlegen und sie bei jedem Fehler wiederherstellen.
- Gleichzeitige Publish- und Restore-Vorgänge für dasselbe Ziel koordinieren.
- Die Lösung in den gemeinsamen JCR-Abstraktionen so umsetzen, dass Jackrabbit
  und ModeShape weiterhin unterstützt werden.

Abnahmekriterien:

- Ein absichtlich fehlschlagender Clone oder Import erhält den zuvor
  veröffentlichten Inhalt vollständig.
- Parallele Requests sehen weder einen leeren noch einen teilweise aufgebauten
  Production-Workspace.
- Zwei gleichzeitige Veröffentlichungen auf dasselbe Ziel führen zu einem
  definierten Ergebnis.

## Mittlere Priorität – Performance

### Menü-Selektion nur einmal pro Rendering berechnen

`brix-plugin-menu/src/main/java/org/brixcms/plugin/menu/tile/fulltree/MenuRenderer.java`
berechnet die vollständige Menge ausgewählter Menüpunkte derzeit innerhalb der
Schleife über die Root-Einträge erneut.

- `getSelectedItems(menu)` einmal vor der Schleife ausführen und das Ergebnis
  wiederverwenden.
- Das bestehende Verhalten für ausgewählte und geöffnete Menüzweige mit Tests
  absichern.
- Die entsprechende app-shop-Implementierung im whiskyworld-Repository bei der
  Anpassung mitprüfen.

### Fast-Path für virtuelle app-shop-Katalog-URLs prüfen

Der `ShopRequestMapper` im whiskyworld-Repository lässt virtuelle Katalog-URLs
zunächst durch die generische Brix-Auflösung laufen. Dadurch können unnötige
negative `itemExists`- und Ancestor-Abfragen entstehen.

- Zunächst Anzahl und Laufzeit der JCR-Aufrufe je Request messen.
- Bei nachgewiesenem Aufwand einen Fast-Path vor der generischen Auflösung
  einführen.
- Wicket-Listener-, Resource- und normale Brix-URLs müssen weiterhin über die
  bestehende Auflösung laufen.

### Katalogpfad-Auflösung indexieren

Die app-shop-Auflösung durchsucht Katalogpfade derzeit linear.

- Den Aufwand unter realistischen Workspace-Daten messen.
- Bei bestätigtem Hotspot einen beim Cache-Aufbau erzeugten Pfadindex oder Trie
  einsetzen.
- Cache-Invalidierung und Workspace-Trennung müssen für den Index erhalten
  bleiben.

## Niedrige Priorität – bekannte Risiken

### Workspace-Metadaten und In-Memory-Indizes konsistent halten

`AbstractSimpleWorkspaceManager` aktualisiert Teile seiner In-Memory-Indizes,
bevor die zugehörige JCR-Änderung erfolgreich gespeichert ist. Bei einem
Repositoryfehler können Cache und persistierter Zustand bis zum Neustart
voneinander abweichen. Mehrere Attribute verursachen außerdem mehrere Sessions
und teilweise redundante Saves.

- Attribute zuerst erfolgreich persistieren und erst danach die Indizes
  aktualisieren; Fehler vollständig zurückrollen.
- Eine Batch-Operation für mehrere Workspace-Attribute prüfen.
- Redundante Saves entfernen.
- Die Eindeutigkeit von `(type, name, state)` prüfen und Duplikate nicht durch
  Auswahl eines beliebigen `HashSet`-Treffers verdecken.
- Änderungen müssen mit Jackrabbit und ModeShape funktionieren; der Clustered
  Workspace Manager bleibt außerhalb des Scopes.

### ModeShape-Start, -Shutdown und Konfiguration härten

Der ModeShape-Service im whiskyworld-Repository ignoriert derzeit teilweise den
übergebenen Repository-Pfad, kann bei ungültiger Konfiguration den gesamten
Prozess über `System.exit` beenden und behandelt partielle Start- sowie
Shutdown-Fehler nicht robust. Die eingesetzte Kombination aus ModeShape
5.4.1.Final und H2 2.4.240 benötigt zudem explizite Lifecycle- und
Recovery-Tests.

- Konfigurierten Pfad tatsächlich verwenden und Abweichungen sichtbar machen.
- `System.exit` aus dem Service-Lifecycle entfernen.
- Start und Stop idempotent gestalten; partiell gestartete Ressourcen abbauen.
- Shutdown mit Timeout, vollständigem Fehler-Logging und korrekter
  Interrupt-Behandlung ausführen.
- Deploy/Undeploy/Reopen, abrupten Prozessabbruch und Backup-Restore mit der
  tatsächlich eingesetzten H2-Version testen.

## Backlog

### app-shop nach Fertigstellung von Brix 10.17 aktualisieren

app-shop bleibt bis zur Fertigstellung von Brix 10.17 auf dem derzeit vorgesehenen
Brix-Stand. Anschließend:

- app-shop auf die fertige Version 10.17 aktualisieren,
- den vollständigen app-shop-Build und die relevanten Integrations-/Lasttests
  ausführen,
- die Markup-Cache-Invalidierung nach Publishing verifizieren und
- separat prüfen, welche app-shop-spezifischen Workspace-, Katalog-, Referenz-
  und Menü-Caches nach einem erfolgreichen Publishing gezielt invalidiert oder
  neu aufgebaut werden müssen.

## Separater Prüfauftrag für Codex im whiskyworld-Repository

Dieser Prüfauftrag gehört fachlich zu app-shop und ist keine
Brix-Implementierungsaufgabe. Er kann im Root des whiskyworld-Projekts an Codex
übergeben werden:

```text
Untersuche das whiskyworld-Projekt, insbesondere die Module plugin-shop und
app-shop, auf requestübergreifend geteilte mutable JCR- und Wicket-Objekte in
Anwendungscaches. Arbeite zunächst read-only und ändere keinen Produktionscode.
Ignoriere Demo-Anwendungen und den Clustered Workspace Manager. Berücksichtige
den von app-shop tatsächlich verwendeten Production-Workspace mit ModeShape und
dem normalen Jcr2WorkspaceManager.

Ausgangshypothese:
JCacheHelper verwendet storeByValue(false). Dadurch könnten dieselben Instanzen
von BrixNode, BrixNodeModel, IModel, Reference, LabeledReference, Menu oder
anderen session- beziehungsweise requestgebundenen Objekten zwischen Threads
geteilt werden. Zusätzlich könnte CachedMenuRenderer fertiges HTML mitsamt
aktuellem Selektionszustand oder berechtigungsabhängiger Sichtbarkeit global
cachen.

Prüfaufgaben:
1. Erfasse alle app-shop-/plugin-shop-Caches mit Scope, Lebensdauer, Schlüssel,
   Werttyp, Invalidierungsweg und den Stellen, an denen Werte gelesen und
   geschrieben werden.
2. Verfolge für jeden Werttyp, ob er mutable Models, BrixNode-Instanzen,
   JCR-Sessions, Wicket-Komponenten oder andere requestgebundene Zustände hält.
3. Prüfe konkret SimpleLinkLabel, CatalogPanel, MultiLinkPanel,
   AppShopWorkspaceCache, AppShopCache, FullNaviMenuRendererMegaDropDown und
   CachedMenuRenderer sowie alle vergleichbaren Fundstellen.
4. Analysiere mögliche Rennen zwischen load(), getObject() und detach(), die
   Wiederverwendung geschlossener oder requestfremder Sessions und das
   Wiederbefüllen eines gerade invalidierten Caches durch bereits laufende
   Requests.
5. Prüfe beim Menü getrennt, ob ausgewählter Pfad, Benutzerberechtigungen oder
   sonstiger Requestzustand in global gecachtes HTML einfließen.
6. Entwirf fokussierte Nebenläufigkeits- und Session-Lifecycle-Tests, mit denen
   bestätigte Risiken reproduzierbar nachgewiesen werden können. Führe
   bestehende passende Tests aus; lege ohne weitere Beauftragung noch keine
   dauerhaften Produktionsänderungen an.

Liefere einen nach Schweregrad sortierten Bericht mit exakten Datei- und
Zeilenangaben. Trenne bestätigte Fehler, strukturelle Risiken und nicht
bestätigte Hypothesen. Beschreibe für jeden bestätigten Befund ein konkretes
Fehlerszenario, eine geeignete Teststrategie und eine Lösung, bei der globale
Caches nur unveränderliche DTOs wie Workspace-ID, Node-ID/Pfad, URL, kopierte
Parameter und Labels enthalten. Modelle, References und Nodes sollen bei Bedarf
pro Request neu erzeugt werden. Implementiere die Änderungen erst nach
ausdrücklicher Freigabe.
```
