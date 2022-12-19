package ca.uhn.fhir.jpa.starter.resourceproviders;

import ca.uhn.fhir.jpa.searchparam.SearchParameterMap;
import ca.uhn.fhir.rest.annotation.RequiredParam;
import ca.uhn.fhir.rest.annotation.Search;
import ca.uhn.fhir.rest.param.ReferenceParam;
import ca.uhn.fhir.rest.param.StringParam;
import ca.uhn.fhir.rest.server.IResourceProvider;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.r4.model.Patient;

import java.util.Collections;
import java.util.List;

public class PatientMatchResourceProvider  implements IResourceProvider {

	@Override
	public Class<? extends IBaseResource> getResourceType() {
		return Patient.class;
	}

	@Search
	public List<Patient> getPatientMatch(Patient refPatient) {

		SearchParameterMap searchMap = new SearchParameterMap();

		searchMap.add(Patient.SP_GIVEN, new StringParam(refPatient.getName().get(0).getGivenAsSingleString()));

		return null;

	}

}
