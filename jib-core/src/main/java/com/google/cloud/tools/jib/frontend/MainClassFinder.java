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
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javassist.CannotCompileException;
import javassist.ClassPool;
import javax.annotation.Nullable;

/** Infers the main class in an application. */
public class MainClassFinder {

  /** Helper for loading a .class file. */
  @VisibleForTesting
  static class ClassFileLoader extends ClassLoader {

    /** Maps from class file to class. */
    private final Map<Path, Class<?>> definedClasses = new HashMap<>();

    private final Path rootDirectory;

    @VisibleForTesting
    ClassFileLoader(Path rootDirectory) {
      this.rootDirectory = rootDirectory;
    }

    /**
     * Use {@link #loadFromFile}.
     *
     * <p>This method resolves possible dependency classes for the classes loaded from files.
     */
    @Nullable
    @Override
    public Class<?> findClass(String className) {
      Path classFile = getPathFromClassName(className);

      if (!Files.exists(classFile)) {
        // TODO: Log search class failure?
        // Cannot find corresponding class file. The class is probably in a dependency JAR.
        // Makes an empty class to prevent NoClassDefFoundError.
        try {
          return ClassPool.getDefault()
              .makeClass(className)
              .toClass(this, MainClassFinder.class.getProtectionDomain());

        } catch (CannotCompileException ex) {
          throw new Error(ex);
        }
      }

      return loadClassFromFile(className, classFile);
    }

    @Nullable
    private Class<?> loadFromFile(Path classFile) {
      return loadClassFromFile(null, classFile);
    }

    /**
     * Use {@link #loadFromFile}.
     *
     * @param className the name of the class
     * @param classFile the .class file defining the class
     * @return the {@link Class} defined by the file, or {@code null} if the class could not be
     *     defined
     */
    @Nullable
    private Class<?> loadClassFromFile(@Nullable String className, Path classFile) {
      if (definedClasses.containsKey(classFile)) {
        return definedClasses.get(classFile);
      }

      try {
        byte[] bytes = Files.readAllBytes(classFile);
        Class<?> definedClass = defineClass(className, bytes, 0, bytes.length);
        definedClasses.put(classFile, definedClass);
        return definedClass;

      } catch (IOException | ClassFormatError | SecurityException | NoClassDefFoundError ignored) {
        // Not a valid class file.
        // TODO: Log search class failure when NoClassDefFoundError/SecurityException is caught?
        return null;
      }
    }

    /** Converts a class name (pack.ClassName) to a Path (rootDirectory/pack/ClassName.class). */
    @VisibleForTesting
    Path getPathFromClassName(String className) {
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

    // Makes sure rootDirectory is valid.
    if (!Files.exists(rootDirectory) || !Files.isDirectory(rootDirectory)) {
      return classNames;
    }

    // Gets all .class files.
    ClassFileLoader classFileLoader = new ClassFileLoader(rootDirectory);
    new DirectoryWalker(rootDirectory)
        .filter(Files::isRegularFile)
        .filter(path -> path.toString().endsWith(".class"))
        .walk(
            classFile -> {
              Class<?> fileClass = classFileLoader.loadFromFile(classFile);
              if (fileClass == null) {
                return;
              }
              try {
                // Check if class contains {@code public static void main(String[] args)}.
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
