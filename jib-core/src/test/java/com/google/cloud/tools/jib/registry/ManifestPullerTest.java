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
import com.google.cloud.tools.jib.hash.Digests;
import com.google.cloud.tools.jib.http.Response;
import com.google.cloud.tools.jib.image.json.ManifestTemplate;
import com.google.cloud.tools.jib.image.json.OciIndexTemplate;
import com.google.cloud.tools.jib.image.json.OciManifestTemplate;
import com.google.cloud.tools.jib.image.json.UnknownManifestFormatException;
import com.google.cloud.tools.jib.image.json.V21ManifestTemplate;
import com.google.cloud.tools.jib.image.json.V22ManifestListTemplate;
import com.google.cloud.tools.jib.image.json.V22ManifestTemplate;
import com.google.common.io.Resources;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;
import org.hamcrest.CoreMatchers;
import org.hamcrest.MatcherAssert;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

/** Tests for {@link ManifestPuller}. */
@RunWith(MockitoJUnitRunner.class)
public class ManifestPullerTest {

  private static InputStream stringToInputStreamUtf8(String string) {
    return new ByteArrayInputStream(string.getBytes(StandardCharsets.UTF_8));
  }

  private final RegistryEndpointRequestProperties fakeRegistryEndpointRequestProperties =
      new RegistryEndpointRequestProperties("someServerUrl", "someImageName");
  private final ManifestPuller<ManifestTemplate> testManifestPuller =
      new ManifestPuller<>(
          fakeRegistryEndpointRequestProperties, "test-image-tag", ManifestTemplate.class);

  @Mock private Response mockResponse;

  @Test
  public void testHandleResponse_v21()
      throws URISyntaxException, IOException, UnknownManifestFormatException {
    Path v21ManifestFile = Paths.get(Resources.getResource("core/json/v21manifest.json").toURI());
    InputStream v21Manifest = new ByteArrayInputStream(Files.readAllBytes(v21ManifestFile));

    DescriptorDigest expectedDigest = Digests.computeDigest(v21Manifest).getDigest();
    v21Manifest.reset();

    Mockito.when(mockResponse.getBody()).thenReturn(v21Manifest);
    ManifestAndDigest<?> manifestAndDigest =
        new ManifestPuller<>(
                fakeRegistryEndpointRequestProperties, "test-image-tag", V21ManifestTemplate.class)
            .handleResponse(mockResponse);

    MatcherAssert.assertThat(
        manifestAndDigest.getManifest(), CoreMatchers.instanceOf(V21ManifestTemplate.class));
    Assert.assertEquals(expectedDigest, manifestAndDigest.getDigest());
  }

  @Test
  public void testHandleResponse_v22()
      throws URISyntaxException, IOException, UnknownManifestFormatException {
    Path v22ManifestFile = Paths.get(Resources.getResource("core/json/v22manifest.json").toURI());
    InputStream v22Manifest = new ByteArrayInputStream(Files.readAllBytes(v22ManifestFile));

    DescriptorDigest expectedDigest = Digests.computeDigest(v22Manifest).getDigest();
    v22Manifest.reset();

    Mockito.when(mockResponse.getBody()).thenReturn(v22Manifest);
    ManifestAndDigest<?> manifestAndDigest =
        new ManifestPuller<>(
                fakeRegistryEndpointRequestProperties, "test-image-tag", V22ManifestTemplate.class)
            .handleResponse(mockResponse);

    MatcherAssert.assertThat(
        manifestAndDigest.getManifest(), CoreMatchers.instanceOf(V22ManifestTemplate.class));
    Assert.assertEquals(expectedDigest, manifestAndDigest.getDigest());
  }

  @Test
  public void testHandleResponse_v22ManifestListFailsWhenParsedAsV22Manifest()
      throws URISyntaxException, IOException, UnknownManifestFormatException {
    Path v22ManifestListFile =
        Paths.get(Resources.getResource("core/json/v22manifest_list.json").toURI());
    InputStream v22ManifestList = new ByteArrayInputStream(Files.readAllBytes(v22ManifestListFile));

    Mockito.when(mockResponse.getBody()).thenReturn(v22ManifestList);
    try {
      new ManifestPuller<>(
              fakeRegistryEndpointRequestProperties, "test-image-tag", V22ManifestTemplate.class)
          .handleResponse(mockResponse);
      Assert.fail();
    } catch (ClassCastException ex) {
      // pass
    }
  }

  @Test
  public void testHandleResponse_v22ManifestListFromParentType()
      throws URISyntaxException, IOException, UnknownManifestFormatException {
    Path v22ManifestListFile =
        Paths.get(Resources.getResource("core/json/v22manifest_list.json").toURI());
    InputStream v22ManifestList = new ByteArrayInputStream(Files.readAllBytes(v22ManifestListFile));
    DescriptorDigest expectedDigest = Digests.computeDigest(v22ManifestList).getDigest();
    v22ManifestList.reset();

    Mockito.when(mockResponse.getBody()).thenReturn(v22ManifestList);
    ManifestAndDigest<?> manifestAndDigest =
        new ManifestPuller<>(
                fakeRegistryEndpointRequestProperties, "test-image-tag", ManifestTemplate.class)
            .handleResponse(mockResponse);
    ManifestTemplate manifestTemplate = manifestAndDigest.getManifest();

    MatcherAssert.assertThat(
        manifestTemplate, CoreMatchers.instanceOf(V22ManifestListTemplate.class));
    Assert.assertTrue(((V22ManifestListTemplate) manifestTemplate).getManifests().size() > 0);
    Assert.assertEquals(expectedDigest, manifestAndDigest.getDigest());
  }

  @Test
  public void testHandleResponse_v22ManifestList()
      throws URISyntaxException, IOException, UnknownManifestFormatException {
    Path v22ManifestListFile =
        Paths.get(Resources.getResource("core/json/v22manifest_list.json").toURI());
    InputStream v22ManifestList = new ByteArrayInputStream(Files.readAllBytes(v22ManifestListFile));

    DescriptorDigest expectedDigest = Digests.computeDigest(v22ManifestList).getDigest();
    v22ManifestList.reset();

    Mockito.when(mockResponse.getBody()).thenReturn(v22ManifestList);
    ManifestAndDigest<V22ManifestListTemplate> manifestAndDigest =
        new ManifestPuller<>(
                fakeRegistryEndpointRequestProperties,
                "test-image-tag",
                V22ManifestListTemplate.class)
            .handleResponse(mockResponse);
    V22ManifestListTemplate manifestTemplate = manifestAndDigest.getManifest();

    MatcherAssert.assertThat(
        manifestTemplate, CoreMatchers.instanceOf(V22ManifestListTemplate.class));
    Assert.assertTrue(manifestTemplate.getManifests().size() > 0);
    Assert.assertEquals(expectedDigest, manifestAndDigest.getDigest());
  }

  @Test
  public void testHandleResponse_noSchemaVersion() throws IOException {
    Mockito.when(mockResponse.getBody()).thenReturn(stringToInputStreamUtf8("{}"));
    try {
      testManifestPuller.handleResponse(mockResponse);
      Assert.fail("An empty manifest should throw an error");

    } catch (UnknownManifestFormatException ex) {
      Assert.assertEquals("Cannot find field 'schemaVersion' in manifest", ex.getMessage());
    }
  }

  @Test
  public void testHandleResponse_invalidSchemaVersion() throws IOException {
    Mockito.when(mockResponse.getBody())
        .thenReturn(stringToInputStreamUtf8("{\"schemaVersion\":\"not valid\"}"));
    try {
      testManifestPuller.handleResponse(mockResponse);
      Assert.fail("A non-integer schemaVersion should throw an error");

    } catch (UnknownManifestFormatException ex) {
      Assert.assertEquals("'schemaVersion' field is not an integer", ex.getMessage());
    }
  }

  @Test
  public void testHandleResponse_unknownSchemaVersion() throws IOException {
    Mockito.when(mockResponse.getBody())
        .thenReturn(stringToInputStreamUtf8("{\"schemaVersion\":0}"));
    try {
      testManifestPuller.handleResponse(mockResponse);
      Assert.fail("An unknown manifest schemaVersion should throw an error");

    } catch (UnknownManifestFormatException ex) {
      Assert.assertEquals("Unknown schemaVersion: 0 - only 1 and 2 are supported", ex.getMessage());
    }
  }

  @Test
  public void testHandleResponse_ociIndexWithNoMediaType()
      throws IOException, UnknownManifestFormatException {
    String ociManifestJson =
        "{\n"
            + "  \"schemaVersion\": 2,\n"
            + "  \"manifests\": [\n"
            + "    {\n"
            + "      \"mediaType\": \"application/vnd.oci.image.manifest.v1+json\",\n"
            + "      \"size\": 7143,\n"
            + "      \"digest\": \"sha256:e692418e4cbaf90ca69d05a66403747baa33ee08806650b51fab815ad7fc331f\"\n"
            + "    }\n"
            + "  ]\n"
            + "}";
    Mockito.when(mockResponse.getBody()).thenReturn(stringToInputStreamUtf8(ociManifestJson));

    ManifestTemplate manifest =
        new ManifestPuller<>(
                fakeRegistryEndpointRequestProperties, "test-image-tag", ManifestTemplate.class)
            .handleResponse(mockResponse)
            .getManifest();

    MatcherAssert.assertThat(manifest, CoreMatchers.instanceOf(OciIndexTemplate.class));
    OciIndexTemplate ociIndex = (OciIndexTemplate) manifest;

    Assert.assertEquals("application/vnd.oci.image.index.v1+json", manifest.getManifestMediaType());
    Assert.assertEquals(1, ociIndex.getManifests().size());
    Assert.assertEquals(
        "e692418e4cbaf90ca69d05a66403747baa33ee08806650b51fab815ad7fc331f",
        ociIndex.getManifests().get(0).getDigest().getHash());
  }

  @Test
  public void testHandleResponse_ociManfiestWithNoMediaType()
      throws IOException, UnknownManifestFormatException {
    String ociManifestJson =
        "{\n"
            + "  \"schemaVersion\": 2,\n"
            + "  \"config\": {\n"
            + "    \"mediaType\": \"application/vnd.oci.image.config.v1+json\",\n"
            + "    \"size\": 7023,\n"
            + "    \"digest\": \"sha256:b5b2b2c507a0944348e0303114d8d93aaaa081732b86451d9bce1f432a537bc7\"\n"
            + "  },\n"
            + "  \"layers\": []\n"
            + "}";
    Mockito.when(mockResponse.getBody()).thenReturn(stringToInputStreamUtf8(ociManifestJson));

    ManifestTemplate manifest =
        new ManifestPuller<>(
                fakeRegistryEndpointRequestProperties, "test-image-tag", ManifestTemplate.class)
            .handleResponse(mockResponse)
            .getManifest();

    MatcherAssert.assertThat(manifest, CoreMatchers.instanceOf(OciManifestTemplate.class));
    OciManifestTemplate ociManifest = (OciManifestTemplate) manifest;

    Assert.assertEquals(
        "application/vnd.oci.image.manifest.v1+json", manifest.getManifestMediaType());
    Assert.assertEquals(
        "b5b2b2c507a0944348e0303114d8d93aaaa081732b86451d9bce1f432a537bc7",
        ociManifest.getContainerConfiguration().getDigest().getHash());
  }

  @Test
  public void testHandleResponse_invalidOciManfiest() throws IOException {
    Mockito.when(mockResponse.getBody())
        .thenReturn(stringToInputStreamUtf8("{\"schemaVersion\": 2}"));

    ManifestPuller<ManifestTemplate> manifestPuller =
        new ManifestPuller<>(
            fakeRegistryEndpointRequestProperties, "test-image-tag", ManifestTemplate.class);
    try {
      manifestPuller.handleResponse(mockResponse);
      Assert.fail();
    } catch (UnknownManifestFormatException ex) {
      Assert.assertEquals(
          "'schemaVersion' is 2, but neither 'manifests' nor 'config' exists", ex.getMessage());
    }
  }

  @Test
  public void testGetApiRoute() throws MalformedURLException {
    Assert.assertEquals(
        new URL("http://someApiBase/someImageName/manifests/test-image-tag"),
        testManifestPuller.getApiRoute("http://someApiBase/"));
  }

  @Test
  public void testGetHttpMethod() {
    Assert.assertEquals("GET", testManifestPuller.getHttpMethod());
  }

  @Test
  public void testGetActionDescription() {
    Assert.assertEquals(
        "pull image manifest for someServerUrl/someImageName:test-image-tag",
        testManifestPuller.getActionDescription());
  }

  @Test
  public void testGetContent() {
    Assert.assertNull(testManifestPuller.getContent());
  }

  @Test
  public void testGetAccept() {
    Assert.assertEquals(
        Arrays.asList(
            OciManifestTemplate.MANIFEST_MEDIA_TYPE,
            V22ManifestTemplate.MANIFEST_MEDIA_TYPE,
            V21ManifestTemplate.MEDIA_TYPE,
            V22ManifestListTemplate.MANIFEST_MEDIA_TYPE),
        testManifestPuller.getAccept());

    Assert.assertEquals(
        Collections.singletonList(OciManifestTemplate.MANIFEST_MEDIA_TYPE),
        new ManifestPuller<>(
                fakeRegistryEndpointRequestProperties, "test-image-tag", OciManifestTemplate.class)
            .getAccept());
    Assert.assertEquals(
        Collections.singletonList(V22ManifestTemplate.MANIFEST_MEDIA_TYPE),
        new ManifestPuller<>(
                fakeRegistryEndpointRequestProperties, "test-image-tag", V22ManifestTemplate.class)
            .getAccept());
    Assert.assertEquals(
        Collections.singletonList(V21ManifestTemplate.MEDIA_TYPE),
        new ManifestPuller<>(
                fakeRegistryEndpointRequestProperties, "test-image-tag", V21ManifestTemplate.class)
            .getAccept());
    Assert.assertEquals(
        Collections.singletonList(V22ManifestListTemplate.MANIFEST_MEDIA_TYPE),
        new ManifestPuller<>(
                fakeRegistryEndpointRequestProperties,
                "test-image-tag",
                V22ManifestListTemplate.class)
            .getAccept());
  }
}
