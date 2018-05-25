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

import com.google.cloud.tools.jib.builder.BuildConfiguration;
import com.google.cloud.tools.jib.builder.BuildLogger;
import com.google.cloud.tools.jib.filesystem.DirectoryWalker;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.common.base.Splitter;
import java.io.IOException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.annotation.Nullable;

/** Infers the main class in an application. */
public class MainClassFinder {

  /** Helper for loading a .class file. */
  private static class ClassFileLoader extends ClassLoader {

    private final Path rootDirectory;
    private final Path defaultClassFile;

    private ClassFileLoader(Path defaultClassFile, Path rootDirectory) {
      this.defaultClassFile = defaultClassFile;
      this.rootDirectory = rootDirectory;
    }

    @Nullable
    @Override
    public Class findClass(@Nullable String name) {
      try {
        // Name is only ever null when we call findClass manually, and not null otherwise. If null,
        // we should resolve the correct filename.
        Path file = (name == null ? defaultClassFile : getPathFromClassName(name));
        if (!Files.exists(file)) {
          // TODO: Log search class failure?
          return null;
        }

        byte[] bytes = Files.readAllBytes(file);
        return defineClass(name, bytes, 0, bytes.length);

      } catch (IOException
          | ClassFormatError
          | IndexOutOfBoundsException
          | SecurityException
          | NoClassDefFoundError ignored) {
        // Not a valid class file
        // TODO: Log search class failure when NoClassDefFoundError/SecurityException is caught?
        return null;
      }
    }

    private Path getPathFromClassName(String className) {
      Path path = rootDirectory;
      Deque<String> folders = new ArrayDeque<>(Splitter.on('.').splitToList(className));
      String fileName = folders.removeLast() + ".class";
      for (String folder : folders) {
        path = path.resolve(folder);
      }
      path = path.resolve(fileName);
      return path;
    }
  }

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
          Set<Path> visitedRoots = new HashSet<>();
          List<String> mainClasses = new ArrayList<>();
          for (Path classPath : projectProperties.getSourceFilesConfiguration().getClassesFiles()) {
            Path root = classPath.getParent();
            if (visitedRoots.contains(root)) {
              continue;
            }
            visitedRoots.add(root);
            mainClasses.addAll(findMainClasses(root));
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
   * Searches for a .class file containing a main method in a root directory.
   *
   * @return the name of the class if one is found, null if no class is found.
   * @throws IOException if searching/reading files fails.
   */
  @VisibleForTesting
  static List<String> findMainClasses(Path rootDirectory) throws IOException {
    List<String> classNames = new ArrayList<>();

    // Make sure rootDirectory is valid
    if (!Files.exists(rootDirectory) || !Files.isDirectory(rootDirectory)) {
      return classNames;
    }

    // Get all .class files
    new DirectoryWalker(rootDirectory)
        .filter(Files::isRegularFile)
        .filter(path -> path.toString().endsWith(".class"))
        .walk(
            classFile -> {
              Class<?> fileClass = new ClassFileLoader(classFile, rootDirectory).findClass(null);
              if (fileClass == null) {
                return;
              }
              try {
                // Check if class contains {@code public static void main(String[] args)}
                Method main = fileClass.getMethod("main", String[].class);
                if (main != null
                    && main.getReturnType() == void.class
                    && Modifier.isStatic(main.getModifiers())
                    && Modifier.isPublic(main.getModifiers())) {
                  classNames.add(fileClass.getName());
                }
              } catch (NoSuchMethodException | NoClassDefFoundError ignored) {
                // main method not found
                // TODO: Log search class failure when NoClassDefFoundError is caught?
              }
            });

    return classNames;
  }

  private MainClassFinder() {}
}
