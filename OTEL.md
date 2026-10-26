# OpenTelemetry

This server carries the same OpenTelemetry baseline that the nx-fhir `otel` feature generates, so its metrics line up with the other FAST reference implementations. The OpenTelemetry Java agent records the HTTP server spans, metrics, and logs. The code below gives each request a bounded route and counts it on the `fhir.server.requests` metric, so the signals group by FHIR interaction and not by raw URL. When the agent is not attached, the OpenTelemetry API calls do nothing, and the request log query-string filter stays active.

## What the baseline adds

| File | Purpose |
| --- | --- |
| `src/main/java/org/hl7/fast/telemetry/FhirRouteInterceptor.java` | Sets `http.route` on the server span after HAPI handles a request, and adds one to the `fhir.server.requests` counter. Adds one to the `fhir.server.errors` counter when the response status is 400 or more. Overwrites `url.query` unless query strings are captured. |
| `src/main/java/org/hl7/fast/telemetry/TelemetryProperties.java` | Binds the `telemetry.*` settings in `application.yaml`. |
| `src/main/java/org/hl7/fast/config/TelemetryConfig.java` | Registers the interceptor on the FHIR server with the resource type list and the operation names that registered providers declare, and registers the `fhir.server.start_time` gauge. Removes `${requestParameters}` from the request log format unless query strings are captured. |
| `src/test/java/org/hl7/fast/telemetry/FhirRouteInterceptorTest.java` | Unit test for the route templates and the request metric attributes. |
| `src/test/java/org/hl7/fast/config/TelemetryConfigTest.java` | Unit test for the operation names that `TelemetryConfig` collects. |
| `pom.xml` | Dependencies `io.opentelemetry.instrumentation:opentelemetry-instrumentation-api` and `io.opentelemetry:opentelemetry-api`. The HAPI FHIR parent pom manages their versions. |
| `src/main/resources/application.yaml` | `telemetry.capture-query-strings: false` and `telemetry.resource-types: []` |
| `otel/agent.properties` | Agent settings: the service name and the instrumentations that run. |
| `otel/grafana/dashboards/fast-identity-matching.json` | Baseline Grafana dashboard. |
| `otel/grafana/provisioning/dashboards/fast-identity-matching.yaml` | Grafana provisioning for the dashboard. |
| `docker-compose.otel.yml` | This server and a local Grafana LGTM stack. |

## Route templates

The interceptor sets `http.route` to one of these templates. `{type}` stands for any resource type, and `$name` for any operation the server registers, so the set of routes does not grow with the resource types clients request.

| Route | Requests |
| --- | --- |
| `/fhir/metadata` | Capability statement |
| `/fhir` | Transaction and batch bundles |
| `/fhir/{type}` | Type-level search and create |
| `/fhir/{type}/{id}` | Read, update, patch, and delete |
| `/fhir/{type}/{id}/_history/{version}` | Version read |
| `/fhir/{type}/$name` | Type-level operation |
| `/fhir/{type}/{id}/$name` | Instance-level operation |
| `/fhir/$name` | System-level operation |
| `/fhir/{operation}`, `/fhir/{type}/{operation}`, `/fhir/{type}/{id}/{operation}` | An operation name the server did not register, which only a wildcard provider binds |
| `/fhir/unhandled` | A request HAPI bound to no handler, such as an unknown resource type or a bad path |

HAPI's own tokens `metadata`, `_search`, `_history`, and `$graphql` take the place of `$name`, for example `/fhir/{type}/_search`. Any other operation name that is not registered becomes `{operation}`. Resource types, ids, versions, query strings, and other client text never enter a route. The span keeps the real path in `url.path`. The `/fhir` prefix is a constant that matches the starter's servlet mapping.

## Request metric

The interceptor adds one to the counter `fhir.server.requests` for each request, in the hook that sets the route. HAPI calls that hook for every request, including a request it bound to no handler. In Prometheus the counter is `fhir_server_requests_total`, and each attribute is a label with `_` in place of `.`. When the agent is not attached, the counter does nothing.

| Attribute | Prometheus label | Values | Bound |
| --- | --- | --- | --- |
| `fhir.interaction` | `fhir_interaction` | A value from the interaction table below. | 17 fixed values. |
| `fhir.resource_type` | `fhir_resource_type` | A registered resource type name, or `other`. | The registered types, or the `telemetry.resource-types` list. Absent for system-level and unhandled requests. |
| `fhir.operation` | `fhir_operation` | An operation name such as `$everything`, or `other`. | The operation names that registered providers declare. Absent unless the interaction is `operation`. |

| HAPI `RestOperationTypeEnum` | `fhir.interaction` |
| --- | --- |
| `READ` | `read` |
| `VREAD` | `vread` |
| `SEARCH_TYPE`, `SEARCH_SYSTEM` | `search` |
| `CREATE` | `create` |
| `UPDATE`, `UPDATE_REWRITE_HISTORY` | `update` |
| `PATCH` | `patch` |
| `DELETE` | `delete` |
| `HISTORY_INSTANCE`, `HISTORY_TYPE`, `HISTORY_SYSTEM` | `history` |
| `EXTENDED_OPERATION_SERVER`, `EXTENDED_OPERATION_TYPE`, `EXTENDED_OPERATION_INSTANCE` | `operation` |
| `METADATA` | `capabilities` |
| `TRANSACTION` with a bundle of type `batch`, and `BATCH` | `batch` |
| `TRANSACTION` with any other bundle | `transaction` |
| `VALIDATE` | `validate` |
| `META`, `META_ADD`, `META_DELETE`, `ADD_TAGS`, `DELETE_TAGS`, `GET_TAGS` | `meta` |
| `GRAPHQL_REQUEST` | `graphql` |
| None, because HAPI bound no handler | `unhandled` |
| None, because an interceptor answered the request itself through `tagRoute(request, route)`, as the UDAP discovery and certificate endpoints do | `other` |
| Any other constant | `other` |

A page fetch counts as a `search` on the resource type of the page's first entry, read from the response, and an empty page has no resource type.

The server serves every registered resource type. `telemetry.resource-types` in `application.yaml` only limits which types get their own value on the request metric, and every other registered type counts as `other`. When the list is empty, every registered type is recorded as is. The registered types are every type of the FHIR version when `hapi.fhir.supported_resource_types` is empty, else that list plus `SearchParameter`. The list applies to the metric only. The span route never names a resource type, and the real path stays in `url.path`.

```yaml
telemetry:
  resource-types:
    - Patient
    - Observation
```

An operation name is recorded only when a registered provider declares it. `TelemetryConfig` collects the declared names from the server bindings, the global bindings, and the method bindings of each resource provider. The global bindings hold type-less operations such as the starter's `$diff` and `$reindex`. A provider that declares `@Operation(name = "$*")` accepts any name a client sends, so `$*` is left out and a name outside the set counts as `other`. The interceptor reads the set once, on the first request, so an operation that a provider registers after that counts as `other`.

The agent's SDK limits each instrument to `otel.java.metrics.cardinality.limit` attribute sets, 2,000 by default, including the overflow series. When an instrument reaches the limit, the SDK records the requests of every new combination on the overflow series, which has the label `otel_metric_overflow="true"` and no other attributes. A query that filters on `fhir_interaction!=""` leaves the overflow series out. On a public server, set `telemetry.resource-types` so that the metric stays well below the limit.

### Error counter

The interceptor also adds one to the counter `fhir.server.errors` for each request that ends with a response status of 400 or more. It does this in the same hook, after it adds to the request counter. In Prometheus the counter is `fhir_server_errors_total`. It has no resource type, operation, or status code attribute.

| Attribute | Prometheus label | Values |
| --- | --- | --- |
| `fhir.interaction` | `fhir_interaction` | The value that the request counter records for the same request. |
| `fhir.outcome` | `fhir_outcome` | `client_error` for a status from 400 to 499, and `server_error` for a status of 500 or more. |

### Start time gauge

`TelemetryConfig` registers the gauge `fhir.server.start_time`, which reports the start time of the JVM as seconds since the Unix epoch, from `RuntimeMXBean.getStartTime()`. In Prometheus the gauge is `fhir_server_start_time_seconds`. It has no attributes, so it is one series for each running JVM. The agent exports no other metric with the start time, and each JVM start gets a new `service.instance.id`, so the dashboard reads the newest value as the last start and counts the series whose value falls inside the selected range as starts.

A request with a status below 400 does not change the counter. The status codes stay on the server spans and in the request log.

## Query strings

Search query strings can carry patient identifiers, so by default they stay out of telemetry and the request log:

- The interceptor overwrites the span attribute `url.query` with `[redacted]`.
- `TelemetryConfig` removes `${requestParameters}` from the request log format, `hapi.fhir.logger.format`.

To capture them, for example on a server that holds only synthetic data, set `telemetry.capture-query-strings: true` in `application.yaml` or set the environment variable `TELEMETRY_CAPTURE_QUERY_STRINGS=true`. Then restart the server.

## Agent settings

The agent reads a setting from a system property first, then from an environment variable, then from the file that `OTEL_JAVAAGENT_CONFIGURATION_FILE` names. A variable you set wins over the file.
`otel/agent.properties` holds the first eight settings below.

| Variable | Value | Purpose |
| --- | --- | --- |
| `OTEL_SERVICE_NAME` | `fast-identity-matching` | The `service.name` resource attribute. |
| `OTEL_INSTRUMENTATION_COMMON_DEFAULT_ENABLED` | `false` | Turns off every instrumentation that this table does not turn on. |
| `OTEL_INSTRUMENTATION_TOMCAT_ENABLED` | `true` | The HTTP server span and the `http.server.request.duration` histogram. The agent emits the histogram, and no dashboard panel reads it. |
| `OTEL_INSTRUMENTATION_SERVLET_ENABLED` | `true` | Servlet support for the server span. |
| `OTEL_INSTRUMENTATION_OPENTELEMETRY_API_ENABLED` | `true` | Connects the interceptor's `Span.current()` calls to the agent. |
| `OTEL_INSTRUMENTATION_OPENTELEMETRY_INSTRUMENTATION_API_ENABLED` | `true` | Connects the interceptor's `HttpServerRoute.update` call to the agent. Without it, every route stays at the servlet mapping `/fhir/*`. |
| `OTEL_INSTRUMENTATION_LOGBACK_APPENDER_ENABLED` | `true` | Exports log records. |
| `OTEL_INSTRUMENTATION_RUNTIME_TELEMETRY_ENABLED` | `true` | The `jvm.*` metrics: memory, CPU, garbage collection, threads, and classes. |
| `OTEL_EXPORTER_OTLP_ENDPOINT` | Agent default `http://localhost:4318` | Where the agent sends OTLP over HTTP. |
| `OTEL_JAVAAGENT_CONFIGURATION_FILE` | `otel/agent.properties` | The properties file the agent reads. |
| `JAVA_TOOL_OPTIONS` | `-javaagent:/app/opentelemetry-javaagent.jar` | Attaches the agent in the container image. |

## Grafana dashboard

`otel/grafana/dashboards/fast-identity-matching.json` is a Grafana v2 dashboard with a Summary row, a Traffic row, and a Runtime row from the baseline, then the Auth and Match rows specific to this server, which read the access decision and match log lines from Loki. The Summary row has four stats for the selected range. The Traffic panels show which resource types, interactions, and operations the server handles, and how often. The Runtime panels show the CPU and memory use of the JVM. The Prometheus queries read the counters `fhir_server_requests_total` and `fhir_server_errors_total`, the gauges `fhir_server_start_time_seconds`, `jvm_cpu_recent_utilization_ratio`, and `jvm_memory_used_bytes`, and the metric `target_info`. Each counter query filters on `fhir_interaction!=""`, so the overflow series never joins a sum. `otel/grafana/provisioning/dashboards/fast-identity-matching.yaml` provisions it into a Grafana folder named `fast-identity-matching`.

| Panel | Shows |
| --- | --- |
| Resource interactions, selected range | The requests in the selected range with an interaction other than an operation. Requests that HAPI bound to no handler are left out. |
| Operation invocations, selected range | The requests in the selected range that invoked an operation. |
| Last server start | The start time of the newest JVM, from `fhir_server_start_time_seconds`. |
| Server starts, selected range | The JVMs whose start time is inside the selected range. A server that was running when the range begins adds nothing, so for one server this is its restart count unless its first start is inside the range. The value is orange from 1 and red from 3. |
| Resource interactions | The ten resource types with the most requests in the selected range. |
| Operations | The ten operations with the most requests in the selected range. |
| Requests per minute by interaction | Requests per minute for each interaction, averaged over the 5 minutes before each point. |
| Error share by interaction | The share of requests in the 5 minutes before each point that ended with a 4xx or 5xx status, for each interaction. An interaction with no error since the server started has no line. |
| Top requests (requests in range) | The requests in the selected range for each resource type, interaction, and operation. |
| Average requests per minute, selected range | The requests in the selected range divided by its length in minutes. |
| Average processing time by route (traces) | The mean duration of the server spans for each route, from Tempo. |
| Unhandled request paths (traces) | The ten most frequent paths of requests that HAPI bound to no handler, from Tempo. |
| Minutes since last metric sample | The minutes since the server last exported metrics, from `target_info`. |
| CPU usage | The share of the CPU capacity of the machine that the JVM process used, from `jvm.cpu.recent_utilization`. |
| Memory usage | The bytes the JVM uses for heap and for non-heap memory, from `jvm.memory.used` summed over the memory pools, and the heap in use as a share of the heap limit from `jvm.memory.limit`, with threshold lines at 80% and 90%. |

In "Top requests (requests in range)", the Resource cell is empty for a system-level request, and the Operation cell is empty for a request that is not an operation.

Every counter panel counts requests as the change in the cumulative counter over its window, with a zero baseline for a series that starts inside the window, so the first requests of a series and the requests after a restart are counted. The agent exports every 60 s, so a window shorter than 2m has too few samples and the panel step is 1m. After an export outage longer than 5 minutes, a series has no baseline sample at the start of the first window after the outage, so that window counts the whole total of the series as new requests.

The request metric records a request that HAPI bound to no handler as `fhir_interaction="unhandled"` with no resource type, and the span route is `/fhir/unhandled`. "Unhandled request paths (traces)" shows the ten most frequent paths behind that route over the selected range. It reads Tempo: the server span keeps the real path in `url.path` next to the route. The query is a TraceQL metrics query, so Tempo needs the metrics-generator with the local-blocks processor, which `grafana/otel-lgtm` and Grafana Cloud have on. The panel shows client text and resource ids from the path, which the metrics never carry.

The heap limit in "Memory usage" is the maximum heap the JVM reports, which the starter image does not set, so the JVM derives it from the memory the container may use: 25% of the container limit by default, or the share that `-XX:MaxRAMPercentage` names. The JVM cannot see the container limit itself, and the container also holds the non-heap pools, thread stacks, and native memory, so a container memory alert needs the metrics of the container platform.

"Average processing time by route (traces)" reads Tempo in the same way and averages the server span duration for each `http.route`, so the dashboard keeps a latency view with no metric series stored for it. The agent's `http.server.request.duration` histogram is not needed for this panel, and a stack that drops the histogram keeps the panel.

Edits in the Grafana UI are allowed. A change to the dashboard file overwrites them when Grafana loads the file again. To keep a UI edit, export the dashboard JSON from Grafana and save it over the file.

## Series budget

The request metric has one series for each combination of label values that a running JVM has served. Each JVM start sets a new `service.instance.id`, so after a restart the series of the old and the new JVM both count until the old ones pass the backend's idle timeout. The `fhir_server_start_time_seconds` gauge is one series for each running JVM. An exploratory client in the table below is one that sends every interaction to every resource type.

| Shape | Series per server |
| --- | --- |
| Typical: 20 resource types with 4 interactions each, 3 operations, and 3 system rows (capabilities, transaction, unhandled) | 86 |
| Exploratory client, `telemetry.resource-types` with 10 types: 11 resource values (the 10 types and `other`) with 16 interactions each, the `other` operation, and the system rows | About 200 |
| Exploratory client, no `telemetry.resource-types` list, R4: 146 resource types with 16 interactions each, plus operations | About 2,000, capped by the SDK at 2,000 including the overflow series |
| Error counter: 17 interactions × 2 outcomes, only combinations that occurred | At most 34 |

The server also exports three `io_hapifhir_validation_support_chain_*` gauges for each running instance through the agent's API bridge, and the table above does not count them.

The agent's `runtime-telemetry` instrumentation exports the `jvm_*` metrics: 44 series measured on a HAPI server one minute after start on JDK 25 with the G1 collector (6 memory pools, 6 thread states, 2 garbage collector combinations), and about 55 at most, because the first major garbage collection adds 7. The count is fixed for each running JVM and does not grow with traffic. The dashboard reads `jvm_cpu_recent_utilization_ratio`, `jvm_memory_used_bytes` (one series per memory pool), and `jvm_memory_limit_bytes`. The `jvm_gc_duration_seconds_*` histogram, `jvm_memory_committed_bytes`, `jvm_memory_used_after_last_gc_bytes`, `jvm_thread_count`, `jvm_class_*`, `jvm_cpu_time_seconds_total`, and `jvm_cpu_count` are exported and unread, so a backend that counts series can drop them in the same ways as the histogram below.

The agent's `http.server.request.duration` histogram adds 17 series (15 buckets, sum, and count) for each route, method, and status code combination. The routes name request shapes and not resource types, so the histogram grows with the methods and status codes the server sees and not with the resource types clients request: about 200 series for search and read traffic, 400 to 700 for mixed traffic with create, update, delete, and a few error statuses, and up to about 2,500 for a client that cycles every method and status code on every route shape. To keep the histogram out of a billed backend, drop it on the way:

- In an OpenTelemetry Collector between the server and the backend, add a `filter` processor that drops the metric `http.server.request.duration`.
- On Grafana Cloud, add an Adaptive Metrics drop rule for `http_server_request_duration_seconds_bucket`, `http_server_request_duration_seconds_sum`, and `http_server_request_duration_seconds_count`.
- In the local stack, uncomment the `OTELCOL_EXTRA_ARGS` and `otelcol-filter.yaml` lines in `docker-compose.otel.yml` and recreate the `otel-lgtm` container. `otel/otelcol-filter.yaml` holds the filter.

Without one of these, the backend stores both the histogram and the counter.

## Local stack with Docker Compose

```sh
docker compose -f docker-compose.otel.yml up --build
```

The compose file builds this server with the OpenTelemetry Java agent and runs it next to `grafana/otel-lgtm`, which holds Grafana, Prometheus, Loki, Tempo, and an OpenTelemetry collector. The server listens on http://localhost:8080/fhir and Grafana on http://localhost:3000. The `hapi-fhir-jpaserver-start` service sends its signals to the `lgtm` service.

The build argument `OPENTELEMETRY_JAVA_AGENT_VERSION` pins the agent to 2.32.0. Keep the argument: the starter Dockerfile's default agent can be older than the instrumentation API this server compiles against, and with an older agent every route stays at the servlet mapping `/fhir/*`.

If another stack holds port 3000, 4317, or 4318, set `LGTM_GRAFANA_PORT`, `LGTM_OTLP_GRPC_PORT`, or `LGTM_OTLP_HTTP_PORT` to a free host port.
