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

import com.google.cloud.tools.jib.event.events.LogEvent;
import com.google.cloud.tools.jib.frontend.MainClassFinder;
import com.google.cloud.tools.jib.image.LayerEntry;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;
import java.nio.file.Path;
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
      mainClass = findMainClassInClassFiles(projectProperties);

    } else if (!isValidJavaClass(mainClass)) {
      // If mainClass found in projectProperties is not valid, try to search in class files, but
      // don't error if not found in class files.
      try {
        mainClass = findMainClassInClassFiles(projectProperties);

      } catch (MainClassInferenceException ignored) {
        // Fallback to using the mainClass found in projectProperties.
      }
    }

    Preconditions.checkNotNull(mainClass);
    if (!isValidJavaClass(mainClass)) {
      projectProperties
          .getEventDispatcher()
          .dispatch(LogEvent.warn("'mainClass' is not a valid Java class : " + mainClass));
    }

    return mainClass;
  }

  /**
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

  @Nullable
  private static String getMainClassFromJar(ProjectProperties projectProperties) {
    projectProperties
        .getEventDispatcher()
        .dispatch(
            LogEvent.info(
                "Searching for main class... Add a 'mainClass' configuration to '"
                    + projectProperties.getPluginName()
                    + "' to improve build speed."));
    return projectProperties.getMainClassFromJar();
  }

  private static String findMainClassInClassFiles(ProjectProperties projectProperties)
      throws MainClassInferenceException {
    projectProperties
        .getEventDispatcher()
        .dispatch(
            LogEvent.debug(
                "Could not find a valid main class specified in "
                    + projectProperties.getJarPluginName()
                    + "; attempting to infer main class."));

    ImmutableList<Path> classesSourceFiles =
        projectProperties
            .getJavaLayerConfigurations()
            .getClassLayerEntries()
            .stream()
            .map(LayerEntry::getSourceFile)
            .collect(ImmutableList.toImmutableList());

    MainClassFinder.Result mainClassFinderResult =
        new MainClassFinder(classesSourceFiles, projectProperties.getEventDispatcher()).find();

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

  private MainClassResolver() {}
}
