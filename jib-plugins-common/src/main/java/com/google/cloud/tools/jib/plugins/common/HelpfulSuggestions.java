/*
 * Copyright 2018 Google LLC.
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

package com.google.cloud.tools.jib.plugins.common;

import com.google.cloud.tools.jib.image.ImageReference;
import com.google.common.base.Preconditions;
import java.nio.file.Path;
import java.util.function.Function;
import javax.annotation.Nullable;

/** Builds messages that provides suggestions on how to fix the error. */
public class HelpfulSuggestions {

  /**
   * @param messagePrefix the initial message text
   * @param parameter the parameter name (e.g. 'to.image' or {@literal <to><image>})
   * @param buildConfigFilename the name of the build config (build.gradle or pom.xml)
   * @param command an example command for passing the parameter via commandline
   * @return a suggested fix for a missing target image configuration
   */
  public static String forToNotConfigured(
      String messagePrefix, String parameter, String buildConfigFilename, String command) {
    return suggest(
        messagePrefix,
        "add a "
            + parameter
            + " configuration parameter to your "
            + buildConfigFilename
            + " or set the parameter via the commandline (e.g. '"
            + command
            + "').");
  }

  public static String forDockerNotInstalled(String messagePrefix) {
    return suggest(
        messagePrefix, "make sure Docker is installed and you have correct privileges to run it");
  }

  public static String forMainClassNotFound(String messagePrefix, String pluginName) {
    return suggest(messagePrefix, "add a `mainClass` configuration to " + pluginName);
  }

  public static String forDockerContextInsecureRecursiveDelete(
      String messagePrefix, String directory) {
    return suggest(
        messagePrefix, "clear " + directory + " manually before creating the Docker context");
  }

  /**
   * @param messagePrefix the initial message text
   * @param suggestion a suggested fix for the problem described by {@link #messagePrefix}
   * @return the message containing the suggestion
   */
  public static String suggest(String messagePrefix, String suggestion) {
    return messagePrefix + ", perhaps you should " + suggestion;
  }

  private final String messagePrefix;
  private final String clearCacheCommand;
  @Nullable private final ImageReference baseImageReference;
  private final boolean noCredentialsDefinedForBaseImage;
  private final String baseImageCredHelperConfiguration;
  private final Function<String, String> baseImageAuthConfiguration;
  @Nullable private final ImageReference targetImageReference;
  private final boolean noCredentialsDefinedForTargetImage;
  private final String targetImageCredHelperConfiguration;
  private final Function<String, String> targetImageAuthConfiguration;
  private final String toImageConfiguration;
  private final String buildConfigurationFilename;
  private final String toImageFlag;

  /**
   * Creates a new {@link HelpfulSuggestions} with frontend-specific texts.
   *
   * @param messagePrefix the initial message text
   * @param clearCacheCommand the command for clearing the cache
   * @param baseImageReference the base image reference
   * @param noCredentialsDefinedForBaseImage {@code true} if no credentials were defined for the
   *     base image; {@code false} otherwise
   * @param baseImageCredHelperConfiguration the configuration defining the credential helper name
   *     for the base image
   * @param baseImageAuthConfiguration the way to define raw credentials for the base image - takes
   *     the base image registry as an argument
   * @param targetImageReference the target image reference
   * @param noCredentialsDefinedForTargetImage {@code true} if no credentials were defined for the
   *     base image; {@code false} otherwise
   * @param targetImageCredHelperConfiguration the configuration defining the credential helper name
   *     for the target image
   * @param targetImageAuthConfiguration the way to define raw credentials for the target image -
   *     takes the target image registry as an argument
   * @param toImageConfiguration the configuration defining the target image
   * @param toImageFlag the commandline flag used to set the target image
   * @param buildConfigurationFilename the filename of the build configuration
   */
  public HelpfulSuggestions(
      String messagePrefix,
      String clearCacheCommand,
      @Nullable ImageReference baseImageReference,
      boolean noCredentialsDefinedForBaseImage,
      String baseImageCredHelperConfiguration,
      Function<String, String> baseImageAuthConfiguration,
      @Nullable ImageReference targetImageReference,
      boolean noCredentialsDefinedForTargetImage,
      String targetImageCredHelperConfiguration,
      Function<String, String> targetImageAuthConfiguration,
      String toImageConfiguration,
      String toImageFlag,
      String buildConfigurationFilename) {
    this.messagePrefix = messagePrefix;
    this.clearCacheCommand = clearCacheCommand;
    this.baseImageReference = baseImageReference;
    this.noCredentialsDefinedForBaseImage = noCredentialsDefinedForBaseImage;
    this.baseImageCredHelperConfiguration = baseImageCredHelperConfiguration;
    this.baseImageAuthConfiguration = baseImageAuthConfiguration;
    this.targetImageReference = targetImageReference;
    this.noCredentialsDefinedForTargetImage = noCredentialsDefinedForTargetImage;
    this.targetImageCredHelperConfiguration = targetImageCredHelperConfiguration;
    this.targetImageAuthConfiguration = targetImageAuthConfiguration;
    this.toImageConfiguration = toImageConfiguration;
    this.buildConfigurationFilename = buildConfigurationFilename;
    this.toImageFlag = toImageFlag;
  }

  public String forHttpHostConnect() {
    return suggest("make sure your Internet is up and that the registry you are pushing to exists");
  }

  public String forUnknownHost() {
    return suggest("make sure that the registry you configured exists/is spelled properly");
  }

  public String forCacheNeedsClean() {
    return suggest("run '" + clearCacheCommand + "' to clear your build cache");
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

  public String forNoCredentialsDefined(String registry, String repository) {
    Preconditions.checkNotNull(baseImageReference);
    Preconditions.checkNotNull(targetImageReference);
    if (noCredentialsDefinedForBaseImage
        && registry.equals(baseImageReference.getRegistry())
        && repository.equals(baseImageReference.getRepository())) {
      return forNoCredentialHelpersDefined(
          baseImageCredHelperConfiguration, baseImageAuthConfiguration.apply(registry));
    }
    if (noCredentialsDefinedForTargetImage
        && registry.equals(targetImageReference.getRegistry())
        && repository.equals(targetImageReference.getRepository())) {
      return forNoCredentialHelpersDefined(
          targetImageCredHelperConfiguration, targetImageAuthConfiguration.apply(registry));
    }
    // Credential helper probably was not configured correctly or did not have the necessary
    // credentials.
    return forCredentialsNotCorrect(registry);
  }

  public String forCredentialsNotSent() {
    return suggest(
        "use a registry that supports HTTPS so credentials can be sent safely, or set the 'sendCredentialsOverHttp' system property to true");
  }

  public String forInsecureRegistry() {
    return suggest(
        "use a registry that supports HTTPS or set the configuration parameter 'allowInsecureRegistries'");
  }

  public String forGeneratedTag(String projectName, String projectVersion) {
    return "Tagging image with generated image reference "
        + projectName
        + ":"
        + projectVersion
        + ". If you'd like to specify a different tag, you can set the "
        + toImageConfiguration
        + " parameter in your "
        + buildConfigurationFilename
        + ", or use the "
        + toImageFlag
        + "=<MY IMAGE> commandline flag.";
  }

  public String none() {
    return messagePrefix;
  }

  /**
   * @param suggestion a suggested fix for the problem described by {@link #messagePrefix}
   * @return the message containing the suggestion
   */
  public String suggest(String suggestion) {
    return suggest(messagePrefix, suggestion);
  }

  private String forNoCredentialHelpersDefined(
      String credHelperConfiguration, String authConfiguration) {
    return suggest(
        "set a credential helper name with the configuration '"
            + credHelperConfiguration
            + "' or "
            + authConfiguration);
  }

  private String forCredentialsNotCorrect(String registry) {
    return suggest("make sure your credentials for '" + registry + "' are set up correctly");
  }
}
