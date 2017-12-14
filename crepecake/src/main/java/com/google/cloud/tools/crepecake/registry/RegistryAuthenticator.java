package com.google.cloud.tools.crepecake.registry;

import com.google.api.client.http.HttpResponseException;
import com.google.cloud.tools.crepecake.http.Authorization;
import com.google.cloud.tools.crepecake.http.Authorizations;
import com.google.cloud.tools.crepecake.http.Connection;
import com.google.cloud.tools.crepecake.http.Request;
import com.google.cloud.tools.crepecake.http.Response;
import com.google.cloud.tools.crepecake.json.JsonTemplate;
import com.google.common.base.Charsets;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;

/** Authenticates pull access with a registry service. */
public class RegistryAuthenticator {

  private final URL authenticationUrl;

  /** Template for the authentication response JSON. */
  private static class AuthenticationResponseTemplate extends JsonTemplate {

    private String token;
  }

  RegistryAuthenticator(String realm, String service, String repository) throws MalformedURLException {
    authenticationUrl = new URL(realm + "?service=" + service + "&scope=repository:" + repository +  ":pull");
  }

  /** Sends the authentication request and retrieves the Bearer authorization token. */
  public Authorization authenticate() throws RegistryAuthenticationFailedException {
    try (Connection connection = new Connection(authenticationUrl)) {
      Response response = connection.get(new Request().setResponseIsJson());

      AuthenticationResponseTemplate responseJson = response.parseAs(AuthenticationResponseTemplate.class);
      return Authorizations.withBearerToken(responseJson.token);
    } catch (IOException ex) {
      throw new RegistryAuthenticationFailedException(ex);
    }
  }
}
