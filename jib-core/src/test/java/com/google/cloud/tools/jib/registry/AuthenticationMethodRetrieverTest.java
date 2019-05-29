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
import com.google.api.client.http.HttpMethods;
import com.google.api.client.http.HttpResponseException;
import com.google.api.client.http.HttpStatusCodes;
import com.google.cloud.tools.jib.http.Response;
import java.net.MalformedURLException;
import java.net.URL;
import org.hamcrest.CoreMatchers;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

/** Tests for {@link AuthenticationMethodRetriever}. */
@RunWith(MockitoJUnitRunner.class)
public class AuthenticationMethodRetrieverTest {

  @Mock private HttpResponseException mockHttpResponseException;
  @Mock private HttpHeaders mockHeaders;

  private final RegistryEndpointRequestProperties fakeRegistryEndpointRequestProperties =
      new RegistryEndpointRequestProperties("someServerUrl", "someImageName");
  private final AuthenticationMethodRetriever testAuthenticationMethodRetriever =
      new AuthenticationMethodRetriever(fakeRegistryEndpointRequestProperties, "user-agent");

  @Test
  public void testGetContent() {
    Assert.assertNull(testAuthenticationMethodRetriever.getContent());
  }

  @Test
  public void testGetAccept() {
    Assert.assertEquals(0, testAuthenticationMethodRetriever.getAccept().size());
  }

  @Test
  public void testHandleResponse() {
    Assert.assertNull(
        testAuthenticationMethodRetriever.handleResponse(Mockito.mock(Response.class)));
  }

  @Test
  public void testGetApiRoute() throws MalformedURLException {
    Assert.assertEquals(
        new URL("http://someApiBase/"),
        testAuthenticationMethodRetriever.getApiRoute("http://someApiBase/"));
  }

  @Test
  public void testGetHttpMethod() {
    Assert.assertEquals(HttpMethods.GET, testAuthenticationMethodRetriever.getHttpMethod());
  }

  @Test
  public void testGetActionDescription() {
    Assert.assertEquals(
        "retrieve authentication method for someServerUrl",
        testAuthenticationMethodRetriever.getActionDescription());
  }

  @Test
  public void testHandleHttpResponseException_invalidStatusCode() throws RegistryErrorException {
    Mockito.when(mockHttpResponseException.getStatusCode()).thenReturn(-1);

    try {
      testAuthenticationMethodRetriever.handleHttpResponseException(mockHttpResponseException);
      Assert.fail(
          "Authentication method retriever should only handle HTTP 401 Unauthorized errors");

    } catch (HttpResponseException ex) {
      Assert.assertEquals(mockHttpResponseException, ex);
    }
  }

  @Test
  public void tsetHandleHttpResponseException_noHeader() throws HttpResponseException {
    Mockito.when(mockHttpResponseException.getStatusCode())
        .thenReturn(HttpStatusCodes.STATUS_CODE_UNAUTHORIZED);
    Mockito.when(mockHttpResponseException.getHeaders()).thenReturn(mockHeaders);
    Mockito.when(mockHeaders.getAuthenticate()).thenReturn(null);

    try {
      testAuthenticationMethodRetriever.handleHttpResponseException(mockHttpResponseException);
      Assert.fail(
          "Authentication method retriever should fail if 'WWW-Authenticate' header is not found");

    } catch (RegistryErrorException ex) {
      Assert.assertThat(
          ex.getMessage(), CoreMatchers.containsString("'WWW-Authenticate' header not found"));
    }
  }

  @Test
  public void testHandleHttpResponseException_badAuthenticationMethod()
      throws HttpResponseException {
    String authenticationMethod = "bad authentication method";

    Mockito.when(mockHttpResponseException.getStatusCode())
        .thenReturn(HttpStatusCodes.STATUS_CODE_UNAUTHORIZED);
    Mockito.when(mockHttpResponseException.getHeaders()).thenReturn(mockHeaders);
    Mockito.when(mockHeaders.getAuthenticate()).thenReturn(authenticationMethod);

    try {
      testAuthenticationMethodRetriever.handleHttpResponseException(mockHttpResponseException);
      Assert.fail(
          "Authentication method retriever should fail if 'WWW-Authenticate' header failed to parse");

    } catch (RegistryErrorException ex) {
      Assert.assertThat(
          ex.getMessage(),
          CoreMatchers.containsString(
              "Failed get authentication method from 'WWW-Authenticate' header"));
    }
  }

  @Test
  public void testHandleHttpResponseException_pass()
      throws RegistryErrorException, HttpResponseException, MalformedURLException {
    String authenticationMethod =
        "Bearer realm=\"https://somerealm\",service=\"someservice\",scope=\"somescope\"";

    Mockito.when(mockHttpResponseException.getStatusCode())
        .thenReturn(HttpStatusCodes.STATUS_CODE_UNAUTHORIZED);
    Mockito.when(mockHttpResponseException.getHeaders()).thenReturn(mockHeaders);
    Mockito.when(mockHeaders.getAuthenticate()).thenReturn(authenticationMethod);

    RegistryAuthenticator registryAuthenticator =
        testAuthenticationMethodRetriever.handleHttpResponseException(mockHttpResponseException);

    Assert.assertEquals(
        new URL("https://somerealm?service=someservice&scope=repository:someImageName:someScope"),
        registryAuthenticator.getAuthenticationUrl(null, "someScope"));
  }
}
