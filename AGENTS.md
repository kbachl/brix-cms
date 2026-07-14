# Repository Guidelines

## Project Structure & Module Organization
This is a multi-module Maven build. Core code lives in modules like `brix-core`, `brix-wrapper`, `brix-workspace`, and plugin modules (`brix-plugin-*`). The demo webapp is in `brix-demo`. Typical layout per module:
- `src/main/java` for Java sources and Wicket markup/assets (e.g., `.html`, `.properties`) kept alongside components.
- `src/main/resources` for non-code resources.
- `src/test/java` and `src/test/resources` for tests and fixtures.

## Build, Test, and Development Commands
Java 25 and Maven 3.6.3+ are required (see root `pom.xml`).
- `mvn clean install` builds all modules and runs tests.
- `mvn -pl brix-core test` runs tests for a single module.
- `mvn -pl brix-demo -am package` builds the demo WAR plus dependencies.
- `mvn -pl brix-demo liberty:dev` starts the demo app on Open Liberty for local development.

expected compilation + test time is about 15-30seconds

## Coding Style & Naming Conventions
Use the Eclipse formatter in `etc/eclipse-formatter.xml` (4-space indentation, spaces not tabs). Follow Java conventions: packages are lowercase (`org.brixcms.*`), classes in `PascalCase`, methods/fields in `camelCase`. For Wicket components, keep `Component.java`, `Component.html`, and optional `Component.properties` together and name them identically.

## Testing Guidelines
Tests use JUnit 4; Surefire includes `**/*Test.java`. Place tests in the same package structure as the code under `src/test/java`. No coverage gate is configured, so add tests when touching logic-heavy paths or plugins.

## Runtime Scope & JCR Backends
- The clustered workspace manager is a proof of concept and is not used in production. Exclude its clustering, session, listener, performance, and stability concerns from normal production reviews, and do not change it unless explicitly requested.
- The project intentionally contains JCR integration paths for both Jackrabbit and ModeShape. Retaining, consolidating, or removing either repository adapter is an architectural decision outside routine reviews and cleanup. Do not remove or redesign either adapter without an explicit request and migration plan; changes to shared JCR abstractions must preserve compatibility with both backends.

## Commit & Pull Request Guidelines
Recent commits use short, imperative sentences with context, often including version bumps (e.g., "Fix exception handling during snapshot import and bump Brix version to 10.8.3"). Keep commit messages concise and specific to the module. PRs should include:
- A short summary and affected modules.
- Test commands run and results.
- Screenshots or steps for UI/demo changes (brix-demo).

## Configuration & Security Notes
Demo configuration lives under `brix-demo/src/main/webapp/WEB-INF` and Open Liberty settings under `brix-demo/src/main/liberty/config`. The `brix-demo` module is only for local development and must never be deployed or exposed outside a local environment. Do not commit secrets or real credentials; use sample configs and document any required local setup.
