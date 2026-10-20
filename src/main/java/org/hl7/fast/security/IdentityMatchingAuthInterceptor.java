package org.hl7.fast.security;
import ca.uhn.fhir.interceptor.api.Hook;
import ca.uhn.fhir.interceptor.api.Interceptor;
import ca.uhn.fhir.interceptor.api.Pointcut;
import org.hl7.fast.common.SecurityUtil;
import org.hl7.fast.security.models.SecurityConfig;
import ca.uhn.fhir.rest.api.Constants;
import ca.uhn.fhir.rest.api.server.RequestDetails;
import ca.uhn.fhir.rest.server.exceptions.AuthenticationException;

import com.auth0.jwk.InvalidPublicKeyException;
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
import io.opentelemetry.api.trace.Span;
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
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import jakarta.servlet.http.HttpServletRequest;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
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
	static final String ACCESS_OUTCOME_ATTRIBUTE = "udap.access.outcome";

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
	public boolean incomingRequestPostProcessed(RequestDetails details, HttpServletRequest request) throws AuthenticationException {

		if (!enableAuthentication) {
			return record(AccessOutcome.AUTH_DISABLED, null, request);
		}

		//check for public access header, if not detected then proceed with authentication checks
		// if public access header is present, circumvent authentication and allow public access to all endpoints
		//
		//*** THIS IS JUST FOR RI TESTING, THIS SHOULD NOT BE INCLUDED IN A PRODUCTION SYSTEM ***
		String publicAccessHeader = request.getHeader(bypassHeader);
		if (publicAccessHeader != null) {
			_logger.info("The '{}' header was detected, ignoring security configuration.", bypassHeader);
			return record(AccessOutcome.BYPASS_HEADER, null, request);
		}

		// check if request path is an endpoint that needs validation
		// no values in proctedEndpoints means all endpoints require authentication
		String uri = request.getRequestURI();
		if (publicEndpoints.contains(uri)) {
			return record(AccessOutcome.PUBLIC_ENDPOINT, null, request);
		}
		if (!protectedEndpoints.isEmpty() && !protectedEndpoints.contains(uri)) {
			return record(AccessOutcome.UNPROTECTED_ENDPOINT, null, request);
		}

		String authHeader = request.getHeader(Constants.HEADER_AUTHORIZATION);
		if (authHeader == null) {
			record(AccessOutcome.MISSING_TOKEN, null, request);
			throw unauthorized("Not authorized (no authorization header found in request)", "Bearer");
		}
		if (!authHeader.startsWith(Constants.HEADER_AUTHORIZATION_VALPREFIX_BEARER)) {
			record(AccessOutcome.NOT_BEARER, null, request);
			throw unauthorized("Not authorized (authorization header does not contain a bearer token)", "Bearer error=\"invalid_request\"");
		}
		String token = authHeader.substring(Constants.HEADER_AUTHORIZATION_VALPREFIX_BEARER.length()).trim();

		//if a confidential client, use the client secret and introspect endpoint to validate token
		boolean introspect = !StringUtils.isBlank(clientSecret) && !StringUtils.isBlank(introspectUrl);
		try {
			if (introspect) {
				JsonNode introspection = introspectionCheck(token);
				String introspectedClient = introspection.path("client_id").asText(null);
				if (!introspection.path("active").asBoolean()) {
					record(AccessOutcome.INTROSPECTION_INACTIVE, introspectedClient, request);
					throw unauthorized("Not authorized (token is not active)", "Bearer error=\"invalid_token\"");
				}
				return record(AccessOutcome.INTROSPECTION_OK, introspectedClient, request);
			}
			//assume public client and validate using the token and supplied public key
			DecodedJWT jwt = validateToken(token);
			String jwtClient = jwt.getClaim("client_id").asString();
			return record(AccessOutcome.JWT_OK, jwtClient != null ? jwtClient : jwt.getSubject(), request);
		} catch (AuthenticationException ex) {
			throw ex;
		} catch (AccessException ex) {
			record(ex.outcome, null, request);
			if (ex.outcome.isError()) {
				throw ex;
			}
			throw unauthorized("Not authorized (" + ex.getMessage() + ")", "Bearer error=\"invalid_token\"");
		} catch (RuntimeException ex) {
			record(introspect ? AccessOutcome.INTROSPECTION_OTHER : AccessOutcome.JWT_OTHER, null, request);
			throw ex;
		}
	}

	/** The single place an access decision is recorded: on the request span and in the log. Path only, never the query string. */
	private boolean record(AccessOutcome outcome, String clientId, HttpServletRequest request) {
		Span.current().setAttribute(ACCESS_OUTCOME_ATTRIBUTE, outcome.name().toLowerCase());
		_logger.info("access decision={} method={} reason={} client_id={} path={}", outcome.decision, outcome.method,
				outcome.reason, clientId != null ? clientId : "-", request.getRequestURI());
		return true;
	}

	/** 401 with a security-coded OperationOutcome and the RFC 6750 challenge header. */
	private static AuthenticationException unauthorized(String message, String challenge) {
		OperationOutcome outcome = new OperationOutcome();
		outcome.addIssue().setSeverity(OperationOutcome.IssueSeverity.ERROR).setCode(OperationOutcome.IssueType.SECURITY).setDiagnostics(message);
		AuthenticationException ex = new AuthenticationException(message);
		ex.setOperationOutcome(outcome);
		ex.addResponseHeader("WWW-Authenticate", challenge);
		return ex;
	}

	/** Carries a classified outcome out of the token checks so the hook records it exactly once. */
	static final class AccessException extends RuntimeException {
		final AccessOutcome outcome;

		AccessException(AccessOutcome outcome, String message) {
			super(message);
			this.outcome = outcome;
		}

		AccessException(AccessOutcome outcome, Throwable cause) {
			super(cause.getMessage(), cause);
			this.outcome = outcome;
		}
	}

	private JsonNode introspectionCheck(String token) {

		// Make an HTTP request to the introspection endpoint to validate the access token.
		RestTemplate restTemplate = new RestTemplate();
		HttpHeaders headers = new HttpHeaders();

		MultiValueMap<String, String> requestBody = new LinkedMultiValueMap<>();
		requestBody.add("token", token);
		requestBody.add("client_id", clientId);
		requestBody.add("client_secret", clientSecret);


		HttpEntity<MultiValueMap<String, String>> idpRequest = new HttpEntity<>(requestBody, headers);

		ObjectMapper objectMapper = new ObjectMapper();
		try {
			ResponseEntity<String> idpResponse = restTemplate.postForEntity(introspectUrl, idpRequest, String.class);

			if (idpResponse.getStatusCode() == HttpStatus.OK) {
				return objectMapper.readTree(idpResponse.getBody());
			}
			else {
				return objectMapper.createObjectNode().put("active", false);
			}
		} catch (RestClientException | JsonProcessingException ex) {
			_logger.warn("Token introspection failed: {}", ex.toString());
			throw new AccessException(AccessOutcome.INTROSPECTION_FAILURE, ex);
		}
	}

	private DecodedJWT validateToken(String token) {

		//current set up for RSA 256, change as necessary
		// System.out.println("publicKey: " + publicKey);
		// byte[] publicBytes = Base64.decodeBase64(publicKey);
		// X509EncodedKeySpec keySpec = new X509EncodedKeySpec(publicBytes);
		// KeyFactory keyFactory = KeyFactory.getInstance("RSA");
		// RSAPublicKey publicKey = (RSAPublicKey) keyFactory.generatePublic(keySpec);

		try {
			
			DecodedJWT decodedJWT = JWT.decode(token);
			if (!SecurityUtil.resolveIssuer(securityConfig).equals(decodedJWT.getIssuer())) {
				throw new AccessException(AccessOutcome.JWT_ISSUER_MISMATCH, "Invalid issuer: Expected \"" + issuer + "\" but received \"" + decodedJWT.getIssuer() + "\"");
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
					} catch (IOException | InterruptedException e) {
						_logger.warn("Discovery document fetch failed: {}", e.toString());
						throw new AccessException(AccessOutcome.JWT_DEPENDENCY_FAILURE, e);
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
						throw new AccessException(AccessOutcome.JWT_UNKNOWN_KEY, "Could not find matching JWK for key ID: " + decodedJWT.getKeyId());
					}

					rsaPublicKey = (RSAPublicKey) jwk.getPublicKey();
				}

				// if we still don't have the public key, throw an exception
				if (rsaPublicKey == null) {
					throw new AccessException(AccessOutcome.JWT_OTHER, "Could not determine public key");
				}

			// }			


			Algorithm algorithm = Algorithm.RSA256(rsaPublicKey, null);
			JWTVerifier verifier = JWT.require(algorithm)
				.withIssuer(SecurityUtil.resolveIssuer(securityConfig))
				.build(); //Reusable verifier instance
			return verifier.verify(token);

		} catch (AccessException ex) {
			throw ex;
		} catch (JWTVerificationException ex) {
			throw new AccessException(AccessOutcome.fromJwtException(ex), ex);
		} catch (IOException | InterruptedException ex) {
			_logger.warn("JWKS fetch failed: {}", ex.toString());
			throw new AccessException(AccessOutcome.JWT_DEPENDENCY_FAILURE, ex);
		} catch (URISyntaxException | NoSuchAlgorithmException | InvalidKeySpecException | InvalidPublicKeyException | RuntimeException ex) {
			_logger.warn("Token validation failed: {}", ex.toString());
			throw new AccessException(AccessOutcome.JWT_OTHER, ex);
		}
	}
}
