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

import com.google.cloud.tools.jib.api.LogEvent;
import com.google.cloud.tools.jib.api.MainClassFinder;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Splitter;
import java.io.IOException;
import javax.annotation.Nullable;
import javax.lang.model.SourceVersion;

/** Infers the main class in an application. */
public class MainClassResolver {

  /**
   * If {@code mainClass} is {@code null}, tries to infer main class in this order:
   *
   * <ul>
   *   <li>1. Looks in a {@code jar} plugin provided by {@code projectProperties} ({@code
   *       maven-jar-plugin} for maven or {@code jar} task for gradle).
   *   <li>2. Searches for a class defined with a main method.
   * </ul>
   *
   * <p>Warns if main class provided by {@code projectProperties} is not valid, or throws an error
   * if no valid main class is found.
   *
   * @param configuredMainClass the explicitly configured main class ({@code null} if not
   *     configured)
   * @param projectProperties properties containing plugin information and help messages
   * @return the name of the main class to be used for the container entrypoint
   * @throws MainClassInferenceException if no valid main class is configured or discovered
   * @throws IOException if getting the class files from {@code projectProperties} fails
   */
  public static String resolveMainClass(
      @Nullable String configuredMainClass, ProjectProperties projectProperties)
      throws MainClassInferenceException, IOException {
    if (configuredMainClass != null) {
      if (isValidJavaClass(configuredMainClass)) {
        return configuredMainClass;
      }
      throw new MainClassInferenceException(
          HelpfulSuggestions.forMainClassNotFound(
              "'mainClass' configured in "
                  + projectProperties.getPluginName()
                  + " is not a valid Java class: "
                  + configuredMainClass,
              projectProperties.getPluginName()));
    }

    projectProperties.log(
        LogEvent.info(
            "Searching for main class... Add a 'mainClass' configuration to '"
                + projectProperties.getPluginName()
                + "' to improve build speed."));

    String mainClassFromJarPlugin = projectProperties.getMainClassFromJarPlugin();
    if (mainClassFromJarPlugin != null && isValidJavaClass(mainClassFromJarPlugin)) {
      return mainClassFromJarPlugin;
    }

    if (mainClassFromJarPlugin != null) {
      projectProperties.log(
          LogEvent.warn(
              "'mainClass' configured in "
                  + projectProperties.getJarPluginName()
                  + " is not a valid Java class: "
                  + mainClassFromJarPlugin));
    }
    projectProperties.log(
        LogEvent.info(
            "Could not find a valid main class from "
                + projectProperties.getJarPluginName()
                + "; looking into all class files to infer main class."));

    MainClassFinder.Result mainClassFinderResult =
        MainClassFinder.find(projectProperties.getClassFiles(), projectProperties::log);
    switch (mainClassFinderResult.getType()) {
      case MAIN_CLASS_FOUND:
        return mainClassFinderResult.getFoundMainClass();

      case MAIN_CLASS_NOT_FOUND:
        throw new MainClassInferenceException(
            HelpfulSuggestions.forMainClassNotFound(
                "Main class was not found", projectProperties.getPluginName()));

      case MULTIPLE_MAIN_CLASSES:
        throw new MainClassInferenceException(
            HelpfulSuggestions.forMainClassNotFound(
                "Multiple valid main classes were found: "
                    + String.join(", ", mainClassFinderResult.getFoundMainClasses()),
                projectProperties.getPluginName()));

      default:
        throw new IllegalStateException("Cannot reach here");
    }
  }

  /**
   * Checks if a string is a valid Java class name.
   *
   * @param className the class name to check
   * @return {@code true} if {@code className} is a valid Java class name; {@code false} otherwise
   */
  @VisibleForTesting
  static boolean isValidJavaClass(String className) {
    for (String part : Splitter.on('.').split(className)) {
      if (!SourceVersion.isIdentifier(part)) {
        return false;
      }
    }
    return true;
  }

  private MainClassResolver() {}
}
