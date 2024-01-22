package ca.uhn.fhir.jpa.starter.identitymatching;

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

import org.springframework.util.ResourceUtils;

import com.auth0.jwt.algorithms.Algorithm;

import ca.uhn.fhir.jpa.starter.security.models.SecurityConfig;

public class CertUtil {

  private static KeyStore keyStore;
  private static String alias;
  private static X509Certificate certificate;

  public static X509Certificate getCert(SecurityConfig securityConfig) throws KeyStoreException, NoSuchAlgorithmException, CertificateException, IOException {

    if (certificate != null) {
      return certificate;
    }

    FileInputStream stream = new FileInputStream(ResourceUtils.getFile(securityConfig.getCertFile()));

    if (keyStore == null) {
      keyStore = KeyStore.getInstance("pkcs12");
      keyStore.load(stream, securityConfig.getCertPassword().toCharArray());
      alias = keyStore.aliases().nextElement();
    }
    X509Certificate certificate = (X509Certificate) keyStore.getCertificate(alias);
    return certificate;
  }

  public static Algorithm getAlgorithm(SecurityConfig securityConfig) throws KeyStoreException, NoSuchAlgorithmException, CertificateException, IOException, UnrecoverableKeyException {
    X509Certificate certificate = getCert(securityConfig);
    RSAPublicKey publicKey = (RSAPublicKey) certificate.getPublicKey();
    RSAPrivateKey privateKey = (RSAPrivateKey) keyStore.getKey(alias, securityConfig.getCertPassword().toCharArray());
    return Algorithm.RSA256(publicKey, privateKey);
  }

}
