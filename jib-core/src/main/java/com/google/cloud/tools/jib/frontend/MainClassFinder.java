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

import java.io.IOException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.annotation.Nullable;

/** Class used for inferring the main class in an application. */
public class MainClassFinder {

  /** Helper class for loading a .class file. */
  private static class ClassFileLoader extends ClassLoader {

    private Path classFile;

    ClassFileLoader(Path classFile) {
      this.classFile = classFile;
    }

    @Nullable
    @Override
    public Class findClass(String name) {
      try {
        byte[] bytes = Files.readAllBytes(classFile);
        return defineClass(name, bytes, 0, bytes.length);
      } catch (IOException | ClassFormatError ignored) {
        // Not a valid class file
        return null;
      }
    }
  }

  /**
   * Searches for a class containing a main method given a root directory.
   *
   * @return the name of the class if one is found, null if no class is found.
   * @throws MultipleClassesFoundException if more than one valid main class is found.
   */
  @Nullable
  public static String findMainClass(String rootDirectory)
      throws MultipleClassesFoundException, IOException {
    // Make sure rootDirectory is valid
    if (!Files.exists(Paths.get(rootDirectory)) || !Files.isDirectory(Paths.get(rootDirectory))) {
      return null;
    }

    String className = null;
    try (Stream<Path> pathStream = Files.walk(Paths.get(rootDirectory))) {
      List<Path> classFiles =
          pathStream
              .filter(Files::isRegularFile)
              .filter(path -> path.toString().endsWith(".class"))
              .collect(Collectors.toList());

      for (Path classFile : classFiles) {
        // Convert filename to class name
        String name = classFile.toAbsolutePath().toString();
        if (!rootDirectory.isEmpty()) {
          name = name.substring(rootDirectory.length() + 1);
        }
        name = name.replace('/', '.').replace('\\', '.');
        name = name.substring(0, name.length() - ".class".length());

        // Load class from file
        Class fileClass = new ClassFileLoader(classFile).findClass(name);
        if (fileClass == null) {
          continue;
        }

        // Check if class contains a public static void main(String[] args)
        try {
          Method main = fileClass.getMethod("main", String[].class);
          if (main != null
              && main.getReturnType() == void.class
              && Modifier.isStatic(main.getModifiers())
              && Modifier.isPublic(main.getModifiers())) {
            if (className == null) {
              className = name;
            } else {
              throw new MultipleClassesFoundException(className, name);
            }
          }

        } catch (NoSuchMethodException ignored) {
          // main method not found
        }
      }
    }

    return className;
  }
}
