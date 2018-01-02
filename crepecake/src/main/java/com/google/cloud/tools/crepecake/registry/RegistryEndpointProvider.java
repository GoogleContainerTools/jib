package com.google.cloud.tools.crepecake.registry;

import com.google.cloud.tools.crepecake.http.Response;
import java.io.IOException;

/** Provides implementations for a registry endpoint. */
interface RegistryEndpointProvider {

  /** Handles the response specific to the registry action. */
  Object handleResponse(Response response) throws IOException, RegistryException;

  /**
   * @return the suffix for the registry endpoint after the namespace (for example, {@code
   *     "/manifests/latest"})
   */
  String getApiRouteSuffix();

  /**
   * @return a description of the registry action performed, used in error messages to describe the
   *     action that failed
   */
  String getActionDescription(String serverUrl, String imageName);
}
