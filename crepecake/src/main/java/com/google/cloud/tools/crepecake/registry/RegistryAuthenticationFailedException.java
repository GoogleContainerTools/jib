package com.google.cloud.tools.crepecake.registry;

/** Thrown because registry authentication failed. */
public class RegistryAuthenticationFailedException extends Exception {

  RegistryAuthenticationFailedException(Throwable cause) {
    super("Failed to authenticate with the registry because: " + cause.getMessage(), cause);
  }
}
