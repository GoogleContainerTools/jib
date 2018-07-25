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
import com.google.cloud.tools.jib.filesystem.DirectoryWalker;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javassist.ClassPool;
import javassist.CtClass;
import javassist.CtMethod;
import javassist.NotFoundException;
import javax.annotation.Nullable;

/** Infers the main class in an application. */
public class MainClassFinder {

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

        try {
          // Adds each file in the classes output directory to the classes files list.
          ImmutableList<Path> classesFiles =
              projectProperties.getClassesLayerEntry().getSourceFiles();
          List<String> mainClasses = new ArrayList<>();
          Set<Path> visitedRoots = new HashSet<>();
          for (Path classPath : classesFiles) {
            Path root = classPath.getParent();
            if (visitedRoots.contains(root)) {
              continue;
            }
            visitedRoots.add(root);
            mainClasses.addAll(findMainClasses(root, logger));
          }

          if (mainClasses.size() == 1) {
            // Valid class found; use inferred main class
            mainClass = mainClasses.get(0);
          } else if (mainClasses.size() == 0 && mainClass == null) {
            // No main class found anywhere
            throw new MainClassInferenceException(
                projectProperties
                    .getMainClassHelpfulSuggestions("Main class was not found")
                    .forMainClassNotFound(projectProperties.getPluginName()));
          } else if (mainClasses.size() > 1 && mainClass == null) {
            // More than one main class found with no jar plugin to fall back on; error
            throw new MainClassInferenceException(
                projectProperties
                    .getMainClassHelpfulSuggestions(
                        "Multiple valid main classes were found: " + String.join(", ", mainClasses))
                    .forMainClassNotFound(projectProperties.getPluginName()));
          }
        } catch (IOException ex) {
          throw new MainClassInferenceException(
              projectProperties
                  .getMainClassHelpfulSuggestions("Failed to get main class")
                  .forMainClassNotFound(projectProperties.getPluginName()),
              ex);
        }
      }
    }
    Preconditions.checkNotNull(mainClass);
    if (!BuildConfiguration.isValidJavaClass(mainClass)) {
      logger.warn("'mainClass' is not a valid Java class : " + mainClass);
    }

    return mainClass;
  }

  /**
   * Finds the classes with {@code public static void main(String[] args)} in {@code rootDirectory}.
   *
   * @param rootDirectory directory containing the {@code .class} files.
   * @param buildLogger used for displaying status messages.
   * @return a list of class names containing a main method.
   * @throws IOException if searching the root directory fails.
   */
  @VisibleForTesting
  static List<String> findMainClasses(Path rootDirectory, BuildLogger buildLogger)
      throws IOException {
    // Makes sure rootDirectory is valid.
    if (!Files.exists(rootDirectory) || !Files.isDirectory(rootDirectory)) {
      return Collections.emptyList();
    }

    List<String> classNames = new ArrayList<>();

    ClassPool classPool = new ClassPool();
    classPool.appendSystemPath();

    try {
      CtClass[] mainMethodParams = new CtClass[] {classPool.get("java.lang.String[]")};

      new DirectoryWalker(rootDirectory)
          .filter(Files::isRegularFile)
          .filter(path -> path.toString().endsWith(".class"))
          .walk(
              classFile -> {
                try (InputStream classFileInputStream = Files.newInputStream(classFile)) {
                  CtClass fileClass = classPool.makeClass(classFileInputStream);

                  // Check if class contains 'public static void main(String[] args)'.
                  CtMethod mainMethod = fileClass.getDeclaredMethod("main", mainMethodParams);

                  if (CtClass.voidType.equals(mainMethod.getReturnType())
                      && Modifier.isStatic(mainMethod.getModifiers())
                      && Modifier.isPublic(mainMethod.getModifiers())) {
                    classNames.add(fileClass.getName());
                  }

                } catch (NotFoundException ex) {
                  // Ignores main method not found.

                } catch (IOException ex) {
                  // Could not read class file.
                  buildLogger.warn("Could not read class file: " + classFile);
                }
              });

      return classNames;

    } catch (NotFoundException ex) {
      // Thrown if 'java.lang.String' is not found in classPool.
      throw new RuntimeException(ex);
    }
  }

  private MainClassFinder() {}
}
