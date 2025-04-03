package ca.uhn.fhir.jpa.starter.custom;

import java.net.InetAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.security.cert.X509Certificate;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import org.apache.commons.lang3.StringUtils;

import ca.uhn.fhir.jpa.starter.security.models.SecurityConfig;

public class SecurityUtil {

  public static HttpClient getHttpClient(SecurityConfig securityConfig) {

    if (securityConfig.getSslVerify()) {
      return HttpClient.newBuilder()
          .build();
    }

    SSLContext sslContext;
    try {
      sslContext = SSLContext.getInstance("SSL");
      sslContext.init(null, new TrustManager[] { new X509TrustManager() {
        @Override
        public void checkClientTrusted(X509Certificate[] chain, String authType) {
        }

        @Override
        public void checkServerTrusted(X509Certificate[] chain, String authType) {
        }

        @Override
        public X509Certificate[] getAcceptedIssuers() {
          return new X509Certificate[0];
        }
      } }, new java.security.SecureRandom());
    } catch (Exception e) {
      e.printStackTrace();
      return HttpClient.newBuilder()
          .build();
    }

    return HttpClient.newBuilder()
        .sslContext(sslContext)
        .build();
  }


  /**
   * Gets the issuer URL from the security configuration and resolves it to a hopefully proper hostname.
   * This is a helper for when the issuer is set to a hostname that cannot be resolved from outside of a container.
   * Defaults to localhost if the hostname cannot be resolved.
   * For example, "host.docker.internal" is not resolvable from outside of the container but is needed for use within the container.
   *
   * @param securityConfig The security configuration
   * @return The issuer URL
   */
  public static String resolveIssuer(SecurityConfig securityConfig) {

    String issuer = StringUtils.removeEnd(securityConfig.getIssuer(), "/");

    // Don't attempt to resolve if not configured to do so
    if (securityConfig.getResolveIssuer() && securityConfig.getIssuer() != null) {
      URI issuerUri = URI.create(issuer);
      String hostname = issuerUri.getHost();
      try {
        InetAddress address = InetAddress.getByName(hostname);

        if (address.isLoopbackAddress() || address.isSiteLocalAddress()
            || "host.docker.internal".equalsIgnoreCase(hostname)) {
          hostname = "localhost";
        }

      } catch (Exception e) {
        // Could not resolve hostname, default to localhost
        e.printStackTrace();
        hostname = "localhost";
      }
      issuer = issuerUri.getScheme() + "://" + hostname + (issuerUri.getPort() != -1 ? ":" + issuerUri.getPort() : "");
    }

    return issuer;

  }

}
