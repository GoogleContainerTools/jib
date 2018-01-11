/*
 * Copyright 2018 Google Inc.
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

import com.google.cloud.tools.crepecake.blob.Blob;
import com.google.cloud.tools.crepecake.blob.BlobDescriptor;
import com.google.cloud.tools.crepecake.blob.Blobs;
import com.google.cloud.tools.crepecake.http.Response;
import com.google.cloud.tools.crepecake.image.DescriptorDigest;
import com.google.common.io.ByteStreams;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.DigestException;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

/** Tests for {@link BlobPuller}. */
@RunWith(MockitoJUnitRunner.class)
public class BlobPullerTest {

  @Rule public TemporaryFolder temporaryFolder = new TemporaryFolder();

  @Mock private RegistryEndpointProperties mockRegistryEndpointProperties;

  private DescriptorDigest fakeDigest;
  private Path temporaryPath;

  @Before
  public void setUpFakes() throws DigestException, IOException {
    fakeDigest =
        DescriptorDigest.fromHash(
            "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa");
    temporaryPath = temporaryFolder.newFile().toPath();
  }

  @Test
  public void testInitializer_handleResponse() throws IOException, UnexpectedBlobDigestException {
    Blob testBlob = Blobs.from("some BLOB content");
    DescriptorDigest testBlobDigest = testBlob.writeTo(ByteStreams.nullOutputStream()).getDigest();

    Response mockResponse = Mockito.mock(Response.class);
    Mockito.when(mockResponse.getBody()).thenReturn(testBlob);

    BlobPuller blobPuller =
        new BlobPuller(mockRegistryEndpointProperties, testBlobDigest, temporaryPath);
    ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
    BlobDescriptor blobDescriptor =
        blobPuller.handleResponse(mockResponse).writeTo(byteArrayOutputStream);
    Assert.assertEquals(
        "some BLOB content",
        new String(byteArrayOutputStream.toByteArray(), StandardCharsets.UTF_8));
    Assert.assertEquals(testBlobDigest, blobDescriptor.getDigest());
  }

  @Test
  public void testInitializer_handleResponse_unexpectedDigest() throws IOException {
    Blob testBlob = Blobs.from("some BLOB content");
    DescriptorDigest testBlobDigest = testBlob.writeTo(ByteStreams.nullOutputStream()).getDigest();

    Response mockResponse = Mockito.mock(Response.class);
    Mockito.when(mockResponse.getBody()).thenReturn(testBlob);

    BlobPuller blobPuller =
        new BlobPuller(mockRegistryEndpointProperties, fakeDigest, temporaryPath);
    try {
      blobPuller.handleResponse(mockResponse);
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
  public void testInitializer_getApiRoute() throws MalformedURLException {
    BlobPuller blobPuller =
        new BlobPuller(mockRegistryEndpointProperties, fakeDigest, temporaryPath);
    Assert.assertEquals(
        new URL("http://someApiBase/blobs/" + fakeDigest),
        blobPuller.getApiRoute("http://someApiBase"));
  }

  @Test
  public void testInitializer_getActionDescription() {
    BlobPuller blobPuller =
        new BlobPuller(
            new RegistryEndpointProperties("someServer", "someImage"), fakeDigest, temporaryPath);
    Assert.assertEquals(
        "pull BLOB for someServer/someImage with digest " + fakeDigest,
        blobPuller.getActionDescription());
  }
}
