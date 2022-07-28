/*
 * Copyright 2017 Google LLC.
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

package com.google.cloud.tools.jib.registry;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.api.client.http.HttpMethods;
import com.google.cloud.tools.jib.api.DescriptorDigest;
import com.google.cloud.tools.jib.hash.Digests;
import com.google.cloud.tools.jib.http.BlobHttpContent;
import com.google.cloud.tools.jib.http.Response;
import com.google.cloud.tools.jib.http.ResponseException;
import com.google.cloud.tools.jib.image.json.ManifestTemplate;
import com.google.cloud.tools.jib.image.json.OciIndexTemplate;
import com.google.cloud.tools.jib.image.json.OciManifestTemplate;
import com.google.cloud.tools.jib.image.json.UnknownManifestFormatException;
import com.google.cloud.tools.jib.image.json.V21ManifestTemplate;
import com.google.cloud.tools.jib.image.json.V22ManifestListTemplate;
import com.google.cloud.tools.jib.image.json.V22ManifestTemplate;
import com.google.cloud.tools.jib.json.JsonTemplateMapper;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import javax.annotation.Nullable;

/** Base class for manifest pullers. */
abstract class AbstractManifestPuller<T extends ManifestTemplate, R>
    implements RegistryEndpointProvider<R> {

  private final RegistryEndpointRequestProperties registryEndpointRequestProperties;
  private final String imageQualifier;
  private final Class<T> manifestTemplateClass;

  AbstractManifestPuller(
      RegistryEndpointRequestProperties registryEndpointRequestProperties,
      String imageQualifier,
      Class<T> manifestTemplateClass) {
    this.registryEndpointRequestProperties = registryEndpointRequestProperties;
    this.imageQualifier = imageQualifier;
    this.manifestTemplateClass = manifestTemplateClass;
  }

  @Nullable
  @Override
  public BlobHttpContent getContent() {
    return null;
  }

  @Override
  public List<String> getAccept() {
    if (manifestTemplateClass.equals(V21ManifestTemplate.class)) {
      return Collections.singletonList(V21ManifestTemplate.MEDIA_TYPE);
    }
    if (manifestTemplateClass.equals(V22ManifestTemplate.class)) {
      return Collections.singletonList(V22ManifestTemplate.MANIFEST_MEDIA_TYPE);
    }
    if (manifestTemplateClass.equals(OciManifestTemplate.class)) {
      return Collections.singletonList(OciManifestTemplate.MANIFEST_MEDIA_TYPE);
    }
    if (manifestTemplateClass.equals(V22ManifestListTemplate.class)) {
      return Collections.singletonList(V22ManifestListTemplate.MANIFEST_MEDIA_TYPE);
    }
    if (manifestTemplateClass.equals(OciIndexTemplate.class)) {
      return Collections.singletonList(OciIndexTemplate.MEDIA_TYPE);
    }

    return Arrays.asList(
        OciManifestTemplate.MANIFEST_MEDIA_TYPE,
        V22ManifestTemplate.MANIFEST_MEDIA_TYPE,
        V21ManifestTemplate.MEDIA_TYPE,
        V22ManifestListTemplate.MANIFEST_MEDIA_TYPE,
        OciIndexTemplate.MEDIA_TYPE);
  }

  /** Parses the response body into a {@link ManifestAndDigest}. */
  @Override
  public R handleResponse(Response response) throws IOException, UnknownManifestFormatException {
    ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
    DescriptorDigest digest =
        Digests.computeDigest(response.getBody(), byteArrayOutputStream).getDigest();
    String jsonString = byteArrayOutputStream.toString(StandardCharsets.UTF_8.name());
    T manifestTemplate = getManifestTemplateFromJson(jsonString);
    return computeReturn(new ManifestAndDigest<>(manifestTemplate, digest));
  }

  abstract R computeReturn(ManifestAndDigest<T> manifestAndDigest);

  @Override
  public URL getApiRoute(String apiRouteBase) throws MalformedURLException {
    return new URL(
        apiRouteBase
            + registryEndpointRequestProperties.getImageName()
            + "/manifests/"
            + imageQualifier);
  }

  @Override
  public String getHttpMethod() {
    return HttpMethods.GET;
  }

  @Override
  public String getActionDescription() {
    return "pull image manifest for "
        + registryEndpointRequestProperties.getServerUrl()
        + "/"
        + registryEndpointRequestProperties.getImageName()
        + ":"
        + imageQualifier;
  }

  /**
   * Instantiates a {@link ManifestTemplate} from a JSON string. This checks the {@code
   * schemaVersion} field of the JSON to determine which manifest version to use.
   */
  private T getManifestTemplateFromJson(String jsonString)
      throws IOException, UnknownManifestFormatException {
    ObjectNode node = new ObjectMapper().readValue(jsonString, ObjectNode.class);
    if (!node.has("schemaVersion")) {
      throw new UnknownManifestFormatException("Cannot find field 'schemaVersion' in manifest");
    }

    int schemaVersion = node.get("schemaVersion").asInt(-1);
    if (schemaVersion == -1) {
      throw new UnknownManifestFormatException("'schemaVersion' field is not an integer");
    }

    if (schemaVersion == 1) {
      return manifestTemplateClass.cast(
          JsonTemplateMapper.readJson(jsonString, V21ManifestTemplate.class));
    }
    if (schemaVersion == 2) {
      // 'schemaVersion' of 2 can be either Docker V2.2 or OCI.
      JsonNode mediaTypeNode = node.get("mediaType");
      if (mediaTypeNode == null) { // not Docker, hence OCI
        if (node.get("manifests") != null) {
          return manifestTemplateClass.cast(
              JsonTemplateMapper.readJson(jsonString, OciIndexTemplate.class));
        }
        if (node.get("config") != null) {
          return manifestTemplateClass.cast(
              JsonTemplateMapper.readJson(jsonString, OciManifestTemplate.class));
        }
        throw new UnknownManifestFormatException(
            "'schemaVersion' is 2, but neither 'manifests' nor 'config' exists");
      }

      String mediaType = mediaTypeNode.asText();
      if (OciManifestTemplate.MANIFEST_MEDIA_TYPE.equals(mediaType)) {
        return manifestTemplateClass.cast(
            JsonTemplateMapper.readJson(jsonString, OciManifestTemplate.class));
      }
      if (V22ManifestTemplate.MANIFEST_MEDIA_TYPE.equals(mediaType)) {
        return manifestTemplateClass.cast(
            JsonTemplateMapper.readJson(jsonString, V22ManifestTemplate.class));
      }
      if (V22ManifestListTemplate.MANIFEST_MEDIA_TYPE.equals(mediaType)) {
        return manifestTemplateClass.cast(
            JsonTemplateMapper.readJson(jsonString, V22ManifestListTemplate.class));
      }
      if (OciIndexTemplate.MEDIA_TYPE.equals(mediaType)) {
        return manifestTemplateClass.cast(
            JsonTemplateMapper.readJson(jsonString, OciIndexTemplate.class));
      }
      throw new UnknownManifestFormatException("Unknown mediaType: " + mediaType);
    }
    throw new UnknownManifestFormatException(
        "Unknown schemaVersion: " + schemaVersion + " - only 1 and 2 are supported");
  }

  @Override
  public R handleHttpResponseException(ResponseException responseException)
      throws ResponseException, RegistryErrorException {
    throw responseException;
  }
}
