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

import com.google.cloud.tools.jib.builder.BuildLogger;
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
   * <p>Warns if main class is not valid, or throws an error if no valid main class is not found.
   *
   * @param mainClass the explicitly configured main class ({@code null} if not configured).
   * @param projectProperties properties containing plugin information and help messages.
   * @return the name of the main class to be used for the container entrypoint.
   * @throws MainClassInferenceException if no valid main class is configured or discovered.
   */
  public static String resolveMainClass(
      @Nullable String mainClass, ProjectProperties projectProperties)
      throws MainClassInferenceException {
    BuildLogger logger = projectProperties.getLogger();
    if (mainClass == null) {
      logger.info(
          "Searching for main class... Add a 'mainClass' configuration to '"
              + projectProperties.getPluginName()
              + "' to improve build speed.");
      mainClass = projectProperties.getMainClassFromJar();
      if (mainClass == null || !BuildConfiguration.isValidJavaClass(mainClass)) {
        logger.debug(
            "Could not find a valid main class specified in "
                + projectProperties.getJarPluginName()
                + "; attempting to infer main class.");

        MainClassFinder.Result mainClassFinderResult =
            new MainClassFinder(
                    projectProperties.getClassesLayerEntry().getSourceFiles(),
                    projectProperties.getLogger())
                .find();

        if (mainClassFinderResult.isSuccess()) {
          mainClass = mainClassFinderResult.getFoundMainClass();

        } else if (mainClass == null) {
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
      }
    }
    Preconditions.checkNotNull(mainClass);
    if (!BuildConfiguration.isValidJavaClass(mainClass)) {
      logger.warn("'mainClass' is not a valid Java class : " + mainClass);
    }

    return mainClass;
  }

  private MainClassResolver() {}
}
