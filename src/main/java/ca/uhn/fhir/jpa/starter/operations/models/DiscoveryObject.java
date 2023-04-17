package ca.uhn.fhir.jpa.starter.operations.models;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Getter;
import lombok.Setter;

@JsonIgnoreProperties(ignoreUnknown = true)
@Getter
@Setter
public class DiscoveryObject {
	private String[] udap_versions_supported;
	private String[] udap_profiles_supported;
	private String[] udap_authorization_extensions_supported;
	private String[] udap_authorization_extensions_required;
	private String[] udap_certifications_supported;
	private String[] udap_certifications_required;
	private String[] grant_types_supported;
	private String[] scopes_supported;
	private String authorization_endpoint;
	private String token_endpoint;
	private String[] token_endpoint_auth_methods_supported;
	private String[] token_endpoint_auth_signing_alg_values_supported;
	private String registration_endpoint;
	private String[] registration_endpoint_jwt_signing_alg_values_supported;
	private String signed_metadata;
}
