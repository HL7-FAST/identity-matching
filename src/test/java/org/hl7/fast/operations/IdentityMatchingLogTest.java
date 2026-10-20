package org.hl7.fast.operations;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class IdentityMatchingLogTest {

	@Test
	void remoteErrorMessageLosesTheQueryString() {
		String message = "HAPI-1361: Failed to parse response from server when performing GET to URL "
				+ "http://remote/fhir/Patient?birthdate=eq1970-01-01&family=Smith&given=John&_count=50";

		assertEquals("HAPI-1361: Failed to parse response from server when performing GET to URL http://remote/fhir/Patient",
				IdentityMatching.withoutQuery(message));
		assertEquals("Connection refused", IdentityMatching.withoutQuery("Connection refused"));
		assertNull(IdentityMatching.withoutQuery(null));
	}
}
