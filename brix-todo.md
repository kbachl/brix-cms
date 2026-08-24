# Brix TODO

Die Priorisierung basiert auf der Performance- und Stabilitätsprüfung aus Sicht
von app-shop. `brix-demo` und der Clustered Workspace Manager sind ausdrücklich
nicht Bestandteil dieser Aufgaben. Für app-shop ist der normale
`Jcr2WorkspaceManager` mit ModeShape maßgeblich.

## Audit 2026-08-24 – kritische Fehlerpfade aus Sicht von app-shop

Die folgenden Punkte stammen aus einer gemeinsamen Prüfung von Brix und
Whiskyworld `app-shop`/`plugin-shop`. OpenSearch- und relationale
Datenbankursachen sowie deterministische Fehler, die den gesamten Shop für alle
Benutzer lahmlegen würden, sind ausdrücklich nicht Bestandteil dieses Audits.

Die Zuordnung bezeichnet die primäre Fehlerursache:

- **Brix:** Fehler liegt im Brix-CMS und sollte hier behoben werden.
- **app-shop:** Fehler liegt im Whiskyworld-Repository.
- **Integration:** Brix stellt den Schutz oder Aufrufpfad bereit, app-shop macht
  ihn durch seine Konfiguration oder Fehlerbehandlung unwirksam.

### P1 – Zugriff auf unveröffentlichte Workspaces im Shop verhindern

**Zuordnung: Integration; konkrete Freigabelücke in app-shop.**

`WorkspaceUtils` validiert Workspaces aus `brix:workspace`, Referer und dem
`brix-revision`-Cookie über eine `ViewWorkspaceAction`. Die
`ShopAuthorizationStrategy` erlaubt jedoch jede Action außer dem Zugriff auf
die Workspace-Switcher-Toolbar. Damit ist die Brix-Sicherheitsprüfung im Shop
für `ViewWorkspaceAction` wirkungslos. Ein anonymer Benutzer kann bei Kenntnis
einer existierenden Workspace-ID Draft- oder Staging-Inhalte abrufen; ein
Preview-Cookie kann diesen Zustand über weitere Requests erhalten.

Betroffene Stellen:

- `brix-core/src/main/java/org/brixcms/workspace/WorkspaceUtils.java`
- `app-shop/src/main/java/de/whiskyworld/shop/web/ShopAuthorizationStrategy.java`

Ziel und Abnahmekriterien:

- Anonyme Benutzer dürfen ausschließlich den Production-/Default-Workspace
  sehen.
- Preview-Workspaces erfordern eine ausdrücklich berechtigte, authentifizierte
  Rolle.
- Integrationstests decken URL-Parameter, Referer, Cookie und Logout ab.

### P1 – Unerwartete Fehler bei der URL-Erzeugung nicht als stille 404 tarnen

**Zuordnung: app-shop; der verdeckte Fehler kann aus Brix stammen.**

`ShopRequestMapper.mapHandler()` fängt jede `RuntimeException` des gewrappten
Mappers ab, verwirft die Originalexception und erzeugt eine neue
`AbortWithHttpErrorCodeException(404)`. Der `ShopRequestCycleListener` behandelt
diese anschließend nur als normalen HTTP-Abbruch auf DEBUG-Ebene. Fehler in
Brix-Referenzen, Page-/Tile-Parametern, CDI oder anderer URL-Erzeugungslogik
erscheinen dadurch als gewöhnliche 404 ohne Ursache und Stacktrace.

Besonders kritisch ist der Standardcheckout: Die Bestellung wird erstellt und
der Warenkorb geleert, bevor die URL zur Bestellansicht mit
`Reference.generateUrl()` erzeugt wird. Scheitert diese URL-Erzeugung, sieht der
Kunde nach erfolgreicher Bestellung nur eine 404 und der eigentliche Fehler ist
in den Logs nicht mehr vorhanden.

Betroffene Stellen:

- `app-shop/src/main/java/de/whiskyworld/shop/web/ShopRequestMapper.java`
- `app-shop/src/main/java/de/whiskyworld/shop/web/ShopRequestCycleListener.java`
- `plugin-shop/src/main/java/de/whiskyworld/shop/plugin/tiles/checkout/version1/steps/Step4PruefenBestellen.java`
- `brix-core/src/main/java/org/brixcms/web/reference/Reference.java`

Ziel und Abnahmekriterien:

- Nur konkret erwartete Mapper-Misses in 404 umwandeln.
- Unerwartete Exceptions mit Originalcause, Handler-Typ und Request-Kontext auf
  ERROR loggen beziehungsweise unverändert weiterreichen.
- Nach bereits angelegter Bestellung bei einem Redirectfehler einen stabilen,
  geloggten Fallback zur Bestellansicht anbieten.
- Tests erzwingen eine Exception aus dem gewrappten `mapHandler()` und prüfen,
  dass sie weder verloren geht noch als unauffällige 404 endet.

### P1 – Redirect-Schleifen bei echten Markupfehlern vermeiden

**Zuordnung: app-shop.**

Der `ShopRequestCycleListener` behandelt eine direkte
`MarkupNotFoundException` wie einen veralteten Wicket-Callback und leitet auf
die um Wicket-Informationen bereinigte aktuelle URL um. Ist die URL bereits
kanonisch, ist das Redirect-Ziel identisch mit der aktuellen URL. Ein echter
Markupfehler einer einzelnen Page oder eines Tiles kann dadurch eine endlose
Redirect-Schleife erzeugen, ohne ERROR-Log oder Stacktrace.

Betroffene Stelle:

- `app-shop/src/main/java/de/whiskyworld/shop/web/ShopRequestCycleListener.java`

Ziel und Abnahmekriterien:

- Nur aufräumbare stale Callback-Fehler umleiten.
- Eine Umleitung nur ausführen, wenn sich die bereinigte URL tatsächlich von
  der aktuellen URL unterscheidet.
- Echte `MarkupNotFoundException` mit Request- und Page-Kontext loggen und über
  einen definierten Fehlerhandler beantworten.
- Tests decken Markupfehler auf kanonischen URLs sowie verschachtelte
  Exceptions ab.

### P1, falls noch erreichbar – TLS-Prüfung im Paydirekt-/Giropay-Pfad aktivieren

**Zuordnung: app-shop/plugin-shop.**

`TrustAllHttpCloseableClient` verwendet `TrustAllStrategy` und akzeptiert damit
beliebige Zertifikatsketten. Der Client wird im Paydirekt-Zahlungs- und
Bestellstatuspfad weiterhin referenziert. Ist diese Zahlart noch konfiguriert,
für Altbestellungen erreichbar oder reaktivierbar, besteht ein
Man-in-the-Middle-Risiko für Zahlungsaufrufe.

Betroffene Stellen:

- `plugin-shop/src/main/java/de/whiskyworld/shop/plugin/tiles/bestellansicht/payment/paydirekt/TrustAllHttpCloseableClient.java`
- `plugin-shop/src/main/java/de/whiskyworld/shop/plugin/tiles/bestellansicht/BestellungStatusPanel.java`
- `plugin-shop/src/main/java/de/whiskyworld/shop/plugin/tiles/bestellansicht/payment/paydirekt/PaymentPanelPD.java`

Ziel:

- Zunächst feststellen, ob der Pfad produktiv oder für Altbestellungen noch
  erreichbar ist.
- Bei Erreichbarkeit ausschließlich reguläre Zertifikats- und
  Hostname-Validierung verwenden; andernfalls den toten Zahlungspfad samt
  Konfiguration kontrolliert entfernen.

### P2 – Fehler nach erfolgreicher Bestellerstellung strukturiert loggen

**Zuordnung: app-shop/plugin-shop.**

Der Standardcheckout verwendet nach der Bestellerstellung für Fehler beim
Aktualisieren von Kundenadressen `printStackTrace()` und für Tracking-Snapshot
sowie Neu-/Bestandskundenermittlung leere Catch-Blöcke. Dadurch fehlen je nach
Logging-Setup Bestellnummer, Request-Kontext und Stacktrace vollständig. Der
Express-Checkout loggt vergleichbare Fehler bereits strukturiert.

Betroffene Stelle:

- `plugin-shop/src/main/java/de/whiskyworld/shop/plugin/tiles/checkout/version1/steps/Step4PruefenBestellen.java`

Ziel:

- Leere Catch-Blöcke und `printStackTrace()` durch strukturiertes Logging mit
  Bestellnummer und betroffenem Nachbearbeitungsschritt ersetzen.
- Fachlich bewusste Best-Effort-Schritte weiterhin vom erfolgreichen Anlegen
  der Bestellung entkoppeln, aber sichtbar und messbar machen.

### Erledigt 2026-08-24 – Fehlgeschlagene Markup-Cache-Invalidierung sichtbar machen

**Zuordnung: Brix.**

`MarkupCacheInvalidationListener` loggt unerwartete Invalidierungsfehler jetzt
auf WARN mit Workspace, Node-ID und sicher ermitteltem Pfad. Scheitert die
gezielte Invalidierung, wird der vollständige Cache-Bucket des betroffenen
Workspaces entfernt. Schlägt auch dieser Fallback fehl oder ist der Workspace
nicht sicher ermittelbar, wird dies im WARN-Log ausdrücklich ausgewiesen; eine
Fallbackexception bleibt als unterdrückte Exception am ursprünglichen Fehler
erhalten.

Betroffene Stellen:

- `brix-core/src/main/java/org/brixcms/markup/MarkupCacheInvalidationListener.java`
- `brix-core/src/main/java/org/brixcms/markup/MarkupCache.java`

Umgesetzte Abnahmekriterien:

- Unerwartete Invalidierungsfehler werden auf WARN mit Workspace, Node-ID,
  sicherem Pfad und Stacktrace geloggt.
- Der betroffene Workspace-Cache-Bucket wird als sicherer Fallback entfernt.
- `MarkupCacheTest.failedTargetedInvalidationInvalidatesTheCompleteWorkspaceCache`
  erzwingt den Fehler und weist nach, dass das Production-Markup neu erzeugt
  wird, während der Cache eines anderen Workspaces erhalten bleibt.

### P2 – Client-Abbrüche nicht nur anhand breiter Meldungstexte erkennen

**Zuordnung: Brix und app-shop.**

`ResourceNodeHandler` und `CachingFilter` stufen Exceptions anhand von
Meldungsteilen wie `connection is closed`, `stream was already closed` oder
jedem `h2exception` als harmlosen Client-Abbruch ein. Diese Meldungen sind nicht
client-exklusiv. Ein echter serverseitiger Stream- oder HTTP/2-Fehler kann so
nur auf DEBUG erscheinen und eine unvollständige CSS-, JavaScript-, Bild- oder
sonstige Response hinterlassen.

Betroffene Stellen:

- `brix-core/src/main/java/org/brixcms/plugin/site/resource/ResourceNodeHandler.java`
- `app-shop/src/main/java/de/whiskyworld/filter/CachingFilter.java`

Ziel:

- Soweit vom Container möglich konkrete Abort-Exception-Typen und Zustände
  verwenden.
- Unspezifische Texte wie jedes `h2exception` nicht pauschal unterdrücken.
- Unterdrückte Abbrüche über eine Metrik zählen und unbekannte Fälle mit
  Request-Pfad und Cause mindestens auf WARN sichtbar machen.

### P3 – `IPageRequestHandler`-Vertrag korrekt implementieren

**Zuordnung: Brix.**

`BrixNodeRequestHandler.isPageInstanceCreated()` liefert derzeit `true`, wenn
`page == null` ist. Der Wicket-Vertrag verlangt das Gegenteil. Dadurch erhalten
Wicket-Integrationen wie CDI-Conversation-Propagation für bereits erzeugte
Brix-Pages keinen Page-Kontext und können für noch nicht erzeugte Pages einen
falschen Zustand sehen. Im aktuellen Shop wurden keine `@ConversationScoped`-
Beans gefunden; der Fehler ist dort daher momentan eher latent, im Brix-API
aber eindeutig.

Betroffene Stelle:

- `brix-core/src/main/java/org/brixcms/web/nodepage/BrixNodeRequestHandler.java`

Ziel und Abnahmekriterien:

- `isPageInstanceCreated()` liefert genau dann `true`, wenn `page != null` ist.
- Tests decken sowohl den page-basierten als auch den model-basierten
  Konstruktor und `IPageRequestHandler.getPage()` ab.

### Verifikation und bestehende Testlücken

Zum Audit-Zeitpunkt liefen folgende bestehende Tests erfolgreich:

- `mvn -pl brix-core test`: 104 Tests, keine Fehler.
- `ShopRequestMapperTest` und `ShopRequestCycleListenerTest`: 43 Tests, keine
  Fehler.

Die genannten Grenzfälle sind in diesen Tests nicht abgedeckt. Insbesondere
fehlen Tests für `ViewWorkspaceAction` in app-shop, Exceptions aus
`ShopRequestMapper.mapHandler()`, `MarkupNotFoundException` auf einer bereits
kanonischen URL und den invertierten Page-Handler-Zustand.

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
