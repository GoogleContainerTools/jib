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

import com.google.cloud.tools.jib.JibLogger;
import com.google.cloud.tools.jib.configuration.credentials.Credential;
import com.google.cloud.tools.jib.http.Authorization;
import com.google.cloud.tools.jib.image.ImageReference;
import com.google.cloud.tools.jib.image.InvalidImageReferenceException;
import com.google.common.base.Strings;
import java.util.function.Function;
import javax.annotation.Nullable;

/** Validator for plugin configuration parameters and system properties. */
public class ConfigurationPropertyValidator {

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

  /**
   * Gets a {@link Credential} from a username and password. First tries system properties, then
   * tries build configuration, otherwise returns null.
   *
   * @param logger the {@link JibLogger} used to print warnings messages
   * @param usernameProperty the name of the username system property
   * @param passwordProperty the name of the password system property
   * @param auth the configured credentials
   * @return a new {@link Authorization} from the system properties or build configuration, or
   *     {@code null} if neither is configured.
   */
  @Nullable
  public static Credential getImageCredential(
      JibLogger logger, String usernameProperty, String passwordProperty, AuthProperty auth) {
    // System property takes priority over build configuration
    String commandlineUsername = System.getProperty(usernameProperty);
    String commandlinePassword = System.getProperty(passwordProperty);
    if (!Strings.isNullOrEmpty(commandlineUsername)
        && !Strings.isNullOrEmpty(commandlinePassword)) {
      return new Credential(commandlineUsername, commandlinePassword);
    }

    // Warn if a system property is missing
    if (!Strings.isNullOrEmpty(commandlinePassword) && Strings.isNullOrEmpty(commandlineUsername)) {
      logger.warn(
          passwordProperty
              + " system property is set, but "
              + usernameProperty
              + " is not; attempting other authentication methods.");
    }
    if (!Strings.isNullOrEmpty(commandlineUsername) && Strings.isNullOrEmpty(commandlinePassword)) {
      logger.warn(
          usernameProperty
              + " system property is set, but "
              + passwordProperty
              + " is not; attempting other authentication methods.");
    }

    // Check auth configuration next; warn if they aren't both set
    if (Strings.isNullOrEmpty(auth.getUsername()) && Strings.isNullOrEmpty(auth.getPassword())) {
      return null;
    }
    if (Strings.isNullOrEmpty(auth.getUsername())) {
      logger.warn(
          auth.getUsernamePropertyDescriptor()
              + " is missing from build configuration; ignoring auth section.");
      return null;
    }
    if (Strings.isNullOrEmpty(auth.getPassword())) {
      logger.warn(
          auth.getPasswordPropertyDescriptor()
              + " is missing from build configuration; ignoring auth section.");
      return null;
    }

    return new Credential(auth.getUsername(), auth.getPassword());
  }

  /**
   * Returns an {@link ImageReference} parsed from the configured target image, or one of the form
   * {@code project-name:project-version} if target image is not configured
   *
   * @param targetImage the configured target image reference
   * @param logger the {@link JibLogger} used to show messages
   * @param projectName the project name, as determined by the plugin
   * @param projectVersion the project version, as determined by the plugin
   * @param helpfulSuggestions used for generating the message notifying the user of the generated
   *     tag
   * @return an {@link ImageReference} parsed from the configured target image, or one of the form
   *     {@code project-name:project-version} if target image is not configured
   * @throws InvalidImageReferenceException if the configured or generated image reference is
   *     invalid
   */
  public static ImageReference getGeneratedTargetDockerTag(
      @Nullable String targetImage,
      JibLogger logger,
      String projectName,
      String projectVersion,
      HelpfulSuggestions helpfulSuggestions)
      throws InvalidImageReferenceException {
    if (Strings.isNullOrEmpty(targetImage)) {
      logger.lifecycle(helpfulSuggestions.forGeneratedTag(projectName, projectVersion));

      // Try to parse generated tag to verify that project name and version are valid (throws an
      // exception if parse fails)
      ImageReference.parse(projectName + ":" + projectVersion);
      return ImageReference.of(null, projectName, projectVersion);
    } else {
      return ImageReference.parse(targetImage);
    }
  }

  private ConfigurationPropertyValidator() {}
}
