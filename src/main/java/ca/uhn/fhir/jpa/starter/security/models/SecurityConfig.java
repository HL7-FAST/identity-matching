package ca.uhn.fhir.jpa.starter.security.models;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Configuration
@ConfigurationProperties(prefix = "security")
public class SecurityConfig {
	@Getter @Setter
	Boolean enableAuthentication = false;
	@Getter @Setter
	String issuer;
	@Getter @Setter
	String publicKey;
	@Getter @Setter
	String introspectionUrl;
	@Getter @Setter
	String clientId;
	@Getter @Setter
	String clientSecret;
	@Setter
	List<String> protectedEndpoints = new ArrayList<>();
	@Setter
	List<String> publicEndpoints = new ArrayList<>();

	@Getter @Setter
	String certFile;
	@Getter @Setter
	String certPassword;

	@Getter @Setter
	String defaultCertPassword = "udap-test";

	@Getter @Setter
	boolean fetchCert = true;

	@Getter @Setter
	int fetchCertRetryAttempts = 10;

	@Getter @Setter
	long fetchCertRetryDelay = 5000;

	@Getter @Setter
	String bypassHeader = "X-Allow-Public-Access";

	@Getter @Setter
	Boolean sslVerify = true;

	@Getter @Setter
	Boolean resolveIssuer = true;

	@Getter @Setter
	String authorizationEndpoint;
	@Getter @Setter
	String tokenEndpoint;
	@Getter @Setter
	String registrationEndpoint;
	@Getter @Setter
	String userinfoEndpoint;
	@Getter @Setter
	String revocationEndpoint;

	public List<String> getProtectedEndpoints() {
		if(this.protectedEndpoints.size() > 0) {
			return List.of(this.protectedEndpoints.get(0).split("[;]"));
		}
		return protectedEndpoints;
	}

	public List<String> getPublicEndpoints() {

		if(this.publicEndpoints.size() > 0) {
			return List.of(this.publicEndpoints.get(0).split("[;]"));
		}
		return publicEndpoints;
	}
}
