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
 * Finds main classes in a list of class files. Main classes are classes that define a valid main
 * method.
 *
 * <p>For class files compiled with Java 25 or later (JEP 512), valid main methods include:
 *
 * <ul>
 *   <li>{@code static void main(String[] args)} - with public, protected, or package-private access
 *   <li>{@code static void main()} - static main without parameters
 *   <li>{@code void main(String[] args)} - instance main with parameters
 *   <li>{@code void main()} - instance main without parameters
 * </ul>
 *
 * <p>For class files compiled with earlier Java versions, only the traditional {@code public static
 * void main(String[] args)} is recognized.
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

    /** Java 25 class file major version (flexible main methods finalized). */
    private static final int JAVA_25_CLASS_VERSION = 69;

    /** The return/argument types for main with String[] parameter. */
    private static final String MAIN_WITH_ARGS_DESCRIPTOR =
        org.objectweb.asm.Type.getMethodDescriptor(
            org.objectweb.asm.Type.VOID_TYPE, org.objectweb.asm.Type.getType(String[].class));

    /** The return/argument types for main without parameters. */
    private static final String MAIN_NO_ARGS_DESCRIPTOR =
        org.objectweb.asm.Type.getMethodDescriptor(org.objectweb.asm.Type.VOID_TYPE);

    /** Optional modifiers that main may or may not have. */
    private static final int OPTIONAL_MODIFIERS =
        Opcodes.ACC_FINAL | Opcodes.ACC_DEPRECATED | Opcodes.ACC_VARARGS | Opcodes.ACC_SYNTHETIC;

    private boolean visitedMainClass;
    private int classVersion;

    private MainClassVisitor() {
      super(Opcodes.ASM9);
    }

    @Override
    public void visit(
        int version,
        int access,
        String name,
        String signature,
        String superName,
        String[] interfaces) {
      this.classVersion = version;
      super.visit(version, access, name, signature, superName, interfaces);
    }

    @Override
    @Nullable
    public MethodVisitor visitMethod(
        int access, String name, String descriptor, String signature, String[] exceptions) {
      if (!name.equals("main")) {
        return null;
      }

      if ((access & Opcodes.ACC_PRIVATE) != 0) {
        return null;
      }

      // For class files before Java 25, only traditional main is valid
      if (classVersion < JAVA_25_CLASS_VERSION) {
        // Traditional main: public static void main(String[] args)
        int requiredAccess = Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC;
        if ((access & ~OPTIONAL_MODIFIERS) == requiredAccess
            && descriptor.equals(MAIN_WITH_ARGS_DESCRIPTOR)) {
          visitedMainClass = true;
        }
        return null;
      }

      // For Java 25+, check flexible main method signatures (JEP 512)
      boolean isValidDescriptor =
          descriptor.equals(MAIN_WITH_ARGS_DESCRIPTOR)
              || descriptor.equals(MAIN_NO_ARGS_DESCRIPTOR);

      if (!isValidDescriptor) {
        return null;
      }

      int relevantAccess =
          access & ~(Opcodes.ACC_PUBLIC | Opcodes.ACC_PROTECTED | OPTIONAL_MODIFIERS);
      if (relevantAccess == Opcodes.ACC_STATIC || relevantAccess == 0) {
        visitedMainClass = true;
      }

      return null;
    }
  }

  /**
   * Tries to find classes with valid main methods (see class javadoc) in {@code files}.
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

      } catch (IllegalArgumentException ex) {
        throw new UnsupportedOperationException(
            "Check the full stace trace, and if the root cause is from ASM ClassReader about "
                + "unsupported class file version, see "
                + "https://github.com/GoogleContainerTools/jib/blob/master/docs/faq.md"
                + "#i-am-seeing-unsupported-class-file-major-version-when-building",
            ex);

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
    if (mainClasses.isEmpty()) {
      // No main class found anywhere.
      return Result.mainClassNotFound();
    }
    // More than one main class found.
    return Result.multipleMainClasses(mainClasses);
  }
}
