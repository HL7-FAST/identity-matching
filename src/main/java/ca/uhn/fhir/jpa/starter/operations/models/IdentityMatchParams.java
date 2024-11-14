package ca.uhn.fhir.jpa.starter.operations.models;

import org.hl7.fhir.r4.model.BooleanType;
import org.hl7.fhir.r4.model.IntegerType;
import org.hl7.fhir.r4.model.Patient;

import lombok.Getter;
import lombok.Setter;

public class IdentityMatchParams {

  @Getter @Setter
  Patient patient;

  @Getter @Setter
  BooleanType onlyCertainMatches = new BooleanType(false);

  @Getter @Setter
  IntegerType count;
  
}
