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

import com.google.api.client.http.GenericUrl;
import com.google.cloud.tools.jib.api.DescriptorDigest;
import com.google.cloud.tools.jib.api.RegistryException;
import com.google.cloud.tools.jib.blob.Blob;
import com.google.cloud.tools.jib.blob.Blobs;
import com.google.cloud.tools.jib.http.BlobHttpContent;
import com.google.cloud.tools.jib.http.Response;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.DigestException;
import java.util.Arrays;
import java.util.Collections;
import java.util.concurrent.atomic.LongAdder;
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

/** Tests for {@link BlobPusher}. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class BlobPusherTest {

  private static final String TEST_BLOB_CONTENT = "some BLOB content";
  private static final Blob TEST_BLOB = Blobs.from(TEST_BLOB_CONTENT);

  @Mock private URL mockUrl;
  @Mock private Response mockResponse;

  private DescriptorDigest fakeDescriptorDigest;
  private BlobPusher testBlobPusher;

  @BeforeEach
  void setUpFakes() throws DigestException {
    fakeDescriptorDigest =
        DescriptorDigest.fromHash(
            "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa");
    testBlobPusher =
        new BlobPusher(
            new RegistryEndpointRequestProperties("someServerUrl", "someImageName"),
            fakeDescriptorDigest,
            TEST_BLOB,
            null);
  }

  @Test
  void testInitializer_getContent() {
    Assert.assertNull(testBlobPusher.initializer().getContent());
  }

  @Test
  void testGetAccept() {
    Assert.assertEquals(0, testBlobPusher.initializer().getAccept().size());
  }

  @Test
  void testInitializer_handleResponse_created() throws IOException, RegistryException {
    Mockito.when(mockResponse.getStatusCode()).thenReturn(201); // Created
    Assert.assertFalse(testBlobPusher.initializer().handleResponse(mockResponse).isPresent());
  }

  @Test
  void testInitializer_handleResponse_accepted() throws IOException, RegistryException {
    Mockito.when(mockResponse.getStatusCode()).thenReturn(202); // Accepted
    Mockito.when(mockResponse.getHeader("Location"))
        .thenReturn(Collections.singletonList("location"));
    GenericUrl requestUrl = new GenericUrl("https://someurl");
    Mockito.when(mockResponse.getRequestUrl()).thenReturn(requestUrl);
    Assert.assertEquals(
        new URL("https://someurl/location"),
        testBlobPusher.initializer().handleResponse(mockResponse).get());
  }

  @Test
  void testInitializer_handleResponse_accepted_multipleLocations()
      throws IOException, RegistryException {
    Mockito.when(mockResponse.getStatusCode()).thenReturn(202); // Accepted
    Mockito.when(mockResponse.getHeader("Location"))
        .thenReturn(Arrays.asList("location1", "location2"));
    try {
      testBlobPusher.initializer().handleResponse(mockResponse);
      Assert.fail("Multiple 'Location' headers should be a registry error");

    } catch (RegistryErrorException ex) {
      MatcherAssert.assertThat(
          ex.getMessage(),
          CoreMatchers.containsString("Expected 1 'Location' header, but found 2"));
    }
  }

  @Test
  void testInitializer_handleResponse_unrecognized() throws IOException, RegistryException {
    Mockito.when(mockResponse.getStatusCode()).thenReturn(-1); // Unrecognized
    try {
      testBlobPusher.initializer().handleResponse(mockResponse);
      Assert.fail("Multiple 'Location' headers should be a registry error");

    } catch (RegistryErrorException ex) {
      MatcherAssert.assertThat(
          ex.getMessage(), CoreMatchers.containsString("Received unrecognized status code -1"));
    }
  }

  @Test
  void testInitializer_getApiRoute_nullSource() throws MalformedURLException {
    Assert.assertEquals(
        new URL("http://someApiBase/someImageName/blobs/uploads/"),
        testBlobPusher.initializer().getApiRoute("http://someApiBase/"));
  }

  @Test
  void testInitializer_getApiRoute_sameSource() throws MalformedURLException {
    testBlobPusher =
        new BlobPusher(
            new RegistryEndpointRequestProperties("someServerUrl", "someImageName"),
            fakeDescriptorDigest,
            TEST_BLOB,
            "sourceImageName");

    Assert.assertEquals(
        new URL(
            "http://someApiBase/someImageName/blobs/uploads/?mount="
                + fakeDescriptorDigest
                + "&from=sourceImageName"),
        testBlobPusher.initializer().getApiRoute("http://someApiBase/"));
  }

  @Test
  void testInitializer_getHttpMethod() {
    Assert.assertEquals("POST", testBlobPusher.initializer().getHttpMethod());
  }

  @Test
  void testInitializer_getActionDescription() {
    Assert.assertEquals(
        "push BLOB for someServerUrl/someImageName with digest " + fakeDescriptorDigest,
        testBlobPusher.initializer().getActionDescription());
  }

  @Test
  void testWriter_getContent() throws IOException {
    LongAdder byteCount = new LongAdder();
    BlobHttpContent body = testBlobPusher.writer(mockUrl, byteCount::add).getContent();

    Assert.assertNotNull(body);
    Assert.assertEquals("application/octet-stream", body.getType());

    ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
    body.writeTo(byteArrayOutputStream);

    Assert.assertEquals(
        TEST_BLOB_CONTENT, new String(byteArrayOutputStream.toByteArray(), StandardCharsets.UTF_8));
    Assert.assertEquals(TEST_BLOB_CONTENT.length(), byteCount.sum());
  }

  @Test
  void testWriter_GetAccept() {
    Assert.assertEquals(0, testBlobPusher.writer(mockUrl, ignored -> {}).getAccept().size());
  }

  @Test
  void testWriter_handleResponse() throws IOException, RegistryException {
    Mockito.when(mockResponse.getHeader("Location"))
        .thenReturn(Collections.singletonList("https://somenewurl/location"));
    GenericUrl requestUrl = new GenericUrl("https://someurl");
    Mockito.when(mockResponse.getRequestUrl()).thenReturn(requestUrl);
    Assert.assertEquals(
        new URL("https://somenewurl/location"),
        testBlobPusher.writer(mockUrl, ignored -> {}).handleResponse(mockResponse));
  }

  @Test
  void testWriter_getApiRoute() throws MalformedURLException {
    URL fakeUrl = new URL("http://someurl");
    Assert.assertEquals(fakeUrl, testBlobPusher.writer(fakeUrl, ignored -> {}).getApiRoute(""));
  }

  @Test
  void testWriter_getHttpMethod() {
    Assert.assertEquals("PATCH", testBlobPusher.writer(mockUrl, ignored -> {}).getHttpMethod());
  }

  @Test
  void testWriter_getActionDescription() {
    Assert.assertEquals(
        "push BLOB for someServerUrl/someImageName with digest " + fakeDescriptorDigest,
        testBlobPusher.writer(mockUrl, ignored -> {}).getActionDescription());
  }

  @Test
  void testCommitter_getContent() {
    Assert.assertNull(testBlobPusher.committer(mockUrl).getContent());
  }

  @Test
  void testCommitter_GetAccept() {
    Assert.assertEquals(0, testBlobPusher.committer(mockUrl).getAccept().size());
  }

  @Test
  void testCommitter_handleResponse() throws IOException, RegistryException {
    Assert.assertNull(
        testBlobPusher.committer(mockUrl).handleResponse(Mockito.mock(Response.class)));
  }

  @Test
  void testCommitter_getApiRoute() throws MalformedURLException {
    Assert.assertEquals(
        new URL("https://someurl?somequery=somevalue&digest=" + fakeDescriptorDigest),
        testBlobPusher.committer(new URL("https://someurl?somequery=somevalue")).getApiRoute(""));
  }

  @Test
  void testCommitter_getHttpMethod() {
    Assert.assertEquals("PUT", testBlobPusher.committer(mockUrl).getHttpMethod());
  }

  @Test
  void testCommitter_getActionDescription() {
    Assert.assertEquals(
        "push BLOB for someServerUrl/someImageName with digest " + fakeDescriptorDigest,
        testBlobPusher.committer(mockUrl).getActionDescription());
  }
}
