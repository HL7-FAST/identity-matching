package ca.uhn.fhir.jpa.starter.operations.models;


/**
 * The level of validation to perform on the Patient resource parameter when performing an identity match operation
 */
public enum IdentityMatchValidationLevel {
  
  /**
   * Requires that the Patient validates against an IDI-Patient profile specified in the meta.profile field.
   * If no profile is provided, the Patient will be validated against the base IDI-Patient profile: 
   * http://hl7.org/fhir/us/identity-matching/StructureDefinition/IDI-Patient
   */
  DEFAULT,

  /**
   * Validate the Patient resource against the most restrictive IDI-Patient profile specified in the meta.profile field.
   * If an expected IDI-Patient profile is not found, the validation will fail.
   */
  META_PROFILE,

  /**
   * Do not perform any validation of the Patient resource
   */
  NONE
  

}
