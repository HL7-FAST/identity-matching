package ca.uhn.fhir.jpa.starter.security.models;

import java.util.List;

import lombok.Getter;
import lombok.Setter;

public class CertGenerateRequest {

  @Getter @Setter
  List<String> altNames;

  @Getter @Setter
  String password;
  
}
