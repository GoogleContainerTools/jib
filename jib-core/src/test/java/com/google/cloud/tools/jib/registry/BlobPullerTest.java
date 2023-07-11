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
import com.google.cloud.tools.jib.hash.CountingDigestOutputStream;
import com.google.cloud.tools.jib.hash.Digests;
import com.google.cloud.tools.jib.http.Response;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.DigestException;
import java.util.concurrent.atomic.LongAdder;
import org.junit.Assert;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/** Tests for {@link BlobPuller}. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class BlobPullerTest {

  private final RegistryEndpointRequestProperties fakeRegistryEndpointRequestProperties =
      new RegistryEndpointRequestProperties("someServerUrl", "someImageName");
  private DescriptorDigest fakeDigest;

  private final ByteArrayOutputStream layerContentOutputStream = new ByteArrayOutputStream();
  private final CountingDigestOutputStream layerOutputStream =
      new CountingDigestOutputStream(layerContentOutputStream);

  private BlobPuller testBlobPuller;

  @BeforeEach
  void setUpFakes() throws DigestException {
    fakeDigest =
        DescriptorDigest.fromHash(
            "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa");

    testBlobPuller =
        new BlobPuller(
            fakeRegistryEndpointRequestProperties,
            fakeDigest,
            layerOutputStream,
            ignored -> {},
            ignored -> {});
  }

  @Test
  void testHandleResponse() throws IOException, UnexpectedBlobDigestException {
    InputStream blobContent =
        new ByteArrayInputStream("some BLOB content".getBytes(StandardCharsets.UTF_8));
    DescriptorDigest testBlobDigest = Digests.computeDigest(blobContent).getDigest();
    blobContent.reset();

    Response mockResponse = Mockito.mock(Response.class);
    Mockito.when(mockResponse.getContentLength()).thenReturn((long) "some BLOB content".length());
    Mockito.when(mockResponse.getBody()).thenReturn(blobContent);

    LongAdder byteCount = new LongAdder();
    BlobPuller blobPuller =
        new BlobPuller(
            fakeRegistryEndpointRequestProperties,
            testBlobDigest,
            layerOutputStream,
            size -> Assert.assertEquals("some BLOB content".length(), size.longValue()),
            byteCount::add);
    blobPuller.handleResponse(mockResponse);
    Assert.assertEquals(
        "some BLOB content",
        new String(layerContentOutputStream.toByteArray(), StandardCharsets.UTF_8));
    Assert.assertEquals(testBlobDigest, layerOutputStream.computeDigest().getDigest());
    Assert.assertEquals("some BLOB content".length(), byteCount.sum());
  }

  @Test
  void testHandleResponse_unexpectedDigest() throws IOException {
    InputStream blobContent =
        new ByteArrayInputStream("some BLOB content".getBytes(StandardCharsets.UTF_8));
    DescriptorDigest testBlobDigest = Digests.computeDigest(blobContent).getDigest();
    blobContent.reset();

    Response mockResponse = Mockito.mock(Response.class);
    Mockito.when(mockResponse.getBody()).thenReturn(blobContent);

    try {
      testBlobPuller.handleResponse(mockResponse);
      Assert.fail("Receiving an unexpected digest should fail");

    } catch (UnexpectedBlobDigestException ex) {
      Assert.assertEquals(
          "The pulled BLOB has digest '"
              + testBlobDigest
              + "', but the request digest was '"
              + fakeDigest
              + "'",
          ex.getMessage());
    }
  }

  @Test
  void testGetApiRoute() throws MalformedURLException {
    Assert.assertEquals(
        new URL("http://someApiBase/someImageName/blobs/" + fakeDigest),
        testBlobPuller.getApiRoute("http://someApiBase/"));
  }

  @Test
  void testGetActionDescription() {
    Assert.assertEquals(
        "pull BLOB for someServerUrl/someImageName with digest " + fakeDigest,
        testBlobPuller.getActionDescription());
  }

  @Test
  void testGetHttpMethod() {
    Assert.assertEquals("GET", testBlobPuller.getHttpMethod());
  }

  @Test
  void testGetContent() {
    Assert.assertNull(testBlobPuller.getContent());
  }

  @Test
  void testGetAccept() {
    Assert.assertEquals(0, testBlobPuller.getAccept().size());
  }
}
