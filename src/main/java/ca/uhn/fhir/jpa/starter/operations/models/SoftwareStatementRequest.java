package ca.uhn.fhir.jpa.starter.operations.models;

import org.apache.commons.lang3.StringUtils;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import lombok.Getter;
import lombok.Setter;


@JsonIgnoreProperties(ignoreUnknown = true)
public class SoftwareStatementRequest {

  @Setter
  private String client_name;
  public String getClient_name() {
    return (StringUtils.isNotBlank(client_name) ? client_name : "Identity Matching Client");
  }

  @Getter @Setter
  private String[] redirect_uris;

  @Getter @Setter
  private String[] contacts;

  @Setter
  private String logo_uri;
  public String getLogo_uri() {
    return (StringUtils.isNotBlank(logo_uri) ? logo_uri : "https://build.fhir.org/icon-fhir-16.png");
  }

  @Setter  
  private String[] grant_types;
  public String[] getGrant_types() {
    return (grant_types != null ? grant_types : new String[] { "client_credentials" });
  }

  @Setter
  private String[] response_types;
  public String[] getResponse_types() {
    return (response_types != null ? response_types : (grant_types[0] == "client_credentials") ? new String[]{} : new String[] { "code" });
  }

  @Getter @Setter
  private String scope;
  
}
