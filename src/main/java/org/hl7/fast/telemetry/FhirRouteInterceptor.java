package org.hl7.fast.telemetry;

import ca.uhn.fhir.interceptor.api.Hook;
import ca.uhn.fhir.interceptor.api.Interceptor;
import ca.uhn.fhir.interceptor.api.Pointcut;
import ca.uhn.fhir.rest.api.server.RequestDetails;
import ca.uhn.fhir.rest.server.servlet.ServletRequestDetails;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.semconv.http.HttpServerRoute;
import io.opentelemetry.instrumentation.api.semconv.http.HttpServerRouteSource;
import org.hl7.fhir.instance.model.api.IIdType;

/**
 * Gives HAPI traffic a bounded http.route path template, such as /fhir/Patient/{id}, which the
 * OpenTelemetry Java agent also uses for the span name. The real URL stays in url.path.
 * The RestfulServer is a plain servlet, so without this the agent only knows "/fhir/*".
 * HAPI fires SERVER_PROCESSING_COMPLETED in a finally block, so every request is tagged.
 * Unless query strings are captured, the span's url.query is overwritten too.
 * Every call is a no-op when the agent is not attached.
 */
@Interceptor
public class FhirRouteInterceptor {

	static final String BASE = "/fhir";
	static final String UNHANDLED = BASE + "/unhandled";
	static final String REDACTED = "[redacted]";

	private final boolean captureQueryStrings;

	public FhirRouteInterceptor(boolean captureQueryStrings) {
		this.captureQueryStrings = captureQueryStrings;
	}

	@Hook(Pointcut.SERVER_PROCESSING_COMPLETED)
	public void finishServerSpan(RequestDetails details) {
		tagRoute(buildRoute(details));
		if (!captureQueryStrings && hasQueryString(details)) {
			Span.current().setAttribute("url.query", REDACTED);
		}
	}

	/** The agent sets url.query only when the request had one, so the overwrite follows the same rule. */
	private static boolean hasQueryString(RequestDetails details) {
		return details instanceof ServletRequestDetails servlet
				&& servlet.getServletRequest().getQueryString() != null;
	}

	/** For interceptors that answer a request themselves. CONTROLLER is first-wins, so their route survives. */
	public static void tagRoute(String route) {
		HttpServerRoute.update(Context.current(), HttpServerRouteSource.CONTROLLER, route);
	}

	/**
	 * A null operation type means HAPI bound no handler: an unknown resource type, a bad path, or an
	 * interceptor that answered before binding. The parsed path parts are then raw URL text and must not
	 * become a route. With a handler, resource type and operation are limited to what the server
	 * registers. Compartment and secondary operation are left out because update, delete and patch bind
	 * without checking them.
	 */
	static String buildRoute(RequestDetails details) {
		if (details.getRestOperationType() == null) {
			return UNHANDLED;
		}
		StringBuilder route = new StringBuilder(BASE);
		if (details.getResourceName() != null) {
			route.append('/').append(details.getResourceName());
		}
		IIdType id = details.getId();
		if (id != null && id.hasIdPart()) {
			route.append("/{id}");
			if (id.hasVersionIdPart()) {
				route.append("/_history/{version}");
			}
		}
		if (details.getOperation() != null) {
			route.append('/').append(details.getOperation());
		}
		return route.toString();
	}
}
