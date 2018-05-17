/*
 * Copyright 2018 Google LLC. All rights reserved.
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

package com.google.cloud.tools.jib.frontend;

import java.nio.file.Path;
import java.util.function.Function;

/** Builds messages that provides suggestions on how to fix the error. */
public class HelpfulSuggestions {

  private final String messagePrefix;
  private final String clearCacheCommand;
  private final String baseImageCredHelperConfiguration;
  private final Function<String, String> baseImageAuthConfiguration;
  private final String targetImageCredHelperConfiguration;
  private final Function<String, String> targetImageAuthConfiguration;

  /**
   * Creates a new {@link HelpfulSuggestions} with frontend-specific texts.
   *
   * @param messagePrefix the initial message text
   * @param clearCacheCommand the command for clearing the cache
   * @param baseImageCredHelperConfiguration the configuration defining the credential helper name
   *     for the base image
   * @param baseImageAuthConfiguration the way to define raw credentials for the base image - takes
   *     the base image registry as an argument
   * @param targetImageCredHelperConfiguration the configuration defining the credential helper name
   *     for the target image
   * @param targetImageAuthConfiguration the way to define raw credentials for the target image -
   *     takes the target image registry as an argument
   */
  public HelpfulSuggestions(
      String messagePrefix,
      String clearCacheCommand,
      String baseImageCredHelperConfiguration,
      Function<String, String> baseImageAuthConfiguration,
      String targetImageCredHelperConfiguration,
      Function<String, String> targetImageAuthConfiguration) {
    this.messagePrefix = messagePrefix;
    this.clearCacheCommand = clearCacheCommand;
    this.baseImageCredHelperConfiguration = baseImageCredHelperConfiguration;
    this.baseImageAuthConfiguration = baseImageAuthConfiguration;
    this.targetImageCredHelperConfiguration = targetImageCredHelperConfiguration;
    this.targetImageAuthConfiguration = targetImageAuthConfiguration;
  }

  public String forCacheMetadataCorrupted() {
    return suggest("run '" + clearCacheCommand + "' to clear the cache");
  }

  public String forHttpHostConnect() {
    return suggest("make sure your Internet is up and that the registry you are pushing to exists");
  }

  public String forUnknownHost() {
    return suggest("make sure that the registry you configured exists/is spelled properly");
  }

  public String forCacheDirectoryNotOwned(Path cacheDirectory) {
    return suggest(
        "check that '"
            + cacheDirectory
            + "' is not used by another application or set the `useOnlyProjectCache` "
            + "configuration");
  }

  public String forHttpStatusCodeForbidden(String imageReference) {
    return suggest("make sure you have permissions for " + imageReference);
  }

  public String forNoCredentialHelpersDefinedForBaseImage(String registry) {
    return forNoCredentialHelpersDefined(
        baseImageCredHelperConfiguration, baseImageAuthConfiguration.apply(registry));
  }

  public String forNoCredentialHelpersDefinedForTargetImage(String registry) {
    return forNoCredentialHelpersDefined(
        targetImageCredHelperConfiguration, targetImageAuthConfiguration.apply(registry));
  }

  public String forCredentialsNotCorrect(String registry) {
    return suggest("make sure your credentials for '" + registry + "' are set up correctly");
  }

  public String forDockerContextInsecureRecursiveDelete(String directory) {
    return suggest("clear " + directory + " manually before creating the Docker context");
  }

  public String forMainClassNotFound(String pluginName) {
    return suggest("add a `mainClass` configuration to " + pluginName);
  }

  public String forDockerNotInstalled() {
    return suggest("make sure Docker is installed and you have correct privileges to run it");
  }

  public String none() {
    return messagePrefix;
  }

  /** @return the message containing a suggestion */
  public String suggest(String suggestion) {
    return messagePrefix + ", perhaps you should " + suggestion;
  }

  private String forNoCredentialHelpersDefined(
      String credHelperConfiguration, String authConfiguration) {
    return suggest(
        "set a credential helper name with the configuration '"
            + credHelperConfiguration
            + "' or "
            + authConfiguration);
  }
}
