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
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import javassist.ClassPool;
import javassist.CtClass;
import javassist.CtMethod;
import javassist.NotFoundException;
import javax.annotation.Nullable;

/** Finds main classes in a list of class files.. */
public class MainClassFinder {

  /** The result of a call to {@link #find}. */
  public static class Result {

    /** The type of error. */
    public enum ErrorType {

      // Did not find any main class.
      MAIN_CLASS_NOT_FOUND,

      // Found multiple main classes.
      MULTIPLE_MAIN_CLASSES
    }

    private static Result success(String foundMainClass) {
      return new Result(true, Collections.singletonList(foundMainClass), null);
    }

    private static Result mainClassNotFound() {
      return new Result(false, Collections.emptyList(), ErrorType.MAIN_CLASS_NOT_FOUND);
    }

    private static Result multipleMainClasses(List<String> foundMainClasses) {
      return new Result(false, foundMainClasses, ErrorType.MULTIPLE_MAIN_CLASSES);
    }

    private final boolean isSuccess;
    private final List<String> foundMainClasses;
    @Nullable private final ErrorType errorType;

    private Result(
        boolean isSuccess, List<String> foundMainClasses, @Nullable ErrorType errorType) {
      this.isSuccess = isSuccess;
      this.foundMainClasses = foundMainClasses;
      this.errorType = errorType;
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
     * Gets the found main classes.
     *
     * @return the found main classes
     */
    public List<String> getFoundMainClasses() {
      return foundMainClasses;
    }
  }

  private final ImmutableList<Path> files;
  private final JibLogger buildLogger;

  /**
   * Finds a class with {@code psvm} in {@code files}.
   *
   * @param files the files to check
   * @param buildLogger used for displaying status messages.
   */
  public MainClassFinder(ImmutableList<Path> files, JibLogger buildLogger) {
    this.files = files;
    this.buildLogger = buildLogger;
  }

  /**
   * Tries to find a class with {@code psvm} in {@link #files}.
   *
   * @return the {@link Result} of the main class finding attempt
   */
  public Result find() {
    List<String> mainClasses = new ArrayList<>();
    for (Path file : files) {
      findMainClass(file).ifPresent(mainClasses::add);
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
  }

  /**
   * Checks the {@code file} for being a {@code .class} file with {@code public static void
   * main(String[] args)}.
   *
   * @param file the file
   * @return name of the class containing a main method, or {@link Optional#empty} if {@code
   *     classFile} is not a class
   */
  private Optional<String> findMainClass(Path file) {
    // Makes sure classFile is valid.
    if (!Files.exists(file) || !Files.isRegularFile(file) || !file.toString().endsWith(".class")) {
      return Optional.empty();
    }

    ClassPool classPool = new ClassPool();
    classPool.appendSystemPath();

    try {
      CtClass[] mainMethodParams = new CtClass[] {classPool.get("java.lang.String[]")};

      try (InputStream classFileInputStream = Files.newInputStream(file)) {
        CtClass fileClass = classPool.makeClass(classFileInputStream);

        // Check if class contains 'public static void main(String[] args)'.
        CtMethod mainMethod = fileClass.getDeclaredMethod("main", mainMethodParams);

        if (CtClass.voidType.equals(mainMethod.getReturnType())
            && Modifier.isStatic(mainMethod.getModifiers())
            && Modifier.isPublic(mainMethod.getModifiers())) {
          return Optional.of(fileClass.getName());
        }

      } catch (NotFoundException ex) {
        // Ignores main method not found.

      } catch (IOException ex) {
        // Could not read class file.
        buildLogger.warn("Could not read file: " + file);
      }

    } catch (NotFoundException ex) {
      // Thrown if 'java.lang.String' is not found in classPool.
      throw new RuntimeException(ex);
    }

    return Optional.empty();
  }
}
