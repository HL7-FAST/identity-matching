package org.hl7.fast.telemetry;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.interceptor.api.Hook;
import ca.uhn.fhir.interceptor.api.Interceptor;
import ca.uhn.fhir.interceptor.api.Pointcut;
import ca.uhn.fhir.rest.api.Constants;
import ca.uhn.fhir.rest.api.RestOperationTypeEnum;
import ca.uhn.fhir.rest.api.server.RequestDetails;
import ca.uhn.fhir.rest.server.servlet.ServletRequestDetails;
import ca.uhn.fhir.util.BundleUtil;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.api.metrics.LongCounter;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.semconv.http.HttpServerRoute;
import io.opentelemetry.instrumentation.api.semconv.http.HttpServerRouteSource;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import org.hl7.fhir.instance.model.api.IBaseBundle;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.instance.model.api.IIdType;

/**
 * Sets a bounded http.route, such as /fhir/{type}/{id}, on the agent's server span, which also
 * names the span. On its own the agent sees only the servlet mapping /fhir/*. The real URL stays
 * in url.path. Counts every request on fhir.server.requests with interaction, resource type, and
 * operation values the server defines, so a client cannot create metric series, and every response
 * status of 400 or more on fhir.server.errors. SERVER_PROCESSING_COMPLETED fires in a finally
 * block, so no request is missed. Without the agent, every call is a no-op.
 */
@Interceptor
public class FhirRouteInterceptor {

	static final String BASE = "/fhir";
	static final String UNHANDLED = BASE + "/unhandled";
	static final String REDACTED = "[redacted]";
	static final String OTHER = "other";
	static final String PAGE_RESOURCE_TYPE = FhirRouteInterceptor.class.getName() + ".pageResourceType";
	static final String SELF_ANSWERED = FhirRouteInterceptor.class.getName() + ".selfAnswered";
	/** Path tokens HAPI binds itself. Any other operation name must come from a registered provider. */
	static final Set<String> HAPI_TOKENS = Set.of(
			Constants.URL_TOKEN_METADATA, Constants.PARAM_SEARCH, Constants.PARAM_HISTORY, Constants.OPERATION_NAME_GRAPHQL);

	static final AttributeKey<String> INTERACTION = AttributeKey.stringKey("fhir.interaction");
	static final AttributeKey<String> RESOURCE_TYPE = AttributeKey.stringKey("fhir.resource_type");
	static final AttributeKey<String> OPERATION = AttributeKey.stringKey("fhir.operation");
	static final AttributeKey<String> OUTCOME = AttributeKey.stringKey("fhir.outcome");

	private final boolean captureQueryStrings;
	private final Set<String> resourceTypes;
	private final Supplier<Set<String>> registeredOperations;
	private final AtomicReference<Set<String>> knownOperations = new AtomicReference<>();
	private final LongCounter requests;
	private final LongCounter errors;

	public FhirRouteInterceptor(
			boolean captureQueryStrings, Set<String> resourceTypes, Supplier<Set<String>> registeredOperations) {
		this(
				captureQueryStrings,
				resourceTypes,
				registeredOperations,
				counter("fhir.server.requests",
						"FHIR requests handled by the server, by resource type, interaction, and operation"),
				counter("fhir.server.errors", "FHIR requests that ended in an error, by interaction and outcome class"));
	}

	/** Tests pass recording counters here. */
	FhirRouteInterceptor(
			boolean captureQueryStrings,
			Set<String> resourceTypes,
			Supplier<Set<String>> registeredOperations,
			LongCounter requests,
			LongCounter errors) {
		this.captureQueryStrings = captureQueryStrings;
		this.resourceTypes = resourceTypes;
		this.registeredOperations = registeredOperations;
		this.requests = requests;
		this.errors = errors;
	}

	private static LongCounter counter(String name, String description) {
		return GlobalOpenTelemetry.getMeter("org.hl7.fast.telemetry")
				.counterBuilder(name)
				.setUnit("{request}")
				.setDescription(description)
				.build();
	}

	@Hook(Pointcut.SERVER_PROCESSING_COMPLETED)
	public void finishServerSpan(RequestDetails details) {
		tagRoute(buildRoute(details, operations()));
		if (!captureQueryStrings && hasQueryString(details)) {
			Span.current().setAttribute("url.query", REDACTED);
		}
		Attributes request = attributesFor(details, resourceTypes, operations());
		requests.add(1, request);
		String outcome = details instanceof ServletRequestDetails servlet
				? outcomeFor(servlet.getServletResponse().getStatus())
				: null;
		if (outcome != null) {
			errors.add(1, Attributes.of(INTERACTION, request.get(INTERACTION), OUTCOME, outcome));
		}
	}

	/** The status is final by SERVER_PROCESSING_COMPLETED. An unset status reads 200, so only 400 and above count. */
	static String outcomeFor(int status) {
		if (status >= 500) {
			return "server_error";
		}
		return status >= 400 ? "client_error" : null;
	}

	/**
	 * A page request names no resource type, so it is read from the response. Search matches come before
	 * _include entries, so the first entry is the searched type unless an interceptor, such as HAPI's
	 * consent interceptor, removed or replaced entries. The type of whatever is first is bounded either
	 * way, because it comes from the resource class. An empty page stores nothing.
	 */
	@Hook(Pointcut.SERVER_OUTGOING_RESPONSE)
	public void rememberPageResourceType(RequestDetails details, IBaseResource response) {
		FhirContext context = details.getFhirContext();
		if (details.getRestOperationType() == RestOperationTypeEnum.GET_PAGE
				&& context != null
				&& response instanceof IBaseBundle bundle) {
			BundleUtil.toListOfResources(context, bundle).stream()
					.findFirst()
					.ifPresent(first -> details.getUserData().put(PAGE_RESOURCE_TYPE, context.getResourceType(first)));
		}
	}

	/** Read once, on the first request. A provider registered later counts as "other" and routes as {operation}. */
	private Set<String> operations() {
		return knownOperations.updateAndGet(known -> known != null ? known : registeredOperations.get());
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
	 * For interceptors that answer a request before HAPI binds a handler, such as the UDAP discovery and
	 * certificate endpoints. HAPI still completes the request with no operation type, so the mark on the
	 * servlet request keeps it out of the unhandled count.
	 */
	public static void tagRoute(HttpServletRequest request, String route) {
		request.setAttribute(SELF_ANSWERED, route);
		tagRoute(route);
	}

	private static boolean isSelfAnswered(RequestDetails details) {
		return details instanceof ServletRequestDetails servlet
				&& servlet.getServletRequest().getAttribute(SELF_ANSWERED) != null;
	}

	/**
	 * A null operation type means HAPI bound no handler, so the parsed path parts are raw client text and
	 * must not become a route. The resource name becomes {type} so the route set does not grow with the
	 * types clients request. An operation name only a wildcard provider binds becomes {operation}. HAPI's
	 * own tokens stay as they are. Compartment and secondary operation are left out because update,
	 * delete, and patch bind without checking them. Only a vread adds the version segment, because an
	 * update copies an If-Match version into its id.
	 */
	static String buildRoute(RequestDetails details, Set<String> registeredOperations) {
		if (details.getRestOperationType() == null) {
			return UNHANDLED;
		}
		StringBuilder route = new StringBuilder(BASE);
		if (details.getResourceName() != null) {
			route.append("/{type}");
		}
		IIdType id = details.getId();
		if (id != null && id.hasIdPart()) {
			route.append("/{id}");
			if (details.getRestOperationType() == RestOperationTypeEnum.VREAD && id.hasVersionIdPart()) {
				route.append("/_history/{version}");
			}
		}
		String operation = details.getOperation();
		if (operation != null) {
			boolean bounded = registeredOperations.contains(operation) || HAPI_TOKENS.contains(operation);
			route.append('/').append(bounded ? operation : "{operation}");
		}
		return route.toString();
	}

	/**
	 * An unhandled request gets no resource type, because its path parts are raw client text. A page takes
	 * the type rememberPageResourceType stored, if any. With a non-empty allowlist, every other type is
	 * "other". An operation name is kept only when a registered provider declares it, because a wildcard
	 * provider accepts any name.
	 */
	static Attributes attributesFor(RequestDetails details, Set<String> resourceTypes, Set<String> registeredOperations) {
		String interaction = interactionFor(details);
		AttributesBuilder attributes = Attributes.builder().put(INTERACTION, interaction);
		String resourceType = details.getRestOperationType() == RestOperationTypeEnum.GET_PAGE
				? (String) details.getUserData().get(PAGE_RESOURCE_TYPE)
				: details.getResourceName();
		if (!"unhandled".equals(interaction) && resourceType != null) {
			boolean listed = resourceTypes.isEmpty() || resourceTypes.contains(resourceType);
			attributes.put(RESOURCE_TYPE, listed ? resourceType : OTHER);
		}
		if ("operation".equals(interaction)) {
			// Immutable sets throw on contains(null).
			String operation = details.getOperation();
			boolean registered = operation != null && registeredOperations.contains(operation);
			attributes.put(OPERATION, registered ? operation : OTHER);
		}
		return attributes.build();
	}

	/**
	 * Batch and transaction bundles share the TRANSACTION handler, so the bundle type tells them apart.
	 * A constant a later HAPI release adds is "other", and so is a request an interceptor answered itself.
	 */
	static String interactionFor(RequestDetails details) {
		RestOperationTypeEnum type = details.getRestOperationType();
		if (type == null) {
			return isSelfAnswered(details) ? OTHER : "unhandled";
		}
		return switch (type) {
			case READ -> "read";
			case VREAD -> "vread";
			case SEARCH_TYPE, SEARCH_SYSTEM, GET_PAGE -> "search";
			case CREATE -> "create";
			case UPDATE, UPDATE_REWRITE_HISTORY -> "update";
			case PATCH -> "patch";
			case DELETE -> "delete";
			case HISTORY_INSTANCE, HISTORY_TYPE, HISTORY_SYSTEM -> "history";
			case EXTENDED_OPERATION_SERVER, EXTENDED_OPERATION_TYPE, EXTENDED_OPERATION_INSTANCE -> "operation";
			case METADATA -> "capabilities";
			case TRANSACTION -> isBatchBundle(details) ? "batch" : "transaction";
			case BATCH -> "batch";
			case VALIDATE -> "validate";
			case META, META_ADD, META_DELETE, ADD_TAGS, DELETE_TAGS, GET_TAGS -> "meta";
			case GRAPHQL_REQUEST -> "graphql";
			default -> OTHER;
		};
	}

	private static boolean isBatchBundle(RequestDetails details) {
		return details.getFhirContext() != null
				&& details.getResource() instanceof IBaseBundle bundle
				&& "batch".equals(BundleUtil.getBundleType(details.getFhirContext(), bundle));
	}
}
