package org.hl7.fast.telemetry;

import ca.uhn.fhir.rest.api.RestOperationTypeEnum;
import ca.uhn.fhir.rest.api.server.RequestDetails;
import ca.uhn.fhir.rest.api.server.SystemRequestDetails;
import org.hl7.fhir.r4.model.IdType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FhirRouteInterceptorTest {

	@Test
	void route() {
		assertEquals("/fhir/unhandled", route(null, ".well-known", null, "udap"));
		assertEquals("/fhir/unhandled", route(null, "Patient", null, null));
		assertEquals("/fhir/metadata", route(RestOperationTypeEnum.METADATA, null, null, "metadata"));
		assertEquals("/fhir", route(RestOperationTypeEnum.TRANSACTION, null, null, null));
		assertEquals("/fhir/Patient", route(RestOperationTypeEnum.SEARCH_TYPE, "Patient", null, null));
		assertEquals("/fhir/Patient/_search", route(RestOperationTypeEnum.SEARCH_TYPE, "Patient", null, "_search"));
		assertEquals("/fhir/Patient/{id}", route(RestOperationTypeEnum.READ, "Patient", "Patient/123", null));
		assertEquals("/fhir/Patient/{id}/_history/{version}",
				route(RestOperationTypeEnum.VREAD, "Patient", "Patient/123/_history/2", null));
		assertEquals("/fhir/Patient/{id}/_history",
				route(RestOperationTypeEnum.HISTORY_INSTANCE, "Patient", "Patient/123", "_history"));
		assertEquals("/fhir/Patient/$idi-match",
				route(RestOperationTypeEnum.EXTENDED_OPERATION_TYPE, "Patient", null, "$idi-match"));
		assertEquals("/fhir/Patient/{id}/$everything",
				route(RestOperationTypeEnum.EXTENDED_OPERATION_INSTANCE, "Patient", "Patient/123", "$everything"));
	}

	@Test
	void routeIgnoresUncheckedPathSegments() {
		RequestDetails delete = details(RestOperationTypeEnum.DELETE, "Patient", "Patient/123", null);
		delete.setCompartmentName("garbage");
		delete.setSecondaryOperation("garbage");
		assertEquals("/fhir/Patient/{id}", FhirRouteInterceptor.buildRoute(delete));
	}

	private static String route(RestOperationTypeEnum type, String resourceName, String id, String operation) {
		return FhirRouteInterceptor.buildRoute(details(type, resourceName, id, operation));
	}

	private static RequestDetails details(RestOperationTypeEnum type, String resourceName, String id, String operation) {
		RequestDetails details = new SystemRequestDetails();
		details.setRestOperationType(type);
		details.setResourceName(resourceName);
		details.setId(id == null ? null : new IdType(id));
		details.setOperation(operation);
		return details;
	}
}
