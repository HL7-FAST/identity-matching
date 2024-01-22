package ca.uhn.fhir.jpa.starter.identitymatching;

import java.io.IOException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.util.Arrays;
import java.util.Base64;
import java.util.Date;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.auth0.jwt.JWT;
import com.auth0.jwt.JWTCreator.Builder;
import com.auth0.jwt.algorithms.Algorithm;
import com.fasterxml.jackson.databind.ObjectMapper;

import ca.uhn.fhir.interceptor.api.Hook;
import ca.uhn.fhir.interceptor.api.Interceptor;
import ca.uhn.fhir.interceptor.api.Pointcut;
import ca.uhn.fhir.jpa.starter.AppProperties;
import ca.uhn.fhir.jpa.starter.operations.models.ClientAssertionRequest;
import ca.uhn.fhir.jpa.starter.operations.models.ClientAssertionResponse;
import ca.uhn.fhir.jpa.starter.operations.models.SoftwareStatementRequest;
import ca.uhn.fhir.jpa.starter.operations.models.SoftwareStatementResponse;
import ca.uhn.fhir.jpa.starter.security.models.SecurityConfig;

@Interceptor
public class CertInterceptor {
  
  private final Logger _logger = LoggerFactory.getLogger(DiscoveryInterceptor.class);

	private SecurityConfig securityConfig;
	private AppProperties appProperties;

  public CertInterceptor(AppProperties appProperties, SecurityConfig securityConfig) {
    this.appProperties = appProperties;
    this.securityConfig = securityConfig;
  }

  @Hook(Pointcut.SERVER_INCOMING_REQUEST_PRE_PROCESSED)
  public boolean incomingRequestPreProcessed(HttpServletRequest theRequest, HttpServletResponse theResponse) throws IOException, UnrecoverableKeyException, KeyStoreException, NoSuchAlgorithmException, CertificateException {


    //
    // Check if the request is for /fhir/cert/software-statement
    //

    if (theRequest.getRequestURI().equals("/fhir/cert/software-statement") && theRequest.getMethod().equalsIgnoreCase("POST")) {

      _logger.info("Intercepted /fhir/cert/software-statement request, sending signed software statement.");

      ObjectMapper objectMapper = new ObjectMapper();
      String requestBody = theRequest.getReader().lines().collect(Collectors.joining(System.lineSeparator()));
      SoftwareStatementRequest ssRequest = objectMapper.readValue(requestBody, SoftwareStatementRequest.class);

      if (
        (ssRequest.getRedirect_uris() == null || ssRequest.getRedirect_uris().length == 0) && 
        Arrays.asList(ssRequest.getGrant_types()).contains("authorization_code")
      ) {
        theResponse.setStatus(400);
        theResponse.getWriter().write("redirect_uris is required for authorization_code grant_type");
        return false;
      }

      String fhirBase = StringUtils.removeEnd(appProperties.getServer_address(), "/");
      String issuer = StringUtils.removeEnd(securityConfig.getIssuer(), "/");
      X509Certificate certificate = CertUtil.getCert(securityConfig);
      Algorithm algorithm = CertUtil.getAlgorithm(securityConfig);
      Instant now = Instant.now();
      
      Builder builder = JWT.create()
        .withHeader(Map.of(
          "alg", algorithm.getName(),
					"x5c", new String[] { Base64.getEncoder().encodeToString(certificate.getEncoded()) }
        ))
        .withIssuer(fhirBase)
        .withSubject(fhirBase)
        .withAudience(issuer + "/connect/register")
        .withIssuedAt(Date.from(now))
        .withExpiresAt(Date.from(now.plusSeconds(300)))
        .withJWTId(UUID.randomUUID().toString())
        .withClaim("client_name", ssRequest.getClient_name())
        .withArrayClaim("contacts", ssRequest.getContacts())
        .withClaim("logo_uri", ssRequest.getLogo_uri())
        .withArrayClaim("grant_types", ssRequest.getGrant_types())
        .withArrayClaim("response_types", ssRequest.getResponse_types())
        .withClaim("token_endpoint_auth_method", "private_key_jwt")
        .withClaim("scope", ssRequest.getScope());

      if (!Arrays.asList(ssRequest.getGrant_types()).contains("client_credentials")) {
        builder.withArrayClaim("redirect_uris", ssRequest.getRedirect_uris());
      }

      String signedJWT = builder.sign(algorithm);


      SoftwareStatementResponse softwareStatement = new SoftwareStatementResponse();
      softwareStatement.setSoftware_statement(signedJWT);
      
      theResponse.setStatus(200);
      theResponse.setContentType("application/json");
      objectMapper.writeValue(theResponse.getWriter(), softwareStatement);

      return false;

    }


    //
    // Check if the request is for /fhir/cert/client-assertion
    //

    else if (theRequest.getRequestURI().equals("/fhir/cert/client-assertion") && theRequest.getMethod().equalsIgnoreCase("POST")) {

      _logger.info("Intercepted /fhir/cert/client-assertion request, sending signed client assertion.");

      ObjectMapper objectMapper = new ObjectMapper();
      String requestBody = theRequest.getReader().lines().collect(Collectors.joining(System.lineSeparator()));
      ClientAssertionRequest caRequest = objectMapper.readValue(requestBody, ClientAssertionRequest.class);

      if (caRequest.getClient_id() == null) {
        theResponse.setStatus(400);
        theResponse.getWriter().write("client_id is required");
        return false;
      }

      String issuer = StringUtils.removeEnd(securityConfig.getIssuer(), "/");
      X509Certificate certificate = CertUtil.getCert(securityConfig);
      Algorithm algorithm = CertUtil.getAlgorithm(securityConfig);
      Instant now = Instant.now();

      String signedJWT = JWT.create()
        .withHeader(Map.of(
          "alg", algorithm.getName(),
					"x5c", new String[] { Base64.getEncoder().encodeToString(certificate.getEncoded()) }
        ))
        .withIssuer(caRequest.getClient_id())
        .withSubject(caRequest.getClient_id())
        .withAudience(issuer + "/connect/token")
        .withIssuedAt(Date.from(now))
        .withExpiresAt(Date.from(now.plusSeconds(300)))
        .withNotBefore(now)
        .withJWTId(UUID.randomUUID().toString())
        .sign(algorithm);

      ClientAssertionResponse clientAssertion = new ClientAssertionResponse();
      clientAssertion.setClient_assertion(signedJWT);

      theResponse.setStatus(200);
      theResponse.setContentType("application/json");
      objectMapper.writeValue(theResponse.getWriter(), clientAssertion);

      return false;

    }

    return true;
  }

}
