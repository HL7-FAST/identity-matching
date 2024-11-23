package ca.uhn.fhir.jpa.starter.operations.models;

import java.util.List;

import org.hl7.fhir.r4.model.Coding;

public record IdentifierRegistryEntry(
  String id,
  String name,
  String uri,
  List<Coding> coding
) {
}
