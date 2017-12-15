/*
 * Copyright 2017 Google Inc.
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

import com.fasterxml.jackson.databind.exc.MismatchedInputException;
import com.google.cloud.tools.crepecake.http.Authorization;
import java.io.IOException;
import org.junit.Assert;
import org.junit.Test;

/** Integration tests for {@link DockerCredentialRetriever}. */
public class DockerCredentialRetrieverIntegrationTest {

  /**
   * Tests retrieval via {@code docker-credential-gcloud} CLI.
   *
   * <p>This test will ignore errors if
   *
   * <ul>
   *   <li>{@code docker-credential-gcloud} does not exist, or
   *   <li>{@code gcr.io} does not exist as a server URL
   * </ul>
   */
  @Test
  public void testRetrieveGCR() throws IOException {
    try {
      DockerCredentialRetriever dockerCredentialRetriever =
          new DockerCredentialRetriever("gcr.io", "gcloud");

      Authorization authorization = dockerCredentialRetriever.retrieve();

      // Checks that some token was received.
      Assert.assertTrue(0 < authorization.getToken().length());

    } catch (MismatchedInputException ex) {
      if (!ex.getMessage().contains("No content to map due to end-of-input")) {
        throw ex;
      }
      // The credential store has nothing for serverUrl=gcr.io.

    } catch (IOException ex) {
      if (!ex.getMessage().contains("No such file or directory")) {
        throw ex;
      }
      // The system does not have docker-credential-gcloud CLI.
    }
  }
}
