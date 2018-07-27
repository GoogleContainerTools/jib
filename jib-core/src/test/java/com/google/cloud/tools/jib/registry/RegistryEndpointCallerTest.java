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
import com.google.cloud.tools.jib.blob.Blobs;
import com.google.cloud.tools.jib.http.Authorizations;
import com.google.cloud.tools.jib.http.BlobHttpContent;
import com.google.cloud.tools.jib.http.Connection;
import com.google.cloud.tools.jib.http.MockConnection;
import com.google.cloud.tools.jib.http.Response;
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

  private static HttpResponse mockHttpResponse(int statusCode, @Nullable HttpHeaders headers)
      throws IOException {
    HttpResponse mock = Mockito.mock(HttpResponse.class);
    Mockito.when(mock.getStatusCode()).thenReturn(statusCode);
    Mockito.when(mock.parseAsString()).thenReturn("");
    Mockito.when(mock.getHeaders()).thenReturn(headers != null ? headers : new HttpHeaders());
    return mock;
  }

  @Mock private Connection mockConnection;
  @Mock private Connection mockInsecureConnection;
  @Mock private Response mockResponse;
  @Mock private Function<URL, Connection> mockConnectionFactory;
  @Mock private Function<URL, Connection> mockInsecureConnectionFactory;

  private RegistryEndpointCaller<String> secureEndpointCaller;

  @Before
  public void setUp() throws IOException {
    secureEndpointCaller = createRegistryEndpointCaller(false);

    Mockito.when(mockConnectionFactory.apply(Mockito.any())).thenReturn(mockConnection);
    Mockito.when(mockInsecureConnectionFactory.apply(Mockito.any()))
        .thenReturn(mockInsecureConnection);
    Mockito.when(mockResponse.getBody()).thenReturn(Blobs.from("body"));
  }

  @After
  public void tearDown() {
    System.clearProperty("jib.httpTimeout");
    System.clearProperty("sendCredentialsOverHttp");
  }

  @Test
  public void testCall_secureCallerOnUnverifiableServer() throws IOException, RegistryException {
    Mockito.when(mockConnection.send(Mockito.eq("httpMethod"), Mockito.any()))
        .thenThrow(Mockito.mock(SSLPeerUnverifiedException.class)); // unverifiable HTTPS server

    try {
      secureEndpointCaller.call();
      Assert.fail("Secure caller should fail if cannot verify server");
    } catch (InsecureRegistryException ex) {
      Assert.assertEquals(
          "Failed to verify the server at https://apiRouteBase/api because only secure connections are allowed.",
          ex.getMessage());
    }
  }

  @Test
  public void testCall_insecureCallerOnUnverifiableServer() throws IOException, RegistryException {
    Mockito.when(mockConnection.send(Mockito.eq("httpMethod"), Mockito.any()))
        .thenThrow(Mockito.mock(SSLPeerUnverifiedException.class)); // unverifiable HTTPS server
    Mockito.when(mockInsecureConnection.send(Mockito.eq("httpMethod"), Mockito.any()))
        .thenReturn(mockResponse); // OK with non-verifying connection

    RegistryEndpointCaller<String> insecureCaller = createRegistryEndpointCaller(true);
    Assert.assertEquals("body", insecureCaller.call());

    ArgumentCaptor<URL> urlCaptor = ArgumentCaptor.forClass(URL.class);
    Mockito.verify(mockConnectionFactory, Mockito.times(1)).apply(urlCaptor.capture());
    Assert.assertEquals(new URL("https://apiRouteBase/api"), urlCaptor.getAllValues().get(0));

    Mockito.verify(mockInsecureConnectionFactory, Mockito.times(1)).apply(urlCaptor.capture());
    Assert.assertEquals(new URL("https://apiRouteBase/api"), urlCaptor.getAllValues().get(1));
  }

  @Test
  public void testCall_insecureCallerOnHttpServer() throws IOException, RegistryException {
    Mockito.when(mockConnection.send(Mockito.eq("httpMethod"), Mockito.any()))
        .thenThrow(Mockito.mock(SSLPeerUnverifiedException.class)) // server is not HTTPS
        .thenReturn(mockResponse);
    Mockito.when(mockInsecureConnection.send(Mockito.eq("httpMethod"), Mockito.any()))
        .thenThrow(Mockito.mock(SSLPeerUnverifiedException.class)); // server is not HTTPS

    RegistryEndpointCaller<String> insecureEndpointCaller = createRegistryEndpointCaller(true);
    Assert.assertEquals("body", insecureEndpointCaller.call());

    ArgumentCaptor<URL> urlCaptor = ArgumentCaptor.forClass(URL.class);
    Mockito.verify(mockConnectionFactory, Mockito.times(2)).apply(urlCaptor.capture());
    Assert.assertEquals(new URL("https://apiRouteBase/api"), urlCaptor.getAllValues().get(0));
    Assert.assertEquals(new URL("http://apiRouteBase/api"), urlCaptor.getAllValues().get(1));

    Mockito.verify(mockInsecureConnectionFactory, Mockito.times(1)).apply(urlCaptor.capture());
    Assert.assertEquals(new URL("https://apiRouteBase/api"), urlCaptor.getAllValues().get(2));
  }

  @Test
  public void testCall_insecureCallerOnHttpServerByHttpHostConnectException()
      throws IOException, RegistryException {
    Mockito.when(mockConnection.send(Mockito.eq("httpMethod"), Mockito.any()))
        .thenThrow(Mockito.mock(SSLPeerUnverifiedException.class)) // server is not HTTPS
        .thenReturn(mockResponse);
    Mockito.when(mockInsecureConnection.send(Mockito.eq("httpMethod"), Mockito.any()))
        .thenThrow(Mockito.mock(HttpHostConnectException.class)); // server is not HTTPS

    RegistryEndpointCaller<String> insecureEndpointCaller = createRegistryEndpointCaller(true);
    Assert.assertEquals("body", insecureEndpointCaller.call());

    ArgumentCaptor<URL> urlCaptor = ArgumentCaptor.forClass(URL.class);
    Mockito.verify(mockConnectionFactory, Mockito.times(2)).apply(urlCaptor.capture());
    Assert.assertEquals(new URL("https://apiRouteBase/api"), urlCaptor.getAllValues().get(0));
    Assert.assertEquals(new URL("http://apiRouteBase/api"), urlCaptor.getAllValues().get(1));

    Mockito.verify(mockInsecureConnectionFactory, Mockito.times(1)).apply(urlCaptor.capture());
    Assert.assertEquals(new URL("https://apiRouteBase/api"), urlCaptor.getAllValues().get(2));
  }

  @Test
  public void testCall_noHttpResponse() throws IOException, RegistryException {
    NoHttpResponseException mockNoHttpResponseException =
        Mockito.mock(NoHttpResponseException.class);
    Mockito.when(mockConnection.send(Mockito.eq("httpMethod"), Mockito.any()))
        .thenThrow(mockNoHttpResponseException);

    try {
      secureEndpointCaller.call();
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
  public void testCall_credentialsNotSentOverHttp() throws IOException, RegistryException {
    HttpResponse redirectResponse =
        mockHttpResponse(
            HttpStatusCodes.STATUS_CODE_TEMPORARY_REDIRECT,
            new HttpHeaders().setLocation("http://newlocation"));

    HttpResponse unauthroizedResponse =
        mockHttpResponse(HttpStatusCodes.STATUS_CODE_UNAUTHORIZED, null);

    Mockito.when(mockConnection.send(Mockito.eq("httpMethod"), Mockito.any()))
        .thenThrow(Mockito.mock(SSLPeerUnverifiedException.class)) // server is not HTTPS
        .thenThrow(new HttpResponseException(redirectResponse)) // redirect to HTTP
        .thenThrow(new HttpResponseException(unauthroizedResponse)); // final response
    Mockito.when(mockInsecureConnection.send(Mockito.eq("httpMethod"), Mockito.any()))
        .thenThrow(Mockito.mock(SSLPeerUnverifiedException.class)); // server is not HTTPS

    Assert.assertNull(System.getProperty("jib.httpTimeout"));
    RegistryEndpointCaller<String> insecureEndpointCaller = createRegistryEndpointCaller(true);
    try {
      insecureEndpointCaller.call();
      Assert.fail("Call should have failed");

    } catch (RegistryCredentialsNotSentException ex) {
      Assert.assertEquals(
          "Required credentials for serverUrl/imageName were not sent because the connection was over HTTP",
          ex.getMessage());
    }
  }

  @Test
  public void testCall_credentialsForcedOverHttp() throws IOException, RegistryException {
    HttpResponse redirectResponse =
        mockHttpResponse(
            HttpStatusCodes.STATUS_CODE_TEMPORARY_REDIRECT,
            new HttpHeaders().setLocation("http://newlocation"));

    Mockito.when(mockConnection.send(Mockito.eq("httpMethod"), Mockito.any()))
        .thenThrow(Mockito.mock(SSLPeerUnverifiedException.class)) // server is not HTTPS
        .thenThrow(new HttpResponseException(redirectResponse)) // redirect to HTTP
        .thenReturn(mockResponse); // final response
    Mockito.when(mockInsecureConnection.send(Mockito.eq("httpMethod"), Mockito.any()))
        .thenThrow(Mockito.mock(SSLPeerUnverifiedException.class)); // server is not HTTPS

    System.setProperty("sendCredentialsOverHttp", "true");
    RegistryEndpointCaller<String> insecureEndpointCaller = createRegistryEndpointCaller(true);
    Assert.assertEquals("body", insecureEndpointCaller.call());
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
    HttpResponse mockHttpResponse =
        mockHttpResponse(HttpStatusCodes.STATUS_CODE_SERVER_ERROR, null);
    HttpResponseException httpResponseException = new HttpResponseException(mockHttpResponse);

    Mockito.when(mockConnection.send(Mockito.eq("httpMethod"), Mockito.any()))
        .thenThrow(httpResponseException);

    try {
      secureEndpointCaller.call();
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
    HttpResponse redirectResponse =
        mockHttpResponse(
            HttpStatusCodes.STATUS_CODE_TEMPORARY_REDIRECT,
            new HttpHeaders().setLocation("http://newlocation"));

    HttpResponseException redirectException = new HttpResponseException(redirectResponse);
    Mockito.when(mockConnection.send(Mockito.eq("httpMethod"), Mockito.any()))
        .thenThrow(redirectException);

    try {
      secureEndpointCaller.call();
      Assert.fail("Call should have failed");

    } catch (InsecureRegistryException ex) {
      // pass
    }
  }

  @Test
  public void testHttpTimeout_propertyNotSet() throws IOException, RegistryException {
    MockConnection mockConnection = new MockConnection((httpMethod, request) -> mockResponse);
    Mockito.when(mockConnectionFactory.apply(Mockito.any())).thenReturn(mockConnection);

    Assert.assertNull(System.getProperty("jib.httpTimeout"));
    secureEndpointCaller.call();

    // We fall back to the default timeout:
    // https://github.com/GoogleContainerTools/jib/pull/656#discussion_r203562639
    Assert.assertNull(mockConnection.getRequestedHttpTimeout());
  }

  @Test
  public void testHttpTimeout_stringValue() throws IOException, RegistryException {
    MockConnection mockConnection = new MockConnection((httpMethod, request) -> mockResponse);
    Mockito.when(mockConnectionFactory.apply(Mockito.any())).thenReturn(mockConnection);

    System.setProperty("jib.httpTimeout", "random string");
    secureEndpointCaller.call();

    Assert.assertNull(mockConnection.getRequestedHttpTimeout());
  }

  @Test
  public void testHttpTimeout_negativeValue() throws IOException, RegistryException {
    MockConnection mockConnection = new MockConnection((httpMethod, request) -> mockResponse);
    Mockito.when(mockConnectionFactory.apply(Mockito.any())).thenReturn(mockConnection);

    System.setProperty("jib.httpTimeout", "-1");
    secureEndpointCaller.call();

    // We let the negative value pass through:
    // https://github.com/GoogleContainerTools/jib/pull/656#discussion_r203562639
    Assert.assertEquals(Integer.valueOf(-1), mockConnection.getRequestedHttpTimeout());
  }

  @Test
  public void testHttpTimeout_0accepted() throws IOException, RegistryException {
    System.setProperty("jib.httpTimeout", "0");

    MockConnection mockConnection = new MockConnection((httpMethod, request) -> mockResponse);
    Mockito.when(mockConnectionFactory.apply(Mockito.any())).thenReturn(mockConnection);

    secureEndpointCaller.call();

    Assert.assertEquals(Integer.valueOf(0), mockConnection.getRequestedHttpTimeout());
  }

  @Test
  public void testHttpTimeout() throws IOException, RegistryException {
    System.setProperty("jib.httpTimeout", "7593");

    MockConnection mockConnection = new MockConnection((httpMethod, request) -> mockResponse);
    Mockito.when(mockConnectionFactory.apply(Mockito.any())).thenReturn(mockConnection);

    secureEndpointCaller.call();

    Assert.assertEquals(Integer.valueOf(7593), mockConnection.getRequestedHttpTimeout());
  }

  /**
   * Verifies that a response with {@code httpStatusCode} throws {@link
   * RegistryUnauthorizedException}.
   */
  private void verifyThrowsRegistryUnauthorizedException(int httpStatusCode)
      throws IOException, RegistryException {
    HttpResponse mockHttpResponse = mockHttpResponse(httpStatusCode, null);
    HttpResponseException httpResponseException = new HttpResponseException(mockHttpResponse);

    Mockito.when(mockConnection.send(Mockito.eq("httpMethod"), Mockito.any()))
        .thenThrow(httpResponseException);

    try {
      secureEndpointCaller.call();
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
    HttpResponse errorResponse = mockHttpResponse(httpStatusCode, null);
    Mockito.when(errorResponse.parseAsString())
        .thenReturn("{\"errors\":[{\"code\":\"code\",\"message\":\"message\"}]}");
    HttpResponseException httpResponseException = new HttpResponseException(errorResponse);

    Mockito.when(mockConnection.send(Mockito.eq("httpMethod"), Mockito.any()))
        .thenThrow(httpResponseException);

    try {
      secureEndpointCaller.call();
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
    HttpResponse redirectResponse =
        mockHttpResponse(httpStatusCode, new HttpHeaders().setLocation("https://newlocation"));

    // Has mockConnection.send throw first, then succeed.
    HttpResponseException redirectException = new HttpResponseException(redirectResponse);
    Mockito.when(mockConnection.send(Mockito.eq("httpMethod"), Mockito.any()))
        .thenThrow(redirectException)
        .thenReturn(mockResponse);

    Assert.assertEquals("body", secureEndpointCaller.call());

    // Checks that the URL was changed to the new location.
    ArgumentCaptor<URL> urlArgumentCaptor = ArgumentCaptor.forClass(URL.class);
    Mockito.verify(mockConnectionFactory, Mockito.times(2)).apply(urlArgumentCaptor.capture());
    Assert.assertEquals(
        new URL("https://apiRouteBase/api"), urlArgumentCaptor.getAllValues().get(0));
    Assert.assertEquals(new URL("https://newlocation"), urlArgumentCaptor.getAllValues().get(1));
  }

  private RegistryEndpointCaller<String> createRegistryEndpointCaller(boolean allowInsecure)
      throws MalformedURLException {
    return new RegistryEndpointCaller<>(
        "userAgent",
        "apiRouteBase",
        new TestRegistryEndpointProvider(),
        Authorizations.withBasicToken("token"),
        new RegistryEndpointRequestProperties("serverUrl", "imageName"),
        allowInsecure,
        mockConnectionFactory,
        mockInsecureConnectionFactory);
  }
}
