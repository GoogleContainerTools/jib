/*
 * Copyright 2018 Google LLC. All rights reserved.
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

import com.google.api.client.http.HttpHeaders;
import com.google.api.client.http.HttpResponse;
import com.google.api.client.http.HttpResponseException;
import com.google.api.client.http.HttpStatusCodes;
import com.google.cloud.tools.jib.blob.Blob;
import com.google.cloud.tools.jib.blob.Blobs;
import com.google.cloud.tools.jib.http.Authorizations;
import com.google.cloud.tools.jib.http.BlobHttpContent;
import com.google.cloud.tools.jib.http.Connection;
import com.google.cloud.tools.jib.http.MockConnection;
import com.google.cloud.tools.jib.http.Response;
import com.google.cloud.tools.jib.json.JsonTemplateMapper;
import com.google.cloud.tools.jib.registry.json.ErrorEntryTemplate;
import com.google.cloud.tools.jib.registry.json.ErrorResponseTemplate;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;
import javax.annotation.Nullable;
import javax.net.ssl.SSLPeerUnverifiedException;
import org.apache.http.NoHttpResponseException;
import org.apache.http.conn.HttpHostConnectException;
import org.hamcrest.CoreMatchers;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

/** Tests for {@link RegistryEndpointCaller}. */
@RunWith(MockitoJUnitRunner.class)
public class RegistryEndpointCallerTest {

  /** Implementation of {@link RegistryEndpointProvider} for testing. */
  private static class TestRegistryEndpointProvider implements RegistryEndpointProvider<String> {

    @Override
    public String getHttpMethod() {
      return "httpMethod";
    }

    @Override
    public URL getApiRoute(String apiRouteBase) throws MalformedURLException {
      return new URL(apiRouteBase + "/api");
    }

    @Nullable
    @Override
    public BlobHttpContent getContent() {
      return null;
    }

    @Override
    public List<String> getAccept() {
      return Collections.emptyList();
    }

    @Nullable
    @Override
    public String handleResponse(Response response) throws IOException {
      return Blobs.writeToString(response.getBody());
    }

    @Override
    public String getActionDescription() {
      return "actionDescription";
    }
  }

  @Mock private Connection mockConnection;
  @Mock private Response mockResponse;
  @Mock private Function<URL, Connection> mockConnectionFactory;
  @Mock private HttpResponse mockHttpResponse;

  private RegistryEndpointCaller<String> testRegistryEndpointCallerSecure;

  @Before
  public void setUp() throws IOException {
    testRegistryEndpointCallerSecure =
        new RegistryEndpointCaller<>(
            "userAgent",
            "apiRouteBase",
            new TestRegistryEndpointProvider(),
            Authorizations.withBasicToken("token"),
            new RegistryEndpointRequestProperties("serverUrl", "imageName"),
            false,
            mockConnectionFactory);

    Mockito.when(mockConnectionFactory.apply(Mockito.any())).thenReturn(mockConnection);
    Mockito.when(mockHttpResponse.parseAsString()).thenReturn("");
    Mockito.when(mockHttpResponse.getHeaders()).thenReturn(new HttpHeaders());
  }

  @After
  public void tearDown() {
    System.clearProperty("jib.httpTimeout");
  }

  @Test
  public void testCall_httpsPeerUnverified() throws IOException, RegistryException {
    verifyRetriesWithHttp(SSLPeerUnverifiedException.class);
  }

  @Test
  public void testCall_retryWithHttp() throws IOException, RegistryException {
    verifyRetriesWithHttp(HttpHostConnectException.class);
  }

  @Test
  public void testCall_noHttpResponse() throws IOException, RegistryException {
    NoHttpResponseException mockNoHttpResponseException =
        Mockito.mock(NoHttpResponseException.class);
    Mockito.when(mockConnection.send(Mockito.eq("httpMethod"), Mockito.any()))
        .thenThrow(mockNoHttpResponseException);

    try {
      testRegistryEndpointCallerSecure.call();
      Assert.fail("Call should have failed");

    } catch (RegistryNoResponseException ex) {
      Assert.assertSame(mockNoHttpResponseException, ex.getCause());
    }
  }

  @Test
  public void testCall_unauthorized() throws IOException, RegistryException {
    verifyThrowsRegistryUnauthorizedException(HttpStatusCodes.STATUS_CODE_UNAUTHORIZED);
  }

  @Test
  public void testCall_credentialsNotSent() throws IOException, RegistryException {
    // Mocks a response for temporary redirect to a new location.
    Mockito.when(mockHttpResponse.getStatusCode())
        .thenReturn(HttpStatusCodes.STATUS_CODE_UNAUTHORIZED);
    Mockito.when(mockHttpResponse.getHeaders())
        .thenReturn(new HttpHeaders().setLocation("http://location"));

    HttpResponseException httpResponseException = new HttpResponseException(mockHttpResponse);
    Mockito.when(mockConnection.send(Mockito.eq("httpMethod"), Mockito.any()))
        .thenThrow(httpResponseException)
        .thenReturn(mockResponse);

    RegistryEndpointCaller<String> testRegistryEndpointCallerInsecure =
        new RegistryEndpointCaller<>(
            "userAgent",
            "apiRouteBase",
            new TestRegistryEndpointProvider(),
            Authorizations.withBasicToken("token"),
            new RegistryEndpointRequestProperties("serverUrl", "imageName"),
            true,
            mockConnectionFactory);
    try {
      testRegistryEndpointCallerInsecure.call(new URL("http://location"));
      Assert.fail("Call should have failed");

    } catch (RegistryCredentialsNotSentException ex) {
      Assert.assertEquals(
          "Required credentials for serverUrl/imageName were not sent because the connection was over HTTP",
          ex.getMessage());
    }
  }

  @Test
  public void testCall_forbidden() throws IOException, RegistryException {
    verifyThrowsRegistryUnauthorizedException(HttpStatusCodes.STATUS_CODE_FORBIDDEN);
  }

  @Test
  public void testCall_badRequest() throws IOException, RegistryException {
    verifyThrowsRegistryErrorException(HttpStatusCodes.STATUS_CODE_BAD_REQUEST);
  }

  @Test
  public void testCall_notFound() throws IOException, RegistryException {
    verifyThrowsRegistryErrorException(HttpStatusCodes.STATUS_CODE_NOT_FOUND);
  }

  @Test
  public void testCall_methodNotAllowed() throws IOException, RegistryException {
    verifyThrowsRegistryErrorException(HttpStatusCodes.STATUS_CODE_METHOD_NOT_ALLOWED);
  }

  @Test
  public void testCall_unknown() throws IOException, RegistryException {
    Mockito.when(mockHttpResponse.getStatusCode())
        .thenReturn(HttpStatusCodes.STATUS_CODE_SERVER_ERROR);
    HttpResponseException httpResponseException = new HttpResponseException(mockHttpResponse);

    Mockito.when(mockConnection.send(Mockito.eq("httpMethod"), Mockito.any()))
        .thenThrow(httpResponseException);

    try {
      testRegistryEndpointCallerSecure.call();
      Assert.fail("Call should have failed");

    } catch (HttpResponseException ex) {
      Assert.assertSame(httpResponseException, ex);
    }
  }

  @Test
  public void testCall_temporaryRedirect() throws IOException, RegistryException {
    verifyRetriesWithNewLocation(HttpStatusCodes.STATUS_CODE_TEMPORARY_REDIRECT);
  }

  @Test
  public void testCall_movedPermanently() throws IOException, RegistryException {
    verifyRetriesWithNewLocation(HttpStatusCodes.STATUS_CODE_MOVED_PERMANENTLY);
  }

  @Test
  public void testCall_permanentRedirect() throws IOException, RegistryException {
    verifyRetriesWithNewLocation(RegistryEndpointCaller.STATUS_CODE_PERMANENT_REDIRECT);
  }

  @Test
  public void testCall_disallowInsecure() throws IOException, RegistryException {
    // Mocks a response for temporary redirect to a new location.
    Mockito.when(mockHttpResponse.getStatusCode())
        .thenReturn(HttpStatusCodes.STATUS_CODE_TEMPORARY_REDIRECT);
    Mockito.when(mockHttpResponse.getHeaders())
        .thenReturn(new HttpHeaders().setLocation("http://newlocation"));

    HttpResponseException httpResponseException = new HttpResponseException(mockHttpResponse);
    Mockito.when(mockConnection.send(Mockito.eq("httpMethod"), Mockito.any()))
        .thenThrow(httpResponseException)
        .thenReturn(mockResponse);

    try {
      testRegistryEndpointCallerSecure.call();
      Assert.fail("Call should have failed");

    } catch (InsecureRegistryException ex) {
      // pass
    }
  }

  @Test
  public void testHttpTimeout_propertyNotSet() throws IOException, RegistryException {
    MockConnection mockConnection = new MockConnection((httpMethod, request) -> mockResponse);
    Mockito.when(mockConnectionFactory.apply(Mockito.any())).thenReturn(mockConnection);
    Mockito.when(mockResponse.getBody()).thenReturn(Mockito.mock(Blob.class));

    Assert.assertNull(System.getProperty("jib.httpTimeout"));
    testRegistryEndpointCallerSecure.call();

    // We fall back to the default timeout:
    // https://github.com/GoogleContainerTools/jib/pull/656#discussion_r203562639
    Assert.assertNull(mockConnection.getRequestedHttpTimeout());
  }

  @Test
  public void testHttpTimeout_stringValue() throws IOException, RegistryException {
    MockConnection mockConnection = new MockConnection((httpMethod, request) -> mockResponse);
    Mockito.when(mockConnectionFactory.apply(Mockito.any())).thenReturn(mockConnection);
    Mockito.when(mockResponse.getBody()).thenReturn(Mockito.mock(Blob.class));

    System.setProperty("jib.httpTimeout", "random string");
    testRegistryEndpointCallerSecure.call();

    Assert.assertNull(mockConnection.getRequestedHttpTimeout());
  }

  @Test
  public void testHttpTimeout_negativeValue() throws IOException, RegistryException {
    MockConnection mockConnection = new MockConnection((httpMethod, request) -> mockResponse);
    Mockito.when(mockConnectionFactory.apply(Mockito.any())).thenReturn(mockConnection);
    Mockito.when(mockResponse.getBody()).thenReturn(Mockito.mock(Blob.class));

    System.setProperty("jib.httpTimeout", "-1");
    testRegistryEndpointCallerSecure.call();

    // We let the negative value pass through:
    // https://github.com/GoogleContainerTools/jib/pull/656#discussion_r203562639
    Assert.assertEquals(Integer.valueOf(-1), mockConnection.getRequestedHttpTimeout());
  }

  @Test
  public void testHttpTimeout_0accepted() throws IOException, RegistryException {
    System.setProperty("jib.httpTimeout", "0");

    MockConnection mockConnection = new MockConnection((httpMethod, request) -> mockResponse);
    Mockito.when(mockConnectionFactory.apply(Mockito.any())).thenReturn(mockConnection);

    Mockito.when(mockResponse.getBody()).thenReturn(Mockito.mock(Blob.class));
    testRegistryEndpointCallerSecure.call();

    Assert.assertEquals(Integer.valueOf(0), mockConnection.getRequestedHttpTimeout());
  }

  @Test
  public void testHttpTimeout() throws IOException, RegistryException {
    System.setProperty("jib.httpTimeout", "7593");

    MockConnection mockConnection = new MockConnection((httpMethod, request) -> mockResponse);
    Mockito.when(mockConnectionFactory.apply(Mockito.any())).thenReturn(mockConnection);

    Mockito.when(mockResponse.getBody()).thenReturn(Mockito.mock(Blob.class));
    testRegistryEndpointCallerSecure.call();

    Assert.assertEquals(Integer.valueOf(7593), mockConnection.getRequestedHttpTimeout());
  }

  /** Verifies a request is retried with HTTP protocol if {@code exceptionClass} is thrown. */
  private void verifyRetriesWithHttp(Class<? extends Throwable> exceptionClass)
      throws IOException, RegistryException {
    // Has mockConnection.send throw first, then succeed.
    Mockito.when(mockConnection.send(Mockito.eq("httpMethod"), Mockito.any()))
        .thenThrow(Mockito.mock(exceptionClass))
        .thenReturn(mockResponse);
    Mockito.when(mockResponse.getBody()).thenReturn(Blobs.from("body"));

    RegistryEndpointCaller<String> testRegistryEndpointCallerInsecure =
        new RegistryEndpointCaller<>(
            "userAgent",
            "apiRouteBase",
            new TestRegistryEndpointProvider(),
            Authorizations.withBasicToken("token"),
            new RegistryEndpointRequestProperties("serverUrl", "imageName"),
            true,
            mockConnectionFactory);
    Assert.assertEquals("body", testRegistryEndpointCallerInsecure.call());

    // Checks that the URL protocol was first HTTPS, then HTTP.
    ArgumentCaptor<URL> urlArgumentCaptor = ArgumentCaptor.forClass(URL.class);
    Mockito.verify(mockConnectionFactory, Mockito.times(2)).apply(urlArgumentCaptor.capture());
    Assert.assertEquals("https", urlArgumentCaptor.getAllValues().get(0).getProtocol());
    Assert.assertEquals("http", urlArgumentCaptor.getAllValues().get(1).getProtocol());
  }

  /**
   * Verifies that a response with {@code httpStatusCode} throws {@link
   * RegistryUnauthorizedException}.
   */
  private void verifyThrowsRegistryUnauthorizedException(int httpStatusCode)
      throws IOException, RegistryException {
    Mockito.when(mockHttpResponse.getStatusCode()).thenReturn(httpStatusCode);
    HttpResponseException httpResponseException = new HttpResponseException(mockHttpResponse);

    Mockito.when(mockConnection.send(Mockito.eq("httpMethod"), Mockito.any()))
        .thenThrow(httpResponseException);

    try {
      testRegistryEndpointCallerSecure.call();
      Assert.fail("Call should have failed");

    } catch (RegistryUnauthorizedException ex) {
      Assert.assertEquals("serverUrl", ex.getRegistry());
      Assert.assertEquals("imageName", ex.getRepository());
      Assert.assertSame(httpResponseException, ex.getHttpResponseException());
    }
  }

  /**
   * Verifies that a response with {@code httpStatusCode} throws {@link
   * RegistryUnauthorizedException}.
   */
  private void verifyThrowsRegistryErrorException(int httpStatusCode)
      throws IOException, RegistryException {
    ErrorResponseTemplate errorResponseTemplate =
        new ErrorResponseTemplate().addError(new ErrorEntryTemplate("code", "message"));

    Mockito.when(mockHttpResponse.getStatusCode()).thenReturn(httpStatusCode);
    Mockito.when(mockHttpResponse.parseAsString())
        .thenReturn(Blobs.writeToString(JsonTemplateMapper.toBlob(errorResponseTemplate)));
    HttpResponseException httpResponseException = new HttpResponseException(mockHttpResponse);

    Mockito.when(mockConnection.send(Mockito.eq("httpMethod"), Mockito.any()))
        .thenThrow(httpResponseException);

    try {
      testRegistryEndpointCallerSecure.call();
      Assert.fail("Call should have failed");

    } catch (RegistryErrorException ex) {
      Assert.assertThat(
          ex.getMessage(),
          CoreMatchers.containsString(
              "Tried to actionDescription but failed because: unknown: message"));
    }
  }

  /**
   * Verifies that a response with {@code httpStatusCode} retries the request with the {@code
   * Location} header.
   */
  private void verifyRetriesWithNewLocation(int httpStatusCode)
      throws IOException, RegistryException {
    // Mocks a response for temporary redirect to a new location.
    Mockito.when(mockHttpResponse.getStatusCode()).thenReturn(httpStatusCode);
    Mockito.when(mockHttpResponse.getHeaders())
        .thenReturn(new HttpHeaders().setLocation("https://newlocation"));

    // Has mockConnection.send throw first, then succeed.
    HttpResponseException httpResponseException = new HttpResponseException(mockHttpResponse);
    Mockito.when(mockConnection.send(Mockito.eq("httpMethod"), Mockito.any()))
        .thenThrow(httpResponseException)
        .thenReturn(mockResponse);
    Mockito.when(mockResponse.getBody()).thenReturn(Blobs.from("body"));

    Assert.assertEquals("body", testRegistryEndpointCallerSecure.call());

    // Checks that the URL was changed to the new location.
    ArgumentCaptor<URL> urlArgumentCaptor = ArgumentCaptor.forClass(URL.class);
    Mockito.verify(mockConnectionFactory, Mockito.times(2)).apply(urlArgumentCaptor.capture());
    Assert.assertEquals(
        new URL("https://apiRouteBase/api"), urlArgumentCaptor.getAllValues().get(0));
    Assert.assertEquals(new URL("https://newlocation"), urlArgumentCaptor.getAllValues().get(1));
  }
}
