package ca.uhn.fhir.jpa.starter;

import ca.uhn.fhir.batch2.jobs.config.Batch2JobsConfig;
import ca.uhn.fhir.jpa.api.dao.DaoRegistry;
import ca.uhn.fhir.jpa.batch2.JpaBatch2Config;
import ca.uhn.fhir.jpa.starter.annotations.OnEitherVersion;
import ca.uhn.fhir.jpa.starter.common.FhirTesterConfig;
import ca.uhn.fhir.jpa.starter.identitymatching.CertInterceptor;
import ca.uhn.fhir.jpa.starter.identitymatching.DiscoveryInterceptor;
import ca.uhn.fhir.jpa.starter.mdm.MdmConfig;
import ca.uhn.fhir.jpa.starter.operations.IdentityMatching;
import ca.uhn.fhir.jpa.starter.security.IdentityMatchingAuthInterceptor;
import ca.uhn.fhir.jpa.starter.security.models.SecurityConfig;
import ca.uhn.fhir.jpa.subscription.channel.config.SubscriptionChannelConfig;
import ca.uhn.fhir.jpa.subscription.match.config.SubscriptionProcessorConfig;
import ca.uhn.fhir.jpa.subscription.match.config.WebsocketDispatcherConfig;
import ca.uhn.fhir.jpa.subscription.submit.config.SubscriptionSubmitterConfig;
import ca.uhn.fhir.rest.server.RestfulServer;
import ca.uhn.fhir.rest.server.interceptor.CorsInterceptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.AutowireCapableBeanFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.elasticsearch.ElasticsearchRestClientAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.servlet.ServletComponentScan;
import org.springframework.boot.web.servlet.ServletRegistrationBean;
import org.springframework.boot.web.servlet.support.SpringBootServletInitializer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Import;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.servlet.DispatcherServlet;

import java.util.Arrays;

@ServletComponentScan(basePackageClasses = {RestfulServer.class}) //, UnHapiServlet.class
@SpringBootApplication(exclude = {ElasticsearchRestClientAutoConfiguration.class})
@Import({
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

  public static void main(String[] args) {

    SpringApplication.run(Application.class, args);

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
  public ServletRegistrationBean hapiServletRegistration(RestfulServer restfulServer) {

	  //add custom operations
	  IdentityMatching identityMatcher = new IdentityMatching();
	  identityMatcher.setOrgDao(this.getDaoRegistry().getResourceDao("Patient"));
	  identityMatcher.setServerAddress(this.appProperties.getServer_address());
	  restfulServer.registerProviders(identityMatcher);

	  //register FAST security interceptors
	  DiscoveryInterceptor securityDiscoveryInterceptor = new DiscoveryInterceptor(appProperties, securityConfig);
		restfulServer.registerInterceptor(securityDiscoveryInterceptor);

	  IdentityMatchingAuthInterceptor authInterceptor = new IdentityMatchingAuthInterceptor(securityConfig.getEnableAuthentication(),
		  securityConfig.getIssuer(), securityConfig.getPublicKey(),
		  securityConfig.getIntrospectionUrl(), securityConfig.getClientId(), securityConfig.getClientSecret(),
		  securityConfig.getProtectedEndpoints(), securityConfig.getPublicEndpoints());
		restfulServer.registerInterceptor(authInterceptor);

		CertInterceptor certInterceptor = new CertInterceptor(appProperties, securityConfig);
		restfulServer.registerInterceptor(certInterceptor);	  
	  

	  //check if there is existing CORS configuration, if so add 'x-allow-public-access' to the allowed headers, otherwise create a new CORS interceptor
	  var existingCorsInterceptor = restfulServer.getInterceptorService().getAllRegisteredInterceptors().stream().filter(interceptor -> interceptor instanceof CorsInterceptor).findFirst().orElse(null);
	  if (existingCorsInterceptor != null) {
		  // Cast the interceptor to CorsInterceptor
		  CorsInterceptor corsInterceptor = (CorsInterceptor) existingCorsInterceptor;

		  // Add custom header to the existing CORS configuration
		  corsInterceptor.getConfig().addAllowedHeader("x-allow-public-access");
	  }
	  else {
		  // Define your CORS configuration
		  CorsConfiguration config = new CorsConfiguration();
		  config.addAllowedHeader("x-fhir-starter");
		  config.addAllowedHeader("Origin");
		  config.addAllowedHeader("Accept");
		  config.addAllowedHeader("X-Requested-With");
		  config.addAllowedHeader("Content-Type");
		  config.addAllowedHeader("x-allow-public-access");

		  config.addAllowedOrigin("*");

		  config.addExposedHeader("Location");
		  config.addExposedHeader("Content-Location");
		  config.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH"));

		  // Create the interceptor and register it
		  CorsInterceptor corsInterceptor = new CorsInterceptor(config);
		  restfulServer.registerInterceptor(corsInterceptor);
	  }

    ServletRegistrationBean servletRegistrationBean = new ServletRegistrationBean();
    beanFactory.autowireBean(restfulServer);
    servletRegistrationBean.setServlet(restfulServer);
    servletRegistrationBean.addUrlMappings("/fhir/*");
    servletRegistrationBean.setLoadOnStartup(2);

    return servletRegistrationBean;
  }

  @Bean
  public ServletRegistrationBean overlayRegistrationBean() {

    AnnotationConfigWebApplicationContext annotationConfigWebApplicationContext = new AnnotationConfigWebApplicationContext();
    annotationConfigWebApplicationContext.register(FhirTesterConfig.class);

    DispatcherServlet dispatcherServlet = new DispatcherServlet(
      annotationConfigWebApplicationContext);
    dispatcherServlet.setContextClass(AnnotationConfigWebApplicationContext.class);
    dispatcherServlet.setContextConfigLocation(FhirTesterConfig.class.getName());

    ServletRegistrationBean registrationBean = new ServletRegistrationBean();
    registrationBean.setServlet(dispatcherServlet);
    registrationBean.addUrlMappings("/*");
    registrationBean.setLoadOnStartup(1);
    return registrationBean;

  }
}
