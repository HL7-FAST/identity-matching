package ca.uhn.fhir.jpa.starter.operations;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.jpa.starter.common.FhirContextProvider;
import ca.uhn.fhir.jpa.starter.operations.models.IdentifierQueryParams;
import ca.uhn.fhir.jpa.starter.operations.models.IdentityMatchingScorer;
import ca.uhn.fhir.model.base.composite.BaseIdentifierDt;
import ca.uhn.fhir.model.dstu2.composite.IdentifierDt;
import ca.uhn.fhir.rest.annotation.Operation;
import ca.uhn.fhir.rest.annotation.OperationParam;
import ca.uhn.fhir.rest.annotation.ResourceParam;
import ca.uhn.fhir.rest.client.api.IGenericClient;
import ca.uhn.fhir.rest.gclient.IQuery;
import ca.uhn.fhir.rest.gclient.StringClientParam;
import org.apache.commons.lang3.StringUtils;
import org.hl7.fhir.r4.model.*;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

public class IdentityMatching {

	private final String IDI_Patient_Profile = "http://hl7.org/fhir/us/identity-matching/StructureDefinition/IDI-Patient";
	private final String IDI_Patient_L0_Profile = "http://hl7.org/fhir/us/identity-matching/StructureDefinition/IDI-Patient-L0";
	private final String IDI_Patient_L1_Profile = "http://hl7.org/fhir/us/identity-matching/StructureDefinition/IDI-Patient-L1";
	private boolean assertIDIPatientProfile = false;
	private boolean assertIDIPatientL0Profile = false;
	private boolean assertIDIPatientL1Profile = false;

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
	public Bundle patientMatchOperation(
		@ResourceParam Patient patient,
		@OperationParam(name="onlyCertainMatches") BooleanType onlyCertainMatches,
		@OperationParam(name="count") IntegerType count
	)
	{

		FhirContext ctx = FhirContextProvider.getFhirContext();
		IGenericClient client = ctx.newRestfulGenericClient("http://localhost:8080/fhir/"); //TODO: pull from config

		//check profile assertions
		List<CanonicalType> metaProfiles = patient.getMeta().getProfile();
		for(CanonicalType profile : metaProfiles) {
			switch(profile.getValue()) {
				case(IDI_Patient_Profile): { assertIDIPatientProfile = true; } break;
				case(IDI_Patient_L0_Profile): { assertIDIPatientL0Profile = true; } break;
				case(IDI_Patient_L1_Profile): { assertIDIPatientL1Profile = true; } break;
			}
		}

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

				baseIdentifierParams.add(new IdentifierDt(x.getSystem(), x.getValue()));

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

		//TODO: Add middle name/initial

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
				List<String> addressValues = new ArrayList<>();
				x.getLine().stream().forEach(line -> addressValues.add(line.toString()));
				addressValues.add(x.getCity());
				addressValues.add(x.getState());
				addressValues.add(x.getPostalCode());
				patientQuery.where(Patient.ADDRESS.contains().values(addressValues));
			}

		//check telecom if present
		if(patient.hasTelecom())
		{
			for (ContactPoint x : patient.getTelecom()) {
				if (x.getSystem().toCode().equals(ContactPoint.ContactPointSystem.PHONE.toCode())) {
					StringClientParam phoneParam = new StringClientParam(Patient.SP_PHONE);
					patientQuery.where(phoneParam.matchesExactly().value(x.getValue()));
				} else if (x.getSystem().toCode().equals(ContactPoint.ContactPointSystem.EMAIL.toCode())) {
					StringClientParam emailParam = new StringClientParam(Patient.SP_EMAIL);
					patientQuery.where(emailParam.matchesExactly().value(x.getValue()));
				}
			}
		}

		Bundle foundPatients = patientQuery.execute();

		//Loop through results and grade matches
		for (Bundle.BundleEntryComponent pf : foundPatients.getEntry())
		{
			IdentityMatchingScorer scorer = new IdentityMatchingScorer();
			List<String> scorerNotes = new ArrayList<>();
			Patient patientEntry = (Patient)pf.getResource();

			//score identifiers
			if(patient.hasIdentifier() && patientEntry.hasIdentifier())
			{
				List<Identifier> identifiers = patientEntry.getIdentifier();
				for(IdentifierQueryParams id : identifierParams) {
					identifiers.stream().forEach(x -> {
						if(x.getSystem().equals(id.getIdentifierSystem()) && x.getValue().equals(id.getIdentifierValue())) {
							//TODO: figure out if there is a class/enum that represents the identifier codes rather than hard code them
							//http://build.fhir.org/ig/HL7/fhir-identity-matching-ig/ValueSet-Identity-Identifier-vs.html
							switch (id.getIdentifierCode()) {
								case("MR"): { scorer.setMrnMatch(true);  } break;
								case("DL"): { scorer.setDriversLicenseMatch(true); } break;
								case("PPN"): { scorer.setPassportMatch(true); } break;
								case("SB"): { scorer.setSSNMatch(true); } break;
							}
						}
					});
				}
			}

			//score names
			if(patient.hasName() && patientEntry.hasName()) {
				for(HumanName name : patientEntry.getName()) {
					HumanName patientRef = patient.getName().get(0);

					//check family name
					if(patientRef.getFamily().equals(name.getFamily())) {
						scorer.setFamilyNameMatch(true);
					}

					//check given names
					for(StringType givenName : name.getGiven()) {
						for(StringType refName : patientRef.getGiven()) {
							if(refName.toString().equals(givenName.toString())) {
								scorer.setGivenNameMatch(true);
							}
						}

//						if(patientRef.getGiven().contains(givenName)) {
//							scorer.setGivenNameMatch(true);
//						}
					}

					//TODO: Add middle name/initial

				}
			}

			//score gender
			if(patient.hasGender() && patientEntry.hasGender() && patientEntry.getGender().toCode().equals(patient.getGender().toCode())) {
				scorer.setGenderMatch(true);
			}

			//score birthdate
			if(patient.hasBirthDate() && patientEntry.hasBirthDate() && patientEntry.getBirthDate().equals(patient.getBirthDate())) {
				scorer.setBirthDateMatch(true);
			}

			//score addresses
			if(patient.hasAddress() && patientEntry.hasAddress()) {
				for(Address epAddress : patientEntry.getAddress()) {
					for(Address rpAddress : patient.getAddress()) {
						if(rpAddress.getLine().stream().anyMatch(new HashSet<>(epAddress.getLine())::contains)) {
							scorer.setAddressLineMatch(true);
						}
						if(rpAddress.getCity().equals(epAddress.getCity())) { scorer.setAddressCityMatch(true); }
						if(rpAddress.getState().equals(epAddress.getState())) { scorer.setAddressStateMatch(true);}
						if(rpAddress.getPostalCode().equals(epAddress.getPostalCode())) { scorer.setAddressPostalCodeMatch(true);}
					}
				}
			}

			//score telecom
			if(patient.hasTelecom() && patientEntry.hasTelecom()) {
				for (ContactPoint com : patientEntry.getTelecom()) {
					if(com.getSystem().toCode().equals(ContactPoint.ContactPointSystem.PHONE.toCode())) {
						scorer.setPhoneNumberMatch(true);
					}
					else if(com.getSystem().toCode().equals(ContactPoint.ContactPointSystem.EMAIL.toCode())) {
						scorer.setEmailMatch(true);
					}

				}
			}

			//create bundle search component element
			Bundle.BundleEntrySearchComponent searchComp = new Bundle.BundleEntrySearchComponent();
			searchComp.setMode(Bundle.SearchEntryMode.MATCH);
			searchComp.setScore(scorer.scoreMatch());

			//Add extension to place match messages
			Extension extExplanation = new Extension();
			extExplanation.setUrl("http://build.fhir.org/ig/HL7/fhir-identity-matching-ig/patient-matching#match-messages-extension");
			extExplanation.setValue(new StringType(StringUtils.join(scorer.getMatchMessages(), "|")));
			searchComp.addExtension(extExplanation);

			//set profile and weight extensions for testing
			if(assertIDIPatientProfile || assertIDIPatientL0Profile || assertIDIPatientL1Profile) {
				Integer scoredWeight = scorer.getMatchWeight();
				Extension extAssertion = new Extension();
				extAssertion.setUrl("#assertion");
				if(assertIDIPatientProfile) {
					extAssertion.setValue(new StringType("Profile " + IDI_Patient_Profile + ": Match weight " + scoredWeight + "  passes profile assertion."));
				}
				else if(assertIDIPatientL0Profile) {
					extAssertion.setValue(new StringType("Profile " + IDI_Patient_L0_Profile + ": Match weight " + scoredWeight + (scoredWeight >= 10 ? " passes profile assertion." : " fails profile assertion, weight score must be >= 10.")));
				}
				else if(assertIDIPatientL1Profile) {
					extAssertion.setValue(new StringType("Profile " + IDI_Patient_L1_Profile + ": Match weight " + scoredWeight + (scoredWeight >= 20 ? " passes profile assertion." : " fails profile assertion, weight score must be >= 20.")));
				}
				searchComp.addExtension(extAssertion);
			}

			pf.setSearch(searchComp);

		}

		return foundPatients;
	}

}
