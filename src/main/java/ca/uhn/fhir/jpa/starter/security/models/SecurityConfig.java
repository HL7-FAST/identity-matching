package ca.uhn.fhir.jpa.starter.security.models;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;

@ConfigurationProperties(prefix = "security")
@Configuration
@EnableConfigurationProperties
public class SecurityConfig {
	boolean enableAuthentication;
	String introspectionUrl;
	String clientId;
	String clientSecret;
	List<String> protectedEndpoints = new ArrayList<>();

	public boolean isEnableAuthentication() { return enableAuthentication; }
	public void setEnableAuthentication(boolean enableAuthentication) { this.enableAuthentication = enableAuthentication; }

	public String getIntrospectionUrl() { return introspectionUrl; }
	public void setIntrospectionUrl(String introspectionUrl) { this.introspectionUrl = introspectionUrl;}

	public String getClientId() { return clientId; }
	public void setClientId(String clientId) { this.clientId = clientId; }

	public String getClientSecret() { return clientSecret; }
	public void setClientSecret(String clientSecret) { this.clientSecret = clientSecret; }

	public List<String> getProtectedEndpoints() { return protectedEndpoints; }
	public void setProtectedEndpoints(List<String> protectedEndpoints) { this.protectedEndpoints = protectedEndpoints; }
}
