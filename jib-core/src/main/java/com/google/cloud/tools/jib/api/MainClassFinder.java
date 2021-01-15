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

package com.google.cloud.tools.jib.api;

import com.google.common.base.Preconditions;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;
import javax.annotation.Nullable;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

/**
 * Finds main classes in a list of class files. Main classes are classes that define the {@code
 * public static void main(String[] args)} method.
 */
public class MainClassFinder {

  /** The result of a call to {@link #find}. */
  public static class Result {

    /** The type of result. */
    public enum Type {

      // Found a single main class.
      MAIN_CLASS_FOUND,

      // Did not find any main class.
      MAIN_CLASS_NOT_FOUND,

      // Found multiple main classes.
      MULTIPLE_MAIN_CLASSES
    }

    private static Result success(String foundMainClass) {
      return new Result(Type.MAIN_CLASS_FOUND, Collections.singletonList(foundMainClass));
    }

    private static Result mainClassNotFound() {
      return new Result(Type.MAIN_CLASS_NOT_FOUND, Collections.emptyList());
    }

    private static Result multipleMainClasses(List<String> foundMainClasses) {
      return new Result(Type.MULTIPLE_MAIN_CLASSES, foundMainClasses);
    }

    private final Type type;
    private final List<String> foundMainClasses;

    private Result(Type type, List<String> foundMainClasses) {
      this.foundMainClasses = foundMainClasses;
      this.type = type;
    }

    /**
     * Gets the found main class. Only call if {@link #getType} is {@link Type#MAIN_CLASS_FOUND}.
     *
     * @return the found main class
     */
    public String getFoundMainClass() {
      Preconditions.checkState(Type.MAIN_CLASS_FOUND == type);
      Preconditions.checkState(foundMainClasses.size() == 1);
      return foundMainClasses.get(0);
    }

    /**
     * Gets the type of the result.
     *
     * @return the type of the result
     */
    public Type getType() {
      return type;
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

  /** {@link ClassVisitor} that keeps track of whether or not it has visited a main class. */
  private static class MainClassVisitor extends ClassVisitor {

    /** The return/argument types for main. */
    private static final String MAIN_DESCRIPTOR =
        org.objectweb.asm.Type.getMethodDescriptor(
            org.objectweb.asm.Type.VOID_TYPE, org.objectweb.asm.Type.getType(String[].class));

    /** Accessors that main may or may not have. */
    private static final int OPTIONAL_ACCESS =
        Opcodes.ACC_FINAL | Opcodes.ACC_DEPRECATED | Opcodes.ACC_VARARGS | Opcodes.ACC_SYNTHETIC;

    private boolean visitedMainClass;

    private MainClassVisitor() {
      super(Opcodes.ASM9);
    }

    @Override
    @Nullable
    public MethodVisitor visitMethod(
        int access, String name, String descriptor, String signature, String[] exceptions) {
      if ((access & ~OPTIONAL_ACCESS) == (Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC)
          && name.equals("main")
          && descriptor.equals(MAIN_DESCRIPTOR)) {
        visitedMainClass = true;
      }
      return null;
    }
  }

  /**
   * Tries to find classes with {@code psvm} (see class javadoc) in {@code files}.
   *
   * @param files the files to search
   * @param logger a {@link Consumer} used to handle log events
   * @return the {@link Result} of the main class finding attempt
   */
  public static Result find(List<Path> files, Consumer<LogEvent> logger) {
    List<String> mainClasses = new ArrayList<>();
    for (Path file : files) {
      // Makes sure classFile is valid.
      if (!Files.exists(file)) {
        logger.accept(LogEvent.debug("MainClassFinder: " + file + " does not exist; ignoring"));
        continue;
      }
      if (!Files.isRegularFile(file)) {
        logger.accept(
            LogEvent.debug("MainClassFinder: " + file + " is not a regular file; skipping"));
        continue;
      }
      if (!file.toString().endsWith(".class")) {
        logger.accept(
            LogEvent.debug("MainClassFinder: " + file + " is not a class file; skipping"));
        continue;
      }

      MainClassVisitor mainClassVisitor = new MainClassVisitor();
      try (InputStream classFileInputStream = Files.newInputStream(file)) {
        ClassReader reader = new ClassReader(classFileInputStream);
        reader.accept(mainClassVisitor, 0);
        if (mainClassVisitor.visitedMainClass) {
          mainClasses.add(reader.getClassName().replace('/', '.'));
        }

      } catch (ArrayIndexOutOfBoundsException ignored) {
        // Not a valid class file (thrown by ClassReader if it reads an invalid format)
        logger.accept(LogEvent.warn("Invalid class file found: " + file));

      } catch (IOException ignored) {
        // Could not read class file.
        logger.accept(LogEvent.warn("Could not read file: " + file));
      }
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
}
