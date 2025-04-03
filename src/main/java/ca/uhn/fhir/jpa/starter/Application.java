package ca.uhn.fhir.jpa.starter;

import ca.uhn.fhir.batch2.jobs.config.Batch2JobsConfig;
import ca.uhn.fhir.jpa.api.dao.DaoRegistry;
import ca.uhn.fhir.jpa.batch2.JpaBatch2Config;
import ca.uhn.fhir.jpa.starter.annotations.OnEitherVersion;
import ca.uhn.fhir.jpa.starter.cdshooks.StarterCdsHooksConfig;
import ca.uhn.fhir.jpa.starter.cr.StarterCrDstu3Config;
import ca.uhn.fhir.jpa.starter.cr.StarterCrR4Config;
import ca.uhn.fhir.jpa.starter.custom.DataInitializer;
import ca.uhn.fhir.jpa.starter.mdm.MdmConfig;
import ca.uhn.fhir.jpa.starter.operations.IdentityMatching;
import ca.uhn.fhir.jpa.starter.security.CertInterceptor;
import ca.uhn.fhir.jpa.starter.security.CertUtil;
import ca.uhn.fhir.jpa.starter.security.DiscoveryInterceptor;
import ca.uhn.fhir.jpa.starter.security.IdentityMatchingAuthInterceptor;
import ca.uhn.fhir.jpa.starter.security.models.SecurityConfig;
import ca.uhn.fhir.jpa.subscription.channel.config.SubscriptionChannelConfig;
import ca.uhn.fhir.jpa.subscription.match.config.SubscriptionProcessorConfig;
import ca.uhn.fhir.jpa.subscription.match.config.WebsocketDispatcherConfig;
import ca.uhn.fhir.jpa.subscription.submit.config.SubscriptionSubmitterConfig;
import ca.uhn.fhir.rest.server.RestfulServer;
import ca.uhn.fhir.rest.server.interceptor.CorsInterceptor;

import org.hl7.fhir.r4.model.Patient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.AutowireCapableBeanFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.elasticsearch.ElasticsearchRestClientAutoConfiguration;
import org.springframework.boot.autoconfigure.thymeleaf.ThymeleafAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.servlet.ServletComponentScan;
import org.springframework.boot.web.servlet.ServletRegistrationBean;
import org.springframework.boot.web.servlet.support.SpringBootServletInitializer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ResourceLoader;
import org.springframework.web.cors.CorsConfiguration;
import java.util.Arrays;

@ServletComponentScan(basePackageClasses = {RestfulServer.class})
@SpringBootApplication(exclude = {ElasticsearchRestClientAutoConfiguration.class, ThymeleafAutoConfiguration.class})
@Import({
	StarterCrR4Config.class,
	StarterCrDstu3Config.class,
	StarterCdsHooksConfig.class,
	SubscriptionSubmitterConfig.class,
	SubscriptionProcessorConfig.class,
	SubscriptionChannelConfig.class,
	WebsocketDispatcherConfig.class,
	MdmConfig.class,
	JpaBatch2Config.class,
	Batch2JobsConfig.class
})
public class Application extends SpringBootServletInitializer {

	@Autowired
	DaoRegistry daoRegistry;

	public DaoRegistry getDaoRegistry() {
		return this.daoRegistry;
	}

	@Autowired
	AppProperties appProperties;

	@Autowired
	SecurityConfig securityConfig;

	@Autowired
	ResourceLoader resourceLoader;

  public static void main(String[] args) {

    var ctx = SpringApplication.run(Application.class, args);

		// The server requires a valid certificate to be present for UDAP functionality and should not run otherwise.
		// So... check that we have a cert file available... this will throw an exception if there is a problem with the cert configuration
		// or if we cannot fetch a default cert from the UDAP security server configured in the security.issuer property
		try {
			SecurityConfig securityConfig = ctx.getBean(SecurityConfig.class);
			AppProperties appProperties = ctx.getBean(AppProperties.class);
			CertUtil.initializeCert(securityConfig, appProperties);
		} catch (Exception e) {
			ctx.close();
			throw new RuntimeException("Error during startup while initializing cert: " + e.getMessage(), e);
		}

    //Server is now accessible at eg. http://localhost:8080/fhir/metadata
    //UI is now accessible at http://localhost:8080/
  }

  @Override
  protected SpringApplicationBuilder configure(
    SpringApplicationBuilder builder) {
    return builder.sources(Application.class);
  }

  @Autowired
  AutowireCapableBeanFactory beanFactory;

  @Bean
  @Conditional(OnEitherVersion.class)
  public ServletRegistrationBean<RestfulServer> hapiServletRegistration(RestfulServer restfulServer) {

	  //add custom operations
	  IdentityMatching identityMatcher = new IdentityMatching(appProperties, this.getDaoRegistry().getResourceDao(Patient.class), resourceLoader);
	  restfulServer.registerProviders(identityMatcher);

	  //register FAST security interceptors
	  DiscoveryInterceptor securityDiscoveryInterceptor = new DiscoveryInterceptor(appProperties, securityConfig);
		restfulServer.registerInterceptor(securityDiscoveryInterceptor);

	  IdentityMatchingAuthInterceptor authInterceptor = new IdentityMatchingAuthInterceptor(securityConfig);
		restfulServer.registerInterceptor(authInterceptor);

		CertInterceptor certInterceptor = new CertInterceptor(appProperties, securityConfig);
		restfulServer.registerInterceptor(certInterceptor);	  
	  

	  //check if there is existing CORS configuration, if so add the security bypass header to the allowed headers, otherwise create a new CORS interceptor
	  var existingCorsInterceptor = restfulServer.getInterceptorService().getAllRegisteredInterceptors().stream().filter(interceptor -> interceptor instanceof CorsInterceptor).findFirst().orElse(null);
	  if (existingCorsInterceptor != null) {
		  // Cast the interceptor to CorsInterceptor
		  CorsInterceptor corsInterceptor = (CorsInterceptor) existingCorsInterceptor;

		  // Add custom header to the existing CORS configuration
		  corsInterceptor.getConfig().addAllowedHeader(securityConfig.getBypassHeader());
		  corsInterceptor.getConfig().addAllowedHeader(appProperties.getMatchValidationHeader());
	  }
	  else {
		  // Define your CORS configuration
		  CorsConfiguration config = new CorsConfiguration();
		  config.addAllowedHeader("x-fhir-starter");
		  config.addAllowedHeader("Origin");
		  config.addAllowedHeader("Accept");
		  config.addAllowedHeader("X-Requested-With");
		  config.addAllowedHeader("Content-Type");
		  config.addAllowedHeader(securityConfig.getBypassHeader());
		  config.addAllowedHeader(appProperties.getMatchValidationHeader());

		  config.addAllowedOrigin("*");

		  config.addExposedHeader("Location");
		  config.addExposedHeader("Content-Location");
		  config.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH"));

		  // Create the interceptor and register it
		  CorsInterceptor corsInterceptor = new CorsInterceptor(config);
		  restfulServer.registerInterceptor(corsInterceptor);
	  }

    ServletRegistrationBean<RestfulServer> servletRegistrationBean = new ServletRegistrationBean<RestfulServer>();
    beanFactory.autowireBean(restfulServer);
    servletRegistrationBean.setServlet(restfulServer);
    servletRegistrationBean.addUrlMappings("/fhir/*");
    servletRegistrationBean.setLoadOnStartup(2);

    return servletRegistrationBean;
  }


	// Ensure data is loaded when the application starts
  @Bean
  public DataInitializer dataInitializer() {
    return new DataInitializer();
  }

}
