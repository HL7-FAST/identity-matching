package ca.uhn.fhir.jpa.starter.operations;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.jpa.starter.common.FhirContextProvider;
import ca.uhn.fhir.jpa.starter.operations.models.IdentifierQueryParams;
import ca.uhn.fhir.model.base.composite.BaseIdentifierDt;
import ca.uhn.fhir.model.dstu2.composite.IdentifierDt;
import ca.uhn.fhir.rest.annotation.Operation;
import ca.uhn.fhir.rest.annotation.OperationParam;
import ca.uhn.fhir.rest.annotation.ResourceParam;
import ca.uhn.fhir.rest.client.api.IGenericClient;
import ca.uhn.fhir.rest.gclient.IQuery;
import org.apache.commons.lang3.StringUtils;
import org.hl7.fhir.r4.model.*;

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
		IGenericClient client = ctx.newRestfulGenericClient("http://localhost:8080/fhir/"); //TODO: pull from config

		//build out identifier search params and base identifier params by traversing the identifiers
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

		//Dynamically build out patient match query based on provided patient resource
		IQuery<Bundle> patientQuery = client.search()
		.forResource(Patient.class)
		.returnBundle(Bundle.class);

		//Check for identifiers if present
		if(baseIdentifierParams.size() > 0)
		{
			patientQuery.where(Patient.IDENTIFIER.exactly().identifiers(baseIdentifierParams));
		}

		//check for family name if present, choose the most recent (first)
		if(patient.getName().get(0).getFamily() != null) {
			patientQuery.where(Patient.FAMILY.matchesExactly().values(patient.getName().get(0).getFamily()));
		}

		//check for given name if present, check joined given names
		if(StringUtils.isNotEmpty(patient.getName().get(0).getGivenAsSingleString()))
		{
			patientQuery.where(Patient.GIVEN.matches().values(patient.getName().get(0).getGivenAsSingleString()));
		}

		//check for birthdate if present
		if(patient.hasBirthDate())
		{
			patientQuery.where(Patient.BIRTHDATE.exactly().day(patient.getBirthDate()));
		}

		//check gender if present
		if(patient.hasGender())
		{
			patientQuery.where(Patient.GENDER.exactly().code(patient.getGender().toCode()));
		}

		//check for address if present
		if(patient.hasAddress())
			for (Address x : patient.getAddress()) {
				if (x.hasLine()) {
					patientQuery.where(Patient.ADDRESS.contains().values(StringUtils.join(x.getLine(), " ")));
				}
				if (x.hasCity()) {
					patientQuery.where(Patient.ADDRESS_CITY.matchesExactly().value(x.getCity()));
				}
				if (x.hasPostalCode()) {
					patientQuery.where(Patient.ADDRESS_POSTALCODE.matchesExactly().value(x.getPostalCode()));
				}
			}

		//TODO: Grade results

		Bundle foundPatients = patientQuery.execute();
		return foundPatients;
	}

}
