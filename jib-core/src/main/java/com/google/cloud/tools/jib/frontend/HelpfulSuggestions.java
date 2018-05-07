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

  /** The initial message text */
  private static final String MESSAGE_PREFIX = "Build image failed";

  private static String forNoCredentialHelpersDefined(
      String credHelperConfiguration, String authConfiguration) {
    return suggest(
        "set a credential helper name with the configuration '"
            + credHelperConfiguration
            + "' or "
            + authConfiguration);
  }

  /** @return the message containing a suggestion */
  private static String suggest(String suggestion) {
    return MESSAGE_PREFIX + ", perhaps you should " + suggestion;
  }

  private final String clearCacheCommand;
  private final String baseImageCredHelperConfiguration;
  private final Function<String, String> baseImageAuthConfiguration;
  private final String targetImageCredHelperConfiguration;
  private final Function<String, String> targetImageAuthConfiguration;

  /**
   * Creates a new {@link HelpfulSuggestions} with frontend-specific texts.
   *
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
      String clearCacheCommand,
      String baseImageCredHelperConfiguration,
      Function<String, String> baseImageAuthConfiguration,
      String targetImageCredHelperConfiguration,
      Function<String, String> targetImageAuthConfiguration) {
    this.clearCacheCommand = clearCacheCommand;
    this.baseImageCredHelperConfiguration = baseImageCredHelperConfiguration;
    this.baseImageAuthConfiguration = baseImageAuthConfiguration;
    this.targetImageCredHelperConfiguration = targetImageCredHelperConfiguration;
    this.targetImageAuthConfiguration = targetImageAuthConfiguration;
  }

  String forCacheMetadataCorrupted() {
    return suggest("run '" + clearCacheCommand + "' to clear the cache");
  }

  String forHttpHostConnect() {
    return suggest("make sure your Internet is up and that the registry you are pushing to exists");
  }

  String forUnknownHost() {
    return suggest("make sure that the registry you configured exists/is spelled properly");
  }

  String forCacheDirectoryNotOwned(Path cacheDirectory) {
    return suggest(
        "check that '"
            + cacheDirectory
            + "' is not used by another application or set the `useOnlyProjectCache` "
            + "configuration");
  }

  String forHttpStatusCodeForbidden(String imageReference) {
    return suggest("make sure you have permissions for " + imageReference);
  }

  String forNoCredentialHelpersDefinedForBaseImage(String registry) {
    return suggest(
        "set a credential helper name with the configuration '"
            + baseImageCredHelperConfiguration
            + "' or "
            + baseImageAuthConfiguration.apply(registry));
  }

  String forNoCredentialHelpersDefinedForTargetImage(String registry) {
    return suggest(
        "set a credential helper name with the configuration '"
            + targetImageCredHelperConfiguration
            + "' or "
            + targetImageAuthConfiguration.apply(registry));
  }

  String forCredentialsNotCorrect(String registry) {
    return suggest("make sure your credentials for '" + registry + "' are set up correctly");
  }

  String none() {
    return MESSAGE_PREFIX;
  }
}
