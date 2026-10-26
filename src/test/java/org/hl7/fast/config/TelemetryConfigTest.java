package org.hl7.fast.config;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.rest.annotation.Operation;
import ca.uhn.fhir.rest.annotation.Search;
import ca.uhn.fhir.rest.server.IResourceProvider;
import ca.uhn.fhir.rest.server.RestfulServer;
import java.util.List;
import java.util.Set;
import org.hl7.fhir.r4.model.Parameters;
import org.hl7.fhir.r4.model.Patient;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TelemetryConfigTest {

	@Test
	void registeredOperationsListsDeclaredNamesWithoutTheWildcard() throws Exception {
		RestfulServer server = new RestfulServer(FhirContext.forR4Cached());
		server.registerProvider(new SystemOperations());
		server.registerProvider(new PatientOperations());
		server.init();
		assertEquals(Set.of("$server-named", "$global-named", "$type-named"), TelemetryConfig.registeredOperations(server));
	}

	public static class SystemOperations {

		@Operation(name = "$server-named")
		public Parameters serverNamed() {
			return new Parameters();
		}

		@Operation(name = "$global-named", global = true)
		public Parameters globalNamed() {
			return new Parameters();
		}

		@Operation(name = "$*")
		public Parameters wildcard() {
			return new Parameters();
		}
	}

	public static class PatientOperations implements IResourceProvider {

		@Override
		public Class<Patient> getResourceType() {
			return Patient.class;
		}

		@Operation(name = "$type-named")
		public Parameters typeNamed() {
			return new Parameters();
		}

		@Search
		public List<Patient> search() {
			return List.of();
		}
	}
}
