package ca.uhn.fhir.jpa.starter.security;
import ca.uhn.fhir.interceptor.api.Hook;
import ca.uhn.fhir.interceptor.api.Interceptor;
import ca.uhn.fhir.interceptor.api.Pointcut;
import ca.uhn.fhir.jpa.starter.AppProperties;
import ca.uhn.fhir.jpa.starter.custom.SecurityUtil;
import ca.uhn.fhir.jpa.starter.operations.models.DiscoveryObject;
import ca.uhn.fhir.jpa.starter.security.models.SecurityConfig;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.ResourceUtils;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.FileInputStream;
import java.io.IOException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.InvalidKeySpecException;
import java.sql.Date;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;

@Interceptor
public class DiscoveryInterceptor
{
	private final Logger _logger = LoggerFactory.getLogger(DiscoveryInterceptor.class);

	private SecurityConfig securityConfig;
	private AppProperties appProperties;

	public DiscoveryInterceptor(AppProperties appProperties, SecurityConfig securityConfig) {
		this.appProperties = appProperties;
		this.securityConfig = securityConfig;
	}

	@Hook(Pointcut.SERVER_INCOMING_REQUEST_PRE_PROCESSED)
	public boolean incomingRequestPreProcessed(HttpServletRequest theRequest, HttpServletResponse theResponse) throws IOException, KeyStoreException, NoSuchAlgorithmException, CertificateException, InvalidKeySpecException, UnrecoverableKeyException {
		// Check if the request is for /.well-known/udap
		if (theRequest.getRequestURI().equals("/fhir/.well-known/udap")) {

			_logger.info("Intercepted discovery request, sending security capability statement.");

			//get the discovery results
			ObjectMapper objectMapper = new ObjectMapper();
			DiscoveryObject myJsonObject = new DiscoveryObject();
			myJsonObject.setUdap_versions_supported(new String[] { "1" });
			myJsonObject.setUdap_profiles_supported(new String[] { "udap_dcr", "udap_authn", "udap_authz" });
			myJsonObject.setUdap_authorization_extensions_supported(new String[] { "hl7-b2b" });
			myJsonObject.setUdap_authorization_extensions_required(new String[] { "hl7-b2b" });
			
			myJsonObject.setUdap_certifications_supported(new String[] { "https://www.example.com/udap/profiles/example-certification" });
			myJsonObject.setUdap_certifications_required(new String[] { "https://www.example.com/udap/profiles/example-certification" });
			myJsonObject.setGrant_types_supported(new String[] {"authorization_code", "refresh_token",  "client_credentials"});
			myJsonObject.setScopes_supported(new String[] {"openid", "patient/*.read", "patient/*.rs", "user/*.read", "user/*.rs", "system/*.read", "system/*.rs"});
			myJsonObject.setToken_endpoint_auth_methods_supported(new String[] { "private_key_jwt" });
			myJsonObject.setToken_endpoint_auth_signing_alg_values_supported(new String[] { "ES256", "ES384", "RS256", "RS384" });
			myJsonObject.setRegistration_endpoint_jwt_signing_alg_values_supported(new String[] { "ES256", "ES384", "RS256", "RS384" });


			String fhirBase = StringUtils.removeEnd(appProperties.getServer_address(), "/");
			String issuer = SecurityUtil.resolveIssuer(securityConfig);

			myJsonObject.setAuthorization_endpoint(issuer + "/connect/authorize");
			myJsonObject.setToken_endpoint(issuer + "/connect/token");
			myJsonObject.setRegistration_endpoint(issuer + "/connect/register");

			String signedMetadata = "";

			FileInputStream stream = new FileInputStream(ResourceUtils.getFile(securityConfig.getCertFile()));
			KeyStore ks = KeyStore.getInstance("pkcs12");
			ks.load(stream, securityConfig.getCertPassword().toCharArray());
			String alias = ks.aliases().nextElement();

			X509Certificate certificate = (X509Certificate) ks.getCertificate(alias);

			if (!(certificate.getPublicKey() instanceof RSAPublicKey)) {
				_logger.error("Certificate must be RS256");
				throw new IllegalArgumentException("Certificate must be RS256");
			}

			RSAPublicKey publicKey = (RSAPublicKey) certificate.getPublicKey();
			RSAPrivateKey privateKey = (RSAPrivateKey) ks.getKey(alias, securityConfig.getCertPassword().toCharArray());

			// IG specifies that the JWT SHALL be signed using the RS256 signature algorithm.
			// https://build.fhir.org/ig/HL7/fhir-udap-security-ig/discovery.html#signed-metadata-elements
			if (publicKey.getModulus().bitLength() != 2048) {
				_logger.error("Certificate must be RS256");
				throw new IllegalArgumentException("Certificate must be RS256");
			}

			Algorithm algorithm = Algorithm.RSA256(publicKey, privateKey);
			signedMetadata = JWT.create()
				.withHeader(Map.of(
					"alg", algorithm.getName(),
					"x5c", new String[] { Base64.getEncoder().encodeToString(certificate.getEncoded()) }
				))
				.withIssuer(fhirBase)
				.withSubject(fhirBase)
				.withIssuedAt(Date.from(Instant.now()))
				.withExpiresAt(Date.from(Instant.now().plusMillis(86400000)))
				.withJWTId(UUID.randomUUID().toString())
				.withClaim("authorization_endpoint", myJsonObject.getAuthorization_endpoint())
				.withClaim("token_endpoint", myJsonObject.getToken_endpoint())
				.withClaim("registration_endpoint", myJsonObject.getRegistration_endpoint())
				.sign(algorithm);

			myJsonObject.setSigned_metadata(signedMetadata);

			//return the discovery object
			theResponse.setContentType("application/json");
			objectMapper.writeValue(theResponse.getOutputStream(), myJsonObject);

			return false;
		}

		// If the request is not for /fhir/.well-known/udap, continue processing the request
		return true;
	}

}
