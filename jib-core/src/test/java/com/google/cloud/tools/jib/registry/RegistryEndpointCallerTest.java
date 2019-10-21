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

import com.google.api.client.http.HttpHeaders;
import com.google.api.client.http.HttpResponse;
import com.google.api.client.http.HttpResponseException;
import com.google.api.client.http.HttpStatusCodes;
import com.google.cloud.tools.jib.api.LogEvent;
import com.google.cloud.tools.jib.api.RegistryException;
import com.google.cloud.tools.jib.api.RegistryUnauthorizedException;
import com.google.cloud.tools.jib.event.EventHandlers;
import com.google.cloud.tools.jib.global.JibSystemProperties;
import com.google.cloud.tools.jib.http.Authorization;
import com.google.cloud.tools.jib.http.BlobHttpContent;
import com.google.cloud.tools.jib.http.Connection;
import com.google.cloud.tools.jib.http.Response;
import com.google.common.io.CharStreams;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.net.SocketException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import javax.annotation.Nullable;
import javax.net.ssl.SSLException;
import org.apache.http.NoHttpResponseException;
import org.hamcrest.CoreMatchers;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.contrib.java.lang.system.RestoreSystemProperties;
import org.junit.runner.RunWith;
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
      return new URL(apiRouteBase + "api");
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
      return CharStreams.toString(
          new InputStreamReader(response.getBody(), StandardCharsets.UTF_8));
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

  @Rule public final RestoreSystemProperties systemPropertyRestorer = new RestoreSystemProperties();

  @Mock private EventHandlers mockEventHandlers;
  @Mock private Connection mockHttpClient;
  @Mock private Response mockResponse;

  private RegistryEndpointCaller<String> secureEndpointCaller;

  @Before
  public void setUp() throws IOException {
    secureEndpointCaller =
        new RegistryEndpointCaller<>(
            mockEventHandlers,
            "userAgent",
            new TestRegistryEndpointProvider(),
            Authorization.fromBasicToken("token"),
            new RegistryEndpointRequestProperties("serverUrl", "imageName"),
            mockHttpClient);

    Mockito.when(mockResponse.getBody())
        .thenReturn(new ByteArrayInputStream("body".getBytes(StandardCharsets.UTF_8)));
  }

  @Test
  public void testCall_noHttpResponse() throws IOException, RegistryException {
    NoHttpResponseException mockNoHttpResponseException =
        Mockito.mock(NoHttpResponseException.class);
    mockRegistryHttpCall(mockNoHttpResponseException);

    try {
      secureEndpointCaller.call();
      Assert.fail("Call should have failed");

    } catch (NoHttpResponseException ex) {
      Assert.assertSame(mockNoHttpResponseException, ex);
    }
  }

  @Test
  public void testCall_unauthorized() throws IOException, RegistryException {
    verifyThrowsRegistryUnauthorizedException(HttpStatusCodes.STATUS_CODE_UNAUTHORIZED);
  }

  @Test
  public void testCall_credentialsNotSentOverHttp() throws IOException, RegistryException {
    HttpResponse unauthroizedResponse =
        mockHttpResponse(HttpStatusCodes.STATUS_CODE_UNAUTHORIZED, null);
    mockRegistryHttpCall(new HttpResponseException(unauthroizedResponse));

    try {
      secureEndpointCaller.call();
      Assert.fail("Call should have failed");

    } catch (RegistryCredentialsNotSentException ex) {
      Assert.assertEquals(
          "Required credentials for serverUrl/imageName were not sent because the connection was over HTTP",
          ex.getMessage());
    }
  }

  @Test
  public void testCall_credentialsForcedOverHttp() throws IOException, RegistryException {
    HttpResponse unauthroizedResponse =
        mockHttpResponse(HttpStatusCodes.STATUS_CODE_UNAUTHORIZED, null);
    mockRegistryHttpCall(new HttpResponseException(unauthroizedResponse));
    System.setProperty(JibSystemProperties.SEND_CREDENTIALS_OVER_HTTP, "true");

    try {
      secureEndpointCaller.call();
      Assert.fail("Call should have failed");

    } catch (RegistryCredentialsNotSentException ex) {
      Assert.fail("should have sent credentials");
    } catch (RegistryUnauthorizedException ex) {
      Assert.assertEquals("Unauthorized for serverUrl/imageName", ex.getMessage());
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
    HttpResponseException httpResponseException =
        new HttpResponseException(mockHttpResponse(HttpStatusCodes.STATUS_CODE_SERVER_ERROR, null));

    mockRegistryHttpCall(httpResponseException);

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
  public void testCall_logErrorOnIoExceptions() throws IOException, RegistryException {
    IOException ioException = new IOException("detailed exception message");
    mockRegistryHttpCall(ioException);

    try {
      secureEndpointCaller.call();
      Assert.fail();

    } catch (IOException ex) {
      Assert.assertSame(ioException, ex);
      Mockito.verify(mockEventHandlers)
          .dispatch(
              LogEvent.error("\u001B[31;1mI/O error for image [serverUrl/imageName]:\u001B[0m"));
      Mockito.verify(mockEventHandlers)
          .dispatch(LogEvent.error("\u001B[31;1m    detailed exception message\u001B[0m"));
      Mockito.verifyNoMoreInteractions(mockEventHandlers);
    }
  }

  @Test
  public void testCall_logErrorOnBrokenPipe() throws IOException, RegistryException {
    IOException ioException = new IOException("this is due to broken pipe");
    mockRegistryHttpCall(ioException);

    try {
      secureEndpointCaller.call();
      Assert.fail();

    } catch (IOException ex) {
      Assert.assertSame(ioException, ex);
      Mockito.verify(mockEventHandlers)
          .dispatch(
              LogEvent.error("\u001B[31;1mI/O error for image [serverUrl/imageName]:\u001B[0m"));
      Mockito.verify(mockEventHandlers)
          .dispatch(LogEvent.error("\u001B[31;1m    this is due to broken pipe\u001B[0m"));
      Mockito.verify(mockEventHandlers)
          .dispatch(
              LogEvent.error(
                  "\u001B[31;1mbroken pipe: the server shut down the connection. Check the server "
                      + "log if possible. This could also be a proxy issue. For example, a proxy "
                      + "may prevent sending packets that are too large.\u001B[0m"));
      Mockito.verifyNoMoreInteractions(mockEventHandlers);
    }
  }

  @Test
  public void testIsBrokenPipe_notBrokenPipe() {
    Assert.assertFalse(RegistryEndpointCaller.isBrokenPipe(new IOException()));
    Assert.assertFalse(RegistryEndpointCaller.isBrokenPipe(new SocketException()));
    Assert.assertFalse(RegistryEndpointCaller.isBrokenPipe(new SSLException("mock")));
  }

  @Test
  public void testIsBrokenPipe_brokenPipe() {
    Assert.assertTrue(RegistryEndpointCaller.isBrokenPipe(new IOException("cool broken pipe !")));
    Assert.assertTrue(RegistryEndpointCaller.isBrokenPipe(new SocketException("BROKEN PIPE")));
    Assert.assertTrue(RegistryEndpointCaller.isBrokenPipe(new SSLException("calm BrOkEn PiPe")));
  }

  @Test
  public void testIsBrokenPipe_nestedBrokenPipe() {
    IOException exception = new IOException(new SSLException(new SocketException("Broken pipe")));
    Assert.assertTrue(RegistryEndpointCaller.isBrokenPipe(exception));
  }

  @Test
  public void testIsBrokenPipe_terminatesWhenCauseIsOriginal() {
    IOException exception = Mockito.mock(IOException.class);
    Mockito.when(exception.getCause()).thenReturn(exception);

    Assert.assertFalse(RegistryEndpointCaller.isBrokenPipe(exception));
  }

  @Test
  public void testNewRegistryErrorException_jsonErrorOutput() {
    HttpResponseException httpException = Mockito.mock(HttpResponseException.class);
    Mockito.when(httpException.getContent())
        .thenReturn(
            "{\"errors\": [{\"code\": \"MANIFEST_UNKNOWN\", \"message\": \"manifest unknown\"}]}");

    RegistryErrorException registryException =
        secureEndpointCaller.newRegistryErrorException(httpException);
    Assert.assertSame(httpException, registryException.getCause());
    Assert.assertEquals(
        "Tried to actionDescription but failed because: manifest unknown | If this is a bug, "
            + "please file an issue at https://github.com/GoogleContainerTools/jib/issues/new",
        registryException.getMessage());
  }

  @Test
  public void testNewRegistryErrorException_nonJsonErrorOutput() {
    HttpResponseException httpException = Mockito.mock(HttpResponseException.class);
    // Registry returning non-structured error output
    Mockito.when(httpException.getContent()).thenReturn(">>>>> (404) page not found <<<<<");
    Mockito.when(httpException.getStatusCode()).thenReturn(404);

    RegistryErrorException registryException =
        secureEndpointCaller.newRegistryErrorException(httpException);
    Assert.assertSame(httpException, registryException.getCause());
    Assert.assertEquals(
        "Tried to actionDescription but failed because: registry returned error code 404; "
            + "possible causes include invalid or wrong reference. Actual error output follows:\n"
            + ">>>>> (404) page not found <<<<<\n"
            + " | If this is a bug, please file an issue at "
            + "https://github.com/GoogleContainerTools/jib/issues/new",
        registryException.getMessage());
  }

  /**
   * Verifies that a response with {@code httpStatusCode} throws {@link
   * RegistryUnauthorizedException}.
   */
  private void verifyThrowsRegistryUnauthorizedException(int httpStatusCode)
      throws IOException, RegistryException {
    HttpResponse mockHttpResponse = mockHttpResponse(httpStatusCode, null);
    HttpResponseException httpResponseException = new HttpResponseException(mockHttpResponse);
    mockRegistryHttpCall(httpResponseException);

    try {
      secureEndpointCaller.call();
      Assert.fail("Call should have failed");

    } catch (RegistryUnauthorizedException ex) {
      Assert.assertEquals("serverUrl/imageName", ex.getImageReference());
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
    mockRegistryHttpCall(new HttpResponseException(errorResponse));

    try {
      secureEndpointCaller.call();
      Assert.fail("Call should have failed");

    } catch (RegistryErrorException ex) {
      Assert.assertThat(
          ex.getMessage(),
          CoreMatchers.startsWith(
              "Tried to actionDescription but failed because: unknown error code: code (message)"));
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
    mockRegistryHttpCall(redirectException);
    Mockito.when(
            mockHttpClient.call(
                Mockito.eq("httpMethod"),
                Mockito.eq(new URL("https://newlocation")),
                Mockito.any()))
        .thenReturn(mockResponse);

    Assert.assertEquals("body", secureEndpointCaller.call());
  }

  private void mockRegistryHttpCall(Exception exceptionToThrow)
      throws MalformedURLException, IOException {
    Mockito.when(
            mockHttpClient.call(
                Mockito.eq("httpMethod"),
                Mockito.eq(new URL("https://serverUrl/v2/api")),
                Mockito.any()))
        .thenThrow(exceptionToThrow);
  }
}
