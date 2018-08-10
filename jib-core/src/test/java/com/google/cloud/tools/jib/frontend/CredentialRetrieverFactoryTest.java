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

package com.google.cloud.tools.jib.frontend;

import com.google.cloud.tools.jib.JibLogger;
import com.google.cloud.tools.jib.configuration.credentials.Credentials;
import com.google.cloud.tools.jib.image.ImageReference;
import com.google.cloud.tools.jib.registry.credentials.DockerCredentialHelper;
import com.google.cloud.tools.jib.registry.credentials.DockerCredentialHelperFactory;
import java.nio.file.Paths;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

/** Tests for {@link CredentialRetrieverFactory}. */
@RunWith(MockitoJUnitRunner.class)
public class CredentialRetrieverFactoryTest {

  @Mock private JibLogger mockJibLogger;
  @Mock private DockerCredentialHelperFactory mockDockerCredentialHelperFactory;
  @Mock private DockerCredentialHelper mockDockerCredentialHelper;

  @Test
  public void testDockerCredentialHelper() throws Exception {
    CredentialRetrieverFactory credentialProviderFactory =
        CredentialRetrieverFactory.forImage(
            ImageReference.of("registry", null, null), mockJibLogger);
    Credentials expectedCredentials = new Credentials("username", "password");

    Mockito.when(
            mockDockerCredentialHelperFactory.newDockerCredentialHelper(
                "registry", Paths.get("docker-credential-helper")))
        .thenReturn(mockDockerCredentialHelper);
    Mockito.when(mockDockerCredentialHelper.retrieve()).thenReturn(expectedCredentials);

    Assert.assertEquals(
        expectedCredentials,
        credentialProviderFactory
            .dockerCredentialHelper(
                Paths.get("docker-credential-helper"), mockDockerCredentialHelperFactory)
            .retrieve());

    Mockito.verify(mockJibLogger).info("Using docker-credential-helper for registry");
  }
}
