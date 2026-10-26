package org.hl7.fast.config;

import org.hl7.fast.telemetry.FhirRouteInterceptor;
import org.hl7.fast.telemetry.TelemetryProperties;
import ca.uhn.fhir.jpa.starter.AppProperties;
import ca.uhn.fhir.rest.server.RestfulServer;
import ca.uhn.fhir.rest.server.RestfulServerConfiguration;
import ca.uhn.fhir.rest.server.interceptor.LoggingInterceptor;
import ca.uhn.fhir.rest.server.method.OperationMethodBinding;
import io.opentelemetry.api.GlobalOpenTelemetry;
import jakarta.annotation.PostConstruct;
import java.lang.management.ManagementFactory;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.springframework.stereotype.Component;

/**
 * Registers the route interceptor on the FHIR server and the fhir.server.start_time gauge, which
 * reports the JVM start as seconds since the Unix epoch so a dashboard can show the last start and
 * count starts in a time range. When query strings are not captured, the request log drops
 * ${requestParameters} too, because it prints the same values as url.query. The LoggingInterceptor
 * bean is the instance the server registers, so the format change applies to it.
 */
@Component
public class TelemetryConfig {

	private final RestfulServer restfulServer;
	private final LoggingInterceptor loggingInterceptor;
	private final AppProperties appProperties;
	private final TelemetryProperties telemetryProperties;

	public TelemetryConfig(
			RestfulServer restfulServer,
			LoggingInterceptor loggingInterceptor,
			AppProperties appProperties,
			TelemetryProperties telemetryProperties) {
		this.restfulServer = restfulServer;
		this.loggingInterceptor = loggingInterceptor;
		this.appProperties = appProperties;
		this.telemetryProperties = telemetryProperties;
	}

	@PostConstruct
	public void register() {
		boolean capture = telemetryProperties.isCaptureQueryStrings();
		Set<String> resourceTypes = Set.copyOf(telemetryProperties.getResourceTypes());
		restfulServer.registerInterceptor(new FhirRouteInterceptor(capture, resourceTypes, () -> registeredOperations(restfulServer)));
		GlobalOpenTelemetry.getMeter("org.hl7.fast.telemetry")
				.gaugeBuilder("fhir.server.start_time")
				.ofLongs()
				.setUnit("s")
				.setDescription("Start time of the server JVM, in seconds since the Unix epoch")
				.buildWithCallback(measurement -> measurement.record(ManagementFactory.getRuntimeMXBean().getStartTime() / 1000));
		if (!capture) {
			String format = appProperties.getLogger().getFormat().replace("${requestParameters}", "");
			// HAPI rejects a blank message format, so a token-only format gets a fixed one with no query string.
			loggingInterceptor.setMessageFormat(format.isBlank() ? "${operationType} ${idOrResourceName}" : format);
		}
	}

	/**
	 * The operation names that registered providers declare at server, global, and resource level. The
	 * server configuration is read because RestfulServer exposes global operations, such as $diff, only
	 * through it. A wildcard provider accepts any name a client sends, so its name is left out and those
	 * requests count as "other".
	 */
	static Set<String> registeredOperations(RestfulServer server) {
		RestfulServerConfiguration configuration = server.createConfiguration();
		Stream<?> bindings = Stream.concat(
				Stream.concat(configuration.getServerBindings().stream(), configuration.getGlobalBindings().stream()),
				configuration.getResourceBindings().stream().flatMap(resource -> resource.getMethodBindings().stream()));
		return bindings
				.filter(OperationMethodBinding.class::isInstance)
				.map(binding -> ((OperationMethodBinding) binding).getName())
				.filter(name -> !OperationMethodBinding.WILDCARD_NAME.equals(name))
				.collect(Collectors.toUnmodifiableSet());
	}
}
