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

import java.nio.file.Path;

/** Builds messages that provides suggestions on how to fix the error. */
public class HelpfulSuggestions {

  /**
   * Generates message for when "target image" isn't configured.
   *
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

  public static String forIncompatibleBaseImageJavaVersionForGradle(
      int baseImageMajorJavaVersion, int projectMajorJavaVersion) {
    return forIncompatibleBaseImageJavaVersion(
        baseImageMajorJavaVersion,
        projectMajorJavaVersion,
        "using the 'jib.from.image' parameter, or set targetCompatibility = "
            + baseImageMajorJavaVersion
            + " or below");
  }

  public static String forIncompatibleBaseImageJavaVersionForMaven(
      int baseImageMajorJavaVersion, int projectMajorJavaVersion) {
    return forIncompatibleBaseImageJavaVersion(
        baseImageMajorJavaVersion,
        projectMajorJavaVersion,
        "using the '<from><image>' parameter, or set maven-compiler-plugin's '<target>' or "
            + "'<release>' version to "
            + baseImageMajorJavaVersion
            + " or below");
  }

  public static String forInvalidImageReference(String reference) {
    return suggest(
        "Invalid image reference " + reference,
        "check that the reference is formatted correctly according to "
            + "https://docs.docker.com/engine/reference/commandline/tag/#extended-description\n"
            + "For example, slash-separated name components cannot have uppercase letters");
  }

  /**
   * Helper for creating messages with suggestions.
   *
   * @param messagePrefix the initial message text
   * @param suggestion a suggested fix for the problem described by param: messagePrefix
   * @return the message containing the suggestion
   */
  public static String suggest(String messagePrefix, String suggestion) {
    return messagePrefix + ", perhaps you should " + suggestion;
  }

  private static String forIncompatibleBaseImageJavaVersion(
      int baseImageMajorJavaVersion, int projectMajorJavaVersion, String parameterInstructions) {
    return suggest(
        "Your project is using Java "
            + projectMajorJavaVersion
            + " but the base image is for Java "
            + baseImageMajorJavaVersion,
        "configure a Java "
            + projectMajorJavaVersion
            + "-compatible base image "
            + parameterInstructions
            + " in your build configuration");
  }

  private final String messagePrefix;
  private final String clearCacheCommand;
  private final String toImageConfiguration;
  private final String buildConfigurationFilename;
  private final String toImageFlag;

  /**
   * Creates a new {@link HelpfulSuggestions} with frontend-specific texts.
   *
   * @param messagePrefix the initial message text
   * @param clearCacheCommand the command for clearing the cache
   * @param toImageConfiguration the configuration defining the target image
   * @param toImageFlag the commandline flag used to set the target image
   * @param buildConfigurationFilename the filename of the build configuration
   */
  public HelpfulSuggestions(
      String messagePrefix,
      String clearCacheCommand,
      String toImageConfiguration,
      String toImageFlag,
      String buildConfigurationFilename) {
    this.messagePrefix = messagePrefix;
    this.clearCacheCommand = clearCacheCommand;
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
            + "' is not used by another application or set the `jib.useOnlyProjectCache` system "
            + "property");
  }

  public String forHttpStatusCodeForbidden(String imageReference) {
    return suggest(
        "make sure you have permissions for "
            + imageReference
            + " and set correct credentials. See "
            + "https://github.com/GoogleContainerTools/jib/blob/master/docs/faq.md#what-should-i-do-when-the-registry-responds-with-forbidden-or-denied for help");
  }

  public String forNoCredentialsDefined(String imageReference) {
    return suggest(
        "make sure your credentials for '"
            + imageReference
            + "' are set up correctly. See "
            + "https://github.com/GoogleContainerTools/jib/blob/master/docs/faq.md#what-should-i-do-when-the-registry-responds-with-unauthorized for help");
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
   * Helper for suggestions with configured message prefix.
   *
   * @param suggestion a suggested fix for the problem described by {@link #messagePrefix}
   * @return the message containing the suggestion
   */
  public String suggest(String suggestion) {
    return suggest(messagePrefix, suggestion);
  }
}
