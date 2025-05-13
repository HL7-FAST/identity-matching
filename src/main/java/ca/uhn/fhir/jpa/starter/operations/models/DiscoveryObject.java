package ca.uhn.fhir.jpa.starter.operations.models;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
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
	private String userinfo_endpoint;
	private String revocation_endpoint;
	private String[] token_endpoint_auth_methods_supported;
	private String[] token_endpoint_auth_signing_alg_values_supported;
	private String registration_endpoint;
	private String[] registration_endpoint_jwt_signing_alg_values_supported;
	private String signed_metadata;

	public String[] getUdap_versions_supported() { return udap_versions_supported; }
	public void setUdap_versions_supported(String[] system) { this.udap_versions_supported = system; }

	public String[] getUdap_profiles_supported() { return udap_profiles_supported; }
	public void setUdap_profiles_supported(String []system) { this.udap_profiles_supported = system; }

	public String[] getUdap_authorization_extensions_supported() { return udap_authorization_extensions_supported; }
	public void setUdap_authorization_extensions_supported(String[] system) { this.udap_authorization_extensions_supported = system; }

	public String[] getUdap_authorization_extensions_required() { return udap_authorization_extensions_required; }
	public void setUdap_authorization_extensions_required(String[] system) { this.udap_authorization_extensions_required = system; }

	public String[] getUdap_certifications_supported() { return udap_certifications_supported; }
	public void setUdap_certifications_supported(String[] system) { this.udap_certifications_supported = system; }

	public String[] getUdap_certifications_required() { return udap_certifications_required; }
	public void setUdap_certifications_required(String[] system) { this.udap_certifications_required = system; }

	public String[] getGrant_types_supported() { return grant_types_supported; }
	public void setGrant_types_supported(String[] system) { this.grant_types_supported = system; }

	public String[] getScopes_supported() { return scopes_supported; }
	public void setScopes_supported(String[] system) { this.scopes_supported = system; }

	public String getAuthorization_endpoint() { return authorization_endpoint; }
	public void setAuthorization_endpoint(String system) { this.authorization_endpoint = system; }

	public String getToken_endpoint() { return token_endpoint; }
	public void setToken_endpoint(String endpoint) { this.token_endpoint = endpoint; }

	public String getUserinfo_endpoint() { return userinfo_endpoint; }
	public void setUserinfo_endpoint(String endpoint) { this.userinfo_endpoint = endpoint; }

	public String getRevocation_endpoint() { return revocation_endpoint; }
	public void setRevocation_endpoint(String endpoint) { this.revocation_endpoint = endpoint; }

	public String[] getToken_endpoint_auth_methods_supported() { return token_endpoint_auth_methods_supported; }
	public void setToken_endpoint_auth_methods_supported(String[] system) { this.token_endpoint_auth_methods_supported = system; }

	public String[] getToken_endpoint_auth_signing_alg_values_supported() { return token_endpoint_auth_signing_alg_values_supported; }
	public void setToken_endpoint_auth_signing_alg_values_supported(String[] system) { this.token_endpoint_auth_signing_alg_values_supported = system; }

	public String getRegistration_endpoint() { return registration_endpoint; }
	public void setRegistration_endpoint(String system) { this.registration_endpoint = system; }

	public String[] getRegistration_endpoint_jwt_signing_alg_values_supported() { return registration_endpoint_jwt_signing_alg_values_supported; }
	public void setRegistration_endpoint_jwt_signing_alg_values_supported(String[] system) { this.registration_endpoint_jwt_signing_alg_values_supported = system; }

	public String getSigned_metadata() { return signed_metadata; }
	public void setSigned_metadata(String system) { this.signed_metadata = system; }

}
