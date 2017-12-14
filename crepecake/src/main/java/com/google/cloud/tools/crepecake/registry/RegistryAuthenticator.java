package com.google.cloud.tools.crepecake.registry;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.google.cloud.tools.crepecake.blob.Blob;
import com.google.cloud.tools.crepecake.http.Authorization;
import com.google.cloud.tools.crepecake.http.Authorizations;
import com.google.cloud.tools.crepecake.http.Connection;
import com.google.cloud.tools.crepecake.http.Request;
import com.google.cloud.tools.crepecake.http.Response;
import com.google.cloud.tools.crepecake.json.JsonHelper;
import com.google.cloud.tools.crepecake.json.JsonTemplate;
import com.google.common.base.Charsets;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;

/**
 * Authenticates pull access with a registry service.
 *
 * @see <a
 *     href="https://docs.docker.com/registry/spec/auth/token/">https://docs.docker.com/registry/spec/auth/token/</a>
 */
public class RegistryAuthenticator {

  private final URL authenticationUrl;

  /** Template for the authentication response JSON. */
  @JsonIgnoreProperties(ignoreUnknown = true)
  private static class AuthenticationResponseTemplate extends JsonTemplate {

    private String token;
  }

  /** Gets the contents of a {@link Blob} as a string. */
  private static String writeBlobToString(Blob blob) throws IOException {
    ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
    blob.writeTo(byteArrayOutputStream);
    return new String(byteArrayOutputStream.toByteArray(), Charsets.UTF_8);
  }

  RegistryAuthenticator(String realm, String service, String repository)
      throws MalformedURLException {
    authenticationUrl =
        new URL(realm + "?service=" + service + "&scope=repository:" + repository + ":pull");
  }

  /** Sends the authentication request and retrieves the Bearer authorization token. */
  public Authorization authenticate() throws RegistryAuthenticationFailedException {
    try (Connection connection = new Connection(authenticationUrl)) {
      Response response = connection.get(new Request());
      String responseString = writeBlobToString(response.getBody());

      AuthenticationResponseTemplate responseJson =
          JsonHelper.readJson(responseString, AuthenticationResponseTemplate.class);
      return Authorizations.withBearerToken(responseJson.token);
    } catch (IOException ex) {
      throw new RegistryAuthenticationFailedException(ex);
    }
  }
}
