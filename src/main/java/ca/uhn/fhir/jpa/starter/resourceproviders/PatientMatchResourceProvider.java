package ca.uhn.fhir.jpa.starter.resourceproviders;

import ca.uhn.fhir.jpa.api.dao.IFhirResourceDao;
import ca.uhn.fhir.jpa.searchparam.SearchParameterMap;
import ca.uhn.fhir.rest.annotation.RequiredParam;
import ca.uhn.fhir.rest.annotation.Search;
import ca.uhn.fhir.rest.api.server.IBundleProvider;
import ca.uhn.fhir.rest.param.ReferenceParam;
import ca.uhn.fhir.rest.param.StringParam;
import ca.uhn.fhir.rest.server.IResourceProvider;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Organization;
import org.hl7.fhir.r4.model.Patient;

import java.util.Collections;
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
	public List<Patient> getPatientMatch(Patient refPatient) {
		Bundle retBundle = new Bundle();
		SearchParameterMap searchMap = new SearchParameterMap();
		searchMap.add(Patient.SP_GIVEN, new StringParam(refPatient.getName().get(0).getGivenAsSingleString()));

		IBundleProvider patientResults = patientDao.search(searchMap);

		patientResults.getResources(0, patientResults.size())
			.stream().map(Patient.class::cast)
			.forEach(o -> retBundle.addEntry(this.createBundleEntry(o)));

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
