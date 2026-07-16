package com.google.cloud.tools.jib.registry;

import com.google.cloud.tools.jib.api.RegistryException;
import com.google.cloud.tools.jib.api.RegistryUnauthorizedException;
import java.io.IOException;
import java.util.function.Supplier;

public interface RegistryUnauthorizedExceptionHandler {

  /**
   * Handle the exception caught by the registry client when it attempted to communicate with the
   * registry.
   *
   * <p>The most obvious action is to simply throw {@code ex}. Other possible actions are to
   * reauthenticate the client.
   *
   * @param registryClient the registry client which may be reconfigured
   * @param ex the exception being handled on behalf of the client
   * @return a supplier to use if another exception occurs on the next retry
   * @throws IOException if an I/O error occurs
   * @throws RegistryException if a registry error occurs
   */
  Supplier<RegistryUnauthorizedExceptionHandler> handle(
      RegistryClient registryClient, RegistryUnauthorizedException ex)
      throws IOException, RegistryException;
}
