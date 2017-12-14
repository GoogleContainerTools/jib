package com.google.cloud.tools.crepecake.registry;

import com.google.cloud.tools.crepecake.http.Authorization;
import org.junit.Assert;
import org.junit.Test;

/** Integration tests for {@link RegistryAuthenticator}. */
public class RegistryAuthenticatorIntegrationTest {

  @Test
  public void testAuthenticate() throws RegistryAuthenticationFailedException {
    RegistryAuthenticator registryAuthenticator =
        RegistryAuthenticators.forDockerHub("library/busybox");
    Authorization authorization = registryAuthenticator.authenticate();

    // Checks that some token was received.
    Assert.assertTrue(0 < authorization.asList().get(1).length());
  }
}
