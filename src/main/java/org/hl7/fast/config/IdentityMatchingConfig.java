package org.hl7.fast.config;

import ca.uhn.fhir.jpa.api.dao.DaoRegistry;
import ca.uhn.fhir.jpa.starter.AppProperties;
import ca.uhn.fhir.rest.server.RestfulServer;
import ca.uhn.fhir.rest.server.interceptor.CorsInterceptor;

import org.hl7.fast.operations.IdentityMatching;
import org.hl7.fast.security.CertInterceptor;
import org.hl7.fast.security.CertUtil;
import org.hl7.fast.security.DiscoveryInterceptor;
import org.hl7.fast.security.IdentityMatchingAuthInterceptor;
import org.hl7.fast.security.models.SecurityConfig;
import org.hl7.fhir.r4.model.Patient;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ResourceLoader;

import jakarta.annotation.PostConstruct;

import java.util.Optional;

/**
 * Wires the custom operations and interceptors into the RestfulServer.
 */
@Configuration
public class IdentityMatchingConfig {

	private final RestfulServer restfulServer;
	private final AppProperties appProperties;
	private final IdentityMatchingProperties identityMatchingProperties;
	private final SecurityConfig securityConfig;
	private final DaoRegistry daoRegistry;
	private final ResourceLoader resourceLoader;
	private final Optional<CorsInterceptor> corsInterceptor;

	public IdentityMatchingConfig(RestfulServer restfulServer, AppProperties appProperties,
			IdentityMatchingProperties identityMatchingProperties, SecurityConfig securityConfig, DaoRegistry daoRegistry,
			ResourceLoader resourceLoader, Optional<CorsInterceptor> corsInterceptor) {
		this.restfulServer = restfulServer;
		this.appProperties = appProperties;
		this.identityMatchingProperties = identityMatchingProperties;
		this.securityConfig = securityConfig;
		this.daoRegistry = daoRegistry;
		this.resourceLoader = resourceLoader;
		this.corsInterceptor = corsInterceptor;
	}

	@PostConstruct
	public void configureFastServer() {
		// UDAP requires a valid certificate, so fail startup without one.
		try {
			CertUtil.initializeCert(securityConfig, appProperties);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException("Startup interrupted while initializing cert", e);
		} catch (Exception e) {
			throw new IllegalStateException("Error during startup while initializing cert: " + e.getMessage(), e);
		}

		// add custom operations
		IdentityMatching identityMatcher = new IdentityMatching(appProperties, identityMatchingProperties,
				daoRegistry.getResourceDao(Patient.class), resourceLoader);
		restfulServer.registerProviders(identityMatcher);

		// register security interceptors
		restfulServer.registerInterceptor(new DiscoveryInterceptor(appProperties, securityConfig));
		restfulServer.registerInterceptor(new IdentityMatchingAuthInterceptor(securityConfig));
		restfulServer.registerInterceptor(new CertInterceptor(appProperties, securityConfig));

		// Allow the custom headers through CORS preflight.
		corsInterceptor.ifPresent(cors -> {
			cors.getConfig().addAllowedHeader(securityConfig.getBypassHeader());
			cors.getConfig().addAllowedHeader(identityMatchingProperties.getMatchValidationHeader());
		});
	}
}
