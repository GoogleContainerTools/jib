/*
 * Copyright 2018 Google LLC. All Rights Reserved.
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

import com.google.common.base.Strings;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import javax.annotation.Nullable;
import jdk.internal.org.objectweb.asm.ClassReader;
import jdk.internal.org.objectweb.asm.ClassVisitor;
import jdk.internal.org.objectweb.asm.MethodVisitor;
import jdk.internal.org.objectweb.asm.Opcodes;
import jdk.internal.org.objectweb.asm.Type;

/** Class used for inferring the main class in an application. */
public class MainClassFinder {

  /** Exception thrown when multiple main class candidates are found. */
  public static class MultipleClassesFoundException extends Exception {
    MultipleClassesFoundException() {
      super("Multiple classes found while trying to infer main class");
    }
  }

  /** ClassVisitor used to search for main method within a class file. */
  private static class ClassDescriptor extends ClassVisitor {
    private boolean foundMainMethod;

    private ClassDescriptor() {
      super(Opcodes.ASM5);
    }

    /** Builds a ClassDescriptor from an input stream. */
    static ClassDescriptor build(InputStream inputStream) throws IOException {
      ClassReader classReader = new ClassReader(inputStream);
      ClassDescriptor classDescriptor = new ClassDescriptor();
      classReader.accept(classDescriptor, ClassReader.SKIP_CODE);
      return classDescriptor;
    }

    @Nullable
    @Override
    public MethodVisitor visitMethod(
        int access, String name, String desc, String signature, String[] exceptions) {
      Type methodType = Type.getMethodType(Type.VOID_TYPE, Type.getType(String[].class));
      if ((access & Opcodes.ACC_PUBLIC) != 0
          && (access & Opcodes.ACC_STATIC) != 0
          && name.equals("main")
          && desc.equals(methodType.getDescriptor())) {
        this.foundMainMethod = true;
      }
      return null;
    }

    boolean isMainMethodFound() {
      return foundMainMethod;
    }
  }

  /**
   * Searches for a class containing a main method in a list of files.
   *
   * @return the name of the class if one is found, null if no class is found.
   * @throws MultipleClassesFoundException if more than one valid main class is found.
   */
  @Nullable
  public static String findMainClass(List<Path> classFiles, String rootDirectory)
      throws MultipleClassesFoundException {
    String className = null;
    for (Path classFile : classFiles) {
      // Skip non-class files
      if (!classFile.toString().endsWith(".class")) {
        continue;
      }

      try (InputStream inputStream = Files.newInputStream(classFile)) {
        ClassDescriptor classDescriptor = ClassDescriptor.build(inputStream);
        if (!classDescriptor.isMainMethodFound()) {
          // Valid class, but has no main method
          continue;
        }
      } catch (IOException | ArrayIndexOutOfBoundsException ex) {
        // Not a valid class file
        continue;
      }

      if (className == null) {
        // Found a valid main class, save it and continue
        className = classFile.toAbsolutePath().toString();
        if (!Strings.isNullOrEmpty(rootDirectory)) {
          className = className.substring(rootDirectory.length() + 1);
        }
        className = className.replace('/', '.').replace('\\', '.');
        className = className.substring(0, className.length() - ".class".length());
      } else {
        // Found more than one valid main class, error out
        throw new MultipleClassesFoundException();
      }
    }

    return className;
  }
}
