package ca.uhn.fhir.jpa.starter.operations;
import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.fhirpath.IFhirPath;
import ca.uhn.fhir.jpa.api.dao.IFhirResourceDao;
import ca.uhn.fhir.jpa.searchparam.SearchParameterMap;
import ca.uhn.fhir.jpa.starter.AppProperties;
import ca.uhn.fhir.jpa.starter.common.FhirContextProvider;
import ca.uhn.fhir.jpa.starter.operations.models.IdentifierQueryParams;
import ca.uhn.fhir.jpa.starter.operations.models.IdentifierRegistry;
import ca.uhn.fhir.jpa.starter.operations.models.IdentityMatchParams;
import ca.uhn.fhir.jpa.starter.operations.models.IdentityMatchValidationLevel;
import ca.uhn.fhir.jpa.starter.operations.models.IdentityMatchingScorer;
import ca.uhn.fhir.rest.annotation.Operation;
import ca.uhn.fhir.rest.annotation.ResourceParam;
import ca.uhn.fhir.rest.api.server.IBundleProvider;
import ca.uhn.fhir.rest.api.server.RequestDetails;
import ca.uhn.fhir.rest.client.api.IGenericClient;
import ca.uhn.fhir.rest.param.*;
import ca.uhn.fhir.rest.server.exceptions.InternalErrorException;
import ca.uhn.fhir.rest.server.exceptions.InvalidRequestException;

import org.apache.commons.lang3.StringUtils;
import org.hl7.fhir.r4.model.*;
import org.springframework.core.io.ResourceLoader;
import org.springframework.core.io.support.ResourcePatternUtils;
import java.io.IOException;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.stream.Collectors;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;


public class IdentityMatching {

	private final String IDI_Patient_Profile = "http://hl7.org/fhir/us/identity-matching/StructureDefinition/IDI-Patient";
	private final String IDI_Patient_L0_Profile = "http://hl7.org/fhir/us/identity-matching/StructureDefinition/IDI-Patient-L0";
	private final String IDI_Patient_L1_Profile = "http://hl7.org/fhir/us/identity-matching/StructureDefinition/IDI-Patient-L1";

	private final String IDI_Patient_Name_FhirPath = "name.family.exists() or name.given.exists()";
	private final String IDI_Patient_FhirPath = "identifier.exists() or telecom.exists() or (name.family.exists() and name.given.exists()) or (address.line.exists() and address.city.exists()) or birthDate.exists()";
	private final String IDI_Patient_L0_FhirPath = "((identifier.type.coding.exists(code = 'PPN' or code = 'DL' or code = 'STID') or identifier.exists(system='http://hl7.org/fhir/us/identity-matching/ns/HL7Identifier')) and identifier.value.exists()).toInteger()*10 + iif(((address.exists(use = 'home') and address.line.exists() and (address.postalCode.exists() or (address.state.exists() and address.city.exists()))).toInteger() + (identifier.type.coding.exists(code != 'PPN' and code != 'DL' and code != 'STID') and identifier.value.exists()).toInteger() + (telecom.exists(system = 'email') and telecom.value.exists()).toInteger() + (telecom.exists(system = 'phone') and telecom.value.exists()).toInteger() + (photo.exists()).toInteger()) =1,4,iif(((address.exists(use = 'home') and address.line.exists() and (address.postalCode.exists() or (address.state.exists() and address.city.exists()))).toInteger() + (identifier.type.coding.exists(code != 'PPN' and code != 'DL' and code != 'STID') and identifier.value.exists()).toInteger() + (telecom.exists(system = 'email') and telecom.value.exists()).toInteger() + (telecom.exists(system = 'phone') and telecom.value.exists()).toInteger() + (photo.exists()).toInteger()) >1,5,0)) + (name.family.exists() and name.given.exists()).toInteger()*3 + (birthDate.exists().toInteger()*2) >= 9";
	private final String IDI_Patient_L1_FhirPath = "((identifier.type.coding.exists(code = 'PPN' or code = 'DL' or code = 'STID') or identifier.exists(system='http://hl7.org/fhir/us/identity-matching/ns/HL7Identifier')) and identifier.value.exists()).toInteger()*10 + iif(((address.exists(use = 'home') and address.line.exists() and (address.postalCode.exists() or (address.state.exists() and address.city.exists()))).toInteger() + (identifier.type.coding.exists(code != 'PPN' and code != 'DL' and code != 'STID') and identifier.value.exists()).toInteger() + (telecom.exists(system = 'email') and telecom.value.exists()).toInteger() + (telecom.exists(system = 'phone') and telecom.value.exists()).toInteger() + (photo.exists()).toInteger()) =1,4,iif(((address.exists(use = 'home') and address.line.exists() and (address.postalCode.exists() or (address.state.exists() and address.city.exists()))).toInteger() + (identifier.type.coding.exists(code != 'PPN' and code != 'DL' and code != 'STID') and identifier.value.exists()).toInteger() + (telecom.exists(system = 'email') and telecom.value.exists()).toInteger() + (telecom.exists(system = 'phone') and telecom.value.exists()).toInteger() + (photo.exists()).toInteger()) >1,5,0)) + (name.family.exists() and name.given.exists()).toInteger()*3 + (birthDate.exists().toInteger()*2) >= 10";

	private final String BIRTH_SEX_EXTENSION = "http://hl7.org/fhir/us/core/StructureDefinition/us-core-birthsex";
	private final String IDENTIFIER_SSN_SYSTEM = "http://hl7.org/fhir/sid/us-ssn";
	private final String IDENTITY_IDENTIFIER_SSN4_SYSTEM = "http://hl7.org/fhir/us/identity-matching/CodeSystem/Identity-Identifier-cs";
	private final String IDENTITY_IDENTIFIER_SSN4_CODE = "SSN4";
	private final String IDENTITY_IDENTIFIER_SSN4_PARAMETER = "ssn4";


	private AppProperties appProperties;
	private String serverAddress;
	private IFhirResourceDao<Patient> patientDao;
	private ResourceLoader resourceLoader;
	private IdentifierRegistry identifierRegistry;


	public IdentityMatching(AppProperties appProperties, IFhirResourceDao<Patient> patientDao, ResourceLoader resourceLoader) {
		this.appProperties = appProperties;
		this.serverAddress = appProperties.getServer_address();
		this.patientDao = patientDao;
		this.resourceLoader = resourceLoader;
		this.identifierRegistry = new IdentifierRegistry(resourceLoader);
	}


	/**
	 * $match operation defined in Identity Matching IG STU1
	 * Extends the HL7 FHIR patient $match operation: http://hl7.org/fhir/R4/patient-operation-match.html
	 */
	@Operation(name = "$match", typeName = "Patient")
	public Resource patientMatchOperation(
		@ResourceParam Parameters params,
		HttpServletRequest theServletRequest,
		HttpServletResponse theServletResponse,
		RequestDetails theRequestDetails
	) throws Exception
	{

		IdentityMatchParams identityMatchParams = new IdentityMatchParams();

		for(var param : params.getParameter()) {
			//if a patient resource, set as patient to search against
			if(param.getName().equals("resource") && param.getResource() != null && param.getResource().getClass().equals(Patient.class))
			{
				identityMatchParams.setPatient((Patient)param.getResource());
			}

			//check for onlyCertainMatches
			if(param.getName().equals("onlyCertainMatches"))
			{
				identityMatchParams.setOnlyCertainMatches((BooleanType) param.getValue());
			}

			//check for count
			if(param.getName().equals("count"))
			{
				identityMatchParams.setCount((IntegerType)param.getValue());
			}
		}


		// Patient resource must be provided and the parameter must be named "patient"
		if (identityMatchParams.getPatient() == null) {

			String message = "A parameter named 'resource' must be provided with a valid Patient resource.";
			OperationOutcome outcome = new OperationOutcome();
			outcome.addIssue().setCode(OperationOutcome.IssueType.INVALID).setSeverity(OperationOutcome.IssueSeverity.ERROR)
				.setDiagnostics(message);
			
			throw new InvalidRequestException(message, outcome);
		}

		Resource output = doMatch(identityMatchParams, theRequestDetails);

		if (output.getResourceType() == ResourceType.Bundle) {
			Bundle bundle = (Bundle)output;
			bundle.setType(Bundle.BundleType.SEARCHSET);

			// self link because this is a searchset and will produce a validation warning otherwise
			String matchUrl = StringUtils.removeEnd(appProperties.getServer_address(), "/") + "/Patient/$match";
			bundle.addLink(new Bundle.BundleLinkComponent().setRelation("self").setUrl(matchUrl));

			// set total for the searchset to the number of Patient entries
			bundle.setTotal((int)bundle.getEntry().stream().filter(x -> x.getResource() instanceof Patient).count());
		}

		return output;

	}

	
	/**
	 * $idi-match operation defined in Identity Matching IG STU2
	 */
	@Operation(name = "$idi-match", typeName = "Patient")
	public Resource idiMatchOperation(
		@ResourceParam Parameters params,
		HttpServletRequest theServletRequest,
		HttpServletResponse theServletResponse,
		RequestDetails theRequestDetails
	) throws IOException
	{

		IdentityMatchParams identityMatchParams = new IdentityMatchParams();

		for(var param : params.getParameter()) {
			//if a patient resource, set as patient to search against
			if(param.getName().equals("patient") && param.getResource() != null && param.getResource().getClass().equals(Patient.class))
			{
				identityMatchParams.setPatient((Patient)param.getResource());
			}

			//check for onlyCertainMatches
			if(param.getName().equals("onlyCertainMatches"))
			{
				identityMatchParams.setOnlyCertainMatches((BooleanType) param.getValue());
			}

			//check for count
			if(param.getName().equals("count"))
			{
				identityMatchParams.setCount((IntegerType)param.getValue());
			}
		}


		// Patient resource must be provided and the parameter must be named "patient"
		if (identityMatchParams.getPatient() == null) {

			String message = "A parameter named 'patient' must be provided with a valid Patient resource.";
			OperationOutcome outcome = new OperationOutcome();
			outcome.addIssue().setCode(OperationOutcome.IssueType.INVALID).setSeverity(OperationOutcome.IssueSeverity.ERROR)
				.setDiagnostics(message);
			
			throw new InvalidRequestException(message, outcome);
		}

		Resource output = doMatch(identityMatchParams, theRequestDetails);

		if (output.getResourceType() == ResourceType.Bundle) {

			Bundle bundle = (Bundle)output;

			bundle.setType(Bundle.BundleType.COLLECTION);
			output.setMeta(new Meta().addProfile("http://hl7.org/fhir/us/identity-matching/StructureDefinition/idi-match-bundle"));

			// add example Organization to the output bundle as the first entry
			var resource = ResourcePatternUtils.getResourcePatternResolver(resourceLoader).getResource("classpath:Organization-OrgExample.json");
			String resourceText = new String(resource.getInputStream().readAllBytes());
			FhirContext ctx = FhirContextProvider.getFhirContext();
			Organization exampleOrg = ctx.newJsonParser().parseResource(Organization.class, resourceText);

			if (exampleOrg != null) {
				bundle.getEntry().add(0, createBundleEntry(exampleOrg));
			}		
			else {
				String message = "Organization-OrgExample.json file not found.";
				OperationOutcome outcome = new OperationOutcome();
				outcome.addIssue().setCode(OperationOutcome.IssueType.EXCEPTION).setSeverity(OperationOutcome.IssueSeverity.ERROR)
					.setDiagnostics(message);

				throw new InternalErrorException(message, outcome);
			}

			// add organization to identifier property
			Reference orgRef = new Reference("http://example.org/Organization/" + exampleOrg.getIdPart());
			bundle.setIdentifier(new Identifier().setAssigner(orgRef));


			// bundle of type "collection" cannot have a search property in the entry
			bundle.getEntry().forEach(x -> x.setSearch(null));
		}

		return output;
	}


	protected Resource doMatch(
		IdentityMatchParams params,
		RequestDetails theRequestDetails
	) throws IOException 
	{

		// assertIDIPatientProfile = false;
		// assertIDIPatientL0Profile = false;
		// assertIDIPatientL1Profile = false;

		FhirContext ctx = FhirContextProvider.getFhirContext();
		IGenericClient client = ctx.newRestfulGenericClient(serverAddress);

		Patient patient = params.getPatient();
		BooleanType onlyCertainMatches = params.getOnlyCertainMatches();
		IntegerType count = params.getCount();
		Bundle outputBundle = new Bundle();
		
		
		// ensure we have a valid Patient resource
		assertPatientIsValid(patient, theRequestDetails);


		//build out identifier search params and base identifier params by traversing the identifiers
		List<IdentifierQueryParams> identifierParams = new ArrayList<>();
		patient.getIdentifier().stream().forEach(x -> {
			
			if (x.hasType() && x.getType().hasCoding()) {
				for (Coding coding : x.getType().getCoding()) {
					identifierParams.add(new IdentifierQueryParams(
						x.getSystem(),
						x.getValue(),
						coding.getCode(),
						coding.getSystem()
					));
				}
			}
			else {
				identifierParams.add(new IdentifierQueryParams(
					x.getSystem(),
					x.getValue(),
					null,
					null
				));
			}
		});

		//Dynamically build out patient match query based on provided patient resource
		outputBundle = getPatientMatch(patient, theRequestDetails);
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
					// System.out.println("id: " + id.getIdentifierSystem() + " :" + id.getIdentifierValue() + " -- " + id.getIdentifierTypeSystem() + " : " + id.getIdentifierTypeCode());
					identifiers.stream().forEach(x -> {
						// System.out.println("x: " + (x.hasType() ? x.getType().getCoding().stream().findFirst().get().getSystem() : "") + " -- " + x.getSystem() + " " + x.getValue());

						if (!x.hasValue()) {
							return;
						}
						
						// primary check for a full identifier match
						if (
							// identifier value must match
							x.getValue().equals(id.getIdentifierValue())

							// ... and
							&& (
								// identifier type system + code need to match...
								(x.hasType() && x.getType().hasCoding() && x.getType().getCoding().stream().anyMatch(
									coding -> coding.hasSystem() && coding.getSystem().equals(id.getIdentifierTypeSystem()) 
														&& coding.hasCode() && coding.getCode().equals(id.getIdentifierTypeCode())
								))
								// ...or identifier system has to match (if no type exists)
								|| (x.hasSystem() && x.getSystem().equals(id.getIdentifierSystem()))
							)						
							
						) {

							// make sure we have a code to check against...
							String code = id.getIdentifierTypeCode() != null ? id.getIdentifierTypeCode() : identifierRegistry.getTypeFromUri(id.getIdentifierSystem());

							if (code == null) {
								return;
							}

							switch (code) {
								case("MR"): { scorer.setMrnMatch(true);  } break;
								case("PRN"): { scorer.setDigitalIdentifierMatch(true); } break;
								case("DL"): { scorer.setDriversLicenseMatch(true); } break;
								case("PPN"): { scorer.setPassportMatch(true); } break;
								case("SB"): { scorer.setSSNMatch(true); } break;
								case("SSN4"): { scorer.setSSNLast4Match(true); } break;
							}
							
						}


						// Additional SSN4 match case:
						// Patient may be matched based on the custom SSN4 search parameter but patient resource has a full SSN (http://terminology.hl7.org/CodeSystem/v2-0203|SB type)
						// But, the search was only for last four digits, so we cannot weigh it as a full SSN match
						else if (
							id.hasIdentifierSystem() && id.hasIdentifierTypeCode()
							&& id.getIdentifierTypeSystem().equals(IDENTITY_IDENTIFIER_SSN4_SYSTEM) && id.getIdentifierTypeCode().equals(IDENTITY_IDENTIFIER_SSN4_CODE)
							&& x.getSystem().equals(IDENTIFIER_SSN_SYSTEM) 
							&& x.getValue().substring(Math.max(x.getValue().length() - 4, 0)).equals(id.getIdentifierValue())
						) {
							scorer.setSSNLast4Match(true);
						}
					});
				}
			}

			// score names
			if(patient.hasName() && patientEntry.hasName()) {
				for(HumanName name : patientEntry.getName()) {
					for (HumanName patientRef : patient.getName()) {					

						// check family name
						if(patientRef.getFamily() != null &&  patientRef.getFamily().equals(name.getFamily())) {
							scorer.setFamilyNameMatch(true);
						}

						// check given names
						if (name.hasGiven() && patientRef.hasGiven()) {

							// first name
							if (name.getGiven().size() > 0 && patientRef.getGiven().size() > 0) {
								if (name.getGiven().get(0).toString().equals(patientRef.getGiven().get(0).toString())) {
									scorer.setFirstNameMatch(true);
								}
							}

							// middle name and initial
							if (name.getGiven().size() > 1 && patientRef.getGiven().size() > 1) {
								String nameMiddle = name.getGiven().get(1).toString();
								String refMiddle = patientRef.getGiven().get(1).toString();

								// full middle name
								if (nameMiddle.equals(refMiddle)) {
									scorer.setMiddleNameMatch(true);
								}

								// middle initial
								if (refMiddle.length() == 1 && nameMiddle.charAt(0) == refMiddle.charAt(0)) {
									scorer.setMiddleInitialMatch(true);
								}

							}

						}

					}
				}
			}

			//score birth sex
			if (patient.hasExtension(BIRTH_SEX_EXTENSION) && patientEntry.hasExtension(BIRTH_SEX_EXTENSION)) {
				Extension patientBirthSex = patient.getExtensionByUrl(BIRTH_SEX_EXTENSION);
				Extension patientEntryBirthSex = patientEntry.getExtensionByUrl(BIRTH_SEX_EXTENSION);
				if (patientBirthSex.getValue().toString().equals(patientEntryBirthSex.getValue().toString())) {
					scorer.setBirthSexMatch(true);
				}
			}

			//score birthdate
			if(patient.hasBirthDate() && patientEntry.hasBirthDate() && patientEntry.getBirthDate().equals(patient.getBirthDate())) {
				scorer.setBirthDateMatch(true);
			}

			//score addresses
			if(patient.hasAddress() && patientEntry.hasAddress()) {
				for(Address epAddress : patientEntry.getAddress()) {
					for(Address rpAddress : patient.getAddress()) {
						if(rpAddress.getLine().stream().map(StringType::toString).anyMatch(line -> epAddress.getLine().stream().map(StringType::toString).anyMatch(line::equals))) {
							scorer.setAddressLineMatch(true);
						}
						if(rpAddress.getCity() != null && rpAddress.getCity().equals(epAddress.getCity())) { scorer.setAddressCityMatch(true); }
						if(rpAddress.getState() != null && rpAddress.getState().equals(epAddress.getState())) { scorer.setAddressStateMatch(true);}
						if(rpAddress.getPostalCode() != null && rpAddress.getPostalCode().equals(epAddress.getPostalCode())) { scorer.setAddressPostalCodeMatch(true);}
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
							if (refCom.hasSystem() && refCom.getSystem().toCode().equals(ContactPoint.ContactPointSystem.EMAIL.toCode())) {
								if(com.getValue().equals(refCom.getValue())) { scorer.setEmailMatch(true); }
							}
						}
					}
				}
			}

			//create bundle search component element
			Bundle.BundleEntrySearchComponent searchComp = new Bundle.BundleEntrySearchComponent();
			searchComp.setMode(Bundle.SearchEntryMode.MATCH);
			// searchComp.setScore(scorer.scoreMatch());
			// for(String msg : scorer.getMatchMessages()) {
			// 	System.out.println(msg);
			// }
			// System.out.println("Match score: " + scorer.scoreMatch());

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

			pf.setSearch(searchComp);

		}



		// no matches found... return an OperationOutcome instead
		if (outputBundle.getEntry().isEmpty()) {
			String message = "No matches found.";
			OperationOutcome outcome = new OperationOutcome();
			outcome.addIssue().setCode(OperationOutcome.IssueType.NOTFOUND).setSeverity(OperationOutcome.IssueSeverity.WARNING)
				.setDiagnostics(message);

			outputBundle.addEntry().setResource(outcome);
		}


		return outputBundle;

	}

	private void assertPatientIsValid(Patient patient, RequestDetails theRequestDetails) {

		// Patient resource must be provided
		if (patient == null) {
			String message = "A valid Patient resource must be provided.";
			OperationOutcome outcome = new OperationOutcome();
			outcome.addIssue().setCode(OperationOutcome.IssueType.INVALID).setSeverity(OperationOutcome.IssueSeverity.ERROR)
				.setDiagnostics(message);
			
			throw new InvalidRequestException(message, outcome);
		}

		// Validate the Patient resource if configured

		IdentityMatchValidationLevel validationLevel = appProperties.getMatchValidationLevel();

		// override validationLevel to the value of the header if it is a valid value of the enum
		String matchHeader = theRequestDetails.getHeader(appProperties.getMatchValidationHeader());
		if (matchHeader != null) {
			try {
				validationLevel = IdentityMatchValidationLevel.valueOf(matchHeader.toUpperCase());
			} catch (IllegalArgumentException e) {
				throw new InvalidRequestException("Invalid value for " + appProperties.getMatchValidationHeader() + " header: \"" + matchHeader 
					+ "\". Valid values are: " 
					+ EnumSet.allOf(IdentityMatchValidationLevel.class).stream().map(Enum::toString).collect(Collectors.joining(", "))
				);
			}
		}

		// no validation required
		if (validationLevel == IdentityMatchValidationLevel.NONE) {
			return;
		}
		
		// check if the Patient resource declares conformance to an expected profile
		boolean assertIDIPatientProfile = false;
	 	boolean assertIDIPatientL0Profile = false;
		boolean assertIDIPatientL1Profile = false;
		List<CanonicalType> metaProfiles = patient.getMeta().getProfile();
		for(CanonicalType profile : metaProfiles) {
			switch(profile.getValue()) {
				case(IDI_Patient_L1_Profile): { assertIDIPatientL1Profile = true; } break;
				case(IDI_Patient_L0_Profile): { assertIDIPatientL0Profile = true; } break;
				case(IDI_Patient_Profile): { assertIDIPatientProfile = true; } break;
			}
		}

		// if the server is configured to require a profile in the meta.profile,
		// throw an error if the Patient resource does not declare conformance to an expected profile
		if (validationLevel == IdentityMatchValidationLevel.META_PROFILE) {
			if (!(assertIDIPatientProfile || assertIDIPatientL0Profile || assertIDIPatientL1Profile)) {
				String message = "The Patient resource must declare conformance to an appropriate IDI-Patient profile in the meta.profile field.";
				OperationOutcome outcome = new OperationOutcome();
				outcome.addIssue().setCode(OperationOutcome.IssueType.INVALID).setSeverity(OperationOutcome.IssueSeverity.ERROR)
					.setDiagnostics(message);
				
				throw new InvalidRequestException(message, outcome);
			}
		}


		// default validation level starts here
		// validate the resource against the most restrictive profile declared or the base IDI-Patient profile

		IFhirPath fhirPath = FhirContextProvider.getFhirContext().newFhirPath();

		String message = null;

		// Name validation is required for all profiles
		var nameResult = fhirPath.evaluateFirst(patient, IDI_Patient_Name_FhirPath, BooleanType.class);
		if (nameResult == null || !nameResult.isPresent() || !nameResult.get().booleanValue()) {
			message = "Either the given or family name SHALL be present.";
		}

		if (message == null) {

			// IDI-Patient L1 profile validation
			if (assertIDIPatientL1Profile) {
				var result = fhirPath.evaluateFirst(patient, IDI_Patient_L1_FhirPath, BooleanType.class);
				if (result == null || !result.isPresent() || !result.get().booleanValue()) {
					message = "The Patient resource does not meet the requirements of the IDI-Patient-L1 profile.";
				}
			}

			// IDI-Patient L0 profile validation
			else if (assertIDIPatientL0Profile) {
				var result = fhirPath.evaluateFirst(patient, IDI_Patient_L0_FhirPath, BooleanType.class);
				if (result == null || !result.isPresent() || !result.get().booleanValue()) {
					message = "The Patient resource does not meet the requirements of the IDI-Patient-L0 profile.";
				}
			}

			// base IDI-Patient profile validation
			else {
				var result = fhirPath.evaluateFirst(patient, IDI_Patient_FhirPath, BooleanType.class);
				if (result == null || !result.isPresent() || !result.get().booleanValue()) {
					message = "The Patient resource does not meet the requirements of the IDI-Patient profile.";
				}
			}

		}


		if (message != null) {
			OperationOutcome outcome = new OperationOutcome();
			outcome.addIssue().setCode(OperationOutcome.IssueType.INVALID).setSeverity(OperationOutcome.IssueSeverity.ERROR).setDiagnostics(message);
			throw new InvalidRequestException(message, outcome);
		}

	}

	// private List<Bundle.BundleEntryComponent> executeMatchQuery(IGenericClient client, ICriterion criterion) {

	// 	IQuery<Bundle> patientQuery = client.search()
	// 		.forResource(Patient.class)
	// 		.returnBundle(Bundle.class);

	// 	return patientQuery.where(criterion).execute().getEntry();

	// }

	// private boolean uniquePatientMatch(Bundle.BundleEntryComponent newComponent, List<Bundle.BundleEntryComponent> prevComponents) {
	// 	return prevComponents.stream().anyMatch(x -> x.getFullUrl().equals(newComponent.getFullUrl()));
	// }

	// private Bundle matchPatients(Patient patient, IGenericClient client, List<BaseIdentifierDt> baseIdentifierParams) {

	// 	Bundle patientBundle = new Bundle();

	// 	//TODO: build query based on profile assertion

	// 	//Check for identifiers if present
	// 	if(baseIdentifierParams.size() > 0)
	// 	{
	// 		for(Bundle.BundleEntryComponent comp: executeMatchQuery(client, Patient.IDENTIFIER.exactly().identifiers(baseIdentifierParams))) {
	// 			if(patientBundle.getEntry().isEmpty() || !uniquePatientMatch(comp, patientBundle.getEntry())) {
	// 				patientBundle.addEntry(comp);
	// 			}
	// 		}
	// 	}

	// 	//check for family name if present, choose the most recent (first)
	// 	if(patient.getName().get(0).getFamily() != null) {
	// 		for(Bundle.BundleEntryComponent comp: executeMatchQuery(client, Patient.FAMILY.matchesExactly().values(patient.getName().get(0).getFamily()))) {
	// 			if(patientBundle.getEntry().isEmpty() || !uniquePatientMatch(comp, patientBundle.getEntry())) {
	// 				patientBundle.addEntry(comp);
	// 			}
	// 		}
	// 	}

	// 	//check for given name if present, check joined given names
	// 	if(StringUtils.isNotEmpty(patient.getName().get(0).getGivenAsSingleString()))
	// 	{
	// 		for(Bundle.BundleEntryComponent comp: executeMatchQuery(client, Patient.GIVEN.matches().values(patient.getName().get(0).getGivenAsSingleString()))) {
	// 			if(patientBundle.getEntry().isEmpty() || !uniquePatientMatch(comp, patientBundle.getEntry())) {
	// 				patientBundle.addEntry(comp);
	// 			}
	// 		}
	// 		//patientQuery.where(Patient.GIVEN.matches().values(patient.getName().get(0).getGivenAsSingleString()));
	// 	}

	// 	//TODO: Add middle name/initial

	// 	//check for birthdate if present
	// 	if(patient.hasBirthDate())
	// 	{
	// 		for(Bundle.BundleEntryComponent comp: executeMatchQuery(client, Patient.BIRTHDATE.exactly().day(patient.getBirthDate()))) {
	// 			if(patientBundle.getEntry().isEmpty() || !uniquePatientMatch(comp, patientBundle.getEntry())) {
	// 				patientBundle.addEntry(comp);
	// 			}
	// 		}
	// 		//patientQuery.where(Patient.BIRTHDATE.exactly().day(patient.getBirthDate()));
	// 	}

	// 	//check gender if present
	// 	if(patient.hasGender())
	// 	{
	// 		for(Bundle.BundleEntryComponent comp: executeMatchQuery(client, Patient.GENDER.exactly().code(patient.getGender().toCode()))) {
	// 			if(patientBundle.getEntry().isEmpty() || !uniquePatientMatch(comp, patientBundle.getEntry())) {
	// 				patientBundle.addEntry(comp);
	// 			}
	// 		}
	// 		//patientQuery.where(Patient.GENDER.exactly().code(patient.getGender().toCode()));
	// 	}

	// 	//check for address if present
	// 	if(patient.hasAddress()) {
	// 		for (Address x : patient.getAddress()) {
	// 			List<String> addressValues = new ArrayList<>();
	// 			x.getLine().stream().forEach(line -> addressValues.add(line.toString()));
	// 			addressValues.add(x.getCity());
	// 			addressValues.add(x.getState());
	// 			addressValues.add(x.getPostalCode());

	// 			for (Bundle.BundleEntryComponent comp : executeMatchQuery(client, Patient.ADDRESS.contains().values(addressValues))) {
	// 				if (patientBundle.getEntry().isEmpty() || !uniquePatientMatch(comp, patientBundle.getEntry())) {
	// 					patientBundle.addEntry(comp);
	// 				}
	// 			}
	// 			//patientQuery.where(Patient.ADDRESS.contains().values(addressValues));
	// 		}
	// 	}
	// 	//check telecom if present
	// 	if(patient.hasTelecom())
	// 	{
	// 		for (ContactPoint x : patient.getTelecom()) {
	// 			if (x.getSystem().toCode().equals(ContactPoint.ContactPointSystem.PHONE.toCode())) {
	// 				StringClientParam phoneParam = new StringClientParam(Patient.SP_PHONE);
	// 				for(Bundle.BundleEntryComponent comp: executeMatchQuery(client, phoneParam.matchesExactly().value(x.getValue()))) {
	// 					if(patientBundle.getEntry().isEmpty() || !uniquePatientMatch(comp, patientBundle.getEntry())) {
	// 						patientBundle.addEntry(comp);
	// 					}
	// 				}
	// 				//patientQuery.where(phoneParam.matchesExactly().value(x.getValue()));
	// 			} else if (x.getSystem().toCode().equals(ContactPoint.ContactPointSystem.EMAIL.toCode())) {
	// 				StringClientParam emailParam = new StringClientParam(Patient.SP_EMAIL);
	// 				for(Bundle.BundleEntryComponent comp: executeMatchQuery(client, emailParam.matchesExactly().value(x.getValue()))) {
	// 					if(patientBundle.getEntry().isEmpty() || !uniquePatientMatch(comp, patientBundle.getEntry())) {
	// 						patientBundle.addEntry(comp);
	// 					}
	// 				}
	// 				//patientQuery.where(emailParam.matchesExactly().value(x.getValue()));
	// 			}
	// 		}
	// 	}

	// 	return patientBundle;

	// }

	//DEPRECATED
	// private IQuery<Bundle> buildMatchQuery(Patient patient, IGenericClient client, List<IdentifierQueryParams> identifierParams, List<BaseIdentifierDt> baseIdentifierParams) {

	// 	IQuery<Bundle> patientQuery = client.search()
	// 		.forResource(Patient.class)
	// 		.returnBundle(Bundle.class);

	// 	//Check for identifiers if present
	// 	if(baseIdentifierParams.size() > 0)
	// 	{
	// 		patientQuery.where(Patient.IDENTIFIER.exactly().identifiers(baseIdentifierParams));
	// 	}

	// 	//check for family name if present, choose the most recent (first)
	// 	if(patient.getName().get(0).getFamily() != null) {
	// 		patientQuery.where(Patient.FAMILY.matchesExactly().values(patient.getName().get(0).getFamily()));
	// 	}

	// 	//check for given name if present, check joined given names
	// 	if(StringUtils.isNotEmpty(patient.getName().get(0).getGivenAsSingleString()))
	// 	{
	// 		patientQuery.where(Patient.GIVEN.matches().values(patient.getName().get(0).getGivenAsSingleString()));
	// 	}

	// 	//check for birthdate if present
	// 	if(patient.hasBirthDate())
	// 	{
	// 		patientQuery.where(Patient.BIRTHDATE.exactly().day(patient.getBirthDate()));
	// 	}

	// 	//check gender if present
	// 	if(patient.hasGender())
	// 	{
	// 		patientQuery.where(Patient.GENDER.exactly().code(patient.getGender().toCode()));
	// 	}

	// 	//check for address if present
	// 	if(patient.hasAddress())
	// 		for (Address x : patient.getAddress()) {
	// 			List<String> addressValues = new ArrayList<>();
	// 			x.getLine().stream().forEach(line -> addressValues.add(line.toString()));
	// 			addressValues.add(x.getCity());
	// 			addressValues.add(x.getState());
	// 			addressValues.add(x.getPostalCode());
	// 			patientQuery.where(Patient.ADDRESS.contains().values(addressValues));
	// 		}

	// 	//check telecom if present
	// 	if(patient.hasTelecom())
	// 	{
	// 		for (ContactPoint x : patient.getTelecom()) {
	// 			if (x.getSystem().toCode().equals(ContactPoint.ContactPointSystem.PHONE.toCode())) {
	// 				StringClientParam phoneParam = new StringClientParam(Patient.SP_PHONE);
	// 				patientQuery.where(phoneParam.matchesExactly().value(x.getValue()));
	// 			} else if (x.getSystem().toCode().equals(ContactPoint.ContactPointSystem.EMAIL.toCode())) {
	// 				StringClientParam emailParam = new StringClientParam(Patient.SP_EMAIL);
	// 				patientQuery.where(emailParam.matchesExactly().value(x.getValue()));
	// 			}
	// 		}
	// 	}

	// 	return  patientQuery;

	// }


	// private String getProfileAssertion() {
	// 	if(assertIDIPatientProfile) {
	// 		return IDI_Patient_Profile;
	// 	}
	// 	else if(assertIDIPatientL0Profile) {
	// 		return  IDI_Patient_L0_Profile;
	// 	}
	// 	else if(assertIDIPatientL1Profile) {
	// 		return IDI_Patient_L1_Profile;
	// 	}

	// 	return "No profile provided";
	// }

	// private IdentityMatchingScorer gradePatientReference(Patient referencePatient) {
	// 	IdentityMatchingScorer refScorer = new IdentityMatchingScorer();

	// 	//score identifiers
	// 	if(referencePatient.hasIdentifier())
	// 	{
	// 		for(Identifier id : referencePatient.getIdentifier()) {
	// 			for(Coding code : id.getType().getCoding()) {
	// 				switch(code.getCode()) {
	// 					case("MR"): { refScorer.setMrnMatch(true);  } break;
	// 					case("DL"): { refScorer.setDriversLicenseMatch(true); } break;
	// 					case("PPN"): { refScorer.setPassportMatch(true); } break;
	// 					case("SB"): { refScorer.setSSNMatch(true); } break;
	// 				}

	// 				//break out of loop if all referenced identifiers are found
	// 				if(refScorer.getMrnMatch() && refScorer.getDriversLicenseMatch() && refScorer.getPassportMatch() && refScorer.getSSNMatch()) {
	// 					break;
	// 				}
	// 			}
	// 		}
	// 	}

	// 	//score names
	// 	if(referencePatient.hasName()) {
	// 		for(HumanName name : referencePatient.getName()) {
	// 			if(name.hasGiven()) { refScorer.setGivenNameMatch(true); }
	// 			if(name.hasFamily()) { refScorer.setFamilyNameMatch(true); }

	// 			//if given and family name found, break out of for loop
	// 			if(refScorer.getGivenNameMatch() && refScorer.getFamilyNameMatch()) break;
	// 		}
	// 	}

	// 	//score gender
	// 	if(referencePatient.hasGender()) { refScorer.setGenderMatch(true); }

	// 	//score birthdate
	// 	if(referencePatient.hasBirthDate()) { refScorer.setBirthDateMatch(true);	}

	// 	//score addresses
	// 	if(referencePatient.hasAddress()) {
	// 		for(Address refAddress : referencePatient.getAddress()) {

	// 			//see if address has individual components
	// 			if(refAddress.hasLine()) {	refScorer.setAddressLineMatch(true); }
	// 			if(refAddress.hasCity()) { refScorer.setAddressCityMatch(true); }
	// 			if(refAddress.hasState()) { refScorer.setAddressStateMatch(true);}
	// 			if(refAddress.hasPostalCode()) { refScorer.setAddressPostalCodeMatch(true);}

	// 			//if all address components found, break out of for loop
	// 			if(refScorer.getAddressLineMatch() && refScorer.getAddressCityMatch() && refScorer.getAddressStateMatch() && refScorer.getAddressPostalCodeMatch()) {
	// 				break;
	// 			}

	// 		}
	// 	}

	// 	//score telecom
	// 	if(referencePatient.hasTelecom()) {
	// 		for (ContactPoint com : referencePatient.getTelecom()) {
	// 			if(com.getSystem().toCode().equals(ContactPoint.ContactPointSystem.PHONE.toCode())) {
	// 				refScorer.setPhoneNumberMatch(true);
	// 			}
	// 			else if(com.getSystem().toCode().equals(ContactPoint.ContactPointSystem.EMAIL.toCode())) {
	// 				refScorer.setEmailMatch(true);
	// 			}

	// 			//if phone and email  found, break out of for loop
	// 			if(refScorer.getPhoneNumberMatch() && refScorer.getEmailMatch()) break;

	// 		}
	// 	}

	// 	return refScorer;

	// }

	// private boolean passesProfileAssertion(IdentityMatchingScorer refScorer) {
	// 	Integer scoredWeight = refScorer.getMatchWeight();

	// 	if(assertIDIPatientProfile) {
	// 		return true;
	// 	}
	// 	else if(assertIDIPatientL0Profile) {
	// 		return scoredWeight >= 9;
	// 	}
	// 	else if(assertIDIPatientL1Profile) {
	// 		return scoredWeight >= 10;
	// 	}
	// 	else {
	// 		return false;
	// 	}
	// }


	//TESTING DOA
	private Bundle getPatientMatch(Patient refPatient, RequestDetails theRequestDetails) {
		Bundle patientBundle = new Bundle();
		SearchParameterMap searchMap = new SearchParameterMap();

		//Check for identifiers if present
		if (refPatient.hasIdentifier()) {
			// TokenOrListParam identifierParam = new TokenOrListParam();
			TokenAndListParam identifierParam = new TokenAndListParam();
			for (Identifier identifier : refPatient.getIdentifier()) {

				if (!identifier.hasValue()) {
					continue;
				}

				// custom search parameter for SSN4 matching
				// if (coding.getSystem().equals(IDENTITY_IDENTIFIER_SYSTEM) && coding.getCode().equals(IDENTITY_IDENTIFIER_SSN4_CODE)) {
				if (
					identifier.getType().getCoding().stream().anyMatch(
						coding -> coding.getSystem().equals(IDENTITY_IDENTIFIER_SSN4_SYSTEM) && coding.getCode().equals(IDENTITY_IDENTIFIER_SSN4_CODE)
					)
				) {
					searchMap.add(IDENTITY_IDENTIFIER_SSN4_PARAMETER, new TokenParam().setValue(identifier.getValue()));
					continue;
				}


				// search params for identifier type if present (":of-type" modifier)
				if (identifier.hasType() && identifier.getType().hasCoding()) {
					for (Coding coding : identifier.getType().getCoding()) {
						if (coding.hasSystem() && coding.hasCode()) {
							identifierParam.addAnd(new TokenParam()
								.setSystem(coding.getSystem())
								.setValue(coding.getCode() + "|*" + identifier.getValue())
								.setModifier(TokenParamModifier.OF_TYPE));
						}
					}
				}
				
				// search parameter for identifier system + value
				if (identifier.hasSystem()) {
					identifierParam.addAnd(new TokenParam().setSystem(identifier.getSystem()).setValue(identifier.getValue()));
				}
				
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
			searchMap.add(Patient.BIRTHDATE.getParamName(), new DateParam(ParamPrefixEnum.EQUAL, refPatient.getBirthDateElement().asStringValue()));
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


		// System.out.println("Search Map: " + searchMap.toNormalizedQueryString(FhirContextProvider.getFhirContext()));
		IBundleProvider patientResults = patientDao.search(searchMap, theRequestDetails);

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


}
