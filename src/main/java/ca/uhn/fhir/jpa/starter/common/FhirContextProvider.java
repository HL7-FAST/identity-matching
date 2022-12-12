package ca.uhn.fhir.jpa.starter.common;

import ca.uhn.fhir.context.FhirContext;

public class FhirContextProvider {
	private static final FhirContext fhirContext = FhirContext.forR4();

	public static FhirContext getFhirContext() {
		return fhirContext;
	}
}
