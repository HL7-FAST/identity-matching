package ca.uhn.fhir.jpa.starter.resourceproviders;

import ca.uhn.fhir.jpa.api.dao.IFhirResourceDao;
import ca.uhn.fhir.jpa.searchparam.SearchParameterMap;
import ca.uhn.fhir.model.api.IQueryParameterOr;
import ca.uhn.fhir.model.base.composite.BaseIdentifierDt;
import ca.uhn.fhir.rest.annotation.RequiredParam;
import ca.uhn.fhir.rest.annotation.Search;
import ca.uhn.fhir.rest.api.server.IBundleProvider;
import ca.uhn.fhir.rest.param.*;
import ca.uhn.fhir.rest.server.IResourceProvider;
import com.mysql.cj.x.protobuf.MysqlxDatatypes;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.r4.model.*;
import org.hl7.fhir.r4.model.codesystems.ContactPointSystem;
import org.hl7.fhir.r4.model.codesystems.ContactPointUse;

import java.util.List;


public class PatientMatchResourceProvider  implements IResourceProvider {

	private String serverAddress;
	private IFhirResourceDao<Patient> patientDao;

	public void setOrgDao(IFhirResourceDao<Patient> patientDao) {
		this.patientDao = patientDao;
	}

	public void setServerAddress(String serverAddress) {
		this.serverAddress = serverAddress;
	}

	@Override
	public Class<? extends IBaseResource> getResourceType() {
		return Patient.class;
	}

	@Search
	public List<Patient> getPatientMatch(Patient refPatient, List<BaseIdentifierDt> baseIdentifierParams) {
		Bundle patientBundle = new Bundle();
		SearchParameterMap searchMap = new SearchParameterMap();

		//Check for identifiers if present
		if(baseIdentifierParams.size() > 0)
		{

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
			searchMap.add(Patient.SP_GIVEN, givenNameParam);
			searchMap.add(Patient.SP_FAMILY, familyNameParam);
		}

		//check for birthdate if present
		if(refPatient.hasBirthDate())
		{
			searchMap.add(Patient.SP_BIRTHDATE, new DateParam(refPatient.getBirthDateElement().getValueAsString()));
		}

		//check gender if present
		if(refPatient.hasGender())
		{
			searchMap.add(Patient.SP_GENDER, new StringParam(refPatient.getGenderElement().getValueAsString()));
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

			searchMap.add(Patient.SP_ADDRESS, lineParams);
			searchMap.add(Patient.SP_ADDRESS_CITY, cityParams);
			searchMap.add(Patient.SP_ADDRESS_STATE, stateParams);
			searchMap.add(Patient.SP_ADDRESS_POSTALCODE, postalCodeParams);
		}

		//check telecom if present
		if(refPatient.hasTelecom()) {
			StringOrListParam phoneParams = new StringOrListParam();
			StringOrListParam emailParams = new StringOrListParam();
			for(ContactPoint contact: refPatient.getTelecom()) {
				var system = contact.getSystemElement();
				if(system.equals(ContactPoint.ContactPointSystem.PHONE)
					&& (contact.getUseElement().equals(ContactPointUse.HOME) || contact.getUseElement().equals(ContactPointUse.MOBILE))) {
					phoneParams.addOr(new StringParam(contact.getValueElement().asStringValue()));
				}
				if(system.equals(ContactPoint.ContactPointSystem.EMAIL)) {
					emailParams.addOr(new StringParam(contact.getValueElement().asStringValue()));
				}
			}
			searchMap.add(Patient.SP_PHONE, phoneParams);
			searchMap.add(Patient.SP_EMAIL, phoneParams);
		}


		IBundleProvider patientResults = patientDao.search(searchMap);

		patientResults.getResources(0, patientResults.size())
			.stream().map(Patient.class::cast)
			.forEach(o -> patientBundle.addEntry(this.createBundleEntry(o)));

		return null;

	}

	private Bundle.BundleEntryComponent createBundleEntry(Patient patient) {
		Bundle.BundleEntryComponent entry = new Bundle.BundleEntryComponent();
		entry.setResource(patient);
		if (this.serverAddress != null && !this.serverAddress.isEmpty()) {
			try {
				// Setting the fullUrl only works if there is a server address, which is only set when deploying with an application.yml file; it is not present when debugging in IntelliJ/Eclipse.
				String fullUrl = this.serverAddress;
				fullUrl += (!fullUrl.endsWith("/") ? "/" : "") + patient.getId();
				entry.setFullUrl(fullUrl);
			} catch (Exception ex) { }
		}
		return entry;
	}
}
