/*
 * Copyright 2017 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package com.google.cloud.tools.crepecake.registry;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.google.cloud.tools.crepecake.http.Authorization;
import com.google.cloud.tools.crepecake.http.Authorizations;
import com.google.cloud.tools.crepecake.http.Connection;
import com.google.cloud.tools.crepecake.http.Request;
import com.google.cloud.tools.crepecake.http.Response;
import com.google.cloud.tools.crepecake.json.JsonTemplate;
import com.google.cloud.tools.crepecake.json.JsonTemplateMapper;
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

  RegistryAuthenticator(String realm, String service, String repository)
      throws MalformedURLException {
    authenticationUrl =
        new URL(realm + "?service=" + service + "&scope=repository:" + repository + ":pull");
  }

  /** Sends the authentication request and retrieves the Bearer authorization token. */
  public Authorization authenticate() throws RegistryAuthenticationFailedException {
    try (Connection connection = new Connection(authenticationUrl)) {
      Response response = connection.get(new Request());
      String responseString = response.getBody().writeToString();

      AuthenticationResponseTemplate responseJson =
          JsonTemplateMapper.readJson(responseString, AuthenticationResponseTemplate.class);
      return Authorizations.withBearerToken(responseJson.token);
    } catch (IOException ex) {
      throw new RegistryAuthenticationFailedException(ex);
    }
  }
}
