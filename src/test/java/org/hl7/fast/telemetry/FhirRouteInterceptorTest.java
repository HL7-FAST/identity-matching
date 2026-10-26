package org.hl7.fast.telemetry;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.rest.api.RestOperationTypeEnum;
import ca.uhn.fhir.rest.api.server.RequestDetails;
import ca.uhn.fhir.rest.api.server.SystemRequestDetails;
import ca.uhn.fhir.rest.server.servlet.ServletRequestDetails;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.LongCounter;
import io.opentelemetry.context.Context;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.IdType;
import org.hl7.fhir.r4.model.Organization;
import org.hl7.fhir.r4.model.Patient;
import org.hl7.fhir.r4.model.Resource;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class FhirRouteInterceptorTest {

	@Test
	void route() {
		assertEquals("/fhir/unhandled", route(null, "Patient", null, null));
		assertEquals("/fhir/metadata", route(RestOperationTypeEnum.METADATA, null, null, "metadata"));
		assertEquals("/fhir", route(RestOperationTypeEnum.TRANSACTION, null, null, null));
		assertEquals("/fhir/{type}", route(RestOperationTypeEnum.SEARCH_TYPE, "Patient", null, null));
		assertEquals("/fhir/{type}/_search", route(RestOperationTypeEnum.SEARCH_TYPE, "Patient", null, "_search"));
		assertEquals("/fhir/{type}/{id}", route(RestOperationTypeEnum.READ, "Patient", "Patient/123", null));
		assertEquals("/fhir/{type}/{id}/_history/{version}",
				route(RestOperationTypeEnum.VREAD, "Patient", "Patient/123/_history/2", null));
		assertEquals("/fhir/{type}/{id}",
				route(RestOperationTypeEnum.UPDATE, "Patient", "Patient/123/_history/2", null));
		assertEquals("/fhir/{type}/{id}/_history",
				route(RestOperationTypeEnum.HISTORY_INSTANCE, "Patient", "Patient/123", "_history"));
		assertEquals("/fhir/{type}/$match",
				route(RestOperationTypeEnum.EXTENDED_OPERATION_TYPE, "Patient", null, "$match"));
		assertEquals("/fhir/{type}/{id}/$everything",
				route(RestOperationTypeEnum.EXTENDED_OPERATION_INSTANCE, "Patient", "Patient/123", "$everything"));
		assertEquals("/fhir/$export", route(RestOperationTypeEnum.EXTENDED_OPERATION_SERVER, null, null, "$export"));
		assertEquals("/fhir/{type}/{id}/$graphql",
				route(RestOperationTypeEnum.GRAPHQL_REQUEST, "Patient", "Patient/123", "$graphql"));
	}

	@Test
	void routeHidesOperationNamesTheServerDidNotRegister() {
		RequestDetails wildcard = details(RestOperationTypeEnum.EXTENDED_OPERATION_TYPE, "Patient", null, "$client-text");
		assertEquals("/fhir/{type}/{operation}", FhirRouteInterceptor.buildRoute(wildcard, Set.of("$match")));
		RequestDetails server = details(RestOperationTypeEnum.EXTENDED_OPERATION_SERVER, null, null, "$client-text");
		assertEquals("/fhir/{operation}", FhirRouteInterceptor.buildRoute(server, Set.of()));
		RequestDetails underscore = details(RestOperationTypeEnum.EXTENDED_OPERATION_SERVER, null, null, "_client-text");
		assertEquals("/fhir/{operation}", FhirRouteInterceptor.buildRoute(underscore, Set.of()));
		RequestDetails trailing = details(RestOperationTypeEnum.EXTENDED_OPERATION_INSTANCE, "Patient", "Patient/123", "client-text");
		assertEquals("/fhir/{type}/{id}/{operation}", FhirRouteInterceptor.buildRoute(trailing, Set.of()));
	}

	@Test
	void routeIgnoresUncheckedPathSegments() {
		RequestDetails delete = details(RestOperationTypeEnum.DELETE, "Patient", "Patient/123", null);
		delete.setCompartmentName("garbage");
		delete.setSecondaryOperation("garbage");
		assertEquals("/fhir/{type}/{id}", FhirRouteInterceptor.buildRoute(delete, Set.of()));
	}

	@Test
	void interactionMapping() {
		assertEquals("read", interaction(RestOperationTypeEnum.READ));
		assertEquals("vread", interaction(RestOperationTypeEnum.VREAD));
		assertEquals("search", interaction(RestOperationTypeEnum.SEARCH_TYPE));
		assertEquals("search", interaction(RestOperationTypeEnum.SEARCH_SYSTEM));
		assertEquals("search", interaction(RestOperationTypeEnum.GET_PAGE));
		assertEquals("create", interaction(RestOperationTypeEnum.CREATE));
		assertEquals("update", interaction(RestOperationTypeEnum.UPDATE));
		assertEquals("update", interaction(RestOperationTypeEnum.UPDATE_REWRITE_HISTORY));
		assertEquals("patch", interaction(RestOperationTypeEnum.PATCH));
		assertEquals("delete", interaction(RestOperationTypeEnum.DELETE));
		assertEquals("history", interaction(RestOperationTypeEnum.HISTORY_INSTANCE));
		assertEquals("history", interaction(RestOperationTypeEnum.HISTORY_TYPE));
		assertEquals("history", interaction(RestOperationTypeEnum.HISTORY_SYSTEM));
		assertEquals("operation", interaction(RestOperationTypeEnum.EXTENDED_OPERATION_SERVER));
		assertEquals("operation", interaction(RestOperationTypeEnum.EXTENDED_OPERATION_TYPE));
		assertEquals("operation", interaction(RestOperationTypeEnum.EXTENDED_OPERATION_INSTANCE));
		assertEquals("capabilities", interaction(RestOperationTypeEnum.METADATA));
		assertEquals("transaction", interaction(RestOperationTypeEnum.TRANSACTION));
		assertEquals("batch", interaction(RestOperationTypeEnum.BATCH));
		assertEquals("validate", interaction(RestOperationTypeEnum.VALIDATE));
		assertEquals("meta", interaction(RestOperationTypeEnum.META));
		assertEquals("meta", interaction(RestOperationTypeEnum.META_ADD));
		assertEquals("meta", interaction(RestOperationTypeEnum.META_DELETE));
		assertEquals("meta", interaction(RestOperationTypeEnum.ADD_TAGS));
		assertEquals("meta", interaction(RestOperationTypeEnum.DELETE_TAGS));
		assertEquals("meta", interaction(RestOperationTypeEnum.GET_TAGS));
		assertEquals("graphql", interaction(RestOperationTypeEnum.GRAPHQL_REQUEST));
		assertEquals("unhandled", interaction(null));
	}

	@Test
	void batchBundleIsBatch() {
		RequestDetails details = details(RestOperationTypeEnum.TRANSACTION, null, null, null);
		((SystemRequestDetails) details).setFhirContext(FhirContext.forR4Cached());
		details.setResource(new Bundle().setType(Bundle.BundleType.BATCH));
		assertEquals("batch", FhirRouteInterceptor.interactionFor(details));
		details.setResource(new Bundle().setType(Bundle.BundleType.TRANSACTION));
		assertEquals("transaction", FhirRouteInterceptor.interactionFor(details));
	}

	@Test
	void transactionWithoutBundleIsTransaction() {
		assertEquals("transaction", interaction(RestOperationTypeEnum.TRANSACTION));
	}

	@Test
	void resourceTypeFollowsAllowlist() {
		RequestDetails read = details(RestOperationTypeEnum.READ, "Patient", "Patient/1", null);
		assertEquals("Patient", attributes(read, Set.of(), Set.of()).get(FhirRouteInterceptor.RESOURCE_TYPE));
		assertEquals("Patient", attributes(read, Set.of("Patient"), Set.of()).get(FhirRouteInterceptor.RESOURCE_TYPE));
		assertEquals("other", attributes(read, Set.of("Observation"), Set.of()).get(FhirRouteInterceptor.RESOURCE_TYPE));
	}

	@Test
	void systemLevelRequestHasNoResourceType() {
		assertNull(attributes(details(RestOperationTypeEnum.METADATA, null, null, "metadata"), Set.of(), Set.of()).get(FhirRouteInterceptor.RESOURCE_TYPE));
		assertNull(attributes(details(null, "Patient", null, null), Set.of(), Set.of()).get(FhirRouteInterceptor.RESOURCE_TYPE));
		assertEquals("unhandled", attributes(details(null, "Patient", null, null), Set.of(), Set.of()).get(FhirRouteInterceptor.INTERACTION));
	}

	@Test
	void operationOutsideRegisteredSetIsOther() {
		RequestDetails everything = details(RestOperationTypeEnum.EXTENDED_OPERATION_INSTANCE, "Patient", "Patient/1", "$everything");
		assertEquals("$everything", attributes(everything, Set.of(), Set.of("$everything")).get(FhirRouteInterceptor.OPERATION));
		RequestDetails wildcard = details(RestOperationTypeEnum.EXTENDED_OPERATION_TYPE, "Patient", null, "$client-text");
		assertEquals("other", attributes(wildcard, Set.of(), Set.of("$everything")).get(FhirRouteInterceptor.OPERATION));
	}

	@Test
	void operationAttributeOnlyForOperations() {
		assertNull(attributes(details(RestOperationTypeEnum.SEARCH_TYPE, "Patient", null, "_search"), Set.of(), Set.of()).get(FhirRouteInterceptor.OPERATION));
		assertNull(attributes(details(RestOperationTypeEnum.HISTORY_INSTANCE, "Patient", "Patient/1", "_history"), Set.of(), Set.of()).get(FhirRouteInterceptor.OPERATION));
		assertNull(attributes(details(RestOperationTypeEnum.METADATA, null, null, "metadata"), Set.of(), Set.of()).get(FhirRouteInterceptor.OPERATION));
	}

	@Test
	void pageTakesResourceTypeFromResponseBundle() {
		RequestDetails page = r4Details(RestOperationTypeEnum.GET_PAGE, null, null);
		respond(page, bundleOf(new Patient(), new Organization()));
		assertEquals("search", attributes(page, Set.of(), Set.of()).get(FhirRouteInterceptor.INTERACTION));
		assertEquals("Patient", attributes(page, Set.of(), Set.of()).get(FhirRouteInterceptor.RESOURCE_TYPE));
		assertEquals("other", attributes(page, Set.of("Observation"), Set.of()).get(FhirRouteInterceptor.RESOURCE_TYPE));
	}

	@Test
	void emptyPageHasNoResourceType() {
		RequestDetails page = r4Details(RestOperationTypeEnum.GET_PAGE, null, null);
		respond(page, bundleOf());
		assertEquals("search", attributes(page, Set.of(), Set.of()).get(FhirRouteInterceptor.INTERACTION));
		assertNull(attributes(page, Set.of(), Set.of()).get(FhirRouteInterceptor.RESOURCE_TYPE));
	}

	@Test
	void outgoingResponseHookIgnoresNonPageRequests() {
		RequestDetails read = r4Details(RestOperationTypeEnum.READ, "Observation", "Observation/1");
		respond(read, bundleOf(new Patient()));
		assertNull(read.getUserData().get(FhirRouteInterceptor.PAGE_RESOURCE_TYPE));
		assertEquals("Observation", attributes(read, Set.of(), Set.of()).get(FhirRouteInterceptor.RESOURCE_TYPE));
	}

	@Test
	void pageHookSkipsMissingContextAndNonBundleResponses() {
		RequestDetails noContext = details(RestOperationTypeEnum.GET_PAGE, null, null, null);
		respond(noContext, bundleOf(new Patient()));
		assertNull(attributes(noContext, Set.of(), Set.of()).get(FhirRouteInterceptor.RESOURCE_TYPE));
		RequestDetails noResponse = r4Details(RestOperationTypeEnum.GET_PAGE, null, null);
		respond(noResponse, null);
		assertNull(attributes(noResponse, Set.of(), Set.of()).get(FhirRouteInterceptor.RESOURCE_TYPE));
		RequestDetails patientResponse = r4Details(RestOperationTypeEnum.GET_PAGE, null, null);
		respond(patientResponse, new Patient());
		assertNull(attributes(patientResponse, Set.of(), Set.of()).get(FhirRouteInterceptor.RESOURCE_TYPE));
	}

	private static String route(RestOperationTypeEnum type, String resourceName, String id, String operation) {
		return FhirRouteInterceptor.buildRoute(details(type, resourceName, id, operation), Set.of("$match", "$everything", "$export"));
	}

	@Test
	void finishServerSpanCountsEachRequestOnce() {
		RecordingCounter counter = new RecordingCounter();
		RecordingCounter errors = new RecordingCounter();
		FhirRouteInterceptor interceptor = new FhirRouteInterceptor(false, Set.of(), () -> Set.of("$everything"), counter, errors);
		interceptor.finishServerSpan(details(RestOperationTypeEnum.EXTENDED_OPERATION_INSTANCE, "Patient", "Patient/1", "$everything"));
		interceptor.finishServerSpan(details(null, "Patient", null, null));
		assertEquals(List.of(
				new Increment(1, Attributes.of(
						FhirRouteInterceptor.INTERACTION, "operation",
						FhirRouteInterceptor.RESOURCE_TYPE, "Patient",
						FhirRouteInterceptor.OPERATION, "$everything")),
				new Increment(1, Attributes.of(FhirRouteInterceptor.INTERACTION, "unhandled"))),
				counter.increments);
		assertEquals(List.of(), errors.increments);
	}

	@Test
	void registeredOperationsAreReadOnFirstRequestAndCached() {
		AtomicInteger reads = new AtomicInteger();
		Supplier<Set<String>> operations = () -> {
			reads.incrementAndGet();
			return Set.of("$everything");
		};
		RecordingCounter errors = new RecordingCounter();
		FhirRouteInterceptor interceptor = new FhirRouteInterceptor(false, Set.of(), operations, new RecordingCounter(), errors);
		assertEquals(0, reads.get());
		interceptor.finishServerSpan(details(RestOperationTypeEnum.READ, "Patient", "Patient/1", null));
		assertEquals(1, reads.get());
		interceptor.finishServerSpan(details(RestOperationTypeEnum.READ, "Patient", "Patient/1", null));
		assertEquals(1, reads.get());
		assertEquals(List.of(), errors.increments);
	}

	@Test
	void outcomeClass() {
		assertNull(FhirRouteInterceptor.outcomeFor(200));
		assertNull(FhirRouteInterceptor.outcomeFor(399));
		assertEquals("client_error", FhirRouteInterceptor.outcomeFor(400));
		assertEquals("client_error", FhirRouteInterceptor.outcomeFor(404));
		assertEquals("client_error", FhirRouteInterceptor.outcomeFor(499));
		assertEquals("server_error", FhirRouteInterceptor.outcomeFor(500));
		assertEquals("server_error", FhirRouteInterceptor.outcomeFor(503));
	}

	@Test
	void errorResponseAddsOneToErrorCounter() {
		RecordingCounter requests = new RecordingCounter();
		RecordingCounter errors = new RecordingCounter();
		new FhirRouteInterceptor(false, Set.of(), () -> Set.of(), requests, errors).finishServerSpan(patientSearchWithStatus(404));
		assertEquals(List.of(new Increment(1, Attributes.of(
						FhirRouteInterceptor.INTERACTION, "search",
						FhirRouteInterceptor.RESOURCE_TYPE, "Patient"))),
				requests.increments);
		assertEquals(List.of(new Increment(1, Attributes.of(
						FhirRouteInterceptor.INTERACTION, "search",
						FhirRouteInterceptor.OUTCOME, "client_error"))),
				errors.increments);
	}

	@Test
	void serverErrorResponseRecordsServerErrorOutcome() {
		RecordingCounter errors = new RecordingCounter();
		new FhirRouteInterceptor(false, Set.of(), () -> Set.of(), new RecordingCounter(), errors)
				.finishServerSpan(patientSearchWithStatus(500));
		assertEquals(List.of(new Increment(1, Attributes.of(
						FhirRouteInterceptor.INTERACTION, "search",
						FhirRouteInterceptor.OUTCOME, "server_error"))),
				errors.increments);
	}

	@Test
	void successfulResponseLeavesErrorCounterUnchanged() {
		RecordingCounter requests = new RecordingCounter();
		RecordingCounter errors = new RecordingCounter();
		new FhirRouteInterceptor(false, Set.of(), () -> Set.of(), requests, errors).finishServerSpan(patientSearchWithStatus(200));
		assertEquals(1, requests.increments.size());
		assertEquals(List.of(), errors.increments);
	}

	private static String interaction(RestOperationTypeEnum type) {
		return FhirRouteInterceptor.interactionFor(details(type, null, null, null));
	}

	private static Attributes attributes(RequestDetails details, Set<String> resourceTypes, Set<String> operations) {
		return FhirRouteInterceptor.attributesFor(details, resourceTypes, operations);
	}

	private static RequestDetails details(RestOperationTypeEnum type, String resourceName, String id, String operation) {
		RequestDetails details = new SystemRequestDetails();
		details.setRestOperationType(type);
		details.setResourceName(resourceName);
		details.setId(id == null ? null : new IdType(id));
		details.setOperation(operation);
		return details;
	}

	private static RequestDetails patientSearchWithStatus(int status) {
		ServletRequestDetails details = new ServletRequestDetails();
		details.setRestOperationType(RestOperationTypeEnum.SEARCH_TYPE);
		details.setResourceName("Patient");
		details.setServletRequest(new MockHttpServletRequest());
		MockHttpServletResponse response = new MockHttpServletResponse();
		response.setStatus(status);
		details.setServletResponse(response);
		return details;
	}

	private static RequestDetails r4Details(RestOperationTypeEnum type, String resourceName, String id) {
		SystemRequestDetails details = (SystemRequestDetails) details(type, resourceName, id, null);
		details.setFhirContext(FhirContext.forR4Cached());
		return details;
	}

	private static Bundle bundleOf(Resource... resources) {
		Bundle bundle = new Bundle();
		for (Resource resource : resources) {
			bundle.addEntry().setResource(resource);
		}
		return bundle;
	}

	private static void respond(RequestDetails details, IBaseResource response) {
		new FhirRouteInterceptor(false, Set.of(), () -> Set.of(), new RecordingCounter(), new RecordingCounter())
				.rememberPageResourceType(details, response);
	}

	private record Increment(long value, Attributes attributes) {}

	private static final class RecordingCounter implements LongCounter {
		final List<Increment> increments = new ArrayList<>();

		@Override
		public void add(long value) {
			add(value, Attributes.empty());
		}

		@Override
		public void add(long value, Attributes attributes) {
			increments.add(new Increment(value, attributes));
		}

		@Override
		public void add(long value, Attributes attributes, Context context) {
			add(value, attributes);
		}
	}
}
