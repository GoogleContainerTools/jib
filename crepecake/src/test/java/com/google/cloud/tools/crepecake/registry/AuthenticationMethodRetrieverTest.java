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

import com.google.api.client.http.HttpMethods;
import com.google.cloud.tools.crepecake.http.Response;
import java.net.MalformedURLException;
import java.net.URL;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;

/** Tests for {@link AuthenticationMethodRetriever}. */
public class AuthenticationMethodRetrieverTest {

  private final RegistryEndpointProperties fakeRegistryEndpointProperties =
      new RegistryEndpointProperties("someServerUrl", "someImageName");
  private final AuthenticationMethodRetriever testAuthenticationMethodRetriever =
      new AuthenticationMethodRetriever(fakeRegistryEndpointProperties);

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
}
