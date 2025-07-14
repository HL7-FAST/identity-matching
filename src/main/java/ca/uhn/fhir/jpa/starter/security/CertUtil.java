package ca.uhn.fhir.jpa.starter.security;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.util.Base64;
import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.ResourceUtils;

import com.auth0.jwt.algorithms.Algorithm;
import com.fasterxml.jackson.databind.ObjectMapper;

import ca.uhn.fhir.jpa.starter.AppProperties;
import ca.uhn.fhir.jpa.starter.custom.SecurityUtil;
import ca.uhn.fhir.jpa.starter.security.models.CertGenerateRequest;
import ca.uhn.fhir.jpa.starter.security.models.SecurityConfig;

public class CertUtil {

  private final static Logger _logger = LoggerFactory.getLogger(CertUtil.class);

  private static KeyStore keyStore;
  private static String alias;
  private static X509Certificate certificate;

  public static KeyStore getKeyStore(SecurityConfig securityConfig)
      throws KeyStoreException, NoSuchAlgorithmException, CertificateException, IOException {

    if (keyStore != null) {
      return keyStore;
    }

    InputStream stream = null;

    try {
      // Check if the string is valid base64
      byte[] bytes = Base64.getDecoder().decode(securityConfig.getCertFile());
      stream = new ByteArrayInputStream(bytes);
    } catch (IllegalArgumentException e) {
    }

    // If the string is not valid base64, treat it as a file path
    if (stream == null) {
      stream = new FileInputStream(ResourceUtils.getFile(securityConfig.getCertFile()));
    }

    if (keyStore == null) {
      keyStore = KeyStore.getInstance("pkcs12");
      keyStore.load(stream, securityConfig.getCertPassword().toCharArray());
      alias = keyStore.aliases().nextElement();
    }

    return keyStore;
  }

  public static X509Certificate getCert(SecurityConfig securityConfig)
      throws KeyStoreException, NoSuchAlgorithmException, CertificateException, IOException {

    if (certificate != null) {
      return certificate;
    }

    keyStore = getKeyStore(securityConfig);
    certificate = (X509Certificate) keyStore.getCertificate(alias);

    return certificate;
  }

  public static Algorithm getAlgorithm(SecurityConfig securityConfig)
      throws KeyStoreException, NoSuchAlgorithmException, CertificateException, IOException, UnrecoverableKeyException {
    X509Certificate certificate = getCert(securityConfig);
    RSAPublicKey publicKey = (RSAPublicKey) certificate.getPublicKey();
    RSAPrivateKey privateKey = (RSAPrivateKey) keyStore.getKey(alias, securityConfig.getCertPassword().toCharArray());
    return Algorithm.RSA256(publicKey, privateKey);
  }

  /**
   * Checks the current cert configuration. If a cert file is configured, then
   * this will be checked and throw an exception if this configuration is invalid.
   * If a cert is not set in the configuration, we will attempt to fetch a default
   * cert from the UDAP security server that is configured in the issuer property.
   * 
   * @param securityConfig
   * @throws IOException
   * @throws CertificateException
   * @throws NoSuchAlgorithmException
   * @throws KeyStoreException
   * @throws InterruptedException
   */
  public static void initializeCert(SecurityConfig securityConfig, AppProperties appProperties)
      throws KeyStoreException, NoSuchAlgorithmException, CertificateException, IOException, InterruptedException {

    // a path to a cert file is configured, so attempt to load it
    if (securityConfig.getCertFile() != null) {

      // password is required
      if (securityConfig.getCertPassword() == null) {
        throw new IllegalArgumentException("Cert password is required when a cert file is configured.");
      }

      // loads the cert or throws an exception if there is a problem loading it
      getCert(securityConfig);

    }

    // otherwise we will attempt to use a generated certificate...
    else if (securityConfig.isFetchCert()) {

      securityConfig.setCertFile("generated-cert.pfx");
      securityConfig.setCertPassword(securityConfig.getDefaultCertPassword());

      // check if a cert is already generated and present on the local filesystem with
      // the expected "generated-cert.pfx" filename
      Path certPath = Paths.get("generated-cert.pfx");

      if (Files.exists(certPath)) {
        _logger.info("Certificate already exists at: " + certPath.toAbsolutePath());
        return;
      }

      // otherwise fetch a default cert from the UDAP security server

      CertGenerateRequest certRequest = new CertGenerateRequest();
      certRequest.setAltNames(List.of(appProperties.getServer_address()));
      certRequest.setPassword(securityConfig.getDefaultCertPassword());
      ObjectMapper objectMapper = new ObjectMapper();
      String jsonBody = objectMapper.writeValueAsString(certRequest);

      // issuer should be configured to the UDAP security server to use (likely
      // localhost or foundry hosted version)
      if (securityConfig.getIssuer() == null) {
        throw new IllegalArgumentException(
            "Issuer is not configured.  This should be set to the UDAP security server to use.");
      }

      // retrieve a cert from the /api/cert/generate endpoint
      String certUrl = StringUtils.removeEnd(securityConfig.getIssuer(), "/") + "/api/cert/generate";
      HttpClient client = SecurityUtil.getHttpClient(securityConfig);

      HttpRequest request = HttpRequest.newBuilder()
          .uri(URI.create(certUrl))
          .header("Content-Type", "application/json")
          .POST(BodyPublishers.ofString(jsonBody))
          .build();

      _logger.info("Fetching certificate from: " + certUrl);

      IOException lastException = null;
      boolean success = false;
      
      // Retry fetching the certificate the configured number of times if it fails
      for (int attempt = 1; attempt <= securityConfig.getFetchCertRetryAttempts(); attempt++) {
        try {
          _logger.info("Certificate fetch attempt {} of {}", attempt, securityConfig.getFetchCertRetryAttempts());
          
          HttpResponse<Path> response = client.send(request, HttpResponse.BodyHandlers.ofFile(certPath));

          if (response.statusCode() == 200) {
            File tempCertFile = response.body().toFile();
            _logger.info("Certificate saved to: " + tempCertFile.getAbsolutePath());
            success = true;
            break;
          } else {
            throw new IOException("Failed to generate certificate, HTTP status: " + response.statusCode() + ", body: " + response.body());
          }
        } catch (IOException | InterruptedException e) {
          lastException = e instanceof IOException ? (IOException) e : new IOException("Request interrupted", e);
          _logger.warn("Certificate fetch attempt {} failed: {}", attempt, e.getMessage());
          
          if (attempt < securityConfig.getFetchCertRetryAttempts()) {
            try {
              _logger.info("Waiting {} ms before retry attempt {}", securityConfig.getFetchCertRetryDelay(), attempt + 1);
              Thread.sleep(securityConfig.getFetchCertRetryDelay());
            } catch (InterruptedException sleepException) {
              Thread.currentThread().interrupt();
              throw new IOException("Certificate fetch interrupted during retry delay", sleepException);
            }
          }
        }
      }
      
      if (!success) {
        throw new IOException("Failed to generate certificate after " + securityConfig.getFetchCertRetryAttempts() + " attempts", lastException);
      }

    }

    else {
      throw new IllegalArgumentException(
          "No cert file is configured.  Either set a cert file or set fetchCert to true to generate a default cert.");
    }

  }

}
