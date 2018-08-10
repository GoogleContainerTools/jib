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

package com.google.cloud.tools.jib.plugins.common;

import com.google.cloud.tools.jib.JibLogger;
import com.google.cloud.tools.jib.http.Authorization;
import com.google.cloud.tools.jib.http.Authorizations;
import com.google.cloud.tools.jib.image.ImageReference;
import com.google.cloud.tools.jib.image.InvalidImageReferenceException;
import com.google.common.base.Strings;
import java.util.function.Function;
import javax.annotation.Nullable;

/** Validator for plugin configuration parameters and system properties. */
public class ConfigurationPropertyValidator {

  /**
   * Creates a new {@link ConfigurationPropertyValidator} for maven-specific messages.
   *
   * @param jibLogger the logger used to show messages
   * @return the {@link ConfigurationPropertyValidator}
   */
  public static ConfigurationPropertyValidator newMavenPropertyValidator(JibLogger jibLogger) {
    return new ConfigurationPropertyValidator(
        "<%s><image>", "<%s><auth><%s>", "pom.xml", jibLogger);
  }

  /**
   * Creates a new {@link ConfigurationPropertyValidator} for gradle-specific messages.
   *
   * @param jibLogger the logger used to show messages
   * @return the {@link ConfigurationPropertyValidator}
   */
  public static ConfigurationPropertyValidator newGradlePropertyValidator(JibLogger jibLogger) {
    return new ConfigurationPropertyValidator(
        "jib.%s.image", "jib.%s.auth.%s", "build.gradle", jibLogger);
  }

  /**
   * Checks the {@code jib.httpTimeout} system property for invalid (non-integer or negative)
   * values.
   *
   * @param exceptionFactory factory to create an exception with the given description
   * @param <T> the exception type to throw if invalid values
   * @throws T if invalid values
   */
  public static <T extends Throwable> void checkHttpTimeoutProperty(
      Function<String, T> exceptionFactory) throws T {
    String value = System.getProperty("jib.httpTimeout");
    if (value == null) {
      return;
    }
    try {
      if (Integer.parseInt(value) < 0) {
        throw exceptionFactory.apply("jib.httpTimeout cannot be negative: " + value);
      }
    } catch (NumberFormatException ex) {
      throw exceptionFactory.apply("jib.httpTimeout must be an integer: " + value);
    }
  }

  private final String imageParameterTemplate;
  private final String authParameterTemplate;
  private final String buildConfigFilename;
  private final JibLogger jibLogger;

  private ConfigurationPropertyValidator(
      String imageParameterTemplate,
      String authParameterTemplate,
      String buildConfigFilename,
      JibLogger jibLogger) {
    this.imageParameterTemplate = imageParameterTemplate;
    this.authParameterTemplate = authParameterTemplate;
    this.buildConfigFilename = buildConfigFilename;
    this.jibLogger = jibLogger;
  }

  /**
   * Gets an {@link Authorization} from a username and password. First tries system properties, then
   * tries build configuration, otherwise returns null.
   *
   * @param imageParameter which image parameter to get the auth for (e.g. "from" or "to")
   * @param username the configured username
   * @param password the configured password
   * @return a new {@link Authorization} from the system properties or build configuration, or
   *     {@code null} if neither is configured.
   */
  @Nullable
  public Authorization getImageAuth(
      String imageParameter, @Nullable String username, @Nullable String password) {
    // System property takes priority over build configuration
    String usernameProperty = "jib." + imageParameter + ".auth.username";
    String passwordProperty = "jib." + imageParameter + ".auth.password";
    String commandlineUsername = System.getProperty(usernameProperty);
    String commandlinePassword = System.getProperty(passwordProperty);
    if (!Strings.isNullOrEmpty(commandlineUsername)
        && !Strings.isNullOrEmpty(commandlinePassword)) {
      return Authorizations.withBasicCredentials(commandlineUsername, commandlinePassword);
    }

    // Warn if a system property is missing
    if (!Strings.isNullOrEmpty(commandlinePassword) && Strings.isNullOrEmpty(commandlineUsername)) {
      jibLogger.warn(
          passwordProperty
              + " system property is set, but "
              + usernameProperty
              + " is not; attempting other authentication methods.");
    }
    if (!Strings.isNullOrEmpty(commandlineUsername) && Strings.isNullOrEmpty(commandlinePassword)) {
      jibLogger.warn(
          usernameProperty
              + " system property is set, but "
              + passwordProperty
              + " is not; attempting other authentication methods.");
    }

    // Check auth configuration next; warn if they aren't both set
    if (Strings.isNullOrEmpty(username) && Strings.isNullOrEmpty(password)) {
      return null;
    }
    if (Strings.isNullOrEmpty(username)) {
      jibLogger.warn(
          String.format(authParameterTemplate, imageParameter, "username")
              + " is missing from build configuration; ignoring auth section.");
      return null;
    }
    if (Strings.isNullOrEmpty(password)) {
      jibLogger.warn(
          String.format(authParameterTemplate, imageParameter, "password")
              + " is missing from build configuration; ignoring auth section.");
      return null;
    }

    return Authorizations.withBasicCredentials(username, password);
  }

  /**
   * Returns an {@link ImageReference} parsed from the configured target image, or one of the form
   * {@code project-name:project-version} if target image is not configured
   *
   * @param targetImage the configured target image reference (can be empty)
   * @param projectName the name of the project as defined by the plugin
   * @param projectVersion the version of the project as defined by the plugin
   * @throws InvalidImageReferenceException if the configured image is invalid
   * @return an {@link ImageReference} parsed from the configured target image, or one of the form
   *     {@code project-name:project-version} if target image is not configured
   */
  public ImageReference getGeneratedTargetDockerTag(
      @Nullable String targetImage, String projectName, String projectVersion)
      throws InvalidImageReferenceException {
    if (Strings.isNullOrEmpty(targetImage)) {
      jibLogger.lifecycle(
          "Tagging image with generated image reference "
              + projectName
              + ":"
              + projectVersion
              + ". If you'd like to specify a different tag, you can set the "
              + String.format(imageParameterTemplate, "to")
              + " parameter in your "
              + buildConfigFilename
              + ", or use the -Dimage=<MY IMAGE> commandline flag.");
      // TODO: Verify projectName and projectVersion are valid
      return ImageReference.of(null, projectName, projectVersion);
    } else {
      return ImageReference.parse(targetImage);
    }
  }
}
