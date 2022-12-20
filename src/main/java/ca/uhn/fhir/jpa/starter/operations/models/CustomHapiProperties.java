package ca.uhn.fhir.jpa.starter.operations.models;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@ConfigurationProperties(prefix = "hapi")
@Configuration
@EnableConfigurationProperties
public class CustomHapiProperties {
	String fhirBase;

	public String getFhirBase() {
		return fhirBase;
	}

	public void setFhirBase(String fhirBase) {
		this.fhirBase = fhirBase;
	}
}
