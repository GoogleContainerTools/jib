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

import com.google.api.client.http.HttpStatusCodes;
import com.google.cloud.tools.jib.api.DescriptorDigest;
import com.google.cloud.tools.jib.blob.BlobDescriptor;
import com.google.cloud.tools.jib.http.Response;
import com.google.cloud.tools.jib.http.ResponseException;
import com.google.cloud.tools.jib.json.JsonTemplateMapper;
import com.google.cloud.tools.jib.registry.json.ErrorEntryTemplate;
import com.google.cloud.tools.jib.registry.json.ErrorResponseTemplate;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.security.DigestException;
import org.hamcrest.CoreMatchers;
import org.hamcrest.MatcherAssert;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

/** Tests for {@link BlobChecker}. */
@RunWith(MockitoJUnitRunner.class)
public class BlobCheckerTest {

  @Mock private Response mockResponse;

  private final RegistryEndpointRequestProperties fakeRegistryEndpointRequestProperties =
      new RegistryEndpointRequestProperties("someServerUrl", "someImageName");

  private BlobChecker testBlobChecker;
  private DescriptorDigest fakeDigest;

  @Before
  public void setUpFakes() throws DigestException {
    fakeDigest =
        DescriptorDigest.fromHash(
            "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa");
    testBlobChecker = new BlobChecker(fakeRegistryEndpointRequestProperties, fakeDigest);
  }

  @Test
  public void testHandleResponse() throws RegistryErrorException {
    Mockito.when(mockResponse.getContentLength()).thenReturn(0L);
    BlobDescriptor expectedBlobDescriptor = new BlobDescriptor(0, fakeDigest);

    BlobDescriptor blobDescriptor = testBlobChecker.handleResponse(mockResponse).get();

    Assert.assertEquals(expectedBlobDescriptor, blobDescriptor);
  }

  @Test
  public void testHandleResponse_noContentLength() {
    Mockito.when(mockResponse.getContentLength()).thenReturn(-1L);

    try {
      testBlobChecker.handleResponse(mockResponse);
      Assert.fail("Should throw exception if Content-Length header is not present");

    } catch (RegistryErrorException ex) {
      MatcherAssert.assertThat(
          ex.getMessage(), CoreMatchers.containsString("Did not receive Content-Length header"));
    }
  }

  @Test
  public void testHandleHttpResponseException() throws IOException {
    ResponseException mockResponseException = Mockito.mock(ResponseException.class);
    Mockito.when(mockResponseException.getStatusCode())
        .thenReturn(HttpStatusCodes.STATUS_CODE_NOT_FOUND);

    ErrorResponseTemplate emptyErrorResponseTemplate =
        new ErrorResponseTemplate()
            .addError(new ErrorEntryTemplate(ErrorCodes.BLOB_UNKNOWN.name(), "some message"));
    Mockito.when(mockResponseException.getContent())
        .thenReturn(JsonTemplateMapper.toUtf8String(emptyErrorResponseTemplate));

    Assert.assertFalse(
        testBlobChecker.handleHttpResponseException(mockResponseException).isPresent());
  }

  @Test
  public void testHandleHttpResponseException_hasOtherErrors() throws IOException {
    ResponseException mockResponseException = Mockito.mock(ResponseException.class);
    Mockito.when(mockResponseException.getStatusCode())
        .thenReturn(HttpStatusCodes.STATUS_CODE_NOT_FOUND);

    ErrorResponseTemplate emptyErrorResponseTemplate =
        new ErrorResponseTemplate()
            .addError(new ErrorEntryTemplate(ErrorCodes.BLOB_UNKNOWN.name(), "some message"))
            .addError(new ErrorEntryTemplate(ErrorCodes.MANIFEST_UNKNOWN.name(), "some message"));
    Mockito.when(mockResponseException.getContent())
        .thenReturn(JsonTemplateMapper.toUtf8String(emptyErrorResponseTemplate));

    try {
      testBlobChecker.handleHttpResponseException(mockResponseException);
      Assert.fail("Non-BLOB_UNKNOWN errors should not be handled");

    } catch (ResponseException ex) {
      Assert.assertEquals(mockResponseException, ex);
    }
  }

  @Test
  public void testHandleHttpResponseException_notBlobUnknown() throws IOException {
    ResponseException mockResponseException = Mockito.mock(ResponseException.class);
    Mockito.when(mockResponseException.getStatusCode())
        .thenReturn(HttpStatusCodes.STATUS_CODE_NOT_FOUND);

    ErrorResponseTemplate emptyErrorResponseTemplate = new ErrorResponseTemplate();
    Mockito.when(mockResponseException.getContent())
        .thenReturn(JsonTemplateMapper.toUtf8String(emptyErrorResponseTemplate));

    try {
      testBlobChecker.handleHttpResponseException(mockResponseException);
      Assert.fail("Non-BLOB_UNKNOWN errors should not be handled");

    } catch (ResponseException ex) {
      Assert.assertEquals(mockResponseException, ex);
    }
  }

  @Test
  public void testHandleHttpResponseException_invalidStatusCode() {
    ResponseException mockResponseException = Mockito.mock(ResponseException.class);
    Mockito.when(mockResponseException.getStatusCode()).thenReturn(-1);

    try {
      testBlobChecker.handleHttpResponseException(mockResponseException);
      Assert.fail("Non-404 status codes should not be handled");

    } catch (ResponseException ex) {
      Assert.assertEquals(mockResponseException, ex);
    }
  }

  @Test
  public void testGetApiRoute() throws MalformedURLException {
    Assert.assertEquals(
        new URL("http://someApiBase/someImageName/blobs/" + fakeDigest),
        testBlobChecker.getApiRoute("http://someApiBase/"));
  }

  @Test
  public void testGetContent() {
    Assert.assertNull(testBlobChecker.getContent());
  }

  @Test
  public void testGetAccept() {
    Assert.assertEquals(0, testBlobChecker.getAccept().size());
  }

  @Test
  public void testGetActionDescription() {
    Assert.assertEquals(
        "check BLOB exists for someServerUrl/someImageName with digest " + fakeDigest,
        testBlobChecker.getActionDescription());
  }

  @Test
  public void testGetHttpMethod() {
    Assert.assertEquals("HEAD", testBlobChecker.getHttpMethod());
  }
}
