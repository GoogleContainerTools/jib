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

package com.google.cloud.tools.jib.registry;

import com.google.cloud.tools.jib.http.Authorization;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

/**
 * Tests for {@link RegistryClient}. More comprehensive tests can be found in the integration tests.
 */
@RunWith(MockitoJUnitRunner.class)
public class RegistryClientTest {

  @Mock private Authorization mockAuthorization;

  private RegistryClient testRegistryClient;

  @Before
  public void setUp() {
    testRegistryClient =
        new RegistryClient(mockAuthorization, "some.server.url", "some image name");
  }

  @Test
  public void testGetUserAgent_null() {
    Assert.assertTrue(RegistryClient.getUserAgent().startsWith("jib"));

    RegistryClient.setUserAgentSuffix(null);
    Assert.assertTrue(RegistryClient.getUserAgent().startsWith("jib"));
  }

  @Test
  public void testGetUserAgent() {
    RegistryClient.setUserAgentSuffix("some user agent suffix");

    Assert.assertTrue(RegistryClient.getUserAgent().startsWith("jib "));
    Assert.assertTrue(RegistryClient.getUserAgent().endsWith(" some user agent suffix"));
  }

  @Test
  public void testGetApiRouteBase() {
    Assert.assertEquals("https://some.server.url/v2/", testRegistryClient.getApiRouteBase());
  }
}
