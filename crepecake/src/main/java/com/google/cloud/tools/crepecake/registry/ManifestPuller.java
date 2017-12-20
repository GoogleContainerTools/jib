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

import com.google.api.client.http.HttpResponseException;
import com.google.cloud.tools.crepecake.http.Authorization;
import com.google.cloud.tools.crepecake.http.Connection;
import com.google.cloud.tools.crepecake.http.HttpStatusCodes;
import com.google.cloud.tools.crepecake.http.Request;
import com.google.cloud.tools.crepecake.http.Response;
import com.google.cloud.tools.crepecake.image.json.ManifestTemplateHolder;
import com.google.cloud.tools.crepecake.image.json.UnknownManifestFormatException;
import com.google.cloud.tools.crepecake.json.JsonTemplateMapper;
import com.google.cloud.tools.crepecake.registry.json.ErrorEntryTemplate;
import com.google.cloud.tools.crepecake.registry.json.ErrorResponseTemplate;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import javax.annotation.Nullable;

/** Registry interface for pulling an image's manifest. */
public class ManifestPuller {

  // TODO: This should be configurable.
  private static final String PROTOCOL = "http";

  @Nullable private final Authorization authorization;
  private final String serverUrl;
  private final String baseImage;

  public ManifestPuller(@Nullable Authorization authorization, String serverUrl, String baseImage) {
    this.authorization = authorization;
    this.serverUrl = serverUrl;
    this.baseImage = baseImage;
  }

  public ManifestTemplateHolder pull(String imageTag)
      throws IOException, RegistryErrorException, RegistryUnauthorizedException,
          RegistryTooManyRequestsException, UnknownManifestFormatException {
    URL pullUrl = getApiRoute("/manifests/" + imageTag);

    try (Connection connection = new Connection(pullUrl)) {
      Request request = new Request();
      if (null != authorization) {
        request.setAuthorization(authorization);
      }
      Response response = connection.get(request);
      String responseString = response.getBody().writeToString();

      return ManifestTemplateHolder.fromJson(responseString);

    } catch (HttpResponseException ex) {
      switch (ex.getStatusCode()) {
        case HttpStatusCodes.BAD_REQUEST:
        case HttpStatusCodes.NOT_FOUND:
          // The name or reference was invalid.
          ErrorResponseTemplate errorResponse;
          errorResponse = JsonTemplateMapper.readJson(ex.getContent(), ErrorResponseTemplate.class);
          String method = "pull image manifest for " + serverUrl + "/" + baseImage + ":" + imageTag;
          RegistryErrorExceptionBuilder registryErrorExceptionBuilder =
              new RegistryErrorExceptionBuilder(method, ex);
          for (ErrorEntryTemplate errorEntry : errorResponse.getErrors()) {
            registryErrorExceptionBuilder.addErrorEntry(errorEntry);
          }

          throw registryErrorExceptionBuilder.toRegistryHttpException();

        case HttpStatusCodes.UNAUTHORIZED:
        case HttpStatusCodes.FORBIDDEN:
          throw new RegistryUnauthorizedException(ex);

        case HttpStatusCodes.TOO_MANY_REQUESTS:
          throw new RegistryTooManyRequestsException(ex);

        default: // Unknown
          throw ex;
      }
    }
  }

  private URL getApiRoute(String route) throws MalformedURLException {
    String apiBase = "/v2/";
    return new URL(PROTOCOL + "://" + serverUrl + apiBase + baseImage + route);
  }
}
