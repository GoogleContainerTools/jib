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

import com.google.cloud.tools.jib.blob.Blob;
import com.google.cloud.tools.jib.blob.Blobs;
import com.google.cloud.tools.jib.hash.CountingDigestOutputStream;
import com.google.cloud.tools.jib.http.Response;
import com.google.cloud.tools.jib.image.DescriptorDigest;
import com.google.common.io.ByteStreams;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.DigestException;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

/** Tests for {@link BlobPuller}. */
@RunWith(MockitoJUnitRunner.class)
public class BlobPullerTest {

  private final RegistryEndpointRequestProperties fakeRegistryEndpointRequestProperties =
      new RegistryEndpointRequestProperties("someServerUrl", "someImageName");
  private DescriptorDigest fakeDigest;

  private final ByteArrayOutputStream layerContentOutputStream = new ByteArrayOutputStream();
  private final CountingDigestOutputStream layerOutputStream =
      new CountingDigestOutputStream(layerContentOutputStream);

  private BlobPuller testBlobPuller;

  @Before
  public void setUpFakes() throws DigestException {
    fakeDigest =
        DescriptorDigest.fromHash(
            "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa");

    testBlobPuller =
        new BlobPuller(fakeRegistryEndpointRequestProperties, fakeDigest, layerOutputStream);
  }

  @Test
  public void testHandleResponse() throws IOException, UnexpectedBlobDigestException {
    Blob testBlob = Blobs.from("some BLOB content");
    DescriptorDigest testBlobDigest = testBlob.writeTo(ByteStreams.nullOutputStream()).getDigest();

    Response mockResponse = Mockito.mock(Response.class);
    Mockito.when(mockResponse.getBody()).thenReturn(testBlob);

    BlobPuller blobPuller =
        new BlobPuller(fakeRegistryEndpointRequestProperties, testBlobDigest, layerOutputStream);
    blobPuller.handleResponse(mockResponse);
    Assert.assertEquals(
        "some BLOB content",
        new String(layerContentOutputStream.toByteArray(), StandardCharsets.UTF_8));
    Assert.assertEquals(testBlobDigest, layerOutputStream.toBlobDescriptor().getDigest());
  }

  @Test
  public void testHandleResponse_unexpectedDigest() throws IOException {
    Blob testBlob = Blobs.from("some BLOB content");
    DescriptorDigest testBlobDigest = testBlob.writeTo(ByteStreams.nullOutputStream()).getDigest();

    Response mockResponse = Mockito.mock(Response.class);
    Mockito.when(mockResponse.getBody()).thenReturn(testBlob);

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
  public void testGetApiRoute() throws MalformedURLException {
    Assert.assertEquals(
        new URL("http://someApiBase/someImageName/blobs/" + fakeDigest),
        testBlobPuller.getApiRoute("http://someApiBase/"));
  }

  @Test
  public void testGetActionDescription() {
    Assert.assertEquals(
        "pull BLOB for someServerUrl/someImageName with digest " + fakeDigest,
        testBlobPuller.getActionDescription());
  }

  @Test
  public void testGetHttpMethod() {
    Assert.assertEquals("GET", testBlobPuller.getHttpMethod());
  }

  @Test
  public void testGetContent() {
    Assert.assertNull(testBlobPuller.getContent());
  }

  @Test
  public void testGetAccept() {
    Assert.assertEquals(0, testBlobPuller.getAccept().size());
  }
}
