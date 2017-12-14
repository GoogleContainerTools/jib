package com.google.cloud.tools.crepecake.registry;

import com.fasterxml.jackson.databind.exc.MismatchedInputException;
import com.google.cloud.tools.crepecake.http.Authorization;
import java.io.IOException;
import org.junit.Assert;
import org.junit.Test;

/** Integration tests for {@link DockerCredentialRetriever}. */
public class DockerCredentialRetrieverIntegrationTest {

  @Test
  public void testRetrieveGCR() throws IOException {
    DockerCredentialRetriever dockerCredentialRetriever =
        new DockerCredentialRetriever("gcr.io", "gcloud");

    Authorization authorization;
    try {
      authorization = dockerCredentialRetriever.retrieve();

    } catch (MismatchedInputException ex) {
      if (!ex.getMessage().contains("No content to map due to end-of-input")) {
        throw ex;
      }
      // The credential store has nothing for serverUrl=gcr.io.
      return;

    } catch (IOException ex) {
      if (!ex.getMessage().contains("No such file or directory")) {
        throw ex;
      }
      // The system does not have docker-credential-gcloud CLI.
      return;
    }

    // Checks that some token was received.
    Assert.assertTrue(0 < authorization.asList().get(1).length());
  }
}
