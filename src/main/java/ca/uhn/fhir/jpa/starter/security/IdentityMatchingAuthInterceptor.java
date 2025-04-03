package ca.uhn.fhir.jpa.starter.security;
import ca.uhn.fhir.interceptor.api.Hook;
import ca.uhn.fhir.interceptor.api.Interceptor;
import ca.uhn.fhir.interceptor.api.Pointcut;
import ca.uhn.fhir.jpa.starter.custom.SecurityUtil;
import ca.uhn.fhir.jpa.starter.security.models.SecurityConfig;
import ca.uhn.fhir.rest.api.Constants;
import ca.uhn.fhir.rest.api.server.RequestDetails;
import ca.uhn.fhir.rest.server.exceptions.AuthenticationException;

import com.auth0.jwk.Jwk;
import com.auth0.jwt.JWT;
import com.auth0.jwt.JWTVerifier;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTVerificationException;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.codec.binary.Base64;
import org.apache.commons.lang3.StringUtils;
import org.hl7.fhir.r4.model.OperationOutcome;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.X509EncodedKeySpec;
import java.util.List;
import java.util.Map;

@Interceptor
public class IdentityMatchingAuthInterceptor {
	private boolean enableAuthentication = false;
	private String bypassHeader;

	private String issuer;

	private String publicKey;
	private RSAPublicKey rsaPublicKey;
	private String introspectUrl;
	private String clientId;
	private String clientSecret;
	private List<String> protectedEndpoints;
	private List<String> publicEndpoints;

	private final Logger _logger = LoggerFactory.getLogger(IdentityMatchingAuthInterceptor.class);

	private SecurityConfig securityConfig;

	public IdentityMatchingAuthInterceptor(SecurityConfig securityConfig) {
		this.securityConfig = securityConfig;
		this.enableAuthentication = securityConfig.getEnableAuthentication();
		this.bypassHeader = securityConfig.getBypassHeader();
		this.issuer = securityConfig.getIssuer();
		this.publicKey = securityConfig.getPublicKey();
		this.introspectUrl = securityConfig.getIntrospectionUrl();
		this.clientId = securityConfig.getClientId();
		this.clientSecret = securityConfig.getClientSecret();
		this.protectedEndpoints = securityConfig.getProtectedEndpoints();
		this.publicEndpoints = securityConfig.getPublicEndpoints();
	}

	@Hook(Pointcut.SERVER_INCOMING_REQUEST_POST_PROCESSED)
	public boolean incomingRequestPostProcessed(RequestDetails details, HttpServletRequest request, HttpServletResponse response) throws AuthenticationException, IOException {

		if (!enableAuthentication) {
			return true;
		}

		boolean authenticated = false;

		//check for public access header, if not detected then proceed with authentication checks
		// if public access header is present, circumvent authentication and allow public access to all endpoints
		//
		//*** THIS IS JUST FOR RI TESTING, THIS SHOULD NOT BE INCLUDED IN A PRODUCTION SYSTEM ***
		String publicAccessHeader = request.getHeader(bypassHeader);
		if(publicAccessHeader == null) {

			// check if request path is an endpoint that needs validation
			// no values in proctedEndpoints means all endpoints require authentication
			if ((protectedEndpoints.size() == 0 || protectedEndpoints.contains(request.getRequestURI())) && !publicEndpoints.contains(request.getRequestURI())) {
				try {
					String authHeader = request.getHeader(Constants.HEADER_AUTHORIZATION);
					if (authHeader == null) {
						// throw new AuthenticationException("Not authorized (no authorization header found in request)");
						OperationOutcome outcome = new OperationOutcome();
						outcome.addIssue().setSeverity(OperationOutcome.IssueSeverity.ERROR).setCode(OperationOutcome.IssueType.SECURITY).setDiagnostics("Not authorized (no authorization header found in request)");
						response.setStatus(401);
						response.setContentType(Constants.CT_FHIR_JSON);
						details.getFhirContext().newJsonParser().encodeResourceToWriter(outcome, response.getWriter());
						return false;
					}
					if (!authHeader.startsWith(Constants.HEADER_AUTHORIZATION_VALPREFIX_BEARER)) {
						throw new AuthenticationException("Not authorized (authorization header does not contain a bearer token)");
					}

					//if a confidential client, use the client secret and introspect endpoint to validate token
					if (!StringUtils.isBlank(clientSecret) && !StringUtils.isBlank(introspectUrl)) {
						authenticated = introspectionCheck(authHeader);
					} else { //assume public client and validate using the token and supplied public key
						authenticated = validateToken(authHeader);
					}

				} catch (AuthenticationException ex) {
					_logger.error(ex.getMessage(), ex);
					_logger.info("Failed to authenticate request");
					throw ex;
				} catch (NoSuchAlgorithmException | InvalidKeySpecException ex) {
					throw new RuntimeException(ex);
				}
			} //otherwise, allow all
			else {
				authenticated = true;
			}
		}
		else { //public access header detected or a public access point was requested - allow request
			authenticated = true;
			_logger.info("The '" + bypassHeader + "' header was detected, ignoring security configuration.");
		}

		if(!authenticated) {
			throw new AuthenticationException("You are unauthorized to perform this request.");
		}

		return true;
	}

	private boolean introspectionCheck(String authHeader) throws JsonProcessingException {

		// Make an HTTP request to the introspection endpoint to validate the access token.
		RestTemplate restTemplate = new RestTemplate();
		HttpHeaders headers = new HttpHeaders();

		var token = authHeader.split(" ")[1];
		MultiValueMap<String, String> requestBody = new LinkedMultiValueMap<>();
		requestBody.add("token", token);
		requestBody.add("client_id", clientId);
		requestBody.add("client_secret", clientSecret);


		HttpEntity<MultiValueMap<String, String>> idpRequest = new HttpEntity<>(requestBody, headers);

		ResponseEntity<String> idpResponse = restTemplate.postForEntity(introspectUrl, idpRequest, String.class);

		if (idpResponse.getStatusCode() == HttpStatus.OK) {
			ObjectMapper objectMapper = new ObjectMapper();
			JsonNode introspectionResponse = objectMapper.readTree(idpResponse.getBody());
			return introspectionResponse.get("active").asBoolean();
		}
		else {
			return false;
		}
	}

	private boolean validateToken(String authHeader) throws NoSuchAlgorithmException, InvalidKeySpecException {

		var token = authHeader.split(" ")[1];

		//current set up for RSA 256, change as necessary
		// System.out.println("publicKey: " + publicKey);
		// byte[] publicBytes = Base64.decodeBase64(publicKey);
		// X509EncodedKeySpec keySpec = new X509EncodedKeySpec(publicBytes);
		// KeyFactory keyFactory = KeyFactory.getInstance("RSA");
		// RSAPublicKey publicKey = (RSAPublicKey) keyFactory.generatePublic(keySpec);

		try {
			
			DecodedJWT decodedJWT = JWT.decode(token);
			if (!decodedJWT.getIssuer().equals(SecurityUtil.resolveIssuer(securityConfig))) {
				throw new JWTVerificationException("Invalid issuer: Expected \"" + issuer + "\" but received \"" + decodedJWT.getIssuer() + "\"");
			}

			// TODO: implement caching
			// check if we already have the public key
			// if (rsaPublicKey == null) {

				// check if the public key was supplied in the configuration and attempt to use it
				// _logger.info("!StringUtils.isEmpty(publicKey): " + !StringUtils.isEmpty(publicKey));
				if (!StringUtils.isEmpty(publicKey)) {
					byte[] publicBytes = Base64.decodeBase64(publicKey);
					X509EncodedKeySpec keySpec = new X509EncodedKeySpec(publicBytes);
					KeyFactory keyFactory = KeyFactory.getInstance("RSA");
					rsaPublicKey = (RSAPublicKey) keyFactory.generatePublic(keySpec);
				} 
				
				// otherwise, attempt to retrieve the public key from the jwks endpoint
				else {
					HttpClient client = SecurityUtil.getHttpClient(securityConfig);

					HttpResponse<String> response;
					try {
						HttpRequest request = HttpRequest.newBuilder()
							.uri(URI.create(StringUtils.removeEnd(issuer, "/") + "/.well-known/openid-configuration"))
							.build();	
						response = client.send(request, HttpResponse.BodyHandlers.ofString());
					} catch (Exception e) {
						// TODO: handle exception
						System.err.println("HTTP Exception: " + e.getMessage());
						throw new RuntimeException(e);
					}
					String jwksUri = new ObjectMapper().readTree(response.body()).get("jwks_uri").asText();
					// TODO: maybe handle issue where jwks_uri might put to a different host than issuer
					URI jwksUriParsed = URI.create(jwksUri);
					URI issuerUri = URI.create(securityConfig.getIssuer());
					// Replace the host in jwksUri if it's different than the host in the configured issuer
					if (!jwksUriParsed.getHost().equals(issuerUri.getHost())) {
						jwksUri = new URI(
								issuerUri.getScheme(),
								issuerUri.getUserInfo(),
								issuerUri.getHost(),
								issuerUri.getPort(),
								jwksUriParsed.getPath(),
								jwksUriParsed.getQuery(),
								jwksUriParsed.getFragment()
						).toString();
					}

					// _logger.info("decodedJWT.getKeyId(): " + decodedJWT.getKeyId());
					// TODO: further investigate strategies for possibly using JwkProvider so that it can ignore SSL errors
					// JwkProvider provider = new UrlJwkProvider(new URL(jwksUri));
					// Jwk jwk = provider.get(decodedJWT.getKeyId());
					HttpClient jwkClient = SecurityUtil.getHttpClient(securityConfig);
					HttpRequest jwkRequest = HttpRequest.newBuilder()
						.uri(URI.create(jwksUri))
						.build();
					HttpResponse<String> jwkResponse = jwkClient.send(jwkRequest, HttpResponse.BodyHandlers.ofString());
					Map<String, Object> jwkMap = new ObjectMapper().convertValue(
							new ObjectMapper().readTree(jwkResponse.body()), 
							new TypeReference<Map<String, Object>>() {}
					);

					Jwk jwk = null;
					var keys = jwkMap.get("keys");
					if (keys instanceof Iterable) {
						for (Object key : (Iterable<?>) keys) {
							if (key instanceof Map) {
								@SuppressWarnings("unchecked")
								Map<String, Object> innerJwk = (Map<String, Object>) key;
								if (decodedJWT.getKeyId().equals(innerJwk.get("kid"))) {
									jwk = Jwk.fromValues(innerJwk);
									break;
								}
							}
						}
					}
					if (jwk == null) {
						throw new JWTVerificationException("Could not find matching JWK for key ID: " + decodedJWT.getKeyId());
					}

					rsaPublicKey = (RSAPublicKey) jwk.getPublicKey();
				}

				// if we still don't have the public key, throw an exception
				if (rsaPublicKey == null) {
					throw new JWTVerificationException("Could not determine public key");
				}

			// }			


			Algorithm algorithm = Algorithm.RSA256(rsaPublicKey, null);
			JWTVerifier verifier = JWT.require(algorithm)
				.withIssuer(SecurityUtil.resolveIssuer(securityConfig))
				.build(); //Reusable verifier instance
			DecodedJWT verifiedJwt = verifier.verify(token);

			return verifiedJwt != null;

		} catch (JWTVerificationException ex){
			throw new JWTVerificationException(ex.getMessage());
		} catch (Exception ex) {
			System.err.println("Exception: " + ex.getMessage());
			throw new RuntimeException(ex);
		}
	}
}
