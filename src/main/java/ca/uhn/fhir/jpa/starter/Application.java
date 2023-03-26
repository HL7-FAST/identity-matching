package ca.uhn.fhir.jpa.starter;

import ca.uhn.fhir.batch2.jobs.config.Batch2JobsConfig;
import ca.uhn.fhir.jpa.api.dao.DaoRegistry;
import ca.uhn.fhir.jpa.batch2.JpaBatch2Config;
import ca.uhn.fhir.jpa.starter.annotations.OnEitherVersion;
import ca.uhn.fhir.jpa.starter.common.FhirTesterConfig;
import ca.uhn.fhir.jpa.starter.identitymatching.UnHapiServlet;
import ca.uhn.fhir.jpa.starter.mdm.MdmConfig;
import ca.uhn.fhir.jpa.starter.operations.IdentityMatching;
import ca.uhn.fhir.jpa.starter.operations.models.CustomHapiProperties;
import ca.uhn.fhir.jpa.starter.resourceproviders.PatientMatchResourceProvider;
import ca.uhn.fhir.jpa.subscription.channel.config.SubscriptionChannelConfig;
import ca.uhn.fhir.jpa.subscription.match.config.SubscriptionProcessorConfig;
import ca.uhn.fhir.jpa.subscription.match.config.WebsocketDispatcherConfig;
import ca.uhn.fhir.jpa.subscription.submit.config.SubscriptionSubmitterConfig;
import ca.uhn.fhir.rest.server.RestfulServer;
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
import org.springframework.web.servlet.DispatcherServlet;

@ServletComponentScan(basePackageClasses = {RestfulServer.class, UnHapiServlet.class})
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
	CustomHapiProperties customHapiProperties;

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
	public ServletRegistrationBean notHapiServletRegistration(){

//		ServletRegistrationBean servletRegistrationBean = new ServletRegistrationBean<>(new UnHapiServlet());
//		servletRegistrationBean.setLoadOnStartup(1);

		ServletRegistrationBean servletRegistrationBean = new ServletRegistrationBean<>();
		DispatcherServlet dispatcherServlet = new DispatcherServlet();
		AnnotationConfigWebApplicationContext applicationContext = new AnnotationConfigWebApplicationContext();
		applicationContext.register(UnHapiServlet.class);
		dispatcherServlet.setApplicationContext(applicationContext);
		servletRegistrationBean.setServlet(dispatcherServlet);
		servletRegistrationBean.setLoadOnStartup(1);

		return servletRegistrationBean;
	}

  @Bean
  @Conditional(OnEitherVersion.class)
  public ServletRegistrationBean hapiServletRegistration(RestfulServer restfulServer) {

	  //add custom operations
	  IdentityMatching identityMatcher = new IdentityMatching();
	  identityMatcher.setOrgDao(this.getDaoRegistry().getResourceDao("Patient"));
	  identityMatcher.setServerAddress(this.customHapiProperties.getFhirBase());
	  restfulServer.registerProviders(identityMatcher);

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
