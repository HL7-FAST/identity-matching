package ca.uhn.fhir.jpa.starter.operations.models;

import java.util.ArrayList;
import java.util.List;

import org.springframework.core.io.ResourceLoader;
import org.springframework.core.io.support.ResourcePatternUtils;

import lombok.Getter;
import lombok.Setter;
import java.io.IOException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

public class IdentifierRegistry {

  @Getter @Setter
  private List<IdentifierRegistryEntry> entries = new ArrayList<>();


  public IdentifierRegistry(ResourceLoader resourceLoader) {

    try {
      
      var resource = ResourcePatternUtils.getResourcePatternResolver(resourceLoader).getResource("classpath:identifier-registry.json");
			String resourceText = new String(resource.getInputStream().readAllBytes());

      ObjectMapper objectMapper = new ObjectMapper();
      this.entries = objectMapper.readValue(resourceText, new TypeReference<List<IdentifierRegistryEntry>>(){});
    } catch (IOException e) {
      e.printStackTrace();
      this.entries = new ArrayList<>();
    }
  }


  public boolean uriIsType(String uri, String type) {
    return this.entries.stream().anyMatch(
      entry -> entry.uri().equals(uri) && entry.coding().stream().anyMatch(coding -> coding.getCode().equals(type))
    );
  }


  public String getTypeFromUri(String uri) {
    return this.entries.stream()
      .filter(entry -> entry.uri().equals(uri))
      .map(entry -> entry.coding().stream().filter(coding -> coding.getSystem().equals("http://terminology.hl7.org/CodeSystem/v2-0203")).findFirst().get().getCode())
      .findFirst().orElse(null);
  }
  
}