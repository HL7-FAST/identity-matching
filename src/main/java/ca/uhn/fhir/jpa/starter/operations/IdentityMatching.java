package ca.uhn.fhir.jpa.starter.operations;

import ca.uhn.fhir.rest.annotation.Operation;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Patient;

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
		//@OperationParam(name="resource") Resource patient,
		//@OperationParam(name="onlyCertainMatches") boolean onlyCertainMatches,
		//@OperationParam(name="count") int count
	)
	{

		Bundle retVal = new Bundle();
		// Populate bundle with matching resources
		Patient foundPatient = new Patient();
		foundPatient.addIdentifier().setSystem("urn:mrns").setValue("12345");
		foundPatient.addName().setFamily("Phillips").addGiven("Adam");
		foundPatient.addAddress()
			.addLine("1777 Some Street")
			.setCity("Morgantown")
			.setState("West Virginia")
			.setPostalCode("26506");

		retVal.setType(Bundle.BundleType.SEARCHSET);
		retVal.addEntry()
			.setFullUrl(foundPatient.getIdElement().getValue())
			.setResource(foundPatient);


		return retVal;
	}

}
