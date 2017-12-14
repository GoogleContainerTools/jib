package com.google.cloud.tools.crepecake.registry;

import java.net.MalformedURLException;

/** Static initializers for {@link RegistryAuthenticator}. */
public abstract class RegistryAuthenticators {

  public static RegistryAuthenticator forDockerHub(String repository) throws RegistryAuthenticationFailedException {
    try {
      return new RegistryAuthenticator("https://auth.docker.io/token", "registry.docker.io", repository);
    } catch (MalformedURLException ex) {
      throw new RegistryAuthenticationFailedException(ex);
    }
  }
}
