package ca.uhn.fhir.jpa.starter.security;
import ca.uhn.fhir.interceptor.api.Hook;
import ca.uhn.fhir.interceptor.api.Interceptor;
import ca.uhn.fhir.interceptor.api.Pointcut;
import ca.uhn.fhir.rest.api.Constants;
import ca.uhn.fhir.rest.api.server.RequestDetails;
import com.auth0.jwt.JWT;
import com.auth0.jwt.JWTVerifier;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTVerificationException;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.codec.binary.Base64;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import javax.naming.AuthenticationException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.X509EncodedKeySpec;
import java.util.List;

@Interceptor
public class IdentityMatchingAuthInterceptor {
	private boolean enableAuthentication = false;

	private String issuer;

	private String publicKey;
	private String introspectUrl;
	private String clientId;
	private String clientSecret;
	private List<String> protectedEndpoints;
	private List<String> publicEndpoints;

	private final Logger _logger = LoggerFactory.getLogger(IdentityMatchingAuthInterceptor.class);
	private final String allowPublicAccessHeader = "X-Allow-Public-Access";

	public IdentityMatchingAuthInterceptor(boolean enableAuthentication, String issuer, String publicKey, String introspectUrl, String clientId, String clientSecret, List<String> protectedEndpoints, List<String> publicEndpoints) {
		this.enableAuthentication = enableAuthentication;
		this.issuer = issuer;
		this.publicKey = publicKey;
		this.introspectUrl = introspectUrl;
		this.clientId = clientId;
		this.clientSecret = clientSecret;
		this.protectedEndpoints = protectedEndpoints;
		this.publicEndpoints = publicEndpoints;
	}

	@Hook(Pointcut.SERVER_INCOMING_REQUEST_POST_PROCESSED)
	public boolean incomingRequestPostProcessed(RequestDetails details, HttpServletRequest request, HttpServletResponse response) throws AuthenticationException, IOException {
		boolean authenticated = false;

		//check for public access header, if not detected then proceed with authentication checks
		// if public access header is present, circumvent authentication and allow public access to all endpoints
		//
		//*** THIS IS JUST FOR RI TESTING, THIS SHOULD NOT BE INCLUDED IN A PRODUCTION SYSTEM ***
		String publicAccessHeader = request.getHeader(allowPublicAccessHeader);
		if(publicAccessHeader == null) {

			// check if request path is an endpoint that needs validation
			// no values in proctedEndpoints means all endpoints require authentication
			if ((protectedEndpoints.size() == 0 || protectedEndpoints.contains(request.getRequestURI())) && !publicEndpoints.contains(request.getRequestURI())) {
				try {
					String authHeader = request.getHeader(Constants.HEADER_AUTHORIZATION);
					if (authHeader == null) {
						throw new AuthenticationException("Not authorized (no authorization header found in request)");
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
			_logger.info("The 'X-Allow-Public-Access' header was detected, ignoring security configuration.");
		}

		if(!authenticated) {
			response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
			response.getWriter().println("You are unauthorized to perform this request.");
		}
		return authenticated;
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
		byte[] publicBytes = Base64.decodeBase64(publicKey);
		X509EncodedKeySpec keySpec = new X509EncodedKeySpec(publicBytes);
		KeyFactory keyFactory = KeyFactory.getInstance("RSA");
		RSAPublicKey publicKey = (RSAPublicKey) keyFactory.generatePublic(keySpec);

		try {
			Algorithm algorithm = Algorithm.RSA256(publicKey, null);
			JWTVerifier verifier = JWT.require(algorithm)
				.withIssuer(issuer)
				.build(); //Reusable verifier instance
			DecodedJWT verifiedJwt = verifier.verify(token);

			return verifiedJwt != null;

		} catch (JWTVerificationException ex){
			throw new JWTVerificationException(ex.getMessage());
		}
	}
}
