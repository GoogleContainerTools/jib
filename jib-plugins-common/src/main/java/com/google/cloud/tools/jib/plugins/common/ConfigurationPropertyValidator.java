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

import com.google.cloud.tools.jib.api.ImageReference;
import com.google.cloud.tools.jib.api.InvalidImageReferenceException;
import com.google.cloud.tools.jib.configuration.credentials.Credential;
import com.google.cloud.tools.jib.event.EventHandlers;
import com.google.cloud.tools.jib.event.events.LogEvent;
import com.google.cloud.tools.jib.http.Authorization;
import com.google.common.base.Strings;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.annotation.Nullable;

/** Validator for plugin configuration parameters and system properties. */
public class ConfigurationPropertyValidator {

  /** Matches key-value pairs in the form of "key=value" */
  private static final Pattern KEY_VALUE_PATTERN = Pattern.compile("(?<name>[^=]+)=(?<value>.*)");

  /**
   * Gets a {@link Credential} from a username and password. First tries system properties, then
   * tries build configuration, otherwise returns null.
   *
   * @param eventHandlers the {@link EventHandlers} used to dispatch log events
   * @param usernameProperty the name of the username system property
   * @param passwordProperty the name of the password system property
   * @param auth the configured credentials
   * @param rawConfiguration the {@link RawConfiguration} that provides system properties
   * @return a new {@link Authorization} from the system properties or build configuration, or
   *     {@link Optional#empty} if neither is configured.
   */
  public static Optional<Credential> getImageCredential(
      EventHandlers eventHandlers,
      String usernameProperty,
      String passwordProperty,
      AuthProperty auth,
      RawConfiguration rawConfiguration) {
    // System property takes priority over build configuration
    String commandlineUsername = rawConfiguration.getProperty(usernameProperty).orElse(null);
    String commandlinePassword = rawConfiguration.getProperty(passwordProperty).orElse(null);
    if (!Strings.isNullOrEmpty(commandlineUsername)
        && !Strings.isNullOrEmpty(commandlinePassword)) {
      return Optional.of(Credential.from(commandlineUsername, commandlinePassword));
    }

    // Warn if a system property is missing
    if (!Strings.isNullOrEmpty(commandlinePassword) && Strings.isNullOrEmpty(commandlineUsername)) {
      eventHandlers.dispatch(
          LogEvent.warn(
              passwordProperty
                  + " system property is set, but "
                  + usernameProperty
                  + " is not; attempting other authentication methods."));
    }
    if (!Strings.isNullOrEmpty(commandlineUsername) && Strings.isNullOrEmpty(commandlinePassword)) {
      eventHandlers.dispatch(
          LogEvent.warn(
              usernameProperty
                  + " system property is set, but "
                  + passwordProperty
                  + " is not; attempting other authentication methods."));
    }

    // Check auth configuration next; warn if they aren't both set
    if (Strings.isNullOrEmpty(auth.getUsername()) && Strings.isNullOrEmpty(auth.getPassword())) {
      return Optional.empty();
    }
    if (Strings.isNullOrEmpty(auth.getUsername())) {
      eventHandlers.dispatch(
          LogEvent.warn(
              auth.getUsernameDescriptor()
                  + " is missing from build configuration; ignoring auth section."));
      return Optional.empty();
    }
    if (Strings.isNullOrEmpty(auth.getPassword())) {
      eventHandlers.dispatch(
          LogEvent.warn(
              auth.getPasswordDescriptor()
                  + " is missing from build configuration; ignoring auth section."));
      return Optional.empty();
    }

    return Optional.of(Credential.from(auth.getUsername(), auth.getPassword()));
  }

  /**
   * Returns an {@link ImageReference} parsed from the configured target image, or one of the form
   * {@code project-name:project-version} if target image is not configured
   *
   * @param targetImage the configured target image reference
   * @param eventHandlers the {@link EventHandlers} used to dispatch log events
   * @param generatedName the image name to use if {@code targetImage} is {@code null}
   * @param generatedTag the tag to use if {@code targetImage} is {@code null}
   * @param helpfulSuggestions used for generating the message notifying the user of the generated
   *     tag
   * @return an {@link ImageReference} parsed from the configured target image, or one of the form
   *     {@code project-name:project-version} if target image is not configured
   * @throws InvalidImageReferenceException if the configured or generated image reference is
   *     invalid
   */
  public static ImageReference getGeneratedTargetDockerTag(
      @Nullable String targetImage,
      EventHandlers eventHandlers,
      String generatedName,
      String generatedTag,
      HelpfulSuggestions helpfulSuggestions)
      throws InvalidImageReferenceException {
    if (Strings.isNullOrEmpty(targetImage)) {
      eventHandlers.dispatch(
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
    int startIndex = 0;
    for (int endIndex = 0; endIndex < property.length(); endIndex++) {
      if (property.charAt(endIndex) == ',') {
        // Split on non-escaped comma
        items.add(property.substring(startIndex, endIndex));
        startIndex = endIndex + 1;
      } else if (property.charAt(endIndex) == '\\') {
        // Found a backslash, ignore next character
        endIndex++;
      }
    }
    items.add(property.substring(startIndex));
    return items;
  }

  private ConfigurationPropertyValidator() {}
}
