/*
 * Copyright 2017 Google LLC.
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
import com.google.cloud.tools.jib.api.Credential;
import com.google.common.io.Resources;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Paths;
import org.hamcrest.CoreMatchers;
import org.hamcrest.MatcherAssert;
import org.junit.Assert;
import org.junit.Test;

/** Integration tests for {@link DockerCredentialHelper}. */
public class DockerCredentialHelperIntegrationTest {

  // This binary must exist and be functioning properly for these tests to succeed.
  private static final String GCR_CREDENTIAL_HELPER = "docker-credential-gcr";

  /** Tests retrieval via {@code docker-credential-gcr} CLI. */
  @Test
  public void testRetrieveGcr()
      throws IOException, CredentialHelperUnhandledServerUrlException,
          CredentialHelperNotFoundException, URISyntaxException, InterruptedException {
    new Command(GCR_CREDENTIAL_HELPER, "store")
        .run(Files.readAllBytes(Paths.get(Resources.getResource("credentials.json").toURI())));

    DockerCredentialHelper dockerCredentialHelper =
        new DockerCredentialHelper("myregistry", Paths.get(GCR_CREDENTIAL_HELPER));

    Credential credentials = dockerCredentialHelper.retrieve();
    Assert.assertEquals("myusername", credentials.getUsername());
    Assert.assertEquals("mysecret", credentials.getPassword());
  }

  @Test
  public void testRetrieve_nonexistentCredentialHelper()
      throws IOException, CredentialHelperUnhandledServerUrlException {
    try {
      DockerCredentialHelper fakeDockerCredentialHelper =
          new DockerCredentialHelper("", Paths.get("non-existing-helper"));

      fakeDockerCredentialHelper.retrieve();

      Assert.fail("Retrieve should have failed for nonexistent credential helper");

    } catch (CredentialHelperNotFoundException ex) {
      Assert.assertEquals("The system does not have non-existing-helper CLI", ex.getMessage());
    }
  }

  @Test
  public void testRetrieve_nonexistentServerUrl()
      throws IOException, CredentialHelperNotFoundException {
    try {
      DockerCredentialHelper fakeDockerCredentialHelper =
          new DockerCredentialHelper("fake.server.url", Paths.get(GCR_CREDENTIAL_HELPER));

      fakeDockerCredentialHelper.retrieve();

      Assert.fail("Retrieve should have failed for nonexistent server URL");

    } catch (CredentialHelperUnhandledServerUrlException ex) {
      MatcherAssert.assertThat(
          ex.getMessage(),
          CoreMatchers.containsString(
              "The credential helper (docker-credential-gcr) has nothing for server URL: fake.server.url"));
    }
  }
}
