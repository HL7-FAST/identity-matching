# AGENTS.md

This file provides guidance to agents when working with code in this repository.

## What this is

FHIR server reference implementation of the HL7 FAST Interoperable Digital Identity and Patient Matching IG. It is a fork of the HAPI FHIR JPA Starter Project (hapi-fhir-jpaserver-starter, HAPI 7.4.5, Java 17, Spring Boot, Maven WAR packaging). All custom code lives in `src/main/java/org/hl7/fast/`; everything under `src/main/java/ca/uhn/fhir/jpa/starter/` is upstream starter code (with small upstream-flavored additions in `AppProperties`, `FhirServerConfigCommon`, and `StarterJpaConfig`). Do not add custom code to the starter package; keep diffs against upstream minimal so merges stay easy.

## Commands

```bash
mvn spring-boot:run              # run locally (Tomcat), server at http://localhost:8080/fhir
mvn -Pjetty spring-boot:run      # run with Jetty instead
docker compose up -d             # run via Docker
mvn clean package -DskipTests && java -jar ./target/ROOT.war   # boot profile jar
mvn test                         # unit tests (surefire)
mvn verify                       # also runs *IT integration tests (failsafe)
mvn test -Dtest=CustomOperationTest   # single test class
```

There is no lint/format plugin configured; match the existing style (tabs, upstream HAPI conventions). `.editorconfig` exists at the repo root.

## Architecture: where the custom code lives

The identity-matching functionality lives in `src/main/java/org/hl7/fast/`:

- `operations/IdentityMatching.java` — the core of this project. Registers the `Patient/$match` and `Patient/$idi-match` operations, validates the incoming Patient parameter against the IG's IDI-Patient / L0 / L1 profiles using FHIRPath expressions, runs the search/scoring against the JPA store, and orchestrates remote-server matching. Supporting models (scorer, match params, identifier registry, validation-level enum) are in `operations/models/`.
- `security/` — UDAP security layer: `IdentityMatchingAuthInterceptor` (JWT auth via the configured UDAP issuer, bypassable with the `X-Allow-Public-Access` header), `DiscoveryInterceptor` (serves `/fhir/.well-known/udap` metadata), `CertInterceptor`/`CertUtil` (loads or fetches the server certificate; `fetch-cert: true` generates `generated-cert.pfx` via the UDAP RI, local testing only), and `models/SecurityConfig` (binds the `security:` YAML block).
- `common/` — `DataInitializer` (startup data seeding), `SecurityUtil`, `FhirContextProvider`.
- `config/` — the bridge to the starter. `IdentityMatchingConfig` registers the operation provider and interceptors on the upstream `RestfulServer` bean and runs the fail-fast cert initialization at context startup. `IdentityMatchingProperties` binds the identity-matching keys under `hapi.fhir:` (the upstream `AppProperties` shares that prefix and ignores them).

Spring finds `org.hl7.fast` through the starter's built-in extension point: `hapi.fhir.custom-bean-packages: org.hl7.fast` in `application.yaml` (component-scanned by `StarterJpaConfig`). `Application.java` is stock upstream. The test profile never sees `custom-bean-packages`, so tests boot a pure stock server; an integration test for the custom code must set that property and `security.fetch-cert: false` (or provide a cert).

Configuration lives in `src/main/resources/application.yaml`, which holds two YAML documents. The first is the upstream starter configuration and should stay identical to upstream. The second, at the end of the file, holds every project-specific setting: the `security:` block (`enable-authentication`, `issuer`, `cert-file`/`cert-password`, `fetch-cert`, `bypass-header`) and, under `hapi.fhir:`, `custom-bean-packages`, `server_address`, `initialdata`, `match-validation-level` (`DEFAULT` | `META_PROFILE` | `NONE`), `match-validation-header` (default `X-Match-Validation`), `remote-match-header` (default `X-Remote-Match`), `remote-servers`, `remote-limit`, and `implementationguides`. That second document is gated on `spring.config.activate.on-profile: "!test"`, so the upstream starter tests (which all activate the `test` profile) never load the custom beans or IGs. Put new project settings in the second document, never in the first.

Requests flow: HTTP POST to `/fhir/Patient/$match` or `$idi-match` → auth interceptor (unless bypassed/disabled) → parameter validation against IDI profiles (400 + OperationOutcome on failure) → local JPA match scoring → optional remote matching when the `X-Remote-Match` header is present.

## Deployment artifacts

- `Dockerfile` is multi-stage (distroless default, `--target tomcat` for a debuggable image) and bundles the OpenTelemetry Java agent.
- `charts/hapi-fhir-jpaserver` — Helm chart; `configs/app` — Foundry configuration.
- `custom/` (repo root) holds the tester UI overrides (welcome/about pages, logo).
