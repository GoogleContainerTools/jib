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

import com.google.cloud.tools.jib.configuration.BuildConfiguration;
import com.google.common.base.Preconditions;
import com.google.common.base.Verify;
import javax.annotation.Nullable;

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
   * @param mainClass the explicitly configured main class ({@code null} if not configured).
   * @param projectProperties properties containing plugin information and help messages.
   * @return the name of the main class to be used for the container entrypoint.
   * @throws MainClassInferenceException if no valid main class is configured or discovered.
   */
  public static String resolveMainClass(
      @Nullable String mainClass, ProjectProperties projectProperties)
      throws MainClassInferenceException {
    // If mainClass is null, try to find via projectProperties.
    if (mainClass == null) {
      mainClass = getMainClassFromJar(projectProperties);
    }

    // If mainClass is still null, try to search in class files.
    if (mainClass == null) {
      mainClass = findMainClassInClassFiles(projectProperties, true);

    } else if (!BuildConfiguration.isValidJavaClass(mainClass)) {
      // If mainClass found in projectProperties is not valid, try to search in class files, but
      // don't error if not found in class files.
      String mainClassInClassFiles = findMainClassInClassFiles(projectProperties, false);
      if (mainClassInClassFiles != null) {
        mainClass = mainClassInClassFiles;
      }
    }

    Preconditions.checkNotNull(mainClass);
    if (!BuildConfiguration.isValidJavaClass(mainClass)) {
      projectProperties.getLogger().warn("'mainClass' is not a valid Java class : " + mainClass);
    }

    return mainClass;
  }

  @Nullable
  private static String getMainClassFromJar(ProjectProperties projectProperties) {
    projectProperties
        .getLogger()
        .info(
            "Searching for main class... Add a 'mainClass' configuration to '"
                + projectProperties.getPluginName()
                + "' to improve build speed.");
    return projectProperties.getMainClassFromJar();
  }

  @Nullable
  private static String findMainClassInClassFiles(
      ProjectProperties projectProperties, boolean shouldError) throws MainClassInferenceException {
    projectProperties
        .getLogger()
        .debug(
            "Could not find a valid main class specified in "
                + projectProperties.getJarPluginName()
                + "; attempting to infer main class.");

    MainClassFinder.Result mainClassFinderResult =
        new MainClassFinder(
                projectProperties.getClassesLayerEntry().getSourceFiles(),
                projectProperties.getLogger())
            .find();

    if (mainClassFinderResult.isSuccess()) {
      return mainClassFinderResult.getFoundMainClass();
    }

    if (!shouldError) {
      return null;
    }
    Verify.verify(mainClassFinderResult.getErrorType() != null);
    switch (mainClassFinderResult.getErrorType()) {
      case MAIN_CLASS_NOT_FOUND:
        throw new MainClassInferenceException(
            projectProperties
                .getMainClassHelpfulSuggestions("Main class was not found")
                .forMainClassNotFound(projectProperties.getPluginName()));

      case MULTIPLE_MAIN_CLASSES:
        throw new MainClassInferenceException(
            projectProperties
                .getMainClassHelpfulSuggestions(
                    "Multiple valid main classes were found: "
                        + String.join(", ", mainClassFinderResult.getFoundMainClasses()))
                .forMainClassNotFound(projectProperties.getPluginName()));

      case IO_EXCEPTION:
        throw new MainClassInferenceException(
            projectProperties
                .getMainClassHelpfulSuggestions("Failed to get main class")
                .forMainClassNotFound(projectProperties.getPluginName()),
            mainClassFinderResult.getErrorCause());

      default:
        throw new IllegalStateException("Cannot reach here");
    }
  }

  private MainClassResolver() {}
}
