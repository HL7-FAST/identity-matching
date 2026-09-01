package org.hl7.fast.common;

import java.nio.charset.StandardCharsets;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fast.config.IdentityMatchingProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.core.io.support.ResourcePatternUtils;
import org.springframework.util.FileCopyUtils;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.jpa.api.config.JpaStorageSettings;
import ca.uhn.fhir.jpa.api.dao.DaoRegistry;
import ca.uhn.fhir.jpa.api.dao.IFhirResourceDao;
import ca.uhn.fhir.jpa.starter.AppProperties;
import ca.uhn.fhir.rest.api.server.SystemRequestDetails;
import jakarta.annotation.PostConstruct;

// Ensure data is loaded when the application starts
@Component
public class DataInitializer {

  private static final Logger logger = LoggerFactory.getLogger(DataInitializer.class);

  @Autowired
  private FhirContext fhirContext;

  @Autowired
  private DaoRegistry daoRegistry;

  @Autowired
  private AppProperties appProperties;

  @Autowired
  private IdentityMatchingProperties identityMatchingProperties;

  @Autowired
  private ResourceLoader resourceLoader;

  @Autowired
  private JpaStorageSettings storageSettings;


  @PostConstruct
  public void initializeData() {

    if (identityMatchingProperties.getInitialData() == null || identityMatchingProperties.getInitialData().isEmpty()) {
      return;
    }

    logger.info("Initializing data");

    // Disable referential integrity checks so that resources can be loaded in any order
    storageSettings.setEnforceReferentialIntegrityOnWrite(false);

    for (String directoryPath : identityMatchingProperties.getInitialData()) {
      logger.info("Loading resources from directory: " + directoryPath);

      Resource[] resources = null;

      try {
        resources = ResourcePatternUtils.getResourcePatternResolver(resourceLoader).getResources("classpath:" + directoryPath + "/**/*.json");  
      } catch (Exception e) {
        logger.error("Error loading resources from directory: " + directoryPath, e);
        continue;
      }

      for (Resource resource : resources) {
        try {
          String resourceText = new String(FileCopyUtils.copyToByteArray(resource.getInputStream()), StandardCharsets.UTF_8);

          IBaseResource fhirResource = fhirContext.newJsonParser().parseResource(resourceText);

          IFhirResourceDao<IBaseResource> dao = daoRegistry.getResourceDao(fhirResource);
          dao.update(fhirResource, new SystemRequestDetails());
          logger.info("Loaded resource: " + resource.getFilename());
        } catch (Exception e) {
          logger.error("Error loading resource: " + resource.getFilename(), e);
        }
      }

    }

    // Re-enable referential integrity checks if they were previously enabled
    storageSettings.setEnforceReferentialIntegrityOnWrite(appProperties.getEnforce_referential_integrity_on_write());

  }

}
