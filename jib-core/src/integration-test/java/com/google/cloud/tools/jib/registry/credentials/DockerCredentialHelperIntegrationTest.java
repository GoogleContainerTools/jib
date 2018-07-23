/*
 * Copyright 2017 Google LLC. All rights reserved.
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

package com.google.cloud.tools.jib.registry.credentials;

import com.google.cloud.tools.jib.Command;
import com.google.cloud.tools.jib.http.Authorization;
import com.google.common.io.Resources;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Paths;
import org.hamcrest.CoreMatchers;
import org.junit.Assert;
import org.junit.Test;

/** Integration tests for {@link DockerCredentialHelper}. */
public class DockerCredentialHelperIntegrationTest {

  /** Tests retrieval via {@code docker-credential-gcr} CLI. */
  @Test
  public void testRetrieveGCR()
      throws IOException, NonexistentServerUrlDockerCredentialHelperException,
          NonexistentDockerCredentialHelperException, URISyntaxException, InterruptedException {
    new Command("docker-credential-gcr", "store")
        .run(Files.readAllBytes(Paths.get(Resources.getResource("credentials.json").toURI())));

    DockerCredentialHelper dockerCredentialHelper =
        new DockerCredentialHelperFactory().newDockerCredentialHelper("myregistry", "gcr");

    Authorization authorization = dockerCredentialHelper.retrieve();

    // Checks that token received was base64 encoding of "myusername:mysecret".
    Assert.assertEquals("bXl1c2VybmFtZTpteXNlY3JldA==", authorization.getToken());
  }

  @Test
  public void testRetrieve_nonexistentCredentialHelper()
      throws IOException, NonexistentServerUrlDockerCredentialHelperException {
    try {
      DockerCredentialHelper fakeDockerCredentialHelper =
          new DockerCredentialHelperFactory().newDockerCredentialHelper("", "fake-cloud-provider");

      fakeDockerCredentialHelper.retrieve();

      Assert.fail("Retrieve should have failed for nonexistent credential helper");

    } catch (NonexistentDockerCredentialHelperException ex) {
      Assert.assertEquals(
          "The system does not have docker-credential-fake-cloud-provider CLI", ex.getMessage());
    }
  }

  @Test
  public void testRetrieve_nonexistentServerUrl()
      throws IOException, NonexistentDockerCredentialHelperException {
    try {
      DockerCredentialHelper fakeDockerCredentialHelper =
          new DockerCredentialHelperFactory().newDockerCredentialHelper("fake.server.url", "gcr");

      fakeDockerCredentialHelper.retrieve();

      Assert.fail("Retrieve should have failed for nonexistent server URL");

    } catch (NonexistentServerUrlDockerCredentialHelperException ex) {
      Assert.assertThat(
          ex.getMessage(),
          CoreMatchers.containsString(
              "The credential helper (docker-credential-gcr) has nothing for server URL: fake.server.url"));
    }
  }
}
