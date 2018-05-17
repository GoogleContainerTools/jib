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

import com.google.cloud.tools.jib.filesystem.DirectoryWalker;
import java.io.IOException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.StringJoiner;
import javax.annotation.Nullable;

/** Infers the main class in an application. */
public class MainClassFinder {

  /** Helper for loading a .class file. */
  private static class ClassFileLoader extends ClassLoader {

    private final Path classFile;

    private ClassFileLoader(Path classFile) {
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
   * Searches for a .class file containing a main method given an absolute root directory.
   *
   * @return the name of the class if one is found, null if no class is found.
   * @throws IOException if searching/reading files fails.
   */
  public static List<String> findMainClass(Path rootDirectory) throws IOException {
    List<String> results = new ArrayList<>();

    // Make sure rootDirectory is valid
    if (!Files.exists(rootDirectory) || !Files.isDirectory(rootDirectory)) {
      return results;
    }

    // Get all .class files
    new DirectoryWalker(rootDirectory)
        .filter(Files::isRegularFile)
        .filter(path -> path.toString().endsWith(".class"))
        .walk(
            classFile -> {
              // Convert filename (rootDir/path/to/ClassName.class) to class name
              // (path.to.ClassName)
              Path relativized = rootDirectory.relativize(classFile);
              StringJoiner stringJoiner = new StringJoiner(".");
              for (Path path : relativized) {
                stringJoiner.add(path.toString());
              }
              String name = stringJoiner.toString();
              name = name.substring(0, name.length() - ".class".length());

              Class<?> fileClass = new ClassFileLoader(classFile).findClass(name);
              if (fileClass != null) {
                try {
                  // Check if class contains {@code public static void main(String[] args)}
                  Method main = fileClass.getMethod("main", String[].class);
                  if (main != null
                      && main.getReturnType() == void.class
                      && Modifier.isStatic(main.getModifiers())
                      && Modifier.isPublic(main.getModifiers())) {
                    results.add(name);
                  }
                } catch (NoSuchMethodException ignored) {
                  // main method not found
                }
              }
            });

    return results;
  }

  private MainClassFinder() {}
}
