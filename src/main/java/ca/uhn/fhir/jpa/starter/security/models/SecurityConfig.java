package ca.uhn.fhir.jpa.starter.security.models;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import java.util.ArrayList;
import java.util.List;

@Configuration
public class SecurityConfig {
	@Value("${enable-authentication}")
	boolean enableAuthentication;
	@Value("${introspection-url}")
	String introspectionUrl;
	@Value("${client-id}")
	String clientId;
	@Value("${client-secret}")
	String clientSecret;
	@Value("${protected-endpoints}")
	List<String> protectedEndpoints = new ArrayList<>();
	@Value("${public-endpoints}")
	List<String> publicEndpoints = new ArrayList<>();

	public boolean isEnableAuthentication() { return enableAuthentication; }

	public String getIntrospectionUrl() { return introspectionUrl; }

	public String getClientId() { return clientId; }

	public String getClientSecret() { return clientSecret; }

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
