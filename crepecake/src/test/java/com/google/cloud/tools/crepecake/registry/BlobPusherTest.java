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
import com.google.cloud.tools.crepecake.http.Request;
import com.google.cloud.tools.crepecake.http.Response;
import com.google.cloud.tools.crepecake.image.DescriptorDigest;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.security.DigestException;
import java.util.Arrays;
import java.util.Collections;
import org.hamcrest.CoreMatchers;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

/** Tests for {@link BlobPusher}. */
@RunWith(MockitoJUnitRunner.class)
public class BlobPusherTest {

  @Mock private Blob mockBlob;

  private DescriptorDigest fakeDescriptorDigest;
  private BlobPusher testBlobPusher;

  @Before
  public void setUpFakes() throws DigestException {
    fakeDescriptorDigest =
        DescriptorDigest.fromHash(
            "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa");
    testBlobPusher = new BlobPusher(fakeDescriptorDigest, mockBlob);
  }

  @Test
  public void testInitializer_buildRequest() {
    Request.Builder mockRequestBuilder = Mockito.mock(Request.Builder.class);
    testBlobPusher.initializer().buildRequest(mockRequestBuilder);
    Mockito.verify(mockRequestBuilder, Mockito.never()).setBody(Mockito.any());
  }

  @Test
  public void testInitializer_handleResponse_created() throws IOException, RegistryException {
    Response mockResponse = Mockito.mock(Response.class);

    Mockito.when(mockResponse.getStatusCode()).thenReturn(201); // Created
    Assert.assertNull(testBlobPusher.initializer().handleResponse(mockResponse));
  }

  @Test
  public void testInitializer_handleResponse_accepted() throws IOException, RegistryException {
    Response mockResponse = Mockito.mock(Response.class);

    Mockito.when(mockResponse.getStatusCode()).thenReturn(202); // Accepted
    Mockito.when(mockResponse.getHeader("Location"))
        .thenReturn(Collections.singletonList("location"));
    Assert.assertEquals("location", testBlobPusher.initializer().handleResponse(mockResponse));
  }

  @Test
  public void testInitializer_handleResponse_accepted_multipleLocations()
      throws IOException, RegistryException {
    Response mockResponse = Mockito.mock(Response.class);

    Mockito.when(mockResponse.getStatusCode()).thenReturn(202); // Accepted
    Mockito.when(mockResponse.getHeader("Location"))
        .thenReturn(Arrays.asList("location1", "location2"));
    try {
      testBlobPusher.initializer().handleResponse(mockResponse);
      Assert.fail("Multiple 'Location' headers should be a registry error");

    } catch (RegistryErrorException ex) {
      Assert.assertThat(
          ex.getMessage(),
          CoreMatchers.containsString("Expected 1 'Location' header, but found 2"));
    }
  }

  @Test
  public void testInitializer_handleResponse_unrecognized() throws IOException, RegistryException {
    Response mockResponse = Mockito.mock(Response.class);

    Mockito.when(mockResponse.getStatusCode()).thenReturn(-1); // Unrecognized
    try {
      testBlobPusher.initializer().handleResponse(mockResponse);
      Assert.fail("Multiple 'Location' headers should be a registry error");

    } catch (RegistryErrorException ex) {
      Assert.assertThat(
          ex.getMessage(), CoreMatchers.containsString("Received unrecognized status code -1"));
    }
  }

  @Test
  public void testInitializer_getApiRouteSuffix() throws MalformedURLException {
    Assert.assertEquals(
        new URL("http://someApiBase/blobs/uploads/?mount=" + fakeDescriptorDigest),
        testBlobPusher.initializer().getApiRoute("http://someApiBase"));
  }

  @Test
  public void testInitializer_getHttpMethod() {
    Assert.assertEquals("POST", testBlobPusher.initializer().getHttpMethod());
  }

  @Test
  public void testInitializer_getActionDescription() {
    Assert.assertEquals(
        "push BLOB for someServer/someImage with digest " + fakeDescriptorDigest,
        testBlobPusher.initializer().getActionDescription("someServer", "someImage"));
  }

  @Test
  public void testWriter_buildRequest() {
    Request.Builder mockRequestBuilder = Mockito.mock(Request.Builder.class);
    testBlobPusher.writer().buildRequest(mockRequestBuilder);
    Mockito.verify(mockRequestBuilder).setContentType("application/octet-stream");
    Mockito.verify(mockRequestBuilder).setBody(mockBlob);
  }

  @Test
  public void testWriter_handleResponse() throws IOException, RegistryException {
    Response mockResponse = Mockito.mock(Response.class);

    Mockito.when(mockResponse.getHeader("Location"))
        .thenReturn(Collections.singletonList("location"));
    Assert.assertEquals("location", testBlobPusher.writer().handleResponse(mockResponse));
  }

  @Test
  public void testWriter_getApiRouteSuffix() throws MalformedURLException {
    Assert.assertNull(testBlobPusher.writer().getApiRoute(""));
  }

  @Test
  public void testWriter_getHttpMethod() {
    Assert.assertEquals("PATCH", testBlobPusher.writer().getHttpMethod());
  }

  @Test
  public void testWriter_getActionDescription() {
    Assert.assertEquals(
        "push BLOB for someServer/someImage with digest " + fakeDescriptorDigest,
        testBlobPusher.writer().getActionDescription("someServer", "someImage"));
  }

  @Test
  public void testCommitter() throws IOException, RegistryException {
    Assert.assertNull(testBlobPusher.committer().handleResponse(Mockito.mock(Response.class)));
    Assert.assertNull(testBlobPusher.committer().getApiRoute(""));
    Assert.assertEquals("PUT", testBlobPusher.committer().getHttpMethod());
    Assert.assertNull(testBlobPusher.committer().getActionDescription("", ""));
  }

  @Test
  public void testGetCommitUrl() throws MalformedURLException {
    Assert.assertEquals(
        "https://someurl?somequery=somevalue&digest=" + fakeDescriptorDigest,
        testBlobPusher.getCommitUrl(new URL("https://someurl?somequery=somevalue")).toString());
  }
}
