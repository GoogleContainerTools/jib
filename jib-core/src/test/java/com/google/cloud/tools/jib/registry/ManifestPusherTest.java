/*
 * Copyright 2018 Google LLC.
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

import com.google.cloud.tools.jib.api.DescriptorDigest;
import com.google.cloud.tools.jib.api.LogEvent;
import com.google.cloud.tools.jib.event.EventHandlers;
import com.google.cloud.tools.jib.hash.Digests;
import com.google.cloud.tools.jib.http.BlobHttpContent;
import com.google.cloud.tools.jib.http.Response;
import com.google.cloud.tools.jib.http.ResponseException;
import com.google.cloud.tools.jib.image.json.V22ManifestTemplate;
import com.google.cloud.tools.jib.json.JsonTemplateMapper;
import com.google.common.io.Resources;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;
import org.apache.http.HttpStatus;
import org.hamcrest.CoreMatchers;
import org.hamcrest.MatcherAssert;
import org.junit.Assert;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/** Tests for {@link ManifestPusher}. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ManifestPusherTest {

  @Mock private Response mockResponse;
  @Mock private EventHandlers mockEventHandlers;

  private Path v22manifestJsonFile;
  private V22ManifestTemplate fakeManifestTemplate;
  private ManifestPusher testManifestPusher;

  @BeforeEach
  void setUp() throws URISyntaxException, IOException {
    v22manifestJsonFile = Paths.get(Resources.getResource("core/json/v22manifest.json").toURI());
    fakeManifestTemplate =
        JsonTemplateMapper.readJsonFromFile(v22manifestJsonFile, V22ManifestTemplate.class);

    testManifestPusher =
        new ManifestPusher(
            new RegistryEndpointRequestProperties("someServerUrl", "someImageName"),
            fakeManifestTemplate,
            "test-image-tag",
            mockEventHandlers);
  }

  @Test
  void testGetContent() throws IOException {
    BlobHttpContent body = testManifestPusher.getContent();

    Assert.assertNotNull(body);
    Assert.assertEquals(V22ManifestTemplate.MANIFEST_MEDIA_TYPE, body.getType());

    ByteArrayOutputStream bodyCaptureStream = new ByteArrayOutputStream();
    body.writeTo(bodyCaptureStream);
    String v22manifestJson =
        new String(Files.readAllBytes(v22manifestJsonFile), StandardCharsets.UTF_8);
    Assert.assertEquals(
        v22manifestJson, new String(bodyCaptureStream.toByteArray(), StandardCharsets.UTF_8));
  }

  @Test
  void testHandleResponse_valid() throws IOException {
    DescriptorDigest expectedDigest = Digests.computeJsonDigest(fakeManifestTemplate);
    Mockito.when(mockResponse.getHeader("Docker-Content-Digest"))
        .thenReturn(Collections.singletonList(expectedDigest.toString()));
    Assert.assertEquals(expectedDigest, testManifestPusher.handleResponse(mockResponse));
  }

  @Test
  void testHandleResponse_noDigest() throws IOException {
    DescriptorDigest expectedDigest = Digests.computeJsonDigest(fakeManifestTemplate);
    Mockito.when(mockResponse.getHeader("Docker-Content-Digest"))
        .thenReturn(Collections.emptyList());

    Assert.assertEquals(expectedDigest, testManifestPusher.handleResponse(mockResponse));
    Mockito.verify(mockEventHandlers)
        .dispatch(LogEvent.warn("Expected image digest " + expectedDigest + ", but received none"));
  }

  @Test
  void testHandleResponse_multipleDigests() throws IOException {
    DescriptorDigest expectedDigest = Digests.computeJsonDigest(fakeManifestTemplate);
    Mockito.when(mockResponse.getHeader("Docker-Content-Digest"))
        .thenReturn(Arrays.asList("too", "many"));

    Assert.assertEquals(expectedDigest, testManifestPusher.handleResponse(mockResponse));
    Mockito.verify(mockEventHandlers)
        .dispatch(
            LogEvent.warn("Expected image digest " + expectedDigest + ", but received: too, many"));
  }

  @Test
  void testHandleResponse_invalidDigest() throws IOException {
    DescriptorDigest expectedDigest = Digests.computeJsonDigest(fakeManifestTemplate);
    Mockito.when(mockResponse.getHeader("Docker-Content-Digest"))
        .thenReturn(Collections.singletonList("not valid"));

    Assert.assertEquals(expectedDigest, testManifestPusher.handleResponse(mockResponse));
    Mockito.verify(mockEventHandlers)
        .dispatch(
            LogEvent.warn("Expected image digest " + expectedDigest + ", but received: not valid"));
  }

  @Test
  void testApiRoute() throws MalformedURLException {
    Assert.assertEquals(
        new URL("http://someApiBase/someImageName/manifests/test-image-tag"),
        testManifestPusher.getApiRoute("http://someApiBase/"));
  }

  @Test
  void testGetHttpMethod() {
    Assert.assertEquals("PUT", testManifestPusher.getHttpMethod());
  }

  @Test
  void testGetActionDescription() {
    Assert.assertEquals(
        "push image manifest for someServerUrl/someImageName:test-image-tag",
        testManifestPusher.getActionDescription());
  }

  @Test
  void testGetAccept() {
    Assert.assertEquals(0, testManifestPusher.getAccept().size());
  }

  /** Docker Registry 2.0 and 2.1 return 400 / TAG_INVALID. */
  @Test
  void testHandleHttpResponseException_dockerRegistry_tagInvalid() throws ResponseException {
    ResponseException exception = Mockito.mock(ResponseException.class);
    Mockito.when(exception.getStatusCode()).thenReturn(HttpStatus.SC_BAD_REQUEST);
    Mockito.when(exception.getContent())
        .thenReturn(
            "{\"errors\":[{\"code\":\"TAG_INVALID\","
                + "\"message\":\"manifest tag did not match URI\"}]}");
    try {
      testManifestPusher.handleHttpResponseException(exception);
      Assert.fail();

    } catch (RegistryErrorException ex) {
      MatcherAssert.assertThat(
          ex.getMessage(),
          CoreMatchers.containsString(
              "Registry may not support pushing OCI Manifest or "
                  + "Docker Image Manifest Version 2, Schema 2"));
    }
  }

  /** Docker Registry 2.2 returns a 400 / MANIFEST_INVALID. */
  @Test
  void testHandleHttpResponseException_dockerRegistry_manifestInvalid() throws ResponseException {
    ResponseException exception = Mockito.mock(ResponseException.class);
    Mockito.when(exception.getStatusCode()).thenReturn(HttpStatus.SC_BAD_REQUEST);
    Mockito.when(exception.getContent())
        .thenReturn(
            "{\"errors\":[{\"code\":\"MANIFEST_INVALID\","
                + "\"message\":\"manifest invalid\",\"detail\":{}}]}");
    try {
      testManifestPusher.handleHttpResponseException(exception);
      Assert.fail();

    } catch (RegistryErrorException ex) {
      MatcherAssert.assertThat(
          ex.getMessage(),
          CoreMatchers.containsString(
              "Registry may not support pushing OCI Manifest or "
                  + "Docker Image Manifest Version 2, Schema 2"));
    }
  }

  /** Quay.io returns an undocumented 415 / MANIFEST_INVALID. */
  @Test
  void testHandleHttpResponseException_quayIo() throws ResponseException {
    ResponseException exception = Mockito.mock(ResponseException.class);
    Mockito.when(exception.getStatusCode()).thenReturn(HttpStatus.SC_UNSUPPORTED_MEDIA_TYPE);
    Mockito.when(exception.getContent())
        .thenReturn(
            "{\"errors\":[{\"code\":\"MANIFEST_INVALID\","
                + "\"detail\":{\"message\":\"manifest schema version not supported\"},"
                + "\"message\":\"manifest invalid\"}]}");
    try {
      testManifestPusher.handleHttpResponseException(exception);
      Assert.fail();

    } catch (RegistryErrorException ex) {
      MatcherAssert.assertThat(
          ex.getMessage(),
          CoreMatchers.containsString(
              "Registry may not support pushing OCI Manifest or "
                  + "Docker Image Manifest Version 2, Schema 2"));
    }
  }

  @Test
  void testHandleHttpResponseException_otherError() throws RegistryErrorException {
    ResponseException exception = Mockito.mock(ResponseException.class);
    Mockito.when(exception.getStatusCode()).thenReturn(HttpStatus.SC_UNAUTHORIZED);
    try {
      testManifestPusher.handleHttpResponseException(exception);
      Assert.fail();

    } catch (ResponseException ex) {
      Assert.assertSame(exception, ex);
    }
  }
}
