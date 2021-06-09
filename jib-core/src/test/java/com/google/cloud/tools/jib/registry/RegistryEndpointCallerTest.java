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
import com.google.cloud.tools.jib.api.InsecureRegistryException;
import com.google.cloud.tools.jib.api.LogEvent;
import com.google.cloud.tools.jib.api.RegistryException;
import com.google.cloud.tools.jib.api.RegistryUnauthorizedException;
import com.google.cloud.tools.jib.event.EventHandlers;
import com.google.cloud.tools.jib.global.JibSystemProperties;
import com.google.cloud.tools.jib.http.Authorization;
import com.google.cloud.tools.jib.http.BlobHttpContent;
import com.google.cloud.tools.jib.http.FailoverHttpClient;
import com.google.cloud.tools.jib.http.Request;
import com.google.cloud.tools.jib.http.RequestWrapper;
import com.google.cloud.tools.jib.http.Response;
import com.google.cloud.tools.jib.http.ResponseException;
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
import javax.net.ssl.SSLPeerUnverifiedException;
import org.apache.http.NoHttpResponseException;
import org.hamcrest.CoreMatchers;
import org.hamcrest.MatcherAssert;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.contrib.java.lang.system.RestoreSystemProperties;
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

    @Override
    public String handleHttpResponseException(ResponseException responseException)
        throws ResponseException, RegistryErrorException {
      throw responseException;
    }
  }

  private static ResponseException mockResponseException(int statusCode) {
    ResponseException mock = Mockito.mock(ResponseException.class);
    Mockito.when(mock.getStatusCode()).thenReturn(statusCode);
    return mock;
  }

  @Rule public final RestoreSystemProperties systemPropertyRestorer = new RestoreSystemProperties();

  @Mock private EventHandlers mockEventHandlers;
  @Mock private FailoverHttpClient mockHttpClient;
  @Mock private Response mockResponse;

  private RegistryEndpointCaller<String> endpointCaller;

  @Before
  public void setUp() throws IOException {
    endpointCaller =
        new RegistryEndpointCaller<>(
            mockEventHandlers,
            "userAgent",
            new TestRegistryEndpointProvider(),
            Authorization.fromBasicCredentials("user", "pass"),
            new RegistryEndpointRequestProperties("serverUrl", "imageName"),
            mockHttpClient);

    Mockito.when(mockResponse.getBody())
        .thenReturn(new ByteArrayInputStream("body".getBytes(StandardCharsets.UTF_8)));
  }

  @Test
  public void testCall_secureCallerOnUnverifiableServer() throws IOException, RegistryException {
    Mockito.when(mockHttpClient.call(Mockito.any(), Mockito.any(), Mockito.any()))
        .thenThrow(Mockito.mock(SSLPeerUnverifiedException.class));

    try {
      endpointCaller.call();
      Assert.fail("Should throw InsecureRegistryException when getting SSLException");
    } catch (InsecureRegistryException ex) {
      Assert.assertEquals(
          "Failed to verify the server at https://serverUrl/v2/api because only secure connections "
              + "are allowed.",
          ex.getMessage());
    }
  }

  @Test
  public void testCall_noHttpResponse() throws IOException, RegistryException {
    NoHttpResponseException mockNoResponseException = Mockito.mock(NoHttpResponseException.class);
    setUpRegistryResponse(mockNoResponseException);

    try {
      endpointCaller.call();
      Assert.fail("Call should have failed");

    } catch (NoHttpResponseException ex) {
      Assert.assertSame(mockNoResponseException, ex);
    }
  }

  @Test
  public void testCall_unauthorized() throws IOException, RegistryException {
    verifyThrowsRegistryUnauthorizedException(HttpStatusCodes.STATUS_CODE_UNAUTHORIZED);
  }

  @Test
  public void testCall_credentialsNotSentOverHttp() throws IOException, RegistryException {
    ResponseException unauthorizedException =
        mockResponseException(HttpStatusCodes.STATUS_CODE_UNAUTHORIZED);
    Mockito.when(unauthorizedException.requestAuthorizationCleared()).thenReturn(true);
    setUpRegistryResponse(unauthorizedException);

    try {
      endpointCaller.call();
      Assert.fail("Call should have failed");

    } catch (RegistryCredentialsNotSentException ex) {
      Assert.assertEquals(
          "Required credentials for serverUrl/imageName were not sent because the connection was over HTTP",
          ex.getMessage());
    }
  }

  @Test
  public void testCall_credentialsForcedOverHttp() throws IOException, RegistryException {
    ResponseException unauthorizedException =
        mockResponseException(HttpStatusCodes.STATUS_CODE_UNAUTHORIZED);
    setUpRegistryResponse(unauthorizedException);
    System.setProperty(JibSystemProperties.SEND_CREDENTIALS_OVER_HTTP, "true");

    try {
      endpointCaller.call();
      Assert.fail("Call should have failed");

    } catch (RegistryCredentialsNotSentException ex) {
      throw new AssertionError("should have sent credentials", ex);
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
    ResponseException responseException =
        mockResponseException(HttpStatusCodes.STATUS_CODE_SERVER_ERROR);
    setUpRegistryResponse(responseException);

    try {
      endpointCaller.call();
      Assert.fail("Call should have failed");

    } catch (ResponseException ex) {
      Assert.assertSame(responseException, ex);
    }
  }

  @Test
  public void testCall_logErrorOnIoExceptions() throws IOException, RegistryException {
    IOException ioException = new IOException("detailed exception message");
    setUpRegistryResponse(ioException);

    try {
      endpointCaller.call();
      Assert.fail();

    } catch (IOException ex) {
      Assert.assertSame(ioException, ex);
      Mockito.verify(mockEventHandlers)
          .dispatch(
              LogEvent.error("\u001B[31;1mI/O error for image [serverUrl/imageName]:\u001B[0m"));
      Mockito.verify(mockEventHandlers)
          .dispatch(LogEvent.error("\u001B[31;1m    java.io.IOException\u001B[0m"));
      Mockito.verify(mockEventHandlers)
          .dispatch(LogEvent.error("\u001B[31;1m    detailed exception message\u001B[0m"));
      Mockito.verifyNoMoreInteractions(mockEventHandlers);
    }
  }

  @Test
  public void testCall_logErrorOnBrokenPipe() throws IOException, RegistryException {
    IOException ioException = new IOException("this is due to broken pipe");
    setUpRegistryResponse(ioException);

    try {
      endpointCaller.call();
      Assert.fail();

    } catch (IOException ex) {
      Assert.assertSame(ioException, ex);
      Mockito.verify(mockEventHandlers)
          .dispatch(
              LogEvent.error("\u001B[31;1mI/O error for image [serverUrl/imageName]:\u001B[0m"));
      Mockito.verify(mockEventHandlers)
          .dispatch(LogEvent.error("\u001B[31;1m    java.io.IOException\u001B[0m"));
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
  public void testCall_logNullExceptionMessage() throws IOException, RegistryException {
    setUpRegistryResponse(new IOException());

    try {
      endpointCaller.call();
      Assert.fail();

    } catch (IOException ex) {
      Mockito.verify(mockEventHandlers)
          .dispatch(
              LogEvent.error("\u001B[31;1mI/O error for image [serverUrl/imageName]:\u001B[0m"));
      Mockito.verify(mockEventHandlers)
          .dispatch(LogEvent.error("\u001B[31;1m    java.io.IOException\u001B[0m"));
      Mockito.verify(mockEventHandlers)
          .dispatch(LogEvent.error("\u001B[31;1m    (null exception message)\u001B[0m"));
      Mockito.verifyNoMoreInteractions(mockEventHandlers);
    }
  }

  @Test
  public void testHttpTimeout_propertyNotSet() throws IOException, RegistryException {
    ArgumentCaptor<Request> requestCaptor = ArgumentCaptor.forClass(Request.class);
    Mockito.when(mockHttpClient.call(Mockito.any(), Mockito.any(), requestCaptor.capture()))
        .thenReturn(mockResponse);

    System.clearProperty(JibSystemProperties.HTTP_TIMEOUT);
    endpointCaller.call();

    // We fall back to the default timeout:
    // https://github.com/GoogleContainerTools/jib/pull/656#discussion_r203562639
    Assert.assertEquals(20000, new RequestWrapper(requestCaptor.getValue()).getHttpTimeout());
  }

  @Test
  public void testHttpTimeout_stringValue() throws IOException, RegistryException {
    ArgumentCaptor<Request> requestCaptor = ArgumentCaptor.forClass(Request.class);
    Mockito.when(mockHttpClient.call(Mockito.any(), Mockito.any(), requestCaptor.capture()))
        .thenReturn(mockResponse);

    System.setProperty(JibSystemProperties.HTTP_TIMEOUT, "random string");
    endpointCaller.call();

    Assert.assertEquals(20000, new RequestWrapper(requestCaptor.getValue()).getHttpTimeout());
  }

  @Test
  public void testHttpTimeout_negativeValue() throws IOException, RegistryException {
    ArgumentCaptor<Request> requestCaptor = ArgumentCaptor.forClass(Request.class);
    Mockito.when(mockHttpClient.call(Mockito.any(), Mockito.any(), requestCaptor.capture()))
        .thenReturn(mockResponse);

    System.setProperty(JibSystemProperties.HTTP_TIMEOUT, "-1");
    endpointCaller.call();

    // We let the negative value pass through:
    // https://github.com/GoogleContainerTools/jib/pull/656#discussion_r203562639
    Assert.assertEquals(-1, new RequestWrapper(requestCaptor.getValue()).getHttpTimeout());
  }

  @Test
  public void testHttpTimeout_0accepted() throws IOException, RegistryException {
    ArgumentCaptor<Request> requestCaptor = ArgumentCaptor.forClass(Request.class);
    Mockito.when(mockHttpClient.call(Mockito.any(), Mockito.any(), requestCaptor.capture()))
        .thenReturn(mockResponse);

    System.setProperty(JibSystemProperties.HTTP_TIMEOUT, "0");
    endpointCaller.call();

    Assert.assertEquals(0, new RequestWrapper(requestCaptor.getValue()).getHttpTimeout());
  }

  @Test
  public void testHttpTimeout() throws IOException, RegistryException {
    ArgumentCaptor<Request> requestCaptor = ArgumentCaptor.forClass(Request.class);
    Mockito.when(mockHttpClient.call(Mockito.any(), Mockito.any(), requestCaptor.capture()))
        .thenReturn(mockResponse);

    System.setProperty(JibSystemProperties.HTTP_TIMEOUT, "7593");
    endpointCaller.call();

    Assert.assertEquals(7593, new RequestWrapper(requestCaptor.getValue()).getHttpTimeout());
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
    ResponseException httpException = Mockito.mock(ResponseException.class);
    Mockito.when(httpException.getContent())
        .thenReturn(
            "{\"errors\": [{\"code\": \"MANIFEST_UNKNOWN\", \"message\": \"manifest unknown\"}]}");

    RegistryErrorException registryException =
        endpointCaller.newRegistryErrorException(httpException);
    Assert.assertSame(httpException, registryException.getCause());
    Assert.assertEquals(
        "Tried to actionDescription but failed because: manifest unknown",
        registryException.getMessage());
  }

  @Test
  public void testNewRegistryErrorException_nonJsonErrorOutput() {
    ResponseException httpException = Mockito.mock(ResponseException.class);
    // Registry returning non-structured error output
    Mockito.when(httpException.getContent()).thenReturn(">>>>> (404) page not found <<<<<");
    Mockito.when(httpException.getStatusCode()).thenReturn(404);

    RegistryErrorException registryException =
        endpointCaller.newRegistryErrorException(httpException);
    Assert.assertSame(httpException, registryException.getCause());
    Assert.assertEquals(
        "Tried to actionDescription but failed because: registry returned error code 404; "
            + "possible causes include invalid or wrong reference. Actual error output follows:\n"
            + ">>>>> (404) page not found <<<<<\n",
        registryException.getMessage());
  }

  @Test
  public void testNewRegistryErrorException_noOutputFromRegistry() {
    ResponseException httpException = Mockito.mock(ResponseException.class);
    // Registry returning null error output
    Mockito.when(httpException.getContent()).thenReturn(null);
    Mockito.when(httpException.getStatusCode()).thenReturn(404);

    RegistryErrorException registryException =
        endpointCaller.newRegistryErrorException(httpException);
    Assert.assertSame(httpException, registryException.getCause());
    Assert.assertEquals(
        "Tried to actionDescription but failed because: registry returned error code 404 "
            + "but did not return any details; possible causes include invalid or wrong reference, or proxy/firewall/VPN interfering \n",
        registryException.getMessage());
  }

  /**
   * Verifies that a response with {@code httpStatusCode} throws {@link
   * RegistryUnauthorizedException}.
   */
  private void verifyThrowsRegistryUnauthorizedException(int httpStatusCode)
      throws IOException, RegistryException {
    ResponseException responseException = mockResponseException(httpStatusCode);
    setUpRegistryResponse(responseException);

    try {
      endpointCaller.call();
      Assert.fail("Call should have failed");

    } catch (RegistryUnauthorizedException ex) {
      Assert.assertEquals("serverUrl/imageName", ex.getImageReference());
      Assert.assertSame(responseException, ex.getCause());
    }
  }

  /**
   * Verifies that a response with {@code httpStatusCode} throws {@link
   * RegistryUnauthorizedException}.
   */
  private void verifyThrowsRegistryErrorException(int httpStatusCode)
      throws IOException, RegistryException {
    ResponseException errorResponse = mockResponseException(httpStatusCode);
    Mockito.when(errorResponse.getContent())
        .thenReturn("{\"errors\":[{\"code\":\"code\",\"message\":\"message\"}]}");
    setUpRegistryResponse(errorResponse);

    try {
      endpointCaller.call();
      Assert.fail("Call should have failed");

    } catch (RegistryErrorException ex) {
      MatcherAssert.assertThat(
          ex.getMessage(),
          CoreMatchers.startsWith(
              "Tried to actionDescription but failed because: unknown error code: code (message)"));
    }
  }

  private void setUpRegistryResponse(Exception exceptionToThrow)
      throws MalformedURLException, IOException {
    Mockito.when(
            mockHttpClient.call(
                Mockito.eq("httpMethod"),
                Mockito.eq(new URL("https://serverUrl/v2/api")),
                Mockito.any()))
        .thenThrow(exceptionToThrow);
  }
}
