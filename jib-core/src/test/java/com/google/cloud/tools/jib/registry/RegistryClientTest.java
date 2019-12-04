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

import com.google.cloud.tools.jib.event.EventHandlers;
import com.google.cloud.tools.jib.global.JibSystemProperties;
import com.google.cloud.tools.jib.http.Authorization;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.contrib.java.lang.system.RestoreSystemProperties;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

/**
 * Tests for {@link RegistryClient}. More comprehensive tests can be found in the integration tests.
 */
@RunWith(MockitoJUnitRunner.class)
public class RegistryClientTest {

  @Mock private EventHandlers eventHandlers;
  @Mock private Authorization mockAuthorization;

  private RegistryClient.Factory testRegistryClientFactory;

  @Before
  public void setUp() {
    testRegistryClientFactory =
        RegistryClient.factory(eventHandlers, "some.server.url", "some image name", null);
  }

  @Rule public final RestoreSystemProperties systemPropertyRestorer = new RestoreSystemProperties();

  @Test
  public void testGetUserAgent_null() {
    Assert.assertTrue(
        testRegistryClientFactory
            .setAuthorization(mockAuthorization)
            .newRegistryClient()
            .getUserAgent()
            .startsWith("jib"));

    Assert.assertTrue(
        testRegistryClientFactory
            .setAuthorization(mockAuthorization)
            .setUserAgentSuffix(null)
            .newRegistryClient()
            .getUserAgent()
            .startsWith("jib"));
  }

  @Test
  public void testGetUserAgent() {
    RegistryClient registryClient =
        testRegistryClientFactory.setUserAgentSuffix("some user agent suffix").newRegistryClient();

    Assert.assertTrue(registryClient.getUserAgent().startsWith("jib "));
    Assert.assertTrue(registryClient.getUserAgent().endsWith(" some user agent suffix"));
  }

  @Test
  public void testGetUserAgentWithUpstreamClient() {
    System.setProperty(JibSystemProperties.UPSTREAM_CLIENT, "skaffold/0.34.0");

    RegistryClient registryClient =
        testRegistryClientFactory.setUserAgentSuffix("foo").newRegistryClient();
    Assert.assertTrue(registryClient.getUserAgent().startsWith("jib "));
    Assert.assertTrue(registryClient.getUserAgent().endsWith(" skaffold/0.34.0"));
  }
}
