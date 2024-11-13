package ca.uhn.fhir.jpa.starter.operations;
import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.jpa.api.dao.IFhirResourceDao;
import ca.uhn.fhir.jpa.searchparam.SearchParameterMap;
import ca.uhn.fhir.jpa.starter.AppProperties;
import ca.uhn.fhir.jpa.starter.common.FhirContextProvider;
import ca.uhn.fhir.jpa.starter.operations.models.IdentifierQueryParams;
import ca.uhn.fhir.jpa.starter.operations.models.IdentityMatchingScorer;
import ca.uhn.fhir.model.base.composite.BaseIdentifierDt;
import ca.uhn.fhir.model.dstu2.composite.IdentifierDt;
import ca.uhn.fhir.parser.DataFormatException;
import ca.uhn.fhir.rest.annotation.Operation;
import ca.uhn.fhir.rest.annotation.ResourceParam;
import ca.uhn.fhir.rest.api.server.IBundleProvider;
import ca.uhn.fhir.rest.client.api.IGenericClient;
import ca.uhn.fhir.rest.gclient.ICriterion;
import ca.uhn.fhir.rest.gclient.IQuery;
import ca.uhn.fhir.rest.gclient.StringClientParam;
import ca.uhn.fhir.rest.param.*;
import org.apache.commons.lang3.StringUtils;
import org.hl7.fhir.r4.model.*;
import org.springframework.core.io.ResourceLoader;
import org.springframework.core.io.support.ResourcePatternUtils;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;


public class IdentityMatching {

	private final String IDI_Patient_Profile = "http://hl7.org/fhir/us/identity-matching/StructureDefinition/IDI-Patient";
	private final String IDI_Patient_L0_Profile = "http://hl7.org/fhir/us/identity-matching/StructureDefinition/IDI-Patient-L0";
	private final String IDI_Patient_L1_Profile = "http://hl7.org/fhir/us/identity-matching/StructureDefinition/IDI-Patient-L1";
	private boolean assertIDIPatientProfile = false;
	private boolean assertIDIPatientL0Profile = false;
	private boolean assertIDIPatientL1Profile = false;
	private String serverAddress;
	private IFhirResourceDao<Patient> patientDao;
	private ResourceLoader resourceLoader;


	public IdentityMatching(AppProperties appProperties, IFhirResourceDao<Patient> patientDao, ResourceLoader resourceLoader) {
		this.serverAddress = appProperties.getServer_address();
		this.patientDao = patientDao;
		this.resourceLoader = resourceLoader;
	}

	
	@Operation(name = "$idi-match", typeName = "Patient", manualResponse = true)
	public void patientMatchOperation(
		@ResourceParam Parameters params,
		HttpServletRequest theServletRequest,
		HttpServletResponse theServletResponse
	) throws DataFormatException, IOException
	{

		assertIDIPatientProfile = false;
		assertIDIPatientL0Profile = false;
		assertIDIPatientL1Profile = false;

		FhirContext ctx = FhirContextProvider.getFhirContext();
		IGenericClient client = ctx.newRestfulGenericClient(serverAddress);

		Patient patient = null;
		boolean onlyCertainMatches = false;
		IntegerType count;
		Bundle outputBundle = new Bundle();

		for(var param : params.getParameter()) {
			//if a patient resource, set as patient to search against
			if(param.getName().equals("patient") && param.getResource().getClass().equals(Patient.class))
			{
				patient = (Patient)param.getResource();
			}

			//check for onlyCertainMatches
			if(param.getName().equals("onlyCertainMatches"))
			{
				onlyCertainMatches = ((BooleanType) param.getValue()).booleanValue();
			}

			//check for count
			if(param.getName().equals("count"))
			{
				count = (IntegerType)param.getValue();
			}

		}


		// Patient resource must be provided and the parameter must be named "patient"
		if (patient == null) {
			OperationOutcome outcome = new OperationOutcome();
			outcome.addIssue().setCode(OperationOutcome.IssueType.INVALID).setSeverity(OperationOutcome.IssueSeverity.ERROR)
				.setDiagnostics("A parameter named 'patient' must be provided with a valid Patient resource.");
			
			writeResponse(theServletRequest, theServletResponse, outcome, HttpServletResponse.SC_BAD_REQUEST);
			return;
		}


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
		outputBundle = getPatientMatch(patient);
		//foundPatients = matchPatients(patient, client, baseIdentifierParams);

		//Loop through results and grade matches
		for (Bundle.BundleEntryComponent pf : outputBundle.getEntry())
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
					if(patientRef.getFamily() != null &&  patientRef.getFamily().equals(name.getFamily())) {
						scorer.setFamilyNameMatch(true);
					}

					//check given names
					for(StringType givenName : name.getGiven()) {
						if (givenName.toString() == null) continue;
						for(StringType refName : patientRef.getGiven()) {
							if (refName.toString() == null) continue;
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
					if(com.hasSystem() && com.getSystem().toCode().equals(ContactPoint.ContactPointSystem.PHONE.toCode())) {
						for (ContactPoint refCom : patient.getTelecom()) {
							if (refCom.hasSystem() && refCom.getSystem().toCode().equals(ContactPoint.ContactPointSystem.PHONE.toCode())) {
								if(com.getValue().equals(refCom.getValue())) { scorer.setPhoneNumberMatch(true); }
							}
						}
					}
					else if(com.hasSystem() && com.getSystem().toCode().equals(ContactPoint.ContactPointSystem.EMAIL.toCode())) {
						for (ContactPoint refCom : patient.getTelecom()) {
							if (refCom.hasSystem() && refCom.getSystem().toCode().equals(ContactPoint.ContactPointSystem.PHONE.toCode())) {
								if(com.getValue().equals(refCom.getValue())) { scorer.setEmailMatch(true); }
							}
						}
					}
				}
			}

			//create bundle search component element
			// Bundle.BundleEntrySearchComponent searchComp = new Bundle.BundleEntrySearchComponent();
			// searchComp.setMode(Bundle.SearchEntryMode.MATCH);
			// searchComp.setScore(scorer.scoreMatch());

			//Add extension to place match messages
			// Extension extExplanation = new Extension();
			// extExplanation.setUrl("http://build.fhir.org/ig/HL7/fhir-identity-matching-ig/patient-matching.html");
			// extExplanation.setValue(new StringType(StringUtils.join(scorer.getMatchMessages(), "|")));
			// searchComp.addExtension(extExplanation);

			//set profile and weight extensions for testing
			// if(assertIDIPatientProfile || assertIDIPatientL0Profile || assertIDIPatientL1Profile) {
			// 	Extension extAssertion = new Extension();
			// 	extAssertion.setUrl("http://build.fhir.org/ig/HL7/fhir-identity-matching-ig/artifacts.html#structures-resource-profiles");
			// 	IdentityMatchingScorer assertionScore = gradePatientReference(patient);
			// 	extAssertion.setValue(new StringType("Supplied patient reference" + (passesProfileAssertion(assertionScore) ? " passed " : " failed ") + "profile assertion with a score of " + assertionScore.getMatchWeight() + "."));
			// 	searchComp.addExtension(extAssertion);
			// }

			// pf.setSearch(searchComp);

		}

		outputBundle.setType(Bundle.BundleType.COLLECTION);
		outputBundle.setMeta(new Meta().addProfile("http://hl7.org/fhir/us/identity-matching/StructureDefinition/idi-match-bundle"));

		// add example Organization to the output bundle as the first entry
		var resource = ResourcePatternUtils.getResourcePatternResolver(resourceLoader).getResource("classpath:Organization-OrgExample.json");
		String resourceText = new String(resource.getInputStream().readAllBytes());
		Organization exampleOrg = ctx.newJsonParser().parseResource(Organization.class, resourceText);

		if (exampleOrg != null) {
			outputBundle.getEntry().add(0, createBundleEntry(exampleOrg));
		}		
		else {
			OperationOutcome outcome = new OperationOutcome();
			outcome.addIssue().setCode(OperationOutcome.IssueType.EXCEPTION).setSeverity(OperationOutcome.IssueSeverity.ERROR)
				.setDiagnostics("Organization-OrgExample.json file not found.");

			writeResponse(theServletRequest, theServletResponse, outcome, HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
		}

		// add organization to identifier property
		Reference orgRef = new Reference("http://example.org/Organization/" + exampleOrg.getIdPart());
		outputBundle.setIdentifier(new Identifier().setAssigner(orgRef));

		writeResponse(theServletRequest, theServletResponse, outputBundle, HttpServletResponse.SC_OK);

	}

	private List<Bundle.BundleEntryComponent> executeMatchQuery(IGenericClient client, ICriterion criterion) {

		IQuery<Bundle> patientQuery = client.search()
			.forResource(Patient.class)
			.returnBundle(Bundle.class);

		return patientQuery.where(criterion).execute().getEntry();

	}

	private boolean uniquePatientMatch(Bundle.BundleEntryComponent newComponent, List<Bundle.BundleEntryComponent> prevComponents) {
		return prevComponents.stream().anyMatch(x -> x.getFullUrl().equals(newComponent.getFullUrl()));
	}

	private Bundle matchPatients(Patient patient, IGenericClient client, List<BaseIdentifierDt> baseIdentifierParams) {

		Bundle patientBundle = new Bundle();

		//TODO: build query based on profile assertion

		//Check for identifiers if present
		if(baseIdentifierParams.size() > 0)
		{
			for(Bundle.BundleEntryComponent comp: executeMatchQuery(client, Patient.IDENTIFIER.exactly().identifiers(baseIdentifierParams))) {
				if(patientBundle.getEntry().isEmpty() || !uniquePatientMatch(comp, patientBundle.getEntry())) {
					patientBundle.addEntry(comp);
				}
			}
		}

		//check for family name if present, choose the most recent (first)
		if(patient.getName().get(0).getFamily() != null) {
			for(Bundle.BundleEntryComponent comp: executeMatchQuery(client, Patient.FAMILY.matchesExactly().values(patient.getName().get(0).getFamily()))) {
				if(patientBundle.getEntry().isEmpty() || !uniquePatientMatch(comp, patientBundle.getEntry())) {
					patientBundle.addEntry(comp);
				}
			}
		}

		//check for given name if present, check joined given names
		if(StringUtils.isNotEmpty(patient.getName().get(0).getGivenAsSingleString()))
		{
			for(Bundle.BundleEntryComponent comp: executeMatchQuery(client, Patient.GIVEN.matches().values(patient.getName().get(0).getGivenAsSingleString()))) {
				if(patientBundle.getEntry().isEmpty() || !uniquePatientMatch(comp, patientBundle.getEntry())) {
					patientBundle.addEntry(comp);
				}
			}
			//patientQuery.where(Patient.GIVEN.matches().values(patient.getName().get(0).getGivenAsSingleString()));
		}

		//TODO: Add middle name/initial

		//check for birthdate if present
		if(patient.hasBirthDate())
		{
			for(Bundle.BundleEntryComponent comp: executeMatchQuery(client, Patient.BIRTHDATE.exactly().day(patient.getBirthDate()))) {
				if(patientBundle.getEntry().isEmpty() || !uniquePatientMatch(comp, patientBundle.getEntry())) {
					patientBundle.addEntry(comp);
				}
			}
			//patientQuery.where(Patient.BIRTHDATE.exactly().day(patient.getBirthDate()));
		}

		//check gender if present
		if(patient.hasGender())
		{
			for(Bundle.BundleEntryComponent comp: executeMatchQuery(client, Patient.GENDER.exactly().code(patient.getGender().toCode()))) {
				if(patientBundle.getEntry().isEmpty() || !uniquePatientMatch(comp, patientBundle.getEntry())) {
					patientBundle.addEntry(comp);
				}
			}
			//patientQuery.where(Patient.GENDER.exactly().code(patient.getGender().toCode()));
		}

		//check for address if present
		if(patient.hasAddress()) {
			for (Address x : patient.getAddress()) {
				List<String> addressValues = new ArrayList<>();
				x.getLine().stream().forEach(line -> addressValues.add(line.toString()));
				addressValues.add(x.getCity());
				addressValues.add(x.getState());
				addressValues.add(x.getPostalCode());

				for (Bundle.BundleEntryComponent comp : executeMatchQuery(client, Patient.ADDRESS.contains().values(addressValues))) {
					if (patientBundle.getEntry().isEmpty() || !uniquePatientMatch(comp, patientBundle.getEntry())) {
						patientBundle.addEntry(comp);
					}
				}
				//patientQuery.where(Patient.ADDRESS.contains().values(addressValues));
			}
		}
		//check telecom if present
		if(patient.hasTelecom())
		{
			for (ContactPoint x : patient.getTelecom()) {
				if (x.getSystem().toCode().equals(ContactPoint.ContactPointSystem.PHONE.toCode())) {
					StringClientParam phoneParam = new StringClientParam(Patient.SP_PHONE);
					for(Bundle.BundleEntryComponent comp: executeMatchQuery(client, phoneParam.matchesExactly().value(x.getValue()))) {
						if(patientBundle.getEntry().isEmpty() || !uniquePatientMatch(comp, patientBundle.getEntry())) {
							patientBundle.addEntry(comp);
						}
					}
					//patientQuery.where(phoneParam.matchesExactly().value(x.getValue()));
				} else if (x.getSystem().toCode().equals(ContactPoint.ContactPointSystem.EMAIL.toCode())) {
					StringClientParam emailParam = new StringClientParam(Patient.SP_EMAIL);
					for(Bundle.BundleEntryComponent comp: executeMatchQuery(client, emailParam.matchesExactly().value(x.getValue()))) {
						if(patientBundle.getEntry().isEmpty() || !uniquePatientMatch(comp, patientBundle.getEntry())) {
							patientBundle.addEntry(comp);
						}
					}
					//patientQuery.where(emailParam.matchesExactly().value(x.getValue()));
				}
			}
		}

		return patientBundle;

	}

	//DEPRECATED
	private IQuery<Bundle> buildMatchQuery(Patient patient, IGenericClient client, List<IdentifierQueryParams> identifierParams, List<BaseIdentifierDt> baseIdentifierParams) {

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

		return  patientQuery;

	}


	private String getProfileAssertion() {
		if(assertIDIPatientProfile) {
			return IDI_Patient_Profile;
		}
		else if(assertIDIPatientL0Profile) {
			return  IDI_Patient_L0_Profile;
		}
		else if(assertIDIPatientL1Profile) {
			return IDI_Patient_L1_Profile;
		}

		return "No profile provided";
	}

	private IdentityMatchingScorer gradePatientReference(Patient referencePatient) {
		IdentityMatchingScorer refScorer = new IdentityMatchingScorer();

		//score identifiers
		if(referencePatient.hasIdentifier())
		{
			for(Identifier id : referencePatient.getIdentifier()) {
				for(Coding code : id.getType().getCoding()) {
					switch(code.getCode()) {
						case("MR"): { refScorer.setMrnMatch(true);  } break;
						case("DL"): { refScorer.setDriversLicenseMatch(true); } break;
						case("PPN"): { refScorer.setPassportMatch(true); } break;
						case("SB"): { refScorer.setSSNMatch(true); } break;
					}

					//break out of loop if all referenced identifiers are found
					if(refScorer.getMrnMatch() && refScorer.getDriversLicenseMatch() && refScorer.getPassportMatch() && refScorer.getSSNMatch()) {
						break;
					}
				}
			}
		}

		//score names
		if(referencePatient.hasName()) {
			for(HumanName name : referencePatient.getName()) {
				if(name.hasGiven()) { refScorer.setGivenNameMatch(true); }
				if(name.hasFamily()) { refScorer.setFamilyNameMatch(true); }

				//if given and family name found, break out of for loop
				if(refScorer.getGivenNameMatch() && refScorer.getFamilyNameMatch()) break;
			}
		}

		//score gender
		if(referencePatient.hasGender()) { refScorer.setGenderMatch(true); }

		//score birthdate
		if(referencePatient.hasBirthDate()) { refScorer.setBirthDateMatch(true);	}

		//score addresses
		if(referencePatient.hasAddress()) {
			for(Address refAddress : referencePatient.getAddress()) {

				//see if address has individual components
				if(refAddress.hasLine()) {	refScorer.setAddressLineMatch(true); }
				if(refAddress.hasCity()) { refScorer.setAddressCityMatch(true); }
				if(refAddress.hasState()) { refScorer.setAddressStateMatch(true);}
				if(refAddress.hasPostalCode()) { refScorer.setAddressPostalCodeMatch(true);}

				//if all address components found, break out of for loop
				if(refScorer.getAddressLineMatch() && refScorer.getAddressCityMatch() && refScorer.getAddressStateMatch() && refScorer.getAddressPostalCodeMatch()) {
					break;
				}

			}
		}

		//score telecom
		if(referencePatient.hasTelecom()) {
			for (ContactPoint com : referencePatient.getTelecom()) {
				if(com.getSystem().toCode().equals(ContactPoint.ContactPointSystem.PHONE.toCode())) {
					refScorer.setPhoneNumberMatch(true);
				}
				else if(com.getSystem().toCode().equals(ContactPoint.ContactPointSystem.EMAIL.toCode())) {
					refScorer.setEmailMatch(true);
				}

				//if phone and email  found, break out of for loop
				if(refScorer.getPhoneNumberMatch() && refScorer.getEmailMatch()) break;

			}
		}

		return refScorer;

	}

	private boolean passesProfileAssertion(IdentityMatchingScorer refScorer) {
		Integer scoredWeight = refScorer.getMatchWeight();

		if(assertIDIPatientProfile) {
			return true;
		}
		else if(assertIDIPatientL0Profile) {
			return scoredWeight >= 9;
		}
		else if(assertIDIPatientL1Profile) {
			return scoredWeight >= 10;
		}
		else {
			return false;
		}
	}


	//TESTING DOA
	private Bundle getPatientMatch(Patient refPatient) {
		Bundle patientBundle = new Bundle();
		SearchParameterMap searchMap = new SearchParameterMap();

		//Check for identifiers if present
		if(refPatient.hasIdentifier())
		{
			TokenOrListParam identifierParam = new TokenOrListParam();
			for(Identifier identifier : refPatient.getIdentifier()) {
				identifierParam.addOr(new TokenParam().setValue(identifier.getValue()));
			}
			searchMap.add(Patient.IDENTIFIER.getParamName(), identifierParam);
		}

		//add search parameters for names
		if(refPatient.hasName()) {
			StringOrListParam givenNameParam = new StringOrListParam();
			StringOrListParam familyNameParam = new StringOrListParam();
			for(HumanName name : refPatient.getName()) {
				//add search params for given name
				if(name.hasGiven()){
					for(StringType givenName : name.getGiven()) {
						givenNameParam.addOr(new StringParam().setValue(String.valueOf(givenName)));
					}
				}

				//add search params for family name
				if(name.hasFamily()) {
					familyNameParam.addOr(new StringParam().setValue(name.getFamily()));
				}
			}
			searchMap.add(Patient.GIVEN.getParamName(), givenNameParam);
			searchMap.add(Patient.FAMILY.getParamName(), familyNameParam);
		}

		//check for birthdate if present
		if(refPatient.hasBirthDate())
		{
			searchMap.add(Patient.BIRTHDATE.getParamName(), new DateParam(refPatient.getBirthDateElement().getValue().toString()));
		}

		//check gender if present
		if(refPatient.hasGender())
		{
			TokenParam genderParam = new TokenParam(refPatient.getGender().toCode());
			searchMap.add(Patient.GENDER.getParamName(), genderParam);
		}

		//check for address if present

		if(refPatient.hasAddress()) {
			StringOrListParam lineParams = new StringOrListParam();
			StringOrListParam cityParams = new StringOrListParam();
			StringOrListParam stateParams = new StringOrListParam();
			StringOrListParam postalCodeParams = new StringOrListParam();
			for (Address address : refPatient.getAddress()) {
				//only search on home address
				if(address.hasUse() && address.getUse().equals(Address.AddressUse.HOME)) {

					//capture address line values (house number, apartment number, street name, etc.)
					if(address.hasLine()){
						for(StringType line : address.getLine()) {
							lineParams.addOr(new StringParam(line.asStringValue()));
						}
					}

					//capture city params
					if(address.hasCity()) {
						cityParams.addOr(new StringParam(address.getCity()));
					}

					//capture state params
					if(address.hasState()) {
						stateParams.addOr(new StringParam(address.getState()));
					}

					//capture postal code params
					if(address.hasPostalCode()) {
						postalCodeParams.addOr(new StringParam(address.getPostalCode()));
					}
				}
			}

			searchMap.add(Patient.ADDRESS.getParamName(), lineParams);
			searchMap.add(Patient.ADDRESS_CITY.getParamName(), cityParams);
			searchMap.add(Patient.ADDRESS_STATE.getParamName(), stateParams);
			searchMap.add(Patient.ADDRESS_POSTALCODE.getParamName(), postalCodeParams);
		}

		//check telecom if present
		if(refPatient.hasTelecom()) {
			StringOrListParam phoneParams = new StringOrListParam();
			StringOrListParam emailParams = new StringOrListParam();
			for(ContactPoint contact: refPatient.getTelecom()) {
				var system = contact.getSystemElement();
				if(system.equals(ContactPoint.ContactPointSystem.PHONE)
					&& (contact.getUseElement().equals(ContactPoint.ContactPointUse.HOME) || contact.getUseElement().equals(ContactPoint.ContactPointUse.MOBILE))) {
					phoneParams.addOr(new StringParam(contact.getValueElement().asStringValue()));
				}
				if(system.equals(ContactPoint.ContactPointSystem.EMAIL)) {
					emailParams.addOr(new StringParam(contact.getValueElement().asStringValue()));
				}
			}
			searchMap.add(Patient.PHONE.getParamName(), phoneParams);
			searchMap.add(Patient.EMAIL.getParamName(), phoneParams);
		}


		IBundleProvider patientResults = patientDao.search(searchMap);

		patientResults.getResources(0, patientResults.size())
			.stream().map(Patient.class::cast)
			.forEach(o -> patientBundle.addEntry(this.createBundleEntry(o)));

		return patientBundle;

	}

	private Bundle.BundleEntryComponent createBundleEntry(DomainResource resource) {
		Bundle.BundleEntryComponent entry = new Bundle.BundleEntryComponent();
		entry.setResource(resource);
		if (this.serverAddress != null && !this.serverAddress.isEmpty()) {
			try {
				String fullUrl = this.serverAddress;
				fullUrl += (!fullUrl.endsWith("/") ? "/" : "") + resource.fhirType() + "/" + resource.getIdPart();
				entry.setFullUrl(fullUrl);
			} catch (Exception ex) { }
		}
		return entry;
	}


	private void writeResponse(HttpServletRequest theServletRequest, HttpServletResponse theServletResponse, Resource resource, int reponseStatus) throws IOException {
		theServletResponse.setStatus(reponseStatus);
		theServletResponse.setContentType("application/fhir+json");

		FhirContext ctx = FhirContextProvider.getFhirContext();
		ctx.newJsonParser().encodeResourceToWriter(resource, theServletResponse.getWriter());
	}

}
