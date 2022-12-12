package ca.uhn.fhir.jpa.starter.operations;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.jpa.search.builder.SearchBuilder;
import ca.uhn.fhir.jpa.starter.common.FhirContextProvider;
import ca.uhn.fhir.jpa.starter.operations.models.IdentifierQueryParams;
import ca.uhn.fhir.model.base.composite.BaseIdentifierDt;
import ca.uhn.fhir.model.dstu2.composite.IdentifierDt;
import ca.uhn.fhir.rest.annotation.Operation;
import ca.uhn.fhir.rest.annotation.OperationParam;

import ca.uhn.fhir.rest.annotation.ResourceParam;
import ca.uhn.fhir.rest.client.api.IGenericClient;
import org.hl7.fhir.r4.model.*;

import javax.persistence.EntityManagerFactory;
import javax.persistence.Persistence;
import java.util.ArrayList;
import java.util.List;

public class IdentityMatching {

	/**
	 * Returns welcome message for a customer by customer name and location
	 *
	 * @param patient - Use this to provide an entire set of patient details for the MPI to match against (e.g. POST a patient record to Patient/$match).
	 * @param onlyCertainMatches - If there are multiple potential matches, then the match should not return the results with this flag set to true. When false, the server may return multiple results with each result graded accordingly.
	 * @param count - The maximum number of records to return. If no value is provided, the server decides how many matches to return. Note that clients should be careful when using this, as it may prevent probable - and valid - matches from being returned
	 * @return -
	 * A bundle contain a set of Patient records that represent possible matches, optionally it may also contain an OperationOutcome with further information about the search results (such as warnings or information messages, such as a count of records that were close but eliminated) If the operation was unsuccessful, then an OperationOutcome may be returned along with a BadRequest status Code (e.g. security issue, or insufficient properties in patient fragment - check against profile)
	 * Note: as this is the only out parameter, it is a resource, and it has the name 'return', the result of this operation is returned directly as a resource
	 */
	@Operation(name="$match", typeName="Patient", idempotent=true)
	public Bundle patientTypeOperation(
		@ResourceParam Patient patient,
		@OperationParam(name="onlyCertainMatches") BooleanType onlyCertainMatches,
		@OperationParam(name="count") IntegerType count
	)
	{

		FhirContext ctx = FhirContextProvider.getFhirContext();
		IGenericClient client = ctx.newRestfulGenericClient("http://localhost:8080/fhir/");

		//build out identifier search params
		List<IdentifierQueryParams> identifierParams = new ArrayList<>();
		List<BaseIdentifierDt> baseIdentifierParams = new ArrayList<>();
		patient.getIdentifier().stream().forEach(x -> {

			List<Coding> codings = x.getType().getCoding();

			if(codings.size() > 0) {
				identifierParams.add(new IdentifierQueryParams(
					x.getSystem(),
					x.getValue(),
					codings.stream().findFirst().get().getCode()
				));

				baseIdentifierParams.add(new IdentifierDt(x.getSystem(), x.getValue()));			;

			}
		});



		Bundle foundPatients = client.search()
			.forResource(Patient.class)
//			.where(Patient.IDENTIFIER.exactly()
//					.systemAndValues(
//						identifierParams.stream().filter(x -> x.getIdentifierCode().equals("MR")).findFirst().get().getIdentifierSystem(),
//				identifierParams.stream().filter(x -> x.getIdentifierCode().equals("MR")).findFirst().get().getIdentifierValue()
//				)
//			)
			.where(Patient.IDENTIFIER.exactly().identifiers(baseIdentifierParams))
			.where(Patient.FAMILY.matchesExactly().values(patient.getName().get(0).getFamily()))
			.where(Patient.GIVEN.matchesExactly().values(patient.getName().get(0).getGivenAsSingleString()))
			.where(Patient.BIRTHDATE.exactly().day(patient.getBirthDate()))
			.where(Patient.GENDER.exactly().code(patient.getGender().toCode()))

			.returnBundle(Bundle.class)
			.execute();

		foundPatients.setType(Bundle.BundleType.SEARCHSET);

		return foundPatients;
	}

}
