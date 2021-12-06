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

import com.google.cloud.tools.jib.api.Credential;
import com.google.cloud.tools.jib.api.ImageReference;
import com.google.cloud.tools.jib.api.InvalidImageReferenceException;
import com.google.cloud.tools.jib.api.LogEvent;
import com.google.cloud.tools.jib.http.Authorization;
import com.google.common.base.Strings;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.annotation.Nullable;

/** Validator for plugin configuration parameters and system properties. */
public class ConfigurationPropertyValidator {

  /** Matches key-value pairs in the form of "key=value". */
  private static final Pattern KEY_VALUE_PATTERN = Pattern.compile("(?<name>[^=]+)=(?<value>.*)");

  /**
   * Gets a {@link Credential} from a username and password. First tries system properties, then
   * tries build configuration, otherwise returns null.
   *
   * @param logger a consumer for handling log events
   * @param usernameProperty the name of the username system property
   * @param passwordProperty the name of the password system property
   * @param auth the configured credentials
   * @param rawConfiguration the {@link RawConfiguration} that provides system properties
   * @return a new {@link Authorization} from the system properties or build configuration, or
   *     {@link Optional#empty} if neither is configured.
   */
  public static Optional<Credential> getImageCredential(
      Consumer<LogEvent> logger,
      String usernameProperty,
      String passwordProperty,
      AuthProperty auth,
      RawConfiguration rawConfiguration) {
    // System property takes priority over build configuration
    String commandlineUsername = rawConfiguration.getProperty(usernameProperty).orElse("");
    String commandlinePassword = rawConfiguration.getProperty(passwordProperty).orElse("");
    if (!commandlineUsername.isEmpty() && !commandlinePassword.isEmpty()) {
      return Optional.of(Credential.from(commandlineUsername, commandlinePassword));
    }

    // Warn if a system property is missing
    String missingProperty =
        "%s system property is set, but %s is not; attempting other authentication methods.";
    if (!commandlinePassword.isEmpty()) {
      logger.accept(
          LogEvent.warn(String.format(missingProperty, passwordProperty, usernameProperty)));
    }
    if (!commandlineUsername.isEmpty()) {
      logger.accept(
          LogEvent.warn(String.format(missingProperty, usernameProperty, passwordProperty)));
    }

    // Check auth configuration next; warn if they aren't both set
    if (!Strings.isNullOrEmpty(auth.getUsername()) && !Strings.isNullOrEmpty(auth.getPassword())) {
      return Optional.of(Credential.from(auth.getUsername(), auth.getPassword()));
    }

    String missingConfig = "%s is missing from build configuration; ignoring auth section.";
    if (!Strings.isNullOrEmpty(auth.getPassword())) {
      logger.accept(LogEvent.warn(String.format(missingConfig, auth.getUsernameDescriptor())));
    }
    if (!Strings.isNullOrEmpty(auth.getUsername())) {
      logger.accept(LogEvent.warn(String.format(missingConfig, auth.getPasswordDescriptor())));
    }

    return Optional.empty();
  }

  /**
   * Returns an {@link ImageReference} parsed from the configured target image, or one of the form
   * {@code project-name:project-version} if target image is not configured.
   *
   * @param targetImage the configured target image reference
   * @param projectProperties the {@link ProjectProperties} providing the project name, version, and
   *     log event handler
   * @param helpfulSuggestions used for generating the message notifying the user of the generated
   *     tag
   * @return an {@link ImageReference} parsed from the configured target image, or one of the form
   *     {@code project-name:project-version} if target image is not configured
   * @throws InvalidImageReferenceException if the configured or generated image reference is
   *     invalid
   */
  public static ImageReference getGeneratedTargetDockerTag(
      @Nullable String targetImage,
      ProjectProperties projectProperties,
      HelpfulSuggestions helpfulSuggestions)
      throws InvalidImageReferenceException {
    String generatedName = projectProperties.getName();
    String generatedTag =
        projectProperties.getVersion().equals("unspecified")
            ? "latest"
            : projectProperties.getVersion();
    if (Strings.isNullOrEmpty(targetImage)) {
      projectProperties.log(
          LogEvent.lifecycle(helpfulSuggestions.forGeneratedTag(generatedName, generatedTag)));

      // Try to parse generated tag to verify that project name and version are valid (throws an
      // exception if parse fails)
      ImageReference.parse(generatedName + ":" + generatedTag);
      return ImageReference.of(null, generatedName, generatedTag);
    } else {
      return ImageReference.parse(targetImage);
    }
  }

  /**
   * Parses a string in the form of "key1=value1,key2=value2,..." into a map.
   *
   * @param property the map string to parse, with entries separated by "," and key-value pairs
   *     separated by "="
   * @return the map of parsed values
   */
  public static Map<String, String> parseMapProperty(String property) {
    Map<String, String> result = new LinkedHashMap<>(); // LinkedHashMap to keep insertion order

    // Split on non-escaped commas
    List<String> entries = parseListProperty(property);
    for (String entry : entries) {
      Matcher matcher = KEY_VALUE_PATTERN.matcher(entry);
      if (!matcher.matches()) {
        throw new IllegalArgumentException("'" + entry + "' is not a valid key-value pair");
      }
      result.put(matcher.group("name"), matcher.group("value"));
    }
    return result;
  }

  /**
   * Parses a comma-separated string into a list. Ignores commas escaped with "\".
   *
   * @param property the comma-separated string
   * @return the list of parsed values
   */
  public static List<String> parseListProperty(String property) {
    List<String> items = new ArrayList<>();
    StringBuilder token = new StringBuilder();
    for (int i = 0; i < property.length(); i++) {
      if (property.charAt(i) == ',') {
        // Split on non-escaped comma
        items.add(token.toString());
        token.setLength(0);
      } else {
        if (i + 1 < property.length()
            && property.charAt(i) == '\\'
            && property.charAt(i + 1) == ',') {
          // Found an escaped comma. Add a comma.
          i++;
        }
        token.append(property.charAt(i));
      }
    }
    items.add(token.toString());
    return items;
  }

  private ConfigurationPropertyValidator() {}
}
