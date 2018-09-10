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

package com.google.cloud.tools.jib.frontend;

import com.google.cloud.tools.jib.JibLogger;
import com.google.cloud.tools.jib.filesystem.DirectoryWalker;
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

public class MainClassFinder {

  /** The result of a call to {@link #find}. */
  public static class Result {

    /** The type of error. */
    public enum ErrorType {

      // An IOException occurred.
      IO_EXCEPTION,

      // Did not find any main class.
      MAIN_CLASS_NOT_FOUND,

      // Found multiple main classes.
      MULTIPLE_MAIN_CLASSES
    }

    private static Result success(String foundMainClass) {
      return new Result(true, Collections.singletonList(foundMainClass), null, null);
    }

    private static Result mainClassNotFound() {
      return new Result(false, Collections.emptyList(), ErrorType.MAIN_CLASS_NOT_FOUND, null);
    }

    private static Result multipleMainClasses(List<String> foundMainClasses) {
      return new Result(false, foundMainClasses, ErrorType.MULTIPLE_MAIN_CLASSES, null);
    }

    private static Result ioException(IOException ioException) {
      return new Result(false, Collections.emptyList(), ErrorType.IO_EXCEPTION, ioException);
    }

    private final boolean isSuccess;
    private final List<String> foundMainClasses;
    @Nullable private final ErrorType errorType;
    @Nullable private final Throwable errorCause;

    private Result(
        boolean isSuccess,
        List<String> foundMainClasses,
        @Nullable ErrorType errorType,
        @Nullable Throwable errorCause) {
      this.isSuccess = isSuccess;
      this.foundMainClasses = foundMainClasses;
      this.errorType = errorType;
      this.errorCause = errorCause;
    }

    /**
     * Gets whether or not this result is a success.
     *
     * @return {@code true} if successful; {@code false} if not
     */
    public boolean isSuccess() {
      return isSuccess;
    }

    /**
     * Gets the found main class. Only call if {@link #isSuccess} is {@code true}.
     *
     * @return the found main class
     */
    public String getFoundMainClass() {
      Preconditions.checkArgument(isSuccess);
      Preconditions.checkArgument(foundMainClasses.size() == 1);
      return foundMainClasses.get(0);
    }

    /**
     * Gets the type of error. Call only if {@link #isSuccess} is {@code false}.
     *
     * @return the type of error, or {@code null} if successful
     */
    public ErrorType getErrorType() {
      return Preconditions.checkNotNull(errorType);
    }

    /**
     * Gets the cause of the error. Call only if {@link #getErrorType} is {@link
     * ErrorType#IO_EXCEPTION}.
     *
     * @return the cause of the error, or {@code null} if not available
     */
    public Throwable getErrorCause() {
      return Preconditions.checkNotNull(errorCause);
    }

    /**
     * Gets the found main classes.
     *
     * @return the found main classes
     */
    public List<String> getFoundMainClasses() {
      return foundMainClasses;
    }
  }

  private final ImmutableList<Path> classesFiles;
  private final JibLogger buildLogger;

  /**
   * Finds a class with {@code psvm} in {@code classesFiles}.
   *
   * @param classesFiles the classes files to check
   * @param buildLogger used for displaying status messages.
   */
  public MainClassFinder(ImmutableList<Path> classesFiles, JibLogger buildLogger) {
    this.classesFiles = classesFiles;
    this.buildLogger = buildLogger;
  }

  /**
   * Tries to find a class with {@code psvm} in {@link #classesFiles}.
   *
   * @return the {@link Result} of the main class finding attempt
   */
  public Result find() {
    try {
      List<String> mainClasses = new ArrayList<>();
      Set<Path> roots = new HashSet<>();
      for (Path classPath : classesFiles) {
        roots.add(classPath.getParent());
      }
      for (Path root : roots) {
        mainClasses.addAll(findMainClasses(root));
      }

      if (mainClasses.size() == 1) {
        // Valid class found.
        return Result.success(mainClasses.get(0));
      }
      if (mainClasses.size() == 0) {
        // No main class found anywhere.
        return Result.mainClassNotFound();
      }
      // More than one main class found.
      return Result.multipleMainClasses(mainClasses);

    } catch (IOException ex) {
      return Result.ioException(ex);
    }
  }

  /**
   * Finds the classes with {@code public static void main(String[] args)} in {@code rootDirectory}.
   *
   * @param rootDirectory directory containing the {@code .class} files.
   * @return a list of class names containing a main method.
   * @throws IOException if searching the root directory fails.
   */
  private List<String> findMainClasses(Path rootDirectory) throws IOException {
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
}
