/*
 * Copyright 2020 Google LLC.
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

package com.google.cloud.tools.jib.cli.cli2;

import com.google.cloud.tools.jib.api.CredentialRetriever;
import com.google.cloud.tools.jib.plugins.common.DefaultCredentialRetrievers;
import java.io.FileNotFoundException;
import java.util.List;

/**
 * A helper class to process command line args and generate a list of {@link CredentialRetriever}s.
 */
public class Credentials {
  /**
   * Get credentials for a target image registry.
   *
   * @param buildOptions The command line build options
   * @param defaultCredentialRetrievers An initialized {@link DefaultCredentialRetrievers} to use
   * @return a list of credentials for a target image registry
   * @throws FileNotFoundException when a credential helper file cannot be found
   */
  public static List<CredentialRetriever> getToCredentialRetrievers(
      JibCli buildOptions, DefaultCredentialRetrievers defaultCredentialRetrievers)
      throws FileNotFoundException {
    // these are all mutually exclusive as enforced by the CLI
    buildOptions
        .getUsernamePassword()
        .ifPresent(
            credential ->
                defaultCredentialRetrievers.setKnownCredential(
                    credential, "--username/--password"));
    buildOptions
        .getToUsernamePassword()
        .ifPresent(
            credential ->
                defaultCredentialRetrievers.setKnownCredential(
                    credential, "--to-username/--to-password"));
    buildOptions.getCredentialHelper().ifPresent(defaultCredentialRetrievers::setCredentialHelper);
    buildOptions
        .getToCredentialHelper()
        .ifPresent(defaultCredentialRetrievers::setCredentialHelper);

    return defaultCredentialRetrievers.asList();
  }

  /**
   * Get credentials for a base image registry.
   *
   * @param buildOptions The command line build options
   * @param defaultCredentialRetrievers An initialized {@link DefaultCredentialRetrievers} to use
   * @return a list of credentials for a base image registry
   * @throws FileNotFoundException when a credential helper file cannot be found
   */
  public static List<CredentialRetriever> getFromCredentialRetrievers(
      JibCli buildOptions, DefaultCredentialRetrievers defaultCredentialRetrievers)
      throws FileNotFoundException {
    // these are all mutually exclusive as enforced by the CLI
    buildOptions
        .getUsernamePassword()
        .ifPresent(
            credential ->
                defaultCredentialRetrievers.setKnownCredential(
                    credential, "--username/--password"));
    buildOptions
        .getFromUsernamePassword()
        .ifPresent(
            credential ->
                defaultCredentialRetrievers.setKnownCredential(
                    credential, "--from-username/--from-password"));
    buildOptions.getCredentialHelper().ifPresent(defaultCredentialRetrievers::setCredentialHelper);
    buildOptions
        .getFromCredentialHelper()
        .ifPresent(defaultCredentialRetrievers::setCredentialHelper);

    return defaultCredentialRetrievers.asList();
  }
}
